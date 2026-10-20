package com.survival.sunmoon

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal const val NBSP = ' '
internal const val MINUS = '−'
internal const val NO_VALUE = "—"

private val FR = Locale.FRANCE

private val DAY_NAMES = arrayOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
private val DAY_SHORT = arrayOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
private val MONTH_NAMES = arrayOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre"
)
private val MONTH_SHORT = arrayOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc."
)
private val CARDINALS = arrayOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"
)

private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()

private fun dayOfMonth(date: LocalDate): String = if (date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()

/** « Lundi 19 octobre 2026 ». */
internal fun formatLongDate(date: LocalDate): String {
    val text = "${DAY_NAMES[date.dayOfWeek.value - 1]} ${dayOfMonth(date)} ${MONTH_NAMES[date.monthValue - 1]} ${date.year}"
    return text.replaceFirstChar { it.titlecase(FR) }
}

/** « lun. 26 oct. », with the year when it differs from [referenceYear]. */
internal fun formatShortDate(date: LocalDate, referenceYear: Int): String {
    val text = "${DAY_SHORT[date.dayOfWeek.value - 1]} ${dayOfMonth(date)} ${MONTH_SHORT[date.monthValue - 1]}"
    return if (date.year == referenceYear) text else "$text ${date.year}"
}

/**
 * Local clock time « HH:mm » rounded to the nearest minute, unless rounding would move it to
 * the next day (23:59:40 stays 23:59).
 */
internal fun formatTime(instant: Instant, zone: ZoneId): String {
    val exact = instant.atZone(zone)
    val rounded = instant.plusSeconds(30).atZone(zone)
    val shown = if (rounded.toLocalDate() == exact.toLocalDate()) rounded else exact
    return "${pad2(shown.hour.toLong())}:${pad2(shown.minute.toLong())}"
}

/** Clock time within a day: the end of the day reads « 24:00 » instead of the next midnight. */
internal fun formatTimeOfDay(instant: Instant, window: DayWindow): String =
    if (!instant.isBefore(window.end)) "24:00" else formatTime(instant, window.zone)

/** « 3 h 12 min », « 45 min », « 12 h 00 min ». */
internal fun formatHoursMinutes(totalMinutes: Long): String {
    val minutes = totalMinutes.coerceAtLeast(0)
    return if (minutes < 60) {
        "$minutes${NBSP}min"
    } else {
        "${minutes / 60}${NBSP}h$NBSP${pad2(minutes % 60)}${NBSP}min"
    }
}

/** Day length rounded to the minute. */
internal fun formatDayLength(seconds: Long): String = formatHoursMinutes((seconds / 60.0).roundToLong())

/**
 * Whole minutes from [now] to [event] as the clocks read them: the event rounded to the minute
 * like [formatTime], the current time truncated like the status bar clock.
 */
internal fun minutesUntil(event: Instant, now: Instant): Long =
    Math.floorDiv(event.epochSecond + 30L, 60L) - Math.floorDiv(now.epochSecond, 60L)

/** Time left until [event]: « 3 h 53 min », « 12 min » or « moins d'une minute ». */
internal fun formatRemaining(event: Instant, now: Instant): String {
    val minutes = minutesUntil(event, now)
    return if (minutes <= 0L) "moins d'une minute" else formatHoursMinutes(minutes)
}

/** « 07:58 », « 07:58 et 23:10 », « 01:02, 07:58 et 23:10 ». */
internal fun joinFrench(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " et " + items.last()
}

/** « lun. 26 oct. à 05:12 », with the year when it differs from [referenceYear]. */
internal fun formatEventDate(instant: Instant, zone: ZoneId, referenceYear: Int): String =
    "${formatShortDate(instant.atZone(zone).toLocalDate(), referenceYear)} à ${formatTime(instant, zone)}"

/** Offset of the time zone: « UTC », « UTC+2 », « UTC−3 », « UTC+5:30 ». */
internal fun formatUtcOffset(totalSeconds: Int): String {
    if (totalSeconds == 0) return "UTC"
    val sign = if (totalSeconds < 0) MINUS else '+'
    val minutes = abs(totalSeconds) / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "UTC$sign$hours" else "UTC$sign$hours:${pad2(rest.toLong())}"
}

/** Horizontal accuracy: « ± 12 m », « ± 1,5 km ». */
internal fun formatAccuracy(meters: Double): String {
    val rounded = meters.coerceAtLeast(0.0).roundToLong()
    return if (rounded < 1000L) {
        "±$NBSP$rounded${NBSP}m"
    } else {
        "±$NBSP${formatDecimal(meters / 1000.0, 1)}${NBSP}km"
    }
}

/** « 213° SO ». */
internal fun formatDirection(azimuth: Double): String = "${formatAzimuth(azimuth)}$NBSP${cardinal(azimuth)}"

/** Change of day length since the day before: « +2 min 14 s », « −45 s », « +1 h 05 min ». */
internal fun formatDayLengthChange(deltaSeconds: Long): String {
    if (deltaSeconds == 0L) return "aucun"
    val sign = if (deltaSeconds < 0) MINUS else '+'
    val a = abs(deltaSeconds)
    return when {
        a < 60 -> "$sign$a${NBSP}s"
        a < 3600 && a % 60 == 0L -> "$sign${a / 60}${NBSP}min"
        a < 3600 -> "$sign${a / 60}${NBSP}min$NBSP${a % 60}${NBSP}s"
        else -> {
            val minutes = (a / 60.0).roundToLong()
            "$sign${minutes / 60}${NBSP}h$NBSP${pad2(minutes % 60)}${NBSP}min"
        }
    }
}

/** Number with a French decimal comma and a true minus sign; never « −0,0 ». */
internal fun formatDecimal(value: Double, decimals: Int): String {
    var scale = 1.0
    repeat(decimals) { scale *= 10.0 }
    var rounded = Math.round(value * scale) / scale
    if (rounded == 0.0) rounded = 0.0
    val text = String.format(FR, "%.${decimals}f", abs(rounded))
    return if (rounded < 0.0) "$MINUS$text" else text
}

/** Altitude above the horizon, « 23,4° » or « −3,2° ». */
internal fun formatAltitude(degrees: Double): String = "${formatDecimal(degrees, 1)}°"

/** Azimuth in whole degrees, 0 to 359. */
internal fun formatAzimuth(degrees: Double): String = "${norm360(degrees).roundToInt() % 360}°"

/** Sixteen-point French compass: N, NNE, NE… O (ouest), NO. */
internal fun cardinal(azimuth: Double): String = CARDINALS[floor((norm360(azimuth) + 11.25) / 22.5).toInt() % 16]

internal fun formatLatitude(latitude: Double): String {
    val text = formatDecimal(abs(latitude), 4)
    val south = latitude < 0 && text.any { it in '1'..'9' }
    return "$text°${NBSP}${if (south) "S" else "N"}"
}

internal fun formatLongitude(longitude: Double): String {
    val text = formatDecimal(abs(longitude), 4)
    val west = longitude < 0 && text.any { it in '1'..'9' }
    return "$text°${NBSP}${if (west) "O" else "E"}"
}

internal fun formatCoordinates(latitude: Double, longitude: Double): String =
    "${formatLatitude(latitude)}, ${formatLongitude(longitude)}"

/** Value prefilled in an input field: plain decimal with a comma, « −33,8688 ». */
internal fun coordinateFieldText(value: Double): String {
    val text = String.format(FR, "%.4f", abs(value)).trimEnd('0').trimEnd(',')
    return if (value < 0 && text.any { it in '1'..'9' }) "-$text" else text
}

/** « 63 % ». */
internal fun formatPercent(fraction: Double): String = "${(fraction * 100.0).roundToInt().coerceIn(0, 100)}$NBSP%"

/** « 12,3 jours », « 1,5 jour » (singular below 2 in French). */
internal fun formatAgeDays(days: Double): String {
    val text = formatDecimal(days, 1)
    val rounded = Math.round(days * 10.0) / 10.0
    return "$text${NBSP}${if (rounded < 2.0) "jour" else "jours"}"
}

/** « aujourd'hui à 14:32 », « hier à 09:10 », « le 12 oct. 2025 à 09:10 ». */
internal fun formatTimestamp(instant: Instant, today: LocalDate, zone: ZoneId): String {
    val date = instant.atZone(zone).toLocalDate()
    val time = formatTime(instant, zone)
    return when (date) {
        today -> "aujourd'hui à $time"
        today.minusDays(1) -> "hier à $time"
        else -> "le ${dayOfMonth(date)} ${MONTH_SHORT[date.monthValue - 1]} ${date.year} à $time"
    }
}

/** Origin and age of the remembered position: « GPS (± 8 m), aujourd'hui à 10:15 ». */
internal fun describePlace(place: SavedPlace, today: LocalDate, zone: ZoneId): String {
    val accuracy = place.accuracyM?.let { " (${formatAccuracy(it)})" } ?: ""
    return "${place.source.label}$accuracy, ${formatTimestamp(Instant.ofEpochMilli(place.timeMillis), today, zone)}"
}

/** « Aujourd'hui », « Demain », « Hier », « Dans 5 jours », « Il y a 3 jours ». */
internal fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = date.toEpochDay() - today.toEpochDay()
    return when {
        days == 0L -> "Aujourd'hui"
        days == 1L -> "Demain"
        days == -1L -> "Hier"
        days > 1L -> "Dans $days${NBSP}jours"
        else -> "Il y a ${-days}${NBSP}jours"
    }
}

