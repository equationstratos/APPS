package com.survival.altimeter

import kotlin.math.pow

/**
 * International Standard Atmosphere (troposphere), the model used by aviation and hiking altimeters:
 * 15 °C and 1013,25 hPa at sea level, temperature falling by 6,5 °C per kilometre.
 */
object Isa {
    const val SEA_LEVEL_HPA = 1013.25

    /** T0 / L = 288,15 K / 0,0065 K/m ≈ 44 330,8 m. */
    const val SCALE_HEIGHT_M = 288.15 / 0.0065

    /** g·M / (R·L) ≈ 5,2559. */
    const val EXPONENT = 9.80665 * 0.0289644 / (8.3144598 * 0.0065)

    /** Lowest and highest sea-level pressures ever recorded, rounded outwards. */
    const val MIN_QNH_HPA = 870.0
    const val MAX_QNH_HPA = 1085.0

    /** Dead Sea shore to just above the summit of Everest. */
    const val MIN_ALTITUDE_M = -450.0
    const val MAX_ALTITUDE_M = 8900.0

    /** Range a phone barometer can plausibly report (sensors are specified for 300 to 1 100 hPa). */
    const val MIN_STATION_HPA = 250.0
    const val MAX_STATION_HPA = 1150.0
}

fun isPlausibleStationPressure(hPa: Double): Boolean =
    hPa.isFinite() && hPa >= Isa.MIN_STATION_HPA && hPa <= Isa.MAX_STATION_HPA

/** Altitude (m) for a station pressure, given the pressure reduced to sea level ([referenceHpa]). */
fun altitudeFromPressure(stationHpa: Double, referenceHpa: Double = Isa.SEA_LEVEL_HPA): Double =
    Isa.SCALE_HEIGHT_M * (1.0 - (stationHpa / referenceHpa).pow(1.0 / Isa.EXPONENT))

/** Station pressure expected at [altitudeM] when the sea-level pressure is [referenceHpa]. */
fun pressureAtAltitude(altitudeM: Double, referenceHpa: Double = Isa.SEA_LEVEL_HPA): Double =
    referenceHpa * (1.0 - altitudeM / Isa.SCALE_HEIGHT_M).pow(Isa.EXPONENT)

/** Pressure reduced to sea level (QNH) from a station pressure measured at a known altitude. */
fun seaLevelPressure(stationHpa: Double, altitudeM: Double): Double =
    stationHpa / (1.0 - altitudeM / Isa.SCALE_HEIGHT_M).pow(Isa.EXPONENT)

/** Altitude change for 1 hPa of pressure change at this station pressure: ≈ 8,3 m at sea level, ≈ 11 m at 3 000 m. */
fun metersPerHpa(stationHpa: Double, referenceHpa: Double = Isa.SEA_LEVEL_HPA): Double {
    val n = 1.0 / Isa.EXPONENT
    return Isa.SCALE_HEIGHT_M * n / referenceHpa * (stationHpa / referenceHpa).pow(n - 1.0)
}
