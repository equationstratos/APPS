package com.survival.metal

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val TONE_VOLUME = 80
private const val IDLE_POLL_MS = 100L
private const val SLOWEST_INTERVAL_MS = 650f
private const val FASTEST_INTERVAL_MS = 80f
private const val MAX_TONE_MS = 120L
private const val VIBRATION_MS = 30L

/** DTMF tones ordered by rising pitch (697/1209 Hz up to 941/1633 Hz). */
private val PITCHES = intArrayOf(
    ToneGenerator.TONE_DTMF_1,
    ToneGenerator.TONE_DTMF_5,
    ToneGenerator.TONE_DTMF_9,
    ToneGenerator.TONE_DTMF_D
)

/**
 * Emits beeps and/or vibration pulses whose rate (and beep pitch) rise with [strength] in 0..1.
 * A negative [strength] means nothing is detected. The ToneGenerator lives only while the effect
 * is active and is released when sound is turned off or the screen leaves the composition.
 */
@Composable
fun DetectionFeedback(strength: Float, sound: Boolean, vibrator: Vibrator?) {
    val currentStrength by rememberUpdatedState(strength)
    LaunchedEffect(sound, vibrator) {
        if (!sound && vibrator == null) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val tone = if (sound) {
                runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME) }.getOrNull()
            } else {
                null
            }
            try {
                while (isActive) {
                    val s = currentStrength
                    if (s < 0f) {
                        delay(IDLE_POLL_MS)
                        continue
                    }
                    val level = s.coerceIn(0f, 1f)
                    val interval =
                        (SLOWEST_INTERVAL_MS - (SLOWEST_INTERVAL_MS - FASTEST_INTERVAL_MS) * level).toLong()
                    if (tone != null) {
                        val pitch = PITCHES[(level * PITCHES.size).toInt().coerceAtMost(PITCHES.lastIndex)]
                        tone.startTone(pitch, minOf(interval / 2, MAX_TONE_MS).toInt())
                    }
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                    delay(interval)
                }
            } finally {
                tone?.release()
            }
        }
    }
}

/** The device vibrator, or null when there is none. */
fun Context.findVibrator(): Vibrator? {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
    return vibrator?.takeIf { it.hasVibrator() }
}
