package com.survival.sunmoon

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * Positions of the Sun and the Moon after Jean Meeus, "Astronomical Algorithms" (2nd ed., 1998):
 * chap. 12 (sidereal time), 22 (nutation, 0.5"), 25 (Sun, about 0.01°), 47 (Moon, about 10"),
 * 48 (illuminated fraction). Refraction after the NOAA solar calculator.
 *
 * Angles are in degrees unless the name says otherwise; instants are Unix epoch seconds (UT).
 */

internal const val DEG_TO_RAD = PI / 180.0
internal const val RAD_TO_DEG = 180.0 / PI
internal const val SECONDS_PER_DAY = 86_400.0

private const val JD_UNIX_EPOCH = 2_440_587.5
private const val JD_J2000 = 2_451_545.0
private const val DAYS_PER_CENTURY = 36_525.0
private const val DAYS_PER_YEAR = 365.25
private const val AU_KM = 149_597_870.7
private const val EARTH_RADIUS_KM = 6378.14

/** Solar horizontal parallax at 1 au (8.794"). */
private const val SUN_PARALLAX_AU = 0.002443

/** Standard refraction at the horizon (34'), used for rising and setting. */
internal const val HORIZON_REFRACTION = 34.0 / 60.0

/** Moon radius / Earth equatorial radius: semi-diameter = k × horizontal parallax. */
private const val MOON_RADIUS_RATIO = 0.2725

/** ΔT = TT − UT (seconds): observed values, then a cautious extrapolation. */
private val DELTA_T_YEARS = doubleArrayOf(
    1900.0, 1910.0, 1920.0, 1930.0, 1940.0, 1950.0, 1960.0, 1970.0, 1980.0, 1990.0,
    2000.0, 2010.0, 2015.0, 2020.0, 2025.0, 2050.0, 2100.0
)
private val DELTA_T_SECONDS = doubleArrayOf(
    -2.8, 10.4, 21.2, 24.0, 24.3, 29.1, 33.2, 40.2, 50.5, 56.9,
    63.8, 66.1, 67.6, 69.4, 69.1, 75.0, 95.0
)

internal fun deltaTSeconds(year: Double): Double {
    if (year <= DELTA_T_YEARS.first()) return DELTA_T_SECONDS.first()
    if (year >= DELTA_T_YEARS.last()) return DELTA_T_SECONDS.last()
    var i = 1
    while (DELTA_T_YEARS[i] < year) i++
    val f = (year - DELTA_T_YEARS[i - 1]) / (DELTA_T_YEARS[i] - DELTA_T_YEARS[i - 1])
    return DELTA_T_SECONDS[i - 1] + f * (DELTA_T_SECONDS[i] - DELTA_T_SECONDS[i - 1])
}

/** Angle reduced to [0, 360). */
internal fun norm360(degrees: Double): Double {
    val r = degrees % 360.0
    return if (r < 0.0) r + 360.0 else r
}

/** Angle reduced to (−180, 180]. */
internal fun norm180(degrees: Double): Double {
    val r = norm360(degrees)
    return if (r > 180.0) r - 360.0 else r
}

internal fun julianDay(epochSeconds: Double): Double = epochSeconds / SECONDS_PER_DAY + JD_UNIX_EPOCH

/**
 * Earth orientation for one instant: time in Julian centuries of Terrestrial Time, nutation in
 * longitude, true obliquity of the ecliptic and apparent Greenwich sidereal time.
 */
internal class Frame(epochSeconds: Double) {
    val jdUt: Double = julianDay(epochSeconds)
    val t: Double
    val nutationLongitude: Double
    val trueObliquity: Double
    val siderealTime: Double

    init {
        val year = 2000.0 + (jdUt - JD_J2000) / DAYS_PER_YEAR
        val jde = jdUt + deltaTSeconds(year) / SECONDS_PER_DAY
        t = (jde - JD_J2000) / DAYS_PER_CENTURY
        val omega = (125.04452 - 1934.136261 * t) * DEG_TO_RAD
        val sunMean = (280.4665 + 36000.7698 * t) * DEG_TO_RAD
        val moonMean = (218.3165 + 481267.8813 * t) * DEG_TO_RAD
        val dPsi = (
            -17.20 * sin(omega) - 1.32 * sin(2.0 * sunMean) -
                0.23 * sin(2.0 * moonMean) + 0.21 * sin(2.0 * omega)
            ) / 3600.0
        val dEps = (
            9.20 * cos(omega) + 0.57 * cos(2.0 * sunMean) +
                0.10 * cos(2.0 * moonMean) - 0.09 * cos(2.0 * omega)
            ) / 3600.0
        val meanObliquity = 23.0 + 26.0 / 60.0 + (21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))) / 3600.0
        nutationLongitude = dPsi
        trueObliquity = meanObliquity + dEps
        val d = jdUt - JD_J2000
        val tu = d / DAYS_PER_CENTURY
        val meanSidereal = 280.46061837 + 360.98564736629 * d + tu * tu * (0.000387933 - tu / 38_710_000.0)
        siderealTime = norm360(meanSidereal + dPsi * cos(trueObliquity * DEG_TO_RAD))
    }
}

