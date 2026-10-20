package com.survival.checklist

import java.util.Locale

// Pure data model and operations (no Android imports): every operation returns a new
// immutable value, so the UI state, the undo records and the JSON snapshot never alias.

data class CheckItem(val id: Long, val text: String, val checked: Boolean = false)

data class Section(val id: Long, val title: String, val items: List<CheckItem> = emptyList()) {
    val progress: Progress get() = Progress(items.count { it.checked }, items.size)
}

data class Checklist(
    val id: Long,
    val name: String,
    val sections: List<Section> = emptyList(),
    val hideChecked: Boolean = false,
    val templateKey: String? = null
) {
    val progress: Progress
        get() = Progress(sections.sumOf { s -> s.items.count { it.checked } }, sections.sumOf { it.items.size })

    fun containsText(text: String, exceptItemId: Long? = null): Boolean {
        val key = textKey(text)
        return sections.any { s -> s.items.any { it.id != exceptItemId && textKey(it.text) == key } }
    }
}

data class Progress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else done.coerceIn(0, total).toFloat() / total
    /** Rounded down, so 100 % only ever shows when everything is checked. */
    val percent: Int get() = if (total <= 0) 0 else done.coerceIn(0, total) * 100 / total
    val isComplete: Boolean get() = total > 0 && done >= total
}

data class Library(val lists: List<Checklist> = emptyList(), val nextId: Long = 1L) {
    fun list(id: Long): Checklist? = lists.firstOrNull { it.id == id }
}

data class RemovedList(val index: Int, val list: Checklist)
data class RemovedSection(val listId: Long, val index: Int, val section: Section)
data class RemovedItem(val listId: Long, val sectionId: Long, val index: Int, val item: CheckItem)

object Limits {
    const val NAME_MAX = 60
    const val SECTION_MAX = 60
    const val ITEM_MAX = 200
}

const val UNTITLED = "Sans titre"
const val DEFAULT_SECTION = "Articles"

private val SPACES = Regex("\\s+")
private val COPY_SUFFIX = Regex("\\s\\(copie(?: \\d+)?\\)$")
private val NUMBER_SUFFIX = Regex("\\s\\(\\d+\\)$")

/** Collapses whitespace (including line breaks), trims and caps the length. */
fun cleanText(raw: String, max: Int): String = raw.replace(SPACES, " ").trim().take(max).trim()

/** Comparison key: same text regardless of case and spacing. */
fun textKey(text: String): String = text.replace(SPACES, " ").trim().lowercase(Locale.ROOT)

/** "0 article", "1 article", "2 articles" (French: 0 and 1 are singular). */
fun countLabel(n: Int, singular: String, plural: String): String = "$n ${if (n <= 1) singular else plural}"

/** First free name among "base", "base (2)", "base (3)"… (case-insensitive). */
fun uniqueName(base: String, taken: Collection<String>): String {
    val keys = taken.map(::textKey).toSet()
    val clean = cleanText(base, Limits.NAME_MAX).ifEmpty { UNTITLED }
    if (textKey(clean) !in keys) return clean
    var n = 2
    while (true) {
        val suffix = " ($n)"
        val candidate = clean.take(Limits.NAME_MAX - suffix.length).trimEnd() + suffix
        if (textKey(candidate) !in keys) return candidate
        n++
    }
}

/** "Kit" → "Kit (copie)", then "Kit (copie 2)"…; copying a copy does not stack suffixes. */
fun copyName(original: String, taken: Collection<String>): String {
    val keys = taken.map(::textKey).toSet()
    val base = original.replace(COPY_SUFFIX, "").replace(NUMBER_SUFFIX, "").trim().ifEmpty { UNTITLED }
    var n = 1
    while (true) {
        val suffix = if (n == 1) " (copie)" else " (copie $n)"
        val candidate = base.take(Limits.NAME_MAX - suffix.length).trimEnd() + suffix
        if (textKey(candidate) !in keys) return candidate
        n++
    }
}

// ---------- Checklist-level operations (no new ids needed) ----------

private inline fun Checklist.mapItems(f: (CheckItem) -> CheckItem): Checklist =
    copy(sections = sections.map { s -> s.copy(items = s.items.map(f)) })

fun Checklist.toggle(itemId: Long): Checklist =
    mapItems { if (it.id == itemId) it.copy(checked = !it.checked) else it }

fun Checklist.uncheckAll(): Checklist = mapItems { if (it.checked) it.copy(checked = false) else it }

