package com.survival.notes

import kotlin.math.abs

private const val NBSP = '\u00A0'
private const val DECIMAL_PLACES = 5

/** Tenths of an arcsecond in one degree. */
private const val TENTHS_PER_DEGREE = 36_000L
private const val TENTHS_PER_MINUTE = 600L

/**
 * [value] with [decimals] decimals and [separator] as decimal mark, rounded half away from zero.
 * A value that rounds to zero never shows a minus sign.
 */
internal fun fixed(value: Double, decimals: Int, separator: Char): String {
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val scaled = Math.round(abs(value) * factor)
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return sign + whole
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$whole$separator$fraction"
}

/** "48,85341" : decimal degrees with a French decimal comma, 5 decimals (about 1 m). */
fun formatDecimalDegrees(value: Double): String = fixed(value, DECIMAL_PLACES, ',')

/** "48,85341, 2,34880" (latitude, longitude). */
fun formatDecimal(latitude: Double, longitude: Double): String =
    "${formatDecimalDegrees(latitude)}, ${formatDecimalDegrees(longitude)}"

/** "48.85341, 2.34880" : the form every map application understands. */
fun formatDecimalInternational(latitude: Double, longitude: Double): String =
    "${fixed(latitude, DECIMAL_PLACES, '.')}, ${fixed(longitude, DECIMAL_PLACES, '.')}"

/**
 * Degrees, minutes, seconds to the tenth: 48°51'12,3" N. Rounding is done once on the total in
 * tenths of an arcsecond, so 59,96" carries over to the next minute (and degree) instead of
 * showing 60,0". A value that rounds to zero takes the [positive] hemisphere.
 */
fun formatDmsComponent(value: Double, positive: Char, negative: Char): String {
    val tenths = Math.round(abs(value) * TENTHS_PER_DEGREE)
    val degrees = tenths / TENTHS_PER_DEGREE
    val minutes = (tenths % TENTHS_PER_DEGREE) / TENTHS_PER_MINUTE
    val secondTenths = tenths % TENTHS_PER_MINUTE
    val seconds = (secondTenths / 10).toString().padStart(2, '0')
    val hemisphere = if (value < 0 && tenths != 0L) negative else positive
    return "$degrees°${minutes.toString().padStart(2, '0')}'$seconds,${secondTenths % 10}\" $hemisphere"
}

fun formatLatitudeDms(latitude: Double): String = formatDmsComponent(latitude, 'N', 'S')

/** East / Ouest: French initials for the longitude hemispheres. */
fun formatLongitudeDms(longitude: Double): String = formatDmsComponent(longitude, 'E', 'O')

/** "48°51'12,3" N 2°20'55,7" E" */
fun formatDms(latitude: Double, longitude: Double): String =
    "${formatLatitudeDms(latitude)} ${formatLongitudeDms(longitude)}"

/** "± 12 m", "± 1,5 km" (non-breaking spaces so it never wraps). */
fun formatAccuracy(meters: Double): String {
    val rounded = Math.round(meters).coerceAtLeast(1L)
    return if (rounded < 1000L) {
        "±$NBSP$rounded${NBSP}m"
    } else {
        "±$NBSP${fixed(meters / 1000.0, 1, ',')}${NBSP}km"
    }
}

/** Clipboard text: international decimal form, then degrees-minutes-seconds. */
fun coordinatesForClipboard(latitude: Double, longitude: Double): String =
    formatDecimalInternational(latitude, longitude) + "\n" + formatDms(latitude, longitude)
