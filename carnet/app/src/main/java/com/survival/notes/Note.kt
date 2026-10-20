package com.survival.notes

import java.util.UUID

/** Title shown for a note that has neither a title nor any text. */
const val UNTITLED = "Sans titre"

private const val PREVIEW_MAX_CHARS = 180
private const val TITLE_MAX_CHARS = 80
private val WHITESPACE = Regex("\\s+")

/** Where an attached position comes from. [key] is the stable value written to JSON. */
enum class LocationSource(val key: String, val label: String) {
    GPS("gps", "GPS"),
    NETWORK("network", "Réseau"),
    LAST_KNOWN("last", "Dernière position connue"),
    OTHER("other", "Autre source");

    companion object {
        fun fromKey(key: String?): LocationSource = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** A position attached to a note. [timeMillis] is when the fix was taken (wall clock). */
data class NoteLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val timeMillis: Long,
    val source: LocationSource,
)

data class Note(
    val id: String,
    val title: String = "",
    val body: String = "",
    val createdAt: Long,
    val modifiedAt: Long = createdAt,
    val pinned: Boolean = false,
    val location: NoteLocation? = null,
) {
    /** Nothing worth keeping: such a note is never stored. */
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && location == null
}

fun newNoteId(): String = UUID.randomUUID().toString()

fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

/**
 * Same note with new text. The modification date only moves when the text really changed, and
 * never goes back in time (so a newer copy always wins a merge, even after a clock change).
 */
fun Note.withContent(title: String, body: String, now: Long): Note =
    if (title == this.title && body == this.body) {
        this
    } else {
        copy(title = title, body = body, modifiedAt = maxOf(now, modifiedAt))
    }

fun Note.withLocation(location: NoteLocation?, now: Long): Note =
    if (location == this.location) this else copy(location = location, modifiedAt = maxOf(now, modifiedAt))

/** Pinning is an arrangement of the list, not an edit: the modification date is kept. */
fun Note.withPinned(pinned: Boolean): Note = if (pinned == this.pinned) this else copy(pinned = pinned)

/** Pinned notes first, then the most recently modified. Ties are broken so the order is stable. */
val NOTE_ORDER: Comparator<Note> = compareByDescending<Note> { it.pinned }
    .thenByDescending { it.modifiedAt }
    .thenByDescending { it.createdAt }
    .thenBy { it.id }

fun sortNotes(notes: List<Note>): List<Note> = notes.sortedWith(NOTE_ORDER)

internal fun collapseWhitespace(text: String): String = text.trim().replace(WHITESPACE, " ")

/** Cuts [text] to [max] characters (never inside a surrogate pair) and adds an ellipsis. */
internal fun truncate(text: String, max: Int): String {
    if (text.length <= max) return text
    var end = max
    if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
    return text.substring(0, end).trimEnd() + "…"
}

/** The title, else the first line of text, else [UNTITLED]. */
fun displayTitle(note: Note): String {
    val title = collapseWhitespace(note.title)
    if (title.isNotEmpty()) return title
    val firstLine = note.body.lineSequence().map(::collapseWhitespace).firstOrNull { it.isNotEmpty() }
    return firstLine?.let { truncate(it, TITLE_MAX_CHARS) } ?: UNTITLED
}

/**
 * One-paragraph excerpt for the list. When the first line already serves as the title it is
 * skipped; a note holding only a position shows its coordinates.
 */
fun previewText(note: Note, maxChars: Int = PREVIEW_MAX_CHARS): String {
    val source = if (note.title.isNotBlank()) {
        note.body
    } else {
        note.body.lines().dropWhile { it.isBlank() }.drop(1).joinToString("\n")
    }
    val collapsed = collapseWhitespace(source)
    if (collapsed.isNotEmpty()) return truncate(collapsed, maxChars)
    val location = note.location ?: return ""
    return "Position : " + formatDecimal(location.latitude, location.longitude)
}

/** One note per id: the most recently modified copy (the first one on a tie). */
fun dedupeById(notes: List<Note>): List<Note> {
    val byId = LinkedHashMap<String, Note>()
    for (note in notes) {
        val current = byId[note.id]
        if (current == null || note.modifiedAt > current.modifiedAt) byId[note.id] = note
    }
    return byId.values.toList()
}

data class MergeResult(val notes: List<Note>, val added: Int, val updated: Int, val unchanged: Int)

/**
 * Restores [incoming] into [existing] without ever losing a note: unknown ids are added, a known
 * id is replaced only by a strictly more recent copy, and nothing is deleted. Empty notes are ignored.
 */
fun mergeNotes(existing: List<Note>, incoming: List<Note>): MergeResult {
    val byId = LinkedHashMap<String, Note>()
    for (note in existing) byId[note.id] = note
    var added = 0
    var updated = 0
    var unchanged = 0
    for (note in dedupeById(incoming)) {
        if (note.isEmpty) continue
        val current = byId[note.id]
        when {
            current == null -> {
                byId[note.id] = note
                added++
            }
            note.modifiedAt > current.modifiedAt && note != current -> {
                byId[note.id] = note
                updated++
            }
            else -> unchanged++
        }
    }
    return MergeResult(byId.values.toList(), added, updated, unchanged)
}

data class TextEdit(val text: String, val cursor: Int)

/**
 * Replaces the selection of [text] with [insertion], starting a new line first when the cursor
 * is not already at the beginning of one. The cursor ends right after the insertion.
 */
fun insertOnOwnLine(text: String, selectionStart: Int, selectionEnd: Int, insertion: String): TextEdit {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val prefix = if (start > 0 && text[start - 1] != '\n') "\n" else ""
    val result = text.substring(0, start) + prefix + insertion + text.substring(end)
    return TextEdit(result, start + prefix.length + insertion.length)
}
