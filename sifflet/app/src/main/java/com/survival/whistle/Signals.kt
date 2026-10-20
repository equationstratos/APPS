package com.survival.whistle

import java.util.Locale
import kotlin.math.pow

/*
 * Pure description of every signal (no Android dependency, unit-tested on the JVM).
 *
 * A signal is a cycle of [Segment]s played in a loop: each segment is either a tone or a silence.
 * Mountain distress code (international alpine distress signal, Club alpin 1894/1898): six signals
 * a minute, one every 10 s, then one minute of silence to listen for an answer, repeated until help
 * arrives. The answer is three signals a minute, one every 20 s, followed by one minute of silence.
 */

/** Length of one whistle blast of the mountain codes: a long, clearly separated blast. */
const val BLAST_MS = 3_000

/** Mountain distress: one blast every 10 s (6 per minute). */
const val DISTRESS_PERIOD_MS = 10_000
const val DISTRESS_BLASTS = 6

/** Answer: one blast every 20 s (3 per minute). */
const val REPLY_PERIOD_MS = 20_000
const val REPLY_BLASTS = 3

/** Silence after each minute of signals, to listen for an answer. */
const val LISTEN_PAUSE_MS = 60_000

/** Morse unit of the audible SOS: dot = 1 unit, dash = 3 units. */
const val MORSE_UNIT_MS = 250

/** Siren: one sweep up then one sweep down, each lasting this long. */
const val SIREN_SWEEP_MS = 2_000
const val SIREN_LOW_HZ = 1_000.0
const val SIREN_HIGH_HZ = 3_500.0

/** Alarm: two tones alternating quickly. */
const val ALARM_STEP_MS = 200
const val ALARM_LOW_HZ = 2_500.0
const val ALARM_HIGH_HZ = 3_300.0

/** Pitch of the pea whistle and of the beeps (phone speakers are loudest around 2 to 4 kHz). */
const val WHISTLE_HZ = 3_150.0
const val BEEP_HZ = 3_000.0

/**
 * A tone whose pitch glides from [startHz] to [endHz] (exponential glide, constant when equal).
 * [trillHz] and [trillDepth] add the warble of a pea whistle (relative frequency deviation),
 * [tremolo] the matching amplitude flutter, [drive] a soft saturation that makes the tone louder
 * at the same peak level. [light] tells whether the torch is lit while this tone plays.
 */
data class Tone(
    val startHz: Double,
    val endHz: Double = startHz,
    val trillHz: Double = 0.0,
    val trillDepth: Double = 0.0,
    val tremolo: Double = 0.0,
    val drive: Double = 1.0,
    val light: Boolean = true
) {
    /** Frequency at the relative position [t] (0 to 1) of the segment. */
    fun frequencyAt(t: Double): Double =
        if (startHz == endHz) startHz else startHz * (endHz / startHz).pow(t.coerceIn(0.0, 1.0))
}

/** [durationMs] of [tone], or of silence when [tone] is null. */
data class Segment(val durationMs: Int, val tone: Tone?) {
    val isSilence: Boolean get() = tone == null
}

/** A tone of the cycle placed on the timeline, for drawing and status texts. */
data class ToneSpan(val startMs: Int, val endMs: Int, val startHz: Double, val endHz: Double)

/** Pea whistle timbre: ~3 kHz with a light trill, slightly saturated. */
val WHISTLE_TONE = Tone(WHISTLE_HZ, trillHz = 26.0, trillDepth = 0.035, tremolo = 0.18, drive = 1.6)
private val BEEP_TONE = Tone(BEEP_HZ, drive = 2.0)

