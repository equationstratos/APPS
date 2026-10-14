package com.survival.compass

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val MinorTick = Color.White.copy(alpha = 0.3f)

/**
 * Compass card turned by -[heading] under a fixed lubber line at the top. Labels stay upright;
 * [target] is drawn as a marker on the rim and turns green with the lubber line when [aligned].
 */
@Composable
fun CompassRose(heading: Float, target: Float?, aligned: Boolean, modifier: Modifier = Modifier) {
    // 12 labels per frame: keep them all in the layout cache.
    val textMeasurer = rememberTextMeasurer(16)
    Canvas(modifier) {
        val outer = size.minDimension / 2f
        if (outer <= 0f) return@Canvas
        val indexHeight = outer * 0.09f
        val radius = outer - indexHeight

        drawCircle(color = Card, radius = radius, center = center)
        drawCircle(color = Navy, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
        rotate(degrees = -heading, pivot = center) {
            drawTicks(radius)
            if (target != null) drawTargetMarker(target, radius, if (aligned) Success else Warning)
            drawNeedle(radius)
        }
        drawLabels(textMeasurer, radius, heading)
        drawLubberLine(radius, indexHeight, if (aligned) Success else Accent)
        drawCircle(color = Accent, radius = radius * 0.06f, center = center)
        drawCircle(color = Bg, radius = radius * 0.025f, center = center)
    }
}

/** Point at [distance] from the centre along compass [bearing] (0 = up, clockwise). */
private fun DrawScope.polar(bearing: Float, distance: Float): Offset {
    val rad = Math.toRadians(bearing.toDouble())
    return Offset(center.x + distance * sin(rad).toFloat(), center.y - distance * cos(rad).toFloat())
}

private fun DrawScope.drawTicks(radius: Float) {
    val outerEdge = radius * 0.95f
    for (deg in 0 until 360 step 5) {
        val cardinal = deg % 90 == 0
        val major = deg % 30 == 0
        val length = radius * when {
            cardinal -> 0.13f
            major -> 0.09f
            else -> 0.05f
        }
        val width = when {
            cardinal -> 3.dp
            major -> 2.dp
            else -> 1.dp
        }
        val color = when {
            deg == 0 -> Accent
            cardinal -> Color.White
            major -> TextSecondary
            else -> MinorTick
        }
        val bearing = deg.toFloat()
        drawLine(
            color = color,
            start = polar(bearing, outerEdge - length),
            end = polar(bearing, outerEdge),
            strokeWidth = width.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawTargetMarker(bearing: Float, radius: Float, color: Color) {
    val tip = polar(bearing, radius * 0.80f)
    val left = polar(bearing - 5f, radius * 0.98f)
    val right = polar(bearing + 5f, radius * 0.98f)
    val marker = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path = marker, color = color)
}

private fun DrawScope.drawNeedle(radius: Float) {
    val length = radius * 0.56f
    val half = radius * 0.075f
    val south = Path().apply {
        moveTo(center.x, center.y + length)
        lineTo(center.x + half, center.y)
        lineTo(center.x - half, center.y)
        close()
    }
    val north = Path().apply {
        moveTo(center.x, center.y - length)
        lineTo(center.x + half, center.y)
        lineTo(center.x - half, center.y)
        close()
    }
    drawPath(path = south, color = Color.White)
    drawPath(path = north, color = Danger)
}

private fun DrawScope.drawLabels(measurer: TextMeasurer, radius: Float, heading: Float) {
    val labelRadius = radius * 0.69f
    val cardinalStyle = TextStyle(color = Color.White, fontSize = (radius * 0.15f).toSp(), fontWeight = FontWeight.Bold)
    val northStyle = cardinalStyle.copy(color = Accent)
    val degreeStyle = TextStyle(
        color = TextSecondary,
        fontSize = (radius * 0.085f).toSp(),
        fontFeatureSettings = "tnum"
    )
    for (deg in 0 until 360 step 30) {
        val (text, style) = when (deg) {
            0 -> "N" to northStyle
            90 -> "E" to cardinalStyle
            180 -> "S" to cardinalStyle
            270 -> "O" to cardinalStyle
            else -> deg.toString() to degreeStyle
        }
        val layout = measurer.measure(text = text, style = style)
        val anchor = polar(deg - heading, labelRadius)
        drawText(
            layout,
            topLeft = Offset(anchor.x - layout.size.width / 2f, anchor.y - layout.size.height / 2f)
        )
    }
}

private fun DrawScope.drawLubberLine(radius: Float, height: Float, color: Color) {
    val ringTop = center.y - radius
    val half = height * 0.7f
    val index = Path().apply {
        moveTo(center.x - half, ringTop - height)
        lineTo(center.x + half, ringTop - height)
        lineTo(center.x, ringTop + 2.dp.toPx())
        close()
    }
    drawPath(path = index, color = color)
    drawLine(
        color = color,
        start = Offset(center.x, ringTop),
        end = Offset(center.x, center.y - radius * 0.80f),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
}
