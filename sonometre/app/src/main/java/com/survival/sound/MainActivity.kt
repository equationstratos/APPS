package com.survival.sound

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

private const val PREFS_NAME = "survie"
private const val KEY_CALIBRATION = "sonometre_calibration"
private const val OFFSET_LIMIT = 20f

/** 10 s of history at one value per 100 ms window. */
private const val HISTORY_SIZE = 100

/** Full scale of the level bar and history graph. */
private const val SCALE_MAX_DB = 130.0
private const val WARNING_DB = 60.0
private const val DANGER_DB = 85.0

private const val NO_VALUE = "—"

private val ButtonShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private data class Reference(val db: Int, val label: String)

private val References = listOf(
    Reference(0, "Seuil d'audition"),
    Reference(30, "Chuchotement"),
    Reference(60, "Conversation"),
    Reference(80, "Trafic"),
    Reference(85, "Danger auditif"),
    Reference(110, "Concert"),
    Reference(130, "Seuil de douleur")
)

private enum class MicPermission { Granted, Denied, PermanentlyDenied }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { SurvieTheme { AppScreen() } }
    }
}

@Composable
fun SurvieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Bg,
            primaryContainer = Navy, onPrimaryContainer = Color.White,
            secondary = Accent, onSecondary = Bg,
            secondaryContainer = Navy, onSecondaryContainer = Color.White,
            background = Bg, onBackground = Color.White,
            surface = Card, onSurface = Color.White,
            surfaceVariant = Navy, onSurfaceVariant = TextSecondary,
            surfaceContainer = Card, surfaceContainerHigh = Card, surfaceContainerHighest = Navy,
            outline = Navy, outlineVariant = Navy,
            error = Danger, onError = Bg
        ),
        content = content
    )
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var autoRequested by rememberSaveable { mutableStateOf(false) }
    var permission by remember { mutableStateOf(readMicPermission(context, deniedOnce)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) deniedOnce = true
        permission = readMicPermission(context, deniedOnce)
    }

    LaunchedEffect(Unit) {
        if (permission != MicPermission.Granted && !autoRequested) {
            autoRequested = true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // The user may grant or revoke the permission from the system settings while we are paused.
    LifecycleResumeEffect(Unit) {
        permission = readMicPermission(context, deniedOnce)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header()
        when (permission) {
            MicPermission.Granted -> MeterContent()
            MicPermission.Denied -> PermissionCard(
                permanentlyDenied = false,
                onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
            )
            MicPermission.PermanentlyDenied -> PermissionCard(
                permanentlyDenied = true,
                onClick = { openAppSettings(context) }
            )
        }
        Text(
            "Valeurs indicatives : micro du téléphone non étalonné",
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun Header() {
    Column {
        Text("Sonomètre", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Niveau sonore ambiant", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun MeterContent() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var running by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var stats by rememberSaveable(stateSaver = LevelStats.Saver) { mutableStateOf(LevelStats()) }
    var history by remember { mutableStateOf(emptyList<Double>()) }
    var current by remember { mutableStateOf<Double?>(null) }
    var offset by remember {
        mutableFloatStateOf(prefs.getFloat(KEY_CALIBRATION, 0f).coerceIn(-OFFSET_LIMIT, OFFSET_LIMIT))
    }

    // Capture only while the screen is visible: ON_STOP cancels the flow, which releases the mic.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val measuring = running && lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    val view = LocalView.current
    DisposableEffect(measuring) {
        view.keepScreenOn = measuring
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(measuring) {
        if (!measuring) return@LaunchedEffect
        try {
            SoundLevelMeter.levels(context.applicationContext).collect { level ->
                current = level
                stats = stats.add(level)
                history = (history + level).takeLast(HISTORY_SIZE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? MicUnavailableException)?.message ?: MIC_UNAVAILABLE
            running = false
        } finally {
            current = null
        }
    }

    ReadoutCard(level = current?.let { calibrated(it, offset) })
    StatsRow(stats = stats, offset = offset)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                error = null
                running = !running
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = ButtonShape,
            colors = primaryButtonColors()
        ) {
            Text(if (running) "Arrêter" else "Démarrer", fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = {
                stats = LevelStats()
                history = emptyList()
            },
            enabled = stats.count > 0 || history.isNotEmpty(),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = ButtonShape,
            colors = secondaryButtonColors()
        ) {
            Text("Réinitialiser", fontWeight = FontWeight.SemiBold)
        }
    }
    error?.let { message ->
        SurvieCard {
            Text(message, color = Danger, fontSize = 14.sp)
        }
    }
    HistoryCard(history = history, offset = offset)
    CalibrationCard(
        offset = offset,
        onOffsetChange = { offset = it.roundToInt().toFloat() },
        onOffsetChangeFinished = { prefs.edit { putFloat(KEY_CALIBRATION, offset) } }
    )
    ReferenceCard(level = current?.let { calibrated(it, offset) })
}

@Composable
private fun ReadoutCard(level: Double?) {
    SurvieCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                level?.let(::formatDb) ?: NO_VALUE,
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular
            )
            Text("dB (approx.)", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            LevelBar(level)
            Spacer(Modifier.height(8.dp))
            Text(
                if (level == null) "Mesure arrêtée" else levelZone(level),
                color = if (level == null) TextSecondary else levelColor(level),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LevelBar(level: Double?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Navy)
    ) {
        if (level != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((level / SCALE_MAX_DB).toFloat().coerceIn(0f, 1f))
                    .background(levelColor(level))
            )
        }
    }
}

@Composable
private fun StatsRow(stats: LevelStats, offset: Float) {
    val empty = stats.count == 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Min", if (empty) NO_VALUE else formatDb(calibrated(stats.minDb, offset)), Modifier.weight(1f))
        StatTile("Moyenne", if (empty) NO_VALUE else formatDb(calibrated(stats.average, offset)), Modifier.weight(1f))
        StatTile("Max", if (empty) NO_VALUE else formatDb(calibrated(stats.maxDb, offset)), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Card, CardShape)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        Text(
            value,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = Tabular
        )
        Text("dB", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun HistoryCard(history: List<Double>, offset: Float) {
    SurvieCard {
        Text("Historique (10 s)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Bg),
            contentAlignment = Alignment.Center
        ) {
            HistoryGraph(history = history, offset = offset, modifier = Modifier.fillMaxSize())
            if (history.isEmpty()) {
                Text("Aucune mesure", color = TextSecondary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(Warning)
            Text("60 dB", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(16.dp))
            LegendDot(Danger)
            Text("85 dB", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(8.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun HistoryGraph(history: List<Double>, offset: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        fun yOf(db: Double): Float = height - (db / SCALE_MAX_DB).toFloat().coerceIn(0f, 1f) * height

        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        for ((db, color) in listOf(WARNING_DB to Warning, DANGER_DB to Danger)) {
            val y = yOf(db)
            drawLine(
                color = color.copy(alpha = 0.7f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        }

        if (history.size < 2) return@Canvas
        val step = width / (HISTORY_SIZE - 1)
        val startX = width - step * (history.size - 1)
        val line = Path()
        val area = Path()
        history.forEachIndexed { index, raw ->
            val x = startX + index * step
            val y = yOf(calibrated(raw, offset))
            if (index == 0) {
                line.moveTo(x, y)
                area.moveTo(x, height)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(width, height)
        area.close()
        drawPath(area, color = Accent.copy(alpha = 0.18f))
        drawPath(
            line,
            color = Accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun CalibrationCard(
    offset: Float,
    onOffsetChange: (Float) -> Unit,
    onOffsetChangeFinished: () -> Unit
) {
    SurvieCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Étalonnage",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatOffset(offset),
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular
            )
        }
        Slider(
            value = offset,
            onValueChange = onOffsetChange,
            onValueChangeFinished = onOffsetChangeFinished,
            valueRange = -OFFSET_LIMIT..OFFSET_LIMIT,
            steps = (OFFSET_LIMIT * 2).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Navy,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Text(
            "Corrigez l'écart avec un sonomètre de référence (de −20 à +20 dB).",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ReferenceCard(level: Double?) {
    val active = level?.let { db -> References.lastOrNull { it.db <= db } }
    SurvieCard {
        Text("Repères", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        References.forEach { reference ->
            val isActive = reference == active
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    reference.label,
                    color = if (isActive) Accent else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${reference.db} dB",
                    color = levelColor(reference.db.toDouble()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = Tabular
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(permanentlyDenied: Boolean, onClick: () -> Unit) {
    SurvieCard {
        Text(
            "Accès au micro nécessaire",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied) {
                "L'accès au micro a été refusé. Activez-le dans les paramètres de l'application, " +
                    "rubrique Autorisations > Micro."
            } else {
                "Le sonomètre mesure le niveau sonore avec le micro du téléphone. " +
                    "Aucun son n'est enregistré ni envoyé."
            },
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = ButtonShape,
            colors = primaryButtonColors()
        ) {
            Text(
                if (permanentlyDenied) "Ouvrir les paramètres" else "Autoriser",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SurvieCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun primaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Accent,
    contentColor = Bg,
    disabledContainerColor = Navy,
    disabledContentColor = TextSecondary
)

@Composable
private fun secondaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Navy,
    contentColor = Color.White,
    disabledContainerColor = Navy.copy(alpha = 0.5f),
    disabledContentColor = TextSecondary.copy(alpha = 0.4f)
)

private fun calibrated(raw: Double, offset: Float): Double = (raw + offset).coerceAtLeast(0.0)

private fun levelColor(db: Double): Color = when {
    db < WARNING_DB -> Success
    db < DANGER_DB -> Warning
    else -> Danger
}

private fun levelZone(db: Double): String = when {
    db < WARNING_DB -> "Niveau modéré"
    db < DANGER_DB -> "Niveau élevé"
    else -> "Danger auditif"
}

private fun formatDb(db: Double): String = String.format(Locale.FRANCE, "%.1f", db)

private fun formatOffset(offset: Float): String {
    val value = offset.roundToInt()
    return when {
        value > 0 -> "+$value dB"
        value < 0 -> "−${-value} dB"
        else -> "0 dB"
    }
}

private fun readMicPermission(context: Context, deniedOnce: Boolean): MicPermission {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return MicPermission.Granted
    }
    // After a denial, the rationale flag turns false only when the system will no longer show the dialog.
    val activity = context.findActivity()
    val blocked = deniedOnce && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    return if (blocked) MicPermission.PermanentlyDenied else MicPermission.Denied
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