internal class Equatorial(val rightAscension: Double, val declination: Double)

internal class Horizontal(val altitude: Double, val azimuth: Double)

internal fun eclipticToEquatorial(longitude: Double, latitude: Double, obliquity: Double): Equatorial {
    val l = longitude * DEG_TO_RAD
    val b = latitude * DEG_TO_RAD
    val e = obliquity * DEG_TO_RAD
    val ra = atan2(sin(l) * cos(e) - tan(b) * sin(e), cos(l))
    val dec = asin((sin(b) * cos(e) + cos(b) * sin(e) * sin(l)).coerceIn(-1.0, 1.0))
    return Equatorial(norm360(ra * RAD_TO_DEG), dec * RAD_TO_DEG)
}

/** Local hour angle in (−180, 180]: negative before the meridian transit, positive after. */
internal fun hourAngle(eq: Equatorial, frame: Frame, longitude: Double): Double =
    norm180(frame.siderealTime + longitude - eq.rightAscension)

/** Geometric altitude and azimuth (from north, clockwise) of a body seen from the Earth's centre. */
internal fun toHorizontal(eq: Equatorial, frame: Frame, latitude: Double, longitude: Double): Horizontal {
    val h = hourAngle(eq, frame, longitude) * DEG_TO_RAD
    val phi = latitude * DEG_TO_RAD
    val dec = eq.declination * DEG_TO_RAD
    val sinAlt = sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h)
    val y = -cos(dec) * sin(h)
    val x = cos(phi) * sin(dec) - sin(phi) * cos(dec) * cos(h)
    return Horizontal(asin(sinAlt.coerceIn(-1.0, 1.0)) * RAD_TO_DEG, norm360(atan2(y, x) * RAD_TO_DEG))
}

internal class SunCoordinates(val longitude: Double, val distanceAu: Double, val equatorial: Equatorial)

/** Apparent geocentric position of the Sun (Meeus chap. 25, about 0.01°). */
internal fun sunCoordinates(frame: Frame): SunCoordinates {
    val t = frame.t
    val meanLongitude = 280.46646 + t * (36000.76983 + t * 0.0003032)
    val m = norm360(357.52911 + t * (35999.05029 - t * 0.0001537)) * DEG_TO_RAD
    val e = 0.016708634 - t * (0.000042037 + t * 0.0000001267)
    val center = (1.914602 - t * (0.004817 + t * 0.000014)) * sin(m) +
        (0.019993 - t * 0.000101) * sin(2.0 * m) + 0.000289 * sin(3.0 * m)
    val trueAnomaly = m + center * DEG_TO_RAD
    val r = 1.000001018 * (1.0 - e * e) / (1.0 + e * cos(trueAnomaly))
    // Nutation and aberration (20.4898" / R) give the apparent longitude.
    val longitude = norm360(meanLongitude + center + frame.nutationLongitude - 0.005691611 / r)
    return SunCoordinates(longitude, r, eclipticToEquatorial(longitude, 0.0, frame.trueObliquity))
}

internal class MoonCoordinates(
    val longitude: Double,
    val latitude: Double,
    val distanceKm: Double,
    /** Equatorial horizontal parallax. */
    val parallax: Double,
    val equatorial: Equatorial,
)

