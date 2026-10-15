package com.survival.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SAMPLE_RATE = 48_000
private const val CHUNK_FRAMES = 240 // 5 ms: granularity of the beat-to-UI synchronisation
private const val CLICK_MS = 30
private const val CLICK_DECAY_S = 0.008
private const val ACCENT_HZ = 1_500.0
private const val NORMAL_HZ = 1_000.0
private const val JOIN_TIMEOUT_MS = 500L

/**
 * Sample-accurate metronome. A single streaming [AudioTrack] is fed by a dedicated thread: every
 * beat is exactly round(48000 * 60 / bpm) samples (click + silence), so the tempo is defined by
 * the audio clock instead of timers and cannot drift. Tempo and signature changes are read at the
 * start of each beat. Callbacks are delivered on the main thread.
 */
class MetronomeEngine(
    private val onBeat: (beatInBar: Int) -> Unit,
    private val onError: () -> Unit
) {
    @Volatile
    var bpm: Int = DEFAULT_BPM

    @Volatile
    var beatsPerBar: Int = DEFAULT_BEATS

    // Each playback session owns one generation value; bumping it is the stop signal.
    @Volatile
    private var generation = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val accentClick = click(ACCENT_HZ, amplitude = 0.95)
    private val normalClick = click(NORMAL_HZ, amplitude = 0.65)
    private var worker: Thread? = null

    fun start(bpm: Int, beatsPerBar: Int) {
        stop()
        this.bpm = bpm
        this.beatsPerBar = beatsPerBar
        val session = generation
        worker = thread(name = "metronome-audio") { play(session) }
    }

    /** Stops playback, waits for the audio thread to release the track and drops pending callbacks. */
    fun stop() {
        val current = worker ?: return
        generation++
        current.join(JOIN_TIMEOUT_MS)
        worker = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun play(session: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
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
            mainHandler.post { if (generation == session) onError() }
        }
    }

    /** Writes beats until the session ends; returns false if the audio output failed. */
    private fun stream(track: AudioTrack, session: Int): Boolean {
        val chunk = ShortArray(CHUNK_FRAMES)
        val pendingBeats = ArrayDeque<BeatMark>()
        var writtenFrames = 0L
        var playedFrames = 0L
        var lastHead = 0L
        var beatInBar = 0
        while (generation == session) {
            val perBar = beatsPerBar.coerceIn(1, MAX_BEATS)
            if (beatInBar >= perBar) beatInBar = 0
            val framesPerBeat = (SAMPLE_RATE * 60.0 / bpm.coerceIn(MIN_BPM, MAX_BPM)).roundToInt()
            val click = if (beatInBar == 0 && perBar > 1) accentClick else normalClick
            pendingBeats.addLast(BeatMark(writtenFrames, beatInBar))

            var position = 0
            while (position < framesPerBeat) {
                if (generation != session) return true
                val count = min(CHUNK_FRAMES, framesPerBeat - position)
                val clickPart = (click.size - position).coerceIn(0, count)
                if (clickPart > 0) System.arraycopy(click, position, chunk, 0, clickPart)
                if (clickPart < count) chunk.fill(0, clickPart, count)
                if (!writeFully(track, chunk, count)) return false
                position += count
                writtenFrames += count

                // The head position is an unsigned 32-bit counter that wraps; accumulate deltas.
                val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                playedFrames += (head - lastHead) and 0xFFFFFFFFL
                lastHead = head
                while (pendingBeats.isNotEmpty() && pendingBeats.first().frame <= playedFrames) {
                    val beat = pendingBeats.removeFirst().beatInBar
                    mainHandler.post { if (generation == session) onBeat(beat) }
                }
            }
            beatInBar++
        }
        return true
    }

    private fun createTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
            // pause + flush drops the buffered audio so the sound stops immediately.
            track.pause()
            track.flush()
            track.stop()
        } catch (ignored: IllegalStateException) {
            // The track never reached the playing state; releasing it is enough.
        }
        track.release()
    }
}

private class BeatMark(val frame: Long, val beatInBar: Int)

/** A short sine burst with an exponential decay, faded to zero at the end to avoid a pop. */
private fun click(frequency: Double, amplitude: Double): ShortArray {
    val length = SAMPLE_RATE * CLICK_MS / 1000
    return ShortArray(length) { i ->
        val t = i.toDouble() / SAMPLE_RATE
        val envelope = exp(-t / CLICK_DECAY_S) * (1.0 - i.toDouble() / length)
        (sin(2.0 * PI * frequency * t) * envelope * amplitude * Short.MAX_VALUE).roundToInt().toShort()
    }
}
