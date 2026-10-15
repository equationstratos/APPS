package com.survival.thermo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

private const val PREFS_NAME = "survie"
private const val KEY_FAHRENHEIT = "thermo_fahrenheit"

/** Range drawn on the thermometer graphic, in °C. */
private const val SCALE_MIN_C = -20f
private const val SCALE_MAX_C = 50f

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

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

// ---------------------------------------------------------------------------
// Data sources
// ---------------------------------------------------------------------------

@Stable
private class AmbientState(val available: Boolean) {
    var celsius by mutableFloatStateOf(Float.NaN)
    var unreliable by mutableStateOf(false)
}

@Stable
private class BatteryState {
    var celsius by mutableFloatStateOf(Float.NaN)
    var plugged by mutableStateOf(false)
}

private data class ThermalInfo(val label: String, val detail: String, val color: Color)

@Composable
private fun rememberAmbientState(): AmbientState {
    val context = LocalContext.current
    val sensorManager: SensorManager? = remember(context) {
        context.getSystemService(SensorManager::class.java)
    }
    val sensor: Sensor? = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
    }
    val state = remember(sensor) { AmbientState(available = sensor != null) }
    if (sensorManager != null && sensor != null) {
        LifecycleResumeEffect(sensor) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val value = event?.values?.firstOrNull() ?: return
                    if (value.isFinite()) state.celsius = value
                }

                override fun onAccuracyChanged(changed: Sensor?, accuracy: Int) {
                    state.unreliable = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                        accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT
                }
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            onPauseOrDispose { sensorManager.unregisterListener(listener) }
        }
    }
    return state
}

/**
 * Kept registered while the screen is composed (not only while visible) so that min/max also
 * cover the "screen off for ~15 min" period the tip recommends; the broadcast is cheap.
 */
