package com.survival.signals

/** The seven tabs of the app, in display order. */
enum class Section(val tab: String, val heading: String, val intro: String) {
    URGENCES(
        "Urgences",
        "Numéros d'urgence",
        "Toucher un numéro ouvre le composeur (ou la messagerie pour le 114) : rien ne part sans votre " +
            "confirmation. Ces numéros sont gratuits."
    ),
    ALPHABET(
        "Alphabet",
        "Alphabet radio international",
        "Alphabet OTAN / OACI pour épeler sans ambiguïté. Prononciation à l'anglaise, syllabe accentuée " +
            "en majuscules. Toucher un mot l'affiche en grand."
    ),
    SOL_AIR(
        "Sol-air",
        "Signaux sol-air",
        "Code international (OACI) à tracer au sol par les survivants, pour un aéronef de recherche."
    ),
    GESTES(
        "Gestes",
        "Signaux corporels",
        "Gestes à faire face à un hélicoptère, un avion ou un navire de secours."
    ),
    DETRESSE(
        "Détresse",
        "Signaux de détresse",
        "Signaux lumineux, sonores et visuels pour être repéré quand on ne peut pas téléphoner."
    ),
    RADIO(
        "Radio",
        "Radio : procédures et fréquences",
        "Messages de détresse, d'urgence et de sécurité, et fréquences utiles."
    ),
    ALERTE(
        "Alerte",
        "Alerte des populations",
        "Reconnaître les sirènes et FR-Alert, et savoir comment réagir. Ce guide ne joue aucun son."
    ),
}

/** Drawings rendered on a Canvas; their geometry lives in Figures.kt. */
enum class Figure(val description: String) {
    GROUND_V("Lettre V tracée au sol"),
    GROUND_X("Lettre X tracée au sol"),
    GROUND_N("Lettre N tracée au sol"),
    GROUND_Y("Lettre Y tracée au sol"),
    GROUND_ARROW("Flèche tracée au sol"),
    BODY_YES("Silhouette, les deux bras levés en Y"),
    BODY_NO("Silhouette, un bras levé et l'autre baissé en diagonale"),
    BODY_SEA("Silhouette, bras tendus sur les côtés, levés puis abaissés"),
    FIRES_TRIANGLE("Trois feux disposés en triangle"),
    TIMELINE_SOS("Chronogramme du signal SOS : trois brefs, trois longs, trois brefs"),
    TIMELINE_ALPINE_CALL("Chronogramme : six signaux en une minute, puis une minute de pause"),
    TIMELINE_ALPINE_REPLY("Chronogramme : trois signaux en une minute, puis une minute de pause"),
    TIMELINE_SIREN("Chronogramme : trois cycles de son modulé de 1 minute 41 secondes séparés par 5 secondes"),
    TIMELINE_SIREN_END("Chronogramme : son continu de 30 secondes"),
    TIMELINE_DAM("Chronogramme : sons de 2 secondes séparés par 3 secondes, pendant au moins 2 minutes"),
}

/** Interactive helpers shown on an entry's detail page. */
enum class Tool { ALPINE_CADENCE, MAYDAY_FORM }

/**
 * One fact card. Every entry is searchable; [dial] makes it open the dialer (or the SMS app when [sms]).
 * Texts get French typography (non-breaking spaces before « : ; ! ? », after numbers) on construction.
 *
 * @property glyph short symbol shown large at the start of the card (a letter, a digit, a channel).
 * @property display number as written ("116 000"); [dial] holds the digits to compose.
 * @property big lines shown by "Afficher en grand"; defaults to the number or the title.
 * @property steps details are an ordered procedure (numbered) rather than a bullet list.
 * @property unofficial note shown in a warning box when the practice is common usage, not an official rule.
 * @property source short attribution shown on the detail page.
 */
class Entry(
    val id: String,
    val section: Section,
    group: String,
    title: String,
    summary: String,
    details: List<String> = emptyList(),
    source: String,
    val dial: String? = null,
    val sms: Boolean = false,
    display: String? = null,
    val glyph: String? = null,
    big: List<String>? = null,
    val figure: Figure? = null,
    val tool: Tool? = null,
    val steps: Boolean = false,
    unofficial: String? = null,
    val keywords: String = "",
) {
    val group: String = frenchTypography(group)
    val title: String = frenchTypography(title)
    val summary: String = frenchTypography(summary)
    val details: List<String> = details.map(::frenchTypography)
    val source: String = frenchTypography(source)
    val display: String? = display?.let(::frenchTypography)
    val unofficial: String? = unofficial?.let(::frenchTypography)
    private val bigOverride: List<String>? = big

    /** Number as shown ("116 000"), falling back to the raw digits. */
    val shownNumber: String? get() = display ?: dial

    /** Lines for the large display, or null when the entry has nothing worth showing from afar. */
    val bigLines: List<String>? get() = bigOverride ?: shownNumber?.let { listOf(it, title) }

    /** Whether the detail page adds something to the card (details, drawing, tool or note). */
    val hasDetail: Boolean get() = details.isNotEmpty() || figure != null || tool != null || unofficial != null

    /** Plain text for the clipboard: title, summary, details and source. */
    fun asPlainText(): String = buildString {
        append(listOfNotNull(shownNumber ?: glyph, title).joinToString(" — "))
        append('\n').append(summary)
        details.forEachIndexed { index, line ->
            append('\n').append(if (steps) "${index + 1}. " else "• ").append(line)
        }
        unofficial?.let { append("\n(").append(it).append(')') }
        append("\nSource").append(NBSP).append(": ").append(source)
    }
}

private const val NBSP = ' '
private val SPACE_BEFORE_PUNCTUATION = Regex(" ([:;!?»])")
private val SPACE_AFTER_QUOTE = Regex("« ")
private val SPACE_AFTER_NUMBER = Regex("(?<=\\d) (?=\\p{L}|\\d{3}\\b)")

/**
 * French typography: the spaces before « : ; ! ? » and after « become non-breaking, and so do the
 * spaces that glue a number to its unit or to its next group of digits ("1 min 41 s", "116 000").
 */
fun frenchTypography(text: String): String = text
    .replace(SPACE_BEFORE_PUNCTUATION, "$NBSP$1")
    .replace(SPACE_AFTER_QUOTE, "«$NBSP")
    .replace(SPACE_AFTER_NUMBER, NBSP.toString())
