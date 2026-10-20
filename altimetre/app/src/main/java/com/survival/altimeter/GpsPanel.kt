package com.survival.altimeter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/** A fix older than this is "signal lost". */
private const val STALE_FIX_MS = 10_000L

private data class LocationAccess(val precise: Boolean, val approximate: Boolean, val showRationale: Boolean)

/**
 * GPS altitude with its vertical accuracy. Location is requested only here: when the user opens the
 * GPS calibration, or at launch on a phone without barometer ([autoRequest]).
 * With [onCalibrate], offers to calibrate the barometer once the accuracy is good enough.
 */
@Composable
internal fun GpsAltitudePanel(
    unit: AltitudeUnit,
    autoRequest: Boolean,
    onCalibrate: ((GpsEstimate) -> Unit)?,
) {
    val context = LocalContext.current
    val locationManager = remember(context) { context.getSystemService(LocationManager::class.java) }
    // Listing the providers needs no permission: a phone without GPS is never asked for its location.
    val hasGps = remember(locationManager) {
        locationManager?.allProviders?.contains(LocationManager.GPS_PROVIDER) == true
    }
    val geoid by produceState<Geoid?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { loadGeoid(context) }
    }

    var access by remember { mutableStateOf(context.readLocationAccess()) }
    var answered by rememberSaveable { mutableStateOf(false) }
    var autoRequested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        answered = true
        access = context.readLocationAccess()
    }
    LaunchedEffect(Unit) {
        if (autoRequest && hasGps && !access.precise && !autoRequested) {
            autoRequested = true
            launcher.launch(LOCATION_PERMISSIONS)
        }
    }
    LifecycleResumeEffect(Unit) {
        access = context.readLocationAccess()
        onPauseOrDispose { }
    }

    var reading by remember { mutableStateOf<GpsReading?>(null) }
    var providerEnabled by remember { mutableStateOf(true) }
    var startResult by remember { mutableStateOf<GpsStart?>(null) }
    val averager = remember { GpsAltitudeAverager() }
    var estimate by remember { mutableStateOf<GpsEstimate?>(null) }
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LifecycleResumeEffect(access.precise, locationManager) {
        val session = if (access.precise && locationManager != null) {
            GpsAltitudeSession(
                locationManager = locationManager,
                geoid = { geoid },
                onReading = { newReading ->
                    reading = newReading
                    averager.add(newReading)
                    estimate = averager.estimate(newReading.elapsedMs)
                },
                onProviderEnabledChanged = { providerEnabled = it },
            )
        } else {
            null
        }
        startResult = session?.start()
        onPauseOrDispose {
            session?.stop()
            averager.clear()
            estimate = null
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                nowElapsed = SystemClock.elapsedRealtime()
                estimate = averager.estimate(nowElapsed)
                delay(1000L)
            }
        }
    }

    val permanentlyDenied = answered && !access.showRationale
    val request: () -> Unit = {
        if (permanentlyDenied) context.openAppSettings() else launcher.launch(LOCATION_PERMISSIONS)
    }
    val actionLabel = if (permanentlyDenied) "Ouvrir les paramètres" else "Autoriser la localisation"

    Column(Modifier.fillMaxWidth()) {
        when {
            locationManager == null || !hasGps || startResult == GpsStart.NO_GPS ->
                PanelMessage("Cet appareil n'a pas de GPS$NBSP: l'altitude GPS n'est pas disponible.")

            !access.precise || startResult == GpsStart.DENIED -> {
                PanelMessage(
                    when {
                        access.approximate ->
                            "Seule la position approximative est autorisée$NBSP: elle ne donne pas l'altitude. " +
                                "Autorisez la position exacte. Elle sert uniquement sur l'appareil, rien n'est envoyé."
                        permanentlyDenied ->
                            "L'accès à la position a été refusé. Autorisez la position exacte dans les " +
                                "paramètres de l'application pour utiliser l'altitude GPS."
                        else ->
                            "L'altitude GPS nécessite l'accès à la position exacte. Elle sert uniquement sur " +
                                "l'appareil, rien n'est envoyé."
                    }
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(actionLabel, request, Modifier.fillMaxWidth())
            }

            !providerEnabled -> {
                PanelMessage("La localisation est désactivée sur l'appareil.")
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Activer la localisation", { context.openLocationSettings() }, Modifier.fillMaxWidth())
            }

            else -> GpsReadingView(
                reading = reading?.takeIf { nowElapsed - it.elapsedMs <= STALE_FIX_MS },
                hadFix = reading != null,
                estimate = estimate,
                unit = unit,
                large = onCalibrate == null,
                onCalibrate = onCalibrate,
            )
        }
    }
}

@Composable
private fun GpsReadingView(
    reading: GpsReading?,
    hadFix: Boolean,
    estimate: GpsEstimate?,
    unit: AltitudeUnit,
    large: Boolean,
    onCalibrate: ((GpsEstimate) -> Unit)?,
) {
    val altitude = reading?.altitudeM
    if (reading == null || altitude == null) {
        PanelMessage(
            when {
                reading != null -> "Position reçue, altitude pas encore disponible$NBSP: patientez quelques secondes."
                hadFix -> "Signal GPS perdu. Rapprochez-vous d'un endroit dégagé."
                else -> "Recherche des satellites… Placez-vous à découvert, loin des bâtiments$NBSP; " +
                    "la première position peut prendre une minute."
            }
        )
        return
    }
    val accuracy = reading.verticalAccuracyM
    val accuracyColor = when {
        accuracy == null -> TextSecondary
        accuracy <= GPS_GOOD_ACCURACY_M -> Success
        accuracy <= GPS_MAX_ACCURACY_M -> Warning
        else -> TextSecondary
    }
    Text("Altitude GPS", color = TextSecondary, fontSize = 13.sp)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            formatAltitudeValue(altitude, unit),
            color = Color.White,
            fontSize = if (large) 56.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(6.dp))
        Text(unit.symbol, color = Accent, fontSize = if (large) 24.sp else 18.sp, modifier = Modifier.alignByBaseline())
    }
    Text(
        if (accuracy == null) {
            "Précision verticale inconnue"
        } else {
            "Précision verticale$NBSP: ${formatAccuracy(accuracy, unit)}" + if (reading.accuracyEstimated) " (estimée)" else ""
        },
        color = accuracyColor,
        fontSize = 15.sp,
        style = Tabular,
    )
    if (onCalibrate == null) return

    Spacer(Modifier.height(12.dp))
    if (estimate != null) {
        PrimaryButton(
            "Étalonner sur ${formatAltitude(estimate.altitudeM, unit)}",
            { onCalibrate(estimate) },
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Caption("Moyenne des ${estimate.fixCount} dernières mesures GPS précises.")
    } else if (accuracy != null && accuracy <= GPS_MAX_ACCURACY_M) {
        Caption("Mesures en cours… Restez immobile quelques secondes.")
    } else {
        Caption(
            "Précision insuffisante pour étalonner$NBSP: attendez ${formatAccuracy(GPS_MAX_ACCURACY_M, unit)} " +
                "ou mieux, à découvert. Une altitude lue sur une carte reste plus précise."
        )
    }
}

@Composable
private fun PanelMessage(text: String) {
    Text(text, color = Color.White, fontSize = 15.sp)
}

private fun Context.readLocationAccess(): LocationAccess {
    val activity = findActivity()
    return LocationAccess(
        precise = isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        approximate = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
        showRationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION),
    )
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun loadGeoid(context: Context): Geoid? = try {
    Geoid.fromBytes(context.resources.openRawResource(R.raw.egm96).use { it.readBytes() })
} catch (e: IOException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
