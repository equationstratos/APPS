package com.survival.tuner

import java.util.Locale
import kotlin.math.abs

/** No-break space between a number and its unit (French typography). */
const val NBSP = ' '

/** Typographic minus sign. */
private const val MINUS = '−'

/** Frequency with one decimal and a French decimal comma: "440,0 Hz". */
fun formatFrequency(hz: Double): String = String.format(Locale.FRANCE, "%.1f", hz) + NBSP + "Hz"

/** Whole reference pitch: "440 Hz". */
fun formatReference(hz: Int): String = "$hz${NBSP}Hz"

/** Signed whole cents: "+3", "−12", "0". */
fun formatCents(cents: Int): String = when {
    cents > 0 -> "+$cents"
    cents < 0 -> "$MINUS${-cents}"
    else -> "0"
}

/** Signed cents with the French unit, singular below 2: "0 cent", "−1 cent", "+12 cents". */
fun formatCentsWithUnit(cents: Int): String =
    formatCents(cents) + NBSP + if (abs(cents) >= 2) "cents" else "cent"

/** Spoken form of a deviation for screen readers: "moins 12 cents". */
fun spokenCents(cents: Int): String = when {
    cents > 0 -> "plus " + formatCentsWithUnit(cents).removePrefix("+")
    cents < 0 -> "moins " + formatCentsWithUnit(-cents).removePrefix("+")
    else -> formatCentsWithUnit(0)
}
