package com.survival.altimeter

import kotlin.math.max
import kotlin.math.min

/**
 * Cumulative elevation gain and loss with a dead band: a climb or descent is only counted once the
 * altitude has moved [HYSTERESIS_M] or more away from the last counted point, so sensor noise and
 * small gusts never add phantom metres.
 */
data class ElevationSession(
    val startedAtMs: Long,
    val gainM: Double = 0.0,
    val lossM: Double = 0.0,
    val minM: Double = Double.NaN,
    val maxM: Double = Double.NaN,
    val anchorM: Double = Double.NaN,
) {
    val hasData: Boolean get() = anchorM.isFinite()

    fun update(altitudeM: Double, hysteresisM: Double = HYSTERESIS_M): ElevationSession {
        if (!altitudeM.isFinite()) return this
        if (!hasData) return copy(minM = altitudeM, maxM = altitudeM, anchorM = altitudeM)
        val lowest = min(minM, altitudeM)
        val highest = max(maxM, altitudeM)
        val delta = altitudeM - anchorM
        return when {
            delta >= hysteresisM -> copy(gainM = gainM + delta, anchorM = altitudeM, minM = lowest, maxM = highest)
            delta <= -hysteresisM -> copy(lossM = lossM - delta, anchorM = altitudeM, minM = lowest, maxM = highest)
            lowest != minM || highest != maxM -> copy(minM = lowest, maxM = highest)
            else -> this
        }
    }

    /** A new calibration moves every altitude by the same amount: shift the references, not the totals. */
    fun shifted(deltaM: Double): ElevationSession =
        if (!hasData || !deltaM.isFinite()) this else copy(minM = minM + deltaM, maxM = maxM + deltaM, anchorM = anchorM + deltaM)

    fun encode(): String =
        listOf(FORMAT_VERSION, startedAtMs.toString(), num(gainM), num(lossM), num(minM), num(maxM), num(anchorM))
            .joinToString(";")

    companion object {
        const val HYSTERESIS_M = 3.0
        private const val FORMAT_VERSION = "1"

        private fun num(value: Double): String = if (value.isFinite()) value.toString() else ""

        private fun parse(text: String): Double = text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: Double.NaN

        fun decode(text: String?): ElevationSession? {
            val parts = text?.split(';') ?: return null
            if (parts.size != 7 || parts[0] != FORMAT_VERSION) return null
            val started = parts[1].toLongOrNull() ?: return null
            val gain = parse(parts[2]).takeIf { it >= 0.0 } ?: 0.0
            val loss = parse(parts[3]).takeIf { it >= 0.0 } ?: 0.0
            val anchor = parse(parts[6])
            if (!anchor.isFinite()) return ElevationSession(started, gain, loss)
            val low = parse(parts[4]).takeIf { it.isFinite() } ?: anchor
            val high = parse(parts[5]).takeIf { it.isFinite() } ?: anchor
            return ElevationSession(started, gain, loss, low, high, anchor)
        }
    }
}
