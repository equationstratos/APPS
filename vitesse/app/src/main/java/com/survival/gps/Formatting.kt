package com.survival.gps

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val FR: Locale = Locale.FRANCE
private const val METERS_PER_MILE = 1609.344
private const val METERS_PER_NAUTICAL_MILE = 1852.0
private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

const val NO_VALUE = "—"

enum class SpeedUnit(val key: String, val label: String, val perMps: Double) {
    KMH("kmh", "km/h", 3.6),
    MS("ms", "m/s", 1.0),
    MPH("mph", "mph", 3600.0 / METERS_PER_MILE),
    KNOTS("kn", "nœuds", 3600.0 / METERS_PER_NAUTICAL_MILE);

    companion object {
        fun fromKey(key: String?): SpeedUnit = entries.firstOrNull { it.key == key } ?: KMH
    }
}

/** One decimal below 100 so walking speeds stay readable; whole numbers above. */
fun formatSpeed(mps: Double, unit: SpeedUnit): String {
    val value = mps * unit.perMps
    if (!value.isFinite()) return "--"
    val positive = value.coerceAtLeast(0.0)
    return if (positive < 100.0) String.format(FR, "%.1f", positive) else String.format(FR, "%.0f", positive)
}

fun formatSpeedWithUnit(mps: Double, unit: SpeedUnit): String = "${formatSpeed(mps, unit)} ${unit.label}"

fun formatDistance(meters: Double, unit: SpeedUnit): String {
    if (!meters.isFinite()) return NO_VALUE
    val m = meters.coerceAtLeast(0.0)
    return when (unit) {
        SpeedUnit.KMH, SpeedUnit.MS -> when {
            m < 1000.0 -> String.format(FR, "%.0f m", m)
            m < 100_000.0 -> String.format(FR, "%.2f km", m / 1000.0)
            else -> String.format(FR, "%.1f km", m / 1000.0)
        }
        SpeedUnit.MPH -> String.format(FR, "%.2f mi", m / METERS_PER_MILE)
        SpeedUnit.KNOTS -> String.format(FR, "%.2f NM", m / METERS_PER_NAUTICAL_MILE)
    }
}

fun formatAltitude(meters: Double): String = String.format(FR, "%.0f m", meters)

fun formatAccuracy(meters: Float): String = String.format(FR, "±%.0f m", meters)

fun formatHeading(bearingDeg: Float): String {
    val degrees = ((bearingDeg.roundToInt() % 360) + 360) % 360
    val cardinal = CARDINALS[((degrees + 22) / 45) % CARDINALS.size]
    return "$degrees° $cardinal"
}

fun formatCoordinate(value: Double): String = String.format(FR, "%.6f°", value)

/** e.g. 48°51'24,0"N 2°21'03,0"E — rounded once on tenths of a second so 59,95" never shows as 60,0". */
fun formatDms(latitude: Double, longitude: Double): String =
    "${dms(latitude, "N", "S")} ${dms(longitude, "E", "O")}"

private fun dms(value: Double, positive: String, negative: String): String {
    val tenths = (abs(value) * 36_000.0).roundToLong()
    val degrees = tenths / 36_000
    val minutes = (tenths % 36_000) / 600
    val secondTenths = tenths % 600
    val hemisphere = if (value >= 0.0) positive else negative
    return String.format(
        FR,
        "%d°%02d'%02d,%d\"%s",
        degrees,
        minutes,
        secondTenths / 10,
        secondTenths % 10,
        hemisphere
    )
}

/** Clipboard keeps decimal points so the text pastes straight into maps and other tools. */
fun coordinatesForClipboard(latitude: Double, longitude: Double): String =
    String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude) + "\n" + formatDms(latitude, longitude)

fun formatAge(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "> 1 h"
}
