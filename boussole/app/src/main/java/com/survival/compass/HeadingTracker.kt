package com.survival.compass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Magnetic heading from the fused rotation vector, or from accelerometer + magnetometer when the
 * device has no rotation vector. The azimuth is smoothed on the unit circle (sin/cos), so the
 * 359° ↔ 0° wrap never makes the rose spin. Listener callbacks arrive on the main thread.
 */
class HeadingTracker(
    private val sensorManager: SensorManager,
    displayRotation: Int
) : SensorEventListener {

    private val rotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val fused = rotationVector != null
    private val timeConstantS = if (fused) TAU_FUSED_S else TAU_RAW_S

    val isAvailable: Boolean = magnetometer != null && (rotationVector != null || accelerometer != null)

    /** Smoothed magnetic heading in [0, 360), null until the first reading. */
    var heading: Float? by mutableStateOf<Float?>(null)
        private set

    /** True when the phone is held too far from horizontal for a reliable reading. */
    var isTilted: Boolean by mutableStateOf(false)
        private set

    /** Last SensorManager.SENSOR_STATUS_* reported by the magnetometer, null until known. */
    var magneticAccuracy: Int? by mutableStateOf<Int?>(null)
        private set

    // Keeps the azimuth relative to the top of the screen on devices whose natural orientation
    // is landscape, where portrait is a rotated display.
    private val remapAxes: Pair<Int, Int>? = when (displayRotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> null
    }

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false

    private var smoothSin = 0f
    private var smoothCos = 1f
    private var lastSampleNs = 0L
    private var lastPublishNs = 0L

    fun start() {
        if (!isAvailable) return
        hasGravity = false
        lastSampleNs = 0L
        lastPublishNs = 0L
        if (rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        } else if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        if (magnetometer != null) {
            // With the fused sensor the magnetometer is only needed for its calibration status.
            val delay = if (fused) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_GAME
            sensorManager.registerListener(this, magnetometer, delay)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                update(event.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (hasGravity) {
                    for (i in 0..2) gravity[i] += GRAVITY_ALPHA * (event.values[i] - gravity[i])
                } else {
                    event.values.copyInto(gravity, endIndex = 3)
                    hasGravity = true
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magneticAccuracy = event.accuracy
                if (!fused && hasGravity) {
                    event.values.copyInto(geomagnetic, endIndex = 3)
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        update(event.timestamp)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) magneticAccuracy = accuracy
    }

    private fun update(timestampNs: Long) {
        val axes = remapAxes
        val matrix = if (axes == null) {
            rotationMatrix
        } else {
            SensorManager.remapCoordinateSystem(rotationMatrix, axes.first, axes.second, remappedMatrix)
            remappedMatrix
        }
        SensorManager.getOrientation(matrix, orientation)
        val azimuth = orientation[0]
        if (!azimuth.isFinite()) return

        val s = sin(azimuth)
        val c = cos(azimuth)
        if (lastSampleNs == 0L) {
            smoothSin = s
            smoothCos = c
        } else {
            val dt = ((timestampNs - lastSampleNs) / 1_000_000_000f).coerceIn(0f, 1f)
            val alpha = 1f - exp(-dt / timeConstantS)
            smoothSin += alpha * (s - smoothSin)
            smoothCos += alpha * (c - smoothCos)
        }
        lastSampleNs = timestampNs

        if (timestampNs - lastPublishNs < PUBLISH_INTERVAL_NS) return
        lastPublishNs = timestampNs
        heading = normalizeDegrees(Math.toDegrees(atan2(smoothSin, smoothCos).toDouble()).toFloat())
        // rotationMatrix[8] is the cosine of the angle between the screen normal and the vertical.
        isTilted = rotationMatrix[8] < FLAT_COS
    }

    private companion object {
        const val GRAVITY_ALPHA = 0.2f
        const val TAU_FUSED_S = 0.12f
        const val TAU_RAW_S = 0.25f
        const val PUBLISH_INTERVAL_NS = 33_000_000L
        const val FLAT_COS = 0.5f
    }
}