fun Checklist.editItem(itemId: Long, text: String): Checklist {
    val clean = cleanText(text, Limits.ITEM_MAX)
    if (clean.isEmpty() || containsText(clean, exceptItemId = itemId)) return this
    return mapItems { if (it.id == itemId) it.copy(text = clean) else it }
}

/** Moves an item by [delta] positions inside its own section; no-op at the bounds. */
fun Checklist.moveItem(itemId: Long, delta: Int): Checklist = copy(sections = sections.map { s ->
    val from = s.items.indexOfFirst { it.id == itemId }
    val to = from + delta
    if (from < 0 || delta == 0 || to !in s.items.indices) s
    else s.copy(items = s.items.toMutableList().apply { add(to, removeAt(from)) })
})

fun Checklist.renameSection(sectionId: Long, title: String): Checklist {
    val clean = cleanText(title, Limits.SECTION_MAX)
    if (clean.isEmpty()) return this
    return copy(sections = sections.map { if (it.id == sectionId) it.copy(title = clean) else it })
}

fun Checklist.toShareText(sources: List<String> = emptyList()): String = buildString {
    val p = progress
    append(name)
    append('\n')
    append(
        if (p.total == 0) "Liste vide"
        else "Progression : ${p.done}/${p.total} (${p.percent} %)"
    )
    for (s in sections) {
        if (s.items.isEmpty()) continue
        append("\n\n")
        append(s.title)
        for (item in s.items) {
            append('\n')
            append(if (item.checked) "☑ " else "☐ ")
            append(item.text)
        }
    }
    if (sources.isNotEmpty()) {
        append("\n\nD'après : ")
        append(sources.joinToString(" ; "))
    }
}

// ---------- Library-level operations ----------

fun Library.update(listId: Long, f: (Checklist) -> Checklist): Library =
    copy(lists = lists.map { if (it.id == listId) f(it) else it })

fun Library.createEmpty(name: String): Pair<Library, Long> {
    val id = nextId
    val list = Checklist(id = id, name = uniqueName(name, lists.map { it.name }))
    return copy(lists = lists + list, nextId = id + 1) to id
}

fun Library.createFromTemplate(template: Template): Pair<Library, Long> {
    var next = nextId
    val listId = next++
    val sections = template.sections.map { ts ->
        val sectionId = next++
        Section(sectionId, ts.title, ts.items.map { CheckItem(next++, it) })
    }
    val list = Checklist(
        id = listId,
        name = uniqueName(template.name, lists.map { it.name }),
        sections = sections,
        templateKey = template.key
    )
    return copy(lists = lists + list, nextId = next) to listId
}

/** Deep copy with fresh ids, inserted right after the original. */
fun Library.duplicate(listId: Long): Pair<Library, Long>? {
    val index = lists.indexOfFirst { it.id == listId }
    if (index < 0) return null
    val source = lists[index]
    var next = nextId
    val copyId = next++
    val clone = source.copy(
        id = copyId,
        name = copyName(source.name, lists.map { it.name }),
        sections = source.sections.map { s ->
            val sectionId = next++
            s.copy(id = sectionId, items = s.items.map { it.copy(id = next++) })
        }
    )
    val newLists = lists.toMutableList().apply { add(index + 1, clone) }
    return copy(lists = newLists, nextId = next) to copyId
}

fun Library.rename(listId: Long, name: String): Library {
    val clean = cleanText(name, Limits.NAME_MAX)
    if (clean.isEmpty()) return this
    return update(listId) { it.copy(name = clean) }
}

fun Library.removeList(listId: Long): Pair<Library, RemovedList>? {
    val index = lists.indexOfFirst { it.id == listId }
    if (index < 0) return null
    return copy(lists = lists.filterIndexed { i, _ -> i != index }) to RemovedList(index, lists[index])
}

fun Library.restoreList(removed: RemovedList): Library {
    if (lists.any { it.id == removed.list.id }) return this
    val at = removed.index.coerceIn(0, lists.size)
    return copy(
        lists = lists.toMutableList().apply { add(at, removed.list) },
        nextId = maxOf(nextId, maxId(removed.list) + 1)
    )
}

/** Adds an item to an existing section. Blank or duplicate text is ignored. */
fun Library.addItem(listId: Long, sectionId: Long, text: String): Library {
    val list = list(listId) ?: return this
    val clean = cleanText(text, Limits.ITEM_MAX)
    if (clean.isEmpty() || list.containsText(clean) || list.sections.none { it.id == sectionId }) return this
    val id = nextId
    return copy(nextId = id + 1).update(listId) { l ->
        l.copy(sections = l.sections.map { if (it.id == sectionId) it.copy(items = it.items + CheckItem(id, clean)) else it })
    }
}

