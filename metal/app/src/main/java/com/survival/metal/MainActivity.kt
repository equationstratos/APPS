package com.survival.metal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

private const val PREF_THRESHOLD = "metal_threshold"
private const val PREF_SOUND = "metal_sound"
private const val PREF_VIBRATION = "metal_vibration"
private const val MIN_THRESHOLD = 5f
private const val MAX_THRESHOLD = 50f
private const val DEFAULT_THRESHOLD = 15f

/** The gauge spans this many thresholds; the threshold tick therefore sits at 1/GAUGE_SCALE. */
private const val GAUGE_SCALE = 4f
private const val STRONG_FACTOR = 3f
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")
private val RingRoom = 18.dp

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
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember { sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            "Détecteur de métaux",
            color = Accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Mesure du champ magnétique", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        if (sensorManager == null || sensor == null) {
            MissingSensorCard()
        } else {
            DetectorContent(sensorManager, sensor)
        }
    }
}

@Composable
private fun DetectorContent(sensorManager: SensorManager, sensor: Sensor) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("survie", Context.MODE_PRIVATE) }
    val vibrator = remember { context.findVibrator() }
    val detector = rememberSaveable(saver = MagnetDetector.StateSaver) { MagnetDetector() }

    var threshold by rememberSaveable {
        mutableFloatStateOf(
            prefs.getFloat(PREF_THRESHOLD, DEFAULT_THRESHOLD).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        )
    }
    var soundOn by rememberSaveable { mutableStateOf(prefs.getBoolean(PREF_SOUND, true)) }
    var vibrationOn by rememberSaveable { mutableStateOf(prefs.getBoolean(PREF_VIBRATION, false)) }

    SensorLifecycle(detector, sensorManager, sensor)
    KeepScreenOn(detector.isRunning)

    val delta = detector.delta
    val absDelta = abs(delta)
    // Detection uses |Δ|: depending on its orientation, a magnet can also lower the field.
    val detecting = detector.isRunning && absDelta > threshold
    val strong = detecting && absDelta >= threshold * STRONG_FACTOR
    val strength = if (detecting) {
        ((absDelta - threshold) / (threshold * (GAUGE_SCALE - 1f))).coerceIn(0f, 1f)
    } else {
        -1f
    }
    val levelColor = when {
        strong -> Danger
        detecting -> Warning
        else -> Accent
    }
    val level = when {
        !detector.isCalibrated -> detector.calibrationProgress
        absDelta.isFinite() -> (absDelta / (threshold * GAUGE_SCALE)).coerceIn(0f, 1f)
        else -> 0f
    }

    // Keyed on isRunning so the ToneGenerator is released while the app is paused.
    DetectionFeedback(
        strength = strength,
        sound = soundOn && detector.isRunning,
        vibrator = if (vibrationOn && detector.isRunning) vibrator else null
    )

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DetectorGauge(
            level = level,
            color = levelColor,
            detecting = detecting,
            showThresholdTick = detector.isCalibrated,
            modifier = Modifier.size(240.dp)
        ) {
            GaugeReadout(detector, if (detecting) levelColor else Color.White)
        }
    }
    Spacer(Modifier.height(12.dp))
    StatusBanner(calibrated = detector.isCalibrated, detecting = detecting, color = levelColor)
    Spacer(Modifier.height(8.dp))
    AccuracyIndicator(detector.accuracy)
    if (detector.startFailed) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Impossible de démarrer le magnétomètre. Fermez les applications qui l'utilisent puis revenez.",
            color = Danger,
            fontSize = 14.sp
        )
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = detector::recalibrate,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
    ) {
        Text("Calibrer", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    Text(
        "Éloignez le téléphone de tout objet métallique avant de calibrer.",
        color = TextSecondary,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    )
    Spacer(Modifier.height(16.dp))
    HistoryCard(detector.history, threshold)
    Spacer(Modifier.height(12.dp))
    SettingsCard(
        threshold = threshold,
        onThresholdChange = { threshold = it },
        onThresholdChangeFinished = { prefs.edit { putFloat(PREF_THRESHOLD, threshold) } },
        soundOn = soundOn,
        onSoundChange = {
            soundOn = it
            prefs.edit { putBoolean(PREF_SOUND, it) }
        },
        vibrationAvailable = vibrator != null,
        vibrationOn = vibrationOn,
        onVibrationChange = {
            vibrationOn = it
            prefs.edit { putBoolean(PREF_VIBRATION, it) }
        }
    )
    Spacer(Modifier.height(12.dp))
    SurvieCard {
        Text(
            "Détecte les objets ferreux et les aimants à quelques centimètres. " +
                "Éloignez le téléphone des appareils électroniques.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SensorLifecycle(detector: MagnetDetector, sensorManager: SensorManager, sensor: Sensor) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, detector, sensorManager, sensor) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> detector.start(sensorManager, sensor)
                Lifecycle.Event.ON_PAUSE -> detector.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            detector.stop()
        }
    }
}

@Composable
private fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun DetectorGauge(
    level: Float,
    color: Color,
    detecting: Boolean,
    showThresholdTick: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (detecting) PulsingRing(color, Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(RingRoom)
        ) {
            drawGauge(level, color, showThresholdTick)
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(RingRoom + 28.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private fun DrawScope.drawGauge(level: Float, color: Color, showThresholdTick: Boolean) {
    val strokeWidth = 14.dp.toPx()
    val radius = size.minDimension / 2f
    drawCircle(color = Card, radius = radius)

    val arcRadius = radius - strokeWidth
    val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    drawArc(
        color = Navy,
        startAngle = ARC_START,
        sweepAngle = ARC_SWEEP,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke
    )
    if (level > 0f) {
        drawArc(
            color = color,
            startAngle = ARC_START,
            sweepAngle = ARC_SWEEP * level,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
    }
    if (showThresholdTick) {
        val angle = Math.toRadians((ARC_START + ARC_SWEEP / GAUGE_SCALE).toDouble())
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val reach = strokeWidth / 2f + 3.dp.toPx()
        drawLine(
            color = Color.White,
            start = center + direction * (arcRadius - reach),
            end = center + direction * (arcRadius + reach),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PulsingRing(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )
    Canvas(modifier) {
        val width = 4.dp.toPx()
        val outer = size.minDimension / 2f - width / 2f
        val inner = outer - RingRoom.toPx() + width
        drawCircle(
            color = color,
            radius = inner + (outer - inner) * progress,
            alpha = 1f - progress,
            style = Stroke(width = width)
        )
    }
}

@Composable
private fun GaugeReadout(detector: MagnetDetector, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!detector.isCalibrated) {
            Text("Calibrage", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "${(detector.calibrationProgress * 100f).roundToInt()} %",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                style = TabularDigits
            )
            Text(
                "Ne bougez pas",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                formatDelta(detector.delta),
                color = valueColor,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                style = TabularDigits,
                maxLines = 1,
                softWrap = false
            )
            Text("Δ µT", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "|B| ${formatField(detector.magnitude)} µT",
                color = TextSecondary,
                fontSize = 13.sp,
                style = TabularDigits,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusBanner(calibrated: Boolean, detecting: Boolean, color: Color) {
    val label: String
    val container: Color
    val content: Color
    when {
        !calibrated -> {
            label = "Calibrage en cours…"
            container = Navy
            content = Color.White
        }
        detecting -> {
            label = "MÉTAL DÉTECTÉ"
            container = color
            content = Bg
        }
        else -> {
            label = "Aucune détection"
            container = Navy
            content = TextSecondary
        }
    }
    Text(
        label,
        color = content,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(vertical = 12.dp, horizontal = 16.dp)
    )
}

@Composable
private fun AccuracyIndicator(accuracy: Int) {
    val (label, color) = when (accuracy) {
        ACCURACY_UNKNOWN -> "Lecture du capteur…" to TextSecondary
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Précision du capteur : élevée" to Success
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Précision du capteur : moyenne" to Accent
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Précision faible : décrivez un 8" to Warning
        else -> "Calibrez : décrivez un 8" to Danger
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (color == Danger) Danger else TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HistoryCard(history: FloatArray, threshold: Float) {
    SurvieCard {
        Text("Historique de Δ (µT)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Bg)
        ) {
            drawHistory(history, threshold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Pointillés : seuil de détection (±${threshold.roundToInt()} µT)",
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

private fun DrawScope.drawHistory(history: FloatArray, threshold: Float) {
    val midY = size.height / 2f
    var peak = threshold * 1.5f
    for (value in history) peak = max(peak, abs(value) * 1.15f)
    val scale = midY / peak

    drawLine(
        color = Navy,
        start = Offset(0f, midY),
        end = Offset(size.width, midY),
        strokeWidth = 1.dp.toPx()
    )
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)
    for (y in floatArrayOf(midY - threshold * scale, midY + threshold * scale)) {
        drawLine(
            color = Warning.copy(alpha = 0.7f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dash
        )
    }
    if (history.size < 2) return

    val step = size.width / (HISTORY_SIZE - 1)
    val startX = size.width - (history.size - 1) * step
    val path = Path()
    history.forEachIndexed { i, value ->
        val x = startX + i * step
        val y = midY - value * scale
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = Accent,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun SettingsCard(
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    onThresholdChangeFinished: () -> Unit,
    soundOn: Boolean,
    onSoundChange: (Boolean) -> Unit,
    vibrationAvailable: Boolean,
    vibrationOn: Boolean,
    onVibrationChange: (Boolean) -> Unit
) {
    SurvieCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Seuil de détection",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${threshold.roundToInt()} µT",
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                style = TabularDigits
            )
        }
        Slider(
            value = threshold,
            onValueChange = { onThresholdChange(it.roundToInt().toFloat()) },
            onValueChangeFinished = onThresholdChangeFinished,
            valueRange = MIN_THRESHOLD..MAX_THRESHOLD,
            steps = (MAX_THRESHOLD - MIN_THRESHOLD).toInt() - 1,
            // 44 snap points would draw a dense row of tick dots across the track.
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                activeTickColor = Color.Transparent,
                inactiveTrackColor = Navy,
                inactiveTickColor = Color.Transparent
            )
        )
        Row {
            Text("Plus sensible", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Moins sensible", color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow("Son", soundOn, onSoundChange)
        if (vibrationAvailable) {
            ToggleRow("Vibration", vibrationOn, onVibrationChange)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bg,
                checkedTrackColor = Accent,
                checkedBorderColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Navy,
                uncheckedBorderColor = TextSecondary
            )
        )
    }
}

@Composable
private fun MissingSensorCard() {
    SurvieCard {
        Text(
            "Magnétomètre non disponible sur cet appareil.",
            color = Danger,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Le détecteur de métaux utilise le capteur de champ magnétique (boussole) du téléphone, " +
                "absent de ce modèle.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SurvieCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        content = content
    )
}

private fun formatDelta(value: Float): String = when {
    !value.isFinite() -> "—"
    abs(value) < 0.05f -> "0,0"
    abs(value) >= 100f -> String.format(Locale.FRANCE, "%+.0f", value)
    else -> String.format(Locale.FRANCE, "%+.1f", value)
}

private fun formatField(value: Float): String =
    if (value.isFinite()) String.format(Locale.FRANCE, "%.1f", value) else "—"
