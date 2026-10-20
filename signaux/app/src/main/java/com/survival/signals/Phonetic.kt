package com.survival.signals

import java.text.Normalizer

/**
 * A radiotelephony code word. For letters, [pronunciation] follows the ICAO transcription (Annex 10,
 * vol. II), stressed syllables in capitals. For digits, [word] is the ICAO spoken form and
 * [pronunciation] the French name of the digit.
 */
class CodeWord(val symbol: Char, val word: String, val pronunciation: String)

/** International radiotelephony spelling alphabet (NATO / ICAO / ITU), spelled officially "Alfa" and "Juliett". */
val LETTERS: List<CodeWord> = listOf(
    CodeWord('A', "Alfa", "AL fah"),
    CodeWord('B', "Bravo", "BRAH voh"),
    CodeWord('C', "Charlie", "CHAR lee ou SHAR lee"),
    CodeWord('D', "Delta", "DELL tah"),
    CodeWord('E', "Echo", "ECK oh"),
    CodeWord('F', "Foxtrot", "FOKS trot"),
    CodeWord('G', "Golf", "GOLF"),
    CodeWord('H', "Hotel", "hoh TELL"),
    CodeWord('I', "India", "IN dee ah"),
    CodeWord('J', "Juliett", "JEW lee ETT"),
    CodeWord('K', "Kilo", "KEY loh"),
    CodeWord('L', "Lima", "LEE mah"),
    CodeWord('M', "Mike", "MIKE"),
    CodeWord('N', "November", "no VEM ber"),
    CodeWord('O', "Oscar", "OSS cah"),
    CodeWord('P', "Papa", "pah PAH"),
    CodeWord('Q', "Quebec", "keh BECK"),
    CodeWord('R', "Romeo", "ROW me oh"),
    CodeWord('S', "Sierra", "see AIR rah"),
    CodeWord('T', "Tango", "TANG go"),
    CodeWord('U', "Uniform", "YOU nee form ou OO nee form"),
    CodeWord('V', "Victor", "VIK tah"),
    CodeWord('W', "Whiskey", "WISS key"),
    CodeWord('X', "X-ray", "ECKS ray"),
    CodeWord('Y', "Yankee", "YANG key"),
    CodeWord('Z', "Zulu", "ZOO loo"),
)

/** ICAO pronunciation of the digits (Annex 10, vol. II), with the French name of each digit. */
val DIGITS: List<CodeWord> = listOf(
    CodeWord('0', "ZE-RO", "zéro"),
    CodeWord('1', "WUN", "un"),
    CodeWord('2', "TOO", "deux"),
    CodeWord('3', "TREE", "trois"),
    CodeWord('4', "FOW-er", "quatre"),
    CodeWord('5', "FIFE", "cinq"),
    CodeWord('6', "SIX", "six"),
    CodeWord('7', "SEV-en", "sept"),
    CodeWord('8', "AIT", "huit"),
    CodeWord('9', "NIN-er", "neuf"),
)

/** ICAO word for the decimal separator, as in "WUN TOO WUN DAY-SEE-MAL FIFE" for 121,5. */
const val DECIMAL_WORD = "DAY-SEE-MAL"

/** Longest text accepted by the spelling tool. */
const val SPELL_MAX_LENGTH = 120

/** French names read out for punctuation that has no code word. */
private val SYMBOL_NAMES: Map<Char, String> = mapOf(
    '-' to "tiret",
    '\'' to "apostrophe",
    '’' to "apostrophe",
    '.' to "point",
    ',' to "virgule",
    '@' to "arobase",
    '/' to "barre oblique",
    '_' to "tiret bas",
    '+' to "plus",
    ':' to "deux-points",
    '#' to "dièse",
)

private val LETTER_BY_SYMBOL: Map<Char, CodeWord> = LETTERS.associateBy { it.symbol }
private val DIGIT_BY_SYMBOL: Map<Char, CodeWord> = DIGITS.associateBy { it.symbol }
private val COMBINING_MARKS = Regex("\\p{Mn}+")

fun letterWord(symbol: Char): String? = LETTER_BY_SYMBOL[symbol.uppercaseChar()]?.word

enum class TokenKind { LETTER, DIGIT, DECIMAL, SYMBOL, SPACE, OTHER }

/**
 * One spelled character. [source] is the character as typed ("É"); [words] are what is said for it
 * (an "Œ" gives Oscar + Echo, a "-" gives "tiret"). OTHER characters have no word and are read as they are.
 */
