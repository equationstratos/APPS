package com.survival.chrono

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Average advance of a tabular digit relative to the font size, with a small safety margin. */
private const val GLYPH_WIDTH_RATIO = 0.62f

@Composable
internal fun SurvieButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Accent,
    contentColor: Color = Bg,
    minHeight: Dp = 56.dp,
    fontSize: TextUnit = 17.sp
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Navy,
            disabledContentColor = TextDisabled
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SurvieButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = Navy,
        contentColor = Color.White
    )
}

/**
 * Large tabular readout whose size follows the available width rather than the user font scale,
 * so long values (hours) never clip on a 360 dp screen.
 */
@Composable
internal fun FittedReadout(text: String, color: Color, maxGlyph: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val glyph = minOf(maxGlyph, maxWidth / (text.length.coerceAtLeast(1) * GLYPH_WIDTH_RATIO))
        val fontSize = with(LocalDensity.current) { glyph.toSp() }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = fontSize * 1.2f,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            softWrap = false,
            style = Tnum
        )
    }
}

/** mm:ss,cc — or h:mm:ss,cc from one hour on. */
internal fun formatStopwatch(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    val hours = t / 3_600_000L
    val minutes = t / 60_000L % 60L
    val seconds = t / 1_000L % 60L
    val centis = t / 10L % 100L
    return if (hours > 0L) {
        String.format(Locale.FRANCE, "%d:%02d:%02d,%02d", hours, minutes, seconds, centis)
    } else {
        String.format(Locale.FRANCE, "%02d:%02d,%02d", minutes, seconds, centis)
    }
}

/** Remaining time rounded up to the second, so "00:00" only shows once the countdown is over. */
internal fun formatCountdown(ms: Long, showHours: Boolean): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds / 60L % 60L
    val seconds = totalSeconds % 60L
    return if (showHours || hours > 0L) {
        String.format(Locale.FRANCE, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.FRANCE, "%02d:%02d", minutes, seconds)
    }
}
