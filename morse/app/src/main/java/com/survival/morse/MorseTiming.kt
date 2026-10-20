package com.survival.morse

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

const val MIN_WPM = 5
const val MAX_WPM = 30
const val DEFAULT_WPM = 12
const val DEFAULT_LEARN_WPM = 18

const val MIN_TONE_HZ = 400
const val MAX_TONE_HZ = 1000
const val TONE_STEP_HZ = 50
const val DEFAULT_TONE_HZ = 700

/** Durations in units (ITU-R M.1677-1): a dash is three dots, spaces are 1, 3 and 7 dots. */
const val DOT_UNITS = 1
const val DASH_UNITS = 3
const val SYMBOL_GAP_UNITS = 1
const val LETTER_GAP_UNITS = 3
const val WORD_GAP_UNITS = 7

/** "PARIS " lasts 50 units, so a dot lasts 1200 / WPM milliseconds. */
fun unitMs(wpm: Int): Double = 1200.0 / wpm

fun wpmForUnit(unitMs: Double): Double = 1200.0 / unitMs

/** One "on" interval of a transmission, in units from the start; [token] indexes [Encoded.tokens]. */
class Mark(val startUnit: Int, val units: Int, val token: Int) {
    val endUnit: Int get() = startUnit + units
}

/**
 * A transmission laid out in units. [totalUnits] includes the trailing word space, so a repeated
 * message keeps a 7-unit space between two occurrences.
 */
class Timeline(val marks: List<Mark>, val totalUnits: Int) {
    /** End of the last mark: a single transmission is over at this point. */
    val endUnit: Int get() = marks.lastOrNull()?.endUnit ?: 0

    val isEmpty: Boolean get() = marks.isEmpty()
}

fun timelineOf(encoded: Encoded): Timeline = timelineOf(encoded.words)

fun timelineOf(words: List<List<MorseToken>>): Timeline {
    val marks = mutableListOf<Mark>()
    var unit = 0
    var tokenIndex = 0
    for ((w, word) in words.withIndex()) {
        if (w > 0) unit += WORD_GAP_UNITS
        for ((t, token) in word.withIndex()) {
            if (t > 0) unit += LETTER_GAP_UNITS
            for ((s, symbol) in token.code.withIndex()) {
                if (s > 0) unit += SYMBOL_GAP_UNITS
                val length = if (symbol == '-') DASH_UNITS else DOT_UNITS
                marks += Mark(unit, length, tokenIndex)
                unit += length
            }
            tokenIndex++
        }
    }
    return Timeline(marks, if (marks.isEmpty()) 0 else unit + WORD_GAP_UNITS)
}

/**
 * Sample-accurate view of a [Timeline] for an audio renderer: every boundary is computed from
 * the start (round(unit * framesPerUnit)), so rounding never accumulates, even when repeating.
 * Frames must be queried in increasing order.
 */
class FrameSchedule(timeline: Timeline, private val framesPerUnit: Double, private val repeat: Boolean) {
    private val starts = LongArray(timeline.marks.size) { (timeline.marks[it].startUnit * framesPerUnit).roundToLong() }
    private val ends = LongArray(timeline.marks.size) { (timeline.marks[it].endUnit * framesPerUnit).roundToLong() }
    private val totalUnits = timeline.totalUnits.toLong()
    private var cycle = 0L
    private var cycleStart = 0L
    private var nextCycleStart = cycleStartFrame(1)
    private var index = 0

    fun isOn(frame: Long): Boolean {
        if (starts.isEmpty()) return false
        while (repeat && frame >= nextCycleStart) {
            cycle++
            cycleStart = nextCycleStart
            nextCycleStart = cycleStartFrame(cycle + 1)
            index = 0
        }
        val position = frame - cycleStart
        while (index < ends.size && position >= ends[index]) index++
        return index < ends.size && position >= starts[index]
    }

    /** True once a non-repeating schedule has played its last mark. */
    fun isFinished(frame: Long): Boolean = !repeat && (ends.isEmpty() || frame >= ends.last())

    private fun cycleStartFrame(cycle: Long): Long = (cycle * totalUnits * framesPerUnit).roundToLong()
}

fun formatWpm(wpm: Int): String = "$wpm mots/min"

fun formatUnit(wpm: Int): String = "point = ${unitMs(wpm).roundToInt()} ms"

fun formatHz(hz: Int): String = "$hz Hz"

/** Duration such as "4,2 s" or "1 min 05 s". */
fun formatDuration(ms: Double): String {
    val seconds = ms / 1000.0
    if (seconds < 59.95) return String.format(Locale.FRANCE, "%.1f s", seconds)
    val total = seconds.roundToInt()
    return String.format(Locale.FRANCE, "%d min %02d s", total / 60, total % 60)
}

fun formatPercent(part: Int, total: Int): String =
    if (total <= 0) "—" else "${(100.0 * part / total).roundToInt()} %"