enum class SignalMode(
    val title: String,
    val purpose: String,
    val rhythm: String,
    /** True for the codes made of signals separated by silences (status shows the next signal). */
    val counted: Boolean
) {
    Whistle(
        "Sifflet",
        "Appeler, attirer l'attention, guider quelqu'un vers vous.",
        "Son aigu d'environ 3 kHz avec un léger trille, comme un sifflet à bille.",
        counted = false
    ),
    Sos(
        "SOS sonore",
        "Appel au secours universel, compris partout (code Morse).",
        "··· ––– ··· en boucle : trois courts, trois longs, trois courts.",
        counted = false
    ),
    Mountain(
        "Détresse montagne",
        "Signal international de détresse en montagne.",
        "6 coups de sifflet par minute, un toutes les 10 s, puis 1 min de silence pour écouter.",
        counted = true
    ),
    Reply(
        "Réponse",
        "Répondre à un appel de détresse : « Nous vous avons entendu ».",
        "3 coups de sifflet par minute, un toutes les 20 s, puis 1 min de silence.",
        counted = true
    ),
    Siren(
        "Sirène",
        "Son montant et descendant, très repérable de loin.",
        "Balayage de 1 000 à 3 500 Hz, puis retour, toutes les 4 s.",
        counted = false
    ),
    Alarm(
        "Alarme",
        "Alerte stridente pour signaler un danger immédiat.",
        "Deux tons aigus alternés rapidement (2 500 et 3 300 Hz).",
        counted = false
    );

    /** The cycle of this signal, played in a loop. */
    val segments: List<Segment> by lazy { buildSegments(this) }

    val cycleMs: Int by lazy { segments.sumOf { it.durationMs } }

    val toneSpans: List<ToneSpan> by lazy {
        val spans = ArrayList<ToneSpan>()
        var at = 0
        for (segment in segments) {
            val tone = segment.tone
            if (tone != null) spans += ToneSpan(at, at + segment.durationMs, tone.startHz, tone.endHz)
            at += segment.durationMs
        }
        spans
    }

    companion object {
        fun fromName(name: String?): SignalMode? = entries.firstOrNull { it.name == name }
    }
}

private fun buildSegments(mode: SignalMode): List<Segment> = when (mode) {
    // One second of continuous whistle, looped seamlessly (the synthesizer keeps its phase).
    SignalMode.Whistle -> listOf(Segment(1_000, WHISTLE_TONE))
    SignalMode.Sos -> morse("... --- ...", MORSE_UNIT_MS, BEEP_TONE)
    SignalMode.Mountain -> blasts(DISTRESS_BLASTS, DISTRESS_PERIOD_MS)
    SignalMode.Reply -> blasts(REPLY_BLASTS, REPLY_PERIOD_MS)
    SignalMode.Siren -> listOf(
        Segment(SIREN_SWEEP_MS, Tone(SIREN_LOW_HZ, SIREN_HIGH_HZ, drive = 2.0, light = true)),
        Segment(SIREN_SWEEP_MS, Tone(SIREN_HIGH_HZ, SIREN_LOW_HZ, drive = 2.0, light = false))
    )
    SignalMode.Alarm -> listOf(
        Segment(ALARM_STEP_MS, Tone(ALARM_LOW_HZ, drive = 2.5, light = true)),
        Segment(ALARM_STEP_MS, Tone(ALARM_HIGH_HZ, drive = 2.5, light = false))
    )
}

/** [count] whistle blasts, one every [periodMs], then the listening pause. */
private fun blasts(count: Int, periodMs: Int): List<Segment> {
    val list = ArrayList<Segment>()
    repeat(count) {
        list += Segment(BLAST_MS, WHISTLE_TONE)
        list += Segment(periodMs - BLAST_MS, null)
    }
    list += Segment(LISTEN_PAUSE_MS, null)
    return mergeSilences(list)
}

/**
 * Morse code written with '.', '-' and ' ' between letters. Gaps: 1 unit inside a letter,
 * 3 units between letters, 7 units after the word before it repeats.
 */
fun morse(code: String, unitMs: Int, tone: Tone): List<Segment> {
    val letters = code.trim().split(' ').filter { it.isNotEmpty() }
    require(letters.isNotEmpty() && letters.all { l -> l.all { it == '.' || it == '-' } }) { "Code Morse invalide" }
    val list = ArrayList<Segment>()
    letters.forEachIndexed { letterIndex, letter ->
        letter.forEachIndexed { i, symbol ->
            list += Segment(if (symbol == '.') unitMs else 3 * unitMs, tone)
            if (i < letter.length - 1) list += Segment(unitMs, null)
        }
        list += Segment(if (letterIndex < letters.size - 1) 3 * unitMs else 7 * unitMs, null)
    }
    return list
}

