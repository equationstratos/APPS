package com.survival.accel

import android.hardware.Sensor
import android.hardware.SensorManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Tnum = TextStyle(fontFeatureSettings = "tnum")
private val AxisLabels = listOf("X", "Y", "Z")
private val AxisColors = listOf(Accent, Success, Warning)
private val PanelShape = RoundedCornerShape(16.dp)
private val ButtonShape = RoundedCornerShape(12.dp)

private const val PLACEHOLDER = "—"
private const val BAR_RANGE_WITH_GRAVITY = 20f
private const val BAR_RANGE_WITHOUT_GRAVITY = 10f

/** Consecutive graph samples further apart than this (pause, freeze) are not joined. */
private const val GRAPH_GAP_NS = 200_000_000L

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val sensorManager: SensorManager? = remember(context) {
        context.getSystemService(SensorManager::class.java)
    }
    val accelerometer: Sensor? = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    val linearAcceleration: Sensor? = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }

    var withGravity by rememberSaveable { mutableStateOf(true) }
    var frozen by rememberSaveable { mutableStateOf(false) }
    val state = rememberSaveable(saver = AccelState.Saver) { AccelState() }
    var unreliable by remember { mutableStateOf(false) }
    var registrationFailed by remember { mutableStateOf(false) }

    val source: Sensor? = if (withGravity) accelerometer else linearAcceleration ?: accelerometer
    val highPass = !withGravity && linearAcceleration == null

    val view = LocalView.current
    val measuring = source != null && !frozen
    DisposableEffect(view, measuring) {
        view.keepScreenOn = measuring
        onDispose { view.keepScreenOn = false }
    }

    // The previous listener stays registered until the next recomposition: events it delivers after a
    // mode switch or "Figer" must not reach the freshly cleared or frozen state.
    val subscribedWithGravity = withGravity
    if (sensorManager != null && source != null && !frozen) {
        AccelerationSubscription(
            sensorManager = sensorManager,
            sensor = source,
            highPass = highPass,
            onSample = { t, ax, ay, az ->
                if (!frozen && withGravity == subscribedWithGravity) state.onSample(t, ax, ay, az)
            },
            onAccuracyChanged = { unreliable = it == SensorManager.SENSOR_STATUS_UNRELIABLE },
            onRegistered = { registrationFailed = !it }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Header()
        if (source == null) {
            MissingSensorPanel()
        } else {
            ModeSelector(
                withGravity = withGravity,
                onSelect = { selected ->
                    if (selected != withGravity) {
                        withGravity = selected
                        frozen = false
                        unreliable = false
                        registrationFailed = false
                        state.clear()
                    }
                }
            )
            StatusMessages(
                registrationFailed = registrationFailed,
                highPass = highPass,
                unreliable = unreliable,
                frozen = frozen
            )
            Spacer(Modifier.height(12.dp))
            AxesPanel(
                state = state,
                barRange = if (withGravity) BAR_RANGE_WITH_GRAVITY else BAR_RANGE_WITHOUT_GRAVITY
            )
            Spacer(Modifier.height(12.dp))
            MagnitudePanel(state = state, onResetPeak = state::resetPeak)
            Spacer(Modifier.height(12.dp))
            GraphPanel(state = state)
            Spacer(Modifier.height(16.dp))
            FreezeButton(
                frozen = frozen,
                onToggle = {
                    if (!frozen) state.flush()
                    frozen = !frozen
                }
            )
        }
    }
}

