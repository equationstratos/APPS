package com.survival.pulse

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Heart rate from a photoplethysmogram (PPG): the mean brightness of a fingertip lit by the flash
 * dips slightly at each heartbeat, when more blood fills the capillaries.
 *
 * Pipeline: uniform resampling (the camera frame rate varies) -> linear detrend -> zero-phase
 * band-pass -> robust clipping -> autocorrelation period + peak detection -> cross-check and
 * quality score. Pure Kotlin so that it can be tested on the JVM with synthetic signals; it was
 * also checked on real fingertip recordings (ECG reference) turned into camera-like frames.
 */

/** Uniform rate the camera samples are interpolated to before filtering (Hz). */
internal const val ANALYSIS_RATE_HZ = 30.0

/** Heart-rate band searched by the analysis (BPM). The filter passband is 0,7 to 3,5 Hz (42 to 210 BPM). */
internal const val MIN_BPM = 40.0
internal const val MAX_BPM = 220.0

/** Shortest signal worth analysing (s). */
internal const val MIN_ANALYSIS_S = 6.0

/** Butterworth corners, set slightly outside 0,7 to 3,5 Hz so that the band edges are barely attenuated. */
private const val HIGH_PASS_HZ = 0.5
private const val LOW_PASS_HZ = 4.0

/** Edge padding (odd reflection) before forward-backward filtering, long enough for the transients to die out. */
private const val FILTER_PADDING_S = 3.0

/** Samples beyond this many robust standard deviations (movement, light leak) are clipped. */
private const val CLIP_SIGMAS = 4.0

/**
 * A periodic signal also correlates at 2, 3... periods and noise can make a multiple the best
 * autocorrelation peak. A shorter lag is the period when every multiple of it up to the best lag
 * correlates at least this share of the best peak. Half a real pulse period correlates negatively
 * (real recordings: at most 0,26 of the full period when positive), so slow rates are not doubled.
 */
private const val COMB_TOLERANCE = 0.5
private const val MAX_COMB_MULTIPLE = 5

/** Minimum distance between two beats, as a share of the autocorrelation period, and in absolute terms. */
private const val REFRACTORY_FRACTION = 0.6
private const val MIN_REFRACTORY_S = 0.25

/** Peaks smaller than this share of the median peak height are noise or the dicrotic wave. */
private const val MIN_RELATIVE_PEAK_HEIGHT = 0.35

/**
 * A peak that splits one beat interval into two short ones (each below this share of the median
 * interval) while being clearly smaller than its neighbours is noise inside a long interval.
 */
private const val SPLIT_INTERVAL = 0.7
private const val SPLIT_HEIGHT = 0.7

/** An interval longer than this many median intervals is a dropout rather than missed beats. */
private const val MAX_BEATS_PER_INTERVAL = 3

/** Beat intervals within this relative distance of the median interval count as regular. */
private const val INLIER_TOLERANCE = 0.2

/** Scale factor turning a median absolute deviation into a standard deviation for Gaussian noise. */
private const val MAD_TO_SIGMA = 1.4826

