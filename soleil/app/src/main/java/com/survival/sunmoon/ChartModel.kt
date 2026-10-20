package com.survival.sunmoon

import java.time.Instant
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.floor

/** Vertical range of the altitude chart in degrees, on multiples of 10°. */
internal class AltitudeAxis(val min: Double, val max: Double) {
    /** Height fraction measured from the top: 0 at [max], 1 at [min]. */
    fun fromTop(altitude: Double): Double = ((max - altitude) / (max - min)).coerceIn(0.0, 1.0)
}

/** Range covering the curve, the horizon and the whole twilight down to −20°. */
internal fun altitudeAxis(values: DoubleArray): AltitudeAxis {
    val low = minOf(values.minOrNull() ?: 0.0, ASTRONOMICAL_TWILIGHT - 2.0)
    val high = maxOf(values.maxOrNull() ?: 0.0, 10.0)
    return AltitudeAxis(floor(low / 10.0) * 10.0, ceil(high / 10.0) * 10.0)
}

internal class HourTick(val fraction: Double, val label: String)

/**
 * Ticks every [stepHours] local hours from 0 h to 24 h, placed by elapsed time so that a
 * 23-hour or 25-hour day (clock change) keeps its ticks on the right instants.
 */
internal fun hourTicks(window: DayWindow, stepHours: Int = 6): List<HourTick> {
    val span = window.endSeconds - window.startSeconds
    return (0..24 step stepHours).map { hour ->
        val instant = when (hour) {
            0 -> window.start
            24 -> window.end
            else -> window.date.atTime(LocalTime.of(hour, 0)).atZone(window.zone).toInstant()
        }
        HourTick((instant.seconds() - window.startSeconds) / span, "$hour${NBSP}h")
    }
}

/** Position of [instant] across the day, from 0 to 1, or null outside the day. */
internal fun dayFraction(window: DayWindow, instant: Instant): Double? =
    if (window.contains(instant)) {
        (instant.seconds() - window.startSeconds) / (window.endSeconds - window.startSeconds)
    } else {
        null
    }
