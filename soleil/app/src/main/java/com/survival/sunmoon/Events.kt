package com.survival.sunmoon

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

private const val BISECTION_STEPS = 24
private const val GOLDEN_STEPS = 32
private val GOLDEN_RATIO = (sqrt(5.0) - 1.0) / 2.0

/** Instant (epoch seconds) where a curve crosses a level, upwards when [rising]. */
internal class Crossing(val time: Double, val rising: Boolean)

/** Time span in epoch seconds. */
internal class Span(val start: Double, val end: Double)

/**
 * A smooth function of time (altitude of a body) sampled on [start, end] every [step] seconds.
 * Level crossings are found between samples and refined by bisection; a short dip or peak
 * hidden between two samples is caught by refining each local extremum.
 */
internal class SampledCurve(
    private val f: (Double) -> Double,
    val start: Double,
    val end: Double,
    step: Double,
) {
    val times: DoubleArray
    val values: DoubleArray

    init {
        require(end > start && step > 0.0)
        val n = max(1, ceil((end - start) / step).toInt())
        times = DoubleArray(n + 1) { i -> if (i == n) end else start + i * step }
        values = DoubleArray(n + 1) { i -> f(times[i]) }
    }

    val maxValue: Double get() = values.max()
    val minValue: Double get() = values.min()

    /** Crossings of [level] with start <= time < end, in chronological order. */
    fun crossings(level: Double): List<Crossing> {
        val found = ArrayList<Crossing>()
        val last = times.size - 1
        for (i in 0 until last) {
            val aboveA = values[i] >= level
            val aboveB = values[i + 1] >= level
            if (aboveA != aboveB) {
                found += Crossing(bisect(times[i], times[i + 1], level, aboveA), rising = aboveB)
            }
        }
        for (i in 1 until last) {
            val v = values[i]
            val isMin = v < values[i - 1] && v <= values[i + 1]
            val isMax = v > values[i - 1] && v >= values[i + 1]
            val above = v >= level
            val sameSide = (values[i - 1] >= level) == above && (values[i + 1] >= level) == above
            // A minimum above the level (or a maximum below it) may still cross the level between samples.
            val towardLevel = (isMin && above) || (isMax && !above)
            if (!sameSide || !towardLevel) continue
            val tExt = extremum(times[i - 1], times[i + 1], minimum = isMin)
            if ((f(tExt) >= level) != above) {
                found += Crossing(bisect(times[i - 1], tExt, level, above), rising = !above)
                found += Crossing(bisect(tExt, times[i + 1], level, !above), rising = above)
            }
        }
        return found.filter { it.time >= start && it.time < end }.sortedBy { it.time }
    }

    /** Total time spent at or above [level]. */
    fun durationAbove(level: Double): Double {
        var above = values[0] >= level
        var since = start
        var total = 0.0
        for (c in crossings(level)) {
            if (above) total += c.time - since
            above = c.rising
            since = c.time
        }
        if (above) total += end - since
        return total
    }

    /** Spans where low <= value < high, clipped to [start, end]. */
    fun spansBetween(low: Double, high: Double): List<Span> {
        class Edge(val time: Double, val isHigh: Boolean, val rising: Boolean)

        val edges = crossings(low).map { Edge(it.time, false, it.rising) } +
            crossings(high).map { Edge(it.time, true, it.rising) }
        var aboveLow = values[0] >= low
        var aboveHigh = values[0] >= high
        var inside = aboveLow && !aboveHigh
        var since = start
        val spans = ArrayList<Span>()
        for (edge in edges.sortedBy { it.time }) {
            if (edge.isHigh) aboveHigh = edge.rising else aboveLow = edge.rising
            val now = aboveLow && !aboveHigh
            if (now && !inside) since = edge.time
            if (!now && inside && edge.time > since) spans += Span(since, edge.time)
            inside = now
        }
        if (inside && end > since) spans += Span(since, end)
        return spans
    }

    private fun bisect(t0: Double, t1: Double, level: Double, aboveAtT0: Boolean): Double {
        var lo = t0
        var hi = t1
        repeat(BISECTION_STEPS) {
            val mid = (lo + hi) / 2.0
            if ((f(mid) >= level) == aboveAtT0) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    /** Golden-section search of the extremum of [f] inside [a0, b0]. */
    private fun extremum(a0: Double, b0: Double, minimum: Boolean): Double {
        val sign = if (minimum) 1.0 else -1.0
        var a = a0
        var b = b0
        var c = b - GOLDEN_RATIO * (b - a)
        var d = a + GOLDEN_RATIO * (b - a)
        var fc = sign * f(c)
        var fd = sign * f(d)
        repeat(GOLDEN_STEPS) {
            if (fc < fd) {
                b = d
                d = c
                fd = fc
                c = b - GOLDEN_RATIO * (b - a)
                fc = sign * f(c)
            } else {
                a = c
                c = d
                fc = fd
                d = a + GOLDEN_RATIO * (b - a)
                fd = sign * f(d)
            }
        }
        return (a + b) / 2.0
    }
}
