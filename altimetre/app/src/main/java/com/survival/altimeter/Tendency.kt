package com.survival.altimeter

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Barometric tendency over 3 hours, with the bands used in synoptic station reports
 * (Met Office): steady < 0,1 hPa; slowly 0,1 to 1,5; (no adverb) 1,6 to 3,5; quickly 3,6 to 6,0;
 * very rapidly above 6,0 hPa.
 * [direction] is +1 rising, −1 falling, 0 steady; [strength] goes from 0 (steady) to 4 (very rapid).
 */
enum class PressureTrend(val label: String, val advice: String, val direction: Int, val strength: Int) {
    STEADY("Stable", "Pression stable$NBSP: pas de changement de temps notable à court terme.", 0, 0),
    RISING_SLOWLY("Hausse lente", "Hausse lente$NBSP: temps stable ou en légère amélioration.", 1, 1),
    RISING("Hausse", "Hausse$NBSP: amélioration probable.", 1, 2),
    RISING_QUICKLY("Hausse rapide", "Hausse rapide$NBSP: amélioration probable, mais vent fort possible.", 1, 3),
    RISING_VERY_RAPIDLY(
        "Hausse très rapide",
        "Hausse très rapide$NBSP: temps instable, coups de vent probables.",
        1,
        4,
    ),
    FALLING_SLOWLY("Baisse lente", "Baisse lente$NBSP: changement possible, surveillez le ciel.", -1, 1),
    FALLING("Baisse", "Baisse$NBSP: dégradation probable (nuages, pluie).", -1, 2),
    FALLING_QUICKLY("Baisse rapide", "Baisse rapide$NBSP: dégradation probable (vent, pluie, orage).", -1, 3),
    FALLING_VERY_RAPIDLY(
        "Baisse très rapide",
        "Baisse très rapide$NBSP: tempête ou orage probable, mettez-vous à l'abri.",
        -1,
        4,
    );

    /** Strong falls deserve attention: they announce wind, rain or storms within hours. */
    val isAlert: Boolean get() = direction < 0 && strength >= 3

    /** Arrow angle in degrees: 0 points right (steady), −90 straight up, +90 straight down. */
    val arrowDegrees: Float
        get() = -direction * when (strength) {
            0 -> 0f
            1 -> 20f
            2 -> 45f
            3 -> 65f
            else -> 90f
        }

    companion object {
        /** Classifies a 3-hour change, rounded to 0,1 hPa like a station report. */
        fun classify(change3hHpa: Double): PressureTrend {
            val tenths = (abs(change3hHpa) * 10.0).roundToInt()
            val strength = when {
                tenths == 0 -> return STEADY
                tenths <= 15 -> 1
                tenths <= 35 -> 2
                tenths <= 60 -> 3
                else -> 4
            }
            return entries.first { it.strength == strength && it.direction == if (change3hHpa > 0) 1 else -1 }
        }
    }
}

const val TENDENCY_SPAN_MS = 3L * 3_600_000L

/** The latest reading must be this recent for a tendency to describe the current weather. */
private const val LATEST_MAX_AGE_MS = 20L * 60_000L

/** Readings averaged at the end of the span (smooths the per-minute noise). */
private const val END_WINDOW_MS = 10L * 60_000L

/** Readings accepted around the start of the span (background readings are ~15 min apart). */
const val START_WINDOW_MS = 15L * 60_000L

/**
 * Pressure change over [spanMs] ending at the latest reading, scaled to exactly [spanMs].
 * Returns null when there is no recent reading or no reading around the start of the span.
 * [samples] must be sorted by time.
 */
fun pressureChange(samples: List<PressureSample>, nowMs: Long, spanMs: Long = TENDENCY_SPAN_MS): Double? {
    val latest = samples.lastOrNull { it.timeMs <= nowMs + 60_000L } ?: return null
    if (nowMs - latest.timeMs > LATEST_MAX_AGE_MS) return null
    val end = samples.filter { it.timeMs in (latest.timeMs - END_WINDOW_MS)..latest.timeMs }
    val endTime = end.map { it.timeMs.toDouble() }.average()
    val endHpa = end.map { it.hPa }.average()
    val target = endTime - spanMs
    val start = samples.filter { abs(it.timeMs - target) <= START_WINDOW_MS }
    if (start.isEmpty()) return null
    val startTime = start.map { it.timeMs.toDouble() }.average()
    val startHpa = start.map { it.hPa }.average()
    val elapsed = endTime - startTime
    if (elapsed < spanMs / 2.0) return null
    return (endHpa - startHpa) * spanMs / elapsed
}

/** What the tendency card can say. */
sealed interface TendencyStatus {
    data class Ready(val changeHpa: Double, val trend: PressureTrend) : TendencyStatus

    /** Not enough history yet: the tendency will be available in about [remainingMs]. */
    data class Warming(val remainingMs: Long) : TendencyStatus

    /** History is long enough but readings are missing (phone asleep, background logging off). */
    data object Gap : TendencyStatus

    data object NoRecentReading : TendencyStatus
}

fun tendencyStatus(samples: List<PressureSample>, nowMs: Long): TendencyStatus {
    val change = pressureChange(samples, nowMs)
    if (change != null) return TendencyStatus.Ready(change, PressureTrend.classify(change))
    val latest = samples.lastOrNull() ?: return TendencyStatus.Warming(TENDENCY_SPAN_MS)
    if (nowMs - latest.timeMs > LATEST_MAX_AGE_MS) return TendencyStatus.NoRecentReading
    val covered = latest.timeMs - samples.first().timeMs
    return if (covered < TENDENCY_SPAN_MS) TendencyStatus.Warming(TENDENCY_SPAN_MS - covered) else TendencyStatus.Gap
}
