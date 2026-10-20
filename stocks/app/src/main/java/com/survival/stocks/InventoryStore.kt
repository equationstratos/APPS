package com.survival.stocks

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
 * Process-wide store: the in-memory [Inventory] survives activity recreation, and every change is
 * written to filesDir/stocks.json on a background thread with an atomic replace (AtomicFile keeps
 * a backup until the new file is complete). Writes are conflated. Must be mutated on the main thread.
 */
class InventoryStore private constructor(private val dir: File) {

    private val file = AtomicFile(File(dir, FILE_NAME))
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "stocks-io").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicReference<Inventory?>(null)

    /** Null until the file has been read (a few milliseconds after launch). */
    var inventory by mutableStateOf<Inventory?>(null)
        private set

    init {
        io.execute {
            val loaded = read()
            main.post { if (inventory == null) inventory = loaded }
        }
    }

    fun update(transform: (Inventory) -> Inventory) {
        mutate { transform(it) to Unit }
    }

    /** Applies [op] and returns its result (a new id, an undo record…), or null if nothing changed. */
    fun <T> mutate(op: (Inventory) -> Pair<Inventory, T>?): T? {
        val current = inventory ?: return null
        val (next, result) = op(current) ?: return null
        if (next != current) {
            inventory = next
            pending.set(next)
            io.execute { pending.getAndSet(null)?.let(::write) }
        }
        return result
    }

    private fun read(): Inventory = try {
        decode(String(file.readFully(), Charsets.UTF_8))
    } catch (e: FileNotFoundException) {
        Inventory()
    } catch (e: Exception) {
        // Unreadable file: keep a copy for diagnosis instead of silently losing it.
        runCatching { file.baseFile.copyTo(File(dir, "$FILE_NAME.bad"), overwrite = true) }
        Inventory()
    }

    private fun write(inv: Inventory) {
        var out: FileOutputStream? = null
        try {
            val bytes = encode(inv, pretty = false).toByteArray(Charsets.UTF_8)
            out = file.startWrite()
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            if (out != null) file.failWrite(out)
        }
    }

    companion object {
        private const val FILE_NAME = "stocks.json"
        private const val APP_ID = "survie-stocks"
        private const val VERSION = 1

        @Volatile
        private var instance: InventoryStore? = null

        fun get(context: Context): InventoryStore =
            instance ?: synchronized(this) {
                instance ?: InventoryStore(context.applicationContext.filesDir).also { instance = it }
            }

        /** JSON used both for the private file and for user backups. */
        fun encode(inv: Inventory, pretty: Boolean): String {
            val items = JSONArray()
            for (i in inv.items) {
                val o = JSONObject()
                    .put("id", i.id)
                    .put("name", i.name)
                    .put("category", i.category.key)
                    .put("quantity", i.quantity)
                    .put("unit", i.unit)
                if (i.expiry != null) o.put("expiry", i.expiry.iso())
                if (i.location.isNotEmpty()) o.put("location", i.location)
                if (i.note.isNotEmpty()) o.put("note", i.note)
                if (i.litersPerUnit != null) o.put("litersPerUnit", i.litersPerUnit)
                if (i.kcalPerUnit != null) o.put("kcalPerUnit", i.kcalPerUnit)
                items.put(o)
            }
            val s = inv.settings
            val settings = JSONObject()
                .put("persons", s.persons)
                .put("waterPerDay", s.waterPerDay)
                .put("kcalPerDay", s.kcalPerDay)
                .put("targetDays", s.targetDays)
                .put("soonDays", s.soonDays)
            val root = JSONObject()
                .put("app", APP_ID)
                .put("version", VERSION)
                .put("nextId", inv.nextId)
                .put("settings", settings)
                .put("items", items)
            return if (pretty) root.toString(2) else root.toString()
        }

        /** Reads a file written by [encode]; throws when it is not a Stocks inventory. */
        fun decode(text: String): Inventory {
            val root = JSONObject(text.removePrefix(CSV_BOM).trim())
            val app = root.optString("app", APP_ID)
            require(app == APP_ID) { "not a Stocks file" }
            val array = root.getJSONArray("items")
            val items = ArrayList<Item>(array.length())
            for (k in 0 until array.length()) {
                val o = array.optJSONObject(k) ?: continue
                val quantity = o.optDouble("quantity", 0.0)
                items += Item(
                    id = o.optLong("id", 0L),
                    name = o.str("name"),
                    category = Category.fromKey(o.str("category")),
                    quantity = if (quantity.isNaN()) 0.0 else quantity,
                    unit = o.str("unit"),
                    expiry = Expiry.parseIso(o.str("expiry")),
                    location = o.str("location"),
                    note = o.str("note"),
                    litersPerUnit = o.positive("litersPerUnit"),
                    kcalPerUnit = o.positive("kcalPerUnit"),
                )
            }
            val s = root.optJSONObject("settings")
            val defaults = Settings()
            val settings = if (s == null) defaults else Settings(
                persons = s.optInt("persons", defaults.persons),
                waterPerDay = s.optDouble("waterPerDay", defaults.waterPerDay),
                kcalPerDay = s.optDouble("kcalPerDay", defaults.kcalPerDay),
                targetDays = s.optInt("targetDays", defaults.targetDays),
                soonDays = s.optInt("soonDays", defaults.soonDays),
            )
            return Inventory(items, settings, root.optLong("nextId", 1L)).sanitized()
        }

        private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

        private fun JSONObject.positive(key: String): Double? =
            if (isNull(key)) null else optDouble(key, Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
    }
}
