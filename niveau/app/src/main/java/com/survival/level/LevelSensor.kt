package com.survival.level

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class LevelSensor(
    private val sensorManager: SensorManager,
    private val onTiltChanged: (Float, Float) -> Unit
) : SensorEventListener {

    private val accelerometerReading = FloatArray(3)
    private val alpha = 0.2f
    private var lastX = 0f
    private var lastY = 0f

    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            for (i in event.values.indices) {
                accelerometerReading[i] = alpha * event.values[i] + (1 - alpha) * accelerometerReading[i]
            }

            val x = accelerometerReading[0]
            val y = accelerometerReading[1]
            val z = accelerometerReading[2]

            val magnitude = sqrt(x * x + y * y + z * z)
            val normalizedX = if (magnitude > 0) x / magnitude else 0f
            val normalizedY = if (magnitude > 0) y / magnitude else 0f

            // Angles en degrés
            val tiltX = Math.toDegrees(atan2(normalizedY, sqrt(normalizedX * normalizedX + (z / magnitude) * (z / magnitude))).toDouble()).toFloat()
            val tiltY = Math.toDegrees(atan2(normalizedX, sqrt(normalizedY * normalizedY + (z / magnitude) * (z / magnitude))).toDouble()).toFloat()

            if (abs(tiltX - lastX) > 1f || abs(tiltY - lastY) > 1f) {
                lastX = tiltX
                lastY = tiltY
                onTiltChanged(tiltX, tiltY)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
