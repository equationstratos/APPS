package com.survival.sunmoon

import kotlin.math.abs

/** Where the remembered position comes from. [key] is stored, [label] is shown. */
internal enum class PlaceSource(val key: String, val label: String) {
    GPS("gps", "GPS"),
    NETWORK("network", "Réseau (Wi-Fi ou antennes)"),
    DEVICE("device", "Localisation de l'appareil"),
    LAST_KNOWN("last_known", "Dernière position connue"),
    MANUAL("manual", "Saisie manuelle");

    companion object {
        fun fromKey(key: String): PlaceSource? = entries.firstOrNull { it.key == key }

        /** Source of a fresh fix from the name of the Android location provider that gave it. */
        fun ofProvider(provider: String?): PlaceSource = when (provider) {
            "gps" -> GPS
            "network" -> NETWORK
            else -> DEVICE
        }
    }
}

/** The position used for the ephemerides, remembered between launches. */
internal class SavedPlace(
    val latitude: Double,
    val longitude: Double,
    val source: PlaceSource,
    /** When the position was obtained or typed, epoch milliseconds. */
    val timeMillis: Long,
    /** Horizontal accuracy in metres, when the device reported one. */
    val accuracyM: Double?,
)

internal fun isValidPosition(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && abs(latitude) <= 90.0 && abs(longitude) <= 180.0

/** « lat;lon;source;time;accuracy » with locale-independent numbers. */
internal fun encodePlace(place: SavedPlace): String =
    listOf(
        place.latitude.toString(),
        place.longitude.toString(),
        place.source.key,
        place.timeMillis.toString(),
        place.accuracyM?.toString() ?: "",
    ).joinToString(";")

/** Inverse of [encodePlace]; null for a missing or damaged value. */
internal fun decodePlace(text: String?): SavedPlace? {
    if (text.isNullOrBlank()) return null
    val parts = text.split(';')
    if (parts.size != 5) return null
    val latitude = parts[0].toDoubleOrNull() ?: return null
    val longitude = parts[1].toDoubleOrNull() ?: return null
    if (!isValidPosition(latitude, longitude)) return null
    val source = PlaceSource.fromKey(parts[2]) ?: return null
    val time = parts[3].toLongOrNull() ?: return null
    val accuracy = if (parts[4].isEmpty()) null else parts[4].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    return SavedPlace(latitude, longitude, source, time, accuracy)
}
