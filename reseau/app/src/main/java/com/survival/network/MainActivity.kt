package com.survival.network

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")
private val ButtonShape = RoundedCornerShape(12.dp)
private val HistoryDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM 'à' HH:mm", Locale.FRANCE)

/** The gauge spans 1 to 1000 Mbit/s on a logarithmic scale. */
private val GaugeScaleMax = ln(1001.0)
private const val GAUGE_START_ANGLE = 150f
private const val GAUGE_SWEEP = 240f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val speedTest = ViewModelProvider(this, SpeedTestViewModel.Factory(application))[SpeedTestViewModel::class.java]
        setContent { SurvieTheme { AppScreen(speedTest) } }
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
private fun AppScreen(speedTest: SpeedTestViewModel) {
    val connection = rememberConnectionInfo()
    val state = speedTest.state

    val view = LocalView.current
    DisposableEffect(state.isRunning) {
        view.keepScreenOn = state.isRunning
        onDispose { view.keepScreenOn = false }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Réseau", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Connexion et test de débit", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionCard(connection)
            SpeedTestCard(
                state = state,
                connection = connection,
                onStart = { speedTest.start(connection.type) },
                onCancel = speedTest::cancel
            )
            HistoryCard(history = speedTest.history, onClear = speedTest::clearHistory)
        }
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
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
private fun ConnectionCard(info: ConnectionInfo) {
    SurvieCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Connexion", Modifier.weight(1f))
            val pillColor = if (info.isConnected) Accent else Danger
            Text(
                info.type.label,
                color = pillColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(pillColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!info.isConnected) {
            Text(
                "Aucun réseau actif. Activez le Wi-Fi ou les données mobiles.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        } else {
            InfoRow(
                label = "Accès Internet",
                value = if (info.validated) "Validé" else "Non validé",
                valueColor = if (info.validated) Success else Warning
            )
            InfoRow(label = "Débit estimé ↓", value = formatKbps(info.downstreamKbps))
            InfoRow(label = "Débit estimé ↑", value = formatKbps(info.upstreamKbps))
            InfoRow(
                label = "Facturé à l'usage",
                value = if (info.metered) "Oui" else "Non",
                valueColor = if (info.metered) Warning else Color.White
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = TabularDigits)
    }
}

@Composable
private fun SpeedTestCard(
    state: SpeedTestState,
    connection: ConnectionInfo,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    SurvieCard {
        CardTitle("Test de débit")
        Spacer(Modifier.height(8.dp))
        SpeedGauge(state, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Latence", "ms", state, TestPhase.LATENCY, state.latencyMs, Modifier.weight(1f))
            MetricTile("Descendant", "Mbit/s", state, TestPhase.DOWNLOAD, state.downloadMbps, Modifier.weight(1f))
            MetricTile("Montant", "Mbit/s", state, TestPhase.UPLOAD, state.uploadMbps, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        if (state.isRunning) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
                shape = ButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Annuler", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onStart,
                enabled = connection.isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Bg,
                    disabledContainerColor = Navy,
                    disabledContentColor = TextSecondary
                ),
                shape = ButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Lancer le test", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        TestStatusMessage(state, connection)
        Spacer(Modifier.height(12.dp))
        Text(
            "Test via speed.cloudflare.com — aucune autre donnée envoyée",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TestStatusMessage(state: SpeedTestState, connection: ConnectionInfo) {
    val message = state.message
    val (text, color) = when {
        !connection.isConnected -> "Hors ligne : connectez-vous à un réseau pour lancer le test." to Warning
        message != null -> message to (if (state.isError) Danger else TextSecondary)
        !connection.validated -> "Accès Internet non validé (portail captif ?) : le test risque d'échouer." to Warning
        connection.metered && !state.isRunning ->
            "Réseau facturé à l'usage : un test complet peut consommer jusqu'à 50 Mo." to TextSecondary
        else -> return
    }
    Spacer(Modifier.height(12.dp))
    Text(text, color = color, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

private class GaugeReading(val value: String, val unit: String, val caption: String, val mbps: Double)

private fun gaugeReading(state: SpeedTestState): GaugeReading = when (state.phase) {
    TestPhase.LATENCY ->
        GaugeReading(formatMs(state.liveValue.takeIf { it > 0.0 }), "ms", "Mesure de la latence…", 0.0)
    TestPhase.DOWNLOAD ->
        GaugeReading(formatMbps(state.liveValue), "Mbit/s", "Débit descendant…", state.liveValue)
    TestPhase.UPLOAD ->
        GaugeReading(formatMbps(state.liveValue), "Mbit/s", "Débit montant…", state.liveValue)
    null -> {
        val download = state.downloadMbps
        if (download != null) {
            GaugeReading(formatMbps(download), "Mbit/s", "Débit descendant", download)
        } else {
            GaugeReading("—", "Mbit/s", "Prêt", 0.0)
        }
    }
}

private fun gaugeFraction(mbps: Double): Float =
    if (!mbps.isFinite() || mbps <= 0.0) 0f else (ln(1.0 + mbps) / GaugeScaleMax).toFloat().coerceIn(0f, 1f)

@Composable
private fun SpeedGauge(state: SpeedTestState, modifier: Modifier = Modifier) {
    val reading = gaugeReading(state)
    val fraction by animateFloatAsState(
        targetValue = gaugeFraction(reading.mbps),
        animationSpec = tween(durationMillis = 300),
        label = "gauge"
    )
    Box(
        modifier
            .size(200.dp)
            .clearAndSetSemantics { contentDescription = "${reading.caption} ${reading.value} ${reading.unit}" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            drawArc(
                color = Navy,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = GAUGE_SWEEP,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            if (fraction > 0f) {
                drawArc(
                    color = Accent,
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = GAUGE_SWEEP * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                reading.value,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                style = TabularDigits,
                maxLines = 1
            )
            Text(reading.unit, color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(reading.caption, color = Accent, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    unit: String,
    state: SpeedTestState,
    phase: TestPhase,
    result: Double?,
    modifier: Modifier = Modifier
) {
    val active = state.phase == phase
    val progress = when {
        active -> state.phaseProgress
        result != null -> 1f
        else -> 0f
    }
    val value = when {
        phase == TestPhase.LATENCY -> formatMs(if (active) state.liveValue.takeIf { it > 0.0 } else result)
        active -> formatMbps(state.liveValue)
        else -> formatMbps(result)
    }
    Column(
        modifier
            .background(Navy, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = if (active) Accent else Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = TabularDigits,
            maxLines = 1
        )
        Text(unit, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Bg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Accent)
            )
        }
    }
}

@Composable
private fun HistoryCard(history: List<SpeedResult>, onClear: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    SurvieCard {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardTitle("Derniers tests", Modifier.weight(1f))
            if (history.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Effacer", color = Accent, fontSize = 14.sp)
                }
            }
        }
        if (history.isEmpty()) {
            Text("Aucun test pour le moment.", color = TextSecondary, fontSize = 14.sp)
        } else {
            history.forEachIndexed { index, result ->
                if (index > 0) HorizontalDivider(color = Navy)
                HistoryRow(result)
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Les résultats enregistrés seront supprimés.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) {
                    Text("Effacer", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Annuler") }
            },
            containerColor = Card,
            tonalElevation = 0.dp
        )
    }
}

@Composable
private fun HistoryRow(result: SpeedResult) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatDate(result.timeMillis), color = Color.White, fontSize = 14.sp)
            Text(
                "${result.connection.label} · ${formatMs(result.latencyMs)} ms",
                color = TextSecondary,
                fontSize = 12.sp,
                style = TabularDigits
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "↓ ${formatMbps(result.downloadMbps)} Mbit/s",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                style = TabularDigits
            )
            Text(
                "↑ ${formatMbps(result.uploadMbps)} Mbit/s",
                color = TextSecondary,
                fontSize = 12.sp,
                style = TabularDigits
            )
        }
    }
}

private fun formatMbps(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0.0) return "—"
    val pattern = when {
        value < 10.0 -> "%.2f"
        value < 100.0 -> "%.1f"
        else -> "%.0f"
    }
    return String.format(Locale.FRANCE, pattern, value)
}

private fun formatMs(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0.0) return "—"
    return String.format(Locale.FRANCE, "%.0f", value)
}

private fun formatKbps(kbps: Int): String = when {
    kbps <= 0 -> "Inconnu"
    kbps < 1000 -> "$kbps kbit/s"
    else -> "${formatMbps(kbps / 1000.0)} Mbit/s"
}

private fun formatDate(timeMillis: Long): String =
    HistoryDateFormat.format(Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()))
