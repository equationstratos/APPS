package com.survival.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class EchoMode { FULL, HIDDEN, NONE }

class EditorState(val file: File, val initial: String, val done: CompletableDeferred<Boolean>)

/** Une session = un tampon d'écran + un shell + une entrée clavier. */
class Session(val id: Int, private val scope: CoroutineScope) {

    val buffer = TerminalBuffer()
    val shell = Shell(this)

    var running by mutableStateOf(false)
        private set
    var cols by mutableIntStateOf(80)
    var statusLine by mutableStateOf<String?>(null)
    var promptOverride by mutableStateOf<String?>(null)
        private set
    var editor by mutableStateOf<EditorState?>(null)
        private set

    var echoMode by mutableStateOf(EchoMode.FULL)
    var rawSink: ((String) -> Unit)? = null

    private val input = Channel<String>(Channel.UNLIMITED)
    private var job: Job? = null

    private inner class TermStdIn : StdIn {
        override suspend fun readLine(): String? {
            val v = try { input.receive() } catch (_: Exception) { null } ?: return null
            return if (v == Exec.EOF_MARK) null else v
        }
        override suspend fun readAll(): String {
            val sb = StringBuilder()
            while (true) {
                val l = readLine() ?: break
                sb.append(l).append('\n')
            }
            return sb.toString()
        }
    }

    fun start() {
        scope.launch(Dispatchers.IO) {
            buffer.write(Fs.motd())
            val profile = File(Fs.home, ".profile")
            if (profile.isFile) {
                shell.execute("source ${shell.quote(profile.absolutePath)}", NullSink, BufferSink(buffer), EmptyStdIn)
            }
        }
    }

    /** Ligne saisie par l'utilisateur. */
    fun submit(text: String) {
        if (editor != null) return
        if (running) {
            when (echoMode) {
                EchoMode.FULL -> buffer.write(text + "\n")
                EchoMode.HIDDEN -> buffer.write("\n")
                EchoMode.NONE -> {}
            }
            input.trySend(text)
            return
        }
        buffer.write(shell.promptText() + text + "\n")
        if (text.isBlank()) return
        shell.addHistory(text)
        job = scope.launch(Dispatchers.IO) {
            running = true
            try {
                shell.execute(text, BufferSink(buffer), BufferSink(buffer), TermStdIn())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                buffer.write("${Ansi.BRED}erreur interne : ${e.message}${Ansi.RESET}\n")
            } finally {
                running = false
                echoMode = EchoMode.FULL
                rawSink = null
                statusLine = null
                promptOverride = null
                drain()
                if (shell.exitRequested) {
                    shell.exitRequested = false
                    Handler(Looper.getMainLooper()).post {
                        Terminal.close(Terminal.sessions.indexOf(this@Session))
                    }
                }
            }
        }
    }

    /** Ctrl+C : vers le processus distant si présent, sinon on interrompt la commande. */
    fun interrupt() {
        val raw = rawSink
        if (raw != null) { raw(Exec.CTRL_C); return }
        if (!running) { buffer.write(shell.promptText() + "^C\n"); return }
        buffer.write("^C\n")
        try { shell.activeProcess?.destroy() } catch (_: Exception) { }
        job?.cancel()
    }

    /** Ctrl+D : fin de saisie. */
    fun sendEof() {
        val raw = rawSink
        if (raw != null) { raw(Exec.CTRL_D); return }
        if (running) { buffer.write("\n"); input.trySend(Exec.EOF_MARK) }
    }

    /** Touche de contrôle envoyée telle quelle au processus distant. */
    fun sendRaw(s: String) {
        rawSink?.invoke(s)
    }

    private fun drain() {
        while (true) {
            val r = input.tryReceive()
            if (!r.isSuccess) break
        }
    }

    suspend fun readInput(prompt: String, echo: Boolean): String? {
        if (prompt.isNotEmpty()) buffer.write(prompt)
        val previous = echoMode
        echoMode = if (echo) EchoMode.FULL else EchoMode.HIDDEN
        promptOverride = if (echo) prompt else ""
        val v = try { input.receive() } catch (_: Exception) { null }
        echoMode = previous
        promptOverride = null
        return if (v == null || v == Exec.EOF_MARK) null else v
    }

    /** Lecture sans écho local (session SSH interactive, netcat). */
    suspend fun readRaw(): String? {
        val v = try { input.receive() } catch (_: Exception) { null }
        return if (v == null || v == Exec.EOF_MARK) null else v
    }

    // ------------------------------------------------------------------ éditeur

    suspend fun openEditor(file: File): Boolean {
        val content = try { if (file.isFile) file.readText() else "" } catch (_: Exception) { "" }
        val d = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) { editor = EditorState(file, content, d) }
        return d.await()
    }

    fun closeEditor(save: Boolean, text: String) {
        val e = editor ?: return
        var ok = save
        if (save) {
            try {
                e.file.parentFile?.mkdirs()
                e.file.writeText(text)
            } catch (ex: Exception) {
                buffer.write("${Ansi.BRED}nano : impossible d'écrire ${e.file.name} : ${ex.message}${Ansi.RESET}\n")
                ok = false
            }
        }
        editor = null
        e.done.complete(ok)
    }
}

/** Gestion des onglets : survit aux rotations et aux changements de configuration. */
object Terminal {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sessions = mutableStateListOf<Session>()
    var active by mutableIntStateOf(0)
    private var counter = 0
    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        initialised = true
        Fs.init(context.applicationContext)
        Fs.bootstrap()
        Registry.initAll()
        Apt.ensureBase()
        newSession()
    }

    fun newSession(): Session {
        val s = Session(++counter, scope)
        sessions.add(s)
        active = sessions.size - 1
        s.start()
        return s
    }

    fun close(index: Int) {
        if (index !in sessions.indices) return
        sessions.removeAt(index)
        if (sessions.isEmpty()) newSession()
        else active = active.coerceAtMost(sessions.size - 1)
    }

    fun current(): Session = sessions[active.coerceIn(0, sessions.size - 1)]
}