/** Adds an item to the section named [sectionTitle], creating that section (at the end) if needed. */
fun Library.addItemInSection(listId: Long, sectionTitle: String, text: String): Library {
    val list = list(listId) ?: return this
    val title = cleanText(sectionTitle, Limits.SECTION_MAX).ifEmpty { DEFAULT_SECTION }
    val existing = list.sections.firstOrNull { textKey(it.title) == textKey(title) }
    if (existing != null) return addItem(listId, existing.id, text)
    val clean = cleanText(text, Limits.ITEM_MAX)
    if (clean.isEmpty() || list.containsText(clean)) return this
    val sectionId = nextId
    val itemId = nextId + 1
    return copy(nextId = nextId + 2).update(listId) { l ->
        l.copy(sections = l.sections + Section(sectionId, title, listOf(CheckItem(itemId, clean))))
    }
}

fun Library.removeItem(listId: Long, itemId: Long): Pair<Library, RemovedItem>? {
    val list = list(listId) ?: return null
    for (s in list.sections) {
        val index = s.items.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val removed = RemovedItem(listId, s.id, index, s.items[index])
            val next = update(listId) { l ->
                l.copy(sections = l.sections.map { if (it.id == s.id) it.copy(items = it.items.filter { i -> i.id != itemId }) else it })
            }
            return next to removed
        }
    }
    return null
}

fun Library.restoreItem(removed: RemovedItem): Library {
    val list = list(removed.listId) ?: return this
    if (list.sections.any { s -> s.items.any { it.id == removed.item.id } }) return this
    if (list.sections.none { it.id == removed.sectionId }) return this
    return update(removed.listId) { l ->
        l.copy(sections = l.sections.map { s ->
            if (s.id != removed.sectionId) s
            else s.copy(items = s.items.toMutableList().apply { add(removed.index.coerceIn(0, size), removed.item) })
        })
    }
}

fun Library.removeSection(listId: Long, sectionId: Long): Pair<Library, RemovedSection>? {
    val list = list(listId) ?: return null
    val index = list.sections.indexOfFirst { it.id == sectionId }
    if (index < 0) return null
    val removed = RemovedSection(listId, index, list.sections[index])
    return update(listId) { l -> l.copy(sections = l.sections.filter { it.id != sectionId }) } to removed
}

fun Library.restoreSection(removed: RemovedSection): Library {
    val list = list(removed.listId) ?: return this
    if (list.sections.any { it.id == removed.section.id }) return this
    return update(removed.listId) { l ->
        l.copy(sections = l.sections.toMutableList().apply { add(removed.index.coerceIn(0, size), removed.section) })
    }
}

private fun maxId(list: Checklist): Long =
    maxOf(list.id, list.sections.maxOfOrNull { s -> maxOf(s.id, s.items.maxOfOrNull { it.id } ?: 0L) } ?: 0L)

/**
 * Repairs data read from disk: blank or overlong texts, duplicate or non-positive ids
 * (which would crash keyed lazy lists) and a stale id counter.
 */
fun Library.sanitized(): Library {
    val cleaned = lists.map { l ->
        l.copy(
            name = cleanText(l.name, Limits.NAME_MAX).ifEmpty { UNTITLED },
            sections = l.sections.map { s ->
                s.copy(
                    title = cleanText(s.title, Limits.SECTION_MAX).ifEmpty { DEFAULT_SECTION },
                    items = s.items.mapNotNull { i ->
                        val t = cleanText(i.text, Limits.ITEM_MAX)
                        if (t.isEmpty()) null else i.copy(text = t)
                    }
                )
            }
        )
    }
    val ids = ArrayList<Long>()
    for (l in cleaned) {
        ids += l.id
        for (s in l.sections) {
            ids += s.id
            for (i in s.items) ids += i.id
        }
    }
    val valid = ids.all { it > 0 } && ids.toSet().size == ids.size
    if (valid) return Library(cleaned, maxOf(nextId, (ids.maxOrNull() ?: 0L) + 1))
    var next = 1L
    val renumbered = cleaned.map { l ->
        l.copy(id = next++, sections = l.sections.map { s ->
            s.copy(id = next++, items = s.items.map { it.copy(id = next++) })
        })
    }
    return Library(renumbered, next)
}
