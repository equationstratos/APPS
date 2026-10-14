package com.survival.sound

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

const val MIC_UNAVAILABLE = "Micro indisponible."

class MicUnavailableException(message: String) : Exception(message)

/** Captures the microphone and emits the smoothed sound level (uncalibrated dB) every 100 ms. */
object SoundLevelMeter {
    private const val SAMPLE_RATE = 44_100
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val WINDOW_SAMPLES = SAMPLE_RATE / 10
    private const val WINDOW_SECONDS = 0.1

    // The first buffers after startRecording() are often silent or contain a start-up click.
    private const val WARM_UP_WINDOWS = 2

    // "Fast" time weighting of sound level meters (125 ms) applied to 100 ms windows.
    private val SMOOTHING = 1.0 - exp(-WINDOW_SECONDS / 0.125)

    // Serialises sessions so a quick stop/start waits for the previous AudioRecord to be released.
    private val micLock = Mutex()

    fun levels(context: Context): Flow<Double> = flow {
        micLock.withLock {
            val recorder = openRecorder(context)
            try {
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw MicUnavailableException(
                        "Micro indisponible : il est peut-être utilisé par une autre application."
                    )
                }
                val buffer = ShortArray(WINDOW_SAMPLES)
                var smoothed = Double.NaN
                var skipped = 0
                while (currentCoroutineContext().isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read < 0) {
                        throw MicUnavailableException("Micro indisponible : la capture s'est interrompue.")
                    }
                    if (read == 0) continue
                    if (skipped < WARM_UP_WINDOWS) {
                        skipped++
                        continue
                    }
                    val level = windowLevel(buffer, read)
                    smoothed = if (smoothed.isNaN()) level else smoothed + SMOOTHING * (level - smoothed)
                    emit(smoothed)
                }
            } finally {
                try {
                    recorder.stop()
                } catch (e: IllegalStateException) {
                    // Already stopped: nothing to do before releasing.
                }
                recorder.release()
            }
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission") // Checked explicitly at the top of the function.
    private fun openRecorder(context: Context): AudioRecord {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw MicUnavailableException("Micro indisponible : autorisation refusée.")
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) {
            throw MicUnavailableException("Micro indisponible : format audio non pris en charge.")
        }
        val bufferBytes = maxOf(minBuffer, WINDOW_SAMPLES * 2 * 2)
        for (source in audioSources(context)) {
            val recorder = try {
                AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufferBytes)
            } catch (e: IllegalArgumentException) {
                continue
            } catch (e: SecurityException) {
                throw MicUnavailableException("Micro indisponible : autorisation refusée.")
            }
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            recorder.release()
        }
        throw MicUnavailableException(MIC_UNAVAILABLE)
    }

    // Both sources bypass automatic gain control, which would flatten the measured levels.
    private fun audioSources(context: Context): List<Int> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) {
            listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        } else {
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }
    }

    private fun windowLevel(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        var sumSquares = 0.0
        for (i in 0 until count) {
            val sample = buffer[i].toDouble()
            sum += sample
            sumSquares += sample * sample
        }
        val mean = sum / count
        // Removing the DC offset some microphones add keeps quiet rooms from reading too high.
        val rms = sqrt((sumSquares / count - mean * mean).coerceAtLeast(0.0))
        return if (rms < 1.0) 0.0 else 20.0 * log10(rms)
    }
}
