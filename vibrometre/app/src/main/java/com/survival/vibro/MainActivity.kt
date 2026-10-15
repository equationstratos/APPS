package com.survival.vibro

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

private val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** ~30 Hz: smooth graph without flooding Compose with sensor-rate updates. */
private const val FRAME_INTERVAL_MS = 33L

private val ScaleSteps = floatArrayOf(0.05f, 0.1f, 0.2f, 0.5f, 1f, 2f, 5f, 10f, 20f, 50f, 100f, 200f)

private val ReadingSaver = Saver<Reading?, FloatArray>(
    save = { it?.toArray() },
    restore = { Reading.fromArray(it) }
)

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

private class VibrationSource(val sensor: Sensor, val removesGravity: Boolean)

private fun findSource(sensorManager: SensorManager): VibrationSource? {
    sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
        return VibrationSource(it, removesGravity = false)
    }
    return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
        VibrationSource(it, removesGravity = true)
    }
}

private enum class Intensity(val label: String, val color: Color) {
    Imperceptible("Imperceptible", Success),
    VeryLow("Très faible", Success),
    Low("Faible", Accent),
    Moderate("Modérée", Warning),
    Strong("Forte", Danger),
    VeryStrong("Très forte", Danger);

    companion object {
        fun of(value: Float): Intensity = when {
            value < 0.05f -> Imperceptible
            value < 0.2f -> VeryLow
            value < 0.5f -> Low
            value < 1.5f -> Moderate
            value < 4f -> Strong
            else -> VeryStrong
        }
    }
}

