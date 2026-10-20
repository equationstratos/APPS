package com.survival.tuner

import kotlin.math.log10

/** Analysis window (samples): about 90 ms, a bit more than 3 periods of the bass Mi1. */
const val WINDOW_SIZE = 4096

/** Analyses per second: the window slides by 1/20 s, i.e. about 50 % overlap. */
const val FRAMES_PER_SECOND = 20

/** Below this level (dBFS) no pitch is searched at all; the tracker applies its own gates. */
const val ANALYSIS_FLOOR_DB = -75.0

/** Level reported for digital silence (dBFS). */
const val SILENCE_DB = -120.0

/** Removes DC, handling noise and rumble below the lowest note. */
private const val HIGH_PASS_HZ = 25.0

/** Detection band: keeps the useful harmonics up to the highest notes, drops hiss above. */
private const val WIDE_LOW_PASS_HZ = 5000.0

/**
 * Refinement bands (fourth-order low-pass cut-off, Hz): a note is measured on the first band
 * whose cut-off is at least 4,5 times its frequency, i.e. on its first 4 to 9 harmonics only.
 * Notes above the last band are measured on the detection band.
 */
private val BAND_CUTOFFS_HZ = doubleArrayOf(250.0, 500.0, 1000.0, 2000.0)
private const val BAND_HARMONICS = 4.5

/** Result of one analysis: input level and the detected pitch, if any. */
class AnalysisFrame(val levelDb: Double, val estimate: PitchEstimate?)

/**
 * Streaming pitch analyser: [push] the raw signal as it arrives, then call [analyze] once per hop.
 *
 * The signal is filtered once, sample by sample, into three band-limited copies kept in ring
 * buffers. Detection runs on the wide band; the period found is then refined on the narrowest
 * band that still holds about seven harmonics of the note, which removes most of the noise that
 * would otherwise blur the position of the peak for low notes.
 *
 * Not thread-safe: use it from a single thread.
 */
class PitchAnalyzer(val sampleRate: Int, val windowSize: Int = WINDOW_SIZE) {
    /** Number of new samples between two analyses. */
    val hopSize: Int = sampleRate / FRAMES_PER_SECOND

    private val highPass = Biquad.highPass(sampleRate, HIGH_PASS_HZ)
    private val wideLowPass = Biquad.lowPass(sampleRate, WIDE_LOW_PASS_HZ)
    private val wideRing = DoubleArray(windowSize)
    private val bands = BAND_CUTOFFS_HZ
        .filter { it < sampleRate / 2.5 }
        .map { cutoff -> RefinementBand(cutoff, sampleRate, windowSize) }

    private val window = DoubleArray(windowSize)
    private val bandWindow = DoubleArray(windowSize)
    private var writeIndex = 0
    private var filled = 0

    private val detector = PitchDetector(sampleRate, windowSize)

    /** True once a full window has been received. */
    val isReady: Boolean get() = filled >= windowSize

    /** Appends 16-bit PCM samples. */
    fun push(samples: ShortArray, count: Int = samples.size) {
        for (i in 0 until count) pushSample(samples[i] / 32768.0)
    }

    /** Appends samples in the range −1..1. */
    fun push(samples: DoubleArray, count: Int = samples.size) {
        for (i in 0 until count) pushSample(samples[i])
    }

    fun reset() {
        highPass.reset()
        wideLowPass.reset()
        wideRing.fill(0.0)
        for (band in bands) band.reset()
        writeIndex = 0
        filled = 0
    }

    /** Analyses the latest window. */
    fun analyze(): AnalysisFrame {
        unroll(wideRing, window)
        val level = levelDb(window)
        if (!isReady || level < ANALYSIS_FLOOR_DB) return AnalysisFrame(level, null)
        val peak = detector.findPeriod(window) ?: return AnalysisFrame(level, null)
        val coarse = sampleRate / peak.lag
        if (coarse < MIN_FREQUENCY || coarse > MAX_FREQUENCY) return AnalysisFrame(level, null)
        val band = bands.firstOrNull { it.cutoff >= BAND_HARMONICS * coarse }
        val refineOn = if (band == null) window else unroll(band.ring, bandWindow)
        val frequency = sampleRate / detector.refine(refineOn, peak.lag)
        if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) return AnalysisFrame(level, null)
        return AnalysisFrame(level, PitchEstimate(frequency, peak.clarity))
    }

    private fun pushSample(x: Double) {
        val filtered = highPass.process(x)
        wideRing[writeIndex] = wideLowPass.process(filtered)
        for (band in bands) band.ring[writeIndex] = band.process(filtered)
        writeIndex++
        if (writeIndex == windowSize) writeIndex = 0
        if (filled < windowSize) filled++
    }

    /** Copies a ring buffer into [out], oldest sample first. */
    private fun unroll(ring: DoubleArray, out: DoubleArray): DoubleArray {
        val tail = windowSize - writeIndex
        System.arraycopy(ring, writeIndex, out, 0, tail)
        System.arraycopy(ring, 0, out, tail, writeIndex)
        return out
    }
}

/** A fourth-order Butterworth low-passed copy of the (high-passed) signal, in a ring buffer. */
private class RefinementBand(val cutoff: Double, sampleRate: Int, windowSize: Int) {
    val ring = DoubleArray(windowSize)
    private val sections = Biquad.BUTTERWORTH_Q4.map { q -> Biquad.lowPass(sampleRate, cutoff, q) }

    fun process(x: Double): Double {
        var y = x
        for (section in sections) y = section.process(y)
        return y
    }

    fun reset() {
        ring.fill(0.0)
        for (section in sections) section.reset()
    }
}

/** RMS level of a window in dBFS (a full-scale sine reads −3 dBFS). */
internal fun levelDb(x: DoubleArray): Double {
    var sum = 0.0
    for (v in x) sum += v * v
    val meanSquare = sum / x.size
    return if (meanSquare <= 1e-12) SILENCE_DB else (10.0 * log10(meanSquare)).coerceAtLeast(SILENCE_DB)
}
