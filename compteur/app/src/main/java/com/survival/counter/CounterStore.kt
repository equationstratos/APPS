package com.survival.counter

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Single source of truth for the counter, shared by the Compose UI and the
 * hardware volume keys handled in [MainActivity]. Every change is persisted.
 */
class CounterStore(private val prefs: SharedPreferences) {

    var count by mutableIntStateOf(prefs.getInt(KEY_COUNT, 0).coerceIn(0, MAX_COUNT))
        private set

    var step by mutableIntStateOf(prefs.getInt(KEY_STEP, STEPS.first()).takeIf { it in STEPS } ?: STEPS.first())
        private set

    /** Adds [delta] (may be negative), clamped to [0, MAX_COUNT]. Returns true if the count changed. */
    fun add(delta: Int): Boolean {
        val next = (count.toLong() + delta).coerceIn(0L, MAX_COUNT.toLong()).toInt()
        if (next == count) return false
        count = next
        prefs.edit { putInt(KEY_COUNT, next) }
        return true
    }

    /** Returns true if the count changed. */
    fun reset(): Boolean = add(-count)

    fun selectStep(value: Int) {
        if (value !in STEPS || value == step) return
        step = value
        prefs.edit { putInt(KEY_STEP, value) }
    }

    companion object {
        val STEPS = listOf(1, 5, 10)
        const val MAX_COUNT = 999_999
        private const val KEY_COUNT = "compteur_count"
        private const val KEY_STEP = "compteur_step"
    }
}
