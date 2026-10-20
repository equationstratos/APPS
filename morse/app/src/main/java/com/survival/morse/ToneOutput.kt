package com.survival.morse

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val SAMPLE_RATE = 48_000
private const val CHUNK_FRAMES = 96 // 2 ms: reaction time of the live key
private const val RAMP_FRAMES = SAMPLE_RATE * 5 / 1000 // 5 ms rise and fall: no clicks
private const val AMPLITUDE = 0.6 * Short.MAX_VALUE
private const val TAIL_MS = 150
private const val JOIN_TIMEOUT_MS = 500L

private interface ToneSource {
    fun isOn(frame: Long): Boolean
    fun isFinished(frame: Long): Boolean
}

/**
 * Sine tone on a streaming [AudioTrack] fed by a dedicated thread. It either follows a [Timeline]
 * sample by sample (the audio clock times the elements, so they cannot drift), or a live key for
 * the manipulator. Each edge is shaped by a 5 ms raised-cosine ramp.
 */
internal class ToneOutput {
    // Each playback session owns one generation value; bumping it is the stop signal.
    @Volatile
    private var generation = 0

    @Volatile
    private var keyDown = false

    @Volatile
    var isBusy = false
        private set

    private var worker: Thread? = null

    @Synchronized
    fun play(timeline: Timeline, unitMs: Double, frequencyHz: Int, repeat: Boolean) {
        if (timeline.isEmpty) {
            stop()
            return
        }
        val schedule = FrameSchedule(timeline, SAMPLE_RATE * unitMs / 1000.0, repeat)
        start(
            frequencyHz,
            object : ToneSource {
                override fun isOn(frame: Long): Boolean = schedule.isOn(frame)
                override fun isFinished(frame: Long): Boolean = schedule.isFinished(frame)
            }
        )
    }

    /** Starts a silent stream that sounds while the key is down (see [setKey]). */
    @Synchronized
    fun startLive(frequencyHz: Int) {
        keyDown = false
        start(
            frequencyHz,
            object : ToneSource {
                override fun isOn(frame: Long): Boolean = keyDown
                override fun isFinished(frame: Long): Boolean = false
            }
        )
    }

    fun setKey(down: Boolean) {
        keyDown = down
    }

    /** Stops the sound at once and waits for the audio thread to release the track. */
    @Synchronized
    fun stop() {
        keyDown = false
        val current = worker ?: return
        generation++
        current.join(JOIN_TIMEOUT_MS)
        worker = null
        isBusy = false
    }

    private fun start(frequencyHz: Int, source: ToneSource) {
        stop()
        val session = generation
        isBusy = true
        worker = thread(name = "morse-tone") { render(session, frequencyHz, source) }
    }

    private fun render(session: Int, frequencyHz: Int, source: ToneSource) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var track: AudioTrack? = null
        try {
            val created = createTrack()
            track = created
            created.play()
            stream(created, session, frequencyHz, source)
        } catch (e: RuntimeException) {
            // No audio output available: stay silent rather than crash.
        } finally {
            track?.let { shutdown(it) }
            if (generation == session) isBusy = false
        }
    }

    private fun stream(track: AudioTrack, session: Int, frequencyHz: Int, source: ToneSource) {
        val chunk = ShortArray(CHUNK_FRAMES)
        val step = 2.0 * PI * frequencyHz / SAMPLE_RATE
        var phase = 0.0
        var level = 0.0
        var frame = 0L
        while (generation == session) {
            for (i in 0 until CHUNK_FRAMES) {
                level = if (source.isOn(frame)) {
                    min(1.0, level + 1.0 / RAMP_FRAMES)
                } else {
                    max(0.0, level - 1.0 / RAMP_FRAMES)
                }
                val gain = 0.5 - 0.5 * cos(PI * level)
                chunk[i] = (sin(phase) * gain * AMPLITUDE).toInt().toShort()
                phase += step
                if (phase >= 2.0 * PI) phase -= 2.0 * PI
                frame++
            }
            if (!writeFully(track, chunk)) return
            if (level == 0.0 && source.isFinished(frame)) {
                // Silence until the buffered sound has been played, then release the track.
                chunk.fill(0)
                var tail = track.bufferSizeInFrames + SAMPLE_RATE * TAIL_MS / 1000
                while (tail > 0 && generation == session) {
                    if (!writeFully(track, chunk)) return
                    tail -= CHUNK_FRAMES
                }
                return
            }
        }
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
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(max(minBytes * 2, CHUNK_FRAMES * 2 * 8))
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("AudioTrack not initialized")
        }
        return track
    }

    private fun writeFully(track: AudioTrack, data: ShortArray): Boolean {
        var offset = 0
        while (offset < data.size) {
            val written = track.write(data, offset, data.size - offset)
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
