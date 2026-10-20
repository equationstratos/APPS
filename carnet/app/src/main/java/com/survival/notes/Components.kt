package com.survival.notes

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val CardShape = RoundedCornerShape(16.dp)
internal val ButtonShape = RoundedCornerShape(12.dp)
internal val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Card with a coloured title, an explanation and up to two actions. */
@Composable
internal fun NoticeCard(
    title: String,
    titleColor: Color,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp)
    ) {
        Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White, fontSize = 15.sp)
        if (actionLabel != null) {
            Spacer(Modifier.height(12.dp))
            AccentButton(actionLabel, onAction, Modifier.fillMaxWidth())
        }
        if (dismissLabel != null) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = 48.dp),
            ) {
                Text(dismissLabel, color = Accent, fontSize = 15.sp)
            }
        }
    }
}

@Composable
internal fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Bg,
            disabledContainerColor = Navy,
            disabledContentColor = TextSecondary,
        ),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fontSize: TextUnit = 15.sp,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Accent else Navy),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent, disabledContentColor = TextSecondary),
        contentPadding = contentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = fontSize, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Starts [intent]; false when no application can handle it (or the system refuses it). */
internal fun Context.launchSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: RuntimeException) {
    false
}

internal fun Context.openAppSettings() {
    if (!launchSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))) {
        launchSafely(Intent(Settings.ACTION_SETTINGS))
    }
}

internal fun Context.openLocationSettings() {
    if (!launchSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) launchSafely(Intent(Settings.ACTION_SETTINGS))
}

internal fun Context.copyText(label: String, text: String): Boolean {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
    return try {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    } catch (e: RuntimeException) {
        false
    }
}
