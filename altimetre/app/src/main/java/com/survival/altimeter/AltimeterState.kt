package com.survival.altimeter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

private const val RECORD_INTERVAL_MS = 60_000L

/** Smaller changes are not worth a recomposition (≈ 4 cm of altitude). */
private const val DISPLAY_STEP_HPA = 0.005

/** Live barometer state of the screen: filtered pressure, calibration, elevation session and history. */
class AltimeterState(private val prefs: AppPrefs, nowMs: Long) {
    private val filter = PressureFilter()

    var stationHpa by mutableDoubleStateOf(Double.NaN)
        private set
    var settled by mutableStateOf(false)
        private set
    var calibration by mutableStateOf(prefs.calibration)
        private set
    var session by mutableStateOf(prefs.session ?: ElevationSession(nowMs))
        private set
    var history by mutableStateOf<List<PressureSample>>(emptyList())
    var nowMs by mutableLongStateOf(nowMs)

    private var savedSession: ElevationSession? = session

    /** Calibrated altitude, NaN until the filter has settled. */
    val altitudeM: Double
        get() = if (settled && stationHpa.isFinite()) calibration.altitude(stationHpa) else Double.NaN

    fun onSample(timestampNanos: Long, hPa: Double) {
        val value = filter.add(timestampNanos, hPa)
        if (!value.isFinite()) return
        if (!stationHpa.isFinite() || abs(value - stationHpa) >= DISPLAY_STEP_HPA) stationHpa = value
        settled = filter.isSettled
        if (settled) session = session.update(calibration.altitude(value))
    }

    /** Sensor stopped: the next resume starts a fresh average (the pressure may have changed a lot). */
    fun onPause() {
        filter.reset()
        settled = false
        persistSession()
    }

    fun persistSession() {
        if (session != savedSession) {
            prefs.session = session
            savedSession = session
        }
    }

    fun applyCalibration(newCalibration: Calibration) {
        val anchor = session.anchorM
        if (anchor.isFinite()) {
            // Same air pressure, new reference: every session altitude moves by the same offset.
            val anchorHpa = pressureAtAltitude(anchor, calibration.referenceHpa)
            session = session.shifted(newCalibration.altitude(anchorHpa) - anchor)
        }
        calibration = newCalibration
        prefs.calibration = newCalibration
        persistSession()
    }

    fun resetSession(nowMs: Long) {
        session = ElevationSession(nowMs).let { fresh ->
            if (settled && stationHpa.isFinite()) fresh.update(calibration.altitude(stationHpa)) else fresh
        }
        persistSession()
    }

    /** A reading for the 72 h history, at most one per minute, once the filter has settled. */
    fun sampleToRecord(nowMs: Long): PressureSample? {
        if (!settled || !stationHpa.isFinite()) return null
        val last = history.lastOrNull()?.timeMs
        if (last != null && nowMs - last in 0 until RECORD_INTERVAL_MS) return null
        return PressureSample(nowMs, stationHpa)
    }
}
