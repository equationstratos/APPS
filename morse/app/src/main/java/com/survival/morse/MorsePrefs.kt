package com.survival.morse

import android.content.Context
import androidx.core.content.edit

/** Settings kept between launches (SharedPreferences). */
internal class MorsePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var wpm: Int
        get() = prefs.getInt(KEY_WPM, DEFAULT_WPM).coerceIn(MIN_WPM, MAX_WPM)
        set(value) = prefs.edit { putInt(KEY_WPM, value) }

    var toneHz: Int
        get() = prefs.getInt(KEY_TONE, DEFAULT_TONE_HZ).coerceIn(MIN_TONE_HZ, MAX_TONE_HZ)
        set(value) = prefs.edit { putInt(KEY_TONE, value) }

    var lamp: Boolean
        get() = prefs.getBoolean(KEY_LAMP, false)
        set(value) = prefs.edit { putBoolean(KEY_LAMP, value) }

    var sound: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_SOUND, value) }

    var vibration: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, false)
        set(value) = prefs.edit { putBoolean(KEY_VIBRATION, value) }

    var screen: Boolean
        get() = prefs.getBoolean(KEY_SCREEN, false)
        set(value) = prefs.edit { putBoolean(KEY_SCREEN, value) }

    var repeat: Boolean
        get() = prefs.getBoolean(KEY_REPEAT, false)
        set(value) = prefs.edit { putBoolean(KEY_REPEAT, value) }

    var message: String
        get() = prefs.getString(KEY_MESSAGE, null) ?: ""
        set(value) = prefs.edit { putString(KEY_MESSAGE, value) }

    var sidetone: Boolean
        get() = prefs.getBoolean(KEY_SIDETONE, true)
        set(value) = prefs.edit { putBoolean(KEY_SIDETONE, value) }

    var learnWpm: Int
        get() = prefs.getInt(KEY_LEARN_WPM, DEFAULT_LEARN_WPM).coerceIn(MIN_WPM, MAX_WPM)
        set(value) = prefs.edit { putInt(KEY_LEARN_WPM, value) }

    var learnTyping: Boolean
        get() = prefs.getBoolean(KEY_LEARN_TYPING, false)
        set(value) = prefs.edit { putBoolean(KEY_LEARN_TYPING, value) }

    /** Last dot length estimated by the decoder: the starting point of the next session. */
    var decodeUnitMs: Double
        get() = prefs.getFloat(KEY_DECODE_UNIT, unitMs(DEFAULT_WPM).toFloat()).toDouble().coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)
        set(value) = prefs.edit { putFloat(KEY_DECODE_UNIT, value.toFloat()) }

    var kochLevel: Int
        get() = prefs.getInt(KEY_KOCH_LEVEL, MIN_KOCH_LEVEL).coerceIn(MIN_KOCH_LEVEL, MAX_KOCH_LEVEL)
        set(value) = prefs.edit { putInt(KEY_KOCH_LEVEL, value) }

    private companion object {
        const val PREFS_NAME = "morse"
        const val KEY_WPM = "wpm"
        const val KEY_TONE = "tone_hz"
        const val KEY_LAMP = "out_lamp"
        const val KEY_SOUND = "out_sound"
        const val KEY_VIBRATION = "out_vibration"
        const val KEY_SCREEN = "out_screen"
        const val KEY_REPEAT = "repeat"
        const val KEY_MESSAGE = "message"
        const val KEY_SIDETONE = "sidetone"
        const val KEY_LEARN_WPM = "learn_wpm"
        const val KEY_LEARN_TYPING = "learn_typing"
        const val KEY_KOCH_LEVEL = "koch_level"
        const val KEY_DECODE_UNIT = "decode_unit_ms"
    }
}
