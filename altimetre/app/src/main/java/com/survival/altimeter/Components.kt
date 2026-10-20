package com.survival.altimeter

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val CardShape = RoundedCornerShape(16.dp)
internal val ButtonShape = RoundedCornerShape(12.dp)
internal val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
internal fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp)
    ) {
        if (title != null) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
internal fun NoticeCard(
    title: String,
    titleColor: Color,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    SectionCard {
        Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White, fontSize = 15.sp)
        if (actionLabel != null) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(actionLabel, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp, modifier = modifier)
}

@Composable
internal fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Bg,
            disabledContainerColor = Navy,
            disabledContentColor = TextSecondary,
        ),
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = ButtonShape,
        border = BorderStroke(1.dp, Accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Segmented choice (tabs): every option is a 48 dp target announced as selected or not. */
@Composable
internal fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Navy, ButtonShape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (selected) Accent else Navy, RoundedCornerShape(9.dp))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Bg else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Label above a large tabular value. */
@Composable
internal fun ValueCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Column(modifier) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, style = Tabular, maxLines = 1)
    }
}

@Composable
internal fun ToggleRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Caption(description)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bg,
                checkedTrackColor = Accent,
                checkedBorderColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Navy,
                uncheckedBorderColor = TextSecondary,
            ),
        )
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel, color = Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Annuler", color = TextSecondary)
            }
        },
        title = { Text(title, color = Color.White) },
        text = { Text(text, color = Color.White) },
        containerColor = Card,
    )
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun Context.openAppSettings() {
    launchSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

internal fun Context.openLocationSettings() {
    launchSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}

private fun Context.launchSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
}
