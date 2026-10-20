package com.survival.morse

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal val CardShape = RoundedCornerShape(16.dp)
internal val ButtonShape = RoundedCornerShape(12.dp)
internal val CellShape = RoundedCornerShape(12.dp)
internal val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Scrollable body of a tab, so every tab fits a 360 × 640 dp screen. */
@Composable
internal fun TabContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun SurvieCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
internal fun CardTitle(text: String) {
    Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun Hint(text: String, color: Color = TextSecondary) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
internal fun primaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Accent,
    contentColor = Bg,
    disabledContainerColor = Navy,
    disabledContentColor = TextSecondary
)

@Composable
internal fun secondaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Navy,
    contentColor = Color.White,
    disabledContainerColor = Navy.copy(alpha = 0.5f),
    disabledContentColor = TextSecondary.copy(alpha = 0.4f)
)

@Composable
internal fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = ButtonShape,
        colors = if (primary) primaryButtonColors() else secondaryButtonColors(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Row of mutually exclusive options (tabs, modes). */
@Composable
internal fun SegmentedChoice(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    fontSize: Int = 14
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(Card)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Accent else Color.Transparent)
                    .selectable(selected = isSelected, role = role, onClick = { onSelect(index) })
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) Bg else TextSecondary,
                    fontSize = fontSize.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun CheckOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    detail: String? = null
) {
    Row(
        modifier
            .clip(ButtonShape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .heightIn(min = 48.dp)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.padding(12.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Accent,
                uncheckedColor = TextSecondary,
                checkmarkColor = Bg,
                disabledCheckedColor = Accent.copy(alpha = 0.4f),
                disabledUncheckedColor = TextSecondary.copy(alpha = 0.3f)
            )
        )
        Column {
            Text(
                label,
                color = if (enabled) Color.White else TextSecondary.copy(alpha = 0.6f),
                fontSize = 15.sp
            )
            if (detail != null) Text(detail, color = TextSecondary, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
internal fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean = true
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = if (enabled) Color.White else TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText,
                color = if (enabled) Accent else TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                style = Tabular
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
            enabled = enabled,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Navy,
                activeTickColor = Bg.copy(alpha = 0.5f),
                inactiveTickColor = Accent.copy(alpha = 0.5f)
            )
        )
    }
}

internal enum class MicPermission { Granted, Denied, PermanentlyDenied }

internal fun readMicPermission(context: Context, deniedOnce: Boolean): MicPermission {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return MicPermission.Granted
    }
    // After a denial, the rationale flag turns false only when the system will no longer ask.
    val activity = context.findActivity()
    val blocked = deniedOnce && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    return if (blocked) MicPermission.PermanentlyDenied else MicPermission.Denied
}

internal fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (ignored: ActivityNotFoundException) {
            // No settings screen at all: nothing more we can do.
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
