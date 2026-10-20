package com.survival.tuner

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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val MIC_UNAVAILABLE = "Micro indisponible."

class MicUnavailableException(message: String) : Exception(message)

/**
 * Captures the microphone (mono, 16-bit PCM) and emits one [TunerFrame] per analysis step, about
 * 20 times per second. Capture and analysis run on a background thread; the recorder is released
 * as soon as the collector is cancelled.
 */
object MicrophoneInput {
    /** 48 kHz is the native rate of most devices; 44,1 kHz is guaranteed by Android. */
    private val SAMPLE_RATES = intArrayOf(48_000, 44_100)
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    /** The first buffers after startRecording() often hold silence or a start-up click. */
    private const val WARM_UP_SECONDS = 0.1

    // Serialises sessions so a quick pause/resume waits for the previous recorder to be released.
    private val micLock = Mutex()

    fun frames(context: Context): Flow<TunerFrame> = flow {
        micLock.withLock {
            val recorder = openRecorder(context)
            try {
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw MicUnavailableException(
                        "Micro indisponible${NBSP}: il est peut-être utilisé par une autre application."
                    )
                }
                val analyzer = PitchAnalyzer(recorder.sampleRate)
                val tracker = PitchTracker()
                val buffer = ShortArray(analyzer.hopSize)
                var warmUp = (recorder.sampleRate * WARM_UP_SECONDS).toInt()
                var pending = 0
                while (currentCoroutineContext().isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read < 0) {
                        throw MicUnavailableException("Micro indisponible${NBSP}: la capture s'est interrompue.")
                    }
                    if (read == 0) continue
                    if (warmUp > 0) {
                        warmUp -= read
                        continue
                    }
                    analyzer.push(buffer, read)
                    pending += read
                    if (pending >= analyzer.hopSize && analyzer.isReady) {
                        pending = 0
                        emit(tracker.update(analyzer.analyze()))
                    }
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
    }.flowOn(Dispatchers.IO).conflate()

    @SuppressLint("MissingPermission") // Checked explicitly at the top of the function.
    private fun openRecorder(context: Context): AudioRecord {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw MicUnavailableException("Micro indisponible${NBSP}: autorisation refusée.")
        }
        for (sampleRate in SAMPLE_RATES) {
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
            if (minBuffer <= 0) continue
            // Room for several analysis steps, so a busy moment never drops samples.
            val bufferBytes = maxOf(minBuffer, sampleRate / FRAMES_PER_SECOND * 2 * 4)
            for (source in audioSources(context)) {
                val recorder = try {
                    AudioRecord(source, sampleRate, CHANNEL, ENCODING, bufferBytes)
                } catch (e: IllegalArgumentException) {
                    continue
                } catch (e: SecurityException) {
                    throw MicUnavailableException("Micro indisponible${NBSP}: autorisation refusée.")
                }
                if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
                recorder.release()
            }
        }
        throw MicUnavailableException(MIC_UNAVAILABLE)
    }

    // VOICE_RECOGNITION has neither automatic gain control nor noise suppression (which would
    // distort the decay of a string or treat a held note as noise) and about 10 dB more gain
    // than UNPROCESSED, which suits the level gate of the tracker. MIC is the last resort.
    private fun audioSources(context: Context): List<Int> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) {
            listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.MIC
            )
        } else {
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        }
    }
}
