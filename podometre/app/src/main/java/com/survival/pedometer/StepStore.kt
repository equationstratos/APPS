package com.survival.pedometer

import android.content.Context
import android.provider.Settings
import java.time.LocalDate

/**
 * Today's step tally and user settings, persisted in the shared "survie" preferences.
 *
 * TYPE_STEP_COUNTER reports steps since boot, so the last raw value seen is kept as a baseline
 * and each new reading adds the difference. A new day restarts the tally from the next reading;
 * a reboot (counter below the baseline or a different boot count) adds the whole new counter.
 */
class StepStore(context: Context) {

    private val prefs = context.getSharedPreferences("survie", Context.MODE_PRIVATE)

    private val bootCount: Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN)
    }.getOrDefault(UNKNOWN)

    var goal: Int
        get() = prefs.getInt(KEY_GOAL, DEFAULT_GOAL).takeIf { it in GOALS } ?: DEFAULT_GOAL
        set(value) {
            prefs.edit().putInt(KEY_GOAL, value).apply()
        }

    var strideCm: Int
        get() = prefs.getInt(KEY_STRIDE, DEFAULT_STRIDE_CM).coerceIn(MIN_STRIDE_CM, MAX_STRIDE_CM)
        set(value) {
            prefs.edit().putInt(KEY_STRIDE, value.coerceIn(MIN_STRIDE_CM, MAX_STRIDE_CM)).apply()
        }

    fun todaySteps(): Int {
        ensureToday()
        return prefs.getInt(KEY_STEPS, 0)
    }

    fun onStepCounter(counter: Long): Int {
        ensureToday()
        if (counter < 0L) return prefs.getInt(KEY_STEPS, 0)
        val baseline = prefs.getLong(KEY_BASELINE, UNKNOWN.toLong())
        val storedBoot = prefs.getInt(KEY_BOOT, UNKNOWN)
        val rebooted = counter < baseline ||
            (bootCount != UNKNOWN && storedBoot != UNKNOWN && bootCount != storedBoot)
        val added = when {
            baseline < 0L -> 0L
            rebooted -> counter
            else -> counter - baseline
        }
        val total = clampSteps(prefs.getInt(KEY_STEPS, 0).toLong() + added)
        prefs.edit()
            .putInt(KEY_STEPS, total)
            .putLong(KEY_BASELINE, counter)
            .putInt(KEY_BOOT, bootCount)
            .apply()
        return total
    }

    fun addSteps(count: Int): Int {
        ensureToday()
        val total = clampSteps(prefs.getInt(KEY_STEPS, 0).toLong() + count)
        prefs.edit().putInt(KEY_STEPS, total).apply()
        return total
    }

    fun resetToday(): Int {
        ensureToday()
        prefs.edit().putInt(KEY_STEPS, 0).apply()
        return 0
    }

    private fun ensureToday() {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DATE, null) != today) {
            prefs.edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_STEPS, 0)
                .putLong(KEY_BASELINE, UNKNOWN.toLong())
                .apply()
        }
    }

    private fun clampSteps(value: Long): Int = value.coerceIn(0L, MAX_STEPS.toLong()).toInt()

    companion object {
        val GOALS = listOf(5_000, 8_000, 10_000, 12_000)
        const val DEFAULT_GOAL = 10_000
        const val DEFAULT_STRIDE_CM = 75
        const val MIN_STRIDE_CM = 30
        const val MAX_STRIDE_CM = 150

        private const val MAX_STEPS = 1_000_000
        private const val UNKNOWN = -1

        private const val KEY_DATE = "pedometer_date"
        private const val KEY_STEPS = "pedometer_steps"
        private const val KEY_BASELINE = "pedometer_baseline"
        private const val KEY_BOOT = "pedometer_boot_count"
        private const val KEY_GOAL = "pedometer_goal"
        private const val KEY_STRIDE = "pedometer_stride_cm"
    }
}
