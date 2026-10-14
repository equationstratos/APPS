package com.survival.compass

import android.content.Context
import android.content.SharedPreferences
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PREFS_NAME = "survie"
private const val KEY_TARGET = "compass_target_bearing"

/** Deviation (whole degrees) shown as "aligné". */
private const val ALIGNED_TOLERANCE = 2

/** Wider band to leave the aligned state, so sensor jitter does not re-trigger the haptic. */
private const val ALIGNED_EXIT_TOLERANCE = 4

private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val displayRotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val tracker = remember(context, displayRotation) {
        context.getSystemService(SensorManager::class.java)
            ?.let { HeadingTracker(it, displayRotation) }
            ?.takeIf { it.isAvailable }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Fills the screen so the rose can take the free space, and scrolls when large fonts
        // make the content taller than the screen.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
        ) {
            Text("Boussole", color = Accent, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            Text("Nord magnétique", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            if (tracker == null) UnavailableCard() else CompassPanel(tracker)
        }
    }
}

@Composable
private fun ColumnScope.CompassPanel(tracker: HeadingTracker) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var target by remember { mutableStateOf(prefs.readTarget()) }

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

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(tracker) {
        var inside = false
        snapshotFlow { deviationOf(tracker.heading, target) }.collect { deviation ->
            val limit = if (inside) ALIGNED_EXIT_TOLERANCE else ALIGNED_TOLERANCE
            val now = deviation != null && abs(deviation) <= limit
            if (now && !inside) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            inside = now
        }
    }

    val heading = tracker.heading
    val currentTarget = target
    val bearing = heading?.let(::roundedBearing)
    val deviation = deviationOf(heading, currentTarget)
    val aligned = deviation != null && abs(deviation) <= ALIGNED_TOLERANCE
    val needsCalibration = tracker.magneticAccuracy
        ?.let { it <= SensorManager.SENSOR_STATUS_ACCURACY_LOW } == true

    HeadingReadout(bearing)
    AnimatedVisibility(visible = needsCalibration) {
        Notice("Calibrez : décrivez un 8 avec le téléphone")
    }
    AnimatedVisibility(visible = tracker.isTilted && !needsCalibration) {
        Notice("Tenez le téléphone à plat")
    }
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        CompassRose(
            heading = heading ?: 0f,
            target = currentTarget,
            aligned = aligned,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .aspectRatio(1f)
        )
    }
    TargetCard(currentTarget, deviation)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                if (bearing != null) {
                    val newTarget = bearing.toFloat()
                    target = newTarget
                    prefs.writeTarget(newTarget)
                }
            },
            enabled = bearing != null,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = buttonColors(container = Accent, content = Bg)
        ) {
            Text("Fixer le cap", textAlign = TextAlign.Center)
        }
        Button(
            onClick = {
                target = null
                prefs.writeTarget(null)
            },
            enabled = currentTarget != null,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = buttonColors(container = Navy, content = Color.White)
        ) {
            Text("Effacer", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HeadingReadout(bearing: Int?) {
    // Digits end at the centre line and the cardinal starts after it, so neither jitters
    // when the other changes width.
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = if (bearing != null) "$bearing°" else "—",
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
            color = Color.White,
            fontSize = 56.sp,
            lineHeight = 64.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            style = TabularDigits
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = bearing?.let(::cardinalOf) ?: "",
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
            color = Accent,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TargetCard(target: Float?, deviation: Int?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        if (target == null) {
            Text("Aucun cap fixé", color = TextSecondary, fontSize = 14.sp)
            Text(
                "Visez votre destination avec le haut du téléphone, puis touchez « Fixer le cap ».",
                color = Color.White,
                fontSize = 16.sp
            )
        } else {
            val targetBearing = roundedBearing(target)
            Text(
                "Cap fixé : $targetBearing° ${cardinalOf(targetBearing)}",
                color = TextSecondary,
                fontSize = 14.sp,
                style = TabularDigits
            )
            val (text, color) = when {
                deviation == null -> "—" to TextSecondary
                abs(deviation) <= ALIGNED_TOLERANCE -> "aligné" to Success
                deviation > 0 -> "+$deviation° → tournez à droite" to Color.White
                else -> "−${-deviation}° ← tournez à gauche" to Color.White
            }
            Text(
                text,
                color = color,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                style = TabularDigits
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = Warning,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun UnavailableCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        Text(
            "Capteur magnétique non disponible sur cet appareil",
            color = Warning,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "La boussole a besoin d’un magnétomètre pour trouver le nord. Cet appareil n’en possède pas.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun buttonColors(container: Color, content: Color): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = content,
        disabledContainerColor = Navy.copy(alpha = 0.5f),
        disabledContentColor = Color.White.copy(alpha = 0.38f)
    )

/** Whole degrees to turn from [heading] to reach [target]; positive means turn right. */
private fun deviationOf(heading: Float?, target: Float?): Int? =
    if (heading == null || target == null) null else signedDelta(heading, target).roundToInt()

private fun SharedPreferences.readTarget(): Float? =
    if (contains(KEY_TARGET)) getFloat(KEY_TARGET, 0f).takeIf { it.isFinite() }?.let(::normalizeDegrees) else null

private fun SharedPreferences.writeTarget(value: Float?) {
    edit {
        if (value == null) remove(KEY_TARGET) else putFloat(KEY_TARGET, value)
    }
}
