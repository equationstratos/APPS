package com.survival.tuner

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** Lowest fundamental reported (Hz): the bass Mi1 (41,2 Hz) stays well inside the range. */
const val MIN_FREQUENCY = 30.0

/** Highest fundamental reported (Hz). */
const val MAX_FREQUENCY = 1500.0

/** McLeod's key-maximum threshold: the first peak reaching 90 % of the highest one is the period. */
private const val PEAK_THRESHOLD = 0.9

/** Successive step sizes (samples) of the sub-sample refinement around the NSDF peak. */
private val REFINE_STEPS = doubleArrayOf(0.1, 0.01)

/** Search radius around the detected period on the refinement band (fraction of the period). */
private const val SEARCH_FRACTION = 0.03

/** Lag used for the multi-period refinement, as a fraction of the window. */
private const val MULTI_PERIOD_SPAN = 2.0 / 3.0

/** Largest relative change the multi-period step may bring (otherwise the note is unstable). */
private const val MAX_MULTI_PERIOD_DRIFT = 0.005

/** Minimum NSDF height of a peak used for refinement. */
private const val MIN_REFINE_CLARITY = 0.5

/** A detected fundamental with its clarity (NSDF peak height: 1 = perfectly periodic, 0 = noise). */
class PitchEstimate(val frequency: Double, val clarity: Double)

/** A key maximum of the normalised square difference function: period (samples) and height. */
internal class NsdfPeak(val lag: Double, val clarity: Double)

/**
 * McLeod Pitch Method ("A smarter way to find pitch", McLeod & Wyvill, 2005) on a fixed window.
 *
 * The NSDF n(τ) = 2·r(τ) / m(τ) compares the window with itself shifted by τ: r is the
 * autocorrelation and m the energy of the overlapping parts, so n(τ) ≤ 1 with equality exactly at
 * the period, whatever the window length or the harmonic content. A missing or weak fundamental
 * therefore does not move the peak, and taking the first key maximum above 90 % of the highest
 * one avoids both octave-up (harmonic) and octave-down (multiple period) errors.
 *
 * Not thread-safe: buffers are reused between calls.
 */
internal class PitchDetector(private val sampleRate: Int, private val windowSize: Int) {
    private val fftSize: Int
    private val fft: Fft
    private val re: DoubleArray
    private val im: DoubleArray
    private val power: DoubleArray
    private val prefix = DoubleArray(windowSize + 1)
    private val maxLag: Int
    private val nsdf: DoubleArray
    private val peakLags: DoubleArray
    private val peakValues: DoubleArray
    private val acf = DoubleArray(windowSize)

    init {
        var size = 2
        while (size < 2 * windowSize) size *= 2
        fftSize = size
        fft = Fft(size)
        re = DoubleArray(size)
        im = DoubleArray(size)
        power = DoubleArray(size / 2 + 1)
        maxLag = minOf(ceil(sampleRate / MIN_FREQUENCY).toInt() + 2, windowSize / 2)
        nsdf = DoubleArray(maxLag + 2)
        peakLags = DoubleArray(maxLag)
        peakValues = DoubleArray(maxLag)
    }

    /** Period of [x] (first [windowSize] samples) by the McLeod key-maximum rule, or null. */
    fun findPeriod(x: DoubleArray): NsdfPeak? {
        val total = fillPrefix(x)
        if (total <= 0.0) return null
        spectrum(x)
        // Autocorrelation = inverse transform of the power spectrum (zero padding: no wrap-around).
        val half = fftSize / 2
        for (k in 0 until fftSize) {
            re[k] = power[if (k <= half) k else fftSize - k]
            im[k] = 0.0
        }
        fft.inverse(re, im)
        val floor = total * 1e-9
        for (tau in 0..maxLag + 1) {
            val m = energy(tau, total)
            nsdf[tau] = if (m > floor) 2.0 * re[tau] / m else 0.0
        }

        // Key maxima: the highest point between each upward and the next downward zero crossing,
        // after the central lobe around τ = 0.
        var count = 0
        var tau = 1
        while (tau <= maxLag && nsdf[tau] > 0.0) tau++
        var best = -1
        while (tau <= maxLag) {
            if (nsdf[tau] > 0.0) {
                if (best < 0 || nsdf[tau] > nsdf[best]) best = tau
            } else if (best >= 0) {
                count = addPeak(best, count)
                best = -1
            }
            tau++
        }
        // A region still open at the end counts only if its maximum is a real local maximum.
        if (best in 1 until maxLag) count = addPeak(best, count)
        if (count == 0) return null

        var highest = 0.0
        for (i in 0 until count) highest = max(highest, peakValues[i])
        if (highest <= 0.0) return null
        val threshold = PEAK_THRESHOLD * highest
        for (i in 0 until count) {
            if (peakValues[i] >= threshold) return NsdfPeak(peakLags[i], peakValues[i].coerceAtMost(1.0))
        }
        return null
    }

