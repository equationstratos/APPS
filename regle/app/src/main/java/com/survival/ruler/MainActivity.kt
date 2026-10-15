package com.survival.ruler

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Danger = Color(0xFFFF6B6B)

private const val MM_PER_INCH = 25.4f

// ISO/IEC 7810 ID-1 (bank card) dimensions.
private const val CARD_LENGTH_MM = 85.60f
private const val CARD_WIDTH_MM = 53.98f
private const val CARD_CORNER_MM = 3.18f

private const val MIN_FACTOR = 0.80f
private const val MAX_FACTOR = 1.20f
private const val FACTOR_STEP = 0.001f
private const val DEFAULT_CURSOR_MM = 50f

private const val PREFS_NAME = "survie"
private const val KEY_FACTOR = "regle_facteur_calibration"

/** Horizontal room kept free on each side for the tick marks and their labels. */
private val RulerBandWidth = 64.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // The ruler's zero must be the physical top edge, even on screens with a notch.
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        setContent { SurvieTheme { AppScreen() } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
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
private fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val rawPxPerMm = remember(context) { physicalYdpi(context.resources.displayMetrics) / MM_PER_INCH }
    var factor by rememberSaveable { mutableFloatStateOf(loadFactor(prefs)) }
    var calibrating by rememberSaveable { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val saveFactor: () -> Unit = { prefs.edit { putFloat(KEY_FACTOR, factor) } }

    if (calibrating) {
        val finish: () -> Unit = {
            saveFactor()
            calibrating = false
        }
        BackHandler(onBack = finish)
        CalibrationScreen(
            rawPxPerMm = rawPxPerMm,
            factor = factor,
            onFactorChange = { factor = it.coerceIn(MIN_FACTOR, MAX_FACTOR) },
            onCommit = saveFactor,
            onReset = {
                factor = 1f
                saveFactor()
            },
            onDone = finish
        )
    } else {
        RulerScreen(
            pxPerMm = rawPxPerMm * factor,
            factor = factor,
            onCalibrate = { calibrating = true }
        )
    }
}

/* ---------------------------------------------------------------- Ruler */

@Composable
private fun RulerScreen(pxPerMm: Float, factor: Float, onCalibrate: () -> Unit) {
    var cursorMm by rememberSaveable { mutableFloatStateOf(DEFAULT_CURSOR_MM) }
    val safeTopPx = WindowInsets.safeDrawing.getTop(LocalDensity.current).toFloat()

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .pointerInput(pxPerMm) {
                detectTapGestures(
                    onTap = { offset -> cursorMm = (offset.y / pxPerMm).coerceAtLeast(0f) }
                )
            }
            .pointerInput(pxPerMm) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val maxMm = size.height / pxPerMm
                    cursorMm = (cursorMm + dragAmount / pxPerMm).coerceIn(0f, maxMm)
                }
            }
    ) {
        RulerScale(pxPerMm = pxPerMm, modifier = Modifier.fillMaxSize())
        RulerInfo(
            factor = factor,
            onCalibrate = onCalibrate,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = RulerBandWidth, vertical = 16.dp)
        )
        MeasureCursor(
            pxPerMm = pxPerMm,
            cursorMm = { cursorMm },
            safeTopPx = safeTopPx,
            // Own layer: moving the cursor re-records only this layer, not the whole scale.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { }
        )
    }
}

@Composable
private fun RulerInfo(factor: Float, onCalibrate: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Règle", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "cm à gauche · pouces à droite",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Le 0 est le bord haut de l'affichage. Touchez ou glissez pour placer la ligne de mesure.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (abs(factor - 1f) < FACTOR_STEP / 2f) {
                "Non calibrée"
            } else {
                "Calibrée · ${formatPercent(factor)}"
            },
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onCalibrate,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
        ) {
            Text("Calibrer", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RulerScale(pxPerMm: Float, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum"
        )
    }
    Canvas(modifier) {
        drawCentimetres(pxPerMm, textMeasurer, labelStyle)
        drawInches(pxPerMm * MM_PER_INCH, textMeasurer, labelStyle)
    }
}

