package com.survival.altimeter

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

const val NBSP = ' '
const val NNBSP = ' '
const val MINUS = '−'
const val NO_VALUE = "—"

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE)

enum class AltitudeUnit(val key: String, val symbol: String, val label: String, val metersPerUnit: Double) {
    METERS("m", "m", "Mètres", 1.0),
    FEET("ft", "ft", "Pieds", 0.3048);

    fun fromMeters(meters: Double): Double = meters / metersPerUnit

    fun toMeters(value: Double): Double = value * metersPerUnit

    companion object {
        fun fromKey(key: String?): AltitudeUnit = entries.firstOrNull { it.key == key } ?: METERS
    }
}

/**
 * French number: decimal comma, narrow no-break space between thousands, true minus sign.
 * Written by hand so the output is identical on every Android version and on the JVM.
 */
fun formatNumber(value: Double, decimals: Int = 0, signed: Boolean = false): String {
    if (!value.isFinite()) return NO_VALUE
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val scaled = (abs(value) * factor).roundToLong()
    val integer = (scaled / factor).toString()
    val grouped = StringBuilder()
    integer.forEachIndexed { index, digit ->
        if (index > 0 && (integer.length - index) % 3 == 0) grouped.append(NNBSP)
        grouped.append(digit)
    }
    if (decimals > 0) {
        grouped.append(',').append((scaled % factor).toString().padStart(decimals, '0'))
    }
    val sign = when {
        scaled == 0L -> ""
        value < 0 -> MINUS.toString()
        signed -> "+"
        else -> ""
    }
    return sign + grouped
}

fun formatAltitudeValue(meters: Double, unit: AltitudeUnit): String = formatNumber(unit.fromMeters(meters))

fun formatAltitude(meters: Double, unit: AltitudeUnit): String =
    if (meters.isFinite()) "${formatAltitudeValue(meters, unit)}$NBSP${unit.symbol}" else NO_VALUE

fun formatAccuracy(meters: Double, unit: AltitudeUnit): String =
    if (meters.isFinite()) "±${formatNumber(unit.fromMeters(meters))}$NBSP${unit.symbol}" else NO_VALUE

fun formatHpa(hPa: Double, decimals: Int = 1): String =
    if (hPa.isFinite()) "${formatNumber(hPa, decimals)}${NBSP}hPa" else NO_VALUE

fun formatHpaChange(hPa: Double): String =
    if (hPa.isFinite()) "${formatNumber(hPa, 1, signed = true)}${NBSP}hPa" else NO_VALUE

/** "45 min", "2 h 05", "1 j 4 h". */
fun formatDuration(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    return when {
        safe < MINUTE_MS -> "moins d'une minute"
        safe < HOUR_MS -> "${safe / MINUTE_MS}${NBSP}min"
        safe < DAY_MS -> {
            val hours = safe / HOUR_MS
            val minutes = (safe % HOUR_MS) / MINUTE_MS
            if (minutes == 0L) "$hours${NBSP}h" else "$hours${NBSP}h${NBSP}${minutes.toString().padStart(2, '0')}"
        }
        else -> {
            val days = safe / DAY_MS
            val hours = (safe % DAY_MS) / HOUR_MS
            if (hours == 0L) "$days${NBSP}j" else "$days${NBSP}j${NBSP}$hours${NBSP}h"
        }
    }
}

fun formatAgo(ms: Long): String = if (ms < MINUTE_MS) "à l'instant" else "il y a ${formatDuration(ms)}"

fun formatClock(epochMs: Long, zone: ZoneId): String = CLOCK.format(Instant.ofEpochMilli(epochMs).atZone(zone))

/** "aujourd'hui à 14:05", "hier à 09:30", "le 18/10 à 22:15". */
fun formatDateTime(epochMs: Long, nowMs: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val clock = CLOCK.format(time)
    return when (time.toLocalDate()) {
        today -> "aujourd'hui à $clock"
        today.minusDays(1) -> "hier à $clock"
        else -> "le ${DAY_MONTH.format(time)} à $clock"
    }
}

/** Accepts "1250", "1 250", "1013,2", "1013.2", "-12", "−12". Returns null when not a number. */
fun parseDecimal(text: String): Double? {
    val cleaned = text.trim()
        .filterNot { it == ' ' || it == NBSP || it == NNBSP }
        .replace(MINUS, '-')
        .replace(',', '.')
    if (!Regex("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)$").matches(cleaned)) return null
    return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Keeps what a decimal field may contain, so a stray letter never reaches [parseDecimal]. */
fun sanitizeDecimalInput(text: String, allowNegative: Boolean, maxLength: Int = 9): String =
    text.filter { it.isDigit() || it == ',' || it == '.' || (allowNegative && (it == '-' || it == MINUS)) }
        .take(maxLength)
