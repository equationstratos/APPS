package com.survival.signals

private const val SRC_ALPHABET = "OACI, Annexe 10, vol. II (radiotéléphonie) ; alphabet commun à l'OTAN et à l'UIT"
private const val G_LETTERS = "Lettres"
private const val G_DIGITS = "Chiffres et nombres"

/** One card per letter and digit of the radiotelephony alphabet, plus the ICAO number words. */
val ALPHABET_ENTRIES: List<Entry> = LETTERS.map { code ->
    Entry(
        id = "lettre-${code.symbol}",
        section = Section.ALPHABET,
        group = G_LETTERS,
        title = code.word,
        summary = code.pronunciation,
        source = SRC_ALPHABET,
        glyph = code.symbol.toString(),
        big = listOf(code.symbol.toString(), code.word),
        keywords = "lettre ${code.symbol}",
    )
} + DIGITS.map { code ->
    Entry(
        id = "chiffre-${code.symbol}",
        section = Section.ALPHABET,
        group = G_DIGITS,
        title = code.word,
        summary = "Se dit pour le chiffre ${code.symbol} (${code.pronunciation}).",
        source = SRC_ALPHABET,
        glyph = code.symbol.toString(),
        big = listOf(code.symbol.toString(), code.word),
        keywords = "chiffre ${code.pronunciation}",
    )
} + listOf(
    Entry(
        id = "chiffre-decimale",
        section = Section.ALPHABET,
        group = G_DIGITS,
        title = DECIMAL_WORD,
        summary = "Virgule décimale : 121,5 se dit WUN TOO WUN DAY-SEE-MAL FIFE.",
        source = SRC_ALPHABET,
        glyph = ",",
        big = listOf(",", DECIMAL_WORD),
        keywords = "virgule decimal point frequence",
    ),
    Entry(
        id = "chiffre-centaine",
        section = Section.ALPHABET,
        group = G_DIGITS,
        title = "HUN-dred",
        summary = "Centaine ronde : 2 500 se dit TOO TOU-SAND FIFE HUN-dred.",
        source = SRC_ALPHABET,
        glyph = "100",
        big = listOf("100", "HUN-dred"),
        keywords = "cent centaine hundred",
    ),
    Entry(
        id = "chiffre-millier",
        section = Section.ALPHABET,
        group = G_DIGITS,
        title = "TOU-SAND",
        summary = "Millier rond : 3 000 se dit TREE TOU-SAND.",
        source = SRC_ALPHABET,
        glyph = "1000",
        big = listOf("1000", "TOU-SAND"),
        keywords = "mille millier thousand",
    ),
)

/** Cards sharing a group title, in catalog order. */
class EntryGroup(val title: String, val entries: List<Entry>)

/** Consecutive entries with the same group, kept in order. */
fun groupsOf(entries: List<Entry>): List<EntryGroup> {
    val groups = ArrayList<EntryGroup>()
    var title: String? = null
    var current = ArrayList<Entry>()
    for (entry in entries) {
        if (entry.group != title) {
            if (current.isNotEmpty()) groups += EntryGroup(title.orEmpty(), current)
            title = entry.group
            current = ArrayList()
        }
        current += entry
    }
    if (current.isNotEmpty()) groups += EntryGroup(title.orEmpty(), current)
    return groups
}

object Catalog {
    /** Every card, section by section in tab order. */
    val entries: List<Entry> = (
        EMERGENCY_ENTRIES + ALPHABET_ENTRIES + GROUND_AIR_ENTRIES + GESTURE_ENTRIES +
            DISTRESS_ENTRIES + RADIO_ENTRIES + ALERT_ENTRIES
        ).sortedBy { it.section.ordinal }

    private val byId: Map<String, Entry> = entries.associateBy { it.id }

    private val bySection: Map<Section, List<Entry>> = entries.groupBy { it.section }

    val index: SearchIndex = SearchIndex(entries)

    fun byId(id: String): Entry? = byId[id]

    fun inSection(section: Section): List<Entry> = bySection[section].orEmpty()
}
