package com.survival.gps

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)
private const val PREFS_NAME = "survie"
private const val KEY_SPEED_UNIT = "vitesse_unite"
private const val STALE_AFTER_S = 5L
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val COPY_CONFIRMATION_MS = 2500L
private const val MAX_SPEED_FONT_SP = 88f

private val CardShape = RoundedCornerShape(16.dp)
private val ButtonShape = RoundedCornerShape(12.dp)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private data class LocationAccess(
    val precise: Boolean,
    val approximate: Boolean,
    val showRationale: Boolean,
) {
    val granted: Boolean get() = precise || approximate
}

private enum class Signal { SEARCHING, ACQUIRED, LOST }

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val locationManager = remember(context) { context.getSystemService(LocationManager::class.java) }
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var access by remember { mutableStateOf(context.readLocationAccess()) }
    var autoRequested by rememberSaveable { mutableStateOf(false) }
    var answered by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        answered = true
        access = context.readLocationAccess()
    }
    LaunchedEffect(Unit) {
        if (!access.granted && !autoRequested) {
            autoRequested = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }
    LifecycleResumeEffect(Unit) {
        access = context.readLocationAccess()
        onPauseOrDispose { }
    }

    var unit by remember { mutableStateOf(SpeedUnit.fromKey(prefs.getString(KEY_SPEED_UNIT, null))) }
    var fix by remember { mutableStateOf<GpsFix?>(null) }
    var satellites by remember { mutableStateOf<Satellites?>(null) }
    var providerEnabled by remember { mutableStateOf(true) }
    var startResult by remember { mutableStateOf<StartResult?>(null) }
    var trip by rememberSaveable(stateSaver = TripSaver) { mutableStateOf(Trip()) }

    LifecycleStartEffect(access.precise, access.approximate) {
        val session = if (access.granted && locationManager != null) {
            GpsSession(
                locationManager = locationManager,
                onFix = { newFix ->
                    fix = newFix
                    trip = trip.record(newFix)
                },
                onProviderEnabledChanged = { providerEnabled = it },
                onSatellites = { satellites = it },
            )
        } else {
            null
        }
        startResult = session?.start(access.precise)
        onStopOrDispose { session?.stop() }
    }

    var nowNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowNanos = SystemClock.elapsedRealtimeNanos()
                delay(500L)
            }
        }
    }

    val needsPermission = !access.granted || startResult == StartResult.DENIED
    val gpsMissing = locationManager == null || startResult == StartResult.NO_PROVIDER
    KeepScreenOn(active = !needsPermission && !gpsMissing && providerEnabled)

    var copyCount by remember { mutableIntStateOf(0) }
    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(copyCount) {
        if (copyCount > 0) {
            showCopied = true
            delay(COPY_CONFIRMATION_MS)
            showCopied = false
        }
    }

    val permanentlyDenied = answered && !access.showRationale
    val requestPermissions: () -> Unit = { permissionLauncher.launch(LOCATION_PERMISSIONS) }
    val openAppSettings: () -> Unit = { context.openAppSettings() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Header()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                needsPermission -> NoticeCard(
                    title = "Localisation requise",
                    titleColor = Warning,
                    message = if (permanentlyDenied) {
                        "L'accès à la position a été refusé. Autorisez « Position » dans les paramètres de " +
                            "l'application pour afficher la vitesse et les coordonnées. Aucune donnée ne quitte l'appareil."
                    } else {
                        "La vitesse et les coordonnées sont calculées par le GPS de l'appareil, sans connexion " +
                            "réseau. Autorisez l'accès à la position exacte pour continuer."
                    },
                    actionLabel = if (permanentlyDenied) "Ouvrir les paramètres" else "Autoriser",
                    onAction = if (permanentlyDenied) openAppSettings else requestPermissions,
                )
                gpsMissing -> NoticeCard(
                    title = "GPS non disponible sur cet appareil",
                    titleColor = Danger,
                    message = "Aucun récepteur de localisation utilisable n'a été trouvé.",
                )
                else -> {
                    if (!access.precise) {
                        NoticeCard(
                            title = "Position approximative",
                            titleColor = Warning,
                            message = "Seule la position approximative est autorisée : la vitesse, la distance " +
                                "et la précision nécessitent la position exacte.",
                            actionLabel = if (permanentlyDenied) "Ouvrir les paramètres" else "Autoriser la position exacte",
                            onAction = if (permanentlyDenied) openAppSettings else requestPermissions,
                        )
                    }
                    if (!providerEnabled) {
                        NoticeCard(
                            title = "Localisation désactivée",
                            titleColor = Warning,
                            message = "Activez la localisation de l'appareil pour mesurer la vitesse et la position.",
                            actionLabel = "Activer la localisation",
                            onAction = {
                                if (!context.launchSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) {
                                    context.launchSafely(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                        )
                    }
                    Dashboard(
                        fix = fix,
                        nowNanos = nowNanos,
                        satellites = satellites,
                        trip = trip,
                        unit = unit,
                        copied = showCopied,
                        onUnitChange = { newUnit ->
                            unit = newUnit
                            prefs.edit { putString(KEY_SPEED_UNIT, newUnit.key) }
                        },
                        onCopy = { current ->
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Coordonnées",
                                        coordinatesForClipboard(current.latitude, current.longitude)
                                    )
                                )
                                copyCount++
                            }
                        },
                        onResetTrip = { trip = Trip() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Text("Vitesse & position", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text("Compteur GPS hors ligne", color = TextSecondary, fontSize = 14.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Dashboard(
    fix: GpsFix?,
    nowNanos: Long,
    satellites: Satellites?,
    trip: Trip,
    unit: SpeedUnit,
    copied: Boolean,
    onUnitChange: (SpeedUnit) -> Unit,
    onCopy: (GpsFix) -> Unit,
    onResetTrip: () -> Unit,
) {
    val ageS = fix?.let { (nowNanos - it.elapsedRealtimeNanos).coerceAtLeast(0L) / NANOS_PER_SECOND }
    val fresh = ageS != null && ageS <= STALE_AFTER_S
    val signal = when {
        fix == null -> Signal.SEARCHING
        fresh -> Signal.ACQUIRED
        else -> Signal.LOST
    }
    val liveSpeed = if (fresh) fix?.speedMps?.toDouble() else null
    val displayedSpeed = liveSpeed?.let { if (it < MOVING_SPEED_MPS) 0.0 else it }
    val bearing = fix?.bearingDeg
    val heading = if (bearing != null && liveSpeed != null && liveSpeed >= MOVING_SPEED_MPS) {
        formatHeading(bearing)
    } else {
        NO_VALUE
    }
    val accuracy = fix?.accuracyM

    SpeedCard(
        speedMps = displayedSpeed,
        unit = unit,
        signal = signal,
        onUnitChange = onUnitChange,
    )
    StatRow(
        Stat("Vitesse max", formatSpeedWithUnit(trip.maxSpeedMps, unit)),
        Stat("Moyenne en mouvement", trip.averageMovingSpeedMps?.let { formatSpeedWithUnit(it, unit) } ?: NO_VALUE),
    )
    StatRow(
        Stat("Distance du trajet", formatDistance(trip.distanceM, unit)),
        Stat("Altitude", fix?.altitudeM?.let(::formatAltitude) ?: NO_VALUE),
    )
    StatRow(
        Stat("Cap", heading, caption = if (liveSpeed != null && liveSpeed < MOVING_SPEED_MPS) "À l'arrêt" else null),
        Stat(
            "Précision",
            accuracy?.let(::formatAccuracy) ?: NO_VALUE,
            valueColor = when {
                accuracy == null -> Color.White
                accuracy <= 20f -> Success
                else -> Warning
            },
        ),
    )
    StatRow(
        Stat(
            "Satellites",
            satellites?.let { "${it.used}/${it.visible}" } ?: NO_VALUE,
            caption = "utilisés / visibles",
        ),
        Stat(
            "Âge du point",
            ageS?.let(::formatAge) ?: NO_VALUE,
            valueColor = if (signal == Signal.LOST) Warning else Color.White,
        ),
    )
    PositionCard(fix = fix, copied = copied, onCopy = onCopy)
    ResetTripButton(onConfirm = onResetTrip)
}

@Composable
private fun SpeedCard(
    speedMps: Double?,
    unit: SpeedUnit,
    signal: Signal,
    onUnitChange: (SpeedUnit) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SignalLine(signal)
        val speedText = speedMps?.let { formatSpeed(it, unit) } ?: "--"
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Shrink only when the number would not fit (very large font scale).
            val fitted = with(LocalDensity.current) { (maxWidth / (speedText.length * 0.62f)).toSp() }
            val fontSize = if (fitted.value < MAX_SPEED_FONT_SP) fitted else MAX_SPEED_FONT_SP.sp
            Text(
                speedText,
                color = if (speedMps != null) Color.White else TextSecondary,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(unit.label, color = Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        UnitSelector(current = unit, onSelect = onUnitChange)
    }
}

@Composable
private fun SignalLine(signal: Signal) {
    val (color, text) = when (signal) {
        Signal.SEARCHING -> Warning to "Recherche du signal GPS…"
        Signal.ACQUIRED -> Success to "Signal GPS acquis"
        Signal.LOST -> Warning to "Signal GPS perdu, recherche…"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun UnitSelector(current: SpeedUnit, onSelect: (SpeedUnit) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeedUnit.entries.forEach { option ->
            val isSelected = option == current
            Button(
                onClick = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { selected = isSelected },
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Accent else Navy,
                    contentColor = if (isSelected) Bg else Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Text(option.label, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

private data class Stat(
    val label: String,
    val value: String,
    val caption: String? = null,
    val valueColor: Color = Color.White,
)

@Composable
private fun StatRow(left: Stat, right: Stat) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatTile(left, Modifier.weight(1f).fillMaxHeight())
        StatTile(right, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun StatTile(stat: Stat, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Card, CardShape)
            .padding(16.dp)
    ) {
        Text(stat.label, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            stat.value,
            color = stat.valueColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular,
        )
        stat.caption?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
    }
}

@Composable
private fun PositionCard(fix: GpsFix?, copied: Boolean, onCopy: (GpsFix) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp)
    ) {
        Text("Coordonnées", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        if (fix == null) {
            Text("En attente d'une position…", color = TextSecondary, fontSize = 16.sp)
        } else {
            CoordinateLine("Lat.", formatCoordinate(fix.latitude))
            CoordinateLine("Lon.", formatCoordinate(fix.longitude))
            Spacer(Modifier.height(4.dp))
            Text(formatDms(fix.latitude, fix.longitude), color = Accent, fontSize = 16.sp, style = Tabular)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { fix?.let(onCopy) },
            enabled = fix != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Bg,
                disabledContainerColor = Navy,
                disabledContentColor = TextSecondary,
            ),
        ) {
            Text("Copier les coordonnées", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (copied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Coordonnées copiées dans le presse-papiers",
                color = Success,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CoordinateLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.width(40.dp))
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Tabular)
    }
}

@Composable
private fun ResetTripButton(onConfirm: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Button(
        onClick = { confirming = true },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
    ) {
        Text("Réinitialiser le trajet", fontSize = 16.sp)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    confirming = false
                }) { Text("Réinitialiser") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Annuler") }
            },
            title = { Text("Réinitialiser le trajet ?") },
            text = { Text("La distance, la vitesse max et la vitesse moyenne seront remises à zéro.") },
        )
    }
}

@Composable
private fun NoticeCard(
    title: String,
    titleColor: Color,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp)
    ) {
        Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White, fontSize = 15.sp)
        if (actionLabel != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            ) {
                Text(actionLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    launchSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

private fun Context.launchSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
}
