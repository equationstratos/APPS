package com.survival.sunmoon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val CardShape = RoundedCornerShape(16.dp)
internal val InnerShape = RoundedCornerShape(12.dp)
internal val ButtonShape = RoundedCornerShape(12.dp)
internal val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
internal fun SurvieCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun CardTitle(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp, modifier = modifier)
}

@Composable
internal fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Bg,
            disabledContainerColor = Navy,
            disabledContentColor = TextSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Navy,
            contentColor = Color.White,
            disabledContainerColor = Navy.copy(alpha = 0.5f),
            disabledContentColor = TextSecondary.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

/** Label on the left, value on the right; both wrap on narrow screens or large fonts. */
@Composable
internal fun InfoRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = valueColor,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            style = Tabular,
            modifier = Modifier.weight(1.2f),
        )
    }
}

/** Highlighted event of the day: label, clock time(s) and a detail line. */
@Composable
internal fun EventTile(label: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Navy, InnerShape)
            .padding(12.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = Color.White,
            fontSize = if (value.length > 6) 18.sp else 26.sp,
            lineHeight = if (value.length > 6) 23.sp else 32.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular,
        )
        if (detail != null) {
            Spacer(Modifier.height(2.dp))
            Text(detail, color = TextSecondary, fontSize = 13.sp, lineHeight = 17.sp, style = Tabular)
        }
    }
}

/** Card with a coloured title, an explanation and an optional action button. */
@Composable
internal fun NoticeCard(
    title: String,
    message: String,
    titleColor: Color = Warning,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Navy, InnerShape)
            .padding(12.dp)
    ) {
        Text(title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(message, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
        if (actionLabel != null) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

/** Chevron button for the previous or next day, drawn so no icon library is needed. */
@Composable
internal fun ChevronButton(pointsLeft: Boolean, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        val color = if (enabled) Accent else TextSecondary.copy(alpha = 0.35f)
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (pointsLeft) {
                    moveTo(w * 0.62f, h * 0.18f)
                    lineTo(w * 0.30f, h * 0.5f)
                    lineTo(w * 0.62f, h * 0.82f)
                } else {
                    moveTo(w * 0.38f, h * 0.18f)
                    lineTo(w * 0.70f, h * 0.5f)
                    lineTo(w * 0.38f, h * 0.82f)
                }
            }
            drawPath(path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Small coloured square and its label, for chart legends. */
@Composable
internal fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.size(6.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}