@Composable
private fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    val state = remember { BatteryState() }
    DisposableEffect(context) {
        fun read(intent: Intent) {
            val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (tenths != Int.MIN_VALUE) state.celsius = tenths / 10f
            state.plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent != null) read(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (sticky != null) read(sticky)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Null when the platform has no thermal status API (Android 9 and older). */
@Composable
private fun rememberThermalInfo(): ThermalInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) rememberThermalInfoQ() else null

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun rememberThermalInfoQ(): ThermalInfo? {
    val context = LocalContext.current
    val powerManager: PowerManager? = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    if (powerManager == null) return null
    var status by remember(powerManager) { mutableIntStateOf(powerManager.currentThermalStatus) }
    LifecycleStartEffect(powerManager) {
        status = powerManager.currentThermalStatus
        val listener = PowerManager.OnThermalStatusChangedListener { status = it }
        powerManager.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
        onStopOrDispose { powerManager.removeThermalStatusListener(listener) }
    }
    return thermalInfo(status)
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun thermalInfo(status: Int): ThermalInfo = when (status) {
    PowerManager.THERMAL_STATUS_NONE ->
        ThermalInfo("Normal", "Aucune limitation thermique.", Success)
    PowerManager.THERMAL_STATUS_LIGHT ->
        ThermalInfo("Léger", "Léger échauffement, sans effet notable.", Success)
    PowerManager.THERMAL_STATUS_MODERATE ->
        ThermalInfo("Modéré", "Le système commence à réduire les performances.", Warning)
    PowerManager.THERMAL_STATUS_SEVERE ->
        ThermalInfo("Sévère", "Performances fortement réduites : laissez refroidir le téléphone.", Warning)
    PowerManager.THERMAL_STATUS_CRITICAL ->
        ThermalInfo("Critique", "Le système coupe des fonctions pour refroidir.", Danger)
    PowerManager.THERMAL_STATUS_EMERGENCY ->
        ThermalInfo("Urgence", "Fonctions essentielles coupées : mettez le téléphone à l'ombre.", Danger)
    PowerManager.THERMAL_STATUS_SHUTDOWN ->
        ThermalInfo("Arrêt", "Arrêt imminent pour surchauffe.", Danger)
    else ->
        ThermalInfo("Inconnu", "État non communiqué par le système.", TextSecondary)
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

private fun toDisplayUnit(celsius: Float, fahrenheit: Boolean): Float =
    if (fahrenheit) celsius * 9f / 5f + 32f else celsius

private fun unitSymbol(fahrenheit: Boolean): String = if (fahrenheit) "°F" else "°C"

/** Number only, French decimal comma, "—" when no valid reading. */
private fun formatNumber(celsius: Float, fahrenheit: Boolean): String {
    if (!celsius.isFinite()) return "—"
    val value = toDisplayUnit(celsius, fahrenheit)
    // Avoid showing "-0,0".
    val safe = if (abs(value) < 0.05f) 0f else value
    return String.format(Locale.FRANCE, "%.1f", safe)
}

private fun formatTemp(celsius: Float, fahrenheit: Boolean): String =
    if (celsius.isFinite()) "${formatNumber(celsius, fahrenheit)} ${unitSymbol(fahrenheit)}" else "—"

private fun temperatureColor(celsius: Float): Color = when {
    !celsius.isFinite() -> TextSecondary
    celsius >= 40f -> Danger
    celsius >= 30f -> Warning
    else -> Accent
}

private data class Note(val text: String, val color: Color)

private fun batteryNote(celsius: Float, plugged: Boolean): Note? = when {
    !celsius.isFinite() -> null
    celsius >= 45f -> Note("Batterie très chaude : débranchez-la et laissez refroidir le téléphone.", Danger)
    celsius >= 40f -> Note("Batterie chaude.", Warning)
    plugged -> Note("En charge : la batterie chauffe, la mesure surestime l'air ambiant.", TextSecondary)
    else -> null
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

@Composable
private fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var fahrenheit by remember { mutableStateOf(prefs.getBoolean(KEY_FAHRENHEIT, false)) }

    val ambient = rememberAmbientState()
    val battery = rememberBatteryState()
    val thermal = rememberThermalInfo()

    val mainCelsius = if (ambient.available) ambient.celsius else battery.celsius
    var minCelsius by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var maxCelsius by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(mainCelsius) {
        if (mainCelsius.isFinite()) {
            if (minCelsius.isNaN() || mainCelsius < minCelsius) minCelsius = mainCelsius
            if (maxCelsius.isNaN() || mainCelsius > maxCelsius) maxCelsius = mainCelsius
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Thermomètre", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            if (ambient.available) {
                "Capteur de température ambiante"
            } else {
                "Capteur de température ambiante non disponible sur cet appareil"
            },
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))

        UnitToggle(
            fahrenheit = fahrenheit,
            onChange = {
                fahrenheit = it
                prefs.edit { putBoolean(KEY_FAHRENHEIT, it) }
            }
        )
        Spacer(Modifier.height(16.dp))

        MainReadingCard(
            ambientMode = ambient.available,
            celsius = mainCelsius,
            minCelsius = minCelsius,
            maxCelsius = maxCelsius,
            fahrenheit = fahrenheit,
            note = if (ambient.available) {
                when {
                    !ambient.celsius.isFinite() -> Note("Mesure en cours…", TextSecondary)
                    ambient.unreliable -> Note("Capteur en cours de calibration : mesure peu fiable.", Warning)
                    else -> null
                }
            } else {
                batteryNote(battery.celsius, battery.plugged)
            },
            onReset = {
                minCelsius = mainCelsius
                maxCelsius = mainCelsius
            }
        )

        if (ambient.available) {
            Spacer(Modifier.height(16.dp))
            BatteryCard(battery.celsius, battery.plugged, fahrenheit)
        }

        Spacer(Modifier.height(16.dp))
        ThermalCard(thermal)
    }
}

@Composable
private fun SurvieCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun UnitToggle(fahrenheit: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UnitButton("°C  Celsius", selected = !fahrenheit, modifier = Modifier.weight(1f)) { onChange(false) }
        UnitButton("°F  Fahrenheit", selected = fahrenheit, modifier = Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun UnitButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Navy,
            contentColor = if (selected) Bg else Color.White
        )
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun MainReadingCard(
    ambientMode: Boolean,
    celsius: Float,
    minCelsius: Float,
    maxCelsius: Float,
    fahrenheit: Boolean,
    note: Note?,
    onReset: () -> Unit
) {
    SurvieCard {
        Text(
            if (ambientMode) "Température ambiante" else "Température de la batterie",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        if (!ambientMode) NotAirLabel()

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                formatNumber(celsius, fahrenheit),
                color = temperatureColor(celsius),
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
            if (celsius.isFinite()) {
                Text(
                    " ${unitSymbol(fahrenheit)}",
                    color = TextSecondary,
                    fontSize = 26.sp,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }

        if (note != null) {
            Text(
                note.text,
                color = note.color,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ThermometerGauge(celsius = celsius, fahrenheit = fahrenheit)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Depuis l'ouverture", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                MinMaxLine("Min", minCelsius, fahrenheit)
                MinMaxLine("Max", maxCelsius, fahrenheit)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onReset,
                    enabled = celsius.isFinite(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
                ) {
                    Text("Réinitialiser", maxLines = 1)
                }
            }
        }

        if (!ambientMode) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Pour estimer l'air ambiant : laissez le téléphone inactif, écran éteint, " +
                    "hors de la poche ~15 min.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun NotAirLabel() {
    Text(
        "≠ température de l'air",
        color = Warning,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun MinMaxLine(label: String, celsius: Float, fahrenheit: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 15.sp, modifier = Modifier.width(44.dp))
        Text(
            formatTemp(celsius, fahrenheit),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular,
            maxLines = 1
        )
    }
}

@Composable
private fun ThermometerGauge(celsius: Float, fahrenheit: Boolean) {
    val target = if (celsius.isFinite()) {
        ((celsius - SCALE_MIN_C) / (SCALE_MAX_C - SCALE_MIN_C)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fraction by animateFloatAsState(target, animationSpec = tween(600), label = "thermoFill")
    val fillColor = temperatureColor(celsius)
    val hasReading = celsius.isFinite()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = TextSecondary, fontSize = 11.sp, fontFeatureSettings = "tnum")

    Canvas(Modifier.size(width = 104.dp, height = 200.dp)) {
        val tubeWidth = 18.dp.toPx()
        val border = 4.dp.toPx()
        val bulbRadius = 22.dp.toPx()
        val centerX = 24.dp.toPx()
        val bulbCenterY = size.height - bulbRadius
        val tubeTop = 4.dp.toPx()
        val scaleTop = tubeTop + tubeWidth / 2f
        val scaleBottom = bulbCenterY - bulbRadius - 6.dp.toPx()
        val innerWidth = tubeWidth - 2f * border

        drawRoundRect(
            color = Navy,
            topLeft = Offset(centerX - tubeWidth / 2f, tubeTop),
            size = Size(tubeWidth, bulbCenterY - tubeTop),
            cornerRadius = CornerRadius(tubeWidth / 2f)
        )
        drawCircle(color = Navy, radius = bulbRadius, center = Offset(centerX, bulbCenterY))

        if (hasReading) {
            val fillTop = scaleBottom - (scaleBottom - scaleTop) * fraction
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(centerX - innerWidth / 2f, fillTop),
                size = Size(innerWidth, bulbCenterY - fillTop),
                cornerRadius = CornerRadius(innerWidth / 2f)
            )
            drawCircle(color = fillColor, radius = bulbRadius - border, center = Offset(centerX, bulbCenterY))
        }

        val tickStart = centerX + tubeWidth / 2f + 6.dp.toPx()
        for (tick in SCALE_MIN_C.toInt()..SCALE_MAX_C.toInt() step 10) {
            val y = scaleBottom - (scaleBottom - scaleTop) *
                ((tick - SCALE_MIN_C) / (SCALE_MAX_C - SCALE_MIN_C))
            val major = tick % 20 == 0
            val tickEnd = tickStart + (if (major) 10.dp else 6.dp).toPx()
            drawLine(
                color = TextSecondary,
                start = Offset(tickStart, y),
                end = Offset(tickEnd, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            if (major) {
                val label = "${toDisplayUnit(tick.toFloat(), fahrenheit).roundToInt()}°"
                val layout = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(tickEnd + 4.dp.toPx(), y - layout.size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun BatteryCard(celsius: Float, plugged: Boolean, fahrenheit: Boolean) {
    SurvieCard {
        Text("Température de la batterie", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        NotAirLabel()
        Spacer(Modifier.height(4.dp))
        Text(
            formatTemp(celsius, fahrenheit),
            color = if (celsius >= 40f) temperatureColor(celsius) else Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular,
            maxLines = 1
        )
        val note = batteryNote(celsius, plugged)
        if (note != null) Text(note.text, color = note.color, fontSize = 14.sp)
    }
}

@Composable
private fun ThermalCard(info: ThermalInfo?) {
    SurvieCard {
        Text("État thermique du téléphone", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (info == null) {
            Text(
                "Non disponible sur cet appareil (Android 10 ou plus récent requis).",
                color = TextSecondary,
                fontSize = 14.sp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(info.color, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(info.label, color = info.color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(info.detail, color = TextSecondary, fontSize = 14.sp)
        }
    }
}
