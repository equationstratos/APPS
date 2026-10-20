package com.survival.altimeter

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper

private const val UPDATE_INTERVAL_MS = 1000L

enum class GpsStart { STARTED, NO_GPS, DENIED }

/**
 * GPS updates for the altitude only, started while the GPS panel is visible and the app resumed.
 * [geoid] converts the ellipsoidal height of the fix into an altitude above sea level.
 */
class GpsAltitudeSession(
    private val locationManager: LocationManager,
    private val geoid: () -> Geoid?,
    private val onReading: (GpsReading) -> Unit,
    private val onProviderEnabledChanged: (Boolean) -> Unit,
) {
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onReading(location.toReading(geoid()))
        }

        // Still abstract on Android 8 to 10: without these overrides the framework call
        // ends in an AbstractMethodError.
        @Deprecated("Remplacée par les callbacks de GnssStatus ; conservée pour Android 8 à 10.")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) onProviderEnabledChanged(true)
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) onProviderEnabledChanged(false)
        }
    }

    /** Altitude needs satellites: only the GPS provider is used (the network one gives no altitude). */
    @SuppressLint("MissingPermission")
    fun start(): GpsStart {
        if (LocationManager.GPS_PROVIDER !in locationManager.allProviders) return GpsStart.NO_GPS
        return try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper()
            )
            onProviderEnabledChanged(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            GpsStart.STARTED
        } catch (e: SecurityException) {
            GpsStart.DENIED
        } catch (e: IllegalArgumentException) {
            GpsStart.NO_GPS
        }
    }

    fun stop() {
        locationManager.removeUpdates(listener)
    }
}

private fun Location.toReading(geoid: Geoid?): GpsReading {
    var mslAltitude: Double? = null
    var vertical: Double? = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude()) {
        // Android 14+ may already provide the altitude above mean sea level.
        mslAltitude = mslAltitudeMeters
        if (hasMslAltitudeAccuracy()) vertical = mslAltitudeAccuracyMeters.toDouble()
    } else if (hasAltitude() && geoid != null) {
        mslAltitude = geoid.mslAltitude(latitude, longitude, altitude)
    }
    if (vertical == null && hasVerticalAccuracy()) vertical = verticalAccuracyMeters.toDouble()
    val reported = vertical?.takeIf { it.isFinite() && it > 0.0 }
    val horizontal = if (hasAccuracy()) accuracy.toDouble().takeIf { it.isFinite() && it > 0.0 } else null
    return GpsReading(
        altitudeM = mslAltitude?.takeIf { it.isFinite() },
        verticalAccuracyM = reported ?: horizontal?.times(VERTICAL_FROM_HORIZONTAL),
        accuracyEstimated = reported == null,
        elapsedMs = elapsedRealtimeNanos / 1_000_000L,
    )
}
