package com.survival.checklist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide store: the in-memory [Library] survives activity recreation, and every change
 * is written to filesDir/checklists.json on a background thread with an atomic replace
 * (AtomicFile keeps a backup until the new file is complete). Writes are conflated: only the
 * latest snapshot is written. Must be mutated from the main thread.
 */
class ChecklistStore private constructor(private val dir: File) {

    private val file = AtomicFile(File(dir, FILE_NAME))
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "checklist-io").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicReference<Library?>(null)

    /** Null until the file has been read (a few milliseconds after the first launch). */
    var library by mutableStateOf<Library?>(null)
        private set

    init {
        io.execute {
            val loaded = read()
            main.post { if (library == null) library = loaded }
        }
    }

    fun update(transform: (Library) -> Library) {
        mutate { transform(it) to Unit }
    }

    /** Applies [op] and returns its result (e.g. a new id or an undo record), or null if nothing changed. */
    fun <T> mutate(op: (Library) -> Pair<Library, T>?): T? {
        val current = library ?: return null
        val (next, result) = op(current) ?: return null
        if (next != current) {
            library = next
            pending.set(next)
            io.execute { pending.getAndSet(null)?.let(::write) }
        }
        return result
    }

    private fun read(): Library = try {
        decode(String(file.readFully(), Charsets.UTF_8)).sanitized()
    } catch (e: FileNotFoundException) {
        Library()
    } catch (e: Exception) {
        // Unreadable file: keep a copy for diagnosis instead of silently losing it.
        runCatching { file.baseFile.copyTo(File(dir, "$FILE_NAME.bad"), overwrite = true) }
        Library()
    }

    private fun write(lib: Library) {
        var out: FileOutputStream? = null
        try {
            val bytes = encode(lib).toByteArray(Charsets.UTF_8)
            out = file.startWrite()
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            if (out != null) file.failWrite(out)
        }
    }

    companion object {
        private const val FILE_NAME = "checklists.json"
        private const val VERSION = 1

        @Volatile
        private var instance: ChecklistStore? = null

        fun get(context: Context): ChecklistStore =
            instance ?: synchronized(this) {
                instance ?: ChecklistStore(context.applicationContext.filesDir).also { instance = it }
            }

        private fun encode(lib: Library): String {
            val lists = JSONArray()
            for (l in lib.lists) {
                val sections = JSONArray()
                for (s in l.sections) {
                    val items = JSONArray()
                    for (i in s.items) {
                        items.put(JSONObject().put("id", i.id).put("text", i.text).put("checked", i.checked))
                    }
                    sections.put(JSONObject().put("id", s.id).put("title", s.title).put("items", items))
                }
                val o = JSONObject()
                    .put("id", l.id)
                    .put("name", l.name)
                    .put("hideChecked", l.hideChecked)
                    .put("sections", sections)
                if (l.templateKey != null) o.put("template", l.templateKey)
                lists.put(o)
            }
            return JSONObject()
                .put("version", VERSION)
                .put("nextId", lib.nextId)
                .put("lists", lists)
                .toString()
        }

        private fun decode(text: String): Library {
            val root = JSONObject(text)
            val listsJson = root.optJSONArray("lists") ?: JSONArray()
            val lists = ArrayList<Checklist>(listsJson.length())
            for (li in 0 until listsJson.length()) {
                val lo = listsJson.optJSONObject(li) ?: continue
                val sectionsJson = lo.optJSONArray("sections") ?: JSONArray()
                val sections = ArrayList<Section>(sectionsJson.length())
                for (si in 0 until sectionsJson.length()) {
                    val so = sectionsJson.optJSONObject(si) ?: continue
                    val itemsJson = so.optJSONArray("items") ?: JSONArray()
                    val items = ArrayList<CheckItem>(itemsJson.length())
                    for (ii in 0 until itemsJson.length()) {
                        val itemJson = itemsJson.optJSONObject(ii) ?: continue
                        items += CheckItem(itemJson.optLong("id"), itemJson.optString("text"), itemJson.optBoolean("checked"))
                    }
                    sections += Section(so.optLong("id"), so.optString("title"), items)
                }
                val template = lo.optString("template").takeIf { Templates.byKey(it) != null }
                lists += Checklist(lo.optLong("id"), lo.optString("name"), sections, lo.optBoolean("hideChecked"), template)
            }
            return Library(lists, root.optLong("nextId", 1L))
        }
    }
}