// Table 47.A: D, M, M', F multipliers, then Σl (1e-6 deg) and Σr (1e-3 km).
private val LR_TERMS = intArrayOf(
    0, 0, 1, 0, 6288774, -20905355,
    2, 0, -1, 0, 1274027, -3699111,
    2, 0, 0, 0, 658314, -2955968,
    0, 0, 2, 0, 213618, -569925,
    0, 1, 0, 0, -185116, 48888,
    0, 0, 0, 2, -114332, -3149,
    2, 0, -2, 0, 58793, 246158,
    2, -1, -1, 0, 57066, -152138,
    2, 0, 1, 0, 53322, -170733,
    2, -1, 0, 0, 45758, -204586,
    0, 1, -1, 0, -40923, -129620,
    1, 0, 0, 0, -34720, 108743,
    0, 1, 1, 0, -30383, 104755,
    2, 0, 0, -2, 15327, 10321,
    0, 0, 1, 2, -12528, 0,
    0, 0, 1, -2, 10980, 79661,
    4, 0, -1, 0, 10675, -34782,
    0, 0, 3, 0, 10034, -23210,
    4, 0, -2, 0, 8548, -21636,
    2, 1, -1, 0, -7888, 24208,
    2, 1, 0, 0, -6766, 30824,
    1, 0, -1, 0, -5163, -8379,
    1, 1, 0, 0, 4987, -16675,
    2, -1, 1, 0, 4036, -12831,
    2, 0, 2, 0, 3994, -10445,
    4, 0, 0, 0, 3861, -11650,
    2, 0, -3, 0, 3665, 14403,
    0, 1, -2, 0, -2689, -7003,
    2, 0, -1, 2, -2602, 0,
    2, -1, -2, 0, 2390, 10056,
    1, 0, 1, 0, -2348, 6322,
    2, -2, 0, 0, 2236, -9884,
    0, 1, 2, 0, -2120, 5751,
    0, 2, 0, 0, -2069, 0,
    2, -2, -1, 0, 2048, -4950,
    2, 0, 1, -2, -1773, 4130,
    2, 0, 0, 2, -1595, 0,
    4, -1, -1, 0, 1215, -3958,
    0, 0, 2, 2, -1110, 0,
    3, 0, -1, 0, -892, 3258,
    2, 1, 1, 0, -810, 2616,
    4, -1, -2, 0, 759, -1897,
    0, 2, -1, 0, -713, -2117,
    2, 2, -1, 0, -700, 2354,
    2, 1, -2, 0, 691, 0,
    2, -1, 0, -2, 596, 0,
    4, 0, 1, 0, 549, -1423,
    0, 0, 4, 0, 537, -1117,
    4, -1, 0, 0, 520, -1571,
    1, 0, -2, 0, -487, -1739,
    2, 1, 0, -2, -399, 0,
    0, 0, 2, -2, -381, -4421,
    1, 1, 1, 0, 351, 0,
    3, 0, -2, 0, -340, 0,
    4, 0, -3, 0, 330, 0,
    2, -1, 2, 0, 327, 0,
    0, 2, 1, 0, -323, 1165,
    1, 1, -1, 0, 299, 0,
    2, 0, 3, 0, 294, 0,
    2, 0, -1, -2, 0, 8752,
)
// Table 47.B: D, M, M', F multipliers, then Σb (1e-6 deg).
private val B_TERMS = intArrayOf(
    0, 0, 0, 1, 5128122,
    0, 0, 1, 1, 280602,
    0, 0, 1, -1, 277693,
    2, 0, 0, -1, 173237,
    2, 0, -1, 1, 55413,
    2, 0, -1, -1, 46271,
    2, 0, 0, 1, 32573,
    0, 0, 2, 1, 17198,
    2, 0, 1, -1, 9266,
    0, 0, 2, -1, 8822,
    2, -1, 0, -1, 8216,
    2, 0, -2, -1, 4324,
    2, 0, 1, 1, 4200,
    2, 1, 0, -1, -3359,
    2, -1, -1, 1, 2463,
    2, -1, 0, 1, 2211,
    2, -1, -1, -1, 2065,
    0, 1, -1, -1, -1870,
    4, 0, -1, -1, 1828,
    0, 1, 0, 1, -1794,
    0, 0, 0, 3, -1749,
    0, 1, -1, 1, -1565,
    1, 0, 0, 1, -1491,
    0, 1, 1, 1, -1475,
    0, 1, 1, -1, -1410,
    0, 1, 0, -1, -1344,
    1, 0, 0, -1, -1335,
    0, 0, 3, 1, 1107,
    4, 0, 0, -1, 1021,
    4, 0, -1, 1, 833,
    0, 0, 1, -3, 777,
    4, 0, -2, 1, 671,
    2, 0, 0, -3, 607,
    2, 0, 2, -1, 596,
    2, -1, 1, -1, 491,
    2, 0, -2, 1, -451,
    0, 0, 3, -1, 439,
    2, 0, 2, 1, 422,
    2, 0, -3, -1, 421,
    2, 1, -1, 1, -366,
    2, 1, 0, 1, -351,
    4, 0, 0, 1, 331,
    2, -1, 1, 1, 315,
    2, -2, 0, -1, 302,
    0, 0, 1, 3, -283,
    2, 1, 1, -1, -229,
    1, 1, 0, -1, 223,
    1, 1, 0, 1, 223,
    0, 1, -2, -1, -220,
    2, 1, -1, -1, -220,
    1, 0, 1, 1, -185,
    2, -1, -2, -1, 181,
    0, 1, 2, 1, -177,
    4, 0, -2, -1, 176,
    4, -1, -1, -1, 166,
    1, 0, 1, -1, -164,
    4, 0, 1, -1, 132,
    1, 0, -1, -1, -119,
    4, -1, 0, -1, 115,
    2, -2, 0, 1, 107,
)

