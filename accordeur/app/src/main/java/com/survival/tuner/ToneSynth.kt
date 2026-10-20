package com.survival.tuner

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Lowest and highest notes the reference tone can play (Do1 to Do7). */
const val MIN_TONE_MIDI = 24
const val MAX_TONE_MIDI = 96

/** Fade-in and fade-out durations of the reference tone (s): long enough to never click. */
const val TONE_ATTACK_S = 0.04
const val TONE_RELEASE_S = 0.06

/** Peak level of the pure sine, as a fraction of full scale. */
private const val SINE_PEAK = 0.7

/**
 * Partials of the "rich" timbre (amplitude 1/n): low notes become audible on a phone speaker
 * that cannot reproduce their fundamental, while the ear still hears the same pitch.
 */
private const val RICH_PARTIALS = 10

/** Highest partial frequency kept, as a fraction of the sample rate (well below Nyquist). */
private const val MAX_PARTIAL_FRACTION = 0.4

/** max |Σ sin(nθ)/n| for n = 1..10 is about 1,85 (Gibbs); this keeps the rich peak at ~0,7. */
private const val RICH_NORMALIZATION = 1.9

/**
 * Reference tone generator: a phase-continuous oscillator with smooth (smoothstep) fade-in and
 * fade-out, so starting, stopping or changing the note never produces a click.
 *
 * [frequency] and [rich] may be changed between two [render] calls. Not thread-safe.
 */
class ToneSynth(private val sampleRate: Int) {
    var frequency: Double = 440.0
    var rich: Boolean = false

    private var phase = 0.0 // in cycles, 0..1
    private var ramp = 0.0 // linear fade position, 0..1
    private var on = false
    private val attackStep = 1.0 / (TONE_ATTACK_S * sampleRate)
    private val releaseStep = 1.0 / (TONE_RELEASE_S * sampleRate)

    /** True once a released tone has completely faded out (or before the first [noteOn]). */
    val isSilent: Boolean get() = !on && ramp == 0.0

    fun noteOn() {
        on = true
    }

    fun noteOff() {
        on = false
    }

    /** Fills [out] with the next [count] samples of 16-bit PCM. */
    fun render(out: ShortArray, count: Int = out.size) {
        val f = frequency.coerceIn(1.0, sampleRate * MAX_PARTIAL_FRACTION)
        val increment = f / sampleRate
        val partials = if (rich) max(1, min(RICH_PARTIALS, (sampleRate * MAX_PARTIAL_FRACTION / f).toInt())) else 1
        val gain = if (partials > 1) SINE_PEAK / RICH_NORMALIZATION else SINE_PEAK
        for (i in 0 until count) {
            ramp = if (on) min(1.0, ramp + attackStep) else max(0.0, ramp - releaseStep)
            val envelope = ramp * ramp * (3.0 - 2.0 * ramp)
            val sample = if (envelope == 0.0) {
                0.0
            } else {
                val angle = 2.0 * PI * phase
                var sum = sin(angle)
                for (n in 2..partials) sum += sin(n * angle) / n
                sum * gain * envelope
            }
            out[i] = (sample * Short.MAX_VALUE).roundToInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            phase += increment
            if (phase >= 1.0) phase -= 1.0
        }
    }
}

/** Clamps a reference-tone note to the playable range. */
fun clampToneMidi(midi: Int): Int = midi.coerceIn(MIN_TONE_MIDI, MAX_TONE_MIDI)
