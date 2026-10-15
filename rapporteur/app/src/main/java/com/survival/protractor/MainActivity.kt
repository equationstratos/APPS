package com.survival.protractor

import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/** Approximate height of the readout card, the buttons and the gaps, so the dial takes what is left. */
private val ReservedHeight = 300.dp
private val MinDialSize = 180.dp
private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")

private val SampleSaver = Saver<Sample?, FloatArray>(
    save = { sample ->
        sample?.let {
            floatArrayOf(it.gravity.x, it.gravity.y, it.gravity.z, if (it.upright) 1f else 0f)
        }
    },
    restore = { a ->
        if (a.size < 4) null else Sample(Vec3(a[0], a[1], a[2]), upright = a[3] > 0.5f, stable = true)
    }
)

private val ReferenceSaver = Saver<Reference?, FloatArray>(
    save = { reference -> reference?.let { floatArrayOf(if (it.upright) 1f else 0f, it.value) } },
    restore = { a -> if (a.size < 2) null else Reference(upright = a[0] > 0.5f, value = a[1]) }
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
private fun AppScreen() {
    val context = LocalContext.current
    val tracker = remember { GravityTracker(context.getSystemService(SensorManager::class.java)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, tracker) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tracker.start()
                Lifecycle.Event.ON_PAUSE -> tracker.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }

    val view = LocalView.current
    val active = tracker.available
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Rapporteur", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Angles et inclinaisons", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        if (!active) {
            MissingSensorCard()
        } else {
            MeasureContent(tracker)
        }
    }
}

@Composable
private fun ColumnScope.MeasureContent(tracker: GravityTracker) {
    var held by rememberSaveable(stateSaver = SampleSaver) { mutableStateOf<Sample?>(null) }
    var reference by rememberSaveable(stateSaver = ReferenceSaver) { mutableStateOf<Reference?>(null) }
    val live = tracker.sample
    val measurement = (held ?: live)?.let { Measurement.of(it) }
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        val dialSize = min(maxWidth, maxHeight - ReservedHeight).coerceAtLeast(MinDialSize)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReadoutCard(
                measurement = measurement,
                reference = reference,
                frozen = held != null,
                stable = live?.stable == true,
                unreliable = tracker.unreliable
            )
            ProtractorDial(measurement, reference, Modifier.size(dialSize))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    label = if (held != null) "Reprendre" else "Figer",
                    primary = true,
                    enabled = held != null || live != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        held = if (held == null) tracker.sample else null
                    }
                )
                ActionButton(
                    label = "Zéro",
                    primary = false,
                    enabled = measurement != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        (held ?: tracker.sample)?.let { Measurement.of(it) }?.let {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            reference = Reference(it.upright, it.referenceValue)
                        }
                    }
                )
                ActionButton(
                    label = "Réinitialiser",
                    primary = false,
                    enabled = held != null || reference != null,
                    modifier = Modifier.weight(1.35f),
                    onClick = {
                        held = null
                        reference = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ReadoutCard(
    measurement: Measurement?,
    reference: Reference?,
    frozen: Boolean,
    stable: Boolean,
    unreliable: Boolean,
) {
    val delta = if (measurement != null && reference != null) measurement.deltaFrom(reference) else null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    measurement == null -> "En attente du capteur…"
                    measurement.upright -> "Téléphone debout"
                    else -> "Téléphone à plat"
                },
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            when {
                frozen -> StatusBadge("Figé", Warning)
                measurement == null -> Unit
                stable -> StatusBadge("Stable", Success)
                else -> StatusBadge("En mouvement", Warning)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                delta != null -> "Δ depuis le zéro"
                measurement == null || measurement.upright -> "Bord long vs verticale"
                else -> "Inclinaison du plan"
            },
            color = TextSecondary,
            fontSize = 13.sp
        )
        Text(
            text = when {
                measurement == null -> "—"
                delta != null -> formatAngle(delta, signed = true)
                else -> formatAngle(measurement.primary)
            },
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            style = TabularDigits
        )
        if (measurement != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (delta != null) {
                    Stat(
                        label = if (measurement.upright) "vs verticale" else "Inclinaison",
                        value = formatAngle(measurement.primary),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (measurement.upright) {
                    Stat("vs horizontale", formatAngle(measurement.vsHorizontal), Modifier.weight(1f))
                    Stat("Sens", sideName(measurement.lineAngle), Modifier.weight(1f))
                } else {
                    Stat("Pente", formatPercent(measurement.inclination), Modifier.weight(1f))
                    Stat(
                        label = "Descend vers",
                        value = if (measurement.isLevel) "À plat" else headingName(measurement.slopeHeading),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (measurement != null && reference != null && delta == null) {
            Hint(
                text = "Zéro pris téléphone ${if (reference.upright) "debout" else "à plat"} : " +
                    "replacez-le ainsi pour voir l'écart.",
                color = TextSecondary
            )
        }
        if (unreliable) {
            Hint("Capteur peu fiable : laissez le téléphone immobile un instant.", Warning)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, style = TabularDigits)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun Hint(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ActionButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Accent else Navy,
            contentColor = if (primary) Bg else Color.White,
            disabledContainerColor = Navy.copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.4f)
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MissingSensorCard() {
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
            "Le rapporteur a besoin du capteur de gravité ou de l'accéléromètre pour mesurer les angles.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

private fun formatAngle(degrees: Float, signed: Boolean = false): String {
    // Avoids "-0,0°" once rounded to one decimal.
    val value = if (abs(degrees) < 0.05f) 0f else degrees
    return String.format(Locale.FRANCE, if (signed) "%+.1f°" else "%.1f°", value)
}

private fun formatPercent(inclination: Float): String {
    if (inclination >= 89.9f) return "—"
    val percent = tan(inclination * (PI.toFloat() / 180f)) * 100f
    return String.format(Locale.FRANCE, "%.1f %%", percent)
}

private fun sideName(lineAngle: Float): String = when {
    abs(lineAngle) < 0.05f -> "Aplomb"
    lineAngle > 0f -> "← Gauche"
    else -> "Droite →"
}

private val Headings = arrayOf(
    "Haut", "Haut-droite", "Droite", "Bas-droite", "Bas", "Bas-gauche", "Gauche", "Haut-gauche"
)

private fun headingName(heading: Float): String = Headings[((heading + 22.5f) / 45f).toInt().mod(Headings.size)]
