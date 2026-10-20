package com.survival.morse

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Fastest and slowest speeds the decoder adapts to: 60 and 2 words per minute. */
const val MIN_UNIT_MS = 20.0
const val MAX_UNIT_MS = 600.0

/** A mark longer than this is a stuck key or a continuous sound, not a Morse element. */
const val MAX_MARK_MS = 4000.0

/** Spaces, in units: below 2 inside a sign, from 2 between letters, from 4.5 between words. */
const val LETTER_SPACE_THRESHOLD = 2.0
const val WORD_SPACE_THRESHOLD = 4.5

private const val WINDOW_MARKS = 24

/** After a pause this long (or 15 units), the next transmission may come at another speed. */
private const val RESET_PAUSE_MS = 3000.0
private const val RESET_PAUSE_UNITS = 15.0
private const val MIN_CLUSTER_RATIO = 1.8
private const val MAX_DASH_RATIO = 5.0
private const val MAX_TEXT_LENGTH = 2000
private const val MAX_RAW_LENGTH = 8000

data class DecodeSnapshot(val text: String, val raw: String, val pending: String, val wpm: Int)

/**
 * Turns key durations into text. The dot length is re-estimated after every mark from the last
 * [WINDOW_MARKS] marks: they are split into a short and a long group (dots and dashes) when their
 * lengths differ enough, otherwise the previous estimate tells whether they are dots or dashes.
 *
 * The spaces inside letters take part in the estimate too, which steadies it on short messages.
 *
 * Feed [mark] when a signal ends and [gap] when a silence ends; while a silence goes on, [silence]
 * may be called with growing durations to close letters and words in time, without extra spaces.
 */
