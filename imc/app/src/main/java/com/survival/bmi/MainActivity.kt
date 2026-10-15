package com.survival.bmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

// Intermediate steps of the Warning -> Danger scale for the obesity classes.
private val ObesityMild = Color(0xFFFFA135)
private val ObesitySevere = Color(0xFFD94F4F)
private val FieldBorder = Color(0x66FFFFFF)

private val WeightRangeKg = 20.0..300.0
private val HeightRangeCm = 50.0..250.0
private const val GAUGE_MIN = 15.0
private const val GAUGE_MAX = 40.0
private const val HEALTHY_BMI_MIN = 18.5
private const val HEALTHY_BMI_MAX = 24.9
private const val MAX_INPUT_LENGTH = 6
private const val NBSP = '\u00A0'

private val GaugeTicks = listOf(15.0, 18.5, 25.0, 30.0, 35.0, 40.0)

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

/** WHO adult categories; [from] is the inclusive lower bound of the class. */
private enum class BmiCategory(val label: String, val range: String, val from: Double, val color: Color) {
    Underweight("Maigreur", "IMC < 18,5", 0.0, Accent),
    Normal("Normal", "IMC 18,5 – 24,9", 18.5, Success),
    Overweight("Surpoids", "IMC 25 – 29,9", 25.0, Warning),
    ObesityI("Obésité (classe I)", "IMC 30 – 34,9", 30.0, ObesityMild),
    ObesityII("Obésité (classe II)", "IMC 35 – 39,9", 35.0, Danger),
    ObesityIII("Obésité (classe III)", "IMC ≥ 40", 40.0, ObesitySevere);

    companion object {
        fun of(bmi: Double): BmiCategory = entries.last { bmi >= it.from }
    }
}

