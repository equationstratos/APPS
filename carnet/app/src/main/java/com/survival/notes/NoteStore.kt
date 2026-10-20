package com.survival.notes

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide store of the notes. The list lives in memory (it survives activity recreation)
 * and every change is written to filesDir/notes.json on Dispatchers.IO through an AtomicFile:
 * the previous file is kept as a backup until the new one is complete, so a crash or a full
 * disk mid-write never loses the notes. Writes are conflated: only the latest list is written.
 * Must be changed from the main thread.
 */
class NoteStore private constructor(private val dir: File) {

    private val file = AtomicFile(File(dir, FILE_NAME))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val pending = AtomicReference<List<Note>?>(null)

    /** Null until the file has been read (a few milliseconds after launch). */
    var notes by mutableStateOf<List<Note>?>(null)
        private set

    /** The file could not be read: it was set aside and the notebook started empty. */
    var loadFailed by mutableStateOf(false)
        private set

    /** The last write failed (storage full?): the notes are still in memory and will be retried. */
    var saveFailed by mutableStateOf(false)
        private set

    init {
        scope.launch {
            val loaded = read()
            withContext(Dispatchers.Main) {
                if (notes == null) notes = loaded.first
                loadFailed = loaded.second
            }
        }
    }

    fun find(id: String): Note? = notes?.firstOrNull { it.id == id }

    /** Inserts or replaces [note]. A note that is empty and not stored yet is not created. */
    fun save(note: Note) {
        val current = notes ?: return
        val index = current.indexOfFirst { it.id == note.id }
        val next = when {
            index < 0 && note.isEmpty -> return
            index < 0 -> current + note
            current[index] == note -> return
            else -> current.toMutableList().also { it[index] = note }
        }
        publish(next)
    }

    /** Removes the note and returns it, or null if it was not stored. */
    fun delete(id: String): Note? {
        val current = notes ?: return null
        val removed = current.firstOrNull { it.id == id } ?: return null
        publish(current.filterNot { it.id == id })
        return removed
    }

    /** Merges a restored backup (see [mergeNotes]); null while the notes are still loading. */
    fun merge(incoming: List<Note>): MergeResult? {
        val current = notes ?: return null
        val result = mergeNotes(current, incoming)
        if (result.notes != current) publish(result.notes)
        return result
    }

    /** Undoes a [merge] if nothing changed since; returns false otherwise. */
    fun revert(before: List<Note>, after: List<Note>): Boolean {
        if (notes != after) return false
        publish(before)
        return true
    }

    /** Writes the notes again after a failed save (once space has been freed, for example). */
    fun retrySave() {
        notes?.let { publish(it) }
    }

    /** The user has read the warning about the unreadable file. */
    fun dismissLoadFailure() {
        loadFailed = false
    }

    private fun publish(next: List<Note>) {
        notes = next
        pending.set(next)
        scope.launch {
            writeLock.withLock {
                val snapshot = pending.getAndSet(null) ?: return@withLock
                val ok = write(snapshot)
                withContext(Dispatchers.Main) { saveFailed = !ok }
            }
        }
    }

    private fun read(): Pair<List<Note>, Boolean> = try {
        decodeNotes(String(file.readFully(), Charsets.UTF_8)) to false
    } catch (e: FileNotFoundException) {
        emptyList<Note>() to false
    } catch (e: Exception) {
        // Unreadable file: keep a copy instead of overwriting it with the next save.
        runCatching {
            file.baseFile.copyTo(File(dir, "notes-illisible-${System.currentTimeMillis()}.json"), overwrite = true)
        }
        emptyList<Note>() to true
    }

    private fun write(snapshot: List<Note>): Boolean {
        var out: FileOutputStream? = null
        return try {
            val bytes = encode(snapshot).toByteArray(Charsets.UTF_8)
            out = file.startWrite()
            out.write(bytes)
            file.finishWrite(out)
            true
        } catch (e: Exception) {
            if (out != null) file.failWrite(out)
            false
        }
    }

    companion object {
        private const val FILE_NAME = "notes.json"
        private const val APP_TAG = "carnet"
        private const val VERSION = 1

        @Volatile
        private var instance: NoteStore? = null

        fun get(context: Context): NoteStore =
            instance ?: synchronized(this) {
                instance ?: NoteStore(context.applicationContext.filesDir).also { instance = it }
            }

        /** Format of notes.json and of the backups made with "Sauvegarder". */
        fun encode(notes: List<Note>): String {
            val array = JSONArray()
            for (note in notes) {
                val json = JSONObject()
                    .put("id", note.id)
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("created", note.createdAt)
                    .put("modified", note.modifiedAt)
                    .put("pinned", note.pinned)
                val location = note.location
                if (location != null && isValidCoordinate(location.latitude, location.longitude)) {
                    val place = JSONObject()
                        .put("lat", location.latitude)
                        .put("lon", location.longitude)
                        .put("time", location.timeMillis)
                        .put("source", location.source.key)
                    val accuracy = location.accuracyM
                    if (accuracy != null && accuracy.isFinite()) place.put("accuracy", accuracy)
                    json.put("location", place)
                }
                array.put(json)
            }
            return JSONObject()
                .put("app", APP_TAG)
                .put("version", VERSION)
                .put("notes", array)
                .toString()
        }

        /**
         * Reads notes.json or a backup. Tolerant of missing fields (an id is made up, dates fall
         * back on each other); throws [JSONException] when the text is not a notes file at all.
         */
        fun decodeNotes(text: String): List<Note> {
            val clean = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            val array = if (clean.startsWith("[")) {
                JSONArray(clean)
            } else {
                JSONObject(clean).optJSONArray("notes") ?: throw JSONException("Pas de liste de notes")
            }
            val now = System.currentTimeMillis()
            val notes = ArrayList<Note>(array.length())
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val id = json.text("id").trim().ifEmpty { newNoteId() }
                val createdRaw = json.optLong("created", 0L)
                val modifiedRaw = json.optLong("modified", 0L)
                val created = when {
                    createdRaw > 0L -> createdRaw
                    modifiedRaw > 0L -> modifiedRaw
                    else -> now
                }
                val modified = if (modifiedRaw > 0L) modifiedRaw else created
                notes += Note(
                    id = id,
                    title = json.text("title"),
                    body = json.text("body"),
                    createdAt = created,
                    modifiedAt = modified,
                    pinned = json.optBoolean("pinned", false),
                    location = json.optJSONObject("location")?.let { decodeLocation(it, created) },
                )
            }
            return dedupeById(notes)
        }

        private fun decodeLocation(json: JSONObject, fallbackTime: Long): NoteLocation? {
            val latitude = json.optDouble("lat", Double.NaN)
            val longitude = json.optDouble("lon", Double.NaN)
            if (!isValidCoordinate(latitude, longitude)) return null
            val accuracy = json.optDouble("accuracy", Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
            val time = json.optLong("time", 0L).takeIf { it > 0L } ?: fallbackTime
            return NoteLocation(latitude, longitude, accuracy, time, LocationSource.fromKey(json.text("source")))
        }

        /** optString turns a JSON null into the text "null": treat it as a missing value instead. */
        private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
    }
}