@Composable
private fun AppScreen() {
    val context = LocalContext.current
    val sensorManager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val source = remember(sensorManager) { sensorManager?.let { findSource(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Vibromètre", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Posez le téléphone à plat sur la surface à mesurer",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        if (sensorManager != null && source != null) {
            Vibrometer(sensorManager, source, Modifier.weight(1f))
        } else {
            Panel(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Accéléromètre non disponible sur cet appareil.",
                    color = Danger,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "La mesure des vibrations nécessite un accéléromètre.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun Vibrometer(sensorManager: SensorManager, source: VibrationSource, modifier: Modifier) {
    val analyzer = remember { VibrationAnalyzer() }
    var live by remember { mutableStateOf(Reading.Empty) }
    var frozen by rememberSaveable(stateSaver = ReadingSaver) { mutableStateOf<Reading?>(null) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var sensorStarted by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, sensorManager, source) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                if (values.size < 3) return
                val now = SystemClock.elapsedRealtime()
                if (source.removesGravity) {
                    analyzer.addRaw(values[0], values[1], values[2], event.timestamp, now)
                } else {
                    analyzer.addLinear(values[0], values[1], values[2], now)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    analyzer.restart(SystemClock.elapsedRealtime())
                    sensorStarted = sensorManager.registerListener(
                        listener,
                        source.sensor,
                        SensorManager.SENSOR_DELAY_GAME
                    )
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

    LaunchedEffect(lifecycleOwner, analyzer) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                live = analyzer.read(SystemClock.elapsedRealtime())
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val reading = frozen ?: live

    // The actions stay pinned below the scrollable content so "Figer" is always reachable.
    Column(modifier) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Readout(reading)
            Spacer(Modifier.height(12.dp))
            Seismograph(reading.trace, isFrozen = frozen != null)
            if (!sensorStarted) {
                Note(
                    "Impossible de démarrer l'accéléromètre. Fermez les applications qui l'utilisent puis revenez.",
                    Danger
                )
            }
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                Note("Capteur peu fiable pour le moment : valeurs indicatives.", Warning)
            }
            if (source.removesGravity) {
                Note(
                    "Capteur d'accélération linéaire absent : la gravité est retirée par calcul.",
                    TextSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ActionBar(
            isFrozen = frozen != null,
            onToggleFreeze = {
                frozen = if (frozen == null) analyzer.read(SystemClock.elapsedRealtime()) else null
            },
            onResetPeak = {
                analyzer.resetPeak()
                live = live.withPeak(0f)
                frozen = frozen?.withPeak(0f)
            }
        )
    }
}

@Composable
private fun ActionBar(isFrozen: Boolean, onToggleFreeze: () -> Unit, onResetPeak: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onToggleFreeze,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
        ) {
            Text(if (isFrozen) "Reprendre" else "Figer", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onResetPeak,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
        ) {
            Text("Effacer le pic")
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun Readout(reading: Reading) {
    Panel {
        Text("Accélération actuelle", color = TextSecondary, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatAccel(reading.current),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                style = Tnum,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "m/s²",
                color = TextSecondary,
                fontSize = 18.sp,
                modifier = Modifier.alignByBaseline()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Intensité (RMS sur 1 s)", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        IntensityBadge(Intensity.of(reading.rms))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Pic", reading.peak, Modifier.weight(1f))
            Stat("RMS sur 1 s", reading.rms, Modifier.weight(1f))
        }
    }
}

@Composable
private fun IntensityBadge(intensity: Intensity) {
    Row(
        Modifier
            .background(intensity.color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(intensity.color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            intensity.label,
            color = intensity.color,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Stat(label: String, value: Float, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(
            "${formatAccel(value)} m/s²",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            style = Tnum
        )
    }
}

@Composable
private fun Seismograph(trace: FloatArray, isFrozen: Boolean) {
    val scale = scaleFor(trace)
    val scaleFormat = remember {
        NumberFormat.getNumberInstance(Locale.FRANCE).apply { maximumFractionDigits = 2 }
    }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Sismogramme",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (isFrozen) {
                Text(
                    "Figé",
                    color = Bg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Warning, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "± ${scaleFormat.format(scale.toDouble())} m/s²",
                color = TextSecondary,
                fontSize = 13.sp,
                style = Tnum
            )
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Bg)
        ) {
            val centerY = size.height / 2f
            val amplitude = centerY - 4.dp.toPx()
            val gridStroke = 1.dp.toPx()
            for (fraction in floatArrayOf(-1f, -0.5f, 0.5f, 1f)) {
                val y = centerY + fraction * amplitude
                drawLine(Navy, Offset(0f, y), Offset(size.width, y), strokeWidth = gridStroke)
            }
            drawLine(
                TextSecondary.copy(alpha = 0.35f),
                Offset(0f, centerY),
                Offset(size.width, centerY),
                strokeWidth = gridStroke
            )
            if (trace.size < 2) return@Canvas

            val step = size.width / (trace.size - 1)
            val heights = FloatArray(trace.size) { (trace[it] / scale).coerceIn(0f, 1f) * amplitude }
            val top = Path()
            val bottom = Path()
            val envelope = Path()
            for (i in trace.indices) {
                val x = i * step
                if (i == 0) {
                    top.moveTo(x, centerY - heights[i])
                    bottom.moveTo(x, centerY + heights[i])
                    envelope.moveTo(x, centerY - heights[i])
                } else {
                    top.lineTo(x, centerY - heights[i])
                    bottom.lineTo(x, centerY + heights[i])
                    envelope.lineTo(x, centerY - heights[i])
                }
            }
            for (i in trace.indices.reversed()) {
                envelope.lineTo(i * step, centerY + heights[i])
            }
            envelope.close()

            val outline = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
            drawPath(envelope, Accent.copy(alpha = 0.3f))
            drawPath(top, Accent, style = outline)
            drawPath(bottom, Accent, style = outline)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("−10 s", color = TextSecondary, fontSize = 12.sp)
            Text("maintenant", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Spacer(Modifier.height(12.dp))
    Text(text, color = color, fontSize = 13.sp)
}

/** Smallest "round" full-scale value that fits the trace, so the axis does not jitter every frame. */
private fun scaleFor(trace: FloatArray): Float {
    val target = (trace.maxOrNull() ?: 0f) * 1.1f
    return ScaleSteps.firstOrNull { it >= target } ?: target
}

private fun formatAccel(value: Float): String = when {
    !value.isFinite() -> "—"
    value < 10f -> String.format(Locale.FRANCE, "%.3f", value)
    else -> String.format(Locale.FRANCE, "%.2f", value)
}
