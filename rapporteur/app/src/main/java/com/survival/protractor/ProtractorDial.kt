package com.survival.protractor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val LabelStyle = TextStyle(color = TextSecondary, fontSize = 11.sp, fontFeatureSettings = "tnum")

/** Unit vector on screen for a dial angle: 0° points to the bottom edge, angles grow clockwise. */
private fun dialDirection(degrees: Float): Offset {
    val rad = degrees * (PI.toFloat() / 180f)
    return Offset(-sin(rad), cos(rad))
}

/** Full 360° dial fixed to the phone, with a plumb line that always hangs towards gravity. */
@Composable
internal fun ProtractorDial(
    measurement: Measurement?,
    reference: Reference?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labels = remember(textMeasurer) {
        (0 until 360 step 30).map { deg -> deg to textMeasurer.measure(deg.toString(), style = LabelStyle) }
    }
    val delta = if (measurement != null && reference != null) measurement.deltaFrom(reference) else null

    Canvas(modifier.semantics { contentDescription = "Cadran du rapporteur" }) {
        val c = center
        val r = size.minDimension / 2f - 2.dp.toPx()
        drawCircle(color = Card, radius = r, center = c)
        drawCircle(color = Navy, radius = r, center = c, style = Stroke(width = 3.dp.toPx()))
        drawTicks(c, r)
        labels.forEach { (deg, layout) ->
            val half = max(layout.size.width, layout.size.height) / 2f
            val p = c + dialDirection(deg.toFloat()) * (r - 22.dp.toPx() - half)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }

        val needleLength = (r - 46.dp.toPx()).coerceAtLeast(r * 0.4f)
        if (measurement != null && measurement.upright) {
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(c.x, c.y - needleLength),
                end = Offset(c.x, c.y + needleLength),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            )
            if (delta != null && reference != null) {
                drawAngleArc(c, needleLength * 0.6f, reference.value, delta, Warning)
                drawLine(
                    color = Warning,
                    start = c,
                    end = c + dialDirection(reference.value) * needleLength,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                )
            } else {
                val line = measurement.lineAngle
                drawAngleArc(c, needleLength * 0.6f, measurement.dialAngle - line, line, Accent)
            }
        }

        when {
            measurement == null -> Unit
            measurement.isLevel -> drawCircle(
                color = Success,
                radius = 14.dp.toPx(),
                center = c,
                style = Stroke(width = 3.dp.toPx())
            )
            else -> {
                val tip = c + dialDirection(measurement.dialAngle) * needleLength
                drawLine(Accent, c, tip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color = Accent, radius = 9.dp.toPx(), center = tip)
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = tip)
            }
        }
        drawCircle(color = Color.White, radius = 6.dp.toPx(), center = c)
    }
}

private fun DrawScope.drawTicks(c: Offset, r: Float) {
    for (deg in 0 until 360 step 5) {
        val major = deg % 30 == 0
        val mid = deg % 10 == 0
        val length = when {
            major -> 14.dp
            mid -> 9.dp
            else -> 5.dp
        }.toPx()
        val dir = dialDirection(deg.toFloat())
        drawLine(
            color = when {
                major -> Accent
                mid -> Color.White.copy(alpha = 0.6f)
                else -> Color.White.copy(alpha = 0.3f)
            },
            start = c + dir * (r - length),
            end = c + dir * (r - 1.5.dp.toPx()),
            strokeWidth = if (major) 2.5.dp.toPx() else 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/** Filled sector from dial angle [from] sweeping [sweep] degrees (positive = clockwise on screen). */
private fun DrawScope.drawAngleArc(c: Offset, radius: Float, from: Float, sweep: Float, color: Color) {
    if (abs(sweep) < 0.2f) return
    drawArc(
        color = color.copy(alpha = 0.25f),
        // Canvas angles start at 3 o'clock; dial 0° is at 6 o'clock.
        startAngle = 90f + from,
        sweepAngle = sweep,
        useCenter = true,
        topLeft = Offset(c.x - radius, c.y - radius),
        size = Size(radius * 2f, radius * 2f)
    )
}