/**
 * Flips the hemisphere of a typed coordinate, for numeric keyboards without a minus key:
 * swaps a trailing letter (N and S, E and O) or adds or removes the leading minus sign.
 */
internal fun toggleSign(text: String, isLatitude: Boolean): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "-"
    val swapped = when (trimmed.last().uppercaseChar()) {
        'N' -> if (isLatitude) 'S' else null
        'S' -> if (isLatitude) 'N' else null
        'E' -> if (isLatitude) null else 'O'
        'O', 'W' -> if (isLatitude) null else 'E'
        else -> null
    }
    if (swapped != null) return trimmed.dropLast(1) + swapped
    return when (trimmed.first()) {
        '-', MINUS -> trimmed.drop(1)
        '+' -> "-" + trimmed.drop(1)
        else -> "-$trimmed"
    }
}

internal sealed class CoordinateInput {
    class Valid(val value: Double) : CoordinateInput()
    class Invalid(val message: String) : CoordinateInput()
}

private val DECIMAL_PATTERN = Regex("^[+-]?(\\d+([.,]\\d*)?|[.,]\\d+)$")

/**
 * Parses a latitude or longitude typed in decimal degrees. Accepts « , » or « . », a leading
 * sign (also the « − » character) or a trailing hemisphere letter: N/S, E/O (or W), and « ° ».
 */
