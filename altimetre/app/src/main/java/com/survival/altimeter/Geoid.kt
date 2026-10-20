package com.survival.altimeter

import kotlin.math.floor

/**
 * EGM96 geoid height (metres above the WGS 84 ellipsoid) on a 1° grid, bilinear interpolation.
 * GPS chips report the height above the ellipsoid; maps give the altitude above mean sea level
 * (geoid). In France the two differ by about 45 to 55 m, so the GPS altitude must be corrected.
 * Grid: [ROWS] rows from 90° N to 90° S, [COLS] columns from 180° W eastwards, big-endian int16 in decimetres.
 * Interpolation error versus the full 15' model: 0,5 m RMS, a few metres at most near volcanic islands.
 */
class Geoid(private val grid: ShortArray) {
    init {
        require(grid.size == ROWS * COLS) { "Grille du géoïde invalide" }
    }

    fun undulationM(latitude: Double, longitude: Double): Double {
        val lat = latitude.coerceIn(-90.0, 90.0)
        val y = (90.0 - lat) / STEP_DEG
        val row = floor(y).toInt().coerceAtMost(ROWS - 2)
        val fy = y - row
        val lon = ((longitude + 180.0) % 360.0 + 360.0) % 360.0
        val x = lon / STEP_DEG
        val col = floor(x).toInt().coerceIn(0, COLS - 1)
        val fx = x - floor(x)
        val nextCol = (col + 1) % COLS
        val top = at(row, col) * (1 - fx) + at(row, nextCol) * fx
        val bottom = at(row + 1, col) * (1 - fx) + at(row + 1, nextCol) * fx
        return top * (1 - fy) + bottom * fy
    }

    /** Altitude above mean sea level from a WGS 84 ellipsoidal height. */
    fun mslAltitude(latitude: Double, longitude: Double, ellipsoidHeightM: Double): Double =
        ellipsoidHeightM - undulationM(latitude, longitude)

    private fun at(row: Int, col: Int): Double = grid[row * COLS + col] / 10.0

    companion object {
        const val STEP_DEG = 1.0
        const val ROWS = 181
        const val COLS = 360

        fun fromBytes(bytes: ByteArray): Geoid {
            require(bytes.size == ROWS * COLS * 2) { "Taille de la grille du géoïde invalide" }
            val grid = ShortArray(ROWS * COLS) { index ->
                val high = bytes[2 * index].toInt()
                val low = bytes[2 * index + 1].toInt() and 0xFF
                ((high shl 8) or low).toShort()
            }
            return Geoid(grid)
        }
    }
}
