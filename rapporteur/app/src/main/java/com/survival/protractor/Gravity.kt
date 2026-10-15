package com.survival.protractor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.round
import kotlin.math.sqrt

/** Screen-plane tilt (°) above which a flat phone switches to the upright mode, and below which it switches back. */
private const val FLAT_TO_UPRIGHT_DEG = 55f
private const val UPRIGHT_TO_FLAT_DEG = 35f
private const val LEVEL_DEG = 0.5f
private const val MIN_GRAVITY = 1f
private const val FUSED_TAU_S = 0.12f
private const val RAW_TAU_S = 0.3f
private const val JITTER_TAU_S = 0.3f
private const val STABLE_JITTER = 0.2f
private const val PUBLISH_INTERVAL_NS = 33_333_333L

/** Gravity "up" vector in device coordinates (x right, y towards the top of the screen, z out of the screen). */
internal data class Vec3(val x: Float, val y: Float, val z: Float) {
    val norm: Float get() = sqrt(x * x + y * y + z * z)
}

/** Angle between the screen plane and the horizontal: 0° flat (face up or down), 90° upright. */
private fun Vec3.planeTilt(): Float = acos((abs(z) / norm).coerceIn(0f, 1f)).toDegrees()

private fun Float.toDegrees(): Float = this * (180f / PI.toFloat())

private fun wrap180(degrees: Float): Float {
    val r = degrees % 360f
    return when {
        r > 180f -> r - 360f
        r <= -180f -> r + 360f
        else -> r
    }
}

private fun normalize360(degrees: Float): Float {
    val r = degrees % 360f
    return if (r < 0f) r + 360f else r
}

internal data class Sample(val gravity: Vec3, val upright: Boolean, val stable: Boolean)

/** Zero point: the in-plane angle when upright, the plane inclination when flat. */
internal data class Reference(val upright: Boolean, val value: Float)

internal data class Measurement(
    val upright: Boolean,
    /** Direction of gravity on screen: 0° = towards the bottom edge, growing clockwise, in (−180, 180]. */
    val dialAngle: Float,
    /** Inclination of the screen plane, in [0, 90]. */
    val inclination: Float,
) {
    /** In-plane angle of the long edge, folded to [−90, 90]: positive when the top leans left. */
    val lineAngle: Float get() = dialAngle - 180f * round(dialAngle / 180f)
    val vsVertical: Float get() = abs(lineAngle)
    val vsHorizontal: Float get() = 90f - vsVertical
    val primary: Float get() = if (upright) vsVertical else inclination
    val referenceValue: Float get() = if (upright) dialAngle else inclination
    val isLevel: Boolean get() = !upright && inclination < LEVEL_DEG

    /** Downhill direction of the plane, clockwise from the top edge of the screen. */
    val slopeHeading: Float get() = normalize360(dialAngle + 180f)

    fun deltaFrom(reference: Reference): Float? = when {
        reference.upright != upright -> null
        upright -> wrap180(dialAngle - reference.value)
        else -> inclination - reference.value
    }

    companion object {
        fun of(sample: Sample): Measurement {
            val g = sample.gravity
            return Measurement(
                upright = sample.upright,
                dialAngle = atan2(g.x, g.y).toDegrees(),
                inclination = g.planeTilt()
            )
        }
    }
}

/**
 * Smoothed gravity from TYPE_GRAVITY (sensor fusion) or, when absent, a low-passed accelerometer.
 * Publishes to Compose at most ~30 times per second.
 */
internal class GravityTracker(private val sensorManager: SensorManager?) : SensorEventListener {
    private val sensor: Sensor? = sensorManager?.let {
        it.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    private val fused = sensor?.type == Sensor.TYPE_GRAVITY

    val available: Boolean get() = sensor != null

    var sample by mutableStateOf<Sample?>(null)
        private set
    var unreliable by mutableStateOf(false)
        private set

    private val filtered = FloatArray(3)
    private var primed = false
    private var registered = false
    private var lastEventNs = 0L
    private var lastPublishNs = 0L
    private var jitter = 0f
    private var upright = true

    fun start() {
        val target = sensor ?: return
        if (registered) return
        registered = sensorManager?.registerListener(this, target, SensorManager.SENSOR_DELAY_GAME) == true
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
        primed = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val norm = sqrt(x * x + y * y + z * z)
        if (!norm.isFinite() || norm < MIN_GRAVITY) return
        val t = event.timestamp
        if (!primed) {
            filtered[0] = x
            filtered[1] = y
            filtered[2] = z
            jitter = 0f
            upright = Vec3(x, y, z).planeTilt() >= 45f
            primed = true
            lastPublishNs = 0L
        } else {
            val dt = ((t - lastEventNs) / 1e9f).coerceIn(0f, 0.25f)
            val a = dt / ((if (fused) FUSED_TAU_S else RAW_TAU_S) + dt)
            filtered[0] += a * (x - filtered[0])
            filtered[1] += a * (y - filtered[1])
            filtered[2] += a * (z - filtered[2])
            val dx = x - filtered[0]
            val dy = y - filtered[1]
            val dz = z - filtered[2]
            val deviation = sqrt(dx * dx + dy * dy + dz * dz)
            jitter += (deviation - jitter) * (dt / (JITTER_TAU_S + dt))
        }
        lastEventNs = t
        if (lastPublishNs != 0L && t - lastPublishNs < PUBLISH_INTERVAL_NS) return
        lastPublishNs = t

        val g = Vec3(filtered[0], filtered[1], filtered[2])
        val tilt = g.planeTilt()
        upright = when {
            upright && tilt < UPRIGHT_TO_FLAT_DEG -> false
            !upright && tilt > FLAT_TO_UPRIGHT_DEG -> true
            else -> upright
        }
        sample = Sample(g, upright, stable = jitter < STABLE_JITTER)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor == this.sensor) unreliable = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
    }
}
