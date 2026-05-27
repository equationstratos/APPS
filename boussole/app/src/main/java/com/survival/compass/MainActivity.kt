package com.survival.compass

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.survival.compass.ui.CompassScreen

class MainActivity : ComponentActivity() {

    private var compassSensor: CompassSensor? = null
    private var azimuth by mutableFloatStateOf(0f)
    private var hasCompass = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
            hasCompass = false
        } else {
            compassSensor = CompassSensor(sensorManager) { newAzimuth ->
                azimuth = newAzimuth
            }
        }

        setContent {
            CompassScreen(azimuth = azimuth, hasCompass = hasCompass)
        }
    }

    override fun onResume() {
        super.onResume()
        compassSensor?.start()
    }

    override fun onPause() {
        super.onPause()
        compassSensor?.stop()
    }
}