class SpellToken(val source: String, val words: List<String>, val kind: TokenKind)

/** One typed word and what to say for it: "Paris" → "Papa Alfa Romeo India Sierra". */
class SpellRow(val typed: String, val spoken: String)

/** Latin letters A–Z hidden behind accents and ligatures ("é" → "E", "œ" → "OE", "ß" → "SS"). */
internal fun baseLetters(source: String): String {
    val expanded = source
        .replace("œ", "oe").replace("Œ", "OE")
        .replace("æ", "ae").replace("Æ", "AE")
        .replace("ß", "ss")
    val stripped = COMBINING_MARKS.replace(Normalizer.normalize(expanded, Normalizer.Form.NFD), "")
    return stripped.uppercase().filter { it in 'A'..'Z' }
}

private fun isDigitUnit(unit: String?): Boolean = unit != null && unit.length == 1 && unit[0] in '0'..'9'

/**
 * Spells [text] with the radiotelephony alphabet. Runs of blanks become a single SPACE token,
 * leading and trailing blanks are dropped, and a comma or a point between two digits is read
 * as the ICAO decimal.
 */
fun spell(text: String): List<SpellToken> {
    val units = ArrayList<String>()
    var i = 0
    while (i < text.length) {
        val n = Character.charCount(text.codePointAt(i))
        units += text.substring(i, minOf(i + n, text.length))
        i += n
    }
    val tokens = ArrayList<SpellToken>()
    for ((index, unit) in units.withIndex()) {
        val c = unit[0]
        val symbolName = if (unit.length == 1) SYMBOL_NAMES[c] else null
        when {
            unit.isBlank() -> {
                if (tokens.isNotEmpty() && tokens.last().kind != TokenKind.SPACE) {
                    tokens += SpellToken(" ", emptyList(), TokenKind.SPACE)
                }
            }
            isDigitUnit(unit) -> {
                tokens += SpellToken(unit, listOf(DIGIT_BY_SYMBOL.getValue(c).word), TokenKind.DIGIT)
            }
            (c == ',' || c == '.') && tokens.lastOrNull()?.kind == TokenKind.DIGIT &&
                isDigitUnit(units.getOrNull(index + 1)) -> {
                tokens += SpellToken(unit, listOf(DECIMAL_WORD), TokenKind.DECIMAL)
            }
            symbolName != null -> tokens += SpellToken(unit, listOf(symbolName), TokenKind.SYMBOL)
            else -> {
                val letters = if (Character.isLetter(unit.codePointAt(0))) baseLetters(unit) else ""
                tokens += if (letters.isNotEmpty()) {
                    SpellToken(unit, letters.map { LETTER_BY_SYMBOL.getValue(it).word }, TokenKind.LETTER)
                } else {
                    SpellToken(unit, emptyList(), TokenKind.OTHER)
                }
            }
        }
    }
    if (tokens.lastOrNull()?.kind == TokenKind.SPACE) tokens.removeAt(tokens.lastIndex)
    return tokens
}

private fun SpellToken.spoken(): List<String> = when (kind) {
    TokenKind.SPACE -> emptyList()
    TokenKind.OTHER -> listOf(source)
    TokenKind.SYMBOL -> words.map { "($it)" }
    else -> words
}

/** One row per typed word, in order. */
fun spellRows(text: String): List<SpellRow> {
    val rows = ArrayList<SpellRow>()
    val typed = StringBuilder()
    val spoken = ArrayList<String>()
    fun flush() {
        if (typed.isNotEmpty()) rows += SpellRow(typed.toString(), spoken.joinToString(" "))
        typed.clear()
        spoken.clear()
    }
    for (token in spell(text)) {
        if (token.kind == TokenKind.SPACE) {
            flush()
        } else {
            typed.append(token.source)
            spoken += token.spoken()
        }
    }
    flush()
    return rows
}

/** Everything to say, words separated by " / ": "Bravo Oscar Bravo / WUN TOO". */
fun spelledText(text: String): String = spellRows(text).joinToString(" / ") { it.spoken }

/** Clipboard form: one line per typed word, "Paris : Papa Alfa Romeo India Sierra". */
fun spellingForClipboard(rows: List<SpellRow>): String =
    rows.joinToString("\n") { "${it.typed} : ${it.spoken}" }
