package com.survival.compass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class CompassSensor(
    private val sensorManager: SensorManager,
    private val onAzimuthChanged: (Float) -> Unit
) : SensorEventListener {

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var lastAzimuth = 0f

    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lowPassFilter(event.values, accelerometerReading)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lowPassFilter(event.values, magnetometerReading)
            }
        }

        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f

            if (abs(azimuth - lastAzimuth) > 0.5f) {
                lastAzimuth = azimuth
                onAzimuthChanged(azimuth)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lowPassFilter(input: FloatArray, output: FloatArray) {
        val alpha = 0.15f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
    }
}
