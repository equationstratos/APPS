package com.survival.level

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.abs

const val PREFS_NAME = "survie"

/** Calibrating only makes sense on a nearly level reference surface. */
const val MAX_CALIBRATION_DEG = 5f

private const val EDGE_COUNT = 4
private const val KEY_FLAT_X = "niveau_calib_plat_x"
private const val KEY_FLAT_Y = "niveau_calib_plat_y"

private fun edgeKey(edge: Int) = "niveau_calib_tranche_$edge"

data class FlatOffset(val x: Float, val y: Float)

/** Offsets in degrees: one pair for the flat mode, one value per edge for the upright mode. */
data class Calibration(
    val flat: FlatOffset? = null,
    val edges: List<Float?> = List(EDGE_COUNT) { null }
) {
    fun isSetFor(reading: LevelReading): Boolean = when (reading) {
        is LevelReading.Flat -> flat != null
        is LevelReading.Upright -> edges.getOrNull(reading.edge) != null
    }

    fun applyTo(reading: LevelReading): LevelReading = when (reading) {
        is LevelReading.Flat -> flat?.let { LevelReading.Flat(reading.x - it.x, reading.y - it.y) } ?: reading
        is LevelReading.Upright -> edges.getOrNull(reading.edge)?.let { reading.copy(angle = reading.angle - it) } ?: reading
    }

    /** Takes the raw [reading] as the true horizontal for its mode (and edge). */
    fun calibratedOn(reading: LevelReading): Calibration = when (reading) {
        is LevelReading.Flat -> copy(flat = FlatOffset(reading.x, reading.y))
        is LevelReading.Upright -> copy(edges = edges.mapIndexed { i, old -> if (i == reading.edge) reading.angle else old })
    }

    fun clearedFor(reading: LevelReading): Calibration = when (reading) {
        is LevelReading.Flat -> copy(flat = null)
        is LevelReading.Upright -> copy(edges = edges.mapIndexed { i, old -> if (i == reading.edge) null else old })
    }
}

fun SharedPreferences.loadCalibration(): Calibration {
    val flatX = readOffset(KEY_FLAT_X)
    val flatY = readOffset(KEY_FLAT_Y)
    return Calibration(
        flat = if (flatX != null && flatY != null) FlatOffset(flatX, flatY) else null,
        edges = List(EDGE_COUNT) { readOffset(edgeKey(it)) }
    )
}

fun SharedPreferences.saveCalibration(calibration: Calibration) {
    edit {
        writeOffset(KEY_FLAT_X, calibration.flat?.x)
        writeOffset(KEY_FLAT_Y, calibration.flat?.y)
        calibration.edges.forEachIndexed { edge, offset -> writeOffset(edgeKey(edge), offset) }
    }
}

private fun SharedPreferences.readOffset(key: String): Float? {
    if (!contains(key)) return null
    return getFloat(key, 0f).takeIf { it.isFinite() && abs(it) <= MAX_CALIBRATION_DEG }
}

private fun SharedPreferences.Editor.writeOffset(key: String, value: Float?) {
    if (value == null) remove(key) else putFloat(key, value)
}
