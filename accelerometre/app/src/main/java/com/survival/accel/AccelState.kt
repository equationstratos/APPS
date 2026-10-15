package com.survival.accel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal const val AXIS_COUNT = 3
internal const val GRAPH_WINDOW_NS = 5_000_000_000L

/** Minimum spacing between stored samples (~66 Hz), so the buffer always covers the graph window. */
private const val HISTORY_STEP_NS = 15_000_000L
private const val HISTORY_CAPACITY = 512

/** Compose state is refreshed at most ~30 times per second. */
private const val PUBLISH_INTERVAL_NS = 33_000_000L

private val GRAPH_RANGES = floatArrayOf(2f, 5f, 10f, 20f, 40f, 80f, 160f)

/** Smallest graph range that keeps the visible samples on screen, with a small margin. */
private fun rangeFor(maxAbs: Float): Float =
    GRAPH_RANGES.firstOrNull { it >= maxAbs * 1.05f } ?: GRAPH_RANGES.last()

internal fun magnitudeOf(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

/** Fixed-size ring buffer of timestamped 3-axis samples, oldest first. */
internal class SampleHistory(private val capacity: Int) {
    private val times = LongArray(capacity)
    private val values = Array(AXIS_COUNT) { FloatArray(capacity) }
    private var head = 0

    var size = 0
        private set

    fun time(k: Int): Long = times[index(k)]

    fun value(axis: Int, k: Int): Float = values[axis][index(k)]

    fun lastTimeOrNull(): Long? = if (size == 0) null else time(size - 1)

    fun add(timestampNs: Long, x: Float, y: Float, z: Float) {
        val i = index(size)
        times[i] = timestampNs
        values[0][i] = x
        values[1][i] = y
        values[2][i] = z
        if (size < capacity) size++ else head = (head + 1) % capacity
    }

    fun clear() {
        head = 0
        size = 0
    }

    fun exportTimes(): LongArray = LongArray(size) { time(it) }

    fun exportAxis(axis: Int): FloatArray = FloatArray(size) { value(axis, it) }

    private fun index(k: Int): Int = (head + k) % capacity
}

@Stable
internal class AccelState {
    val history = SampleHistory(HISTORY_CAPACITY)

    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set
    var z by mutableFloatStateOf(0f)
        private set
    var peak by mutableFloatStateOf(0f)
        private set
    var hasData by mutableStateOf(false)
        private set

    /** Timestamp of the most recent published sample: the right edge of the graph. */
    var graphEndNs by mutableLongStateOf(0L)
        private set
    var graphRange by mutableFloatStateOf(GRAPH_RANGES[0])
        private set

    val magnitude: Float get() = magnitudeOf(x, y, z)

    private val latest = FloatArray(AXIS_COUNT)
    private var latestNs = 0L
    private var hasPending = false
    private var pendingPeak = 0f
    private var lastPublishNs = 0L

    fun onSample(timestampNs: Long, ax: Float, ay: Float, az: Float) {
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return
        val m = magnitudeOf(ax, ay, az)
        if (m.isFinite() && m > pendingPeak) pendingPeak = m

        val last = history.lastTimeOrNull()
        if (last != null && timestampNs < last) history.clear()
        if (last == null || timestampNs < last || timestampNs - last >= HISTORY_STEP_NS) {
            history.add(timestampNs, ax, ay, az)
        }

        latest[0] = ax
        latest[1] = ay
        latest[2] = az
        latestNs = timestampNs
        hasPending = true
        if (!hasData || timestampNs < lastPublishNs || timestampNs - lastPublishNs >= PUBLISH_INTERVAL_NS) {
            flush()
        }
    }

    /** Publishes the latest raw sample immediately (used before freezing the display). */
    fun flush() {
        if (!hasPending) return
        x = latest[0]
        y = latest[1]
        z = latest[2]
        peak = pendingPeak
        graphEndNs = latestNs
        graphRange = rangeFor(maxAbsInWindow(latestNs))
        lastPublishNs = latestNs
        hasData = true
        hasPending = false
    }

    fun resetPeak() {
        pendingPeak = if (hasData) magnitude else 0f
        peak = pendingPeak
    }

    fun clear() {
        history.clear()
        x = 0f
        y = 0f
        z = 0f
        peak = 0f
        pendingPeak = 0f
        hasData = false
        hasPending = false
        graphEndNs = 0L
        graphRange = GRAPH_RANGES[0]
        lastPublishNs = 0L
    }

    private fun maxAbsInWindow(endNs: Long): Float {
        var maxAbs = 0f
        for (k in 0 until history.size) {
            if (endNs - history.time(k) > GRAPH_WINDOW_NS) continue
            for (axis in 0 until AXIS_COUNT) maxAbs = max(maxAbs, abs(history.value(axis, k)))
        }
        return maxAbs
    }

    private fun restore(saved: List<Any>) {
        val current = saved[0] as FloatArray
        val times = saved[3] as LongArray
        val axes = Array(AXIS_COUNT) { saved[4 + it] as FloatArray }
        val count = minOf(times.size, axes.minOf { it.size })
        for (k in 0 until count) history.add(times[k], axes[0][k], axes[1][k], axes[2][k])
        if (saved[1] as Boolean) {
            x = current[0]
            y = current[1]
            z = current[2]
            peak = current[3]
            pendingPeak = current[3]
            graphEndNs = saved[2] as Long
            lastPublishNs = graphEndNs
            graphRange = rangeFor(maxAbsInWindow(graphEndNs))
            hasData = true
        }
    }

    companion object {
        val Saver: Saver<AccelState, Any> = listSaver<AccelState, Any>(
            save = { state ->
                listOf(
                    floatArrayOf(state.x, state.y, state.z, state.peak),
                    state.hasData,
                    state.graphEndNs,
                    state.history.exportTimes(),
                    state.history.exportAxis(0),
                    state.history.exportAxis(1),
                    state.history.exportAxis(2)
                )
            },
            restore = { saved -> AccelState().apply { restore(saved) } }
        )
    }
}
