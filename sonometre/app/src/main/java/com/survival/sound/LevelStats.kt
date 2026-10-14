package com.survival.sound

import androidx.compose.runtime.saveable.listSaver
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Running min / max / energy average of uncalibrated levels in dB. */
data class LevelStats(
    val minDb: Double = 0.0,
    val maxDb: Double = 0.0,
    val energySum: Double = 0.0,
    val count: Int = 0
) {
    fun add(db: Double): LevelStats = LevelStats(
        minDb = if (count == 0) db else min(minDb, db),
        maxDb = if (count == 0) db else max(maxDb, db),
        energySum = energySum + 10.0.pow(db / 10.0),
        count = count + 1
    )

    /** Energy (Leq-style) average: 10·log10(mean(10^(dB/10))). Only meaningful when count > 0. */
    val average: Double
        get() = if (count == 0) 0.0 else 10.0 * log10(energySum / count)

    companion object {
        val Saver = listSaver<LevelStats, Any>(
            save = { listOf(it.minDb, it.maxDb, it.energySum, it.count) },
            restore = {
                LevelStats(
                    minDb = it[0] as Double,
                    maxDb = it[1] as Double,
                    energySum = it[2] as Double,
                    count = it[3] as Int
                )
            }
        )
    }
}
