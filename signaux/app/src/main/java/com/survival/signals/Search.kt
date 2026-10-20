package com.survival.signals

import java.text.Normalizer
import java.util.Locale

private val SEARCH_MARKS = Regex("\\p{Mn}+")
private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")

/** Words too common to narrow a search, ignored unless the query has nothing else. */
private val STOP_WORDS = setOf(
    "a", "au", "aux", "d", "de", "des", "du", "en", "et", "l", "la", "le", "les", "ou", "par", "pour", "sur",
    "un", "une",
)

/** Longest query taken into account, in characters. */
const val SEARCH_MAX_LENGTH = 80

/** Lower-case ASCII form used for matching: "Détresse — Œil" → "detresse — oeil". */
fun foldForSearch(text: String): String {
    val expanded = text
        .replace("œ", "oe").replace("Œ", "OE")
        .replace("æ", "ae").replace("Æ", "AE")
        .replace("ß", "ss")
    return SEARCH_MARKS.replace(Normalizer.normalize(expanded, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
}

/** Searchable words of [text]: accents removed, split on anything that is not a letter or a digit. */
fun searchWords(text: String): List<String> = foldForSearch(text).split(NOT_ALPHANUMERIC).filter { it.isNotEmpty() }

/** Query words, without stop words unless the query is made of stop words only. */
fun queryTokens(query: String): List<String> {
    val words = searchWords(query.take(SEARCH_MAX_LENGTH)).distinct()
    val meaningful = words.filter { it !in STOP_WORDS }
    return meaningful.ifEmpty { words }
}

/**
 * Full-text search over the catalog. A card matches when every query word starts one of its words;
 * results are ranked by where the words were found (title and number first), then by catalog order.
 */
class SearchIndex(entries: List<Entry>) {
    private class Field(val weight: Int, val words: Set<String>)

    private class Indexed(val entry: Entry, val order: Int, val fields: List<Field>)

    private val indexed: List<Indexed> = entries.mapIndexed { order, entry ->
        val numberWords = listOfNotNull(entry.dial, entry.display?.filter { it.isDigit() }) +
            searchWords(entry.display.orEmpty())
        Indexed(
            entry,
            order,
            listOf(
                Field(WEIGHT_GLYPH, searchWords(entry.glyph.orEmpty()).toSet()),
                Field(WEIGHT_TITLE, (searchWords(entry.title) + numberWords).toSet()),
                Field(WEIGHT_KEYWORDS, (searchWords(entry.keywords) + searchWords(entry.group)).toSet()),
                Field(WEIGHT_SUMMARY, (searchWords(entry.summary) + searchWords(entry.unofficial.orEmpty())).toSet()),
                Field(
                    WEIGHT_DETAILS,
                    (
                        entry.details.flatMap(::searchWords) + searchWords(entry.source) +
                            searchWords(entry.section.tab) + searchWords(entry.section.heading)
                        ).toSet()
                ),
            )
        )
    }

    fun search(query: String): List<Entry> {
        val tokens = queryTokens(query)
        if (tokens.isEmpty()) return emptyList()
        val hits = ArrayList<Pair<Indexed, Int>>()
        for (item in indexed) {
            var total = 0
            var matchesAll = true
            for (token in tokens) {
                val best = item.fields.maxOf { field -> field.weight * tokenScore(token, field.words) }
                if (best == 0) {
                    matchesAll = false
                    break
                }
                total += best
            }
            if (matchesAll) hits += item to total
        }
        return hits
            .sortedWith(compareByDescending<Pair<Indexed, Int>> { it.second }.thenBy { it.first.order })
            .map { it.first.entry }
    }

    private companion object {
        const val WEIGHT_GLYPH = 5
        const val WEIGHT_TITLE = 4
        const val WEIGHT_KEYWORDS = 3
        const val WEIGHT_SUMMARY = 2
        const val WEIGHT_DETAILS = 1
    }
}

/** 2 when [token] is one of [words], 1 when it starts one of them, 0 otherwise. */
private fun tokenScore(token: String, words: Set<String>): Int = when {
    token in words -> 2
    words.any { it.startsWith(token) } -> 1
    else -> 0
}
