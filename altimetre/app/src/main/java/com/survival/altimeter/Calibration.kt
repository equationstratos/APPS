package com.survival.altimeter

/** How the sea-level reference pressure was obtained. */
enum class CalibrationMethod(val key: String, val label: String) {
    STANDARD("std", "pression standard"),
    KNOWN_ALTITUDE("alt", "altitude connue"),
    GPS("gps", "GPS"),
    QNH("qnh", "QNH saisi");

    companion object {
        fun fromKey(key: String?): CalibrationMethod? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Sea-level reference pressure (QNH) that turns the station pressure into an altitude.
 * [siteAltitudeM] is the altitude of the place where the calibration was made; it is also used to
 * reduce the station pressure to sea level for the weather display.
 */
data class Calibration(
    val referenceHpa: Double,
    val method: CalibrationMethod,
    val timeMs: Long?,
    val siteAltitudeM: Double?,
) {
    val isStandard: Boolean get() = method == CalibrationMethod.STANDARD

    fun altitude(stationHpa: Double): Double = altitudeFromPressure(stationHpa, referenceHpa)

    /** True when never calibrated, or calibrated more than [STALE_AFTER_MS] ago (or in the future after a clock change). */
    fun isStale(nowMs: Long): Boolean {
        val time = timeMs ?: return true
        val age = nowMs - time
        return age > STALE_AFTER_MS || age < -FUTURE_TOLERANCE_MS
    }

    fun encode(): String = listOf(
        FORMAT_VERSION,
        referenceHpa.toString(),
        method.key,
        timeMs?.toString().orEmpty(),
        siteAltitudeM?.toString().orEmpty(),
    ).joinToString(";")

    companion object {
        const val STALE_AFTER_MS = 6L * 3_600_000L
        private const val FUTURE_TOLERANCE_MS = 10L * 60_000L
        private const val FORMAT_VERSION = "1"

        val STANDARD = Calibration(Isa.SEA_LEVEL_HPA, CalibrationMethod.STANDARD, null, null)

        fun decode(text: String?): Calibration {
            val parts = text?.split(';') ?: return STANDARD
            if (parts.size != 5 || parts[0] != FORMAT_VERSION) return STANDARD
            val reference = parts[1].toDoubleOrNull() ?: return STANDARD
            val method = CalibrationMethod.fromKey(parts[2]) ?: return STANDARD
            if (!reference.isFinite() || reference < Isa.MIN_QNH_HPA || reference > Isa.MAX_QNH_HPA) return STANDARD
            if (method == CalibrationMethod.STANDARD) return STANDARD
            val site = parts[4].toDoubleOrNull()?.takeIf { it.isFinite() }
            return Calibration(reference, method, parts[3].toLongOrNull(), site)
        }
    }
}

sealed interface CalibrationResult {
    data class Success(val calibration: Calibration) : CalibrationResult
    data class Failure(val message: String) : CalibrationResult
}

private const val NO_PRESSURE = "Mesure de pression pas encore disponible$NBSP: patientez quelques secondes."

/** Calibration from the altitude of the current place (map, signpost, summit) or from a GPS fix. */
fun calibrateWithAltitude(
    stationHpa: Double,
    altitudeM: Double,
    nowMs: Long,
    method: CalibrationMethod = CalibrationMethod.KNOWN_ALTITUDE,
): CalibrationResult {
    if (!isPlausibleStationPressure(stationHpa)) return CalibrationResult.Failure(NO_PRESSURE)
    if (!altitudeM.isFinite() || altitudeM < Isa.MIN_ALTITUDE_M || altitudeM > Isa.MAX_ALTITUDE_M) {
        return CalibrationResult.Failure(altitudeRangeMessage(AltitudeUnit.METERS))
    }
    val reference = seaLevelPressure(stationHpa, altitudeM)
    if (reference < Isa.MIN_QNH_HPA || reference > Isa.MAX_QNH_HPA) {
        return CalibrationResult.Failure(
            "Pression au niveau de la mer calculée improbable (${formatHpa(reference)})$NBSP: vérifiez l'altitude saisie."
        )
    }
    return CalibrationResult.Success(Calibration(reference, method, nowMs, altitudeM))
}

/** Calibration from the local QNH given by a weather report or an airfield (METAR). */
fun calibrateWithQnh(stationHpa: Double, qnhHpa: Double, nowMs: Long): CalibrationResult {
    if (!isPlausibleStationPressure(stationHpa)) return CalibrationResult.Failure(NO_PRESSURE)
    if (!qnhHpa.isFinite() || qnhHpa < Isa.MIN_QNH_HPA || qnhHpa > Isa.MAX_QNH_HPA) {
        return CalibrationResult.Failure(
            "Le QNH doit être compris entre ${formatNumber(Isa.MIN_QNH_HPA)} et ${formatHpa(Isa.MAX_QNH_HPA, 0)}."
        )
    }
    val altitude = altitudeFromPressure(stationHpa, qnhHpa)
    if (altitude < Isa.MIN_ALTITUDE_M || altitude > Isa.MAX_ALTITUDE_M) {
        return CalibrationResult.Failure(
            "Altitude obtenue improbable (${formatAltitude(altitude, AltitudeUnit.METERS)})$NBSP: vérifiez le QNH saisi."
        )
    }
    return CalibrationResult.Success(Calibration(qnhHpa, CalibrationMethod.QNH, nowMs, altitude))
}

fun altitudeRangeMessage(unit: AltitudeUnit): String =
    "L'altitude doit être comprise entre ${formatNumber(unit.fromMeters(Isa.MIN_ALTITUDE_M))} et " +
        "${formatAltitude(Isa.MAX_ALTITUDE_M, unit)}."

/** Parses the "known altitude" field, expressed in [unit]. Returns metres or an error message. */
fun parseAltitudeInput(text: String, unit: AltitudeUnit): Pair<Double?, String?> {
    if (text.isBlank()) return null to "Saisissez l'altitude du lieu, par exemple 1250."
    val value = parseDecimal(text) ?: return null to "Nombre non reconnu$NBSP: utilisez des chiffres, par exemple 1250."
    val meters = unit.toMeters(value)
    if (meters < Isa.MIN_ALTITUDE_M || meters > Isa.MAX_ALTITUDE_M) return null to altitudeRangeMessage(unit)
    return meters to null
}

/** Parses the QNH field (hPa). Returns the value or an error message. */
fun parseQnhInput(text: String): Pair<Double?, String?> {
    if (text.isBlank()) return null to "Saisissez le QNH en hectopascals, par exemple 1018."
    val value = parseDecimal(text) ?: return null to "Nombre non reconnu$NBSP: utilisez des chiffres, par exemple 1018."
    if (value < Isa.MIN_QNH_HPA || value > Isa.MAX_QNH_HPA) {
        return null to "Le QNH doit être compris entre ${formatNumber(Isa.MIN_QNH_HPA)} et ${formatHpa(Isa.MAX_QNH_HPA, 0)}."
    }
    return value to null
}
