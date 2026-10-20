package com.survival.morse

import java.text.Normalizer

/** Families used to group the signs in the reference table. */
enum class MorseGroup(val title: String) {
    LETTERS("Lettres"),
    FIGURES("Chiffres"),
    PUNCTUATION("Ponctuation"),
    EXTRA("Signes courants hors UIT")
}

class MorseSign(val char: Char, val code: String, val group: MorseGroup)

/**
 * Procedural signal: letters sent as one block, without the 3-unit letter space.
 * [label] is what the decoder writes when it receives [code] and no ordinary sign uses it.
 */
class Prosign(val name: String, val code: String, val meaning: String, val label: String)

/** One transmitted block: a sign, or a prosign such as SOS whose letters are run together. */
class MorseToken(val label: String, val code: String)

/** Text converted to Morse. [words] never contains an empty word. */
class Encoded(val words: List<List<MorseToken>>, val ignored: List<String>) {
    val tokens: List<MorseToken> = words.flatten()
    val isEmpty: Boolean get() = tokens.isEmpty()

    /** Codes with letters separated by a space and words by " / ". */
    val codeText: String
        get() = words.joinToString(WORD_SEPARATOR) { word -> word.joinToString(" ") { it.code } }
}

/** Marker written in decoded text for a sequence that matches no sign. */
const val UNKNOWN_SIGN = "#"

const val WORD_SEPARATOR = " / "

/** Longest prosign accepted between angle brackets, e.g. <SOS> or <SK>. */
private const val MAX_PROSIGN_LETTERS = 6

/** International Morse code, ITU-R M.1677-1, plus a few widespread non-ITU signs. */
object Morse {
    val signs: List<MorseSign> = buildList {
        add(MorseGroup.LETTERS, "A.-", "B-...", "C-.-.", "D-..", "E.", "F..-.", "G--.", "H....", "I..")
        add(MorseGroup.LETTERS, "J.---", "K-.-", "L.-..", "M--", "N-.", "O---", "P.--.", "Q--.-", "R.-.")
        add(MorseGroup.LETTERS, "S...", "T-", "U..-", "V...-", "W.--", "X-..-", "Y-.--", "Z--..", "É..-..")
        add(MorseGroup.FIGURES, "1.----", "2..---", "3...--", "4....-", "5.....")
        add(MorseGroup.FIGURES, "6-....", "7--...", "8---..", "9----.", "0-----")
        add(MorseGroup.PUNCTUATION, "..-.-.-", ",--..--", "?..--..", "'.----.", "/-..-.", "(-.--.", ")-.--.-")
        add(MorseGroup.PUNCTUATION, ":---...", "=-...-", "+.-.-.", "--....-", "\".-..-.", "@.--.-.")
        add(MorseGroup.EXTRA, "&.-...", "!-.-.--", ";-.-.-.", "_..--.-", "$...-..-")
    }

    val prosigns: List<Prosign> = listOf(
        Prosign("SOS", "...---...", "Détresse", "SOS"),
        Prosign("AR", ".-.-.", "Fin de message (comme +)", "<AR>"),
        Prosign("BT", "-...-", "Séparation (comme =)", "<BT>"),
        Prosign("K", "-.-", "Invitation à transmettre", "<K>"),
        Prosign("KN", "-.--.", "Invitation à une seule station (comme « ( »)", "<KN>"),
        Prosign("AS", ".-...", "Attente (comme &)", "<AS>"),
        Prosign("SN", "...-.", "Compris", "<SN>"),
        Prosign("CT", "-.-.-", "Signal de début de transmission", "<CT>"),
        Prosign("SK", "...-.-", "Fin de vacation", "<SK>"),
        Prosign("HH", "........", "Erreur : le dernier mot est annulé", "<HH>")
    )

    private val codeByChar: Map<Char, String> = signs.associate { it.char to it.code }

    private val textByCode: Map<String, String> = buildMap {
        for (prosign in prosigns) put(prosign.code, prosign.label)
        // Ordinary signs win over the prosigns that share their code (+, =, K, (, &).
        for (sign in signs) put(sign.code, sign.char.toString())
    }

    fun codeOf(char: Char): String? = codeByChar[char]

    /** Text for one received sign ("." and "-" only), or null when no sign uses this code. */
    fun decodeSymbol(code: String): String? = textByCode[code]

    private fun MutableList<MorseSign>.add(group: MorseGroup, vararg entries: String) {
        for (entry in entries) add(MorseSign(entry[0], entry.substring(1), group))
    }
}

