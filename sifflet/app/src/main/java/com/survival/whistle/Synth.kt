package com.survival.whistle

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

const val SAMPLE_RATE = 48_000

/** Attack and release of every tone: long enough to avoid clicks, short enough to stay crisp. */
const val RAMP_MS = 8.0

/** Peak level of the output (full scale = 1). */
const val MASTER_LEVEL = 0.97

/** Number of audio frames of [ms] milliseconds at [sampleRate]. */
fun framesOf(ms: Int, sampleRate: Int = SAMPLE_RATE): Int = (ms.toLong() * sampleRate / 1000).toInt()

/**
 * Real-time synthesizer of a looping signal. All its state (oscillator phase, trill phase,
 * envelope, position in the cycle) lives in the object, so the output is the same whatever the
 * size of the buffers it is rendered into: there is never a discontinuity between two buffers.
 *
 * - The oscillator integrates the instantaneous frequency (continuous phase), so frequency changes
 *   (sweeps, alternating tones, trill) never produce a jump in the waveform.
 * - The envelope moves towards 1 during tones and 0 during silences by at most 1/[rampFrames] per
 *   frame, shaped with a smoothstep curve: every start and end of tone is a soft ramp.
 * - [release] fades the sound out with the same ramp; [isFinished] then turns true.
 */
class Synthesizer(
    segments: List<Segment>,
    val sampleRate: Int = SAMPLE_RATE,
    rampMs: Double = RAMP_MS,
    private val level: Double = MASTER_LEVEL
) {
    private val segments: List<Segment> = segments.filter { framesOf(it.durationMs, sampleRate) > 0 }
    private val frames = IntArray(this.segments.size) { framesOf(this.segments[it].durationMs, sampleRate) }

    val rampFrames: Int = max(1, (sampleRate * rampMs / 1000.0).roundToInt())
    private val step = 1.0 / rampFrames

    private var index = 0
    private var frameInSegment = 0
    private var phase = 0.0
    private var trillPhase = 0.0
    private var ramp = 0.0
    private var released = false

    // Voice of the last tone, kept to play the fade-out tail after a tone ends.
    private var voice: Tone = this.segments.firstOrNull { it.tone != null }?.tone ?: Tone(1_000.0)
    private var hz = voice.startHz

    /** Whether the torch should currently be lit. */
    var lightOn: Boolean = false
        private set

    /** Current envelope gain (0 to 1). */
    val envelope: Double get() = smoothstep(ramp)

    val isFinished: Boolean get() = released && ramp <= 0.0

    init {
        require(this.segments.isNotEmpty()) { "Signal vide" }
    }

    /** Starts the final fade-out. */
    fun release() {
        released = true
    }

    /**
     * Writes [count] samples (-1 to 1) into [out] from [offset]. [onLight] is called with the
     * index (relative to [offset]) of the first sample where the torch must change state.
     */
    fun render(out: FloatArray, offset: Int, count: Int, onLight: (frame: Int, on: Boolean) -> Unit = { _, _ -> }) {
        for (i in 0 until count) {
            val segment = segments[index]
            val tone = if (released) null else segment.tone

            val wantLight = tone != null && tone.light
            if (wantLight != lightOn) {
                lightOn = wantLight
                onLight(i, wantLight)
            }

            if (tone != null) {
                voice = tone
                hz = tone.frequencyAt(frameInSegment.toDouble() / frames[index])
                ramp = min(1.0, ramp + step)
            } else {
                ramp = max(0.0, ramp - step)
            }

            val trill = if (voice.trillHz > 0.0) sin(2.0 * PI * trillPhase) else 0.0
            val gain = smoothstep(ramp)
            out[offset + i] = if (gain == 0.0) {
                0f
            } else {
                val amplitude = 1.0 - voice.tremolo * (0.5 - 0.5 * trill)
                (softClip(sin(2.0 * PI * phase), voice.drive) * gain * amplitude * level).toFloat()
            }

            phase += hz * (1.0 + voice.trillDepth * trill) / sampleRate
            phase -= floor(phase)
            trillPhase += voice.trillHz / sampleRate
            trillPhase -= floor(trillPhase)

            frameInSegment++
            if (frameInSegment >= frames[index]) {
                frameInSegment = 0
                index = (index + 1) % segments.size
            }
        }
    }

    /** Same as [render], converted to 16-bit PCM. */
    fun render(out: ShortArray, scratch: FloatArray, count: Int, onLight: (frame: Int, on: Boolean) -> Unit) {
        render(scratch, 0, count, onLight)
        for (i in 0 until count) out[i] = toPcm16(scratch[i])
    }
}

/** 0 to 1 easing with zero slope at both ends. */
fun smoothstep(x: Double): Double {
    val t = x.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

/** Soft saturation normalised so that |x| <= 1 gives a result <= 1 (drive 1 = untouched). */
fun softClip(x: Double, drive: Double): Double = if (drive <= 1.0) x else tanh(drive * x) / tanh(drive)

fun toPcm16(sample: Float): Short = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
