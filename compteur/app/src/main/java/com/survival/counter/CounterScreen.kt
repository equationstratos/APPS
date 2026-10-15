package com.survival.counter

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val countFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.FRANCE)

private fun formatCount(value: Int): String = countFormat.format(value.toLong())

@Composable
fun AppScreen(store: CounterStore) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val count = store.count
    val step = store.step

    fun change(delta: Int) {
        if (store.add(delta)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Compteur", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Touchez la zone ou utilisez les touches de volume",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))

        StepSelector(current = step, onSelect = store::selectStep)
        Spacer(Modifier.height(16.dp))

        TapZone(
            count = count,
            step = step,
            onTap = { change(step) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                label = "−$step",
                enabled = count > 0,
                onClick = { change(-step) },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                label = "Réinitialiser",
                enabled = count > 0,
                onClick = { confirmReset = true },
                modifier = Modifier.weight(1.4f)
            )
        }
    }

    if (confirmReset) {
        ResetDialog(
            count = count,
            onConfirm = {
                confirmReset = false
                if (store.reset()) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun StepSelector(current: Int, onSelect: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Pas", color = TextSecondary, fontSize = 16.sp)
        CounterStore.STEPS.forEach { value ->
            val isSelected = value == current
            Button(
                onClick = { onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { selected = isSelected },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Accent else Navy,
                    contentColor = if (isSelected) Bg else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    "$value",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
private fun TapZone(count: Int, step: Int, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val formatted = remember(count) { formatCount(count) }
    val atMax = count >= CounterStore.MAX_COUNT
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Card)
            .clickable(onClickLabel = "Ajouter $step", onClick = onTap)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Sized in dp (not sp) so the huge readout always fits the zone, whatever the font scale.
        val digitsSize = minOf(128.dp, maxWidth / (formatted.length * 0.62f), maxHeight * 0.5f)
        val fontSize = with(LocalDensity.current) { digitsSize.toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatted,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                if (atMax) "Maximum atteint" else "Touchez pour ajouter +$step",
                color = if (atMax) Danger else TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun ResetDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Réinitialiser", color = Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
        title = { Text("Réinitialiser le compteur ?") },
        text = { Text("Le compte actuel (${formatCount(count)}) sera remis à zéro.") }
    )
}
