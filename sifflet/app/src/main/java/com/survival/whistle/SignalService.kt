package com.survival.whistle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "signal"
private const val NOTIFICATION_ID = 1
private const val WAKE_LOCK_TAG = "Sifflet:signal"

/** Safety timeout of the wake lock; renewed every hour while a signal plays, released at the stop. */
private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
private const val WAKE_LOCK_RENEW_MS = 60L * 60 * 1000

/**
 * Foreground service (type mediaPlayback) that keeps the signal going with the screen off: audio
 * engine, torch, wake lock, alarm volume and the notification with its "Arrêter" action.
 */
class SignalService : Service() {

    companion object {
        const val ACTION_START = "com.survival.whistle.START"
        const val ACTION_STOP = "com.survival.whistle.STOP"
        const val EXTRA_MODE = "mode"
        const val EXTRA_HOLD = "hold"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var engine: SignalEngine? = null
    private var session = 0
    private var torch: TorchWorker? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var originalAlarmVolume = -1
    private var appliedOptions: SignalOptions? = null
    private var audioManager: AudioManager? = null

    private val autoStop = Runnable { stopEverything(failed = false) }

    private val renewWakeLock = object : Runnable {
        override fun run() {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
            mainHandler.postDelayed(this, WAKE_LOCK_RENEW_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SignalHub.load(this)
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
        scope.launch { SignalHub.options.collect { applyOptions(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val mode = SignalMode.fromName(intent.getStringExtra(EXTRA_MODE)) ?: SignalMode.Whistle
            val hold = intent.getBooleanExtra(EXTRA_HOLD, false)
            // startForeground must come first: the system allows only a few seconds.
            if (enterForeground(mode, hold)) {
                startPlayback(mode, hold)
                return START_NOT_STICKY
            }
            stopPlayback(failed = true)
        } else {
            stopPlayback(failed = false)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (engine != null) stopPlayback(failed = false)
        mainHandler.removeCallbacksAndMessages(null)
        torch?.shutdown()
        torch = null
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(mode: SignalMode, hold: Boolean): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(mode, hold), type)
        true
    } catch (e: RuntimeException) {
        false
    }

    private fun startPlayback(mode: SignalMode, hold: Boolean) {
        // A signal already playing fades out on its own thread while the new one starts.
        engine?.stop()
        session++
        val current = session
        val worker = torch ?: TorchWorker(this) { state ->
            SignalHub.updateStatus { if (it.mode != null) it.copy(torch = state) else it }
        }.also { torch = it }
        worker.begin(current)

        val options = SignalHub.options.value
        val created = SignalEngine(
            mode,
            onLight = { on -> worker.set(current, on) },
            onError = { failed -> if (engine === failed) stopEverything(failed = true) }
        )
        created.flash = options.flash
        engine = created
        appliedOptions = options

        acquireWakeLock()
        requestAudioFocus()
        val volumeBlocked = if (options.maxVolume) !boostVolume() else false
        if (!options.maxVolume) restoreVolume()

        val now = SystemClock.elapsedRealtime()
        val autoStopAt = scheduleAutoStop(if (hold) 0 else options.autoStopMinutes, now)
        SignalHub.updateStatus {
            SignalStatus(mode = mode, hold = hold, startedAt = now, autoStopAt = autoStopAt, volumeBlocked = volumeBlocked)
        }
        created.start()
    }

    private fun stopPlayback(failed: Boolean) {
        mainHandler.removeCallbacks(autoStop)
        mainHandler.removeCallbacks(renewWakeLock)
        val current = engine
        engine = null
        appliedOptions = null
        if (current != null) {
            current.stop()
            torch?.set(session, false)
        }
        restoreVolume()
        abandonAudioFocus()
        wakeLock?.let { if (it.isHeld) it.release() }
        SignalHub.updateStatus { SignalStatus(audioFailed = failed) }
    }

    private fun stopEverything(failed: Boolean) {
        stopPlayback(failed)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Applies option changes made while a signal plays. */
    private fun applyOptions(options: SignalOptions) {
        val running = engine ?: return
        val previous = appliedOptions
        appliedOptions = options
        running.flash = options.flash
        if (previous?.maxVolume != options.maxVolume) {
            val blocked = if (options.maxVolume) {
                !boostVolume()
            } else {
                restoreVolume()
                false
            }
            SignalHub.updateStatus { it.copy(volumeBlocked = blocked) }
        }
        if (previous?.autoStopMinutes != options.autoStopMinutes && !SignalHub.status.value.hold) {
            val at = scheduleAutoStop(options.autoStopMinutes, SystemClock.elapsedRealtime())
            SignalHub.updateStatus { it.copy(autoStopAt = at) }
        }
    }

    /** Schedules the automatic stop [minutes] after [now]; returns its time, or 0 when none. */
    private fun scheduleAutoStop(minutes: Int, now: Long): Long {
        mainHandler.removeCallbacks(autoStop)
        if (minutes <= 0) return 0
        val delay = minutes * 60_000L
        mainHandler.postDelayed(autoStop, delay)
        return now + delay
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
            ?: return
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        mainHandler.removeCallbacks(renewWakeLock)
        mainHandler.postDelayed(renewWakeLock, WAKE_LOCK_RENEW_MS)
    }

    /** Raises the alarm volume to its maximum; false when the system refuses (Do Not Disturb). */
    private fun boostVolume(): Boolean {
        val audio = audioManager ?: return false
        val firstChange = originalAlarmVolume < 0
        return try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            if (firstChange) originalAlarmVolume = current
            if (current < max) audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            audio.getStreamVolume(AudioManager.STREAM_ALARM) >= max
        } catch (e: SecurityException) {
            if (firstChange) originalAlarmVolume = -1
            false
        }
    }

    private fun restoreVolume() {
        val original = originalAlarmVolume
        if (original < 0) return
        originalAlarmVolume = -1
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
        } catch (e: SecurityException) {
            // Do Not Disturb forbids the change: the user keeps the current volume.
        }
    }

    /** Pauses music players while the signal plays (they resume after the stop). */
    private fun requestAudioFocus() {
        if (focusRequest != null) return
        val audio = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            // The signal keeps playing whatever happens: a call for help has priority.
            .setOnAudioFocusChangeListener { }
            .build()
        audio.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonAudioFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        audioManager?.abandonAudioFocusRequest(request)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Signal en cours", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Indique qu'un signal est diffusé et permet de l'arrêter."
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(mode: SignalMode, hold: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SignalService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle(if (hold) "Sifflet en cours" else "${mode.title} en cours")
            .setContentText(if (hold) "Relâchez le bouton pour arrêter." else "Le signal continue même écran éteint.")
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_signal, "Arrêter", stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
