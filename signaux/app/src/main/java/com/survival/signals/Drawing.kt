package com.survival.signals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val FlameOuter = Color(0xFFFFB020)
private val FlameInner = Color(0xFFFFF1B8)

/** Line drawing of a ground symbol, a silhouette or a fire layout, fitted in the largest centred square. */
@Composable
fun SketchCanvas(sketch: Sketch, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        fun at(p: Pt) = Offset(left + p.x * side, top + p.y * side)

        val ink = if (sketch.ground) Color.White else Accent
        if (sketch.ground) {
            drawRoundRect(
                color = Navy,
                topLeft = Offset(left, top),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * 0.1f, side * 0.1f)
            )
        }
        val width = sketch.weight * side
        val dash = PathEffect.dashPathEffect(floatArrayOf(side * 0.035f, side * 0.03f), 0f)
        for (guide in sketch.guides) {
            drawLine(
                color = TextSecondary,
                start = at(guide.a),
                end = at(guide.b),
                strokeWidth = max(1f, side * 0.012f),
                pathEffect = dash
            )
        }
        for (ghost in sketch.ghosts) {
            drawLine(ink.copy(alpha = 0.35f), at(ghost.a), at(ghost.b), strokeWidth = width, cap = StrokeCap.Round)
        }
        for (line in sketch.lines) {
            drawLine(ink, at(line.a), at(line.b), strokeWidth = width, cap = StrokeCap.Round)
        }
        for (head in sketch.heads) {
            drawCircle(ink, radius = head.radius * side, center = at(head.center))
        }
        for (flame in sketch.flames) {
            drawFlame(at(flame), side * 0.17f)
        }
    }
}

/** Stylised flame whose visual centre is [center]. */
private fun DrawScope.drawFlame(center: Offset, height: Float) {
    fun flame(h: Float, color: Color) {
        val w = h * 0.62f
        val baseY = center.y + h * 0.5f
        val tipY = center.y - h * 0.5f
        val x = center.x
        val path = Path().apply {
            moveTo(x, tipY)
            cubicTo(x + w * 0.12f, tipY + h * 0.3f, x + w * 0.6f, tipY + h * 0.42f, x + w * 0.48f, baseY - h * 0.2f)
            cubicTo(x + w * 0.4f, baseY + h * 0.02f, x - w * 0.4f, baseY + h * 0.02f, x - w * 0.48f, baseY - h * 0.2f)
            cubicTo(x - w * 0.6f, tipY + h * 0.42f, x - w * 0.12f, tipY + h * 0.3f, x, tipY)
            close()
        }
        drawPath(path, color)
    }
    flame(height, FlameOuter)
    flame(height * 0.5f, FlameInner)
}

/** Time diagram: solid bars for sounds or flashes, a rising and falling wave for sirens. */
@Composable
fun TimelineCanvas(timeline: Timeline, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = size.width / timeline.length
        val midY = size.height / 2f
        val barHeight = size.height * 0.62f
        val minWidth = 4.dp.toPx()
        drawLine(Navy, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 2.dp.toPx())
        for (span in timeline.spans) {
            val width = max(span.length * scale, minWidth)
            val x0 = min(span.start * scale, size.width - width)
            if (timeline.modulated && timeline.waveCycles > 0) {
                val steps = timeline.waveCycles * 24
                val path = Path()
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = x0 + t * width
                    val y = midY - sin(t * timeline.waveCycles * 2.0 * PI).toFloat() * barHeight / 2f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            } else {
                val radius = min(width, barHeight) / 2f
                drawRoundRect(
                    color = Accent,
                    topLeft = Offset(x0, midY - barHeight / 2f),
                    size = Size(width, barHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }
    }
}

/** Labels under a timeline, each centred on its time and kept inside the width. */
@Composable
fun TimelineMarks(timeline: Timeline, modifier: Modifier = Modifier) {
    if (timeline.marks.isEmpty()) return
    Layout(
        content = {
            timeline.marks.forEach { mark ->
                Text(mark.label, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val center = (timeline.marks[index].at / timeline.length * width).roundToInt()
                val x = (center - placeable.width / 2).coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

/* ---------- Small icons (the core icon set has no equivalent, so they are drawn) ---------- */

@Composable
fun BackArrowIcon(color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.1f
        drawLine(color, Offset(w * 0.18f, w * 0.5f), Offset(w * 0.84f, w * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, w * 0.5f), Offset(w * 0.46f, w * 0.22f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, w * 0.5f), Offset(w * 0.46f, w * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
fun SearchIcon(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.1f
        drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.42f, w * 0.42f), style = Stroke(width = stroke))
        drawLine(color, Offset(w * 0.63f, w * 0.63f), Offset(w * 0.86f, w * 0.86f), stroke, StrokeCap.Round)
    }
}

@Composable
fun ClearIcon(color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.1f
        drawLine(color, Offset(w * 0.22f, w * 0.22f), Offset(w * 0.78f, w * 0.78f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, w * 0.22f), Offset(w * 0.22f, w * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
fun ChevronIcon(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.12f
        drawLine(color, Offset(w * 0.36f, w * 0.18f), Offset(w * 0.68f, w * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, w * 0.5f), Offset(w * 0.36f, w * 0.82f), stroke, StrokeCap.Round)
    }
}

@Composable
fun InfoIcon(color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.09f
        drawCircle(color, radius = w * 0.42f, center = Offset(w / 2f, w / 2f), style = Stroke(width = stroke))
        drawCircle(color, radius = w * 0.06f, center = Offset(w / 2f, w * 0.3f))
        drawLine(color, Offset(w / 2f, w * 0.46f), Offset(w / 2f, w * 0.72f), stroke * 1.1f, StrokeCap.Round)
    }
}
