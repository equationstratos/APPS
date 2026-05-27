package com.survival.compass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassScreen(azimuth: Float, hasCompass: Boolean) {
    val bgColor = Color(0xFF1A1A2E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!hasCompass) {
            Text(
                text = "Aucun capteur magnétique\ndétecté sur cet appareil",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            return@Box
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val direction = getCardinalDirection(azimuth)
            Text(
                text = direction,
                color = Color(0xFF4A9FFF),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${azimuth.toInt()}°",
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Canvas(
                modifier = Modifier.size(300.dp)
            ) {
                drawCompassRose(azimuth)
            }
        }
    }
}

private fun DrawScope.drawCompassRose(azimuth: Float) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2 - 20f

    // Outer circle
    drawCircle(
        color = Color(0xFF16213E),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color(0xFF0F3460),
        radius = radius,
        center = center,
        style = Stroke(width = 3f)
    )

    rotate(-azimuth, pivot = center) {
        // Tick marks
        for (i in 0 until 360 step 5) {
            val rad = Math.toRadians(i.toDouble())
            val isMajor = i % 30 == 0
            val isCardinal = i % 90 == 0
            val tickLen = when {
                isCardinal -> 30f
                isMajor -> 20f
                else -> 10f
            }
            val outerX = center.x + (radius - 5f) * sin(rad).toFloat()
            val outerY = center.y - (radius - 5f) * cos(rad).toFloat()
            val innerX = center.x + (radius - 5f - tickLen) * sin(rad).toFloat()
            val innerY = center.y - (radius - 5f - tickLen) * cos(rad).toFloat()

            val tickColor = when {
                isCardinal -> Color(0xFF4A9FFF)
                isMajor -> Color(0xFF0F3460)
                else -> Color(0xFF0F3460).copy(alpha = 0.5f)
            }
            drawLine(
                color = tickColor,
                start = Offset(innerX, innerY),
                end = Offset(outerX, outerY),
                strokeWidth = if (isCardinal) 3f else 1.5f,
                cap = StrokeCap.Round
            )
        }

        // Cardinal labels
        val labels = listOf(
            Triple(0, "N", Color(0xFF4A9FFF)),
            Triple(90, "E", Color.White),
            Triple(180, "S", Color.White),
            Triple(270, "O", Color.White)
        )
        for ((deg, label, color) in labels) {
            val rad = Math.toRadians(deg.toDouble())
            val labelRadius = radius - 55f
            val x = center.x + labelRadius * sin(rad).toFloat()
            val y = center.y - labelRadius * cos(rad).toFloat()

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(
                        (color.alpha * 255).toInt(),
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt()
                    )
                    textSize = if (label == "N") 48f else 36f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = label == "N"
                    isAntiAlias = true
                }
                drawText(label, x, y + paint.textSize / 3, paint)
            }
        }

        // North needle (red triangle pointing up)
        val needleWidth = 20f
        val northPath = Path().apply {
            moveTo(center.x, center.y - radius + 70f)
            lineTo(center.x - needleWidth, center.y)
            lineTo(center.x + needleWidth, center.y)
            close()
        }
        drawPath(northPath, color = Color(0xFF4A9FFF))

        // South needle (dark triangle pointing down)
        val southPath = Path().apply {
            moveTo(center.x, center.y + radius - 70f)
            lineTo(center.x - needleWidth, center.y)
            lineTo(center.x + needleWidth, center.y)
            close()
        }
        drawPath(southPath, color = Color(0xFF0F3460))
    }

    // Center dot
    drawCircle(color = Color(0xFF4A9FFF), radius = 8f, center = center)
    drawCircle(color = Color(0xFF1A1A2E), radius = 4f, center = center)
}

private fun getCardinalDirection(azimuth: Float): String {
    return when {
        azimuth >= 337.5f || azimuth < 22.5f -> "NORD"
        azimuth < 67.5f -> "NORD-EST"
        azimuth < 112.5f -> "EST"
        azimuth < 157.5f -> "SUD-EST"
        azimuth < 202.5f -> "SUD"
        azimuth < 247.5f -> "SUD-OUEST"
        azimuth < 292.5f -> "OUEST"
        else -> "NORD-OUEST"
    }
}
