package com.survival.tuner

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlin.concurrent.thread
import kotlin.math.max

private const val TONE_SAMPLE_RATE = 48_000
private const val CHUNK_FRAMES = 480 // 10 ms: latency of note changes and of the stop request

/**
 * Plays the reference tone on a streaming [AudioTrack] fed by a dedicated thread. [frequency]
 * and [rich] can change while playing; the oscillator stays phase-continuous. [stop] returns
 * immediately: the thread fades the tone out, lets the fade reach the speaker, then releases the
 * track. [onError] is delivered on the main thread if the audio output cannot be used.
 */
class TonePlayer(private val onError: () -> Unit) {
    @Volatile
    var frequency: Double = 440.0

    @Volatile
    var rich: Boolean = false

    // Each playback session owns one generation value; bumping it is the stop signal.
    @Volatile
    private var generation = 0

    private var playing = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (playing) return
        playing = true
        val session = ++generation
        thread(name = "accordeur-diapason") { play(session) }
    }

    fun stop() {
        if (!playing) return
        playing = false
        generation++
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun play(session: Int) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        } catch (ignored: RuntimeException) {
            // A higher priority only reduces the risk of underruns; the tone plays without it.
        }
        var track: AudioTrack? = null
        val completed = try {
            val created = createTrack()
            track = created
            created.play()
            stream(created, session)
        } catch (e: RuntimeException) {
            false
        } finally {
            track?.let { shutdown(it) }
        }
        if (!completed && generation == session) {
            mainHandler.post {
                if (generation == session) {
                    playing = false
                    onError()
                }
            }
        }
    }

    /** Writes the tone until the session ends and the fade-out has been played; false on failure. */
    private fun stream(track: AudioTrack, session: Int): Boolean {
        val synth = ToneSynth(TONE_SAMPLE_RATE)
        synth.frequency = frequency
        synth.rich = rich
        synth.noteOn()
        val chunk = ShortArray(CHUNK_FRAMES)
        while (true) {
            if (generation != session) synth.noteOff()
            synth.frequency = frequency
            synth.rich = rich
            synth.render(chunk)
            if (!writeFully(track, chunk, chunk.size)) return false
            if (synth.isSilent) break
        }
        // Push one buffer of silence: when it is accepted, the fade-out has reached the mixer,
        // so releasing the track cannot cut the tone mid-fade.
        chunk.fill(0)
        var remaining = max(track.bufferSizeInFrames, CHUNK_FRAMES)
        while (remaining > 0) {
            val count = minOf(remaining, CHUNK_FRAMES)
            if (!writeFully(track, chunk, count)) return true
            remaining -= count
        }
        return true
    }

    private fun createTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            TONE_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(TONE_SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBytes * 2, CHUNK_FRAMES * 2 * 8))
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("AudioTrack not initialized")
        }
        return track
    }

    private fun writeFully(track: AudioTrack, data: ShortArray, size: Int): Boolean {
        var offset = 0
        while (offset < size) {
            val written = track.write(data, offset, size - offset)
            if (written <= 0) return false
            offset += written
        }
        return true
    }

    private fun shutdown(track: AudioTrack) {
        try {
            track.pause()
            track.flush()
            track.stop()
        } catch (ignored: IllegalStateException) {
            // The track never reached the playing state; releasing it is enough.
        }
        track.release()
    }
}
