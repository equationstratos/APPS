package com.survival.metal

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import kotlin.math.sqrt

/** Number of Δ samples kept for the scrolling graph (6 s at the publish rate). */
const val HISTORY_SIZE = 150

/** Accuracy value used until the sensor has reported anything. */
const val ACCURACY_UNKNOWN = Int.MIN_VALUE

private const val SMOOTHING = 0.25f
private const val CALIBRATION_MS = 2_000L
private const val PUBLISH_INTERVAL_MS = 40L

/**
 * Turns raw magnetometer events into a smoothed |B| (µT) and a baseline taken as the median of
 * the first two seconds. Sensor callbacks are delivered on the main looper, so the Compose state
 * below is only ever written from the main thread; it is published at 25 Hz at most.
 */
class MagnetDetector(restoredBaseline: Float = Float.NaN) : SensorEventListener {

    var magnitude by mutableFloatStateOf(Float.NaN)
        private set
    var baseline by mutableFloatStateOf(restoredBaseline)
        private set
    var calibrationProgress by mutableFloatStateOf(if (restoredBaseline.isFinite()) 1f else 0f)
        private set
    var accuracy by mutableIntStateOf(ACCURACY_UNKNOWN)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var startFailed by mutableStateOf(false)
        private set
    var history by mutableStateOf(FloatArray(0))
        private set

    val isCalibrated: Boolean get() = baseline.isFinite()

    /** |B| − baseline, NaN while calibrating or before the first reading. */
    val delta: Float get() = magnitude - baseline

    private var sensorManager: SensorManager? = null
    private var smoothed = Float.NaN
    private val calibrationSamples = ArrayList<Float>()
    private var calibrationStartMs = 0L
    private var lastPublishMs = 0L

    fun start(manager: SensorManager, sensor: Sensor) {
        if (isRunning) return
        smoothed = Float.NaN
        // A calibration interrupted by a pause restarts from scratch: the phone has likely moved.
        calibrationSamples.clear()
        val registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        startFailed = !registered
        if (registered) {
            sensorManager = manager
            isRunning = true
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        isRunning = false
    }

    fun recalibrate() {
        baseline = Float.NaN
        calibrationProgress = 0f
        calibrationSamples.clear()
        history = FloatArray(0)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size < 3) return
        val raw = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (!raw.isFinite()) return
        if (event.accuracy != accuracy) accuracy = event.accuracy

        smoothed = if (smoothed.isNaN()) raw else smoothed + SMOOTHING * (raw - smoothed)
        val now = SystemClock.elapsedRealtime()
        if (!isCalibrated) collectCalibrationSample(raw, now)

        if (now - lastPublishMs < PUBLISH_INTERVAL_MS) return
        lastPublishMs = now
        magnitude = smoothed
        if (isCalibrated) {
            appendHistory(smoothed - baseline)
        } else if (calibrationSamples.isNotEmpty()) {
            calibrationProgress =
                ((now - calibrationStartMs).toFloat() / CALIBRATION_MS).coerceIn(0f, 1f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
        accuracy = newAccuracy
    }

    private fun collectCalibrationSample(raw: Float, now: Long) {
        if (calibrationSamples.isEmpty()) calibrationStartMs = now
        calibrationSamples += raw
        if (now - calibrationStartMs >= CALIBRATION_MS) {
            baseline = calibrationSamples.median()
            calibrationProgress = 1f
            calibrationSamples.clear()
        }
    }

    private fun appendHistory(value: Float) {
        val old = history
        history = if (old.size < HISTORY_SIZE) {
            old + value
        } else {
            FloatArray(HISTORY_SIZE) { i -> if (i < HISTORY_SIZE - 1) old[i + 1] else value }
        }
    }

    companion object {
        /** Keeps the baseline across configuration changes so the user does not re-calibrate. */
        val StateSaver: Saver<MagnetDetector, Float> = Saver(
            save = { it.baseline },
            restore = { MagnetDetector(it) }
        )
    }
}

private fun List<Float>.median(): Float {
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
}
