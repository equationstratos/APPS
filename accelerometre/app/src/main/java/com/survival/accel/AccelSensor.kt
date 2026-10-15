package com.survival.accel

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Time constant of the low-pass gravity estimate used when no linear-acceleration sensor exists. */
private const val GRAVITY_TIME_CONSTANT_S = 0.25f

/** Beyond this gap between events the gravity estimate is stale and is re-seeded. */
private const val MAX_FILTER_GAP_S = 1f

/**
 * Listens to [sensor] while the screen is resumed. When [highPass] is true, gravity is removed in
 * software from raw accelerometer readings.
 */
@Composable
internal fun AccelerationSubscription(
    sensorManager: SensorManager,
    sensor: Sensor,
    highPass: Boolean,
    onSample: (timestampNs: Long, x: Float, y: Float, z: Float) -> Unit,
    onAccuracyChanged: (accuracy: Int) -> Unit,
    onRegistered: (success: Boolean) -> Unit,
) {
    val currentOnSample by rememberUpdatedState(onSample)
    val currentOnAccuracyChanged by rememberUpdatedState(onAccuracyChanged)
    val currentOnRegistered by rememberUpdatedState(onRegistered)

    LifecycleResumeEffect(sensor, highPass) {
        val listener = AccelerationListener(
            highPass = highPass,
            onSample = { t, x, y, z -> currentOnSample(t, x, y, z) },
            onAccuracy = { currentOnAccuracyChanged(it) }
        )
        val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        currentOnRegistered(registered)
        onPauseOrDispose { sensorManager.unregisterListener(listener) }
    }
}

private class AccelerationListener(
    private val highPass: Boolean,
    private val onSample: (Long, Float, Float, Float) -> Unit,
    private val onAccuracy: (Int) -> Unit,
) : SensorEventListener {
    private val gravity = FloatArray(3)
    private var lastTimestampNs = 0L
    private var primed = false

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size < 3 || !v[0].isFinite() || !v[1].isFinite() || !v[2].isFinite()) return
        if (!highPass) {
            onSample(event.timestamp, v[0], v[1], v[2])
            return
        }
        val dt = (event.timestamp - lastTimestampNs) / 1_000_000_000f
        if (!primed || dt <= 0f || dt > MAX_FILTER_GAP_S) {
            v.copyInto(gravity, endIndex = 3)
            primed = true
        } else {
            val alpha = dt / (GRAVITY_TIME_CONSTANT_S + dt)
            for (i in 0 until 3) gravity[i] += alpha * (v[i] - gravity[i])
        }
        lastTimestampNs = event.timestamp
        onSample(event.timestamp, v[0] - gravity[0], v[1] - gravity[1], v[2] - gravity[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        onAccuracy(accuracy)
    }
}
