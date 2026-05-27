package com.survival.sound.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BACKGROUND_COLOR = Color(0xFF1A1A2E)
private val ACCENT_BLUE = Color(0xFF4A9FFF)
private val DARK_NAVY = Color(0xFF0F3460)
private val TEXT_COLOR = Color.White

@Composable
fun SoundLevelScreen(
    currentLevel: Float,
    minLevel: Float,
    maxLevel: Float,
    averageLevel: Float,
    isRecording: Boolean,
    hasPermission: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BACKGROUND_COLOR)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Sonomètre",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ACCENT_BLUE
            )

            // Current Level Display
            LevelDisplay(currentLevel = currentLevel)

            // Animated Level Bar
            LevelBar(currentLevel = currentLevel, maxLevel = 140f)

            Spacer(modifier = Modifier.height(8.dp))

            // Stats Grid
            StatsGrid(minLevel = minLevel, maxLevel = maxLevel, averageLevel = averageLevel)

            Spacer(modifier = Modifier.height(8.dp))

            // Reference Scale
            ReferenceScale()

            Spacer(modifier = Modifier.height(8.dp))

            // Permission and Recording Status
            if (!hasPermission) {
                Text(
                    text = "Audio permission required",
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Control Buttons
            ControlButtons(
                isRecording = isRecording,
                hasPermission = hasPermission,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onReset = onReset
            )
        }
    }
}

@Composable
private fun LevelDisplay(currentLevel: Float) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .background(DARK_NAVY, shape = androidx.compose.foundation.shape.CircleShape)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = String.format("%.1f", currentLevel),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = ACCENT_BLUE
            )
            Text(
                text = "dB",
                fontSize = 20.sp,
                color = TEXT_COLOR
            )
        }
    }
}

@Composable
private fun LevelBar(currentLevel: Float, maxLevel: Float) {
    val normalizedLevel = (currentLevel / maxLevel).coerceIn(0f, 1f)
    val animatedLevel by animateFloatAsState(
        targetValue = normalizedLevel,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "levelAnimation"
    )

    val barColor by animateColorAsState(
        targetValue = when {
            currentLevel < 50 -> ACCENT_BLUE
            currentLevel < 80 -> Color(0xFF7ED321)
            currentLevel < 100 -> Color(0xFFFFD700)
            else -> Color(0xFFFF6B6B)
        },
        animationSpec = tween(durationMillis = 300),
        label = "colorAnimation"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sound Level",
            color = TEXT_COLOR,
            fontSize = 14.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(DARK_NAVY, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedLevel)
                    .fillMaxSize()
                    .background(barColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun StatsGrid(minLevel: Float, maxLevel: Float, averageLevel: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                label = "Min",
                value = if (minLevel == 0f) "-" else String.format("%.1f", minLevel),
                modifier = Modifier.weight(1f)
            )
            StatBox(
                label = "Max",
                value = if (maxLevel == 0f) "-" else String.format("%.1f", maxLevel),
                modifier = Modifier.weight(1f)
            )
        }
        StatBox(
            label = "Average",
            value = if (averageLevel == 0f) "-" else String.format("%.1f", averageLevel),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(DARK_NAVY, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = TEXT_COLOR,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                text = value,
                color = ACCENT_BLUE,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReferenceScale() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DARK_NAVY, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Reference Scale",
            color = ACCENT_BLUE,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        ReferenceItem("Whisper", "~30 dB")
        ReferenceItem("Conversation", "~60 dB")
        ReferenceItem("Traffic", "~80 dB")
        ReferenceItem("Concert", "~110 dB")
    }
}

@Composable
private fun ReferenceItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TEXT_COLOR,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = ACCENT_BLUE,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ControlButtons(
    isRecording: Boolean,
    hasPermission: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isRecording) {
            Button(
                onClick = onStopRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B6B)
                )
            ) {
                Text("Stop Recording", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onStartRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ACCENT_BLUE
                ),
                enabled = hasPermission
            ) {
                Text("Start Recording", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BACKGROUND_COLOR)
            }
        }
        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DARK_NAVY,
                contentColor = ACCENT_BLUE
            )
        ) {
            Text("Reset", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
