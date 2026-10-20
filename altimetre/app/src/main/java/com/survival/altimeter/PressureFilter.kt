package com.survival.altimeter

import kotlin.math.exp
import kotlin.math.max

/**
 * Low-pass filter for the raw barometer stream: exponential moving average whose weight depends on
 * the real time between samples (sensor rates differ between phones). While warming up it is a plain
 * running mean, so the first noisy sample does not bias the output.
 */
class PressureFilter(
    private val timeConstantS: Double = 4.0,
    private val maxGapS: Double = 5.0,
    private val settleS: Double = 3.0,
) {
    var value: Double = Double.NaN
        private set

    private var count = 0
    private var firstNanos = 0L
    private var lastNanos = 0L

    /** Enough samples have been averaged for the value to be trusted (altitude display, elevation gain). */
    val isSettled: Boolean
        get() = count >= MIN_SETTLED_SAMPLES && (lastNanos - firstNanos) >= (settleS * NANOS_PER_SECOND).toLong()

    fun reset() {
        value = Double.NaN
        count = 0
    }

    /** Adds one sample (sensor timestamp in nanoseconds) and returns the filtered pressure. */
    fun add(timestampNanos: Long, hPa: Double): Double {
        if (!isPlausibleStationPressure(hPa)) return value
        val dtS = (timestampNanos - lastNanos) / NANOS_PER_SECOND
        if (count == 0 || dtS < 0.0 || dtS > maxGapS) {
            value = hPa
            count = 1
            firstNanos = timestampNanos
            lastNanos = timestampNanos
            return value
        }
        count++
        val alpha = max(1.0 / count, 1.0 - exp(-dtS / timeConstantS))
        value += alpha * (hPa - value)
        lastNanos = timestampNanos
        return value
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MIN_SETTLED_SAMPLES = 5
    }
}

/** Median, robust to a stray first reading after the sensor wakes up. */
fun medianOrNull(values: List<Double>): Double? {
    val sorted = values.filter { it.isFinite() }.sorted()
    if (sorted.isEmpty()) return null
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
}
