package com.survival.altimeter

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The 72 h pressure log in filesDir, written atomically. The screen and the background worker run
 * in the same process, so a single lock is enough to keep their writes from overlapping.
 */
object HistoryStore {
    private const val FILE_NAME = "pression_72h.txt"
    private val lock = Any()

    fun load(context: Context, nowMs: Long = System.currentTimeMillis()): List<PressureSample> =
        synchronized(lock) { PressureHistory.prune(read(context), nowMs) }

    /** Adds one reading (skipped when another one is less than a minute away) and returns the updated log. */
    fun add(context: Context, sample: PressureSample): List<PressureSample> = synchronized(lock) {
        val current = PressureHistory.prune(read(context), sample.timeMs)
        val next = PressureHistory.append(current, sample)
        if (next !== current) write(context, next)
        next
    }

    fun clear(context: Context) {
        synchronized(lock) { file(context).delete() }
    }

    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    private fun read(context: Context): List<PressureSample> = try {
        PressureHistory.parse(String(file(context).readFully(), Charsets.UTF_8))
    } catch (e: IOException) {
        // Includes FileNotFoundException: no log yet.
        emptyList()
    }

    private fun write(context: Context, samples: List<PressureSample>) {
        val atomic = file(context)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(PressureHistory.serialize(samples).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: IOException) {
            if (stream != null) atomic.failWrite(stream)
        }
    }
}
