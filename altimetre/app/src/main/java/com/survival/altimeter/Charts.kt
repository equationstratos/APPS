package com.survival.altimeter

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val HOUR_MS = 3_600_000L

/** Readings further apart than this are not joined (phone asleep, logging off). */
private const val MAX_GAP_MS = 50L * 60_000L

private val GridLine = Color(0x33FFFFFF)

/** Arrow pointing right when steady, up when rising, down when falling; steeper when faster. */
@Composable
internal fun TrendArrow(trend: PressureTrend, color: Color, modifier: Modifier = Modifier) {
    val description = "Flèche de tendance$NBSP: ${trend.label.lowercase()}"
    Canvas(modifier.semantics { contentDescription = description }) {
        val stroke = size.minDimension * 0.11f
        val radius = size.minDimension * 0.38f
        rotate(trend.arrowDegrees) {
            val tip = Offset(center.x + radius, center.y)
            drawLine(
                color = color,
                start = Offset(center.x - radius, center.y),
                end = Offset(tip.x - stroke, center.y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            val head = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - radius * 0.75f, tip.y - radius * 0.6f)
                lineTo(tip.x - radius * 0.75f, tip.y + radius * 0.6f)
                close()
            }
            drawPath(head, color)
        }
    }
}

/**
 * Station pressure over the last [hours] hours. Each reading is a point; consecutive readings are
 * joined unless they are more than [MAX_GAP_MS] apart.
 */
@Composable
internal fun PressureChart(samples: List<PressureSample>, nowMs: Long, hours: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val spanMs = hours * HOUR_MS
    val visible = remember(samples, nowMs, hours) {
        samples.filter { it.timeMs >= nowMs - spanMs && it.timeMs <= nowMs + 60_000L }
    }
    val axis = remember(visible) { PressureAxis.of(visible.map { it.hPa }) }
    val segments = remember(visible) { PressureHistory.segments(visible, MAX_GAP_MS) }
    val tickHours = if (hours <= 24) 6 else 24
    val description = remember(visible, hours) {
        val low = visible.minOfOrNull { it.hPa }
        val high = visible.maxOfOrNull { it.hPa }
        if (low == null || high == null) {
            "Graphique de pression vide"
        } else {
            "Graphique de la pression sur $hours heures, de ${formatHpa(low)} à ${formatHpa(high)}"
        }
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        val scale = axis ?: return@Canvas
        val labelStyle = TextStyle(color = TextSecondary, fontSize = 11.sp, fontFeatureSettings = "tnum")
        val yLabels = scale.ticks.map { measurer.measure(formatNumber(it), labelStyle) }
        val xTicks = (0..hours / tickHours).map { it * tickHours }
        val xLabels = xTicks.map { offset ->
            measurer.measure(if (offset == hours) "0" else "$MINUS${hours - offset}${NBSP}h", labelStyle)
        }
        val gap = 4.dp.toPx()
        val labelHeight = yLabels.first().size.height.toFloat()
        val plotLeft = yLabels.maxOf { it.size.width } + 2f * gap
        val plotRight = size.width - 2.dp.toPx()
        val plotTop = labelHeight / 2f
        val plotBottom = size.height - labelHeight - gap
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun xOf(timeMs: Long): Float =
            plotLeft + ((timeMs - (nowMs - spanMs)).toFloat() / spanMs.toFloat()).coerceIn(0f, 1f) * plotWidth
        fun yOf(hPa: Double): Float = plotBottom - (scale.fraction(hPa) * plotHeight).toFloat()

        val gridStroke = 1.dp.toPx()
        scale.ticks.forEachIndexed { index, tick ->
            val y = yOf(tick)
            drawLine(GridLine, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = gridStroke)
            val label = yLabels[index]
            drawText(label, topLeft = Offset(plotLeft - gap - label.size.width, y - label.size.height / 2f))
        }
        xTicks.forEachIndexed { index, offset ->
            val x = plotLeft + plotWidth * offset / hours
            drawLine(GridLine, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = gridStroke)
            val label = xLabels[index]
            val left = (x - label.size.width / 2f).coerceIn(0f, maxOf(0f, size.width - label.size.width))
            drawText(label, topLeft = Offset(left, plotBottom + gap))
        }

        val lineStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dotRadius = 2.dp.toPx()
        for (segment in segments) {
            if (segment.size == 1) {
                drawCircle(Accent, radius = dotRadius, center = Offset(xOf(segment[0].timeMs), yOf(segment[0].hPa)))
                continue
            }
            val path = Path()
            segment.forEachIndexed { index, sample ->
                val x = xOf(sample.timeMs)
                val y = yOf(sample.hPa)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Accent, style = lineStroke)
        }
        visible.lastOrNull()?.let { last ->
            val point = Offset(xOf(last.timeMs), yOf(last.hPa))
            drawCircle(Color.White, radius = dotRadius * 2f, center = point)
            drawCircle(Accent, radius = dotRadius * 1.2f, center = point)
        }
    }
}
