package com.survival.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/** Destination de flux (ecran, tube, fichier). */
interface Sink {
    fun write(s: String)
    fun line(s: String) { write(s + "\n") }
    fun close() {}
}

class BufferSink(private val buf: TerminalBuffer) : Sink {
    override fun write(s: String) = buf.write(s)
}

class StringSink : Sink {
    private val sb = StringBuilder()
    override fun write(s: String) { synchronized(sb) { sb.append(s) } }
    fun text(): String = synchronized(sb) { sb.toString() }
}

class FileSink(file: File, append: Boolean) : Sink {
    private val w = FileWriter(file, append)
    override fun write(s: String) { try { w.write(s); w.flush() } catch (_: Exception) {} }
    override fun close() { try { w.close() } catch (_: Exception) {} }
}

object NullSink : Sink {
    override fun write(s: String) {}
}

/** Source d'entree standard. */
interface StdIn {
    suspend fun readLine(): String?
    suspend fun readAll(): String
}

object EmptyStdIn : StdIn {
    override suspend fun readLine(): String? = null
    override suspend fun readAll(): String = ""
}

class TextStdIn(private val text: String) : StdIn {
    private var pos = 0
    override suspend fun readLine(): String? {
        if (pos >= text.length) return null
        val nl = text.indexOf('\n', pos)
        return if (nl < 0) { val s = text.substring(pos); pos = text.length; s }
        else { val s = text.substring(pos, nl); pos = nl + 1; s }
    }
    override suspend fun readAll(): String {
        val s = text.substring(pos.coerceAtMost(text.length)); pos = text.length; return s
    }
}

/** Contexte d'execution d'une commande interne. */
class ExecContext(
    val shell: Shell,
    val argv: List<String>,
    val out: Sink,
    val err: Sink,
    val stdin: StdIn,
    val session: Session?
) {
    val name: String get() = argv.firstOrNull() ?: ""
    val args: List<String> get() = if (argv.isEmpty()) emptyList() else argv.drop(1)

    fun print(s: String) = out.write(s)
    fun println(s: String = "") = out.write(s + "\n")
    fun error(s: String) = err.write(Ansi.BRED + s + Ansi.RESET + "\n")

    /**
     * Arguments hors options. Les options passées en paramètre prennent une
     * valeur, qui est donc ignorée elle aussi (ex : operands("-c") sur « -c 4 hote »).
     */
    fun operands(vararg valueOpts: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                valueOpts.contains(a) -> i++
                a.startsWith("-") && a != "-" -> { }
                else -> out.add(a)
            }
            i++
        }
        return out
    }

    fun flag(vararg names: String): Boolean = args.any { a ->
        names.any { n ->
            a == n || (n.length == 2 && n.startsWith("-") && !a.startsWith("--") &&
                a.startsWith("-") && a.length > 1 && a.contains(n[1]))
        }
    }

    /** Valeur d'une option "-x valeur" ou "--nom=valeur". */
    fun option(short: String, long: String? = null): String? {
        for (i in args.indices) {
            val a = args[i]
            if (a == short || (long != null && a == long)) return args.getOrNull(i + 1)
            if (long != null && a.startsWith("$long=")) return a.substring(long.length + 1)
            if (a.startsWith(short) && a.length > short.length && short.length == 2) return a.substring(short.length)
        }
        return null
    }

    /** Lecture interactive au clavier (mot de passe, confirmation). */
    suspend fun ask(prompt: String, echo: Boolean = true): String? =
        session?.readInput(prompt, echo)

    fun resolve(path: String): File = Fs.resolve(shell.cwd, path)
}

object Exec {
    /** Code des touches de controle transmis au processus. */
    val CTRL_C: String = 3.toChar().toString()
    val CTRL_D: String = 4.toChar().toString()
    val EOF_MARK: String = 4.toChar().toString()

    /**
     * Execute une ligne via le vrai shell d'Android (mksh) : tubes, redirections,
     * jokers et boucles sont donc geres par un shell POSIX reel.
     */
    suspend fun runExternal(ctx: ExecContext, command: String): Int = coroutineScope {
        val pb = ProcessBuilder("/system/bin/sh", "-c", command)
        pb.directory(if (ctx.shell.cwd.isDirectory) ctx.shell.cwd else Fs.home)
        try {
            val e = pb.environment()
            for ((k, v) in ctx.shell.env) e[k] = v
        } catch (_: Exception) { }
        val proc = try {
            pb.start()
        } catch (ex: Exception) {
            ctx.error("${ctx.name}: impossible de demarrer le processus : ${ex.message}")
            return@coroutineScope 127
        }
        ctx.shell.activeProcess = proc
        try {
            val o = launch(Dispatchers.IO) { pump(proc.inputStream, ctx.out) }
            val e = launch(Dispatchers.IO) { pump(proc.errorStream, ctx.err) }
            val i = launch(Dispatchers.IO) { feed(proc.outputStream, ctx.stdin) }
            val code = withContext(Dispatchers.IO) {
                while (!proc.waitFor(80, TimeUnit.MILLISECONDS)) ensureActive()
                proc.exitValue()
            }
            o.join(); e.join(); i.cancel()
            code
        } finally {
            ctx.shell.activeProcess = null
            try { proc.destroy() } catch (_: Exception) { }
        }
    }

    private fun pump(ins: InputStream, sink: Sink) {
        val reader = InputStreamReader(ins, Charsets.UTF_8)
        val buf = CharArray(4096)
        try {
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                if (n > 0) sink.write(String(buf, 0, n))
            }
        } catch (_: Exception) { }
    }

    private suspend fun feed(os: OutputStream, stdin: StdIn) {
        val w = OutputStreamWriter(os, Charsets.UTF_8)
        try {
            while (true) {
                val line = stdin.readLine() ?: break
                w.write(line); w.write("\n"); w.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { w.close() } catch (_: Exception) { }
        }
    }

    /** Cherche un executable dans le PATH courant. */
    fun which(shell: Shell, name: String): File? {
        if (name.contains('/')) {
            val f = Fs.resolve(shell.cwd, name)
            return if (f.isFile && f.canExecute()) f else null
        }
        for (dir in (shell.env["PATH"] ?: "").split(':')) {
            if (dir.isBlank()) continue
            val f = File(dir, name)
            if (f.isFile && f.canExecute()) return f
        }
        return null
    }
}
