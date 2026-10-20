package com.survival.pulse

import kotlin.math.exp
import kotlin.math.roundToInt

/** Length of the camera measurement once the finger is steady (s). */
internal const val MEASURE_DURATION_S = 30.0

/** Settling time after the finger is detected, while auto-exposure adapts (s). */
internal const val STABILIZATION_S = 2.0

/** A live estimate is shown from this much recorded signal on (s). */
internal const val LIVE_START_S = 8.0

/** Window of the live estimate and time between two live updates (s). */
private const val LIVE_WINDOW_S = 12.0
private const val LIVE_INTERVAL_S = 1.0

/** Debounce of the finger detection, so that a single odd frame neither starts nor cancels a measurement. */
private const val FINGER_ON_DELAY_S = 0.3
private const val FINGER_OFF_DELAY_S = 0.4

/** A hole this long in the frame sequence (camera stalled) restarts the measurement (s). */
private const val MAX_FRAME_GAP_S = 1.0

/** Length of the trace drawn on screen (s). */
internal const val TRACE_WINDOW_S = 5.0

/** Finger detection thresholds on the mean YCbCr of the central region (0-255). */
private const val MIN_LUMA = 15.0
private const val MIN_CR = 145.0
private const val MIN_CR_OVER_CB = 30.0
private const val MAX_SPREAD_ABS = 10.0
private const val MAX_SPREAD_REL = 0.3

private const val NANOS = 1_000_000_000.0

/** Mean colour of the central part of one camera frame (YCbCr, 0-255) and its sensor timestamp. */
internal class FrameStats(
    val timestampNs: Long,
    val luma: Double,
    val lumaSpread: Double,
    val cb: Double,
    val cr: Double,
) {
    /** Red level rebuilt from luma and red-difference chroma (ITU-R BT.601). */
    val red: Double get() = luma + 1.402 * (cr - 128.0)
}

internal enum class FingerStatus { PRESENT, ABSENT, TOO_DARK }

/**
 * A fingertip over the lens gives a red, fairly bright and blurred (uniform) image; a scene has
 * neutral colours and contrast; a covered lens without light is black.
 */
internal fun classifyFrame(stats: FrameStats): FingerStatus = when {
    stats.luma < MIN_LUMA -> FingerStatus.TOO_DARK
    stats.cr >= MIN_CR &&
        stats.cr - stats.cb >= MIN_CR_OVER_CB &&
        stats.lumaSpread <= MAX_SPREAD_ABS + MAX_SPREAD_REL * stats.luma -> FingerStatus.PRESENT
    else -> FingerStatus.ABSENT
}

internal enum class Phase { WAITING, STABILIZING, MEASURING, DONE, FAILED }

/** Points of the on-screen trace: [x] from 0 (oldest) to 1 (now), [y] scaled to -1..1. */
internal class Wave(val x: FloatArray, val y: FloatArray)

/** Immutable view of a [PulseSession] for the UI thread. */
internal class SessionSnapshot(
    val phase: Phase,
    val finger: FingerStatus,
    /** A measurement was cancelled because the finger moved away. */
    val interrupted: Boolean,
    val elapsedS: Double,
    val progress: Float,
    val liveBpm: Int?,
    val quality: Double?,
    val result: PulseEstimate?,
    val wave: Wave?,
)

/** Growable pair of time / value arrays without boxing. */
private class Samples {
    var times = DoubleArray(1024)
    var luma = DoubleArray(1024)
    var red = DoubleArray(1024)
    var size = 0
        private set

    fun clear() {
        size = 0
    }

    fun add(t: Double, y: Double, r: Double) {
        if (size == times.size) {
            times = times.copyOf(size * 2)
            luma = luma.copyOf(size * 2)
            red = red.copyOf(size * 2)
        }
        times[size] = t
        luma[size] = y
        red[size] = r
        size++
    }

    /** Index of the first sample at or after [t]. */
    fun indexFrom(t: Double): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < t) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

/**
 * Display trace: brightness minus a slow moving baseline, lightly smoothed and inverted so that
 * beats point up. Time-aware, so it does not depend on the frame rate.
 */
private class Trace {
    private val capacity = 512
    private val times = LongArray(capacity)
    private val values = FloatArray(capacity)
    private var head = 0
    private var count = 0
    private var primed = false
    private var lastNs = 0L
    private var baseline = 0.0
    private var smooth = 0.0

    fun reset() {
        head = 0
        count = 0
        primed = false
    }

