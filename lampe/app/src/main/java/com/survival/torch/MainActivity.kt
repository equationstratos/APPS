package com.survival.torch

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize camera manager for flash control
        cameraManager = getSystemService(CAMERA_SERVICE) as? CameraManager
        try {
            val cameraIds = cameraManager?.cameraIdList
            if (!cameraIds.isNullOrEmpty()) {
                cameraId = cameraIds[0]
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        val hasFlash = cameraId != null

        setContent {
            TorchApp(
                hasFlash = hasFlash,
                onToggleFlash = { enabled -> setFlash(enabled) },
                onScreenLight = { enabled -> setScreenLight(enabled) }
            )
        }
    }

    private fun setFlash(enabled: Boolean) {
        cameraId?.let {
            try {
                cameraManager?.setTorchMode(it, enabled)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    private fun setScreenLight(enabled: Boolean) {
        if (enabled) {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
        } else {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    override fun onPause() {
        super.onPause()
        setFlash(false)
        setScreenLight(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        setFlash(false)
        setScreenLight(false)
    }
}

@Composable
fun TorchApp(
    hasFlash: Boolean,
    onToggleFlash: (Boolean) -> Unit,
    onScreenLight: (Boolean) -> Unit
) {
    val bgColor = Color(0xFF1A1A2E)
    val accentBlue = Color(0xFF4A9FFF)
    val darkNavy = Color(0xFF0F3460)

    var currentMode by remember { mutableIntStateOf(0) } // 0: Normal, 1: SOS, 2: Strobe, 3: Screen
    var isFlashOn by remember { mutableStateOf(false) }
    var strobeSpeed by remember { mutableIntStateOf(500) } // ms

    val modeNames = listOf("Normal", "SOS", "Strobe", "Screen")

    // SOS pattern: ... --- ...
    LaunchedEffect(currentMode, isFlashOn) {
        if (currentMode == 1 && isFlashOn) {
            // SOS: dot=200ms, dash=600ms, gap between signals=200ms, gap between letters=600ms, gap between words=1400ms
            while (isFlashOn) {
                // S (three dots)
                repeat(3) {
                    onToggleFlash(true)
                    delay(200) // dot
                    onToggleFlash(false)
                    delay(200) // gap between signals
                }
                delay(400) // gap between letters (600 - 200 already used)

                // O (three dashes)
                repeat(3) {
                    onToggleFlash(true)
                    delay(600) // dash
                    onToggleFlash(false)
                    delay(200) // gap between signals
                }
                delay(400) // gap between letters

                // S (three dots)
                repeat(3) {
                    onToggleFlash(true)
                    delay(200) // dot
                    onToggleFlash(false)
                    delay(200) // gap between signals
                }

                delay(1400) // gap between words
            }
            onToggleFlash(false)
        }
    }

    // Strobe pattern
    LaunchedEffect(currentMode, isFlashOn, strobeSpeed) {
        if (currentMode == 2 && isFlashOn) {
            while (isFlashOn) {
                onToggleFlash(true)
                delay(strobeSpeed / 2L)
                onToggleFlash(false)
                delay(strobeSpeed / 2L)
            }
            onToggleFlash(false)
        }
    }

    // Screen light
    LaunchedEffect(currentMode, isFlashOn) {
        if (currentMode == 3) {
            onScreenLight(isFlashOn)
        } else {
            onScreenLight(false)
        }
    }

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
                    .clickable {
                        when (currentMode) {
                            0 -> isFlashOn = !isFlashOn
                            1, 2 -> {
                                isFlashOn = !isFlashOn
                            }
                            3 -> isFlashOn = !isFlashOn
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFlashOn && currentMode in listOf(0, 1, 2, 3)) "ON" else "OFF",
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
                        onClick = {
                            isFlashOn = false
                            onToggleFlash(false)
                            currentMode = 0
                        },
                        modifier = Modifier.weight(1f),
                        accentBlue = accentBlue,
                        darkNavy = darkNavy
                    )
                    ModeButton(
                        text = "SOS",
                        isSelected = currentMode == 1,
                        onClick = {
                            isFlashOn = false
                            onToggleFlash(false)
                            currentMode = 1
                        },
                        modifier = Modifier.weight(1f),
                        accentBlue = accentBlue,
                        darkNavy = darkNavy
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
                        onClick = {
                            isFlashOn = false
                            onToggleFlash(false)
                            currentMode = 2
                        },
                        modifier = Modifier.weight(1f),
                        accentBlue = accentBlue,
                        darkNavy = darkNavy
                    )
                    ModeButton(
                        text = "Screen",
                        isSelected = currentMode == 3,
                        onClick = {
                            isFlashOn = false
                            onScreenLight(false)
                            currentMode = 3
                        },
                        modifier = Modifier.weight(1f),
                        accentBlue = accentBlue,
                        darkNavy = darkNavy
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
                        onValueChange = { strobeSpeed = it.toInt() },
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
    modifier: Modifier = Modifier,
    accentBlue: Color,
    darkNavy: Color
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
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
