package com.survival.altimeter

import android.content.Context
import androidx.core.content.edit

/** Small settings and state kept in SharedPreferences (the 72 h history lives in its own file). */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var calibration: Calibration
        get() = Calibration.decode(prefs.getString(KEY_CALIBRATION, null))
        set(value) = prefs.edit { putString(KEY_CALIBRATION, value.encode()) }

    var unit: AltitudeUnit
        get() = AltitudeUnit.fromKey(prefs.getString(KEY_UNIT, null))
        set(value) = prefs.edit { putString(KEY_UNIT, value.key) }

    /** Background logging is on by default so the weather tendency is ready when it is needed. */
    var backgroundLogging: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND, true)
        set(value) = prefs.edit { putBoolean(KEY_BACKGROUND, value) }

    var session: ElevationSession?
        get() = ElevationSession.decode(prefs.getString(KEY_SESSION, null))
        set(value) = prefs.edit {
            if (value == null) remove(KEY_SESSION) else putString(KEY_SESSION, value.encode())
        }

    var chartHours: Int
        get() = prefs.getInt(KEY_CHART_HOURS, 24).takeIf { it == 24 || it == 72 } ?: 24
        set(value) = prefs.edit { putInt(KEY_CHART_HOURS, value) }

    private companion object {
        const val PREFS_NAME = "survie"
        const val KEY_CALIBRATION = "altimetre_etalonnage"
        const val KEY_UNIT = "altimetre_unite"
        const val KEY_BACKGROUND = "altimetre_arriere_plan"
        const val KEY_SESSION = "altimetre_session"
        const val KEY_CHART_HOURS = "altimetre_graphique_heures"
    }
}