    fun add(tNs: Long, value: Double) {
        if (!primed) {
            primed = true
            baseline = value
            smooth = 0.0
            lastNs = tNs
        }
        val dt = (tNs - lastNs).coerceAtLeast(0L) / NANOS
        lastNs = tNs
        baseline += (1.0 - exp(-dt / BASELINE_TAU_S)) * (value - baseline)
        smooth += (1.0 - exp(-dt / SMOOTH_TAU_S)) * ((baseline - value) - smooth)
        times[head] = tNs
        values[head] = smooth.toFloat()
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    fun wave(nowNs: Long): Wave? {
        val windowNs = (TRACE_WINDOW_S * NANOS).toLong()
        val xs = ArrayList<Float>(count)
        val ys = ArrayList<Float>(count)
        for (k in count downTo 1) {
            val index = (head - k + capacity) % capacity
            val age = nowNs - times[index]
            if (age > windowNs || age < 0L) continue
            xs.add(1f - age.toFloat() / windowNs)
            ys.add(values[index])
        }
        if (xs.size < 2) return null
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (y in ys) {
            if (y < min) min = y
            if (y > max) max = y
        }
        val range = max - min
        val y = FloatArray(ys.size) { if (range > 1e-6f) 2f * (ys[it] - min) / range - 1f else 0f }
        return Wave(xs.toFloatArray(), y)
    }

    private companion object {
        const val BASELINE_TAU_S = 1.0
        const val SMOOTH_TAU_S = 0.05
    }
}

/**
 * Camera measurement: waits for the finger, lets the exposure settle, records [durationS] seconds,
 * shows a live estimate along the way and ends with a reliable result or a failure. Removing the
 * finger restarts it. Not thread-safe: feed it from a single thread.
 */
internal class PulseSession(
    private val durationS: Double = MEASURE_DURATION_S,
    private val stabilizationS: Double = STABILIZATION_S,
) {
    private var phase = Phase.WAITING
    private var fingerOn = false
    private var offStatus = FingerStatus.ABSENT
    private var pendingSinceNs: Long? = null
    private var interrupted = false
    private var hasFrame = false
    private var lastNs = 0L
    private var stabilizeUntilNs = 0L
    private var startNs = 0L
    private var nextLiveNs = 0L
    private var liveBpm: Int? = null
    private var liveQuality: Double? = null
    private var result: PulseEstimate? = null
    private val samples = Samples()
    private val trace = Trace()

    val isFinished: Boolean get() = phase == Phase.DONE || phase == Phase.FAILED

    fun onFrame(stats: FrameStats) {
        if (isFinished) return
        val t = stats.timestampNs
        if (hasFrame && t <= lastNs) return
        if (hasFrame && (t - lastNs) / NANOS > MAX_FRAME_GAP_S) {
            // Frames stopped coming (camera stalled): what was recorded no longer forms one signal.
            fingerOn = false
            pendingSinceNs = null
            leaveMeasurement()
        }
        hasFrame = true
        lastNs = t

        val raw = classifyFrame(stats)
        updateFinger(raw, t)
        if (fingerOn) trace.add(t, stats.luma)

        when (phase) {
            Phase.STABILIZING -> if (t >= stabilizeUntilNs) {
                phase = Phase.MEASURING
                startNs = t
                nextLiveNs = t + (LIVE_START_S * NANOS).toLong()
                samples.clear()
            }
            Phase.MEASURING -> {
                // Frames that do not look like a finger (during the off debounce) are left out and bridged.
                if (raw == FingerStatus.PRESENT) samples.add((t - startNs) / NANOS, stats.luma, stats.red)
                if (t >= nextLiveNs) {
                    nextLiveNs = t + (LIVE_INTERVAL_S * NANOS).toLong()
                    updateLive((t - startNs) / NANOS)
                }
                if ((t - startNs) / NANOS >= durationS) finish()
            }
            else -> Unit
        }
    }

    fun snapshot(): SessionSnapshot {
        val elapsed = if (phase == Phase.MEASURING) ((lastNs - startNs) / NANOS).coerceAtLeast(0.0) else 0.0
        val progress = when (phase) {
            Phase.MEASURING -> (elapsed / durationS).toFloat().coerceIn(0f, 1f)
            Phase.DONE, Phase.FAILED -> 1f
            else -> 0f
        }
        val showTrace = fingerOn && (phase == Phase.STABILIZING || phase == Phase.MEASURING)
        return SessionSnapshot(
            phase = phase,
            finger = if (fingerOn) FingerStatus.PRESENT else offStatus,
            interrupted = interrupted,
            elapsedS = elapsed,
            progress = progress,
            liveBpm = liveBpm,
            quality = if (isFinished) result?.quality else liveQuality,
            result = result,
            wave = if (showTrace) trace.wave(lastNs) else null,
        )
    }

    private fun updateFinger(raw: FingerStatus, t: Long) {
        val wantOn = raw == FingerStatus.PRESENT
        if (!wantOn) offStatus = raw
        if (wantOn == fingerOn) {
            pendingSinceNs = null
            return
        }
        val since = pendingSinceNs ?: t.also { pendingSinceNs = it }
        val delayS = if (wantOn) FINGER_ON_DELAY_S else FINGER_OFF_DELAY_S
        if ((t - since) / NANOS < delayS) return
        pendingSinceNs = null
        fingerOn = wantOn
        if (wantOn) {
            if (phase == Phase.WAITING) {
                interrupted = false
                phase = Phase.STABILIZING
                stabilizeUntilNs = t + (stabilizationS * NANOS).toLong()
                trace.reset()
            }
        } else {
            leaveMeasurement()
        }
    }

    /** Back to waiting for the finger; a measurement in progress is discarded. */
    private fun leaveMeasurement() {
        when (phase) {
            Phase.MEASURING -> interrupted = true
            Phase.STABILIZING -> Unit
            else -> return
        }
        phase = Phase.WAITING
        samples.clear()
        liveBpm = null
        liveQuality = null
        trace.reset()
    }

    private fun updateLive(nowS: Double) {
        val from = samples.indexFrom(nowS - LIVE_WINDOW_S)
        val estimate = estimate(from, samples.size)
        liveQuality = estimate?.quality ?: 0.0
        liveBpm = estimate?.takeIf { it.showable }?.bpm?.roundToInt()
    }

    private fun finish() {
        val estimate = estimate(0, samples.size)
        result = estimate
        phase = if (estimate != null && estimate.reliable) Phase.DONE else Phase.FAILED
        interrupted = false
    }

    private fun estimate(from: Int, to: Int): PulseEstimate? {
        if (to - from < 2) return null
        val times = samples.times.copyOfRange(from, to)
        return PulseAnalyzer.best(
            listOf(
                PulseAnalyzer.analyze(times, samples.luma.copyOfRange(from, to)),
                PulseAnalyzer.analyze(times, samples.red.copyOfRange(from, to)),
            )
        )
    }
}
