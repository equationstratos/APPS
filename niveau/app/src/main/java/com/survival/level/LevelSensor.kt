package com.survival.level

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tilt in degrees, either raw from the sensor or with the calibration applied. */
sealed interface LevelReading {
    /** Phone lying flat: [x] > 0 when the right side is higher, [y] > 0 when the top is higher. */
    data class Flat(val x: Float, val y: Float) : LevelReading

    /**
     * Phone standing on one of its edges. [edge] counts quarter turns from portrait (0..3) and
     * [angle] is the in-plane deviation from that edge being horizontal.
     */
    data class Upright(val edge: Int, val angle: Float) : LevelReading
}

val LevelReading.maxDeviation: Float
    get() = when (this) {
        is LevelReading.Flat -> max(abs(x), abs(y))
        is LevelReading.Upright -> abs(angle)
    }

private const val RAD_TO_DEG = 57.29578f
private const val SMOOTHING_SECONDS = 0.2f
private const val PUBLISH_INTERVAL_NS = 33_000_000L
private const val MIN_GRAVITY = 2f
private const val UPRIGHT_ABOVE_DEG = 50f
private const val FLAT_BELOW_DEG = 40f
private const val EDGE_HYSTERESIS_DEG = 5f

/**
 * Low-pass filters the accelerometer continuously (time-constant based, so it does not depend on
 * the sensor rate) and publishes at most ~30 readings per second on the registering thread.
 */
class LevelSensor(
    private val onReading: (LevelReading) -> Unit,
    private val onReliabilityChange: (Boolean) -> Unit
) : SensorEventListener {

    private val gravity = FloatArray(3)
    private var hasSample = false
    private var lastSampleNs = 0L
    private var lastPublishNs = 0L
    private var upright = false
    private var edge = 0

    /** Drops the filter history, e.g. after the app comes back from the background. */
    fun reset() {
        hasSample = false
        onReliabilityChange(true)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return

        if (hasSample) {
            val dt = ((event.timestamp - lastSampleNs) / 1e9f).coerceIn(0f, 1f)
            val alpha = dt / (SMOOTHING_SECONDS + dt)
            gravity[0] += alpha * (ax - gravity[0])
            gravity[1] += alpha * (ay - gravity[1])
            gravity[2] += alpha * (az - gravity[2])
        } else {
            gravity[0] = ax
            gravity[1] = ay
            gravity[2] = az
            hasSample = true
            lastPublishNs = event.timestamp - PUBLISH_INTERVAL_NS
        }
        lastSampleNs = event.timestamp

        if (event.timestamp - lastPublishNs >= PUBLISH_INTERVAL_NS) {
            lastPublishNs = event.timestamp
            computeReading()?.let(onReading)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            onReliabilityChange(accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE)
        }
    }

    private fun computeReading(): LevelReading? {
        val gx = gravity[0]
        val gy = gravity[1]
        val gz = gravity[2]
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        // Free fall or a violent shake: no meaningful "down" direction.
        if (!norm.isFinite() || norm < MIN_GRAVITY) return null

        val inclination = acos((abs(gz) / norm).coerceIn(0f, 1f)).toDegrees()
        upright = if (upright) inclination > FLAT_BELOW_DEG else inclination > UPRIGHT_ABOVE_DEG

        if (!upright) {
            return LevelReading.Flat(
                x = asin((gx / norm).coerceIn(-1f, 1f)).toDegrees(),
                y = asin((gy / norm).coerceIn(-1f, 1f)).toDegrees()
            )
        }
        val roll = atan2(gx, gy).toDegrees()
        if (abs(wrapDegrees(roll - edge * 90f)) > 45f + EDGE_HYSTERESIS_DEG) {
            edge = (roll / 90f).roundToInt().mod(4)
        }
        return LevelReading.Upright(edge = edge, angle = wrapDegrees(roll - edge * 90f))
    }
}

private fun Float.toDegrees(): Float = this * RAD_TO_DEG

/** Maps any angle to [-180, 180). */
private fun wrapDegrees(degrees: Float): Float = (degrees + 180f).mod(360f) - 180f