/** Left edge: one tick per millimetre, starting at x = 0 and pointing inward. */
private fun DrawScope.drawCentimetres(pxPerMm: Float, textMeasurer: TextMeasurer, labelStyle: TextStyle) {
    val thin = max(1.5f, 1.dp.toPx())
    val thick = max(2f, 1.5.dp.toPx())
    val mmLength = 10.dp.toPx()
    val halfCmLength = 16.dp.toPx()
    val cmLength = 24.dp.toPx()
    val labelX = cmLength + 4.dp.toPx()
    val lastMm = (size.height / pxPerMm).toInt()
    for (mm in 0..lastMm) {
        val y = mm * pxPerMm
        val isCm = mm % 10 == 0
        val length = when {
            isCm -> cmLength
            mm % 5 == 0 -> halfCmLength
            else -> mmLength
        }
        drawLine(Accent, Offset(0f, y), Offset(length, y), strokeWidth = if (isCm) thick else thin)
        if (isCm && mm > 0) {
            val label = textMeasurer.measure(AnnotatedString((mm / 10).toString()), style = labelStyle)
            val top = y - label.size.height / 2f
            if (top + label.size.height <= size.height) {
                drawText(label, topLeft = Offset(labelX, top))
            }
        }
    }
}

/** Right edge: one tick per 1/16 inch, starting at the right edge and pointing inward. */
private fun DrawScope.drawInches(pxPerInch: Float, textMeasurer: TextMeasurer, labelStyle: TextStyle) {
    val thin = max(1.5f, 1.dp.toPx())
    val thick = max(2f, 1.5.dp.toPx())
    val inchLength = 26.dp.toPx()
    val gap = 4.dp.toPx()
    val step = pxPerInch / 16f
    val lastStep = (size.height / step).toInt()
    for (i in 0..lastStep) {
        val y = i * step
        val isInch = i % 16 == 0
        val length = when {
            isInch -> inchLength
            i % 8 == 0 -> 20.dp.toPx()
            i % 4 == 0 -> 15.dp.toPx()
            i % 2 == 0 -> 11.dp.toPx()
            else -> 7.dp.toPx()
        }
        drawLine(
            Accent,
            Offset(size.width, y),
            Offset(size.width - length, y),
            strokeWidth = if (isInch) thick else thin
        )
        if (isInch && i > 0) {
            val label = textMeasurer.measure(AnnotatedString((i / 16).toString()), style = labelStyle)
            val top = y - label.size.height / 2f
            if (top + label.size.height <= size.height) {
                drawText(label, topLeft = Offset(size.width - inchLength - gap - label.size.width, top))
            }
        }
    }
}

@Composable
private fun MeasureCursor(
    pxPerMm: Float,
    cursorMm: () -> Float,
    safeTopPx: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val pillStyle = remember {
        TextStyle(color = Bg, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    }
    // The position is read only while drawing, so dragging redraws this layer without recomposing.
    Canvas(modifier) {
        val y = (cursorMm() * pxPerMm).coerceIn(0f, size.height)
        val mm = y / pxPerMm
        drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())

        val text = String.format(Locale.FRANCE, "%.1f cm · %.2f in", mm / 10f, mm / MM_PER_INCH)
        val layout = textMeasurer.measure(AnnotatedString(text), style = pillStyle)
        val padH = 14.dp.toPx()
        val padV = 8.dp.toPx()
        val gap = 10.dp.toPx()
        val pillSize = Size(layout.size.width + 2 * padH, layout.size.height + 2 * padV)
        val above = y - gap - pillSize.height
        val pillTop = if (above >= safeTopPx + gap) above else y + gap
        val pillLeft = (size.width - pillSize.width) / 2f
        drawRoundRect(
            color = Accent,
            topLeft = Offset(pillLeft, pillTop),
            size = pillSize,
            cornerRadius = CornerRadius(pillSize.height / 2f)
        )
        drawText(layout, topLeft = Offset(pillLeft + padH, pillTop + padV))
    }
}

/* ---------------------------------------------------------- Calibration */

@Composable
private fun CalibrationScreen(
    rawPxPerMm: Float,
    factor: Float,
    onFactorChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    val pxPerMm = rawPxPerMm * factor
    val latestFactor by rememberUpdatedState(factor)
    val latestOnFactorChange by rememberUpdatedState(onFactorChange)
    val latestOnCommit by rememberUpdatedState(onCommit)

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        CardFootprint(
            pxPerMm = pxPerMm,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(rawPxPerMm) {
                    var dragFactor = 1f
                    detectVerticalDragGestures(
                        onDragStart = { dragFactor = latestFactor },
                        onDragEnd = { latestOnCommit() },
                        onDragCancel = { latestOnCommit() }
                    ) { change, dragAmount ->
                        change.consume()
                        // The guide line sits at CARD_LENGTH_MM * rawPxPerMm * factor px from the top.
                        dragFactor = (dragFactor + dragAmount / (CARD_LENGTH_MM * rawPxPerMm))
                            .coerceIn(MIN_FACTOR, MAX_FACTOR)
                        latestOnFactorChange(dragFactor)
                    }
                }
        )
        CalibrationPanel(
            factor = factor,
            onFactorChange = onFactorChange,
            onCommit = onCommit,
            onReset = onReset,
            onDone = onDone
        )
        CalibrationGuide(pxPerMm = pxPerMm, modifier = Modifier.fillMaxSize())
    }
}

