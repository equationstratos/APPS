package com.survival.pulse

import kotlin.math.abs
import kotlin.math.sqrt

/** Two taps closer than this are one beat tapped twice (240 BPM). */
internal const val MIN_TAP_GAP_MS = 250L

/** Counting durations offered for the manual mode (s). */
internal val TAP_DURATIONS_S = listOf(15, 30)

/** Minimum regular intervals (5 taps) for a result. */
internal const val MIN_TAP_INTERVALS = 4

/** Intervals within this relative distance of the median are regular; others are missed or extra beats. */
private const val TAP_INLIER_TOLERANCE = 0.25

/** Above this share of irregular intervals, or this dispersion, the rhythm is reported as irregular. */
private const val MAX_IRREGULAR_SHARE = 0.2
private const val MAX_REGULAR_CV = 0.12

/** Rate computed from tapped beats. */
internal class TapRate(
    val bpm: Double,
    /** Intervals used for the rate. */
    val intervals: Int,
    /** Missed beats, extra taps or an irregular rhythm: the value is approximate. */
    val irregular: Boolean,
)

/**
 * Heart rate from tap times (ms, ascending): double taps are merged, intervals far from the
 * median (missed or extra beat) are ignored, and the rate is 60 / mean regular interval.
 */
internal fun tapRate(tapsMs: List<Long>, minIntervals: Int = 3): TapRate? {
    val beats = ArrayList<Long>(tapsMs.size)
    for (t in tapsMs) {
        if (beats.isEmpty() || t - beats.last() >= MIN_TAP_GAP_MS) beats.add(t)
    }
    if (beats.size < 2) return null
    val intervals = DoubleArray(beats.size - 1) { (beats[it + 1] - beats[it]).toDouble() }
    val med = median(intervals)
    val regular = intervals.filter { abs(it - med) <= TAP_INLIER_TOLERANCE * med }
    if (regular.size < minIntervals) return null
    val mean = regular.average()
    val variance = regular.sumOf { (it - mean) * (it - mean) } / regular.size
    val cv = sqrt(variance) / mean
    val irregularShare = 1.0 - regular.size.toDouble() / intervals.size
    return TapRate(
        bpm = 60_000.0 / mean,
        intervals = regular.size,
        irregular = irregularShare > MAX_IRREGULAR_SHARE || cv > MAX_REGULAR_CV,
    )
}

/**
 * One manual count: the timer starts at the first tap and taps are accepted until
 * [durationMs] later. Immutable, so it can live directly in Compose state.
 */
internal data class TapSession(val durationMs: Long, val taps: List<Long> = emptyList()) {
    val started: Boolean get() = taps.isNotEmpty()

    /** End of the count (same clock as the taps), or null before the first tap. */
    val deadlineMs: Long? get() = taps.firstOrNull()?.plus(durationMs)

    fun isFinished(nowMs: Long): Boolean = deadlineMs?.let { nowMs >= it } ?: false

    fun remainingMs(nowMs: Long): Long = deadlineMs?.let { (it - nowMs).coerceIn(0L, durationMs) } ?: durationMs

    fun progress(nowMs: Long): Float =
        if (!started) 0f else (1f - remainingMs(nowMs).toFloat() / durationMs).coerceIn(0f, 1f)

    fun tap(nowMs: Long): TapSession {
        if (isFinished(nowMs)) return this
        val last = taps.lastOrNull()
        if (last != null && nowMs < last) return this
        return copy(taps = taps + nowMs)
    }

    /** Taps that count as beats (double taps merged). */
    val beatCount: Int
        get() {
            var count = 0
            var last: Long? = null
            for (t in taps) {
                if (last == null || t - last >= MIN_TAP_GAP_MS) {
                    count++
                    last = t
                }
            }
            return count
        }

    /** Estimate shown while counting (3 regular intervals are enough). */
    fun liveRate(): TapRate? = tapRate(taps, minIntervals = 3)

    /** Final result, or null when too few beats were tapped. */
    fun finalRate(): TapRate? = tapRate(taps, minIntervals = MIN_TAP_INTERVALS)
}
