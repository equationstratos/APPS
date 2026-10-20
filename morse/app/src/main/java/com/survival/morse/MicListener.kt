package com.survival.morse

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

internal const val MIC_UNAVAILABLE = "Micro indisponible."

internal class MicUnavailableException(message: String) : Exception(message)

internal sealed interface MicEvent {
    /** A mark or silence duration measured by the tone detector. */
    class Key(val event: KeyEvent, val durationMs: Double) : MicEvent

    /** Detector state for the level meter, about 40 times per second. */
    class Level(
        val levelDb: Double,
        val noiseDb: Double,
        val thresholdDb: Double,
        val toneOn: Boolean,
        val toneHz: Int
    ) : MicEvent
}

/** Listens to the microphone and turns a beep or whistle into Morse durations. */
internal object MicListener {
    private const val SAMPLE_RATE = 44_100
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val READ_SAMPLES = 1024

    // The first buffers after startRecording() are often silent or contain a start-up click.
    private const val WARM_UP_READS = 2

    // Serialises sessions so a quick stop/start waits for the previous AudioRecord to be released.
    private val micLock = Mutex()

    fun events(context: Context): Flow<MicEvent> = flow {
        micLock.withLock {
            val recorder = openRecorder(context)
            try {
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw MicUnavailableException(
                        "Micro indisponible : il est peut-être utilisé par une autre application."
                    )
                }
                val keyer = ToneKeyer(SAMPLE_RATE)
                val buffer = ShortArray(READ_SAMPLES)
                val pending = ArrayList<MicEvent>()
                var skipped = 0
                while (currentCoroutineContext().isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read < 0) {
                        throw MicUnavailableException("Micro indisponible : la capture s'est interrompue.")
                    }
                    if (read == 0) continue
                    if (skipped < WARM_UP_READS) {
                        skipped++
                        continue
                    }
                    keyer.feed(buffer, read) { event, durationMs -> pending += MicEvent.Key(event, durationMs) }
                    val detector = keyer.detector
                    pending += MicEvent.Level(
                        levelDb = detector.levelDb,
                        noiseDb = detector.noiseDb,
                        thresholdDb = detector.onThresholdDb,
                        toneOn = detector.isOn,
                        toneHz = detector.toneHz
                    )
                    for (event in pending) emit(event)
                    pending.clear()
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
        val bufferBytes = maxOf(minBuffer, READ_SAMPLES * 2 * 4)
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

    // Sources without noise suppression, which could otherwise erase a steady tone.
    private fun audioSources(context: Context): List<Int> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val preferred = if (unprocessed) {
            listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        } else {
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }
        return preferred + MediaRecorder.AudioSource.MIC
    }
}