/** Second-order IIR section (direct form II transposed), coefficients normalised by a0. */
internal class Biquad(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    /** Filters [x], starting from the steady state reached with a constant input equal to x[0]. */
    fun filter(x: DoubleArray): DoubleArray {
        val y = DoubleArray(x.size)
        if (x.isEmpty()) return y
        val x0 = x[0]
        val y0 = x0 * (b0 + b1 + b2) / (1.0 + a1 + a2)
        var z2 = b2 * x0 - a2 * y0
        var z1 = b1 * x0 - a1 * y0 + z2
        for (i in x.indices) {
            val xi = x[i]
            val yi = b0 * xi + z1
            z1 = b1 * xi - a1 * yi + z2
            z2 = b2 * xi - a2 * yi
            y[i] = yi
        }
        return y
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.7071067811865476

        /** Second-order Butterworth low-pass (bilinear transform with pre-warping). */
        fun lowPass(cutoffHz: Double, rateHz: Double): Biquad {
            val w0 = 2.0 * PI * cutoffHz / rateHz
            val c = cos(w0)
            val alpha = sin(w0) / (2.0 * BUTTERWORTH_Q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = (1.0 - c) / 2.0 / a0,
                b1 = (1.0 - c) / a0,
                b2 = (1.0 - c) / 2.0 / a0,
                a1 = -2.0 * c / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        /** Second-order Butterworth high-pass (bilinear transform with pre-warping). */
        fun highPass(cutoffHz: Double, rateHz: Double): Biquad {
            val w0 = 2.0 * PI * cutoffHz / rateHz
            val c = cos(w0)
            val alpha = sin(w0) / (2.0 * BUTTERWORTH_Q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = (1.0 + c) / 2.0 / a0,
                b1 = -(1.0 + c) / a0,
                b2 = (1.0 + c) / 2.0 / a0,
                a1 = -2.0 * c / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }
    }
}

/**
 * Forward-backward filtering through [sections] (zero phase, squared magnitude response), with
 * odd-reflection padding of [padding] samples at both ends to limit edge transients.
 */
internal fun zeroPhaseFilter(x: DoubleArray, sections: List<Biquad>, padding: Int): DoubleArray {
    val n = x.size
    if (n < 2) return x.copyOf()
    val pad = padding.coerceIn(0, n - 1)
    val extended = DoubleArray(n + 2 * pad)
    x.copyInto(extended, destinationOffset = pad)
    for (k in 1..pad) {
        extended[pad - k] = 2.0 * x[0] - x[k]
        extended[pad + n - 1 + k] = 2.0 * x[n - 1] - x[n - 1 - k]
    }
    var y = extended
    for (section in sections) y = section.filter(y)
    y.reverse()
    for (section in sections) y = section.filter(y)
    y.reverse()
    return y.copyOfRange(pad, pad + n)
}

/** Band-pass used for the pulse: Butterworth high-pass then low-pass, applied forward and backward. */
internal fun pulseBandPass(x: DoubleArray, rateHz: Double): DoubleArray = zeroPhaseFilter(
    x,
    listOf(Biquad.highPass(HIGH_PASS_HZ, rateHz), Biquad.lowPass(LOW_PASS_HZ, rateHz)),
    padding = (FILTER_PADDING_S * rateHz).roundToInt(),
)

/**
 * Linear interpolation of ([times] in seconds, strictly increasing) onto a uniform grid at
 * [rateHz], starting at the first timestamp.
 */
internal fun resampleUniform(times: DoubleArray, values: DoubleArray, rateHz: Double): DoubleArray {
    val n = minOf(times.size, values.size)
    if (n < 2) return DoubleArray(0)
    val t0 = times[0]
    val count = floor((times[n - 1] - t0) * rateHz).toInt() + 1
    val out = DoubleArray(count)
    var j = 0
    for (k in 0 until count) {
        val t = t0 + k / rateHz
        while (j < n - 2 && times[j + 1] < t) j++
        val span = times[j + 1] - times[j]
        val f = if (span > 0.0) ((t - times[j]) / span).coerceIn(0.0, 1.0) else 0.0
        out[k] = values[j] + (values[j + 1] - values[j]) * f
    }
    return out
}

/** Removes the least-squares straight line (offset and slow drift such as auto-exposure or warming). */
internal fun detrendLinear(x: DoubleArray): DoubleArray {
    val n = x.size
    if (n < 2) return DoubleArray(n)
    val meanIndex = (n - 1) / 2.0
    val meanValue = x.average()
    var num = 0.0
    var den = 0.0
    for (i in x.indices) {
        val di = i - meanIndex
        num += di * (x[i] - meanValue)
        den += di * di
    }
    val slope = num / den
    return DoubleArray(n) { x[it] - meanValue - slope * (it - meanIndex) }
}

internal fun median(values: DoubleArray): Double {
    if (values.isEmpty()) return Double.NaN
    val sorted = values.sortedArray()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/** Standard deviation estimated from the median absolute value (insensitive to a few large artefacts). */
internal fun robustScale(x: DoubleArray): Double =
    MAD_TO_SIGMA * median(DoubleArray(x.size) { abs(x[it]) })

/** Vertex offset (-0,5..0,5) of the parabola through three equally spaced points around a maximum. */
internal fun parabolicOffset(left: Double, center: Double, right: Double): Double {
    val den = left - 2.0 * center + right
    if (abs(den) < 1e-12) return 0.0
    return (0.5 * (left - right) / den).coerceIn(-0.5, 0.5)
}

/** Normalised correlation between [x] and itself shifted by [lag] samples (1 for a perfect repeat). */
internal fun normalizedCorrelation(x: DoubleArray, lag: Int): Double {
    var sxy = 0.0
    var sxx = 0.0
    var syy = 0.0
    for (i in 0 until x.size - lag) {
        val a = x[i]
        val b = x[i + lag]
        sxy += a * b
        sxx += a * a
        syy += b * b
    }
    val den = sqrt(sxx * syy)
    return if (den > 0.0) sxy / den else 0.0
}

/** Main period of a zero-mean signal and how strongly it repeats (autocorrelation, 0..1). */
internal class Periodicity(val periodS: Double, val strength: Double)

internal fun dominantPeriod(x: DoubleArray, rateHz: Double): Periodicity? {
    val lagLo = floor(rateHz * 60.0 / MAX_BPM).toInt().coerceAtLeast(2)
    val lagHi = ceil(rateHz * 60.0 / MIN_BPM).toInt()
    if (x.size < 2 * (lagHi + 2)) return null
    val r = DoubleArray(lagHi + 2)
    for (lag in (lagLo - 1)..(lagHi + 1)) r[lag] = normalizedCorrelation(x, lag)
    val candidates = (lagLo..lagHi).filter { lag -> r[lag] > 0.0 && r[lag] > r[lag - 1] && r[lag] >= r[lag + 1] }
    if (candidates.isEmpty()) return null
    val bestLag = candidates.maxBy { r[it] }
    val floor = COMB_TOLERANCE * r[bestLag]
    val lag = candidates.first { lag -> lag == bestLag || isComb(r, candidates, lag, bestLag, floor) }
    val offset = parabolicOffset(r[lag - 1], r[lag], r[lag + 1])
    return Periodicity(periodS = (lag + offset) / rateHz, strength = r[lag].coerceIn(0.0, 1.0))
}

/**
 * True when [bestLag] is about k times [lag] (k from 2 to [MAX_COMB_MULTIPLE]) and the
 * autocorrelation has a peak of at least [floor] near each multiple of [lag] up to [bestLag].
 */
private fun isComb(r: DoubleArray, candidates: List<Int>, lag: Int, bestLag: Int, floor: Double): Boolean {
    val ratio = bestLag.toDouble() / lag
    val k = ratio.roundToInt()
    if (k < 2 || k > MAX_COMB_MULTIPLE || abs(ratio - k) > 0.1 * k) return false
    val step = bestLag.toDouble() / k
    for (j in 1..k) {
        val target = j * step
        val tolerance = max(1.0, 0.1 * target)
        val peak = candidates.filter { abs(it - target) <= tolerance }.maxByOrNull { r[it] } ?: return false
        if (r[peak] < floor) return false
    }
    return true
}

/**
 * Beat positions (fractional sample indices, ascending): positive local maxima kept from the
 * highest down, at least [minDistance] samples apart, then small ones discarded.
 */
internal fun findPeaks(x: DoubleArray, minDistance: Int): DoubleArray {
    val candidates = ArrayList<Int>()
    for (i in 1 until x.size - 1) {
        if (x[i] > 0.0 && x[i] > x[i - 1] && x[i] >= x[i + 1]) candidates.add(i)
    }
    val accepted = ArrayList<Int>()
    for (i in candidates.sortedByDescending { x[it] }) {
        if (accepted.none { abs(it - i) < minDistance }) accepted.add(i)
    }
    if (accepted.isEmpty()) return DoubleArray(0)
    val medianHeight = median(DoubleArray(accepted.size) { x[accepted[it]] })
    val kept = accepted.filter { x[it] >= MIN_RELATIVE_PEAK_HEIGHT * medianHeight }.sorted()
    return DoubleArray(kept.size) { k ->
        val i = kept[k]
        i + parabolicOffset(x[i - 1], x[i], x[i + 1])
    }
}

/**
 * Removes peaks that cut one interval into two short ones while being clearly smaller than both
 * neighbours (noise or a strong dicrotic wave inside a long interval). [peaks] are ascending
 * fractional indices into [x].
 */
internal fun dropSplitPeaks(x: DoubleArray, peaks: DoubleArray): DoubleArray {
    if (peaks.size < 4) return peaks
    val kept = peaks.toMutableList()
    val med = median(DoubleArray(kept.size - 1) { kept[it + 1] - kept[it] })
    fun height(position: Double) = x[position.roundToInt().coerceIn(0, x.size - 1)]
    var k = 1
    while (k < kept.size - 1) {
        val before = kept[k] - kept[k - 1]
        val after = kept[k + 1] - kept[k]
        val neighbours = 0.5 * (height(kept[k - 1]) + height(kept[k + 1]))
        if (before < SPLIT_INTERVAL * med && after < SPLIT_INTERVAL * med && height(kept[k]) < SPLIT_HEIGHT * neighbours) {
            kept.removeAt(k)
        } else {
            k++
        }
    }
    return kept.toDoubleArray()
}

/**
 * Beat-to-beat intervals summary. [regularity] is the share of intervals close to the median.
 * [bpm] is the mean rate between the first and last beat: an interval about n times the median
 * counts as n beats (missed beats) and dropouts are left out. Unlike a mean of the "regular"
 * intervals only, it keeps the long intervals of the breathing rhythm (sinus arrhythmia), which
 * would otherwise bias the rate upwards.
 */
internal class IntervalStats(
    val medianS: Double,
    val regularity: Double,
    val count: Int,
    val bpm: Double,
)

internal fun intervalStats(peaks: DoubleArray, rateHz: Double): IntervalStats? {
    if (peaks.size < 3) return null
    val intervals = DoubleArray(peaks.size - 1) { (peaks[it + 1] - peaks[it]) / rateHz }
    val med = median(intervals)
    if (!(med > 0.0)) return null
    var beats = 0
    var time = 0.0
    var regular = 0
    for (interval in intervals) {
        if (abs(interval - med) <= INLIER_TOLERANCE * med) regular++
        val n = (interval / med).roundToInt().coerceAtLeast(1)
        if (n > MAX_BEATS_PER_INTERVAL) continue
        beats += n
        time += interval
    }
    if (!(time > 0.0)) return null
    return IntervalStats(
        medianS = med,
        regularity = regular.toDouble() / intervals.size,
        count = intervals.size,
        bpm = 60.0 * beats / time,
    )
}

/** Result of one analysis. [bpm] comes from the detected beats; [periodicBpm] from the autocorrelation. */
internal class PulseEstimate(
    val bpm: Double,
    val quality: Double,
    val reliable: Boolean,
    val beats: Int,
    val periodicBpm: Double,
    val periodicity: Double,
    val regularity: Double,
    val methodGap: Double,
) {
    /** Good enough to be shown as a live estimate (looser than [reliable], used for the final result). */
    val showable: Boolean get() = quality >= 0.5 && methodGap <= 0.1 && bpm in MIN_BPM..MAX_BPM
}

internal object PulseAnalyzer {

    /** Analyses camera samples ([times] in seconds, [values] = brightness, any frame rate). */
    fun analyze(times: DoubleArray, values: DoubleArray): PulseEstimate? {
        val n = minOf(times.size, values.size)
        val t = ArrayList<Double>(n)
        val v = ArrayList<Double>(n)
        for (i in 0 until n) {
            if (!times[i].isFinite() || !values[i].isFinite()) continue
            if (t.isNotEmpty() && times[i] <= t.last()) continue
            t.add(times[i])
            v.add(values[i])
        }
        if (t.size < 2 || t.last() - t.first() < MIN_ANALYSIS_S) return null
        val uniform = resampleUniform(t.toDoubleArray(), v.toDoubleArray(), ANALYSIS_RATE_HZ)
        return analyzeUniform(uniform, ANALYSIS_RATE_HZ)
    }

    /** Analyses a brightness signal sampled at a constant [rateHz]. */
    fun analyzeUniform(signal: DoubleArray, rateHz: Double): PulseEstimate? {
        if (signal.size < MIN_ANALYSIS_S * rateHz) return null
        val filtered = pulseBandPass(detrendLinear(signal), rateHz)
        val scale = robustScale(filtered)
        if (!(scale > 1e-9)) return null
        val limit = CLIP_SIGMAS * scale
        // Brightness falls when blood volume rises: inverting puts the sharp systolic peaks on top.
        val pulse = DoubleArray(filtered.size) { (-filtered[it]).coerceIn(-limit, limit) }
        val period = dominantPeriod(pulse, rateHz) ?: return null
        val periodicBpm = 60.0 / period.periodS
        val minDistance = max(REFRACTORY_FRACTION * period.periodS, MIN_REFRACTORY_S) * rateHz

        // Systolic peaks or pulse feet, whichever gives the more regular beat sequence (depending on
        // the finger and the channel, one of the two can be blunted by a strong dicrotic wave).
        val inverted = DoubleArray(pulse.size) { -pulse[it] }
        var bestPeaks = DoubleArray(0)
        var bestStats: IntervalStats? = null
        for (candidate in listOf(pulse, inverted)) {
            val peaks = dropSplitPeaks(candidate, findPeaks(candidate, minDistance.roundToInt()))
            val stats = intervalStats(peaks, rateHz) ?: continue
            val current = bestStats
            if (current == null || stats.regularity > current.regularity + 1e-9) {
                bestPeaks = peaks
                bestStats = stats
            }
        }
        val stats = bestStats ?: return null

        val bpm = stats.bpm
        val methodGap = abs(bpm - periodicBpm) / periodicBpm
        val durationS = signal.size / rateHz
        val expectedBeats = durationS * periodicBpm / 60.0
        val coverage = bestPeaks.size / expectedBeats

        val periodicScore = ((period.strength - 0.25) / 0.5).coerceIn(0.0, 1.0)
        val regularityScore = ((stats.regularity - 0.5) / 0.4).coerceIn(0.0, 1.0)
        val agreementScore = (1.0 - (methodGap - 0.03) / 0.09).coerceIn(0.0, 1.0)
        val quality = 0.4 * periodicScore + 0.4 * regularityScore + 0.2 * agreementScore

        val reliable = quality >= 0.6 &&
            period.strength >= 0.4 &&
            stats.regularity >= 0.7 &&
            methodGap <= 0.08 &&
            coverage in 0.6..1.4 &&
            bpm in MIN_BPM..MAX_BPM
        return PulseEstimate(
            bpm = bpm,
            quality = quality,
            reliable = reliable,
            beats = bestPeaks.size,
            periodicBpm = periodicBpm,
            periodicity = period.strength,
            regularity = stats.regularity,
            methodGap = methodGap,
        )
    }

    /** Best of several channels (e.g. luma and red): reliable first, then highest quality. */
    fun best(estimates: List<PulseEstimate?>): PulseEstimate? =
        estimates.filterNotNull().maxWithOrNull(compareBy<PulseEstimate>({ it.reliable }, { it.quality }))
}