internal fun parseCoordinate(text: String, isLatitude: Boolean): CoordinateInput {
    val name = if (isLatitude) "latitude" else "longitude"
    val limit = if (isLatitude) 90 else 180
    var s = text.trim()
        .replace(MINUS, '-')
        .replace(NBSP, ' ')
        .replace(" ", "")
    if (s.isEmpty()) return CoordinateInput.Invalid("Saisissez la $name.")
    var hemisphereSign = 0
    val letter = s.last().uppercaseChar()
    val hemisphere = when {
        isLatitude && letter == 'N' -> 1
        isLatitude && letter == 'S' -> -1
        !isLatitude && letter == 'E' -> 1
        !isLatitude && (letter == 'O' || letter == 'W') -> -1
        else -> 0
    }
    if (hemisphere != 0) {
        hemisphereSign = hemisphere
        s = s.dropLast(1)
    }
    s = s.removeSuffix("°")
    val example = if (isLatitude) "48,8566" else "2,3522"
    if (!DECIMAL_PATTERN.matches(s)) {
        return CoordinateInput.Invalid(
            "${name.replaceFirstChar { it.uppercaseChar() }} invalide : saisissez des degrés décimaux, par exemple $example."
        )
    }
    if (hemisphereSign != 0 && (s.startsWith("-") || s.startsWith("+"))) {
        val letters = if (isLatitude) "N ou S" else "E ou O"
        return CoordinateInput.Invalid("Utilisez soit un signe, soit $letters, mais pas les deux.")
    }
    val magnitude = s.replace(',', '.').toDoubleOrNull()
        ?: return CoordinateInput.Invalid("${name.replaceFirstChar { it.uppercaseChar() }} invalide.")
    val value = if (hemisphereSign != 0) magnitude * hemisphereSign else magnitude
    if (value.isNaN() || abs(value) > limit) {
        return CoordinateInput.Invalid("La $name doit être comprise entre $MINUS$limit et $limit.")
    }
    return CoordinateInput.Valid(if (value == 0.0) 0.0 else value)
}

