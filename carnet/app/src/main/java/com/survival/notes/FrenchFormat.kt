package com.survival.notes

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val MONTHS = arrayOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
)
private val WEEKDAYS = arrayOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L
private const val THOUSANDS_SEPARATOR = '\u202F'

private fun zoned(millis: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/** "1er mars", "20 octobre". */
private fun dayAndMonth(time: ZonedDateTime): String {
    val day = if (time.dayOfMonth == 1) "1er" else time.dayOfMonth.toString()
    return "$day ${MONTHS[time.monthValue - 1]}"
}

/** "14:05" (24 h). */
fun formatClock(millis: Long, zone: ZoneId): String {
    val time = zoned(millis, zone)
    return "${time.hour.twoDigits()}:${time.minute.twoDigits()}"
}

/** "20/10/2026" */
fun formatNumericDate(millis: Long, zone: ZoneId): String {
    val time = zoned(millis, zone)
    return "${time.dayOfMonth.twoDigits()}/${time.monthValue.twoDigits()}/${time.year}"
}

/** "20/10/2026 à 14:05" */
fun formatNumericDateTime(millis: Long, zone: ZoneId): String =
    "${formatNumericDate(millis, zone)} à ${formatClock(millis, zone)}"

/** "mardi 20 octobre 2026" */
fun formatLongDate(millis: Long, zone: ZoneId): String {
    val time = zoned(millis, zone)
    return "${WEEKDAYS[time.dayOfWeek.value - 1]} ${dayAndMonth(time)} ${time.year}"
}

/** "mardi 20 octobre 2026 à 14:05" */
fun formatFullDateTime(millis: Long, zone: ZoneId): String =
    "${formatLongDate(millis, zone)} à ${formatClock(millis, zone)}"

/**
 * Date for the list: "À l'instant", "Il y a 5 min", "Aujourd'hui à 14:05", "Hier à 09:12",
 * "3 mars à 18:40" this year, "3 mars 2025" before. A date in the future (clock changed, note
 * restored from another device) is shown in full.
 */
fun formatRelative(millis: Long, now: Long, zone: ZoneId): String {
    val elapsed = now - millis
    if (elapsed < -MINUTE_MS) return formatNumericDateTime(millis, zone)
    if (elapsed < MINUTE_MS) return "À l'instant"
    if (elapsed < HOUR_MS) return "Il y a ${elapsed / MINUTE_MS} min"
    val time = zoned(millis, zone)
    val today = zoned(now, zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(time.toLocalDate(), today)
    val clock = formatClock(millis, zone)
    return when {
        days == 0L -> "Aujourd'hui à $clock"
        days == 1L -> "Hier à $clock"
        time.year == today.year -> "${dayAndMonth(time)} à $clock"
        else -> "${dayAndMonth(time)} ${time.year}"
    }
}

/** Inserted by "Horodater": "[20/10/2026 14:05] ". */
fun formatTimestampStamp(millis: Long, zone: ZoneId): String =
    "[${formatNumericDate(millis, zone)} ${formatClock(millis, zone)}] "

/** "2026-10-20", sorts well in file names. */
fun formatFileDate(millis: Long, zone: ZoneId): String {
    val time = zoned(millis, zone)
    return "${time.year}-${time.monthValue.twoDigits()}-${time.dayOfMonth.twoDigits()}"
}

/** "1 234" with a narrow non-breaking space between thousands. */
fun formatInt(value: Int): String {
    val digits = abs(value.toLong()).toString()
    val out = StringBuilder(digits.length + digits.length / 3)
    for ((index, digit) in digits.withIndex()) {
        if (index > 0 && (digits.length - index) % 3 == 0) out.append(THOUSANDS_SEPARATOR)
        out.append(digit)
    }
    return (if (value < 0) "-" else "") + out
}

/** French plural: 0 and 1 take the singular. */
fun plural(count: Int, one: String, many: String): String = if (abs(count) <= 1) one else many

/** "Aucune note", "1 note", "12 notes". */
fun countNotes(count: Int): String = when {
    count <= 0 -> "Aucune note"
    else -> "${formatInt(count)} ${plural(count, "note", "notes")}"
}

/** Line under the title of the list. */
fun listSubtitle(total: Int, shown: Int, searching: Boolean): String = when {
    !searching -> countNotes(total)
    shown == 0 -> "Aucun résultat"
    else -> "${formatInt(shown)} ${plural(shown, "résultat", "résultats")} sur ${formatInt(total)}"
}

/** "Restauration terminée : 2 ajoutées, 1 mise à jour, 3 inchangées." */
fun restoreSummary(result: MergeResult): String {
    val parts = ArrayList<String>(3)
    if (result.added > 0) parts += "${formatInt(result.added)} ${plural(result.added, "ajoutée", "ajoutées")}"
    if (result.updated > 0) parts += "${formatInt(result.updated)} ${plural(result.updated, "mise à jour", "mises à jour")}"
    if (result.unchanged > 0) {
        parts += "${formatInt(result.unchanged)} ${plural(result.unchanged, "inchangée", "inchangées")}"
    }
    if (parts.isEmpty()) return "La sauvegarde ne contient aucune note."
    return "Restauration terminée : ${parts.joinToString(", ")}."
}
