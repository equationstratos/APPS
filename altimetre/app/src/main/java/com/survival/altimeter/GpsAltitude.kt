package com.survival.altimeter

/**
 * One GPS fix reduced to what the altimeter needs.
 * [altitudeM] is above mean sea level (null when the fix has no altitude);
 * [verticalAccuracyM] comes from the receiver, or is estimated from the horizontal accuracy
 * ([accuracyEstimated]) when the phone does not report it.
 */
data class GpsReading(
    val altitudeM: Double?,
    val verticalAccuracyM: Double?,
    val accuracyEstimated: Boolean,
    val elapsedMs: Long,
)

data class GpsEstimate(val altitudeM: Double, val accuracyM: Double, val fixCount: Int)

/** Vertical accuracy needed to calibrate the barometer from the GPS. */
const val GPS_MAX_ACCURACY_M = 15.0

/** Below this, the GPS altitude is shown as good. */
const val GPS_GOOD_ACCURACY_M = 8.0

/** Vertical error is typically 1,5 times the horizontal one when the chip does not report it. */
const val VERTICAL_FROM_HORIZONTAL = 1.5

/**
 * Averages the last good fixes (accuracy-weighted) so a single jumpy fix does not decide the
 * calibration. Fixes older than [windowMs] or less accurate than [GPS_MAX_ACCURACY_M] are ignored.
 */
class GpsAltitudeAverager(private val windowMs: Long = 20_000L, private val maxFixes: Int = 10) {
    private val fixes = ArrayDeque<GpsReading>()

    fun add(reading: GpsReading) {
        if (reading.altitudeM?.isFinite() != true) return
        fixes.addLast(reading)
        while (fixes.size > maxFixes) fixes.removeFirst()
    }

    fun clear() = fixes.clear()

    fun estimate(nowElapsedMs: Long, minFixes: Int = 3): GpsEstimate? {
        val good = fixes.filter { fix ->
            val accuracy = fix.verticalAccuracyM
            nowElapsedMs - fix.elapsedMs in 0..windowMs &&
                accuracy != null && accuracy.isFinite() && accuracy > 0.0 && accuracy <= GPS_MAX_ACCURACY_M
        }
        if (good.size < minFixes) return null
        var weightSum = 0.0
        var altitudeSum = 0.0
        for (fix in good) {
            val accuracy = fix.verticalAccuracyM ?: continue
            val altitude = fix.altitudeM ?: continue
            val weight = 1.0 / (accuracy * accuracy)
            weightSum += weight
            altitudeSum += weight * altitude
        }
        if (weightSum <= 0.0) return null
        // Errors of successive fixes are strongly correlated: report the best single accuracy, not a shrunk one.
        val accuracy = good.minOf { it.verticalAccuracyM ?: GPS_MAX_ACCURACY_M }
        return GpsEstimate(altitudeSum / weightSum, accuracy, good.size)
    }
}
