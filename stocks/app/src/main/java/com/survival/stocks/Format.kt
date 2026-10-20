package com.survival.stocks

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

// Pure French formatting, parsing, search normalisation and CSV export (covered by the JVM tests).

/** Non-breaking space, kept between a number and its unit. */
const val NBSP = '\u00A0'

/** Narrow non-breaking space, the French thousands separator. */
const val THIN_NBSP = '\u202F'

private val SPACES = Regex("[\\s\\u00A0\\u202F\\u2009]")
private val DECIMAL = Regex("""^(\d+\.?\d*|\.\d+)$""")
private val MARKS = Regex("\\p{Mn}+")

/** Parses "1,5", "1.5", " 2 ", "1 000" or ",5"; null for empty, negative or malformed text. */
fun parseDecimal(text: String): Double? {
    val t = text.replace(SPACES, "").replace(',', '.')
    if (!DECIMAL.matches(t)) return null
    return t.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Parses a whole number (digits only, spaces tolerated); null otherwise. */
fun parseWhole(text: String): Int? {
    val t = text.replace(SPACES, "")
    if (t.isEmpty() || t.length > 9 || !t.all { it in '0'..'9' }) return null
    return t.toInt()
}

/**
 * French number: comma as decimal separator, narrow no-break space between thousands,
 * at most [maxDecimals] decimals and no trailing zeros ("1,5", "12 000", "0,25").
 */
fun formatDecimal(value: Double, maxDecimals: Int = 2, grouping: Boolean = true): String {
    if (!value.isFinite()) return "—"
    var scale = 1L
    repeat(maxDecimals) { scale *= 10 }
    val scaled = Math.round(abs(value) * scale)
    val whole = (scaled / scale).toString()
    val fraction = if (maxDecimals > 0) (scaled % scale).toString().padStart(maxDecimals, '0').trimEnd('0') else ""
    val sign = if (value < 0 && scaled != 0L) "−" else ""
    val grouped = if (grouping) groupThousands(whole) else whole
    return sign + grouped + if (fraction.isEmpty()) "" else ",$fraction"
}

private fun groupThousands(digits: String): String {
    if (digits.length <= 3) return digits
    val sb = StringBuilder()
    val head = digits.length % 3
    if (head > 0) sb.append(digits, 0, head)
    var i = head
    while (i < digits.length) {
        if (sb.isNotEmpty()) sb.append(THIN_NBSP)
        sb.append(digits, i, i + 3)
        i += 3
    }
    return sb.toString()
}

/** "3 boîtes", "1 boîte", "1,5 kg". */
fun quantityLabel(item: Item): String =
    "${formatDecimal(item.quantity, 3)}$NBSP${unitForQuantity(item.quantity, item.unit)}"

/** "1 article", "0 article", "3 articles" (French: singular below 2). */
fun countLabel(n: Int, singular: String, plural: String): String = "$n$NBSP${if (n < 2) singular else plural}"

/**
 * Autonomy in days, never overstated: one decimal (rounded down) below 10 days, whole days above.
 * "0 jour", "1,5 jour", "2 jours", "12 jours".
 */
fun formatDays(days: Double): String {
    val d = days.finiteOr(0.0).coerceAtLeast(0.0)
    val shown = if (d < 10) floor(d * 10 + 1e-9) / 10 else floor(d + 1e-9)
    val text = if (d < 10) formatDecimal(shown, 1) else formatDecimal(shown, 0)
    return "$text$NBSP${if (shown >= 2) "jours" else "jour"}"
}

/** "3 jours (72 h)", "1 jour (24 h)", "14 jours". */
fun targetLabel(days: Int): String {
    val base = "$days$NBSP${if (days >= 2) "jours" else "jour"}"
    return if (days <= 3) "$base (${days * 24}${NBSP}h)" else base
}

/** Lower case, no accents, "œ" → "oe": used to search and to sort names. */
fun normalizeForSearch(text: String): String {
    val lowered = text.lowercase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae").replace("ß", "ss")
    return Normalizer.normalize(lowered, Normalizer.Form.NFD).replace(MARKS, "").replace(SPACES, " ").trim()
}

fun exportFileName(today: LocalDate, extension: String): String =
    "stocks-${today.year}-${today.monthValue.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}.$extension"

// ---------------------------------------------------------------- CSV

const val CSV_BOM = "\uFEFF"

val CSV_HEADER = listOf(
    "Nom", "Catégorie", "Quantité", "Unité", "Péremption", "Statut",
    "Emplacement", "Note", "Eau (L)", "Énergie (kcal)"
)

/**
 * One CSV field for Excel (";" separator): quoted when it contains ";", quotes or line breaks,
 * and, for free text, prefixed with an apostrophe when it would be read as a formula.
 */
fun csvField(value: String, text: Boolean = true): String {
    val v = if (text && value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
    val needsQuotes = v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuotes) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

/** Whole inventory as CSV, in urgency order, UTF-8 BOM first so that Excel reads the accents. */
fun buildCsv(items: List<Item>, today: LocalDate, soonDays: Int): String {
    val sb = StringBuilder(CSV_BOM)
    sb.append(CSV_HEADER.joinToString(";") { csvField(it) }).append("\r\n")
    for (item in sortByUrgency(items, today, soonDays)) {
        val fields = listOf(
            csvField(item.name),
            csvField(item.category.label),
            csvField(formatDecimal(item.quantity, 3, grouping = false), text = false),
            csvField(item.unit),
            csvField(item.expiry?.short().orEmpty(), text = false),
            csvField(statusOf(item, today, soonDays).csv),
            csvField(item.location),
            csvField(item.note),
            csvField(waterLiters(item)?.let { formatDecimal(it, 2, grouping = false) }.orEmpty(), text = false),
            csvField(itemKcal(item)?.let { formatDecimal(it, 0, grouping = false) }.orEmpty(), text = false),
        )
        sb.append(fields.joinToString(";")).append("\r\n")
    }
    return sb.toString()
}

/** "1 aliment sans calories n'est pas compté." / "3 aliments sans calories ne sont pas comptés." */
fun notCountedNote(n: Int, singular: String, plural: String): String =
    if (n < 2) "$n$NBSP$singular n'est pas compté." else "$n$NBSP$plural ne sont pas comptés."

/** Shown when nothing is expired or about to expire. */
fun noAlertLabel(soonDays: Int): String = when {
    soonDays <= 0 -> "Aucun article périmé."
    soonDays == 1 -> "Aucune péremption d'ici demain."
    else -> "Aucune péremption dans les $soonDays prochains jours."
}

/** "3 articles ajoutés, 1 déjà présent" after a merge import. */
fun mergeSummary(added: Int, skipped: Int): String {
    val a = if (added < 2) "$added article ajouté" else "$added articles ajoutés"
    return if (skipped == 0) a else a + if (skipped < 2) ", $skipped déjà présent" else ", $skipped déjà présents"
}
