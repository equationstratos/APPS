package com.survival.lux

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

private const val TICK_MS = 100L
private const val GAUGE_MAX_LUX = 100_000.0
private const val NBSP = ' '

private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")
private val ButtonShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(16.dp)

private class GaugeMark(val lux: Float, val label: String)

private val GaugeMarks = listOf(
    GaugeMark(0f, "0"),
    GaugeMark(10f, "10"),
    GaugeMark(100f, "100"),
    GaugeMark(1_000f, "1k"),
    GaugeMark(10_000f, "10k"),
    GaugeMark(100_000f, "100k")
)

private class Reference(val situation: String, val range: String)

private val References = listOf(
    Reference("Nuit sans lune", "< 0,01"),
    Reference("Pleine lune", "0,1 – 0,3"),
    Reference("Crépuscule", "1 – 10"),
    Reference("Éclairage public", "10 – 20"),
    Reference("Salon, lumière tamisée", "50 – 150"),
    Reference("Cuisine, salle de classe", "300 – 500"),
    Reference("Bureau, lecture", "500 – 1${NBSP}000"),
    Reference("Ciel couvert", "1${NBSP}000 – 10${NBSP}000"),
    Reference("Plein jour, à l'ombre", "10${NBSP}000 – 25${NBSP}000"),
    Reference("Soleil direct", "30${NBSP}000 – 100${NBSP}000")
)

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val sensorManager: SensorManager? = remember(context) {
        context.getSystemService(SensorManager::class.java)
    }
    val sensor: Sensor? = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    val state = rememberSaveable(saver = LightMeterState.Saver) { LightMeterState() }

    if (sensorManager != null && sensor != null) {
        LightSensorEffect(sensorManager, sensor, state)
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
        if (sensor == null) {
            MissingSensorCard()
        } else {
            ReadoutCard(state)
            Controls(state)
            StatsCard(state)
        }
        SensorNote(sensor)
        ReferenceCard()
    }
}

