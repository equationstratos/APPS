package com.survival.sunmoon

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Longest wait for a fresh position before falling back on the last known one. */
internal const val LOCATION_TIMEOUT_MS = 30_000L

internal val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

internal enum class LocateFailure { NO_PERMISSION, UNAVAILABLE, DISABLED, TIMEOUT }

internal sealed class LocateResult {
    class Found(val place: SavedPlace) : LocateResult()
    class Failed(val reason: LocateFailure) : LocateResult()
}

internal fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasLocationPermission(): Boolean =
    isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

/** True when at least one GPS or network provider is switched on. */
internal fun Context.isLocationOn(): Boolean {
    val manager = getSystemService(LocationManager::class.java) ?: return false
    return manager.isEnabledSafely(LocationManager.GPS_PROVIDER) ||
        manager.isEnabledSafely(LocationManager.NETWORK_PROVIDER)
}

/**
 * One position for the ephemerides: a fresh fix asked to every usable provider at once (the first
 * answer wins, within [LOCATION_TIMEOUT_MS]), else the most recent position the system remembers.
 * Precision does not matter much here: 1 km moves a sunrise by a few seconds at most.
 */
@SuppressLint("MissingPermission")
internal suspend fun locateOnce(context: Context): LocateResult {
    val fine = context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!fine && !coarse) return LocateResult.Failed(LocateFailure.NO_PERMISSION)
    val manager = context.getSystemService(LocationManager::class.java)
        ?: return LocateResult.Failed(LocateFailure.UNAVAILABLE)
    val present: List<String> = try {
        manager.allProviders
    } catch (e: RuntimeException) {
        emptyList()
    }
    val candidates = buildList {
        // With approximate access only, Android 12+ still serves (coarsened) GPS fixes; older
        // versions refuse the GPS provider and need the network one.
        if (LocationManager.GPS_PROVIDER in present && (fine || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
            add(LocationManager.GPS_PROVIDER)
        }
        if (LocationManager.NETWORK_PROVIDER in present) add(LocationManager.NETWORK_PROVIDER)
    }
    val enabled = candidates.filter { manager.isEnabledSafely(it) }
    if (enabled.isNotEmpty()) {
        val fix = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { firstFix(context, manager, enabled) }
        if (fix != null) {
            return LocateResult.Found(fix.toPlace(PlaceSource.ofProvider(fix.provider), System.currentTimeMillis()))
        }
    }
    val last = lastKnown(manager, present)
    if (last != null) {
        val now = System.currentTimeMillis()
        val time = if (last.time in 1L..now) last.time else now
        return LocateResult.Found(last.toPlace(PlaceSource.LAST_KNOWN, time))
    }
    return LocateResult.Failed(
        when {
            candidates.isEmpty() -> LocateFailure.UNAVAILABLE
            enabled.isEmpty() -> LocateFailure.DISABLED
            else -> LocateFailure.TIMEOUT
        }
    )
}

/**
 * First usable fix from [providers]: getCurrentLocation on Android 11+, location updates removed
 * after the first fix before that. Null when every provider answered without a position.
 * Every request is cancelled as soon as one answers, or when the caller gives up.
 */
@SuppressLint("MissingPermission")
private suspend fun firstFix(context: Context, manager: LocationManager, providers: List<String>): Location? =
    suspendCancellableCoroutine { continuation ->
        val signals = ArrayList<CancellationSignal>()
        val listeners = ArrayList<LocationListener>()
        var pending = providers.size

        fun release() {
            signals.forEach { it.cancel() }
            listeners.forEach { manager.removeUpdates(it) }
        }

        fun finish(location: Location?) {
            if (!continuation.isActive) return
            release()
            continuation.resume(location)
        }

        fun noAnswer() {
            pending--
            if (pending <= 0) finish(null)
        }

        continuation.invokeOnCancellation { release() }
        val executor = ContextCompat.getMainExecutor(context)
        for (provider in providers) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    signals += signal
                    manager.getCurrentLocation(provider, signal, executor) { location ->
                        if (location != null && location.isUsable()) finish(location) else noAnswer()
                    }
                } else {
                    val listener = SingleFixListener { location -> if (location.isUsable()) finish(location) }
                    listeners += listener
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
            } catch (e: RuntimeException) {
                // SecurityException or IllegalArgumentException: this provider cannot be used.
                noAnswer()
            }
        }
    }

/** Most recent position remembered by any provider the app may read. */
@SuppressLint("MissingPermission")
private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
    providers
        .mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: RuntimeException) {
                null
            }
        }
        .filter { it.isUsable() }
        .maxByOrNull { it.time }

private fun LocationManager.isEnabledSafely(provider: String): Boolean = try {
    isProviderEnabled(provider)
} catch (e: RuntimeException) {
    false
}

/** Rejects the (0, 0) placeholder some devices report before a real fix. */
private fun Location.isUsable(): Boolean =
    isValidPosition(latitude, longitude) && !(latitude == 0.0 && longitude == 0.0)

private fun Location.toPlace(source: PlaceSource, timeMillis: Long) = SavedPlace(
    latitude = latitude,
    longitude = longitude,
    source = source,
    timeMillis = timeMillis,
    accuracyM = if (hasAccuracy()) accuracy.toDouble().takeIf { it.isFinite() && it >= 0.0 } else null,
)

/**
 * Listener for Android 8 to 10, where the provider callbacks are still abstract: without these
 * overrides the framework call would end in an AbstractMethodError.
 */
private class SingleFixListener(private val onFix: (Location) -> Unit) : LocationListener {
    override fun onLocationChanged(location: Location) = onFix(location)

    @Deprecated("Abstraite jusqu'à Android 10 : implémentée pour ces versions, jamais appelée ensuite.")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit
}
