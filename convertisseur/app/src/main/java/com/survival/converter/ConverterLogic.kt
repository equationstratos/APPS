package com.survival.converter

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * A unit related to its category's base unit by an affine law: base = (value + offset) * factor.
 * The offset is only non-zero for temperatures (base unit: degree Celsius).
 */
class MeasureUnit(
    val name: String,
    val symbol: String,
    private val factor: Double,
    private val offset: Double = 0.0
) {
    fun toBase(value: Double): Double = (value + offset) * factor
    fun fromBase(base: Double): Double {
        val value = base / factor - offset
        // Cancelling the offset leaves binary round-off (−459,67 °F → 5,7 × 10⁻¹⁴ K): snap it to zero.
        return if (abs(value) <= abs(offset) * OFFSET_ROUND_OFF) 0.0 else value
    }
}

/** Relative size, against the offset, below which a converted value is pure round-off. */
private const val OFFSET_ROUND_OFF = 1e-12

/** Base units: metre, kilogram, degree Celsius, litre, metre per second, square metre, pascal. */
enum class Category(
    val label: String,
    val units: List<MeasureUnit>,
    defaultFromSymbol: String,
    defaultToSymbol: String
) {
    LENGTH(
        "Longueur",
        listOf(
            MeasureUnit("Millimètre", "mm", 0.001),
            MeasureUnit("Centimètre", "cm", 0.01),
            MeasureUnit("Mètre", "m", 1.0),
            MeasureUnit("Kilomètre", "km", 1000.0),
            MeasureUnit("Pouce", "in", 0.0254),
            MeasureUnit("Pied", "ft", 0.3048),
            MeasureUnit("Yard", "yd", 0.9144),
            MeasureUnit("Mile", "mi", 1609.344),
            MeasureUnit("Mille nautique", "NM", 1852.0)
        ),
        "km", "mi"
    ),
    MASS(
        "Masse",
        listOf(
            MeasureUnit("Milligramme", "mg", 1e-6),
            MeasureUnit("Gramme", "g", 1e-3),
            MeasureUnit("Kilogramme", "kg", 1.0),
            MeasureUnit("Tonne", "t", 1000.0),
            MeasureUnit("Once", "oz", 0.028349523125),
            MeasureUnit("Livre", "lb", 0.45359237),
            MeasureUnit("Stone", "st", 6.35029318)
        ),
        "kg", "lb"
    ),
    TEMPERATURE(
        "Température",
        listOf(
            MeasureUnit("Degré Celsius", "°C", 1.0),
            MeasureUnit("Degré Fahrenheit", "°F", 5.0 / 9.0, -32.0),
            MeasureUnit("Kelvin", "K", 1.0, -CELSIUS_KELVIN_OFFSET)
        ),
        "°C", "°F"
    ),
    VOLUME(
        "Volume",
        listOf(
            MeasureUnit("Millilitre", "ml", 0.001),
            MeasureUnit("Centilitre", "cl", 0.01),
            MeasureUnit("Litre", "l", 1.0),
            MeasureUnit("Mètre cube", "m³", 1000.0),
            MeasureUnit("Cuillère à café (US)", "c. à café US", 0.00492892159375),
            MeasureUnit("Cuillère à soupe (US)", "c. à soupe US", 0.01478676478125),
            MeasureUnit("Tasse (US)", "tasse US", 0.2365882365),
            MeasureUnit("Once liquide (US)", "fl oz US", 0.0295735295625),
            MeasureUnit("Pinte (US)", "pinte US", 0.473176473),
            MeasureUnit("Gallon (US)", "gal US", 3.785411784),
            MeasureUnit("Gallon impérial (UK)", "gal UK", 4.54609)
        ),
        "l", "gal US"
    ),
    SPEED(
        "Vitesse",
        listOf(
            MeasureUnit("Mètre par seconde", "m/s", 1.0),
            MeasureUnit("Kilomètre par heure", "km/h", 1000.0 / 3600.0),
            MeasureUnit("Mile par heure", "mph", 0.44704),
            MeasureUnit("Nœud", "nd", 1852.0 / 3600.0)
        ),
        "km/h", "mph"
    ),
    AREA(
        "Surface",
        listOf(
            MeasureUnit("Mètre carré", "m²", 1.0),
            MeasureUnit("Kilomètre carré", "km²", 1e6),
            MeasureUnit("Hectare", "ha", 1e4),
            MeasureUnit("Are", "a", 100.0),
            MeasureUnit("Pied carré", "ft²", 0.09290304),
            MeasureUnit("Acre", "ac", 4046.8564224)
        ),
        "ha", "ac"
    ),
    PRESSURE(
        "Pression",
        listOf(
            MeasureUnit("Pascal", "Pa", 1.0),
            MeasureUnit("Hectopascal", "hPa", 100.0),
            MeasureUnit("Bar", "bar", 1e5),
            MeasureUnit("Atmosphère", "atm", 101325.0),
            MeasureUnit("Livre par pouce carré", "psi", 6894.757293168361),
            MeasureUnit("Millimètre de mercure", "mmHg", 133.322387415)
        ),
        "bar", "psi"
    );

    val defaultFrom: Int = units.indexOfFirst { it.symbol == defaultFromSymbol }
    val defaultTo: Int = units.indexOfFirst { it.symbol == defaultToSymbol }
}