/** Apparent geocentric position of the Moon (Meeus chap. 47, about 10" in longitude). */
internal fun moonCoordinates(frame: Frame): MoonCoordinates {
    val t = frame.t
    val lp = norm360(218.3164477 + t * (481267.88123421 + t * (-0.0015786 + t * (1.0 / 538841.0 - t / 65194000.0))))
    val d = norm360(297.8501921 + t * (445267.1114034 + t * (-0.0018819 + t * (1.0 / 545868.0 - t / 113065000.0))))
    val m = norm360(357.5291092 + t * (35999.0502909 + t * (-0.0001536 + t / 24490000.0)))
    val mp = norm360(134.9633964 + t * (477198.8675055 + t * (0.0087414 + t * (1.0 / 69699.0 - t / 14712000.0))))
    val f = norm360(93.2720950 + t * (483202.0175233 + t * (-0.0036539 + t * (-1.0 / 3526000.0 + t / 863310000.0))))
    val a1 = norm360(119.75 + 131.849 * t) * DEG_TO_RAD
    val a2 = norm360(53.09 + 479264.290 * t) * DEG_TO_RAD
    val a3 = norm360(313.45 + 481266.484 * t) * DEG_TO_RAD
    val e = 1.0 - t * (0.002516 + t * 0.0000074)

    val lpr = lp * DEG_TO_RAD
    val dr = d * DEG_TO_RAD
    val mr = m * DEG_TO_RAD
    val mpr = mp * DEG_TO_RAD
    val fr = f * DEG_TO_RAD

    var sumL = 0.0
    var sumR = 0.0
    var i = 0
    while (i < LR_TERMS.size) {
        val arg = LR_TERMS[i] * dr + LR_TERMS[i + 1] * mr + LR_TERMS[i + 2] * mpr + LR_TERMS[i + 3] * fr
        val ecc = eccentricityFactor(LR_TERMS[i + 1], e)
        sumL += LR_TERMS[i + 4] * ecc * sin(arg)
        sumR += LR_TERMS[i + 5] * ecc * cos(arg)
        i += 6
    }
    var sumB = 0.0
    i = 0
    while (i < B_TERMS.size) {
        val arg = B_TERMS[i] * dr + B_TERMS[i + 1] * mr + B_TERMS[i + 2] * mpr + B_TERMS[i + 3] * fr
        sumB += B_TERMS[i + 4] * eccentricityFactor(B_TERMS[i + 1], e) * sin(arg)
        i += 5
    }
    sumL += 3958.0 * sin(a1) + 1962.0 * sin(lpr - fr) + 318.0 * sin(a2)
    sumB += -2235.0 * sin(lpr) + 382.0 * sin(a3) + 175.0 * sin(a1 - fr) + 175.0 * sin(a1 + fr) +
        127.0 * sin(lpr - mpr) - 115.0 * sin(lpr + mpr)

    val longitude = norm360(lp + sumL / 1_000_000.0 + frame.nutationLongitude)
    val latitude = sumB / 1_000_000.0
    val distance = 385000.56 + sumR / 1000.0
    val parallax = asin(EARTH_RADIUS_KM / distance) * RAD_TO_DEG
    return MoonCoordinates(
        longitude = longitude,
        latitude = latitude,
        distanceKm = distance,
        parallax = parallax,
        equatorial = eclipticToEquatorial(longitude, latitude, frame.trueObliquity),
    )
}

private fun eccentricityFactor(multiplierOfM: Int, e: Double): Double = when (abs(multiplierOfM)) {
    1 -> e
    2 -> e * e
    else -> 1.0
}

