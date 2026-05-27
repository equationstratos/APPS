package com.survival.torch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.survival.torch.ui.TorchScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var flashManager: FlashManager? = null
    private var isFlashOn by mutableStateOf(false)
    private var currentMode by mutableIntStateOf(0) // 0: Normal, 1: SOS, 2: Strobe, 3: Screen
    private var strobeSpeed by mutableIntStateOf(500) // ms

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        flashManager = FlashManager(this)

        setContent {
            TorchScreen(
                isFlashOn = isFlashOn,
                currentMode = currentMode,
                strobeSpeed = strobeSpeed,
                onToggleFlash = { toggleFlash() },
                onModeChange = { newMode -> changeMode(newMode) },
                onSpeedChange = { newSpeed -> strobeSpeed = newSpeed },
                hasFlash = flashManager?.isFlashAvailable() ?: false
            )
        }
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        lifecycleScope.launch {
            if (currentMode == 0) {
                flashManager?.toggleFlash(isFlashOn)
            } else {
                // Stop any blinking pattern
                isFlashOn = false
                flashManager?.toggleFlash(false)
            }
        }
    }

    private fun changeMode(newMode: Int) {
        isFlashOn = false
        lifecycleScope.launch {
            flashManager?.toggleFlash(false)
            currentMode = newMode
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            isFlashOn = false
            flashManager?.toggleFlash(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        flashManager?.release()
    }
}
