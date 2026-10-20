package com.survival.notes

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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Longest wait for a fresh position before falling back on the best one received. */
internal const val LOCATION_TIMEOUT_S = 30
private const val LOCATION_TIMEOUT_MS = LOCATION_TIMEOUT_S * 1000L

internal val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

internal enum class LocateFailure { NO_PERMISSION, UNAVAILABLE, DISABLED, TIMEOUT, INTERRUPTED }

internal sealed class LocateResult {
    class Found(val location: NoteLocation) : LocateResult()
    class Failed(val reason: LocateFailure) : LocateResult()
}

internal fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasLocationPermission(): Boolean =
    isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

/** Only "approximate" was granted (Android 12+ choice): fixes may be off by kilometres. */
internal fun Context.isApproximateOnly(): Boolean =
    !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) && isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

/** False after "Ne plus demander" (or a second refusal): only the settings can grant it then. */
internal fun Context.canAskLocationAgain(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
}

/**
 * One position for a note. GPS and network are asked at once: a GPS fix ends the search at once,
 * a network fix is kept (and reported through [onProvisional]) while waiting for the GPS up to
 * [LOCATION_TIMEOUT_S] s. Without any answer, the most recent position the system remembers is used.
 */
@SuppressLint("MissingPermission")
internal suspend fun locateOnce(context: Context, onProvisional: (NoteLocation) -> Unit): LocateResult {
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
        val best = BestFix()
        withTimeoutOrNull(LOCATION_TIMEOUT_MS) { awaitFix(context, manager, enabled, best, onProvisional) }
        val found = best.location
        if (found != null) {
            return LocateResult.Found(found.toNoteLocation(sourceOf(best.provider), System.currentTimeMillis()))
        }
    }
    val last = lastKnown(manager, present)
    if (last != null) {
        val now = System.currentTimeMillis()
        val time = if (last.time in 1L..now) last.time else now
        return LocateResult.Found(last.toNoteLocation(LocationSource.LAST_KNOWN, time))
    }
    return LocateResult.Failed(
        when {
            candidates.isEmpty() -> LocateFailure.UNAVAILABLE
            enabled.isEmpty() -> LocateFailure.DISABLED
            else -> LocateFailure.TIMEOUT
        }
    )
}

private class BestFix {
    var location: Location? = null
    var provider: String? = null
}

/**
 * Asks [providers] for one fix each (getCurrentLocation on Android 11+, location updates removed
 * at the end before that). Returns as soon as the GPS answers or when every provider answered.
 * Every request is cancelled when this returns or when the caller gives up.
 */
@SuppressLint("MissingPermission")
private suspend fun awaitFix(
    context: Context,
    manager: LocationManager,
    providers: List<String>,
    best: BestFix,
    onProvisional: (NoteLocation) -> Unit,
) {
    suspendCancellableCoroutine<Unit> { continuation ->
        val signals = ArrayList<CancellationSignal>()
        val listeners = ArrayList<LocationListener>()
        val answered = HashSet<String>()

        fun release() {
            signals.forEach { it.cancel() }
            listeners.forEach { manager.removeUpdates(it) }
        }

        fun finish() {
            if (!continuation.isActive) return
            release()
            continuation.resume(Unit)
        }

        fun offer(provider: String, location: Location?) {
            if (!continuation.isActive || !answered.add(provider)) return
            if (location != null && location.isUsable()) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    best.location = location
                    best.provider = provider
                    finish()
                    return
                }
                val current = best.location
                if (current == null || location.accuracyOrMax() < current.accuracyOrMax()) {
                    best.location = location
                    best.provider = provider
                    onProvisional(location.toNoteLocation(sourceOf(provider), System.currentTimeMillis()))
                }
            }
            if (answered.size >= providers.size) finish()
        }

        continuation.invokeOnCancellation { release() }
        val executor = ContextCompat.getMainExecutor(context)
        for (provider in providers) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    signals += signal
                    manager.getCurrentLocation(provider, signal, executor) { location -> offer(provider, location) }
                } else {
                    val listener = SingleFixListener { location -> offer(provider, location) }
                    listeners += listener
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
            } catch (e: RuntimeException) {
                // SecurityException or IllegalArgumentException: this provider cannot be used.
                offer(provider, null)
            }
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

private fun sourceOf(provider: String?): LocationSource = when (provider) {
    LocationManager.GPS_PROVIDER -> LocationSource.GPS
    LocationManager.NETWORK_PROVIDER -> LocationSource.NETWORK
    else -> LocationSource.OTHER
}

/** Rejects the (0, 0) placeholder some devices report before a real fix. */
private fun Location.isUsable(): Boolean =
    isValidCoordinate(latitude, longitude) && !(latitude == 0.0 && longitude == 0.0)

private fun Location.accuracyOrMax(): Float =
    if (hasAccuracy() && accuracy.isFinite() && accuracy >= 0f) accuracy else Float.MAX_VALUE

private fun Location.toNoteLocation(source: LocationSource, timeMillis: Long) = NoteLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyM = if (hasAccuracy()) accuracy.toDouble().takeIf { it.isFinite() && it >= 0.0 } else null,
    timeMillis = timeMillis,
    source = source,
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