private const val CELSIUS_KELVIN_OFFSET = 273.15
private const val ABSOLUTE_ZERO_CELSIUS = -CELSIUS_KELVIN_OFFSET

/** Tolerance for round-off when the user types exactly absolute zero in °F. */
private const val ABSOLUTE_ZERO_EPSILON = 1e-9

fun convert(value: Double, from: MeasureUnit, to: MeasureUnit): Double =
    if (from === to) value else to.fromBase(from.toBase(value))

/** Soft warning shown under the result for physically doubtful inputs, or null. */
fun Category.hintFor(value: Double, unit: MeasureUnit): String? = when {
    this == Category.TEMPERATURE ->
        if (unit.toBase(value) < ABSOLUTE_ZERO_CELSIUS - ABSOLUTE_ZERO_EPSILON) {
            "Température inférieure au zéro absolu (−273,15 °C)."
        } else {
            null
        }
    value < 0.0 -> "Valeur négative : inhabituelle pour cette grandeur."
    else -> null
}

private const val MAX_INPUT_LENGTH = 15

private val INPUT_PATTERN = Regex("-?[0-9]*[.,]?[0-9]*")

/**
 * Cleans what the user typed or pasted (spaces, typographic minus) and returns it,
 * or null when the text is not a number being typed, so the field keeps its previous value.
 */
fun sanitizeInput(raw: String): String? {
    val cleaned = raw.filterNot { it.isWhitespace() }.replace('−', '-')
    return cleaned.takeIf { it.length <= MAX_INPUT_LENGTH && INPUT_PATTERN.matches(it) }
}

fun toggleSign(text: String): String = when {
    text.startsWith("-") -> text.drop(1)
    text.length < MAX_INPUT_LENGTH -> "-$text"
    else -> text
}

fun parseInput(text: String): Double? =
    text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private val SIGNIFICANT = MathContext(6, RoundingMode.HALF_UP)
private val SCIENTIFIC_ABOVE = BigDecimal("1E9")
private val SCIENTIFIC_BELOW = BigDecimal("1E-4")
private const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

/**
 * French display with at most 6 significant digits and digit grouping; scientific notation
 * (mantissa × 10ⁿ) only for |x| ≥ 10⁹ or 0 < |x| < 10⁻⁴.
 */
fun formatNumber(value: Double): String {
    if (!value.isFinite()) return "—"
    if (value == 0.0) return "0"
    val rounded = BigDecimal.valueOf(value).round(SIGNIFICANT).stripTrailingZeros()
    val magnitude = rounded.abs()
    if (magnitude >= SCIENTIFIC_ABOVE || magnitude < SCIENTIFIC_BELOW) {
        val exponent = rounded.precision() - rounded.scale() - 1
        return "${formatPlain(rounded.movePointLeft(exponent))} × 10${superscript(exponent)}"
    }
    return formatPlain(rounded)
}

private fun formatPlain(value: BigDecimal): String {
    val format = NumberFormat.getNumberInstance(Locale.FRANCE)
    format.isGroupingUsed = true
    format.minimumFractionDigits = 0
    format.maximumFractionDigits = value.scale().coerceAtLeast(0)
    return format.format(value)
}

private fun superscript(exponent: Int): String =
    exponent.toString().map { c -> if (c == '-') '⁻' else SUPERSCRIPT_DIGITS[c - '0'] }.joinToString("")
