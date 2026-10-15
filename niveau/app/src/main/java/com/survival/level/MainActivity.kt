package com.survival.level

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

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

/** A reading counts as level when every displayed (rounded) angle is within ±0,5°. */
private const val LEVEL_TOLERANCE_DEG = 0.5f

/** The haptic tick re-arms only once the device has clearly left the level zone. */
private const val TICK_REARM_DEG = 1f

/** Angle at which the bubble covers ~63 % of its travel: fine near zero, never off-scale. */
private const val BULLSEYE_SCALE_DEG = 6f
private const val TUBE_SCALE_DEG = 4f

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val sensorManager: SensorManager? = remember(context) { context.getSystemService(SensorManager::class.java) }
    val accelerometer: Sensor? = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (sensorManager == null || accelerometer == null) {
            Header(subtitle = "Capteur requis : accéléromètre")
            MissingSensorPanel()
        } else {
            LevelContent(sensorManager, accelerometer)
        }
    }
}

@Composable
private fun Header(subtitle: String) {
    Text("Niveau", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(subtitle, color = TextSecondary, fontSize = 14.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun MissingSensorPanel() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        Text(
            "Accéléromètre non disponible sur cet appareil.",
            color = Danger,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Le niveau a besoin de ce capteur pour mesurer l'inclinaison.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ColumnScope.LevelContent(sensorManager: SensorManager, accelerometer: Sensor) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var calibration by remember(prefs) { mutableStateOf(prefs.loadCalibration()) }
    var raw by remember { mutableStateOf<LevelReading?>(null) }
    var reliable by remember { mutableStateOf(true) }

    KeepScreenOn()
    AccelerometerEffect(
        sensorManager = sensorManager,
        accelerometer = accelerometer,
        onReading = { raw = it },
        onReliabilityChange = { reliable = it }
    )

    val reading = raw
    val measure = reading?.let { calibration.applyTo(it) }
    val level = measure?.isLevel() == true
    val farFromLevel = measure != null && measure.maxDeviation > TICK_REARM_DEG
    val calibrated = reading != null && calibration.isSetFor(reading)
    val canCalibrate = reading != null && reading.maxDeviation <= MAX_CALIBRATION_DEG

    var tickArmed by remember { mutableStateOf(true) }
    LaunchedEffect(level) {
        if (level && tickArmed) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            tickArmed = false
        }
    }
    LaunchedEffect(farFromLevel) {
        if (farFromLevel) tickArmed = true
    }

    Header(
        subtitle = when (measure) {
            is LevelReading.Flat -> "À plat : niveau à bulle"
            is LevelReading.Upright -> "Sur la tranche : niveau tubulaire"
            null -> "Mesure en cours…"
        }
    )

    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (measure != null) {
            LevelGauge(measure = measure, level = level, modifier = Modifier.fillMaxSize())
        }
    }

    Spacer(Modifier.height(16.dp))
    ReadoutPanel(measure = measure, level = level)
    Spacer(Modifier.height(12.dp))

    val (hint, hintColor) = when {
        !reliable -> "Capteur signalé peu fiable : vérifiez la mesure." to Warning
        reading == null -> "Posez l'appareil à plat ou sur une tranche." to TextSecondary
        calibrated && reading is LevelReading.Upright -> "Calibrage actif pour cette tranche." to TextSecondary
        calibrated -> "Calibrage actif pour la mesure à plat." to TextSecondary
        !canCalibrate -> "Calibrage possible à moins de 5° d'inclinaison." to TextSecondary
        else -> "Sur une surface de référence, touchez Calibrer." to TextSecondary
    }
    Text(
        hint,
        color = hintColor,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                if (reading != null && canCalibrate) {
                    calibration = calibration.calibratedOn(reading).also(prefs::saveCalibration)
                }
            },
            enabled = canCalibrate,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Bg,
                disabledContainerColor = Navy,
                disabledContentColor = TextSecondary
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        ) {
            Text("Calibrer", fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = {
                if (reading != null) {
                    calibration = calibration.clearedFor(reading).also(prefs::saveCalibration)
                }
            },
            enabled = calibrated,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Navy,
                contentColor = Color.White,
                disabledContainerColor = Navy.copy(alpha = 0.5f),
                disabledContentColor = TextSecondary
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        ) {
            Text("Réinitialiser", fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Listens to the accelerometer only while the screen is resumed. */
@Composable
private fun AccelerometerEffect(
    sensorManager: SensorManager,
    accelerometer: Sensor,
    onReading: (LevelReading) -> Unit,
    onReliabilityChange: (Boolean) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnReading by rememberUpdatedState(onReading)
    val currentOnReliabilityChange by rememberUpdatedState(onReliabilityChange)

    DisposableEffect(lifecycleOwner, sensorManager, accelerometer) {
        val listener = LevelSensor(
            onReading = { currentOnReading(it) },
            onReliabilityChange = { currentOnReliabilityChange(it) }
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    listener.reset()
                    sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
                }
                Lifecycle.Event.ON_PAUSE -> sensorManager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }
}

@Composable
private fun ReadoutPanel(measure: LevelReading?, level: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            when (measure) {
                is LevelReading.Flat -> {
                    Readout("X · gauche-droite", measure.x, level, Modifier.weight(1f))
                    Readout("Y · haut-bas", measure.y, level, Modifier.weight(1f))
                }
                is LevelReading.Upright -> Readout("Inclinaison", measure.angle, level, Modifier.weight(1f))
                null -> Readout("Inclinaison", null, level = false, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                measure == null -> "—"
                level -> "De niveau"
                else -> "Pas de niveau"
            },
            color = if (level) Success else TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Readout(label: String, degrees: Float?, level: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        Text(
            text = degrees?.let(::formatDegrees) ?: "—",
            color = if (level) Success else Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun LevelGauge(measure: LevelReading, level: Boolean, modifier: Modifier = Modifier) {
    val bubbleColor = if (level) Success else Accent
    val guideColor = if (level) Success else TextSecondary
    val description = if (level) "Bulle centrée : de niveau" else "Bulle décentrée : pas de niveau"
    Canvas(modifier.semantics { contentDescription = description }) {
        when (measure) {
            is LevelReading.Flat -> drawBullseye(measure.x, measure.y, bubbleColor, guideColor)
            // The vial is fixed to the edge the phone stands on, like the body of a real level.
            is LevelReading.Upright -> {
                val alongWidth = measure.edge % 2 == 0
                val along = if (alongWidth) size.width else size.height
                val across = if (alongWidth) size.height else size.width
                rotate(degrees = measure.edge * 90f) {
                    drawTube(measure.angle, minOf(along * 0.94f, across * 4f), bubbleColor, guideColor)
                }
            }
        }
    }
}

/** Circular level seen from above: the bubble drifts toward the higher side. */
private fun DrawScope.drawBullseye(x: Float, y: Float, bubbleColor: Color, guideColor: Color) {
    val rim = 3.dp.toPx()
    val radius = size.minDimension / 2f - rim
    if (radius <= 0f) return
    val bubbleRadius = radius * 0.16f
    val travel = radius - bubbleRadius - rim
    val thin = 1.5.dp.toPx()

    drawCircle(Card, radius = radius, center = center)
    drawCircle(Navy, radius = radius, center = center, style = Stroke(width = rim))
    drawLine(Navy, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = thin)
    drawLine(Navy, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = thin)
    for (degrees in floatArrayOf(2f, 5f, 10f)) {
        drawCircle(
            Navy,
            radius = bubbleRadius + travel * response(degrees, BULLSEYE_SCALE_DEG),
            center = center,
            style = Stroke(width = thin)
        )
    }

    val magnitude = hypot(x, y)
    val shift = if (magnitude > 0f) travel * response(magnitude, BULLSEYE_SCALE_DEG) / magnitude else 0f
    // Screen y grows downward while the sensor's y grows toward the top of the phone.
    val bubbleCenter = center + Offset(x * shift, -y * shift)
    drawCircle(bubbleColor, radius = bubbleRadius, center = bubbleCenter)
    drawCircle(
        Color.White.copy(alpha = 0.35f),
        radius = bubbleRadius * 0.3f,
        center = bubbleCenter + Offset(-bubbleRadius * 0.35f, -bubbleRadius * 0.35f)
    )

    drawCircle(
        guideColor,
        radius = bubbleRadius + travel * response(LEVEL_TOLERANCE_DEG, BULLSEYE_SCALE_DEG),
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
}

/** Horizontal vial drawn in the portrait frame; the caller rotates it onto the resting edge. */
private fun DrawScope.drawTube(angle: Float, length: Float, bubbleColor: Color, guideColor: Color) {
    if (length <= 0f) return
    val height = length * 0.2f
    val rim = 3.dp.toPx()
    val left = center.x - length / 2f
    val top = center.y - height / 2f
    val bottom = top + height

    drawRoundRect(Card, topLeft = Offset(left, top), size = Size(length, height), cornerRadius = CornerRadius(height / 2f))
    drawRoundRect(
        Navy,
        topLeft = Offset(left, top),
        size = Size(length, height),
        cornerRadius = CornerRadius(height / 2f),
        style = Stroke(width = rim)
    )

    val bubbleLength = length * 0.22f
    val bubbleHeight = height * 0.62f
    val travel = length / 2f - bubbleLength / 2f - height * 0.22f
    val tick = 1.5.dp.toPx()
    for (degrees in floatArrayOf(2f, 5f, 10f)) {
        val dx = bubbleLength / 2f + travel * response(degrees, TUBE_SCALE_DEG)
        for (x in floatArrayOf(center.x - dx, center.x + dx)) {
            drawLine(Navy, Offset(x, top + rim), Offset(x, top + height * 0.25f), strokeWidth = tick)
            drawLine(Navy, Offset(x, bottom - height * 0.25f), Offset(x, bottom - rim), strokeWidth = tick)
        }
    }

    val bubbleX = center.x + sign(angle) * travel * response(abs(angle), TUBE_SCALE_DEG)
    drawRoundRect(
        bubbleColor,
        topLeft = Offset(bubbleX - bubbleLength / 2f, center.y - bubbleHeight / 2f),
        size = Size(bubbleLength, bubbleHeight),
        cornerRadius = CornerRadius(bubbleHeight / 2f)
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.35f),
        topLeft = Offset(bubbleX - bubbleLength * 0.3f, center.y - bubbleHeight * 0.32f),
        size = Size(bubbleLength * 0.6f, bubbleHeight * 0.18f),
        cornerRadius = CornerRadius(bubbleHeight * 0.09f)
    )

    val guideDx = bubbleLength / 2f + travel * response(LEVEL_TOLERANCE_DEG, TUBE_SCALE_DEG)
    for (x in floatArrayOf(center.x - guideDx, center.x + guideDx)) {
        drawLine(
            guideColor,
            Offset(x, top + rim * 2f),
            Offset(x, bottom - rim * 2f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/** Saturating 0..1 response of the bubble to a tilt of [degrees]. */
private fun response(degrees: Float, scale: Float): Float = 1f - exp(-degrees / scale)

private fun Float.roundToTenth(): Float = (this * 10f).roundToInt() / 10f

private fun LevelReading.isLevel(): Boolean = when (this) {
    is LevelReading.Flat -> abs(x.roundToTenth()) <= LEVEL_TOLERANCE_DEG && abs(y.roundToTenth()) <= LEVEL_TOLERANCE_DEG
    is LevelReading.Upright -> abs(angle.roundToTenth()) <= LEVEL_TOLERANCE_DEG
}

/** Rounds first so that -0,04 shows as "0,0°" rather than "-0,0°". */
private fun formatDegrees(degrees: Float): String =
    String.format(Locale.FRANCE, "%.1f°", degrees.roundToTenth())
