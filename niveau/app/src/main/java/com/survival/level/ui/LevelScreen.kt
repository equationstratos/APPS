package com.survival.level.ui

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun LevelScreen(tiltX: Float, tiltY: Float, hasAccelerometer: Boolean) {
    val bgColor = Color(0xFF1A1A2E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!hasAccelerometer) {
            Text(
                text = "Aucun accéléromètre\ndétecté",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            return@Box
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            val isLevel = abs(tiltX) < 5f && abs(tiltY) < 5f
            val statusColor = if (isLevel) Color(0xFF00D084) else Color(0xFF4A9FFF)
            val statusText = if (isLevel) "NIVEAU" else "INCLINÉ"

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Canvas(
                modifier = Modifier.size(280.dp)
            ) {
                drawLevelBubble(tiltX, tiltY, isLevel)
            }

            Text(
                text = "X: ${tiltX.toInt()}°  Y: ${tiltY.toInt()}°",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

private fun DrawScope.drawLevelBubble(tiltX: Float, tiltY: Float, isLevel: Boolean) {
    val center = Offset(size.width / 2, size.height / 2)
    val outerRadius = size.minDimension / 2 - 20f
    val bubbleRadius = 30f

    // Background circle
    drawCircle(
        color = Color(0xFF16213E),
        radius = outerRadius,
        center = center
    )

    // Border circle
    drawCircle(
        color = if (isLevel) Color(0xFF00D084) else Color(0xFF4A9FFF),
        radius = outerRadius,
        center = center,
        style = Stroke(width = 4f)
    )

    // Grid lines (horizontal & vertical)
    val gridSpacing = outerRadius / 2
    for (i in -1..1) {
        if (i != 0) {
            val offset = i * gridSpacing
            drawLine(
                color = Color(0xFF0F3460).copy(alpha = 0.3f),
                start = Offset(center.x + offset, center.y - outerRadius),
                end = Offset(center.x + offset, center.y + outerRadius),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0xFF0F3460).copy(alpha = 0.3f),
                start = Offset(center.x - outerRadius, center.y + offset),
                end = Offset(center.x + outerRadius, center.y + offset),
                strokeWidth = 1f
            )
        }
    }

    // Center target
    drawCircle(
        color = Color(0xFF0F3460).copy(alpha = 0.5f),
        radius = 8f,
        center = center
    )

    // Bubble position based on tilt
    // Normalize tilt to position (-1 to 1)
    val bubbleX = (tiltX / 45f).coerceIn(-1f, 1f) * (outerRadius - bubbleRadius)
    val bubbleY = (tiltY / 45f).coerceIn(-1f, 1f) * (outerRadius - bubbleRadius)

    val bubbleCenter = Offset(center.x + bubbleX, center.y + bubbleY)

    // Bubble shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.2f),
        radius = bubbleRadius + 2f,
        center = Offset(bubbleCenter.x + 2f, bubbleCenter.y + 2f)
    )

    // Main bubble
    drawCircle(
        color = if (isLevel) Color(0xFF00D084) else Color(0xFF4A9FFF),
        radius = bubbleRadius,
        center = bubbleCenter
    )

    // Bubble highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = bubbleRadius / 3,
        center = Offset(bubbleCenter.x - bubbleRadius / 3, bubbleCenter.y - bubbleRadius / 3)
    )

    // Degree scale around circle
    for (angle in 0 until 360 step 10) {
        val rad = Math.toRadians(angle.toDouble())
        val isMajor = angle % 30 == 0
        val tickLen = if (isMajor) 15f else 8f
        val outerX = center.x + (outerRadius - 10f) * sin(rad).toFloat()
        val outerY = center.y - (outerRadius - 10f) * cos(rad).toFloat()
        val innerX = center.x + (outerRadius - 10f - tickLen) * sin(rad).toFloat()
        val innerY = center.y - (outerRadius - 10f - tickLen) * cos(rad).toFloat()

        drawLine(
            color = if (isMajor) Color(0xFF4A9FFF) else Color(0xFF0F3460),
            start = Offset(innerX, innerY),
            end = Offset(outerX, outerY),
            strokeWidth = if (isMajor) 2f else 1f
        )
    }
}
