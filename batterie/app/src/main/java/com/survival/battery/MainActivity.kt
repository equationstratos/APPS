package com.survival.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private const val POLL_INTERVAL_MS = 2_000L

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

/** Values carried by the ACTION_BATTERY_CHANGED sticky broadcast. */
private data class BatterySnapshot(
    val levelPercent: Int?,
    val status: Int,
    val plugged: Int,
    val health: Int,
    val temperatureC: Float?,
    val voltageV: Float?,
    val technology: String?,
    val present: Boolean
)

/** Values only exposed through BatteryManager properties, refreshed by polling. */
private data class LiveProperties(
    val currentMicroAmps: Int? = null,
    val chargeMilliAmpHours: Int? = null,
    val chargeTimeRemainingMs: Long? = null
)

private fun Intent.toBatterySnapshot(): BatterySnapshot {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val rawTemperature = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    val rawVoltage = getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
    return BatterySnapshot(
        levelPercent = if (level >= 0 && scale > 0) {
            (level * 100f / scale).roundToInt().coerceIn(0, 100)
        } else {
            null
        },
        status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
        plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        health = getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
        temperatureC = if (rawTemperature == Int.MIN_VALUE) null else rawTemperature / 10f,
        // A few older devices report volts instead of millivolts.
        voltageV = when {
            rawVoltage > 100 -> rawVoltage / 1000f
            rawVoltage > 0 -> rawVoltage.toFloat()
            else -> null
        },
        technology = getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
        present = getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
    )
}

private fun BatteryManager.readLiveProperties(): LiveProperties {
    val current = getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    val counter = getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    val timeRemaining = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        computeChargeTimeRemaining()
    } else {
        -1L
    }
    return LiveProperties(
        currentMicroAmps = current.takeIf { it != 0 && it != Int.MIN_VALUE },
        chargeMilliAmpHours = if (counter > 0) counter / 1000 else null,
        chargeTimeRemainingMs = timeRemaining.takeIf { it >= 0L }
    )
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val batteryManager: BatteryManager? = remember(context) {
        context.getSystemService(BatteryManager::class.java)
    }

    var snapshot by remember { mutableStateOf<BatterySnapshot?>(null) }
    var live by remember { mutableStateOf(LiveProperties()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                snapshot = intent.toBatterySnapshot()
            }
        }
        // The broadcast is sticky: registration returns the latest value immediately.
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { snapshot = it.toBatterySnapshot() }
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(batteryManager, lifecycleOwner) {
        val manager = batteryManager ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                live = withContext(Dispatchers.IO) { manager.readLiveProperties() }
                delay(POLL_INTERVAL_MS)
            }
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
        Text("Batterie", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("État de la batterie en temps réel", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        val current = snapshot
        when {
            current == null -> MessagePanel("Lecture de la batterie…")
            !current.present -> MessagePanel("Aucune batterie détectée sur cet appareil.")
            else -> BatteryContent(current, live)
        }
    }
}

@Composable
private fun BatteryContent(snapshot: BatterySnapshot, live: LiveProperties) {
    val charging = snapshot.status == BatteryManager.BATTERY_STATUS_CHARGING
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LevelPanel(snapshot.levelPercent, statusLabel(snapshot.status), charging)
        val level = snapshot.levelPercent
        if (level != null && level < 20 && snapshot.plugged == 0) {
            MessagePanel(
                "Batterie faible : branchez l'appareil ou activez l'économiseur d'énergie.",
                color = Danger
            )
        }
        DetailsPanel(snapshot, live)
    }
}

private fun levelColor(level: Int): Color = when {
    level > 50 -> Success
    level >= 20 -> Warning
    else -> Danger
}

@Composable
private fun LevelPanel(levelPercent: Int?, status: String, charging: Boolean) {
    val fraction by animateFloatAsState(
        targetValue = (levelPercent ?: 0) / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "niveau"
    )
    val color = levelPercent?.let(::levelColor) ?: TextSecondary
    val levelText = levelPercent?.let { "$it %" } ?: "—"

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryGauge(
                fraction = fraction,
                fillColor = color,
                charging = charging,
                description = "Niveau de batterie : $levelText, $status",
                modifier = Modifier.size(width = 92.dp, height = 156.dp)
            )
            Spacer(Modifier.width(24.dp))
            Column {
                Text(
                    levelText,
                    color = color,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    style = Tabular,
                    maxLines = 1
                )
                Text(status, color = TextSecondary, fontSize = 16.sp)
            }
        }
    }
}

// Lightning bolt outline, normalised to a 1:2 (width:height) box.
private val BoltPoints = listOf(
    Offset(0.75f, 0f),
    Offset(0f, 0.5625f),
    Offset(0.46875f, 0.5625f),
    Offset(0.25f, 1f),
    Offset(1f, 0.40625f),
    Offset(0.5625f, 0.40625f)
)

