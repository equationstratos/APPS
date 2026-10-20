package com.survival.whistle

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlin.concurrent.thread
import kotlin.math.max

private const val CHUNK_FRAMES = 480 // 10 ms per write
private const val BUFFER_FRAMES = 4_800 // 100 ms of margin when the screen is off
private const val DRAIN_LIMIT_FRAMES = SAMPLE_RATE / 2

/**
 * Plays one signal on a streaming [AudioTrack] (usage alarm), synthesized in real time by a
 * dedicated thread. Torch changes are reported when the sound is actually heard (playback head),
 * not when it is written, so the light stays in sync with the speaker. One instance plays once:
 * [stop] is non-blocking, the audio thread fades out, lets the fade reach the speaker, switches the
 * torch off and releases the track by itself.
 */
class SignalEngine(
    mode: SignalMode,
    /** Called on the audio thread when the torch must change state. */
    private val onLight: (on: Boolean) -> Unit,
    /** Called on the main thread when the audio output cannot be used. */
    private val onError: (SignalEngine) -> Unit
) {
    /** Whether the torch follows the sound; may change while playing. */
    @Volatile
    var flash: Boolean = false

    @Volatile
    private var stopRequested = false

    private val synth = Synthesizer(mode.segments)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var worker: Thread? = null

    // Audio thread only: what the torch was last told.
    private var torchLit = false

    fun start() {
        if (worker != null) return
        worker = thread(name = "sifflet-audio") { play() }
    }

    fun stop() {
        stopRequested = true
    }

    private fun play() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var track: AudioTrack? = null
        val completed = try {
            val created = createTrack()
            track = created
            created.play()
            stream(created)
        } catch (e: RuntimeException) {
            false
        } finally {
            track?.let { shutdown(it) }
            setTorch(false)
        }
        if (!completed && !stopRequested) mainHandler.post { onError(this) }
    }

    /** Writes the signal until stopped; returns false if the audio output failed. */
    private fun stream(track: AudioTrack): Boolean {
        val pcm = ShortArray(CHUNK_FRAMES)
        val scratch = FloatArray(CHUNK_FRAMES)
        val marks = ArrayDeque<LightMark>()
        var written = 0L
        var played = 0L
        var lastHead = 0L
        var heardLight = false

        fun updatePlayed() {
            // The head position is an unsigned 32-bit counter that wraps; accumulate deltas.
            val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            played += (head - lastHead) and 0xFFFFFFFFL
            lastHead = head
        }

        while (!synth.isFinished) {
            if (stopRequested) synth.release()
            val base = written
            synth.render(pcm, scratch, CHUNK_FRAMES) { frame, on -> marks.addLast(LightMark(base + frame, on)) }
            if (!writeFully(track, pcm, CHUNK_FRAMES)) return false
            written += CHUNK_FRAMES
            updatePlayed()
            while (marks.isNotEmpty() && marks.first().frame <= played) heardLight = marks.removeFirst().on
            setTorch(flash && heardLight && !stopRequested)
        }

        // Feed silence until the fade-out has been heard, so that releasing the track cannot click.
        setTorch(false)
        pcm.fill(0)
        var extra = 0
        while (played < written && extra < DRAIN_LIMIT_FRAMES) {
            if (!writeFully(track, pcm, CHUNK_FRAMES)) return true
            extra += CHUNK_FRAMES
            updatePlayed()
        }
        return true
    }

    private fun setTorch(on: Boolean) {
        if (on == torchLit) return
        torchLit = on
        onLight(on)
    }

    private fun createTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBytes * 2, BUFFER_FRAMES * 2))
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

private class LightMark(val frame: Long, val on: Boolean)
