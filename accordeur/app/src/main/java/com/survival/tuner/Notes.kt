package com.survival.tuner

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Default pitch of La4 (A4), the international standard (ISO 16). */
const val DEFAULT_REFERENCE_HZ = 440
const val MIN_REFERENCE_HZ = 415
const val MAX_REFERENCE_HZ = 466

/** MIDI number of La4, the note the reference pitch applies to (Do4 = 60). */
const val REFERENCE_MIDI = 69

/** Largest deviation, in whole cents as displayed, still considered in tune. */
const val IN_TUNE_CENTS = 5

enum class Notation { French, English }

enum class TuningStatus { Flat, InTune, Sharp }

private val FRENCH_NAMES = arrayOf("Do", "Do♯", "Ré", "Ré♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")
private val ENGLISH_NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

/** The nearest equal-tempered note ([midi]) and the deviation from it in cents (−50..+50). */
class NoteReading(val midi: Int, val cents: Double)

fun clampReference(hz: Int): Int = hz.coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ)

/** Name of the note without octave: "Do♯" or "C♯". */
fun pitchClassName(midi: Int, notation: Notation): String {
    val names = if (notation == Notation.French) FRENCH_NAMES else ENGLISH_NAMES
    return names[Math.floorMod(midi, 12)]
}

/** Octave in international numbering: La4 = 440 Hz, Do4 = middle C. */
fun octaveOf(midi: Int): Int = Math.floorDiv(midi, 12) - 1

/** Full note label: "Do♯4" or "C♯4". */
fun noteLabel(midi: Int, notation: Notation): String = pitchClassName(midi, notation) + octaveOf(midi)

/** Note label as a screen reader should say it: "Do dièse 4". */
fun spokenNoteLabel(midi: Int, notation: Notation): String =
    pitchClassName(midi, notation).replace("♯", " dièse") + " " + octaveOf(midi)

/** Equal-tempered frequency of [midi] when La4 sounds at [referenceHz]. */
fun frequencyOf(midi: Int, referenceHz: Double): Double =
    referenceHz * 2.0.pow((midi - REFERENCE_MIDI) / 12.0)

/** Fractional MIDI number of [frequency] when La4 sounds at [referenceHz]. */
fun midiOf(frequency: Double, referenceHz: Double): Double =
    REFERENCE_MIDI + 12.0 * log2(frequency / referenceHz)

/** Interval from [target] to [frequency] in cents (positive when [frequency] is higher). */
fun centsBetween(frequency: Double, target: Double): Double = 1200.0 * log2(frequency / target)

fun nearestNote(frequency: Double, referenceHz: Double): NoteReading {
    val exact = midiOf(frequency, referenceHz)
    val midi = exact.roundToInt()
    return NoteReading(midi, (exact - midi) * 100.0)
}

/** Deviation as displayed: whole cents. */
fun roundedCents(cents: Double): Int = cents.roundToInt()

/** Status derived from the displayed (rounded) value, so "+5 cents" always reads as in tune. */
fun tuningStatus(cents: Double): TuningStatus {
    val rounded = roundedCents(cents)
    return when {
        rounded < -IN_TUNE_CENTS -> TuningStatus.Flat
        rounded > IN_TUNE_CENTS -> TuningStatus.Sharp
        else -> TuningStatus.InTune
    }
}

fun statusLabel(status: TuningStatus): String = when (status) {
    TuningStatus.Flat -> "Trop bas"
    TuningStatus.InTune -> "Juste"
    TuningStatus.Sharp -> "Trop haut"
}
