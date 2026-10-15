package com.survival.gps

import android.location.Location
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** Below this speed the receiver is considered stationary (GPS speed noise is ~0.1–0.4 m/s). */
const val MOVING_SPEED_MPS = 0.5

private const val MAX_ACCURACY_M = 20.0
private const val MAX_SAMPLE_GAP_S = 5.0

/**
 * Trip statistics. Distance only advances from an anchor point once a fix is accurate
 * (≤ 20 m) and farther from the anchor than its own uncertainty, so a stationary receiver
 * does not accumulate jitter.
 */
data class Trip(
    val distanceM: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val movingTimeS: Double = 0.0,
    val movingDistanceM: Double = 0.0,
    val anchorLat: Double = Double.NaN,
    val anchorLon: Double = Double.NaN,
    val lastFixNanos: Long = 0L,
) {
    val averageMovingSpeedMps: Double?
        get() = if (movingTimeS >= 1.0) movingDistanceM / movingTimeS else null

    fun record(fix: GpsFix): Trip {
        if (lastFixNanos != 0L && fix.elapsedRealtimeNanos <= lastFixNanos) return this

        val accuracy = fix.accuracyM?.toDouble()?.takeIf { it <= MAX_ACCURACY_M }
        val speed = if (accuracy != null) fix.speedMps?.toDouble() else null
        val gapS = if (lastFixNanos == 0L) {
            Double.POSITIVE_INFINITY
        } else {
            (fix.elapsedRealtimeNanos - lastFixNanos) / 1e9
        }
        val movingDt = if (speed != null && speed >= MOVING_SPEED_MPS && gapS <= MAX_SAMPLE_GAP_S) gapS else 0.0

        var distance = distanceM
        var lat = anchorLat
        var lon = anchorLon
        if (accuracy != null) {
            if (lat.isNaN() || lon.isNaN()) {
                lat = fix.latitude
                lon = fix.longitude
            } else {
                val step = distanceMeters(lat, lon, fix.latitude, fix.longitude)
                if (step > accuracy) {
                    distance += step
                    lat = fix.latitude
                    lon = fix.longitude
                }
            }
        }

        return Trip(
            distanceM = distance,
            maxSpeedMps = maxOf(maxSpeedMps, speed ?: 0.0),
            movingTimeS = movingTimeS + movingDt,
            movingDistanceM = movingDistanceM + (speed ?: 0.0) * movingDt,
            anchorLat = lat,
            anchorLon = lon,
            lastFixNanos = fix.elapsedRealtimeNanos,
        )
    }
}

private fun distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    val from = Location("trajet").apply {
        latitude = fromLat
        longitude = fromLon
    }
    val to = Location("trajet").apply {
        latitude = toLat
        longitude = toLon
    }
    return from.distanceTo(to).toDouble()
}

val TripSaver: Saver<Trip, Any> = listSaver<Trip, Any>(
    save = {
        listOf<Any>(
            it.distanceM,
            it.maxSpeedMps,
            it.movingTimeS,
            it.movingDistanceM,
            it.anchorLat,
            it.anchorLon,
            it.lastFixNanos,
        )
    },
    restore = {
        Trip(
            distanceM = it[0] as Double,
            maxSpeedMps = it[1] as Double,
            movingTimeS = it[2] as Double,
            movingDistanceM = it[3] as Double,
            anchorLat = it[4] as Double,
            anchorLon = it[5] as Double,
            lastFixNanos = it[6] as Long,
        )
    },
)
