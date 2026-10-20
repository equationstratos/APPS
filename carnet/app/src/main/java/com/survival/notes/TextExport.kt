package com.survival.notes

import java.time.ZoneId

/** Separates notes in the text export. */
const val EXPORT_RULE = "========================================"

/** Text without its leading blank lines and trailing spaces; inner layout is kept. */
internal fun cleanBody(body: String): String =
    body.lines().dropWhile { it.isBlank() }.joinToString("\n").trimEnd()

/** "Précision : ± 12 m · GPS · relevée le 20/10/2026 à 14:05" */
fun locationDetails(location: NoteLocation, zone: ZoneId): String {
    val parts = ArrayList<String>(3)
    location.accuracyM?.let { parts += "Précision : ${formatAccuracy(it)}" }
    parts += location.source.label
    parts += "relevée le ${formatNumericDateTime(location.timeMillis, zone)}"
    return parts.joinToString(" · ")
}

/** Two lines: both coordinate formats, then accuracy, source and time of the fix. */
fun locationBlock(location: NoteLocation, zone: ZoneId): String =
    "Position : ${formatDecimal(location.latitude, location.longitude)} — " +
        "${formatDms(location.latitude, location.longitude)}\n${locationDetails(location, zone)}"

/** "Créée le 19/10/2026 à 09:00 · modifiée le 20/10/2026 à 08:30" (one date if they match). */
fun noteDatesLine(note: Note, zone: ZoneId): String {
    val created = formatNumericDateTime(note.createdAt, zone)
    val modified = formatNumericDateTime(note.modifiedAt, zone)
    return if (created == modified) "Créée le $created" else "Créée le $created · modifiée le $modified"
}

/** Every note in a plain text file, in the order of the list. */
fun exportText(notes: List<Note>, now: Long, zone: ZoneId): String = buildString {
    val sorted = sortNotes(notes.filterNot { it.isEmpty })
    append("Carnet — export du ").append(formatFullDateTime(now, zone)).append('\n')
    append(countNotes(sorted.size)).append('\n')
    for (note in sorted) {
        append('\n').append(EXPORT_RULE).append('\n')
        append(collapseWhitespace(note.title).ifEmpty { UNTITLED }).append('\n')
        if (note.pinned) append("Épinglée\n")
        append(noteDatesLine(note, zone)).append('\n')
        note.location?.let { append(locationBlock(it, zone)).append('\n') }
        val body = cleanBody(note.body)
        if (body.isNotEmpty()) append('\n').append(body).append('\n')
    }
}

/** Text sent by "Partager": title, text and position, separated by blank lines. */
fun shareText(note: Note, zone: ZoneId): String {
    val parts = ArrayList<String>(3)
    collapseWhitespace(note.title).takeIf { it.isNotEmpty() }?.let { parts += it }
    cleanBody(note.body).takeIf { it.isNotEmpty() }?.let { parts += it }
    note.location?.let { parts += locationBlock(it, zone) }
    return parts.joinToString("\n\n")
}

/** "carnet-2026-10-20.txt" */
fun exportFileName(now: Long, zone: ZoneId): String = "carnet-${formatFileDate(now, zone)}.txt"

/** "carnet-sauvegarde-2026-10-20.json" */
fun backupFileName(now: Long, zone: ZoneId): String = "carnet-sauvegarde-${formatFileDate(now, zone)}.json"
