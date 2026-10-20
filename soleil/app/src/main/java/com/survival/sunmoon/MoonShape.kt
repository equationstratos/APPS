package com.survival.sunmoon

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Side of the disc that is lit, as seen by an observer facing the Moon: a waxing Moon is lit on
 * the right in the northern hemisphere and on the left in the southern hemisphere.
 */
internal fun litOnRight(waxing: Boolean, latitude: Double): Boolean = if (latitude >= 0.0) waxing else !waxing

/**
 * Closed outline of the lit part of a unit disc centred on (0, 0), y pointing down, as
 * x0, y0, x1, y1… The bright limb is a half circle; the terminator is a half ellipse whose
 * horizontal semi-axis is |1 − 2k|, so the enclosed area is exactly k × π.
 */
internal fun litOutline(fraction: Double, litOnRight: Boolean, segments: Int = 48): FloatArray {
    val k = fraction.coerceIn(0.0, 1.0)
    val side = if (litOnRight) 1.0 else -1.0
    val points = FloatArray(4 * (segments + 1))
    var index = 0
    for (i in 0..segments) {
        val theta = -PI / 2.0 + PI * i / segments
        points[index++] = (side * cos(theta)).toFloat()
        points[index++] = sin(theta).toFloat()
    }
    for (i in 0..segments) {
        val theta = PI / 2.0 - PI * i / segments
        points[index++] = (side * (1.0 - 2.0 * k) * cos(theta)).toFloat()
        points[index++] = sin(theta).toFloat()
    }
    return points
}