@Composable
private fun BatteryGauge(
    fraction: Float,
    fillColor: Color,
    charging: Boolean,
    description: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.semantics { contentDescription = description }) {
        val outline = 4.dp.toPx()
        val inset = outline + 4.dp.toPx()
        val bodyRadius = 14.dp.toPx()
        val capHeight = size.height * 0.08f
        val capWidth = size.width * 0.42f
        val bodyTop = capHeight
        val bodyHeight = size.height - capHeight

        drawRoundRect(
            color = TextSecondary,
            topLeft = Offset((size.width - capWidth) / 2f, 0f),
            size = Size(capWidth, capHeight + bodyRadius),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
        drawRoundRect(
            color = Navy,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, bodyHeight),
            cornerRadius = CornerRadius(bodyRadius)
        )
        drawRoundRect(
            color = TextSecondary,
            topLeft = Offset(outline / 2f, bodyTop + outline / 2f),
            size = Size(size.width - outline, bodyHeight - outline),
            cornerRadius = CornerRadius(bodyRadius - outline / 2f),
            style = Stroke(width = outline)
        )

        val innerLeft = inset
        val innerTop = bodyTop + inset
        val innerWidth = size.width - 2f * inset
        val innerHeight = bodyHeight - 2f * inset
        val fillHeight = innerHeight * fraction.coerceIn(0f, 1f)
        if (fillHeight > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(innerLeft, innerTop + innerHeight - fillHeight),
                size = Size(innerWidth, fillHeight),
                cornerRadius = CornerRadius((bodyRadius - inset).coerceAtMost(fillHeight / 2f))
            )
        }

        if (charging) {
            val boltHeight = innerHeight * 0.5f
            val boltWidth = boltHeight * 0.5f
            val left = innerLeft + (innerWidth - boltWidth) / 2f
            val top = innerTop + (innerHeight - boltHeight) / 2f
            val bolt = Path().apply {
                BoltPoints.forEachIndexed { index, point ->
                    val x = left + point.x * boltWidth
                    val y = top + point.y * boltHeight
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(bolt, Color.White)
            drawPath(bolt, Bg, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

private data class Detail(val label: String, val value: String, val color: Color = Color.White)

@Composable
private fun DetailsPanel(snapshot: BatterySnapshot, live: LiveProperties) {
    val details = buildList {
        add(Detail("État", statusLabel(snapshot.status)))
        add(Detail("Source", pluggedLabel(snapshot.plugged)))
        add(Detail("Santé", healthLabel(snapshot.health), healthColor(snapshot.health)))
        snapshot.temperatureC?.let {
            add(Detail("Température", String.format(Locale.FRANCE, "%.1f °C", it), temperatureColor(it)))
        }
        snapshot.voltageV?.let {
            add(Detail("Tension", String.format(Locale.FRANCE, "%.2f V", it)))
        }
        add(Detail("Technologie", snapshot.technology ?: "Inconnue"))
        live.currentMicroAmps?.let { add(Detail("Courant", formatCurrent(it, snapshot.status, snapshot.plugged))) }
        live.chargeMilliAmpHours?.let {
            add(Detail("Charge restante", String.format(Locale.FRANCE, "%,d mAh", it)))
        }
        live.chargeTimeRemainingMs?.let { add(Detail("Temps de charge restant", formatDuration(it))) }
    }

    Panel {
        Text("Détails", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        details.forEachIndexed { index, detail ->
            if (index > 0) HorizontalDivider(color = Navy)
            DetailRow(detail)
        }
    }
}

@Composable
private fun DetailRow(detail: Detail) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(detail.label, color = TextSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            detail.value,
            color = detail.color,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            style = Tabular
        )
    }
}

@Composable
private fun MessagePanel(text: String, color: Color = Color.White) {
    Panel { Text(text, color = color, fontSize = 15.sp) }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

private fun statusLabel(status: Int): String = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "En charge"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Décharge"
    BatteryManager.BATTERY_STATUS_FULL -> "Pleine"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Non en charge"
    else -> "Inconnu"
}

private fun pluggedLabel(plugged: Int): String = when {
    plugged == 0 -> "Sur batterie"
    (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "Secteur"
    (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB"
    (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "Sans fil"
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        (plugged and BatteryManager.BATTERY_PLUGGED_DOCK) != 0 -> "Dock"
    else -> "Inconnue"
}

private fun healthLabel(health: Int): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Bonne"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surchauffe"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Hors service"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Surtension"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Défaillance"
    BatteryManager.BATTERY_HEALTH_COLD -> "Trop froide"
    else -> "Inconnue"
}

private fun healthColor(health: Int): Color = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> Success
    BatteryManager.BATTERY_HEALTH_OVERHEAT,
    BatteryManager.BATTERY_HEALTH_DEAD,
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE,
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE,
    BatteryManager.BATTERY_HEALTH_COLD -> Danger
    else -> Color.White
}

private fun temperatureColor(celsius: Float): Color = when {
    celsius >= 45f -> Danger
    celsius >= 40f || celsius < 0f -> Warning
    else -> Color.White
}

/**
 * The CURRENT_NOW sign convention (positive = into the battery) is not honoured by every
 * manufacturer, so the direction comes from the charging state whenever it is unambiguous.
 */
private fun formatCurrent(microAmps: Int, status: Int, plugged: Int): String {
    val milliAmps = abs(microAmps.toLong()) / 1000.0
    val incoming = when {
        plugged == 0 || status == BatteryManager.BATTERY_STATUS_DISCHARGING -> false
        status == BatteryManager.BATTERY_STATUS_CHARGING -> true
        else -> microAmps > 0
    }
    val direction = if (incoming) "entrant" else "sortant"
    return String.format(Locale.FRANCE, "%,.0f mA (%s)", milliAmps, direction)
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = (ms + 59_999L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        totalMinutes < 1L -> "moins d'une minute"
        hours > 0L -> String.format(Locale.FRANCE, "%d h %02d min", hours, minutes)
        else -> String.format(Locale.FRANCE, "%d min", minutes)
    }
}
