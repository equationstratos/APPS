package com.survival.morse

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max

const val DETECT_MIN_HZ = 300
const val DETECT_MAX_HZ = 3000
private const val DETECT_STEP_HZ = 100
private const val BLOCK_SIZE = 256

/** Minimum distance between the noise floor and the thresholds, in dB. */
private const val MIN_SPAN_DB = 15.0
private const val ON_FRACTION = 0.5
private const val OFF_FRACTION = 0.3

/** Share of the block energy that must sit in the strongest band: rejects broadband noise. */
private const val ON_PURITY = 0.3
private const val OFF_PURITY = 0.15

/** A new state must hold for this many consecutive blocks (filters clicks and short dropouts). */
private const val CONFIRM_BLOCKS = 2
private const val NOISE_FALL = 0.25
private const val NOISE_RISE = 0.002
private const val PEAK_DECAY = 0.004

/** While silent, the current gap is reported every this many blocks so letters close in time. */
private const val GAP_REPORT_BLOCKS = 8

/**
 * Detects a whistle or beep in 300–3000 Hz, block by block. Each block is windowed (Hann) and
 * measured with the Goertzel algorithm every 100 Hz; the strongest band gives the level. A
 * noise floor (follows dips quickly, rises slowly) and a peak level (instant attack, slow decay)
 * set two thresholds: the tone turns on above 50 % of the span and off below 30 % (hysteresis).
 */
class ToneDetector(sampleRate: Int, val blockSize: Int = BLOCK_SIZE) {
    private val frequencies = (DETECT_MIN_HZ..DETECT_MAX_HZ step DETECT_STEP_HZ).toList().toIntArray()
    private val coefficients = DoubleArray(frequencies.size) { 2.0 * cos(2.0 * PI * frequencies[it] / sampleRate) }
    private val window = DoubleArray(blockSize) { 0.5 - 0.5 * cos(2.0 * PI * it / (blockSize - 1)) }

    // A sine of amplitude A centred on a band gives a Goertzel magnitude of A * sum(window) / 2.
    private val gainSquared = (window.sum() / 2.0).let { it * it }
    private val weighted = DoubleArray(blockSize)
    private var initialized = false
    private var disagreement = 0

    val blockMs: Double = blockSize * 1000.0 / sampleRate

    /** Level of the strongest band in the last block, in dB relative to one sample unit. */
    var levelDb = 0.0
        private set
    var noiseDb = 0.0
        private set
    var peakDb = 0.0
        private set

    /** Frequency of the strongest band in the last block. */
    var toneHz = 0
        private set

    /** Debounced detector output. */
    var isOn = false
        private set

    val onThresholdDb: Double get() = noiseDb + ON_FRACTION * max(peakDb - noiseDb, MIN_SPAN_DB)

    /** Analyses [blockSize] samples starting at [offset] and returns the debounced state. */
    fun process(samples: ShortArray, offset: Int = 0): Boolean {
        var sum = 0.0
        var energy = 0.0
        for (i in 0 until blockSize) {
            val x = samples[offset + i].toDouble()
            sum += x
            energy += x * x
            weighted[i] = x * window[i]
        }
        var best = 0.0
        var bestIndex = 0
        for (k in coefficients.indices) {
            val c = coefficients[k]
            var s1 = 0.0
            var s2 = 0.0
            for (i in 0 until blockSize) {
                val s0 = weighted[i] + c * s1 - s2
                s2 = s1
                s1 = s0
            }
            val power = (s1 * s1 + s2 * s2 - c * s1 * s2) / gainSquared
            if (power > best) {
                best = power
                bestIndex = k
            }
        }
        // Variance rather than mean square: a microphone's DC offset is not part of the sound.
        val mean = sum / blockSize
        val meanSquare = (energy / blockSize - mean * mean).coerceAtLeast(0.0)
        // A pure sine puts all its energy (A²/2) in one band (A²): purity close to 1.
        val purity = if (meanSquare > 0.0) best / (2.0 * meanSquare) else 0.0
        val level = 10.0 * log10(best + 1.0)
        levelDb = level
        toneHz = frequencies[bestIndex]

        if (!initialized) {
            noiseDb = level
            peakDb = level
            initialized = true
        }
        val span = max(peakDb - noiseDb, MIN_SPAN_DB)
        val wanted = if (isOn) {
            level > noiseDb + OFF_FRACTION * span && purity >= OFF_PURITY
        } else {
            level > noiseDb + ON_FRACTION * span && purity >= ON_PURITY
        }
        if (wanted != isOn) {
            disagreement++
            if (disagreement >= CONFIRM_BLOCKS) {
                isOn = wanted
                disagreement = 0
            }
        } else {
            disagreement = 0
        }

        noiseDb += (level - noiseDb) * (if (level < noiseDb) NOISE_FALL else NOISE_RISE)
        peakDb = if (level > peakDb) level else peakDb + (level - peakDb) * PEAK_DECAY
        if (peakDb < noiseDb) peakDb = noiseDb
        return isOn
    }

    fun reset() {
        initialized = false
        disagreement = 0
        isOn = false
    }
}

/** What a duration reported by [ToneKeyer] measures. */
enum class KeyEvent {
    /** A signal that has just ended. */
    MARK,

    /** A silence that has just ended. */
    GAP,

    /** A silence still going on. */
    SILENCE
}

/** Cuts an audio stream into detector blocks and reports mark and silence durations. */
class ToneKeyer(sampleRate: Int) {
    val detector = ToneDetector(sampleRate)
    private val block = ShortArray(detector.blockSize)
    private var filled = 0
    private var state = false
    private var blocks = 0L

    fun feed(samples: ShortArray, count: Int, sink: (event: KeyEvent, durationMs: Double) -> Unit) {
        var index = 0
        while (index < count) {
            val take = minOf(count - index, block.size - filled)
            System.arraycopy(samples, index, block, filled, take)
            filled += take
            index += take
            if (filled < block.size) break
            filled = 0
            val on = detector.process(block)
            if (on == state) {
                blocks++
                if (!on && blocks % GAP_REPORT_BLOCKS == 0L) sink(KeyEvent.SILENCE, blocks * detector.blockMs)
            } else {
                sink(if (state) KeyEvent.MARK else KeyEvent.GAP, blocks * detector.blockMs)
                state = on
                blocks = 1
            }
        }
    }

    fun reset() {
        detector.reset()
        filled = 0
        state = false
        blocks = 0
    }
}
