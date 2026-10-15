package com.survival.torch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
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

private enum class TorchMode(val label: String, val description: String, val needsFlash: Boolean) {
    Steady("Lampe", "Éclairage continu, qui reste allumé même écran verrouillé.", true),
    Sos("SOS", "Signal de détresse en morse ··· ——— ··· répété en boucle.", true),
    Strobe("Strobo", "Clignotement régulier pour signaler votre position.", true),
    Screen("Écran", "Écran blanc à luminosité maximale, sans utiliser le flash.", false),
}

private const val SOS_UNIT_MS = 200L

// ··· ——— ··· as alternating lit/dark durations in Morse units: dot 1, dash 3, gap 1, letter gap 3, word gap 7.
private val SOS_UNITS = intArrayOf(1, 1, 1, 1, 1, 3, 3, 1, 3, 1, 3, 3, 1, 1, 1, 1, 1, 7)

private const val MIN_STROBE_HZ = 1
private const val MAX_STROBE_HZ = 15
private const val DEFAULT_STROBE_HZ = 5
private const val MAX_LAG_MS = 50L
private const val MAX_SLIDER_TICKS = 20

private const val PREFS_NAME = "survie"
private const val KEY_STROBE_HZ = "lampe_strobo_hz"
private const val KEY_STRENGTH = "lampe_intensite"

private const val MSG_BUSY = "Lampe indisponible : caméra utilisée par une autre application"
private const val MSG_NO_FLASH = "Flash non disponible sur cet appareil. Le mode Écran reste utilisable."

@Composable
fun AppScreen(torch: TorchController) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val haptics = LocalHapticFeedback.current

    var modeIndex by rememberSaveable {
        mutableIntStateOf(if (torch.hasFlash) TorchMode.Steady.ordinal else TorchMode.Screen.ordinal)
    }
    val mode = TorchMode.entries[modeIndex]
    var patternRunning by rememberSaveable { mutableStateOf(false) }
    var screenActive by rememberSaveable { mutableStateOf(false) }
    var strobeHz by rememberSaveable {
        mutableIntStateOf(prefs.getInt(KEY_STROBE_HZ, DEFAULT_STROBE_HZ).coerceIn(MIN_STROBE_HZ, MAX_STROBE_HZ))
    }
    var strength by rememberSaveable {
        mutableIntStateOf(prefs.getInt(KEY_STRENGTH, torch.maxStrengthLevel).coerceIn(1, torch.maxStrengthLevel))
    }
    // Mirrors the real torch state reported by the system, including changes made from Quick Settings.
    var torchLit by remember { mutableStateOf(false) }
    var cameraBusy by remember { mutableStateOf(false) }

    DisposableEffect(torch) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId != torch.cameraId) return
                torchLit = enabled
                cameraBusy = false
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId != torch.cameraId) return
                torchLit = false
                cameraBusy = true
            }
        }
        torch.registerCallback(callback)
        onDispose { torch.unregisterCallback(callback) }
    }

    LaunchedEffect(mode, patternRunning) {
        if (!patternRunning) return@LaunchedEffect
        when (mode) {
            TorchMode.Sos -> runPattern(torch, { strength }, SOS_UNITS.size) { step -> SOS_UNITS[step] * SOS_UNIT_MS }
            TorchMode.Strobe -> runPattern(torch, { strength }, 2) { 500L / strobeHz }
            TorchMode.Steady, TorchMode.Screen -> return@LaunchedEffect
        }
        // runPattern only returns when the torch stopped responding.
        patternRunning = false
        cameraBusy = true
    }

    val selectMode: (TorchMode) -> Unit = { newMode ->
        if (newMode != mode) {
            patternRunning = false
            screenActive = false
            if (torchLit) torch.setTorch(on = false)
            modeIndex = newMode.ordinal
        }
    }

    val onPower: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        when (mode) {
            TorchMode.Steady -> {
                val target = !torchLit
                if (torch.setTorch(target, strength)) {
                    torchLit = target
                    cameraBusy = false
                } else {
                    cameraBusy = true
                }
            }
            TorchMode.Sos, TorchMode.Strobe -> {
                patternRunning = !patternRunning
                cameraBusy = false
            }
            TorchMode.Screen -> screenActive = true
        }
    }

    if (screenActive) {
        ScreenLight(onExit = { screenActive = false })
    } else {
        val isOn = when (mode) {
            TorchMode.Steady -> torchLit
            TorchMode.Sos, TorchMode.Strobe -> patternRunning
            TorchMode.Screen -> false
        }
        val hint = when (mode) {
            TorchMode.Steady -> if (torchLit) "Touchez pour éteindre" else "Touchez pour allumer"
            TorchMode.Sos -> if (patternRunning) "Signal SOS en cours d'émission" else "Touchez pour émettre le signal SOS"
            TorchMode.Strobe -> if (patternRunning) "Stroboscope en marche" else "Touchez pour lancer le stroboscope"
            TorchMode.Screen -> "Touchez pour allumer l'écran blanc"
        }
        val message = when {
            !torch.hasFlash -> MSG_NO_FLASH to Warning
            cameraBusy && mode.needsFlash -> MSG_BUSY to Danger
            else -> null
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Lampe", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Torche, SOS, stroboscope et écran blanc", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))

            ModeSelector(selected = mode, hasFlash = torch.hasFlash, onSelect = selectMode)
            Spacer(Modifier.height(12.dp))
            Text(mode.description, color = TextSecondary, fontSize = 14.sp)

            message?.let { (text, color) ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text,
                    color = color,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Card)
                        .padding(16.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            PowerButton(isOn = isOn, onClick = onPower, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(
                hint,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (mode == TorchMode.Strobe) {
                Spacer(Modifier.height(20.dp))
                SettingSlider(
                    label = "Fréquence",
                    valueText = "$strobeHz Hz",
                    value = strobeHz,
                    range = MIN_STROBE_HZ..MAX_STROBE_HZ,
                    onValueChange = { strobeHz = it },
                    onValueChangeFinished = { prefs.edit { putInt(KEY_STROBE_HZ, strobeHz) } },
                    footnote = "Déconseillé aux personnes photosensibles."
                )
            }

            if (torch.supportsStrength && mode.needsFlash) {
                Spacer(Modifier.height(16.dp))
                SettingSlider(
                    label = "Intensité",
                    valueText = "$strength / ${torch.maxStrengthLevel}",
                    value = strength,
                    range = 1..torch.maxStrengthLevel,
                    onValueChange = { level ->
                        if (level != strength) {
                            strength = level
                            if (mode == TorchMode.Steady && torchLit && !torch.setTorch(true, level)) {
                                cameraBusy = true
                            }
                        }
                    },
                    onValueChangeFinished = { prefs.edit { putInt(KEY_STRENGTH, strength) } }
                )
            }
        }
    }
}

