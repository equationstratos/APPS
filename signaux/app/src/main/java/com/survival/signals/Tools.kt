package com.survival.signals

import java.util.Locale

/* ---------- Alpine distress signal cadence ---------- */

/** Alpine distress signal: a call is 6 signals a minute, the answer 3 a minute; each minute is followed by a minute of pause. */
enum class CadenceMode(val label: String, val signalsPerMinute: Int) {
    CALL("Appel à l'aide", 6),
    REPLY("Réponse", 3),
}

const val CADENCE_SIGNAL_WINDOW_MS = 60_000L
const val CADENCE_CYCLE_MS = 120_000L

/** How long the guide shows "now" for each signal. */
const val CADENCE_FLASH_MS = 1_000L

/**
 * Where the guide stands at a given time.
 *
 * @property signal 1-based number of the current or last signal of the cycle.
 * @property total signals per cycle.
 * @property flashing the signal must be given right now.
 * @property inPause the minute of silence after the signals.
 * @property nextIsPause the last signal of the minute is done; the pause comes next.
 * @property msToNext time until the next signal (or until the pause, or the end of the pause).
 * @property cycle 0-based cycle number.
 */
class CadenceStep(
    val signal: Int,
    val total: Int,
    val flashing: Boolean,
    val inPause: Boolean,
    val nextIsPause: Boolean,
    val msToNext: Long,
    val cycle: Long,
)

fun cadenceAt(elapsedMs: Long, mode: CadenceMode): CadenceStep {
    val elapsed = elapsedMs.coerceAtLeast(0L)
    val cycle = elapsed / CADENCE_CYCLE_MS
    val t = elapsed % CADENCE_CYCLE_MS
    val total = mode.signalsPerMinute
    val interval = CADENCE_SIGNAL_WINDOW_MS / total
    if (t >= CADENCE_SIGNAL_WINDOW_MS) {
        return CadenceStep(total, total, flashing = false, inPause = true, nextIsPause = false, CADENCE_CYCLE_MS - t, cycle)
    }
    val index = (t / interval).toInt()
    val last = index == total - 1
    val next = if (last) CADENCE_SIGNAL_WINDOW_MS else (index + 1) * interval
    return CadenceStep(
        signal = index + 1,
        total = total,
        flashing = t - index * interval < CADENCE_FLASH_MS,
        inPause = false,
        nextIsPause = last,
        msToNext = next - t,
        cycle = cycle,
    )
}

/** Whole seconds shown for a countdown: 9 001 ms is "10 s", 1 ms is "1 s". */
fun countdownSeconds(ms: Long): Long = (ms.coerceAtLeast(0L) + 999L) / 1_000L

/** Sentence under the cadence guide. */
fun cadenceStatus(step: CadenceStep): String = frenchTypography(
    when {
        step.inPause -> "Pause : reprise dans ${countdownSeconds(step.msToNext)} s"
        step.flashing -> "Signal ${step.signal} sur ${step.total} : maintenant !"
        step.nextIsPause -> "Pause d'une minute dans ${countdownSeconds(step.msToNext)} s"
        else -> "Signal ${step.signal + 1} dans ${countdownSeconds(step.msToNext)} s"
    }
)

/* ---------- MAYDAY message builder ---------- */

class MaydayInput(
    val vessel: String = "",
    val callSign: String = "",
    val position: String = "",
    val nature: String = "",
    val assistance: String = "",
    val persons: String = "",
    val extra: String = "",
)

/**
 * Distress message in the order taught for the French CRR (ANFR): MAYDAY ×3, ICI + name ×3 +
 * call sign spelled once, then MAYDAY + name + call sign, position, nature of distress, help
 * required, persons on board, intentions and other information, and "À VOUS".
 * Missing fields are left as bracketed placeholders.
 */
fun maydayLines(input: MaydayInput): List<String> {
    val vessel = input.vessel.trim()
    val callSign = input.callSign.trim()
    val spelledCallSign = spelledText(callSign)
    val name = vessel.ifEmpty { "[nom du navire]" }
    val persons = input.persons.trim().toIntOrNull()
    return listOf(
        "MAYDAY MAYDAY MAYDAY",
        buildString {
            append("ICI ")
            append(if (vessel.isEmpty()) "[nom du navire, 3 fois]" else "$vessel $vessel $vessel")
            if (spelledCallSign.isNotEmpty()) append(' ').append(spelledCallSign)
        },
        listOf("MAYDAY", name, callSign).filter { it.isNotEmpty() }.joinToString(" "),
        "POSITION : " + input.position.trim().ifEmpty { "[position]" },
        input.nature.trim().ifEmpty { "[nature de la détresse]" },
        input.assistance.trim().ifEmpty { "[assistance demandée]" },
        when {
            persons == null -> "[nombre] personnes à bord"
            persons <= 1 -> "$persons personne à bord"
            else -> "$persons personnes à bord"
        },
    ).plus(listOfNotNull(input.extra.trim().takeIf { it.isNotEmpty() }))
        .plus("À VOUS")
        .map(::frenchTypography)
}

/* ---------- Large display ---------- */

/** Average advance of a bold glyph, in em, used to size the large display. */
private const val GLYPH_WIDTH_EM = 0.62f
private const val LINE_HEIGHT_EM = 1.2f
const val BIG_MIN_SP = 28f
const val BIG_MAX_SP = 160f

/**
 * Font size (sp) that lets the longest line fit the width and all lines fit the height when possible,
 * clamped to a readable range. Dimensions are in dp; [fontScale] converts dp to sp.
 */
fun bigFontSizeSp(widthDp: Float, heightDp: Float, lines: List<String>, fontScale: Float): Float {
    val longest = (lines.maxOfOrNull { it.length } ?: 1).coerceAtLeast(1)
    val count = lines.size.coerceAtLeast(1)
    val byWidth = widthDp / (longest * GLYPH_WIDTH_EM)
    val byHeight = heightDp / (count * LINE_HEIGHT_EM)
    val dp = minOf(byWidth, byHeight)
    val scale = if (fontScale > 0f) fontScale else 1f
    return (dp / scale).coerceIn(BIG_MIN_SP, BIG_MAX_SP)
}

/* ---------- Radio channels ---------- */

const val PMR_CHANNELS = 16

/** Centre frequency (MHz) of analogue PMR446 channel [channel] (1..16), 12,5 kHz apart from 446,00625 MHz. */
fun pmrChannelMhz(channel: Int): Double {
    require(channel in 1..PMR_CHANNELS) { "Canal PMR446 inexistant : $channel" }
    return 446.00625 + (channel - 1) * 0.0125
}

/** "446,00625" — French decimal comma, fixed number of decimals. */
fun formatMhz(mhz: Double, decimals: Int): String = String.format(Locale.FRANCE, "%.${decimals}f", mhz)

/* ---------- Phone numbers ---------- */

private val DIAL_DIGITS = Regex("\\d{2,10}")

/** Short or national number made of digits only, as passed to the dialer. */
fun isDialable(number: String): Boolean = DIAL_DIGITS.matches(number)
