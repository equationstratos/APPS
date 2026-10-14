package com.survival.torch

import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private lateinit var torch: TorchController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        torch = TorchController(getSystemService(CameraManager::class.java))
        setContent { SurvieTheme { AppScreen(torch) } }
    }

    override fun onDestroy() {
        // The steady torch must survive screen lock and configuration changes; only leaving the app turns it off.
        if (isFinishing) torch.setTorch(on = false)
        super.onDestroy()
    }
}
