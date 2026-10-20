package com.survival.sunmoon

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Geometric altitude of the Sun's centre at sunrise and sunset (refraction 34' + semi-diameter 16'). */
internal const val SUNRISE_ALTITUDE = -0.833
internal const val CIVIL_TWILIGHT = -6.0
internal const val NAUTICAL_TWILIGHT = -12.0
internal const val ASTRONOMICAL_TWILIGHT = -18.0
internal const val GOLDEN_HOUR_LOW = -4.0
internal const val GOLDEN_HOUR_HIGH = 6.0

private const val SUN_STEP_S = 300.0
private const val MOON_STEP_S = 600.0
private const val TRANSIT_STEP_S = 1800.0
private const val PHASE_STEP_S = 43_200.0
private const val PHASE_BISECTION_STEPS = 28
private const val PHASE_SEARCH_LIMIT = 400
private const val LOOKAHEAD_DAYS = 3L
private const val UPCOMING_PHASES = 6

/** Local calendar day [start, end) in a time zone: 23, 24 or 25 hours long on clock-change days. */
internal class DayWindow(val date: LocalDate, val zone: ZoneId) {
    val start: Instant = date.atStartOfDay(zone).toInstant()
    val end: Instant = date.plusDays(1).atStartOfDay(zone).toInstant()
    val startSeconds: Double = start.seconds()
    val endSeconds: Double = end.seconds()
    val noon: Instant get() = date.atTime(LocalTime.NOON).atZone(zone).toInstant()

    fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

internal fun Instant.seconds(): Double = epochSecond + nano / 1e9

internal fun instantOf(seconds: Double): Instant = Instant.ofEpochMilli((seconds * 1000.0).roundToLong())

internal enum class PolarState { POLAR_DAY, POLAR_NIGHT }

/** Crossings of a twilight altitude: [dawn] upwards in the morning, [dusk] downwards in the evening. */
internal class Twilight(
    val dawn: List<Instant>,
    val dusk: List<Instant>,
    /** No crossing and the Sun stays above the altitude all day long. */
    val alwaysAbove: Boolean,
    /** No crossing and the Sun stays below the altitude all day long. */
    val alwaysBelow: Boolean,
)

internal class TimeSpan(val start: Instant, val end: Instant)

internal class SunDay(
    val window: DayWindow,
    val sunrises: List<Instant>,
    val sunsets: List<Instant>,
    /** Set when the Sun neither rises nor sets during the day. */
    val polar: PolarState?,
    val noon: Instant?,
    /** Geometric altitude at the solar noon (or the highest of the day when there is no transit). */
    val noonAltitude: Double,
    val daylightSeconds: Long,
    val civil: Twilight,
    val nautical: Twilight,
    val astronomical: Twilight,
    val goldenHours: List<TimeSpan>,
    val blueHours: List<TimeSpan>,
    /** Geometric altitude every 5 minutes across the day, for the chart. */
    val curveTimes: DoubleArray,
    val curveAltitudes: DoubleArray,
)

internal fun computeSunDay(latitude: Double, longitude: Double, window: DayWindow): SunDay {
    val curve = sunCurve(latitude, longitude, window.startSeconds, window.endSeconds)
    val horizon = curve.crossings(SUNRISE_ALTITUDE)
    val polar = when {
        horizon.isNotEmpty() -> null
        curve.values[0] >= SUNRISE_ALTITUDE -> PolarState.POLAR_DAY
        else -> PolarState.POLAR_NIGHT
    }
    val transit = solarTransits(longitude, window.startSeconds, window.endSeconds).firstOrNull()
    return SunDay(
        window = window,
        sunrises = horizon.filter { it.rising }.map { instantOf(it.time) },
        sunsets = horizon.filter { !it.rising }.map { instantOf(it.time) },
        polar = polar,
        noon = transit?.let(::instantOf),
        noonAltitude = transit?.let { sunAltitude(it, latitude, longitude) } ?: curve.maxValue,
        daylightSeconds = curve.durationAbove(SUNRISE_ALTITUDE).roundToLong(),
        civil = twilight(curve, CIVIL_TWILIGHT),
        nautical = twilight(curve, NAUTICAL_TWILIGHT),
        astronomical = twilight(curve, ASTRONOMICAL_TWILIGHT),
        goldenHours = curve.spansBetween(GOLDEN_HOUR_LOW, GOLDEN_HOUR_HIGH).map { it.toTimeSpan() },
        blueHours = curve.spansBetween(CIVIL_TWILIGHT, GOLDEN_HOUR_LOW).map { it.toTimeSpan() },
        curveTimes = curve.times,
        curveAltitudes = curve.values,
    )
}

/** Time the Sun spends above the sunrise altitude during the day, in seconds. */
internal fun daylightSeconds(latitude: Double, longitude: Double, window: DayWindow): Long =
    sunCurve(latitude, longitude, window.startSeconds, window.endSeconds)
        .durationAbove(SUNRISE_ALTITUDE)
        .roundToLong()

/** Sunrises and sunsets from [from] over the following days, to follow the Sun live. */
internal fun horizonCrossingsAhead(latitude: Double, longitude: Double, from: Instant): List<Crossing> {
    val start = from.seconds()
    return sunCurve(latitude, longitude, start, start + LOOKAHEAD_DAYS * SECONDS_PER_DAY).crossings(SUNRISE_ALTITUDE)
}

private fun sunCurve(latitude: Double, longitude: Double, start: Double, end: Double) =
    SampledCurve({ sunAltitude(it, latitude, longitude) }, start, end, SUN_STEP_S)

private fun twilight(curve: SampledCurve, level: Double): Twilight {
    val crossings = curve.crossings(level)
    val startsAbove = curve.values[0] >= level
    return Twilight(
        dawn = crossings.filter { it.rising }.map { instantOf(it.time) },
        dusk = crossings.filter { !it.rising }.map { instantOf(it.time) },
        alwaysAbove = crossings.isEmpty() && startsAbove,
        alwaysBelow = crossings.isEmpty() && !startsAbove,
    )
}

private fun Span.toTimeSpan() = TimeSpan(instantOf(start), instantOf(end))

/** Upper meridian transits of the Sun (hour angle crossing zero upwards) within [start, end). */
internal fun solarTransits(longitude: Double, start: Double, end: Double): List<Double> {
    val transits = ArrayList<Double>(1)
    var t0 = start
    var h0 = sunHourAngle(t0, longitude)
    while (t0 < end) {
        val t1 = minOf(t0 + TRANSIT_STEP_S, end)
        val h1 = sunHourAngle(t1, longitude)
        if (h0 < 0.0 && h1 >= 0.0 && h1 - h0 < 90.0) {
            var lo = t0
            var hi = t1
            repeat(PHASE_BISECTION_STEPS) {
                val mid = (lo + hi) / 2.0
                if (sunHourAngle(mid, longitude) < 0.0) lo = mid else hi = mid
            }
            val t = (lo + hi) / 2.0
            if (t < end) transits += t
        }
        t0 = t1
        h0 = h1
    }
    return transits
}

internal enum class MoonPhase(val label: String) {
    NEW("Nouvelle lune"),
    WAXING_CRESCENT("Premier croissant"),
    FIRST_QUARTER("Premier quartier"),
    WAXING_GIBBOUS("Gibbeuse croissante"),
    FULL("Pleine lune"),
    WANING_GIBBOUS("Gibbeuse décroissante"),
    LAST_QUARTER("Dernier quartier"),
    WANING_CRESCENT("Dernier croissant"),
}

internal class PhaseEvent(val phase: MoonPhase, val time: Instant)

internal class MoonDay(
    val window: DayWindow,
    val rises: List<Instant>,
    val sets: List<Instant>,
    /** No rise nor set and the Moon stays above the horizon all day long. */
    val alwaysAbove: Boolean,
    /** No rise nor set and the Moon stays below the horizon all day long. */
    val alwaysBelow: Boolean,
    /** Last new moon before the start of the day. */
    val previousNewMoon: Instant,
    /** The next six principal phases from the start of the day (more than a lunation). */
    val upcoming: List<PhaseEvent>,
) {
    /** Principal phase occurring during this calendar day, which then names the whole day. */
    val eventToday: PhaseEvent? get() = upcoming.firstOrNull()?.takeIf { window.contains(it.time) }

    /** First [phase] strictly after [after], for an instant of this day. */
    fun next(phase: MoonPhase, after: Instant): PhaseEvent? =
        upcoming.firstOrNull { it.phase == phase && it.time.isAfter(after) }
}

internal fun computeMoonDay(latitude: Double, longitude: Double, window: DayWindow): MoonDay {
    val curve = SampledCurve(
        { moonRiseSetFunction(it, latitude, longitude) },
        window.startSeconds,
        window.endSeconds,
        MOON_STEP_S,
    )
    val crossings = curve.crossings(0.0)
    val startsAbove = curve.values[0] >= 0.0
    return MoonDay(
        window = window,
        rises = crossings.filter { it.rising }.map { instantOf(it.time) },
        sets = crossings.filter { !it.rising }.map { instantOf(it.time) },
        alwaysAbove = crossings.isEmpty() && startsAbove,
        alwaysBelow = crossings.isEmpty() && !startsAbove,
        previousNewMoon = instantOf(previousNewMoon(window.startSeconds)),
        upcoming = principalPhasesAfter(window.startSeconds, UPCOMING_PHASES),
    )
}

internal class MoonSnapshot(
    val fraction: Double,
    val waxing: Boolean,
    val ageDays: Double,
    val phase: MoonPhase,
)

/** Phase of the Moon at [at], an instant of [day]. */
internal fun moonSnapshot(day: MoonDay, at: Instant): MoonSnapshot {
    val illumination = moonIllumination(at.seconds())
    val lastNewMoon = day.upcoming
        .filter { it.phase == MoonPhase.NEW && !it.time.isAfter(at) }
        .map { it.time }
        .maxOrNull() ?: day.previousNewMoon
    return MoonSnapshot(
        fraction = illumination.fraction,
        waxing = illumination.waxing,
        ageDays = ((at.seconds() - lastNewMoon.seconds()) / SECONDS_PER_DAY).coerceAtLeast(0.0),
        phase = day.eventToday?.phase ?: intermediatePhase(illumination.elongation),
    )
}

internal fun intermediatePhase(elongation: Double): MoonPhase = when {
    elongation < 90.0 -> MoonPhase.WAXING_CRESCENT
    elongation < 180.0 -> MoonPhase.WAXING_GIBBOUS
    elongation < 270.0 -> MoonPhase.WANING_GIBBOUS
    else -> MoonPhase.WANING_CRESCENT
}

private fun principalPhase(elongation: Double): MoonPhase = when ((elongation / 90.0).roundToInt() % 4) {
    0 -> MoonPhase.NEW
    1 -> MoonPhase.FIRST_QUARTER
    2 -> MoonPhase.FULL
    else -> MoonPhase.LAST_QUARTER
}

/** The next [count] new moons, first quarters, full moons and last quarters strictly after [from]. */
internal fun principalPhasesAfter(from: Double, count: Int): List<PhaseEvent> {
    val events = ArrayList<PhaseEvent>(count)
    var t0 = from
    var e0 = moonSunElongation(t0)
    var steps = 0
    while (events.size < count && steps < PHASE_SEARCH_LIMIT) {
        steps++
        val t1 = t0 + PHASE_STEP_S
        val e1 = moonSunElongation(t1)
        val next = (floor(e0 / 90.0) + 1.0) * 90.0
        if (e0 + norm360(e1 - e0) >= next) {
            val target = norm360(next)
            events += PhaseEvent(principalPhase(target), instantOf(solveElongation(t0, t1, target)))
        }
        t0 = t1
        e0 = e1
    }
    return events
}

/** Last new moon at or before [before]. */
internal fun previousNewMoon(before: Double): Double {
    var t1 = before
    var e1 = moonSunElongation(t1)
    repeat(PHASE_SEARCH_LIMIT) {
        val t0 = t1 - PHASE_STEP_S
        val e0 = moonSunElongation(t0)
        if (e0 + norm360(e1 - e0) >= 360.0) return solveElongation(t0, t1, 0.0)
        t1 = t0
        e1 = e0
    }
    return t1
}

/** Instant in [t0, t1] where the elongation reaches [target], the elongation increasing through it. */
private fun solveElongation(t0: Double, t1: Double, target: Double): Double {
    var lo = t0
    var hi = t1
    repeat(PHASE_BISECTION_STEPS) {
        val mid = (lo + hi) / 2.0
        if (norm180(moonSunElongation(mid) - target) < 0.0) lo = mid else hi = mid
    }
    return (lo + hi) / 2.0
}

internal class Ephemeris(
    val latitude: Double,
    val longitude: Double,
    val sun: SunDay,
    val previousDaylightSeconds: Long,
    val moon: MoonDay,
    /** Sunrises and sunsets over the next days, only computed for today. */
    val horizonAhead: List<Crossing>,
)

internal fun computeEphemeris(
    latitude: Double,
    longitude: Double,
    date: LocalDate,
    zone: ZoneId,
    followLive: Boolean,
): Ephemeris {
    val window = DayWindow(date, zone)
    return Ephemeris(
        latitude = latitude,
        longitude = longitude,
        sun = computeSunDay(latitude, longitude, window),
        previousDaylightSeconds = daylightSeconds(latitude, longitude, DayWindow(date.minusDays(1), zone)),
        moon = computeMoonDay(latitude, longitude, window),
        horizonAhead = if (followLive) horizonCrossingsAhead(latitude, longitude, window.start) else emptyList(),
    )
}

/** Position of the Sun now and the next sunrise or sunset. */
internal class SunLive(
    /** Apparent altitude (refraction included). */
    val altitude: Double,
    val azimuth: Double,
    val up: Boolean,
    val nextRise: Instant?,
    val nextSet: Instant?,
)

internal fun sunLive(now: Instant, latitude: Double, longitude: Double, ahead: List<Crossing>): SunLive {
    val t = now.seconds()
    val position = sunHorizontal(t, latitude, longitude)
    return SunLive(
        altitude = position.altitude + refraction(position.altitude),
        azimuth = position.azimuth,
        up = position.altitude >= SUNRISE_ALTITUDE,
        nextRise = ahead.firstOrNull { it.rising && it.time > t }?.let { instantOf(it.time) },
        nextSet = ahead.firstOrNull { !it.rising && it.time > t }?.let { instantOf(it.time) },
    )
}

/** Apparent altitude (refraction included) and azimuth of a body; [up] once its upper limb has risen. */
internal class BodyPosition(val altitude: Double, val azimuth: Double, val up: Boolean)

/** Position of the Moon now, as seen from the observer. */
internal fun moonLive(now: Instant, latitude: Double, longitude: Double): BodyPosition {
    val t = now.seconds()
    val position = moonHorizontal(t, latitude, longitude)
    return BodyPosition(
        altitude = position.altitude + refraction(position.altitude),
        azimuth = position.azimuth,
        up = moonRiseSetFunction(t, latitude, longitude) >= 0.0,
    )
}
