package com.survival.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun toneColor(tone: Tone): Color = when (tone) {
    Tone.NEUTRAL -> TextSecondary
    Tone.ACCENT -> Accent
    Tone.SUCCESS -> Success
    Tone.WARNING -> Warning
}

/** Circular progress track with a big tabular value and a caption in its centre. */
@Composable
internal fun ValueRing(
    value: String,
    caption: String,
    progress: Float,
    tone: Tone,
    modifier: Modifier = Modifier,
    diameter: Dp = 220.dp,
) {
    val arcColor = toneColor(tone)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Navy,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = if (value == NO_VALUE) TextSecondary else Color.White,
                fontSize = 64.sp,
                lineHeight = 70.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
            )
            Text(
                text = caption,
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Live trace of the last [TRACE_WINDOW_S] seconds (one grid line per second), or [placeholder]. */
@Composable
internal fun WavePlot(wave: Wave?, placeholder: String, modifier: Modifier = Modifier) {
    val description = if (wave != null) "Courbe du pouls des 5 dernières secondes" else placeholder
    Box(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val grid = 1.dp.toPx()
            val seconds = TRACE_WINDOW_S.toInt()
            for (k in 0..seconds) {
                val x = size.width * k / seconds
                drawLine(color = Navy, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = grid)
            }
            val mid = size.height / 2f
            drawLine(color = Navy, start = Offset(0f, mid), end = Offset(size.width, mid), strokeWidth = grid)
            if (wave != null && wave.x.size >= 2) {
                val amplitude = mid * 0.85f
                val path = Path()
                for (i in wave.x.indices) {
                    val px = wave.x[i] * size.width
                    val py = mid - wave.y[i] * amplitude
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(
                    path = path,
                    color = Accent,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        if (wave == null) {
            Text(
                text = placeholder,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** Waste-bin glyph (the extended icon set is not available), drawn on a 24-unit grid. */
@Composable
internal fun TrashGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(4f * u, 6f * u), Offset(20f * u, 6f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
        val handle = Path().apply {
            moveTo(9f * u, 6f * u)
            lineTo(9f * u, 3.5f * u)
            lineTo(15f * u, 3.5f * u)
            lineTo(15f * u, 6f * u)
        }
        drawPath(handle, color, style = stroke)
        val body = Path().apply {
            moveTo(6f * u, 6f * u)
            lineTo(7f * u, 21f * u)
            lineTo(17f * u, 21f * u)
            lineTo(18f * u, 6f * u)
        }
        drawPath(body, color, style = stroke)
        drawLine(color, Offset(10f * u, 10f * u), Offset(10f * u, 17f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
        drawLine(color, Offset(14f * u, 10f * u), Offset(14f * u, 17f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
    }
}

/** Warning triangle glyph, drawn on a 24-unit grid. */
@Composable
internal fun WarningGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val u = size.width / 24f
        val triangle = Path().apply {
            moveTo(12f * u, 2.5f * u)
            lineTo(22.5f * u, 20.5f * u)
            lineTo(1.5f * u, 20.5f * u)
            close()
        }
        drawPath(triangle, Warning)
        drawPath(triangle, Warning, style = Stroke(width = 1.5f * u, join = StrokeJoin.Round))
        drawLine(Bg, Offset(12f * u, 8.5f * u), Offset(12f * u, 14f * u), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
        drawCircle(Bg, radius = 1.4f * u, center = Offset(12f * u, 17.4f * u))
    }
}
