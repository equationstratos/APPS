package com.survival.lux

import android.hardware.SensorManager
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Light readings and session statistics.
 *
 * The light sensor only reports on change, so smoothing and the average are driven by [tick]
 * at a fixed rate rather than by sensor events: a single step change still converges, and the
 * average is weighted by time instead of by the number of events.
 */
@Stable
class LightMeterState private constructor(
    initialLatest: Float,
    initialCurrent: Float,
    initialMin: Float,
    initialMax: Float,
    initialAverage: Float,
    initialWeightedSum: Double,
    initialMeasuredMs: Long,
    initialHeld: Boolean
) {
    constructor() : this(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0.0, 0L, false)

    var current by mutableFloatStateOf(initialCurrent)
        private set
    var min by mutableFloatStateOf(initialMin)
        private set
    var max by mutableFloatStateOf(initialMax)
        private set
    var average by mutableFloatStateOf(initialAverage)
        private set
    var measuredMs by mutableLongStateOf(initialMeasuredMs)
        private set
    var held by mutableStateOf(initialHeld)
        private set
    var unreliable by mutableStateOf(false)
        private set

    private var latest = initialLatest
    private var weightedSum = initialWeightedSum
    private var lastTickMs = NO_TIME

    fun onSample(lux: Float) {
        if (!lux.isFinite()) return
        val value = lux.coerceAtLeast(0f)
        latest = value
        if (held) return
        if (min.isNaN() || value < min) min = value
        if (max.isNaN() || value > max) max = value
        if (current.isNaN()) {
            current = value
            average = value
        }
    }

    fun onAccuracyChanged(accuracy: Int) {
        unreliable = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    fun tick(nowMs: Long) {
        val value = latest
        if (held || value.isNaN()) return
        val delta = value - current
        current = if (current.isNaN() || abs(delta) <= SNAP_LUX + value * SNAP_RATIO) {
            value
        } else {
            current + SMOOTHING * delta
        }
        val previous = lastTickMs
        lastTickMs = nowMs
        if (previous != NO_TIME && nowMs > previous) {
            val dt = nowMs - previous
            weightedSum += value.toDouble() * dt
            measuredMs += dt
            average = (weightedSum / measuredMs).toFloat()
        }
    }

    /** Time spent paused or held must not count in the time-weighted average. */
    fun onPause() {
        lastTickMs = NO_TIME
    }

    fun toggleHold() {
        held = !held
        lastTickMs = NO_TIME
    }

    fun reset() {
        held = false
        lastTickMs = NO_TIME
        weightedSum = 0.0
        measuredMs = 0L
        current = latest
        min = latest
        max = latest
        average = latest
    }

    companion object {
        private const val NO_TIME = -1L
        private const val SMOOTHING = 0.3f
        private const val SNAP_LUX = 0.05f
        private const val SNAP_RATIO = 0.002f

        val Saver: Saver<LightMeterState, Any> = listSaver<LightMeterState, Any>(
            save = {
                listOf(
                    it.latest, it.current, it.min, it.max, it.average,
                    it.weightedSum, it.measuredMs, it.held
                )
            },
            restore = {
                LightMeterState(
                    initialLatest = it[0] as Float,
                    initialCurrent = it[1] as Float,
                    initialMin = it[2] as Float,
                    initialMax = it[3] as Float,
                    initialAverage = it[4] as Float,
                    initialWeightedSum = it[5] as Double,
                    initialMeasuredMs = it[6] as Long,
                    initialHeld = it[7] as Boolean
                )
            }
        )
    }
}
