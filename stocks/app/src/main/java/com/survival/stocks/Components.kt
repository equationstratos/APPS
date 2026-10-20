package com.survival.stocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Short messages at the bottom of the screen, with an optional « Annuler » action. */
class Notifier(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, undo: (() -> Unit)? = null) {
        host.currentSnackbarData?.dismiss()
        scope.launch {
            val result = host.showSnackbar(
                message = message,
                actionLabel = if (undo != null) "Annuler" else null,
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }
}

@Composable
fun SurvieCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        content = content
    )
}

fun statusColor(status: Status): Color = when (status) {
    Status.EXPIRED -> Danger
    Status.SOON -> Warning
    Status.OK -> Success
    Status.NO_DATE, Status.EMPTY -> TextSecondary
}

@Composable
fun StatusBadge(status: Status, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status.badge, color = color, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Round 48 dp button showing a text symbol ("−", "+"), described for screen readers. */
@Composable
fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) Navy else Navy.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = if (enabled) Color.White else TextSecondary.copy(alpha = 0.5f),
            fontSize = 24.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Navy)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun SurvieChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 14.sp) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Card,
            labelColor = Color.White,
            selectedContainerColor = Navy,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Accent
        ),
        border = BorderStroke(1.dp, if (selected) Accent else TextSecondary.copy(alpha = 0.35f))
    )
}

@Composable
fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.45f),
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Accent,
    focusedSupportingTextColor = TextSecondary,
    unfocusedSupportingTextColor = TextSecondary,
    focusedSuffixColor = TextSecondary,
    unfocusedSuffixColor = TextSecondary,
    focusedPlaceholderColor = TextSecondary.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = TextSecondary.copy(alpha = 0.6f),
    errorBorderColor = Danger,
    errorLabelColor = Danger,
    errorSupportingTextColor = Danger,
    errorCursorColor = Danger
)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}
