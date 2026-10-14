package com.survival.compass

import kotlin.math.roundToInt

private val CARDINALS_16 = arrayOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"
)

/** Maps any angle in degrees to [0, 360). */
fun normalizeDegrees(degrees: Float): Float {
    val r = degrees % 360f
    return if (r < 0f) r + 360f else r
}

/** Whole-degree bearing in 0..359, as displayed. */
fun roundedBearing(degrees: Float): Int = normalizeDegrees(degrees).roundToInt() % 360

/** French 16-point compass name of a whole-degree bearing. */
fun cardinalOf(bearing: Int): String {
    val index = ((normalizeDegrees(bearing.toFloat()) + 11.25f) / 22.5f).toInt() % 16
    return CARDINALS_16[index]
}

/** Shortest signed rotation from [from] to [to], in (-180, 180]; positive means clockwise. */
fun signedDelta(from: Float, to: Float): Float {
    val d = normalizeDegrees(to - from)
    return if (d > 180f) d - 360f else d
}
