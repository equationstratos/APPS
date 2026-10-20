package com.survival.altimeter

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** One station-pressure reading (wall-clock time, hPa). */
data class PressureSample(val timeMs: Long, val hPa: Double)

/** 72 h rolling log of station pressure, stored as "time;hPa" lines. */
object PressureHistory {
    const val RETENTION_MS = 72L * 3_600_000L

    /** Foreground readings are one per minute; anything closer is a duplicate (worker and screen together). */
    const val MIN_INTERVAL_MS = 50_000L

    /** Readings stamped in the future (clock moved back) are dropped beyond this margin. */
    private const val FUTURE_TOLERANCE_MS = 10L * 60_000L

    fun parse(text: String): List<PressureSample> =
        text.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(';')
                if (parts.size != 2) return@mapNotNull null
                val time = parts[0].toLongOrNull() ?: return@mapNotNull null
                val hPa = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                if (time <= 0L || !isPlausibleStationPressure(hPa)) null else PressureSample(time, hPa)
            }
            .sortedBy { it.timeMs }
            .toList()

    fun serialize(samples: List<PressureSample>): String =
        samples.joinToString(separator = "\n", postfix = if (samples.isEmpty()) "" else "\n") {
            "${it.timeMs};${String.format(Locale.ROOT, "%.3f", it.hPa)}"
        }

    /** Keeps the last 72 h (relative to [nowMs]), sorted. */
    fun prune(samples: List<PressureSample>, nowMs: Long): List<PressureSample> =
        samples.filter { it.timeMs >= nowMs - RETENTION_MS && it.timeMs <= nowMs + FUTURE_TOLERANCE_MS }

    /** Inserts [sample] in time order unless another reading lies within [MIN_INTERVAL_MS]. Returns the same list when skipped. */
    fun append(samples: List<PressureSample>, sample: PressureSample): List<PressureSample> {
        if (!isPlausibleStationPressure(sample.hPa)) return samples
        if (samples.any { kotlin.math.abs(it.timeMs - sample.timeMs) < MIN_INTERVAL_MS }) return samples
        val index = samples.indexOfFirst { it.timeMs > sample.timeMs }.let { if (it < 0) samples.size else it }
        return samples.toMutableList().apply { add(index, sample) }
    }

    /** Splits the series where readings are missing for more than [maxGapMs], so the chart does not invent a line. */
    fun segments(samples: List<PressureSample>, maxGapMs: Long): List<List<PressureSample>> {
        val result = mutableListOf<List<PressureSample>>()
        var current = mutableListOf<PressureSample>()
        for (sample in samples) {
            val last = current.lastOrNull()
            if (last != null && sample.timeMs - last.timeMs > maxGapMs) {
                result.add(current)
                current = mutableListOf()
            }
            current.add(sample)
        }
        if (current.isNotEmpty()) result.add(current)
        return result
    }
}

/** Vertical axis of the pressure chart: round bounds, at least [MIN_SPAN_HPA] tall, at most 5 intervals. */
data class PressureAxis(val min: Double, val max: Double, val step: Double) {
    val ticks: List<Double>
        get() {
            val count = ((max - min) / step).toInt()
            return (0..count).map { min + it * step }
        }

    fun fraction(hPa: Double): Double = if (max > min) (hPa - min) / (max - min) else 0.5

    companion object {
        const val MIN_SPAN_HPA = 4.0
        private val STEPS = doubleArrayOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)

        fun of(values: List<Double>): PressureAxis? {
            val finite = values.filter { it.isFinite() }
            if (finite.isEmpty()) return null
            var low = finite.min()
            var high = finite.max()
            if (high - low < MIN_SPAN_HPA) {
                val middle = (low + high) / 2.0
                low = middle - MIN_SPAN_HPA / 2.0
                high = middle + MIN_SPAN_HPA / 2.0
            }
            for (step in STEPS) {
                val bottom = floor(low / step) * step
                val top = ceil(high / step) * step
                if ((top - bottom) / step <= 5.0 + 1e-9) return PressureAxis(bottom, top, step)
            }
            val step = STEPS.last()
            return PressureAxis(floor(low / step) * step, ceil(high / step) * step, step)
        }
    }
}
