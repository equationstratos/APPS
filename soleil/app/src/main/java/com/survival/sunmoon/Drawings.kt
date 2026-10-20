package com.survival.sunmoon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlin.math.roundToInt

/** Background of the altitude bands, shared by the chart and its legend. */
internal val DayBand = Accent.copy(alpha = 0.22f)
internal val CivilBand = Accent.copy(alpha = 0.12f)
internal val NauticalBand = Navy
internal val AstronomicalBand = Navy.copy(alpha = 0.55f)
internal val NightBand = Bg.copy(alpha = 0.7f)

private val MoonLight = Color(0xFFE8EDF5)
private val GridLine = Color.White.copy(alpha = 0.12f)

private class Band(val low: Double, val high: Double, val color: Color)

private val Bands = listOf(
    Band(0.0, 90.0, DayBand),
    Band(CIVIL_TWILIGHT, 0.0, CivilBand),
    Band(NAUTICAL_TWILIGHT, CIVIL_TWILIGHT, NauticalBand),
    Band(ASTRONOMICAL_TWILIGHT, NAUTICAL_TWILIGHT, AstronomicalBand),
    Band(-90.0, ASTRONOMICAL_TWILIGHT, NightBand),
)

/** « 30° », « 0° », « −70° ». */
private fun wholeDegrees(value: Double): String {
    val n = value.roundToInt()
    return if (n < 0) "$MINUS${-n}°" else "$n°"
}

/**
 * Altitude of the Sun's centre across the local day, over the twilight bands, with the horizon.
 * [now] (today only) is read while drawing, so the marker moves without recomposition.
 */
@Composable
internal fun SunChart(
    day: SunDay,
    latitude: Double,
    longitude: Double,
    now: (() -> Instant)?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val axis = remember(day) { altitudeAxis(day.curveAltitudes) }
    val ticks = remember(day) { hourTicks(day.window) }
    Canvas(modifier.semantics { contentDescription = description }) {
        val labelStyle = TextStyle(color = TextSecondary, fontSize = 11.sp, fontFeatureSettings = "tnum")
        val topLabel = measurer.measure(wholeDegrees(axis.max), labelStyle)
        val zeroLabel = measurer.measure(wholeDegrees(0.0), labelStyle)
        val bottomLabel = measurer.measure(wholeDegrees(axis.min), labelStyle)
        val hourLabels = ticks.map { measurer.measure(it.label, labelStyle) }
        val gap = 4.dp.toPx()
        val labelHeight = zeroLabel.size.height.toFloat()

        val plotLeft = maxOf(topLabel.size.width, zeroLabel.size.width, bottomLabel.size.width) + 2f * gap
        val plotRight = size.width - hourLabels.last().size.width / 2f
        val plotTop = 2f
        val plotBottom = size.height - labelHeight - gap
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun xOf(fraction: Double): Float = plotLeft + (fraction * plotWidth).toFloat()
        fun yOf(altitude: Double): Float = plotTop + (axis.fromTop(altitude) * plotHeight).toFloat()

        for (band in Bands) {
            val top = yOf(band.high)
            val bottom = yOf(band.low)
            if (bottom > top) {
                drawRect(band.color, topLeft = Offset(plotLeft, top), size = Size(plotWidth, bottom - top))
            }
        }

        ticks.forEachIndexed { index, tick ->
            val x = xOf(tick.fraction)
            drawLine(GridLine, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1.dp.toPx())
            val layout = hourLabels[index]
            val left = (x - layout.size.width / 2f).coerceIn(0f, maxOf(0f, size.width - layout.size.width))
            drawText(layout, topLeft = Offset(left, plotBottom + gap))
        }

        // Altitude labels: the horizon always, the extremes when they do not collide with it.
        val horizon = yOf(0.0)
        drawText(zeroLabel, topLeft = Offset(plotLeft - gap - zeroLabel.size.width, horizon - labelHeight / 2f))
        if (horizon - plotTop >= 1.6f * labelHeight) {
            drawText(topLabel, topLeft = Offset(plotLeft - gap - topLabel.size.width, plotTop))
        }
        if (plotBottom - horizon >= 1.6f * labelHeight) {
            drawText(bottomLabel, topLeft = Offset(plotLeft - gap - bottomLabel.size.width, plotBottom - labelHeight))
        }

        val span = day.window.endSeconds - day.window.startSeconds
        val curve = Path()
        val area = Path()
        for (i in day.curveTimes.indices) {
            val x = xOf((day.curveTimes[i] - day.window.startSeconds) / span)
            val y = yOf(day.curveAltitudes[i])
            if (i == 0) {
                curve.moveTo(x, y)
                area.moveTo(x, horizon)
                area.lineTo(x, y)
            } else {
                curve.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(xOf(1.0), horizon)
        area.close()
        clipRect(left = plotLeft, top = plotTop, right = plotRight, bottom = horizon) {
            drawPath(area, color = Accent.copy(alpha = 0.3f))
        }
        drawLine(
            Color.White.copy(alpha = 0.75f),
            Offset(plotLeft, horizon),
            Offset(plotRight, horizon),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawPath(curve, color = Accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val instant = now?.invoke()
        val fraction = instant?.let { dayFraction(day.window, it) }
        if (instant != null && fraction != null) {
            val x = xOf(fraction)
            drawLine(
                Color.White.copy(alpha = 0.6f),
                Offset(x, plotTop),
                Offset(x, plotBottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            val point = Offset(x, yOf(sunAltitude(instant.seconds(), latitude, longitude)))
            drawCircle(Bg, radius = 7.dp.toPx(), center = point)
            drawCircle(Color.White, radius = 5.dp.toPx(), center = point)
        }
    }
}

/** The Moon as seen in the sky: dark disc and lit part bounded by the terminator ellipse. */
@Composable
internal fun MoonDisc(fraction: Double, litOnRight: Boolean, description: String, modifier: Modifier = Modifier) {
    val outline = remember(fraction, litOnRight) { litOutline(fraction, litOnRight) }
    Canvas(modifier.semantics { contentDescription = description }) {
        val radius = size.minDimension / 2f
        if (radius <= 0f) return@Canvas
        val c = center
        drawCircle(Navy, radius = radius, center = c)
        val lit = Path()
        lit.moveTo(c.x + outline[0] * radius, c.y + outline[1] * radius)
        var i = 2
        while (i < outline.size) {
            lit.lineTo(c.x + outline[i] * radius, c.y + outline[i + 1] * radius)
            i += 2
        }
        lit.close()
        drawPath(lit, color = MoonLight)
        drawCircle(
            Color.White.copy(alpha = 0.3f),
            radius = radius - 0.5.dp.toPx(),
            center = c,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
