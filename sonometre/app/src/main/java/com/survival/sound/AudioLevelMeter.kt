package com.survival.sound

import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.log10

class AudioLevelMeter(private val onLevelUpdated: (Float) -> Unit) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val bufferSize = 4096
    private var recordingThread: Thread? = null

    fun start() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            maxOf(minBufferSize, bufferSize)
        )

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    val dB = calculateDecibels(buffer, read)
                    onLevelUpdated(dB)
                }
            }
        }.apply { start() }
    }

    fun stop() {
        isRecording = false
        recordingThread?.join(1000)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun calculateDecibels(audioData: ShortArray, numSamples: Int): Float {
        var sum = 0.0
        for (i in 0 until numSamples) {
            val sample = audioData[i].toDouble()
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / numSamples)
        val db = 20 * log10(rms / REFERENCE_AMPLITUDE)
        return maxOf(0f, db.toFloat()).coerceAtMost(140f)
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
        private val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
        private const val REFERENCE_AMPLITUDE = 32768.0
    }
}