/** Sensor registration and the smoothing ticker run only while the screen is resumed. */
@Composable
private fun LightSensorEffect(sensorManager: SensorManager, sensor: Sensor, state: LightMeterState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(sensorManager, sensor, lifecycleOwner, state) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val lux = event?.values?.firstOrNull() ?: return
                    state.onSample(lux)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    state.onAccuracyChanged(accuracy)
                }
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            try {
                while (true) {
                    delay(TICK_MS)
                    state.tick(SystemClock.elapsedRealtime())
                }
            } finally {
                sensorManager.unregisterListener(listener)
                state.onPause()
            }
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text("Luxmètre", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Mesure de l'éclairement ambiant", color = TextSecondary, fontSize = 14.sp)
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
private fun MissingSensorCard() {
    SurvieCard {
        Text(
            "Capteur de luminosité non disponible sur cet appareil.",
            color = Danger,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Le tableau de référence ci-dessous reste consultable pour estimer l'éclairage.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ReadoutCard(state: LightMeterState) {
    val current = state.current
    SurvieCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Éclairement actuel",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            if (state.held) {
                Text(
                    "FIGÉ",
                    color = Bg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Warning, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatLux(current),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                style = TabularDigits,
                maxLines = 1,
                modifier = Modifier
                    .alignByBaseline()
                    .weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "lx",
                color = TextSecondary,
                fontSize = 22.sp,
                modifier = Modifier.alignByBaseline()
            )
        }
        Text(
            if (current.isNaN()) "En attente du capteur…" else describe(current),
            color = Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (state.unreliable) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Précision du capteur faible : la mesure peut être approximative.",
                color = Warning,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        LuxGauge(current = current, minLux = state.min, maxLux = state.max)
    }
}

@Composable
private fun LuxGauge(current: Float, minLux: Float, maxLux: Float) {
    val fraction by animateFloatAsState(
        targetValue = gaugeFraction(current),
        animationSpec = tween(durationMillis = TICK_MS.toInt(), easing = LinearEasing),
        label = "jauge"
    )
    val description = "Jauge logarithmique de 0 à 100${NBSP}000 lux"
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics { contentDescription = description }
    ) {
        val barHeight = size.height
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        drawRoundRect(color = Navy, cornerRadius = radius)

        if (!minLux.isNaN() && !maxLux.isNaN()) {
            val bandWidth = ((gaugeFraction(maxLux) - gaugeFraction(minLux)) * size.width)
                .coerceAtLeast(barHeight)
            val bandStart = (gaugeFraction(minLux) * size.width)
                .coerceAtMost(size.width - bandWidth)
            drawRoundRect(
                color = Accent.copy(alpha = 0.3f),
                topLeft = Offset(bandStart, 0f),
                size = Size(bandWidth, barHeight),
                cornerRadius = radius
            )
        }

        if (fraction > 0f) {
            drawRoundRect(
                color = Accent,
                size = Size((fraction * size.width).coerceAtLeast(barHeight), barHeight),
                cornerRadius = radius
            )
        }

        val tickWidth = 1.dp.toPx()
        GaugeMarks.drop(1).dropLast(1).forEach { mark ->
            val x = gaugeFraction(mark.lux) * size.width
            drawLine(
                color = Bg.copy(alpha = 0.6f),
                start = Offset(x, barHeight * 0.25f),
                end = Offset(x, barHeight * 0.75f),
                strokeWidth = tickWidth
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    GaugeLabels()
}

/** Places each label centred under its tick, clamped so the end labels stay inside the card. */
@Composable
private fun GaugeLabels() {
    Layout(
        content = {
            GaugeMarks.forEach { mark ->
                Text(mark.label, color = TextSecondary, fontSize = 12.sp, style = TabularDigits)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val center = (gaugeFraction(GaugeMarks[index].lux) * width).roundToInt()
                val x = (center - placeable.width / 2)
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
private fun Controls(state: LightMeterState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = state::toggleHold,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = ButtonShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
        ) {
            Text(
                if (state.held) "Reprendre" else "Figer",
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = state::reset,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = ButtonShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
        ) {
            Text("Réinitialiser", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatsCard(state: LightMeterState) {
    SurvieCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Depuis le début",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatDuration(state.measuredMs),
                color = TextSecondary,
                fontSize = 14.sp,
                style = TabularDigits
            )
        }
        Spacer(Modifier.height(8.dp))
        StatRow("Minimum", state.min)
        StatRow("Moyenne", state.average)
        StatRow("Maximum", state.max)
    }
}

@Composable
private fun StatRow(label: String, lux: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            "${formatLux(lux)}${NBSP}lx",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = TabularDigits
        )
    }
}

@Composable
private fun SensorNote(sensor: Sensor?) {
    val range = sensor?.maximumRange?.takeIf { it.isFinite() && it > 0f }
    SurvieCard {
        Text(
            "Le capteur se trouve en général près de la caméra frontale.",
            color = Color.White,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Orientez l'écran vers la source de lumière sans masquer le haut du téléphone.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        if (range != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Plage du capteur : jusqu'à ${formatLux(range)}${NBSP}lx",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ReferenceCard() {
    SurvieCard {
        Text(
            "Valeurs de référence (lx)",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        References.forEach { reference ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    reference.situation,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    reference.range,
                    color = Color.White,
                    fontSize = 14.sp,
                    style = TabularDigits,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

private fun describe(lux: Float): String = when {
    lux < 1f -> "Nuit noire"
    lux < 50f -> "Pièce sombre / crépuscule"
    lux < 500f -> "Pièce éclairée"
    lux < 1_000f -> "Bureau bien éclairé"
    lux < 10_000f -> "Ciel couvert"
    lux < 30_000f -> "Plein jour"
    else -> "Soleil direct"
}

/** Position on the logarithmic gauge, 0 lx at 0 and 100 000 lx at 1. */
private fun gaugeFraction(lux: Float): Float {
    if (!lux.isFinite() || lux <= 0f) return 0f
    return (log10(1.0 + lux) / log10(1.0 + GAUGE_MAX_LUX)).toFloat().coerceIn(0f, 1f)
}

private fun formatLux(lux: Float): String {
    if (!lux.isFinite()) return "—"
    val decimals = if (lux < 10f) 1 else 0
    val format = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }
    return format.format(lux.toDouble())
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> String.format(Locale.FRANCE, "%d h %02d min", hours, minutes)
        minutes > 0L -> String.format(Locale.FRANCE, "%d min %02d s", minutes, seconds)
        else -> String.format(Locale.FRANCE, "%d s", seconds)
    }
}
