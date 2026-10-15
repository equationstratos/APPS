package com.survival.gps

import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

private const val UPDATE_INTERVAL_MS = 1000L

/** Immutable snapshot of a location update, decoupled from the mutable platform [Location]. */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val accuracyM: Float?,
    val elapsedRealtimeNanos: Long,
)

data class Satellites(val used: Int, val visible: Int)

enum class StartResult { STARTED, NO_PROVIDER, DENIED }

/**
 * One registration of the location listener and GNSS status callback. Created on ON_START and
 * stopped on ON_STOP so updates always resume when the user comes back to the app.
 */
class GpsSession(
    private val locationManager: LocationManager,
    private val onFix: (GpsFix) -> Unit,
    private val onProviderEnabledChanged: (Boolean) -> Unit,
    private val onSatellites: (Satellites?) -> Unit,
) {
    private var activeProvider: String? = null
    private var gnssRegistered = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onFix(location.toGpsFix())
        }

        // Still abstract on Android 8 to 10: without these overrides the framework call
        // ends in an AbstractMethodError.
        @Deprecated("Remplacée par les callbacks de GnssStatus ; conservée pour Android 8 à 10.")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) {
            if (provider == activeProvider) onProviderEnabledChanged(true)
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == activeProvider) onProviderEnabledChanged(false)
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            // Dual-frequency receivers list one entry per signal (L1, L5...): count each satellite once.
            val visible = HashSet<Int>()
            val used = HashSet<Int>()
            for (index in 0 until status.satelliteCount) {
                val id = status.getConstellationType(index) * 1000 + status.getSvid(index)
                visible.add(id)
                if (status.usedInFix(index)) used.add(id)
            }
            onSatellites(Satellites(used = used.size, visible = visible.size))
        }

        override fun onStopped() {
            onSatellites(null)
        }
    }

    /**
     * Precise access uses the GPS provider only. With approximate access alone, Android 12+
     * serves coarsened GPS fixes while older versions refuse GPS, so the network provider is the fallback.
     */
    @SuppressLint("MissingPermission")
    fun start(precise: Boolean): StartResult {
        val available = locationManager.allProviders
        val candidates = buildList {
            if (LocationManager.GPS_PROVIDER in available) add(LocationManager.GPS_PROVIDER)
            if (!precise && LocationManager.NETWORK_PROVIDER in available) add(LocationManager.NETWORK_PROVIDER)
        }
        if (candidates.isEmpty()) return StartResult.NO_PROVIDER

        val provider = candidates.firstOrNull { candidate ->
            try {
                locationManager.requestLocationUpdates(
                    candidate,
                    UPDATE_INTERVAL_MS,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
                true
            } catch (e: SecurityException) {
                false
            } catch (e: IllegalArgumentException) {
                false
            }
        } ?: return StartResult.DENIED

        activeProvider = provider
        onProviderEnabledChanged(locationManager.isProviderEnabled(provider))

        if (precise) {
            gnssRegistered = try {
                locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
            } catch (e: SecurityException) {
                false
            }
        }
        return StartResult.STARTED
    }

    fun stop() {
        locationManager.removeUpdates(locationListener)
        if (gnssRegistered) {
            locationManager.unregisterGnssStatusCallback(gnssCallback)
            gnssRegistered = false
        }
        activeProvider = null
        onSatellites(null)
    }
}

private fun Location.toGpsFix(): GpsFix {
    // Prefer altitude above sea level (Android 14+) over the raw WGS84 ellipsoid height.
    val altitudeMeters: Double? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude() -> mslAltitudeMeters
        hasAltitude() -> this.altitude
        else -> null
    }
    return GpsFix(
        latitude = latitude,
        longitude = longitude,
        altitudeM = altitudeMeters?.takeIf { it.isFinite() },
        speedMps = if (hasSpeed()) speed.takeIf { it.isFinite() && it >= 0f } else null,
        bearingDeg = if (hasBearing()) bearing.takeIf { it.isFinite() } else null,
        accuracyM = if (hasAccuracy()) accuracy.takeIf { it.isFinite() && it >= 0f } else null,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
    )
}
