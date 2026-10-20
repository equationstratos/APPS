package com.survival.pulse

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * The last [HISTORY_SIZE] measurements, observable by Compose and saved on every change
 * (SharedPreferences writes its file atomically, through a backup copy).
 */
internal class HistoryStore(private val prefs: SharedPreferences) {

    var entries by mutableStateOf(HistoryCodec.decode(readStored()))
        private set

    /** Saves a new measurement and returns its identifier. */
    fun add(entry: HistoryEntry): Long {
        val id = entries.freeTime(entry.timeMs)
        save(entries.plusEntry(entry.copy(timeMs = id)))
        return id
    }

    fun setContext(id: Long, context: MeasureContext) {
        if (entries.none { it.timeMs == id && it.context != context }) return
        save(entries.withContext(id, context))
    }

    fun delete(id: Long) {
        if (entries.none { it.timeMs == id }) return
        save(entries.without(id))
    }

    fun clear() {
        if (entries.isEmpty()) return
        save(emptyList())
    }

    private fun save(updated: List<HistoryEntry>) {
        entries = updated
        prefs.edit { putString(KEY_HISTORY, HistoryCodec.encode(updated)) }
    }

    private fun readStored(): String? = try {
        prefs.getString(KEY_HISTORY, null)
    } catch (e: ClassCastException) {
        null
    }

    companion object {
        const val PREFS_NAME = "survie"
        private const val KEY_HISTORY = "pouls_historique"
    }
}