/**
 * Plays an on/off pattern until cancelled (even steps lit, odd steps dark) and returns only if the
 * torch stopped responding. The torch is always switched off when it ends, cancellation included.
 */
private suspend fun runPattern(
    torch: TorchController,
    strengthLevel: () -> Int,
    stepCount: Int,
    stepDurationMs: (step: Int) -> Long,
) {
    try {
        withContext(Dispatchers.Default) {
            var step = 0
            var deadline = SystemClock.elapsedRealtime()
            while (torch.setTorch(on = step % 2 == 0, strengthLevel = strengthLevel())) {
                val now = SystemClock.elapsedRealtime()
                // After a stall (e.g. Doze) restart the timeline instead of replaying missed steps in a burst.
                deadline = maxOf(deadline, now - MAX_LAG_MS) + stepDurationMs(step)
                // delay() skips its cancellation check for non-positive values, which happens when the
                // torch switches slower than a step (fast strobe on some devices).
                ensureActive()
                delay(deadline - now)
                step = (step + 1) % stepCount
            }
        }
    } finally {
        torch.setTorch(on = false)
    }
}

@Composable
private fun ModeSelector(selected: TorchMode, hasFlash: Boolean, onSelect: (TorchMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TorchMode.entries.forEach { mode ->
            val enabled = hasFlash || !mode.needsFlash
            val isSelected = mode == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Accent else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(mode) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.label,
                    color = when {
                        isSelected -> Bg
                        enabled -> Color.White
                        else -> Color.White.copy(alpha = 0.38f)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PowerButton(isOn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container by animateColorAsState(if (isOn) Accent else Navy, label = "powerContainer")
    val content by animateColorAsState(if (isOn) Bg else Color.White, label = "powerContent")
    val haloAlpha by animateFloatAsState(if (isOn) 0.25f else 0f, label = "powerHalo")

    Box(
        modifier
            .size(224.dp)
            .drawBehind { drawCircle(Accent, alpha = haloAlpha) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(container)
                .border(4.dp, Accent, CircleShape)
                .toggleable(value = isOn, role = Role.Switch, onValueChange = { onClick() }),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PowerGlyph(color = content, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                if (isOn) "ON" else "OFF",
                color = content,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PowerGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.11f
        val inset = stroke / 2 + size.minDimension * 0.06f
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(center.x, stroke / 2),
            end = Offset(center.x, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    footnote: String? = null,
) {
    val span = range.last - range.first
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText,
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (span in 1..MAX_SLIDER_TICKS) span - 1 else 0,
            onValueChangeFinished = onValueChangeFinished
        )
        if (footnote != null) {
            Text(footnote, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScreenLight(onExit: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        if (window != null) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) {
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
    BackHandler(onBack = onExit)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Quitter le mode Écran",
                onClick = onExit
            )
    ) {
        Text(
            "Touchez l'écran pour quitter",
            color = Bg.copy(alpha = 0.4f),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 16.dp)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