    /**
     * Refines a period [guess] (samples) on [y], usually a low-passed copy of the analysed window:
     * removing the noise above the useful harmonics makes the peak position far more stable.
     *
     * The peak is first located near one period, then near the multiple of the period closest to
     * 2/3 of the window: an error of δ samples on k periods is only δ/k on the period, and 2/3 is
     * the best balance with the shrinking overlap. Between samples, the NSDF is evaluated by
     * band-limited (trigonometric) interpolation of the autocorrelation and its maximum is found
     * by successive parabolic fits on finer steps. Returns [guess] when no clear peak is found.
     */
    fun refine(y: DoubleArray, guess: Double): Double {
        val total = fillPrefix(y)
        if (total <= 0.0) return guess
        spectrum(y)
        val half = fftSize / 2
        for (k in 0 until fftSize) {
            re[k] = power[if (k <= half) k else fftSize - k]
            im[k] = 0.0
        }
        fft.inverse(re, im)
        System.arraycopy(re, 0, acf, 0, windowSize)

        val single = peakNear(guess, max(2.0, guess * SEARCH_FRACTION), total, MIN_REFINE_CLARITY)
            ?: return guess
        val periods = (MULTI_PERIOD_SPAN * windowSize / single).toInt()
        if (periods < 2) return single
        val multiple = peakNear(periods * single, single / 4.0, total, MIN_REFINE_CLARITY)
            ?: return single
        val period = multiple / periods
        return if (abs(period - single) <= single * MAX_MULTI_PERIOD_DRIFT) period else single
    }

    /**
     * Highest NSDF maximum of the refinement window within [radius] of [center], located to a
     * fraction of a sample, or null if it is not a clear interior peak of height ≥ [minHeight].
     */
    private fun peakNear(center: Double, radius: Double, total: Double, minHeight: Double): Double? {
        val low = max(1, floor(center - radius).toInt())
        val high = minOf(windowSize - 2, ceil(center + radius).toInt())
        if (high - low < 2) return null
        var best = -1
        var bestValue = Double.NEGATIVE_INFINITY
        for (tau in low + 1 until high) {
            val value = nsdfAt(tau, total)
            if (value > bestValue) {
                bestValue = value
                best = tau
            }
        }
        if (best < 0 || bestValue < minHeight) return null
        val before = nsdfAt(best - 1, total)
        val after = nsdfAt(best + 1, total)
        if (before > bestValue || after > bestValue) return null

        val m0 = energy(best, total)
        val slope = 0.5 * (energy(best + 1, total) - energy(best - 1, total))
        if (m0 <= 0.0) return null
        var t = best + parabolicShift(before, bestValue, after)
        for (h in REFINE_STEPS) {
            val a = autocorrelationAt(t - h) / (m0 + slope * (t - h - best))
            val b = autocorrelationAt(t) / (m0 + slope * (t - best))
            val c = autocorrelationAt(t + h) / (m0 + slope * (t + h - best))
            val den = a - 2.0 * b + c
            if (den >= 0.0) break
            t += (0.5 * (a - c) / den).coerceIn(-1.0, 1.0) * h
        }
        return if (abs(t - best) <= 1.0) t else null
    }

    private fun nsdfAt(tau: Int, total: Double): Double {
        val m = energy(tau, total)
        return if (m > total * 1e-9) 2.0 * acf[tau] / m else 0.0
    }

    private fun addPeak(tau: Int, count: Int): Int {
        val a = nsdf[tau - 1]
        val b = nsdf[tau]
        val c = nsdf[tau + 1]
        val shift = parabolicShift(a, b, c)
        peakLags[count] = tau + shift
        peakValues[count] = b - 0.25 * (a - c) * shift
        return count + 1
    }

    /** Offset of the vertex of the parabola through (−1, a), (0, b), (1, c), within ±0,5. */
    private fun parabolicShift(a: Double, b: Double, c: Double): Double {
        val den = a - 2.0 * b + c
        return if (den < 0.0) (0.5 * (a - c) / den).coerceIn(-0.5, 0.5) else 0.0
    }

    /** Fills the prefix sums of squares of the window and returns its total energy. */
    private fun fillPrefix(x: DoubleArray): Double {
        prefix[0] = 0.0
        for (i in 0 until windowSize) prefix[i + 1] = prefix[i] + x[i] * x[i]
        return prefix[windowSize]
    }

    /** m(τ): energy of the first W − τ samples plus energy of the last W − τ samples. */
    private fun energy(tau: Int, total: Double): Double = prefix[windowSize - tau] + total - prefix[tau]

    /** Power spectrum |X(k)|², k = 0..N/2, of the zero-padded window. */
    private fun spectrum(x: DoubleArray) {
        for (i in 0 until windowSize) {
            re[i] = x[i]
            im[i] = 0.0
        }
        for (i in windowSize until fftSize) {
            re[i] = 0.0
            im[i] = 0.0
        }
        fft.forward(re, im)
        for (k in 0..fftSize / 2) power[k] = re[k] * re[k] + im[k] * im[k]
    }

    /** Autocorrelation at a fractional lag: trigonometric interpolation of the integer lags. */
    private fun autocorrelationAt(t: Double): Double {
        val half = fftSize / 2
        val theta = 2.0 * PI * t / fftSize
        val cosStep = cos(theta)
        val sinStep = sin(theta)
        var c = 1.0
        var s = 0.0
        var sum = power[0]
        for (k in 1 until half) {
            val next = c * cosStep - s * sinStep
            s = s * cosStep + c * sinStep
            c = next
            sum += 2.0 * power[k] * c
        }
        sum += power[half] * cos(PI * t)
        return sum / fftSize
    }
}