private val TYPOGRAPHIC = mapOf(
    '’' to "'", '‘' to "'", 'ʼ' to "'", '`' to "'", '´' to "'",
    '«' to "\"", '»' to "\"", '“' to "\"", '”' to "\"", '„' to "\"",
    '‐' to "-", '‑' to "-", '–' to "-", '—' to "-", '−' to "-",
    '×' to "X", '…' to "...", 'Œ' to "OE", 'œ' to "OE", 'Æ' to "AE",
    'æ' to "AE", 'ß' to "SS", ' ' to " ", ' ' to " ", ' ' to " ",
    '\t' to " ", '\n' to " ", '\r' to " "
)

private val COMBINING_MARKS = Regex("\\p{M}+")

/**
 * Upper-case form of one character as Morse signs ("" when it cannot be sent). É keeps its own
 * code; the other accented letters fall back to their base letter (À → A, Ç → C, Ê → E).
 */
fun normalizeChar(char: Char): String {
    TYPOGRAPHIC[char]?.let { return it }
    if (char == ' ') return " "
    val upper = char.uppercaseChar()
    if (Morse.codeOf(upper) != null) return upper.toString()
    val base = Normalizer.normalize(upper.toString(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
    return if (base.isNotEmpty() && base.all { Morse.codeOf(it) != null }) base else ""
}

/** Text as it will be sent: upper case, accents folded, unknown characters removed. */
fun normalizeText(text: String): String = buildString {
    for (char in text) append(normalizeChar(char))
}.replace(Regex(" +"), " ").trim()

/**
 * Converts free text to Morse. Spaces separate words; text between angle brackets (<SOS>) is
 * sent as a single prosign. Characters without a Morse code are skipped and listed in [Encoded.ignored].
 */
fun encode(text: String): Encoded {
    val words = mutableListOf<List<MorseToken>>()
    val ignored = linkedSetOf<String>()
    var word = mutableListOf<MorseToken>()

    fun endWord() {
        if (word.isNotEmpty()) words += word
        word = mutableListOf()
    }

    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        val length = Character.charCount(codePoint)
        if (codePoint == '<'.code) {
            val prosign = readProsign(text, i)
            if (prosign != null) {
                word += prosign.first
                i = prosign.second
                continue
            }
        }
        val normalized = if (length == 1) normalizeChar(text[i]) else ""
        when {
            normalized == " " -> endWord()
            normalized.isEmpty() -> if (!Character.isWhitespace(codePoint)) {
                ignored += String(Character.toChars(codePoint))
            } else {
                endWord()
            }
            else -> for (char in normalized) {
                if (char == ' ') endWord() else word += MorseToken(char.toString(), Morse.codeOf(char)!!)
            }
        }
        i += length
    }
    endWord()
    return Encoded(words, ignored.toList())
}

/** Reads "<LETTERS>" starting at [start]; returns the token and the index after '>'. */
private fun readProsign(text: String, start: Int): Pair<MorseToken, Int>? {
    val end = text.indexOf('>', start + 1)
    if (end < 0) return null
    val inner = buildString {
        for (char in text.substring(start + 1, end)) append(normalizeChar(char))
    }
    if (inner.isEmpty() || inner.length > MAX_PROSIGN_LETTERS) return null
    if (inner.any { it == ' ' || Morse.codeOf(it) == null }) return null
    val code = inner.map { Morse.codeOf(it)!! }.joinToString("")
    return MorseToken(inner, code) to end + 1
}

/**
 * Decodes written Morse: signs separated by spaces, words by "/". Accepts the typographic
 * dots and dashes produced by [prettyCode]. Unknown sequences become [UNKNOWN_SIGN].
 */
fun decodeCode(code: String): String {
    val ascii = code.map { asciiSymbol(it) }.joinToString("")
    return ascii.split('/')
        .map { word -> word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { signs -> signs.joinToString("") { Morse.decodeSymbol(it) ?: UNKNOWN_SIGN } }
}

private fun asciiSymbol(char: Char): Char = when (char) {
    '•', '·', '∙' -> '.'
    '−', '–', '—', '_' -> '-'
    else -> char
}

const val PRETTY_DOT = '•'
const val PRETTY_DASH = '−'

/** Readable form of a code: bullets and minus signs instead of '.' and '-'. */
fun prettyCode(code: String): String = buildString(code.length) {
    for (char in code) {
        append(
            when (char) {
                '.' -> PRETTY_DOT
                '-' -> PRETTY_DASH
                else -> char
            }
        )
    }
}
