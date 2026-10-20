package com.survival.notes

import java.text.Normalizer

/**
 * Folds [text] for searching: lower case, accents removed (é → e, ç → c), ligatures expanded
 * (œ → oe, æ → ae, ß → ss), typographic apostrophes and every kind of space unified.
 */
fun normalizeForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
    val out = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
        when (ch) {
            'œ', 'Œ' -> out.append("oe")
            'æ', 'Æ' -> out.append("ae")
            'ß' -> out.append("ss")
            '’', '‘', 'ʼ', '`', '´' -> out.append('\'')
            else -> out.append(if (Character.isWhitespace(ch) || Character.isSpaceChar(ch)) ' ' else ch.lowercaseChar())
        }
    }
    return out.toString()
}

/** Words of the query, folded; every word must appear in a note for it to match. */
fun searchTokens(query: String): List<String> =
    normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }

/** Folded title and text of each note, computed once per list of notes. */
class SearchIndex(notes: List<Note>) {
    private val entries: List<Pair<Note, String>> =
        notes.map { it to normalizeForSearch(it.title + "\n" + it.body) }

    /** Matching notes in their original order; a blank query matches everything. */
    fun filter(query: String): List<Note> {
        val tokens = searchTokens(query)
        if (tokens.isEmpty()) return entries.map { it.first }
        return entries.filter { (_, haystack) -> tokens.all { haystack.contains(it) } }.map { it.first }
    }
}

fun searchNotes(notes: List<Note>, query: String): List<Note> = SearchIndex(notes).filter(query)