@Composable
private fun AppScreen() {
    var weightText by rememberSaveable { mutableStateOf("") }
    var heightText by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val weightKg = parseDecimal(weightText)?.takeIf { it in WeightRangeKg }
    val heightM = parseDecimal(heightText)?.takeIf { it in HeightRangeCm }?.let { it / 100.0 }
    // Rounded first so the category always matches the value shown (24,96 -> 25,0 -> Surpoids).
    val bmi = if (weightKg != null && heightM != null) {
        (weightKg / (heightM * heightM) * 10.0).roundToInt() / 10.0
    } else {
        null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("IMC", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Indice de masse corporelle", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        DecimalField(
            value = weightText,
            onValueChange = { weightText = it },
            name = "Poids",
            unit = "kg",
            range = WeightRangeKg,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Spacer(Modifier.height(8.dp))
        DecimalField(
            value = heightText,
            onValueChange = { heightText = it },
            name = "Taille",
            unit = "cm",
            range = HeightRangeCm,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )
        Spacer(Modifier.height(16.dp))

        ResultCard(bmi = bmi, weightKg = weightKg, heightM = heightM)
        Spacer(Modifier.height(16.dp))

        Text(
            "Indicatif — adultes de 18 ans et plus",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    name: String,
    unit: String,
    range: ClosedFloatingPointRange<Double>,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions
) {
    var focused by remember { mutableStateOf(false) }
    val number = parseDecimal(value)
    val outOfRange = value.isNotEmpty() && (number == null || number !in range)
    // While typing, "1" on the way to "180" is not an error yet; only flag values already too
    // large, or anything invalid once the field is left.
    val showError = outOfRange && (!focused || (number != null && number > range.endInclusive))
    val limits = "entre ${range.start.roundToInt()} et ${range.endInclusive.roundToInt()}$NBSP$unit"

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeDecimal(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"),
        label = { Text("$name ($unit)") },
        supportingText = {
            Text(
                when {
                    !showError -> limits.replaceFirstChar { it.uppercase() }
                    number == null -> "Saisissez un nombre $limits"
                    else -> "$name hors limites$NBSP: $limits"
                }
            )
        },
        isError = showError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = FieldBorder
        )
    )
}

@Composable
private fun ResultCard(bmi: Double?, weightKg: Double?, heightM: Double?) {
    val category = bmi?.let { BmiCategory.of(it) }
    val color by animateColorAsState(category?.color ?: TextSecondary, label = "bmiColor")

    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Votre IMC", color = TextSecondary, fontSize = 14.sp)
        Text(
            bmi?.let { formatOneDecimal(it) } ?: "–,–",
            color = color,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Text(
            category?.label ?: "Saisissez votre poids et votre taille",
            color = if (category != null) color else TextSecondary,
            fontSize = if (category != null) 20.sp else 15.sp,
            fontWeight = if (category != null) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        if (category != null) {
            Text(category.range, color = TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))

        BmiGauge(bmi = bmi)
        Spacer(Modifier.height(16.dp))

        HealthyWeight(category = category, weightKg = weightKg, heightM = heightM)
    }
}

@Composable
private fun BmiGauge(bmi: Double?) {
    val markerFraction by animateFloatAsState(bmi?.let { gaugeFraction(it) } ?: 0f, label = "gaugeMarker")
    val description = if (bmi != null) {
        "Jauge de l'IMC de 15 à 40, valeur ${formatOneDecimal(bmi)}"
    } else {
        "Jauge de l'IMC de 15 à 40"
    }

    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .semantics { contentDescription = description }
        ) {
            val barHeight = 12.dp.toPx()
            val barTop = size.height - barHeight
            val bar = Path().apply {
                addRoundRect(RoundRect(0f, barTop, size.width, size.height, CornerRadius(barHeight / 2f)))
            }
            clipPath(bar) {
                BmiCategory.entries.forEach { category ->
                    val left = gaugeFraction(category.from) * size.width
                    val right = (category.next()?.let { gaugeFraction(it.from) } ?: 1f) * size.width
                    if (right > left) {
                        drawRect(category.color, topLeft = Offset(left, barTop), size = Size(right - left, barHeight))
                    }
                }
            }

            if (bmi != null) {
                val halfWidth = 7.dp.toPx()
                val x = (markerFraction * size.width).coerceIn(halfWidth, size.width - halfWidth)
                val tipY = barTop - 3.dp.toPx()
                val pointer = Path().apply {
                    moveTo(x - halfWidth, 0f)
                    lineTo(x + halfWidth, 0f)
                    lineTo(x, tipY)
                    close()
                }
                drawPath(pointer, Color.White)
                drawLine(
                    Card,
                    Offset(x, barTop - 1.dp.toPx()),
                    Offset(x, size.height + 1.dp.toPx()),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    Color.White,
                    Offset(x, barTop),
                    Offset(x, size.height),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        GaugeLabels()
    }
}

/** Tick labels centred under their gauge position, kept inside the gauge width. */
@Composable
private fun GaugeLabels() {
    Layout(
        content = {
            GaugeTicks.forEach { tick ->
                Text(formatTick(tick), color = TextSecondary, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val center = (gaugeFraction(GaugeTicks[index]) * width).roundToInt()
                val x = (center - placeable.width / 2).coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
private fun HealthyWeight(category: BmiCategory?, weightKg: Double?, heightM: Double?) {
    if (heightM == null) {
        Text(
            "Renseignez votre taille pour connaître votre poids de forme",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        return
    }
    val low = HEALTHY_BMI_MIN * heightM * heightM
    val high = HEALTHY_BMI_MAX * heightM * heightM
    Text(
        "Poids de forme pour votre taille$NBSP: ${formatOneDecimal(low)} – ${formatOneDecimal(high)}${NBSP}kg",
        color = Color.White,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
    if (category != null && weightKg != null) {
        val gap = when (category) {
            BmiCategory.Normal -> null
            BmiCategory.Underweight -> "${formatOneDecimal((low - weightKg).coerceAtLeast(0.1))}${NBSP}kg sous le poids de forme"
            else -> "${formatOneDecimal((weightKg - high).coerceAtLeast(0.1))}${NBSP}kg au-dessus du poids de forme"
        }
        Spacer(Modifier.height(4.dp))
        Text(
            gap ?: "Vous êtes dans la zone de forme",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

private fun BmiCategory.next(): BmiCategory? = BmiCategory.entries.getOrNull(ordinal + 1)

private fun gaugeFraction(bmi: Double): Float =
    ((bmi - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0.0, 1.0).toFloat()

private fun parseDecimal(text: String): Double? =
    text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Keeps ASCII digits and a single decimal separator (',' or '.'), capped in length. */
private fun sanitizeDecimal(raw: String): String {
    val out = StringBuilder()
    var hasSeparator = false
    for (c in raw) {
        if (out.length >= MAX_INPUT_LENGTH) break
        when {
            c in '0'..'9' -> out.append(c)
            (c == ',' || c == '.') && !hasSeparator -> {
                out.append(c)
                hasSeparator = true
            }
        }
    }
    return out.toString()
}

private fun formatOneDecimal(value: Double): String = String.format(Locale.FRANCE, "%.1f", value)

private fun formatTick(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else formatOneDecimal(value)
