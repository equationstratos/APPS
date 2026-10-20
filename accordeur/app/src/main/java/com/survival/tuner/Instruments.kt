package com.survival.tuner

import kotlin.math.abs

/**
 * Hysteresis of the automatic string choice: another string must be this much closer (cents)
 * than the current one before the selection moves, so a note half-way between two strings or a
 * slightly sharp attack does not make the selection flicker.
 */
const val STRING_SWITCH_MARGIN_CENTS = 30.0

/**
 * A tuning preset. [strings] are MIDI numbers in the physical order players name them (from the
 * string nearest the face, e.g. low Mi to high Mi on a guitar); an empty list means chromatic.
 */
class Instrument(val id: String, val name: String, val strings: List<Int>) {
    val isChromatic: Boolean get() = strings.isEmpty()

    /** Conventional string number: the highest-pitched (last) string is number 1. */
    fun stringNumber(index: Int): Int = strings.size - index
}

val CHROMATIC = Instrument("chromatique", "Chromatique", emptyList())

val INSTRUMENTS: List<Instrument> = listOf(
    CHROMATIC,
    Instrument("guitare", "Guitare standard", listOf(40, 45, 50, 55, 59, 64)),
    Instrument("guitare_drop_d", "Guitare Drop D", listOf(38, 45, 50, 55, 59, 64)),
    Instrument("basse", "Basse 4 cordes", listOf(28, 33, 38, 43)),
    Instrument("ukulele", "Ukulélé", listOf(67, 60, 64, 69)),
    Instrument("violon", "Violon", listOf(55, 62, 69, 76)),
    Instrument("mandoline", "Mandoline", listOf(55, 62, 69, 76)),
    Instrument("alto", "Alto", listOf(48, 55, 62, 69)),
    Instrument("violoncelle", "Violoncelle", listOf(36, 43, 50, 57))
)

fun instrumentById(id: String?): Instrument = INSTRUMENTS.firstOrNull { it.id == id } ?: CHROMATIC

/** Short tuning summary: "Mi2 La2 Ré3 Sol3 Si3 Mi4". */
fun tuningSummary(instrument: Instrument, notation: Notation): String =
    instrument.strings.joinToString(" ") { noteLabel(it, notation) }

/**
 * Index of the string to tune for a detected [frequency]: the closest one in cents, except that
 * the [current] string is kept unless another is closer by more than [STRING_SWITCH_MARGIN_CENTS].
 * Returns null only for a chromatic (string-less) instrument.
 */
fun closestString(frequency: Double, strings: List<Int>, referenceHz: Double, current: Int?): Int? {
    if (strings.isEmpty() || !(frequency > 0.0)) return current?.takeIf { it in strings.indices }
    var best = 0
    var bestDistance = Double.MAX_VALUE
    for ((index, midi) in strings.withIndex()) {
        val distance = abs(centsBetween(frequency, frequencyOf(midi, referenceHz)))
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    if (current == null || current !in strings.indices || current == best) return best
    val currentDistance = abs(centsBetween(frequency, frequencyOf(strings[current], referenceHz)))
    return if (currentDistance - bestDistance > STRING_SWITCH_MARGIN_CENTS) best else current
}

/**
 * What the tuner shows for a measured [frequency]: the [targetMidi] note (the selected string,
 * or the nearest note in chromatic mode), the deviation from it in [cents] (may exceed ±50 on a
 * string) and the nearest note actually played.
 */
class TuningReading(val targetMidi: Int, val cents: Double, val playedMidi: Int, val stringIndex: Int?)

fun tuningReading(frequency: Double, referenceHz: Double, strings: List<Int>, stringIndex: Int?): TuningReading {
    val played = nearestNote(frequency, referenceHz)
    val index = stringIndex?.takeIf { it in strings.indices }
        ?: closestString(frequency, strings, referenceHz, null)
    if (index == null) return TuningReading(played.midi, played.cents, played.midi, null)
    val target = strings[index]
    return TuningReading(target, centsBetween(frequency, frequencyOf(target, referenceHz)), played.midi, index)
}
