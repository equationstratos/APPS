package com.survival.pedometer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** Step sources in order of preference. */
enum class TrackingMode(val sensorType: Int?, val usesStepSensor: Boolean, val samplingPeriodUs: Int) {
    StepCounter(Sensor.TYPE_STEP_COUNTER, true, SensorManager.SENSOR_DELAY_UI),
    StepDetector(Sensor.TYPE_STEP_DETECTOR, true, SensorManager.SENSOR_DELAY_UI),
    Accelerometer(Sensor.TYPE_ACCELEROMETER, false, SensorManager.SENSOR_DELAY_GAME),
    Unavailable(null, false, SensorManager.SENSOR_DELAY_UI);

    companion object {
        fun detect(sensorManager: SensorManager?): TrackingMode =
            TrackingMode.entries.firstOrNull { mode ->
                mode.sensorType?.let { type -> sensorManager?.getDefaultSensor(type) } != null
            } ?: Unavailable
    }
}

class StepTracker(
    private val sensorManager: SensorManager?,
    private val mode: TrackingMode,
    private val store: StepStore,
    private val onStepsChanged: (Int) -> Unit,
) : SensorEventListener {

    private val peakDetector = PeakStepDetector()
    private var listening = false

    fun start() {
        refresh()
        if (listening) return
        val manager = sensorManager ?: return
        val sensor = mode.sensorType?.let { type -> manager.getDefaultSensor(type) } ?: return
        listening = manager.registerListener(this, sensor, mode.samplingPeriodUs)
    }

    fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
        peakDetector.reset()
    }

    fun refresh() {
        onStepsChanged(store.todaySteps())
    }

    override fun onSensorChanged(event: SensorEvent) {
        val steps = when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> store.onStepCounter(event.values[0].toLong())
            Sensor.TYPE_STEP_DETECTOR -> store.addSteps(1)
            Sensor.TYPE_ACCELEROMETER -> {
                val values = event.values
                if (!peakDetector.isStep(values[0], values[1], values[2], event.timestamp)) return
                store.addSteps(1)
            }
            else -> return
        }
        onStepsChanged(steps)
    }

    // Step counts do not depend on sensor calibration.
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Accelerometer fallback: low-pass filtered magnitude minus a slowly tracked gravity level,
 * with a hysteresis threshold and a refractory period between steps.
 */
internal class PeakStepDetector {
    private var smoothed = Float.NaN
    private var gravity = Float.NaN
    private var armed = true
    private var lastStepNs = 0L

    fun isStep(x: Float, y: Float, z: Float, timestampNs: Long): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!magnitude.isFinite()) return false
        if (smoothed.isNaN() || gravity.isNaN()) {
            smoothed = magnitude
            gravity = magnitude
            return false
        }
        smoothed += SMOOTHING * (magnitude - smoothed)
        gravity += GRAVITY_TRACKING * (magnitude - gravity)
        val signal = smoothed - gravity
        if (armed && signal > HIGH_THRESHOLD) {
            armed = false
            if (timestampNs - lastStepNs >= MIN_STEP_INTERVAL_NS) {
                lastStepNs = timestampNs
                return true
            }
        } else if (!armed && signal < LOW_THRESHOLD) {
            armed = true
        }
        return false
    }

    fun reset() {
        smoothed = Float.NaN
        gravity = Float.NaN
        armed = true
    }

    private companion object {
        const val SMOOTHING = 0.25f
        const val GRAVITY_TRACKING = 0.02f
        const val HIGH_THRESHOLD = 1.2f
        const val LOW_THRESHOLD = 0.4f
        const val MIN_STEP_INTERVAL_NS = 250_000_000L
    }
}