@Composable
private fun Header() {
    Text("Accéléromètre", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text("Accélération sur les trois axes du téléphone", color = TextSecondary, fontSize = 14.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, PanelShape)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun MissingSensorPanel() {
    Panel {
        Text(
            "Accéléromètre non disponible sur cet appareil.",
            color = Danger,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Cet outil a besoin du capteur d'accélération du téléphone pour fonctionner.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ModeSelector(withGravity: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeButton("Avec gravité", isSelected = withGravity, modifier = Modifier.weight(1f)) { onSelect(true) }
        ModeButton("Sans gravité", isSelected = !withGravity, modifier = Modifier.weight(1f)) { onSelect(false) }
    }
}

@Composable
private fun ModeButton(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { selected = isSelected },
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Accent else Navy,
            contentColor = if (isSelected) Bg else Color.White
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusMessages(registrationFailed: Boolean, highPass: Boolean, unreliable: Boolean, frozen: Boolean) {
    if (registrationFailed) {
        StatusLine("Impossible de lire l'accéléromètre : capteur occupé ou indisponible.", Danger)
    }
    if (highPass) {
        StatusLine(
            "Capteur d'accélération linéaire absent : la gravité est retirée par filtrage logiciel.",
            TextSecondary
        )
    }
    if (unreliable) {
        StatusLine("Précision du capteur signalée comme faible : mesures à prendre avec prudence.", Warning)
    }
    if (frozen) {
        StatusLine("Mesures figées.", Warning)
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = color, fontSize = 13.sp)
}

@Composable
private fun AxesPanel(state: AccelState, barRange: Float) {
    val values = floatArrayOf(state.x, state.y, state.z)
    Panel {
        for (axis in 0 until AXIS_COUNT) {
            if (axis > 0) Spacer(Modifier.height(12.dp))
            AxisRow(
                label = AxisLabels[axis],
                value = values[axis],
                hasData = state.hasData,
                color = AxisColors[axis],
                barRange = barRange
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Barres : ±${barRange.toInt()} m/s²", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun AxisRow(label: String, value: Float, hasData: Boolean, color: Color, barRange: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (hasData) formatSigned(value) else PLACEHOLDER,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            style = Tnum
        )
        Text(" m/s²", color = TextSecondary, fontSize = 14.sp)
    }
    Spacer(Modifier.height(6.dp))
    CenteredBar(
        value = if (hasData) value else 0f,
        range = barRange,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
    )
}

/** Bar growing left or right from the centre line, proportional to [value] / [range]. */
@Composable
private fun CenteredBar(value: Float, range: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = Navy, cornerRadius = radius)
        val centerX = size.width / 2f
        val fraction = if (range > 0f) (value / range).coerceIn(-1f, 1f) else 0f
        val end = centerX + fraction * centerX
        val left = min(centerX, end)
        val right = max(centerX, end)
        if (right - left >= 1f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(right - left, size.height),
                cornerRadius = radius
            )
        }
        drawLine(
            color = Color.White,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun MagnitudePanel(state: AccelState, onResetPeak: () -> Unit) {
    val magnitude = state.magnitude
    Panel {
        Text("Accélération totale", color = TextSecondary, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (state.hasData) formatValue(magnitude) else PLACEHOLDER,
                color = Accent,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                style = Tnum,
                modifier = Modifier.alignByBaseline()
            )
            Text(" m/s²", color = Accent, fontSize = 16.sp, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text(
                if (state.hasData) "${formatValue(magnitude / SensorManager.GRAVITY_EARTH)} g" else PLACEHOLDER,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                style = Tnum,
                modifier = Modifier.alignByBaseline()
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pic", color = TextSecondary, fontSize = 14.sp)
                Text(
                    if (state.hasData) "${formatValue(state.peak)} m/s²" else PLACEHOLDER,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = Tnum
                )
                if (state.hasData) {
                    Text(
                        "${formatValue(state.peak / SensorManager.GRAVITY_EARTH)} g",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        style = Tnum
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onResetPeak,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
            ) {
                Text("Réinitialiser")
            }
        }
    }
}

@Composable
private fun GraphPanel(state: AccelState) {
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dernières 5 secondes",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            for (axis in 0 until AXIS_COUNT) {
                Spacer(Modifier.width(10.dp))
                LegendItem(AxisLabels[axis], AxisColors[axis])
            }
        }
        Spacer(Modifier.height(10.dp))
        AccelGraph(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(ButtonShape)
                .background(Bg)
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("−5 s", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("Échelle ±${state.graphRange.toInt()} m/s²", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("maintenant", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Scrolling 3-line plot; reads state only in the draw phase so updates skip recomposition. */
@Composable
private fun AccelGraph(state: AccelState, modifier: Modifier) {
    val paths = remember { Array(AXIS_COUNT) { Path() } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val thin = 1.dp.toPx()
        for (second in 1 until 5) {
            val gx = w * second / 5f
            drawLine(Navy, Offset(gx, 0f), Offset(gx, h), thin)
        }
        drawLine(Navy, Offset(0f, h * 0.25f), Offset(w, h * 0.25f), thin)
        drawLine(Navy, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), thin)
        drawLine(TextSecondary.copy(alpha = 0.5f), Offset(0f, midY), Offset(w, midY), thin)

        val endNs = state.graphEndNs
        val range = state.graphRange
        val history = state.history
        if (!state.hasData || history.size < 2 || range <= 0f) return@Canvas

        paths.forEach { it.reset() }
        var previousNs = 0L
        var started = false
        for (k in 0 until history.size) {
            val t = history.time(k)
            val age = endNs - t
            if (age < 0L || age > GRAPH_WINDOW_NS) continue
            val px = w * (1f - age.toFloat() / GRAPH_WINDOW_NS.toFloat())
            val newSegment = !started || t - previousNs > GRAPH_GAP_NS
            for (axis in 0 until AXIS_COUNT) {
                val py = (midY - history.value(axis, k) / range * midY).coerceIn(0f, h)
                if (newSegment) paths[axis].moveTo(px, py) else paths[axis].lineTo(px, py)
            }
            started = true
            previousNs = t
        }
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (axis in 0 until AXIS_COUNT) {
            drawPath(paths[axis], color = AxisColors[axis], style = stroke)
        }
    }
}

@Composable
private fun FreezeButton(frozen: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
    ) {
        Text(if (frozen) "Reprendre" else "Figer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Avoids displaying "-0,00" for values that round to zero. */
private fun Float.forDisplay(): Float = if (abs(this) < 0.005f) 0f else this

private fun formatSigned(value: Float): String =
    String.format(Locale.FRANCE, "%+.2f", value.forDisplay())

private fun formatValue(value: Float): String =
    String.format(Locale.FRANCE, "%.2f", value.forDisplay())
