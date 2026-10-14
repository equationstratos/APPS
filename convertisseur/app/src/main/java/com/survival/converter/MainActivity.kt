package com.survival.converter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

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

@Composable
fun AppScreen() {
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    val category = Category.entries[categoryIndex.coerceIn(Category.entries.indices)]
    var fromIndex by rememberSaveable { mutableIntStateOf(category.defaultFrom) }
    var toIndex by rememberSaveable { mutableIntStateOf(category.defaultTo) }
    var input by rememberSaveable { mutableStateOf("1") }
    val focusManager = LocalFocusManager.current

    val from = category.units[fromIndex.coerceIn(category.units.indices)]
    val to = category.units[toIndex.coerceIn(category.units.indices)]

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Convertisseur", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Conversion d'unités instantanée, hors ligne", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        CategoryChips(
            selected = category,
            onSelect = { selected ->
                if (selected != category) {
                    categoryIndex = selected.ordinal
                    fromIndex = selected.defaultFrom
                    toIndex = selected.defaultTo
                }
            }
        )
        Spacer(Modifier.height(16.dp))

        ValueField(
            text = input,
            onTextChange = { raw -> sanitizeInput(raw)?.let { input = it } },
            onToggleSign = { input = toggleSign(input) },
            onDone = { focusManager.clearFocus() }
        )
        Spacer(Modifier.height(12.dp))

        UnitPicker(label = "De", units = category.units, selected = from, onSelect = { fromIndex = it })
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            SwapButton(
                onClick = {
                    val previousFrom = fromIndex
                    fromIndex = toIndex
                    toIndex = previousFrom
                }
            )
        }
        UnitPicker(label = "Vers", units = category.units, selected = to, onSelect = { toIndex = it })
        Spacer(Modifier.height(16.dp))

        ResultPanel(category = category, value = parseInput(input), from = from, to = to)
    }
}

@Composable
private fun CategoryChips(selected: Category, onSelect: (Category) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Category.entries.forEach { category ->
            val isSelected = category == selected
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Accent else Navy)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(category) })
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    category.label,
                    color = if (isSelected) Bg else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ValueField(
    text: String,
    onTextChange: (String) -> Unit,
    onToggleSign: () -> Unit,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontFeatureSettings = "tnum"),
        label = { Text("Valeur à convertir") },
        placeholder = { Text("0") },
        trailingIcon = {
            // Many numeric keyboards have no minus key; this keeps negative temperatures reachable.
            IconButton(
                onClick = onToggleSign,
                modifier = Modifier.semantics { contentDescription = "Changer le signe" }
            ) {
                Text("±", fontSize = 24.sp, color = Accent)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Accent.copy(alpha = 0.45f)
        )
    )
}

@Composable
private fun UnitPicker(
    label: String,
    units: List<MeasureUnit>,
    selected: MeasureUnit,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Navy)
                .clickable(onClickLabel = "Choisir l'unité", role = Role.Button) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = TextSecondary, fontSize = 12.sp)
                Text(
                    selected.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                selected.symbol,
                modifier = Modifier.padding(start = 12.dp),
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            Canvas(
                Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
            ) { drawChevron(TextSecondary) }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEachIndexed { index, unit ->
                val isSelected = unit === selected
                DropdownMenuItem(
                    text = {
                        Text(
                            unit.name,
                            color = if (isSelected) Accent else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = { Text(unit.symbol, color = TextSecondary) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SwapButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Accent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Inverser les unités" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) { drawSwapArrows(Bg) }
    }
}

@Composable
private fun ResultPanel(category: Category, value: Double?, from: MeasureUnit, to: MeasureUnit) {
    val tabular = TextStyle(fontFeatureSettings = "tnum")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        Text("Résultat", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                if (value == null) {
                    append("—")
                } else {
                    append(formatNumber(convert(value, from, to)))
                    withStyle(SpanStyle(color = Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)) {
                        append(" ")
                        append(to.symbol)
                    }
                }
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            style = tabular
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "1 ${from.symbol} = ${formatNumber(convert(1.0, from, to))} ${to.symbol}",
            color = TextSecondary,
            fontSize = 14.sp,
            style = tabular
        )
        val hint = if (value == null) null else category.hintFor(value, from)
        when {
            value == null -> Hint("Saisissez une valeur.", TextSecondary)
            hint != null -> Hint(hint, Warning)
        }
    }
}

@Composable
private fun Hint(text: String, color: Color) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = color, fontSize = 14.sp)
}

/** Downward chevron drawn on a 24-unit grid so it needs no icon font. */
private fun DrawScope.drawChevron(color: Color) {
    gridLine(color, 6f, 9f, 12f, 15f)
    gridLine(color, 12f, 15f, 18f, 9f)
}

/** ⇅ drawn on a 24-unit grid: an up arrow on the left, a down arrow on the right. */
private fun DrawScope.drawSwapArrows(color: Color) {
    gridLine(color, 8f, 20f, 8f, 4f)
    gridLine(color, 3.5f, 8.5f, 8f, 4f)
    gridLine(color, 12.5f, 8.5f, 8f, 4f)
    gridLine(color, 16f, 4f, 16f, 20f)
    gridLine(color, 11.5f, 15.5f, 16f, 20f)
    gridLine(color, 20.5f, 15.5f, 16f, 20f)
}

private fun DrawScope.gridLine(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) {
    val unit = size.width / 24f
    drawLine(
        color = color,
        start = Offset(x1 * unit, y1 * unit),
        end = Offset(x2 * unit, y2 * unit),
        strokeWidth = 2.5f * unit,
        cap = StrokeCap.Round
    )
}
