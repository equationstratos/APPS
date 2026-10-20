package com.survival.whistle

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PREFS = "sifflet"
private const val KEY_MAX_VOLUME = "max_volume"
private const val KEY_FLASH = "flash"
private const val KEY_AUTO_STOP = "auto_stop_minutes"
private const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"

/**
 * State shared between the screen and [SignalService] (same process): what is playing and the
 * user options, which the service applies live.
 */
object SignalHub {
    private val mutableStatus = MutableStateFlow(SignalStatus())
    val status: StateFlow<SignalStatus> = mutableStatus.asStateFlow()

    private val mutableOptions = MutableStateFlow(SignalOptions())
    val options: StateFlow<SignalOptions> = mutableOptions.asStateFlow()

    @Volatile
    private var loaded = false

    /** Reads the saved options once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        val minutes = prefs.getInt(KEY_AUTO_STOP, 0)
        mutableOptions.value = SignalOptions(
            maxVolume = prefs.getBoolean(KEY_MAX_VOLUME, true),
            flash = prefs.getBoolean(KEY_FLASH, false),
            autoStopMinutes = if (minutes in AUTO_STOP_CHOICES) minutes else 0
        )
    }

    fun setOptions(context: Context, value: SignalOptions) {
        mutableOptions.value = value
        prefs(context).edit {
            putBoolean(KEY_MAX_VOLUME, value.maxVolume)
            putBoolean(KEY_FLASH, value.flash)
            putInt(KEY_AUTO_STOP, value.autoStopMinutes)
        }
    }

    fun updateStatus(transform: (SignalStatus) -> SignalStatus) {
        mutableStatus.update(transform)
    }

    fun notificationsAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTIFICATIONS_ASKED, false)

    fun markNotificationsAsked(context: Context) {
        prefs(context).edit { putBoolean(KEY_NOTIFICATIONS_ASKED, true) }
    }

    /** Starts (or switches to) [mode]; false if the system refused to start the service. */
    fun start(context: Context, mode: SignalMode, hold: Boolean): Boolean {
        val intent = Intent(context, SignalService::class.java)
            .setAction(SignalService.ACTION_START)
            .putExtra(SignalService.EXTRA_MODE, mode.name)
            .putExtra(SignalService.EXTRA_HOLD, hold)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    fun stop(context: Context) {
        try {
            context.startService(Intent(context, SignalService::class.java).setAction(SignalService.ACTION_STOP))
        } catch (e: IllegalStateException) {
            // Not allowed from the background: nothing is playing in that case.
        } catch (e: SecurityException) {
            // Same as above.
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
