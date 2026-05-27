package com.survival.level

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.survival.level.ui.LevelScreen

class MainActivity : ComponentActivity() {

    private var levelSensor: LevelSensor? = null
    private var tiltX by mutableFloatStateOf(0f)
    private var tiltY by mutableFloatStateOf(0f)
    private var hasAccelerometer = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
            hasAccelerometer = false
        } else {
            levelSensor = LevelSensor(sensorManager) { newTiltX, newTiltY ->
                tiltX = newTiltX
                tiltY = newTiltY
            }
        }

        setContent {
            LevelScreen(tiltX = tiltX, tiltY = tiltY, hasAccelerometer = hasAccelerometer)
        }
    }

    override fun onResume() {
        super.onResume()
        levelSensor?.start()
    }

    override fun onPause() {
        super.onPause()
        levelSensor?.stop()
    }
}
