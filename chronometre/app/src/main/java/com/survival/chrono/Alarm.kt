package com.survival.chrono

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val ALARM_CHECK_MS = 1_000L
private const val BEEP_MS = 700
private const val MAX_SILENT_CHECKS = 2
private val VIBRATION_PATTERN = longArrayOf(0L, 700L, 500L)

private val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/**
 * Looping alarm: default alarm sound (then notification sound, then a generated tone) plus a
 * repeating vibration. Thread-safe: audio is started off the main thread.
 */
internal class Alarm(private val context: Context) {
    private var ringtone: Ringtone? = null
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var silentChecks = 0

    /** Rings until [deadline] (elapsedRealtime) or until the calling coroutine is cancelled. */
    suspend fun ringUntil(deadline: Long) {
        try {
            withContext(Dispatchers.IO) {
                if (SystemClock.elapsedRealtime() >= deadline) return@withContext
                start()
                while (true) {
                    val left = deadline - SystemClock.elapsedRealtime()
                    if (left <= 0L) break
                    delay(minOf(left, ALARM_CHECK_MS))
                    keepAlive()
                }
            }
        } finally {
            stop()
        }
    }

    @Synchronized
    private fun start() {
        stop()
        ringtone = createRingtone()
        if (ringtone == null) startTone()
        vibrator = defaultVibrator()?.takeIf { it.hasVibrator() }?.also { vibrate(it) }
    }

    /** Restarts a sound that ended (no native looping before Android 9) or falls back to a tone. */
    @Synchronized
    private fun keepAlive() {
        val current = ringtone
        if (current == null) {
            beep()
            return
        }
        if (current.isPlaying) {
            silentChecks = 0
            return
        }
        silentChecks++
        if (silentChecks > MAX_SILENT_CHECKS) {
            runCatching { current.stop() }
            ringtone = null
            startTone()
        } else {
            runCatching { current.play() }
        }
    }

    @Synchronized
    private fun stop() {
        ringtone?.let { runCatching { it.stop() } }
        ringtone = null
        tone?.let { runCatching { it.release() } }
        tone = null
        vibrator?.cancel()
        vibrator = null
        silentChecks = 0
    }

    private fun createRingtone(): Ringtone? {
        val type = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION).firstOrNull { candidate ->
            runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, candidate) }.getOrNull() != null
        } ?: return null
        val sound = runCatching { RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(type)) }
            .getOrNull() ?: return null
        return runCatching {
            sound.audioAttributes = ALARM_AUDIO
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) sound.isLooping = true
            sound.play()
            sound
        }.getOrNull()
    }

    private fun startTone() {
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
        beep()
    }

    private fun beep() {
        tone?.let { generator -> runCatching { generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_MS) } }
    }

    private fun defaultVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }

    private fun vibrate(target: Vibrator) {
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrateLegacy(target, effect)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(target: Vibrator, effect: VibrationEffect) {
        target.vibrate(effect, ALARM_AUDIO)
    }
}