/**
 * Atmospheric refraction (degrees) to add to a geometric altitude, NOAA solar calculator model;
 * continuous from the zenith down to well below the horizon.
 */
internal fun refraction(altitude: Double): Double {
    if (altitude > 85.0) return 0.0
    val te = tan(altitude * DEG_TO_RAD)
    val arcSeconds = when {
        altitude > 5.0 -> 58.1 / te - 0.07 / (te * te * te) + 0.000086 / (te * te * te * te * te)
        altitude > -0.575 -> 1735.0 + altitude * (-518.2 + altitude * (103.4 + altitude * (-12.79 + altitude * 0.711)))
        else -> -20.772 / te
    }
    return arcSeconds / 3600.0
}

/** Geometric position of the Sun's centre seen from the observer (solar parallax included). */
internal fun sunHorizontal(epochSeconds: Double, latitude: Double, longitude: Double): Horizontal {
    val frame = Frame(epochSeconds)
    val sun = sunCoordinates(frame)
    val geo = toHorizontal(sun.equatorial, frame, latitude, longitude)
    val parallax = SUN_PARALLAX_AU / sun.distanceAu * cos(geo.altitude * DEG_TO_RAD)
    return Horizontal(geo.altitude - parallax, geo.azimuth)
}

internal fun sunAltitude(epochSeconds: Double, latitude: Double, longitude: Double): Double =
    sunHorizontal(epochSeconds, latitude, longitude).altitude

/** Hour angle of the Sun, zero at the solar noon (upper meridian transit). */
internal fun sunHourAngle(epochSeconds: Double, longitude: Double): Double {
    val frame = Frame(epochSeconds)
    return hourAngle(sunCoordinates(frame).equatorial, frame, longitude)
}

/** Geometric position of the Moon's centre seen from the observer (parallax in altitude included). */
internal fun moonHorizontal(epochSeconds: Double, latitude: Double, longitude: Double): Horizontal {
    val frame = Frame(epochSeconds)
    val moon = moonCoordinates(frame)
    val geo = toHorizontal(moon.equatorial, frame, latitude, longitude)
    val topocentric = geo.altitude - moon.parallax * cos(geo.altitude * DEG_TO_RAD)
    return Horizontal(topocentric, geo.azimuth)
}

/**
 * Positive while the Moon's upper limb is above the horizon. Geocentric altitude of the centre
 * minus h0 = 0.7275 π − 34' (parallax, semi-diameter and standard refraction; about +0.125°).
 */
internal fun moonRiseSetFunction(epochSeconds: Double, latitude: Double, longitude: Double): Double {
    val frame = Frame(epochSeconds)
    val moon = moonCoordinates(frame)
    val altitude = toHorizontal(moon.equatorial, frame, latitude, longitude).altitude
    val threshold = (1.0 - MOON_RADIUS_RATIO) * moon.parallax - HORIZON_REFRACTION
    return altitude - threshold
}

internal class MoonIllumination(
    /** Difference of apparent ecliptic longitudes Moon − Sun in [0, 360): 0 new, 90 first quarter, 180 full. */
    val elongation: Double,
    /** Illuminated fraction of the disc, 0 to 1. */
    val fraction: Double,
) {
    val waxing: Boolean get() = elongation < 180.0
}

/** Geocentric phase of the Moon (Meeus chap. 48). */
internal fun moonIllumination(epochSeconds: Double): MoonIllumination {
    val frame = Frame(epochSeconds)
    val sun = sunCoordinates(frame)
    val moon = moonCoordinates(frame)
    val dLon = (moon.longitude - sun.longitude) * DEG_TO_RAD
    val beta = moon.latitude * DEG_TO_RAD
    val psi = acos((cos(beta) * cos(dLon)).coerceIn(-1.0, 1.0))
    val sunKm = sun.distanceAu * AU_KM
    val phaseAngle = atan2(sunKm * sin(psi), moon.distanceKm - sunKm * cos(psi))
    return MoonIllumination(
        elongation = norm360(moon.longitude - sun.longitude),
        fraction = ((1.0 + cos(phaseAngle)) / 2.0).coerceIn(0.0, 1.0),
    )
}

/** Moon − Sun apparent ecliptic longitude in [0, 360). */
internal fun moonSunElongation(epochSeconds: Double): Double {
    val frame = Frame(epochSeconds)
    return norm360(moonCoordinates(frame).longitude - sunCoordinates(frame).longitude)
}
