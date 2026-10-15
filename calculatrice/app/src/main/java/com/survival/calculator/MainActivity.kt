package com.survival.calculator

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import kotlin.math.min

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Danger = Color(0xFFFF6B6B)

private val KeyInset = 5.dp
private const val KEY_FONT_SP = 28f
private const val MAX_KEY_FONT_SCALE = 1.3f
private const val FONT_STEP_SP = 2f

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

// Internal (non-display) representations only, so restoring never parses French text.
private val CalcStateSaver = listSaver<CalcState, String>(
    save = { state ->
        listOf(
            state.entry.orEmpty(),
            state.value.toString(),
            state.accumulator?.toString().orEmpty(),
            state.pending?.ordinal?.toString().orEmpty(),
            state.awaitingOperand.toString(),
            state.repeatOp?.ordinal?.toString().orEmpty(),
            state.repeatOperand?.toString().orEmpty(),
            state.expression,
            state.error.toString(),
        )
    },
    restore = { saved ->
        CalcState(
            entry = saved[0].ifEmpty { null },
            value = saved[1].toBigDecimalOrNull() ?: BigDecimal.ZERO,
            accumulator = saved[2].toBigDecimalOrNull(),
            pending = saved[3].toIntOrNull()?.let { Op.entries.getOrNull(it) },
            awaitingOperand = saved[4].toBoolean(),
            repeatOp = saved[5].toIntOrNull()?.let { Op.entries.getOrNull(it) },
            repeatOperand = saved[6].toBigDecimalOrNull(),
            expression = saved[7],
            error = saved[8].toBoolean(),
        )
    },
)

@Composable
fun AppScreen() {
    var state by rememberSaveable(stateSaver = CalcStateSaver) { mutableStateOf(CalcState()) }
    val view = LocalView.current
    val onKey: (Key) -> Unit = { key ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        state = state.press(key)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Calculatrice", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        CalcDisplay(
            state = state,
            onBackspace = { onKey(Key.Backspace) },
            modifier = Modifier.padding(horizontal = KeyInset)
        )
        Spacer(Modifier.height(12.dp))
        Keypad(
            state = state,
            onKey = onKey,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun CalcDisplay(state: CalcState, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(16.dp))
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackspace) {
                Icon(
                    painter = painterResource(R.drawable.ic_backspace),
                    contentDescription = "Effacer le dernier chiffre",
                    tint = TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            AutoSizeText(
                text = state.expression,
                color = TextSecondary,
                maxFontSize = 20.sp,
                minFontSize = 10.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        AutoSizeText(
            text = state.displayText,
            color = if (state.error) Danger else Color.White,
            maxFontSize = 56.sp,
            minFontSize = 10.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** Single-line, end-aligned text whose font shrinks until it fits the available box. */
@Composable
private fun AutoSizeText(
    text: String,
    color: Color,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterEnd) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val style = TextStyle(color = color, fontWeight = fontWeight, fontFeatureSettings = "tnum")
        val fontSize = remember(text, style, widthPx, heightPx, maxFontSize, minFontSize, measurer) {
            var size = maxFontSize.value
            while (size > minFontSize.value) {
                val layout = measurer.measure(
                    text = AnnotatedString(text),
                    style = style.copy(fontSize = size.sp),
                    softWrap = false,
                    maxLines = 1
                )
                if (layout.size.width <= widthPx && layout.size.height <= heightPx) break
                size -= FONT_STEP_SP
            }
            maxOf(size, minFontSize.value).sp
        }
        Text(text = text, style = style.copy(fontSize = fontSize), maxLines = 1, softWrap = false)
    }
}

private enum class KeyStyle { DIGIT, FUNCTION, OPERATOR }

private data class KeySpec(
    val label: String,
    val key: Key,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.DIGIT,
    val description: String? = null,
)

private fun keyRows(allClear: Boolean): List<List<KeySpec>> {
    fun digit(d: Char, weight: Float = 1f) = KeySpec(d.toString(), Key.Digit(d), weight)
    fun operator(op: Op) =
        KeySpec(op.symbol, Key.Operator(op), style = KeyStyle.OPERATOR, description = op.spokenName)
    return listOf(
        listOf(
            KeySpec(
                label = if (allClear) "AC" else "C",
                key = Key.Clear,
                style = KeyStyle.FUNCTION,
                description = if (allClear) "Tout effacer" else "Effacer la saisie"
            ),
            KeySpec("±", Key.Negate, style = KeyStyle.FUNCTION, description = "Changer le signe"),
            KeySpec("%", Key.Percent, style = KeyStyle.FUNCTION, description = "Pourcentage"),
            operator(Op.DIVIDE),
        ),
        listOf(digit('7'), digit('8'), digit('9'), operator(Op.MULTIPLY)),
        listOf(digit('4'), digit('5'), digit('6'), operator(Op.SUBTRACT)),
        listOf(digit('1'), digit('2'), digit('3'), operator(Op.ADD)),
        listOf(
            digit('0', weight = 2f),
            KeySpec(",", Key.Decimal, description = "Virgule"),
            KeySpec("=", Key.Equals, style = KeyStyle.OPERATOR, description = "Égal"),
        ),
    )
}

@Composable
private fun Keypad(state: CalcState, onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
    val allClear = state.showsAllClear
    val rows = remember(allClear) { keyRows(allClear) }
    Column(modifier) {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                row.forEach { spec ->
                    val op = (spec.key as? Key.Operator)?.op
                    CalcKey(spec = spec, highlighted = op != null && op == state.pending, onKey = onKey)
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalcKey(spec: KeySpec, highlighted: Boolean, onKey: (Key) -> Unit) {
    val (container, content) = if (highlighted) {
        Color.White to Bg
    } else {
        when (spec.style) {
            KeyStyle.OPERATOR -> Accent to Bg
            KeyStyle.FUNCTION -> Navy to Accent
            KeyStyle.DIGIT -> Navy to Color.White
        }
    }
    val description = spec.description
    Button(
        onClick = { onKey(spec.key) },
        modifier = Modifier
            .weight(spec.weight)
            .fillMaxHeight()
            .padding(KeyInset),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = spec.label,
            fontSize = keyFontSize(),
            fontWeight = if (spec.style == KeyStyle.DIGIT) FontWeight.Normal else FontWeight.Medium,
            modifier = if (description != null) {
                Modifier.clearAndSetSemantics { contentDescription = description }
            } else {
                Modifier
            }
        )
    }
}

// Key labels follow the user's font scale only up to a point so they always fit inside the key.
@Composable
private fun keyFontSize(): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    return (KEY_FONT_SP * min(fontScale, MAX_KEY_FONT_SCALE) / fontScale).sp
}