/** Where the card goes: a real-size ID-1 outline in the top-left corner. */
@Composable
private fun CardFootprint(pxPerMm: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(
            color = Navy,
            topLeft = Offset.Zero,
            size = Size(CARD_WIDTH_MM * pxPerMm, CARD_LENGTH_MM * pxPerMm),
            cornerRadius = CornerRadius(CARD_CORNER_MM * pxPerMm)
        )
    }
}

/** Vertical bracket along the left edge, from the top edge down to 85,60 mm. */
@Composable
private fun CalibrationGuide(pxPerMm: Float, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(color = Bg, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    }
    val label = remember { "Carte · " + String.format(Locale.FRANCE, "%.2f mm", CARD_LENGTH_MM) }
    Canvas(modifier) {
        val lineY = CARD_LENGTH_MM * pxPerMm
        val bar = 4.dp.toPx()
        val cap = 20.dp.toPx()
        drawLine(Accent, Offset(bar / 2f, 0f), Offset(bar / 2f, lineY), strokeWidth = bar)
        drawLine(Accent, Offset(0f, bar / 2f), Offset(cap, bar / 2f), strokeWidth = bar)
        drawLine(Accent, Offset(0f, lineY), Offset(cap, lineY), strokeWidth = bar)
        drawLine(Accent, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 2.dp.toPx())

        val layout = textMeasurer.measure(AnnotatedString(label), style = labelStyle)
        val padH = 12.dp.toPx()
        val padV = 6.dp.toPx()
        val gap = 8.dp.toPx()
        val pillSize = Size(layout.size.width + 2 * padH, layout.size.height + 2 * padV)
        val below = lineY + gap
        val pillTop = if (below + pillSize.height <= size.height) below else lineY - gap - pillSize.height
        val pillLeft = size.width - 16.dp.toPx() - pillSize.width
        drawRoundRect(
            color = Accent,
            topLeft = Offset(pillLeft, pillTop),
            size = pillSize,
            cornerRadius = CornerRadius(pillSize.height / 2f)
        )
        drawText(layout, topLeft = Offset(pillLeft + padH, pillTop + padV))
    }
}

@Composable
private fun CalibrationPanel(
    factor: Float,
    onFactorChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    // The real card covers the top-left corner, so the controls sit at the bottom where they stay reachable.
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Calibrer", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Posez une carte bancaire dans le coin haut gauche, long côté contre le bord gauche. " +
                    "Glissez le doigt sur l'écran pour amener la ligne bleue au bord bas de la carte, " +
                    "puis affinez avec − et +.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Card
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton(symbol = "−", description = "Réduire l'échelle") {
                        onFactorChange(stepFactor(factor, -1))
                        onCommit()
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatPercent(factor),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                        Text("Échelle", color = TextSecondary, fontSize = 12.sp)
                    }
                    StepButton(symbol = "+", description = "Agrandir l'échelle") {
                        onFactorChange(stepFactor(factor, 1))
                        onCommit()
                    }
                }
                Slider(
                    value = factor,
                    onValueChange = onFactorChange,
                    valueRange = MIN_FACTOR..MAX_FACTOR,
                    onValueChangeFinished = onCommit,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Navy
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onReset,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("Réinitialiser", textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = onDone,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("Terminé", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(symbol, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

/* -------------------------------------------------------------- Helpers */

/** Physical vertical density; falls back to the density bucket when the device reports a placeholder. */
private fun physicalYdpi(metrics: DisplayMetrics): Float {
    val reported = metrics.ydpi
    val nominal = metrics.densityDpi.toFloat()
    return if (reported.isFinite() && reported > 0f && reported / nominal in 0.6f..1.6f) reported else nominal
}

private fun loadFactor(prefs: SharedPreferences): Float {
    val stored = prefs.getFloat(KEY_FACTOR, 1f)
    return if (stored in MIN_FACTOR..MAX_FACTOR) stored else 1f
}

/** One fine step, snapped to the 0.1 % grid so repeated taps never accumulate float drift. */
private fun stepFactor(factor: Float, direction: Int): Float =
    ((factor + direction * FACTOR_STEP) * 1000f).roundToInt() / 1000f

private fun formatPercent(factor: Float): String =
    String.format(Locale.FRANCE, "%.1f %%", factor * 100f)
