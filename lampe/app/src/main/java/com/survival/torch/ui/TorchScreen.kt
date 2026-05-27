package com.survival.torch.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TorchScreen(
    isFlashOn: Boolean,
    currentMode: Int,
    strobeSpeed: Int,
    onToggleFlash: () -> Unit,
    onModeChange: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
    hasFlash: Boolean
) {
    val bgColor = Color(0xFF1A1A2E)
    val accentBlue = Color(0xFF4A9FFF)
    val darkNavy = Color(0xFF0F3460)

    val modeNames = listOf("Normal", "SOS", "Strobe", "Screen")
    
    val buttonColor by animateColorAsState(
        if (isFlashOn && currentMode == 0) accentBlue else darkNavy,
        label = "buttonColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!hasFlash) {
            Text(
                text = "Flash non disponible\nsur cet appareil",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            return@Box
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top display area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Lampe Torche",
                    color = accentBlue,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = modeNames[currentMode],
                    color = Color.White,
                    fontSize = 18.sp
                )
            }

            // Main toggle button (Large circular button)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .clickable { onToggleFlash() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFlashOn && currentMode == 0) "ON" else "OFF",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Mode selector buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Mode:",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // First row of mode buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        text = "Normal",
                        isSelected = currentMode == 0,
                        onClick = { onModeChange(0) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        text = "SOS",
                        isSelected = currentMode == 1,
                        onClick = { onModeChange(1) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Second row of mode buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        text = "Strobe",
                        isSelected = currentMode == 2,
                        onClick = { onModeChange(2) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        text = "Screen",
                        isSelected = currentMode == 3,
                        onClick = { onModeChange(3) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Strobe speed slider (only show in Strobe mode)
                if (currentMode == 2) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Speed: ${strobeSpeed}ms",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = strobeSpeed.toFloat(),
                        onValueChange = { onSpeedChange(it.toInt()) },
                        valueRange = 100f..1000f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = accentBlue,
                            activeTrackColor = accentBlue,
                            inactiveTrackColor = darkNavy
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentBlue = Color(0xFF4A9FFF)
    val darkNavy = Color(0xFF0F3460)
    val bgColor = Color(0xFF1A1A2E)

    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) accentBlue else darkNavy,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