/** Joins consecutive silences into one segment. */
fun mergeSilences(segments: List<Segment>): List<Segment> {
    val out = ArrayList<Segment>()
    for (segment in segments) {
        if (segment.durationMs <= 0) continue
        val last = out.lastOrNull()
        if (segment.isSilence && last != null && last.isSilence) {
            out[out.size - 1] = Segment(last.durationMs + segment.durationMs, null)
        } else {
            out += segment
        }
    }
    return out
}

/** Where a running signal is in its cycle. [toneNumber] is 1-based, 0 during a silence. */
data class CyclePosition(
    val offsetMs: Long,
    val toneNumber: Int,
    val toneCount: Int,
    /** Time until the next tone starts (0 while a tone plays). */
    val untilNextToneMs: Long,
    /** True during the silence that follows the last tone of the cycle. */
    val listening: Boolean
)

fun SignalMode.positionAt(elapsedMs: Long): CyclePosition {
    val spans = toneSpans
    val offset = Math.floorMod(elapsedMs, cycleMs.toLong())
    val playing = spans.indexOfFirst { offset >= it.startMs && offset < it.endMs }
    if (playing >= 0) return CyclePosition(offset, playing + 1, spans.size, 0, listening = false)
    val next = spans.firstOrNull { it.startMs > offset }
    val wait = if (next != null) next.startMs - offset else cycleMs - offset + (spans.firstOrNull()?.startMs ?: 0)
    return CyclePosition(offset, 0, spans.size, wait, listening = next == null)
}

/** One-line French status of a running signal. */
fun SignalMode.statusAt(elapsedMs: Long): String {
    if (!counted) return if (this == SignalMode.Sos) "Message SOS en boucle" else "Diffusion en continu"
    val p = positionAt(elapsedMs)
    return when {
        p.toneNumber > 0 -> "Signal ${p.toneNumber} sur ${p.toneCount}"
        p.listening -> "Silence d'écoute : reprise dans ${formatWait(p.untilNextToneMs)}"
        else -> "Prochain signal dans ${formatWait(p.untilNextToneMs)}"
    }
}

/** "8 s", "1 min 05 s": a wait rounded up to the second. */
fun formatWait(ms: Long): String {
    val total = ((ms.coerceAtLeast(0) + 999) / 1000)
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes == 0L) "$seconds s" else String.format(Locale.FRANCE, "%d min %02d s", minutes, seconds)
}

/** "02:15" or "1:02:15": a chronometer reading, truncated to the second. */
fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.FRANCE, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.FRANCE, "%02d:%02d", m, s)
    }
}

/** Countdown before an automatic stop, rounded up to the second ("14:59"). */
fun formatCountdown(ms: Long): String = formatClock(((ms.coerceAtLeast(0) + 999) / 1000) * 1000)

/** Automatic stop choices, in minutes (0 = never). */
val AUTO_STOP_CHOICES = listOf(0, 5, 15, 30, 60, 120)

fun autoStopLabel(minutes: Int): String = when {
    minutes <= 0 -> "Jamais"
    minutes % 60 == 0 -> "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${String.format(Locale.FRANCE, "%02d", minutes % 60)}"
    else -> "$minutes min"
}

/** User options, persisted by the app and applied live by the service. */
data class SignalOptions(
    val maxVolume: Boolean = true,
    val flash: Boolean = false,
    val autoStopMinutes: Int = 0
)

enum class TorchState { Off, Active, Unavailable }

/** What the service is doing, shared with the screen. [mode] is null when nothing plays. */
data class SignalStatus(
    val mode: SignalMode? = null,
    val hold: Boolean = false,
    /** Monotonic clock (elapsedRealtime) of the start of the cycle. */
    val startedAt: Long = 0,
    /** Monotonic clock of the automatic stop, 0 when none. */
    val autoStopAt: Long = 0,
    val volumeBlocked: Boolean = false,
    val torch: TorchState = TorchState.Off,
    val audioFailed: Boolean = false
)