/** Golden or blue hour spans of a day: « 07:31 – 08:24 et 17:52 – 18:45 », or « aucune ce jour ». */
internal fun formatSpans(spans: List<TimeSpan>, window: DayWindow): String =
    if (spans.isEmpty()) {
        "aucune ce jour"
    } else {
        joinFrench(spans.map { "${formatTimeOfDay(it.start, window)}$NBSP– ${formatTimeOfDay(it.end, window)}" })
    }

/** « 204° SSO, hauteur 28,2° », followed by « sous l'horizon » when the body is not up. */
internal fun describePosition(azimuth: Double, altitude: Double, up: Boolean): String {
    val text = "${formatDirection(azimuth)}, hauteur ${formatAltitude(altitude)}"
    return if (up) text else "$text, sous l'horizon"
}

internal class LiveText(val headline: String, val detail: String)

/** Live summary for today: remaining daylight or time until sunrise, and the clock time of the event. */
internal fun describeSunLive(live: SunLive, now: Instant, zone: ZoneId): LiveText {
    val today = now.atZone(zone).toLocalDate()
    fun at(event: Instant): String {
        val day = if (event.atZone(zone).toLocalDate() == today) "" else "demain "
        return "${day}à ${formatTime(event, zone)}"
    }
    val set = live.nextSet
    val rise = live.nextRise
    return if (live.up) {
        when {
            set == null -> LiveText("Jour polaire", "Le soleil ne se couche pas dans les prochains jours.")
            minutesUntil(set, now) <= MINUTES_PER_DAY ->
                LiveText("Jour restant$NBSP: ${formatRemaining(set, now)}", "Coucher ${at(set)}")
            else -> LiveText("Soleil levé", "Prochain coucher$NBSP: ${formatEventDate(set, zone, today.year)}")
        }
    } else {
        when {
            rise == null -> LiveText("Nuit polaire", "Le soleil ne se lève pas dans les prochains jours.")
            minutesUntil(rise, now) <= MINUTES_PER_DAY ->
                LiveText("Nuit$NBSP: lever dans ${formatRemaining(rise, now)}", "Lever ${at(rise)}")
            else -> LiveText("Soleil couché", "Prochain lever$NBSP: ${formatEventDate(rise, zone, today.year)}")
        }
    }
}

private const val MINUTES_PER_DAY = 24L * 60L

/** Spoken summary of the altitude chart. */
internal fun describeChart(sun: SunDay): String {
    val window = sun.window
    val peak = sun.curveAltitudes.indices.maxByOrNull { sun.curveAltitudes[it] } ?: 0
    val highest = "maximum ${formatAltitude(sun.curveAltitudes[peak])} vers " +
        formatTimeOfDay(instantOf(sun.curveTimes[peak]), window)
    val horizon = when (sun.polar) {
        PolarState.POLAR_DAY -> "le soleil reste au-dessus de l'horizon toute la journée"
        PolarState.POLAR_NIGHT -> "le soleil reste sous l'horizon toute la journée"
        null -> {
            val rises = sun.sunrises.map { formatTimeOfDay(it, window) }
            val sets = sun.sunsets.map { formatTimeOfDay(it, window) }
            listOfNotNull(
                rises.takeIf { it.isNotEmpty() }?.let { "lever à ${joinFrench(it)}" },
                sets.takeIf { it.isNotEmpty() }?.let { "coucher à ${joinFrench(it)}" },
            ).joinToString(", ")
        }
    }
    return "Courbe de la hauteur du soleil sur la journée$NBSP: $highest$NBSP; $horizon."
}