class MorseDecoder(initialUnitMs: Double = unitMs(DEFAULT_WPM), text: String = "", raw: String = "") {
    var unitMs: Double = initialUnitMs.coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)
        private set

    /** Marks at least this long are dashes. */
    private var dashThresholdMs = 2.0 * unitMs
    private val recent = ArrayDeque<Double>()
    private val recentGaps = ArrayDeque<Double>()
    private val pending = ArrayList<Double>()
    private val textBuffer = StringBuilder(text)
    private val rawBuffer = StringBuilder(raw)
    private var wordOpen = text.isNotEmpty() && !text.endsWith(' ')

    val text: String get() = textBuffer.toString()
    val raw: String get() = rawBuffer.toString()

    /** Code of the letter being received, not yet closed by a letter space. */
    val pendingCode: String get() = pending.joinToString("") { if (it >= dashThresholdMs) "-" else "." }

    val wpm: Int get() = wpmForUnit(unitMs).roundToInt()

    fun snapshot(): DecodeSnapshot = DecodeSnapshot(text, raw, pendingCode, wpm)

    fun mark(durationMs: Double) {
        if (durationMs.isNaN() || durationMs <= 0.0 || durationMs > MAX_MARK_MS) return
        recent.addLast(durationMs)
        while (recent.size > WINDOW_MARKS) recent.removeFirst()
        pending += durationMs
        estimate()
    }

    fun gap(durationMs: Double) {
        if (durationMs.isNaN() || durationMs <= 0.0) return
        if (pending.isNotEmpty() && durationMs < LETTER_SPACE_THRESHOLD * unitMs) {
            recentGaps.addLast(durationMs)
            while (recentGaps.size > WINDOW_MARKS) recentGaps.removeFirst()
            estimate()
        }
        silence(durationMs)
    }

    /** Silence still going on, [durationMs] after the last mark. */
    fun silence(durationMs: Double) {
        if (durationMs.isNaN()) return
        if (durationMs >= maxOf(RESET_PAUSE_MS, RESET_PAUSE_UNITS * unitMs)) {
            // Keep the current speed as a starting point but let new durations take over at once.
            flush()
            recent.clear()
            recentGaps.clear()
        }
        if (pending.isNotEmpty() && durationMs >= LETTER_SPACE_THRESHOLD * unitMs) closeLetter()
        if (pending.isEmpty() && wordOpen && durationMs >= WORD_SPACE_THRESHOLD * unitMs) closeWord()
    }

    /** Closes the letter in progress, e.g. when listening stops. */
    fun flush() {
        if (pending.isNotEmpty()) closeLetter()
    }

    /** Erases the text but keeps the speed estimate. */
    fun clear() {
        pending.clear()
        textBuffer.setLength(0)
        rawBuffer.setLength(0)
        wordOpen = false
    }

    private fun closeLetter() {
        val code = pendingCode
        pending.clear()
        if (rawBuffer.isNotEmpty() && !rawBuffer.endsWith(WORD_SEPARATOR)) rawBuffer.append(' ')
        rawBuffer.append(code)
        textBuffer.append(Morse.decodeSymbol(code) ?: UNKNOWN_SIGN)
        wordOpen = true
        if (textBuffer.length > MAX_TEXT_LENGTH) textBuffer.delete(0, textBuffer.length - MAX_TEXT_LENGTH)
        if (rawBuffer.length > MAX_RAW_LENGTH) rawBuffer.delete(0, rawBuffer.length - MAX_RAW_LENGTH)
    }

    private fun closeWord() {
        textBuffer.append(' ')
        rawBuffer.append(WORD_SEPARATOR)
        wordOpen = false
    }

    fun key(event: KeyEvent, durationMs: Double) {
        when (event) {
            KeyEvent.MARK -> mark(durationMs)
            KeyEvent.GAP -> gap(durationMs)
            KeyEvent.SILENCE -> silence(durationMs)
        }
    }

    private fun estimate() {
        if (recent.isEmpty()) return
        val gapSum = recentGaps.sum()
        val samples = recent.size + recentGaps.size
        val groups = splitShortLong(recent)
        if (groups != null) {
            val ratio = groups.longMean / groups.shortMean
            if (ratio <= MAX_DASH_RATIO) {
                setUnit((groups.shortSum + groups.longSum / DASH_UNITS + gapSum) / samples)
                dashThresholdMs = sqrt(groups.shortMean * groups.longMean)
            } else {
                // Too far apart for dots and dashes: keep the reading closest to the current speed.
                val asDots = groups.shortMean
                val asDashes = groups.longMean / DASH_UNITS
                val unit = if (abs(ln(asDots / unitMs)) <= abs(ln(asDashes / unitMs))) asDots else asDashes
                setUnit(unit)
                dashThresholdMs = 2.0 * unitMs
            }
        } else {
            val sum = recent.sum()
            val dots = sum / recent.size < dashThresholdMs
            setUnit(((if (dots) sum else sum / DASH_UNITS) + gapSum) / samples)
            dashThresholdMs = 2.0 * unitMs
        }
    }

    private fun setUnit(value: Double) {
        unitMs = value.coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)
    }
}

class ShortLong(val shortMean: Double, val longMean: Double, val shortSum: Double, val longSum: Double)

/**
 * Two-means clustering of durations on a log scale. Returns null when all durations look alike
 * (longest group less than [MIN_CLUSTER_RATIO] times the shortest).
 */
fun splitShortLong(values: Collection<Double>): ShortLong? {
    if (values.size < 2) return null
    val logs = values.map { ln(it) }
    var low = logs.min()
    var high = logs.max()
    if (high - low < ln(MIN_CLUSTER_RATIO)) return null
    var border = (low + high) / 2.0
    repeat(20) {
        var lowSum = 0.0
        var lowCount = 0
        var highSum = 0.0
        var highCount = 0
        for (value in logs) {
            if (value < border) {
                lowSum += value
                lowCount++
            } else {
                highSum += value
                highCount++
            }
        }
        if (lowCount == 0 || highCount == 0) return null
        low = lowSum / lowCount
        high = highSum / highCount
        val next = (low + high) / 2.0
        if (abs(next - border) < 1e-9) return@repeat
        border = next
    }
    var shortSum = 0.0
    var shortCount = 0
    var longSum = 0.0
    var longCount = 0
    for (value in values) {
        if (ln(value) < border) {
            shortSum += value
            shortCount++
        } else {
            longSum += value
            longCount++
        }
    }
    if (shortCount == 0 || longCount == 0) return null
    val shortMean = shortSum / shortCount
    val longMean = longSum / longCount
    if (longMean / shortMean < MIN_CLUSTER_RATIO) return null
    return ShortLong(shortMean, longMean, shortSum, longSum)
}
