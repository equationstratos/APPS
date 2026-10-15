package com.survival.metronome

import kotlin.math.roundToInt

const val MIN_BPM = 30
const val MAX_BPM = 250
const val DEFAULT_BPM = 120
const val MAX_BEATS = 7
const val DEFAULT_BEATS = 4

fun tempoName(bpm: Int): String = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 66 -> "Larghetto"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 168 -> "Allegro"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

/** Tap tempo: averages the intervals between the last [MAX_TAPS] taps, forgets them after [RESET_MS] idle. */
class TapTempo {
    private val taps = ArrayDeque<Long>()

    /** Records a tap at [nowMs] (monotonic clock) and returns the new tempo, or null until two taps are known. */
    fun tap(nowMs: Long): Int? {
        if (taps.isNotEmpty() && nowMs - taps.last() > RESET_MS) taps.clear()
        taps.addLast(nowMs)
        while (taps.size > MAX_TAPS) taps.removeFirst()
        if (taps.size < 2) return null
        val averageIntervalMs = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
        if (averageIntervalMs <= 0.0) return null
        return (60_000.0 / averageIntervalMs).roundToInt().coerceIn(MIN_BPM, MAX_BPM)
    }

    private companion object {
        const val MAX_TAPS = 4
        const val RESET_MS = 2_000L
    }
}
