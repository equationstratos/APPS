package com.survival.vibro

import kotlin.math.max
import kotlin.math.sqrt

/** Immutable snapshot of the measurement, in m/s². [trace] holds one peak per bin, oldest first. */
class Reading(val current: Float, val peak: Float, val rms: Float, val trace: FloatArray) {

    fun withPeak(value: Float) = Reading(current, value, rms, trace)

    fun toArray(): FloatArray = floatArrayOf(current, peak, rms) + trace

    companion object {
        val Empty = Reading(0f, 0f, 0f, FloatArray(VibrationAnalyzer.TRACE_SIZE))

        fun fromArray(values: FloatArray): Reading? =
            if (values.size < 3) null
            else Reading(values[0], values[1], values[2], values.copyOfRange(3, values.size))
    }
}

/**
 * Accumulates linear-acceleration magnitudes into fixed time bins (peak, sum of squares, count).
 * Bins are addressed by absolute time so gaps (app paused) simply read as empty, and the RMS over
 * one second is exact without storing every sample. Must be used from a single thread.
 */
class VibrationAnalyzer {

    private val binIds = LongArray(TRACE_SIZE) { NO_BIN }
    private val binPeak = FloatArray(TRACE_SIZE)
    private val binSumSquares = DoubleArray(TRACE_SIZE)
    private val binCount = IntArray(TRACE_SIZE)
    private var peak = 0f

    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var lastTimestampNs = NO_TIMESTAMP
    private var warmupUntilMs = 0L

    /**
     * Call when the sensor (re)starts: forgets the gravity estimate (the phone may have moved) and
     * ignores the first samples, during which sensor fusion and the high-pass produce a transient
     * that would otherwise be recorded as the peak.
     */
    fun restart(nowMs: Long) {
        lastTimestampNs = NO_TIMESTAMP
        warmupUntilMs = nowMs + WARMUP_MS
    }

    fun resetPeak() {
        peak = 0f
    }

    /** Sample from TYPE_LINEAR_ACCELERATION: gravity already removed by the system. */
    fun addLinear(x: Float, y: Float, z: Float, nowMs: Long) {
        add(magnitude(x, y, z), nowMs)
    }

    /** Sample from TYPE_ACCELEROMETER: gravity is removed with a time-constant based high-pass. */
    fun addRaw(x: Float, y: Float, z: Float, timestampNs: Long, nowMs: Long) {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
        if (lastTimestampNs == NO_TIMESTAMP) {
            gravityX = x
            gravityY = y
            gravityZ = z
            lastTimestampNs = timestampNs
            return
        }
        val dt = ((timestampNs - lastTimestampNs).toFloat() / 1e9f).coerceIn(0f, MAX_DT_S)
        lastTimestampNs = timestampNs
        val alpha = GRAVITY_TAU_S / (GRAVITY_TAU_S + dt)
        gravityX = alpha * gravityX + (1f - alpha) * x
        gravityY = alpha * gravityY + (1f - alpha) * y
        gravityZ = alpha * gravityZ + (1f - alpha) * z
        add(magnitude(x - gravityX, y - gravityY, z - gravityZ), nowMs)
    }

    fun read(nowMs: Long): Reading {
        val newest = nowMs / BIN_MS
        val trace = FloatArray(TRACE_SIZE)
        var sumSquares = 0.0
        var count = 0
        var current = 0f
        for (k in 0 until TRACE_SIZE) {
            val id = newest - (TRACE_SIZE - 1) + k
            if (id < 0) continue
            val slot = (id % TRACE_SIZE).toInt()
            if (binIds[slot] != id) continue
            trace[k] = binPeak[slot]
            val age = newest - id
            if (age < RMS_BINS) {
                sumSquares += binSumSquares[slot]
                count += binCount[slot]
            }
            if (age < CURRENT_BINS) current = max(current, binPeak[slot])
        }
        val rms = if (count > 0) sqrt(sumSquares / count).toFloat() else 0f
        return Reading(current, peak, rms, trace)
    }

    private fun add(value: Float, nowMs: Long) {
        if (!value.isFinite() || nowMs < warmupUntilMs) return
        val id = nowMs / BIN_MS
        val slot = (id % TRACE_SIZE).toInt()
        if (binIds[slot] != id) {
            binIds[slot] = id
            binPeak[slot] = 0f
            binSumSquares[slot] = 0.0
            binCount[slot] = 0
        }
        binPeak[slot] = max(binPeak[slot], value)
        binSumSquares[slot] += value.toDouble() * value.toDouble()
        binCount[slot] += 1
        if (value > peak) peak = value
    }

    private fun magnitude(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

    companion object {
        private const val BIN_MS = 40L

        /** 250 bins of 40 ms = 10 s of history. */
        const val TRACE_SIZE = 250
        private const val RMS_BINS = 25

        // A single sample of an oscillation can land near zero; the max over ~120 ms shows the amplitude.
        private const val CURRENT_BINS = 3

        private const val GRAVITY_TAU_S = 0.15f
        private const val MAX_DT_S = 0.1f
        private const val WARMUP_MS = 300L
        private const val NO_BIN = -1L
        private const val NO_TIMESTAMP = Long.MIN_VALUE
    }
}
