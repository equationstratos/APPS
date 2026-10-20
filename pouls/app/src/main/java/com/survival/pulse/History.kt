package com.survival.pulse

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Number of measurements kept. */
internal const val HISTORY_SIZE = 20

/**
 * Plausible stored rates. Wide enough for every result the app can produce: a slow manual count
 * (5 taps over 15 s gives 16 BPM) and the fastest taps (240 BPM).
 */
private const val MIN_STORED_BPM = 10
private const val MAX_STORED_BPM = 300

internal enum class MeasureContext(val key: String, val label: String) {
    NONE("aucun", "Non précisé"),
    REST("repos", "Au repos"),
    EFFORT("effort", "Après effort");

    companion object {
        fun fromKey(key: String?): MeasureContext? = entries.firstOrNull { it.key == key }
    }
}

internal enum class MeasureMethod(val key: String, val label: String) {
    CAMERA("camera", "Caméra"),
    MANUAL("manuel", "Manuel");

    companion object {
        fun fromKey(key: String?): MeasureMethod? = entries.firstOrNull { it.key == key }
    }
}

/** One saved measurement; [timeMs] (epoch milliseconds) doubles as its identifier. */
internal data class HistoryEntry(
    val timeMs: Long,
    val bpm: Int,
    val context: MeasureContext,
    val method: MeasureMethod,
)

/**
 * Plain-text storage format, one entry per line: "timeMs;bpm;context;method".
 * Unreadable lines are skipped, so a damaged value never prevents the app from starting.
 */
internal object HistoryCodec {
    private const val SEPARATOR = ";"

    fun encode(entries: List<HistoryEntry>): String = entries.joinToString("\n") {
        listOf(it.timeMs.toString(), it.bpm.toString(), it.context.key, it.method.key).joinToString(SEPARATOR)
    }

    fun decode(text: String?): List<HistoryEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val entries = text.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(SEPARATOR)
            if (parts.size != 4) return@mapNotNull null
            val time = parts[0].toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
            val bpm = parts[1].toIntOrNull()?.takeIf { it in MIN_STORED_BPM..MAX_STORED_BPM } ?: return@mapNotNull null
            val context = MeasureContext.fromKey(parts[2]) ?: return@mapNotNull null
            val method = MeasureMethod.fromKey(parts[3]) ?: return@mapNotNull null
            HistoryEntry(time, bpm, context, method)
        }.toList()
        return entries.distinctBy { it.timeMs }.sortedByDescending { it.timeMs }.take(HISTORY_SIZE)
    }
}

/** [timeMs], moved forward by a few milliseconds if an entry already uses it as identifier. */
internal fun List<HistoryEntry>.freeTime(timeMs: Long): Long {
    var time = timeMs
    while (any { it.timeMs == time }) time++
    return time
}

/** Adds [entry] first (newest first), keeping identifiers unique and at most [HISTORY_SIZE] entries. */
internal fun List<HistoryEntry>.plusEntry(entry: HistoryEntry): List<HistoryEntry> =
    (listOf(entry.copy(timeMs = freeTime(entry.timeMs))) + this).sortedByDescending { it.timeMs }.take(HISTORY_SIZE)

internal fun List<HistoryEntry>.withContext(timeMs: Long, context: MeasureContext): List<HistoryEntry> =
    map { if (it.timeMs == timeMs) it.copy(context = context) else it }

internal fun List<HistoryEntry>.without(timeMs: Long): List<HistoryEntry> = filterNot { it.timeMs == timeMs }

/** "1 mesure enregistrée", "12 mesures enregistrées". */
internal fun historyCountText(count: Int): String =
    if (count > 1) "$count${NBSP}mesures enregistrées" else "$count${NBSP}mesure enregistrée"

/** Confirmation text before clearing the whole history. */
internal fun clearHistoryText(count: Int): String =
    if (count > 1) {
        "Les $count${NBSP}mesures enregistrées seront supprimées définitivement."
    } else {
        "La mesure enregistrée sera supprimée définitivement."
    }

/** Second line of a history entry: "Caméra · Au repos", or the method alone without context. */
internal fun entryDetails(entry: HistoryEntry): String =
    if (entry.context == MeasureContext.NONE) entry.method.label else "${entry.method.label} · ${entry.context.label}"

private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)

/** "Aujourd'hui à 14:05", "Hier à 09:12" or "12/10/2026 à 08:30" (24 h clock). */
internal fun formatEntryDate(timeMs: Long, nowMs: Long, zone: ZoneId): String {
    val moment = Instant.ofEpochMilli(timeMs).atZone(zone)
    val day = moment.toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val dayText = when (day) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> day.format(DateFormat)
    }
    return "$dayText à ${moment.format(TimeFormat)}"
}

/** Clock time only, for accessibility labels ("14:05"). */
internal fun formatEntryTime(timeMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(timeMs).atZone(zone).format(TimeFormat)

/** Date used in spoken labels, e.g. "le 12/10/2026". */
internal fun spokenDay(timeMs: Long, nowMs: Long, zone: ZoneId): String {
    val day: LocalDate = Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when (day) {
        today -> "aujourd'hui"
        today.minusDays(1) -> "hier"
        else -> "le " + day.format(DateFormat)
    }
}
