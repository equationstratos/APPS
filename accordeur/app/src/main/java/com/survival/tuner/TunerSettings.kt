package com.survival.tuner

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Persistent user choices, observable by Compose. Every change is saved immediately. */
class TunerSettings(private val prefs: SharedPreferences) {

    var referenceHz by mutableIntStateOf(clampReference(prefs.getInt(KEY_REFERENCE, DEFAULT_REFERENCE_HZ)))
        private set

    var notation by mutableStateOf(
        if (prefs.getString(KEY_NOTATION, null) == NOTATION_ENGLISH) Notation.English else Notation.French
    )
        private set

    var instrument by mutableStateOf(instrumentById(prefs.getString(KEY_INSTRUMENT, null)))
        private set

    var richTone by mutableStateOf(prefs.getBoolean(KEY_RICH_TONE, false))
        private set

    fun selectReference(hz: Int) {
        val value = clampReference(hz)
        if (value == referenceHz) return
        referenceHz = value
        prefs.edit { putInt(KEY_REFERENCE, value) }
    }

    fun selectNotation(value: Notation) {
        if (value == notation) return
        notation = value
        prefs.edit {
            putString(KEY_NOTATION, if (value == Notation.English) NOTATION_ENGLISH else NOTATION_FRENCH)
        }
    }

    fun selectInstrument(value: Instrument) {
        if (value.id == instrument.id) return
        instrument = value
        prefs.edit { putString(KEY_INSTRUMENT, value.id) }
    }

    fun selectRichTone(value: Boolean) {
        if (value == richTone) return
        richTone = value
        prefs.edit { putBoolean(KEY_RICH_TONE, value) }
    }

    companion object {
        const val PREFS_NAME = "survie"
        private const val KEY_REFERENCE = "accordeur_la_reference"
        private const val KEY_NOTATION = "accordeur_notation"
        private const val KEY_INSTRUMENT = "accordeur_instrument"
        private const val KEY_RICH_TONE = "accordeur_timbre_riche"
        private const val NOTATION_FRENCH = "fr"
        private const val NOTATION_ENGLISH = "en"
    }
}
