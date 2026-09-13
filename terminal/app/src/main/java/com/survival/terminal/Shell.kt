package com.survival.terminal

import java.io.File

/**
 * Mini-shell POSIX. Les commandes internes (apt, ssh, curl...) sont executees
 * dans l'application ; tout le reste part au vrai shell d'Android (mksh), qui
 * fournit toybox : ls, cat, grep, sed, find, ps, chmod, tar...
 */
class Shell(val session: Session?) {

    var cwd: File = Fs.home
    var prevCwd: File = Fs.home
    val env = LinkedHashMap<String, String>()
    val aliases = LinkedHashMap<String, String>()
    val history = ArrayList<String>()
    var lastStatus = 0
    var exitRequested = false

    @Volatile var activeProcess: Process? = null

    init {
        val prefix = Fs.prefix.absolutePath
        env["HOME"] = Fs.home.absolutePath
        env["PREFIX"] = prefix
        env["PATH"] = "$prefix/bin:/system/bin:/system/xbin:/vendor/bin"
        env["TMPDIR"] = File(prefix, "tmp").absolutePath
        env["TERM"] = "xterm-256color"
        env["USER"] = Distro.USER
        env["LOGNAME"] = Distro.USER
        env["HOSTNAME"] = Distro.HOST
        env["SHELL"] = "/system/bin/sh"
        env["LANG"] = "C.UTF-8"
        env["EDITOR"] = "nano"
        env["COLORTERM"] = "truecolor"
        loadHistory()
    }

    // ------------------------------------------------------------------ invite

    fun promptText(): String {
        val u = env["USER"] ?: Distro.USER
        return Ansi.BOLD + Ansi.BGREEN + "$u@${Distro.HOST}" + Ansi.RESET + ":" +
            Ansi.BOLD + Ansi.BBLUE + Fs.pretty(cwd) + Ansi.RESET + "$ "
    }

    fun promptPlain(): String = "${env["USER"] ?: Distro.USER}@${Distro.HOST}:${Fs.pretty(cwd)}$ "

    // -------------------------------------------------------------- historique

    private fun histFile() = File(Fs.home, ".bash_history")

    private fun loadHistory() {
        try {
            val f = histFile()
            if (f.isFile) history.addAll(f.readLines().filter { it.isNotBlank() }.takeLast(500))
        } catch (_: Exception) { }
    }

    fun addHistory(line: String) {
        if (line.isBlank()) return
        if (history.lastOrNull() == line) return
        history.add(line)
        try { histFile().appendText(line + "\n") } catch (_: Exception) { }
    }

    // ----------------------------------------------------------- point d'entree

    suspend fun execute(line: String, out: Sink, err: Sink, stdin: StdIn): Int {
        val text = line.trim()
        if (text.isEmpty()) return lastStatus
        // Constructions propres a mksh : on delegue la ligne entiere.
        if (needsRealShell(text)) {
            lastStatus = Exec.runExternal(ExecContext(this, listOf("sh"), out, err, stdin, session), text)
            return lastStatus
        }
        var status = lastStatus
        for ((op, part) in splitLogical(text)) {
            when (op) {
                "&&" -> if (status != 0) continue
                "||" -> if (status == 0) continue
            }
            status = runPipeline(part, out, err, stdin)
            lastStatus = status
            if (exitRequested) break
        }
        return status
    }

    private fun needsRealShell(t: String): Boolean {
        val first = t.substringBefore(' ')
        if (first in setOf("if", "for", "while", "until", "case", "function", "{", "(")) return true
        return t.contains("$(") || t.contains("`") || t.contains("<<")
    }

    /** Decoupe sur ; && || en respectant les guillemets. Retourne (operateur, texte). */
    private fun splitLogical(line: String): List<Pair<String, String>> {
        val res = ArrayList<Pair<String, String>>()
        val sb = StringBuilder()
        var op = ";"
        var q = ' '
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                q != ' ' -> { sb.append(c); if (c == q) q = ' ' }
                c == '\'' || c == '"' -> { q = c; sb.append(c) }
                c == '\\' && i + 1 < line.length -> { sb.append(c).append(line[i + 1]); i++ }
                c == '#' && (sb.isEmpty() || sb.last() == ' ') -> { i = line.length }
                c == ';' -> { res.add(Pair(op, sb.toString())); sb.setLength(0); op = ";" }
                c == '&' && i + 1 < line.length && line[i + 1] == '&' -> {
                    res.add(Pair(op, sb.toString())); sb.setLength(0); op = "&&"; i++
                }
                c == '|' && i + 1 < line.length && line[i + 1] == '|' -> {
                    res.add(Pair(op, sb.toString())); sb.setLength(0); op = "||"; i++
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotBlank()) res.add(Pair(op, sb.toString()))
        return res.filter { it.second.isNotBlank() }
    }

    /** Decoupe sur | en respectant les guillemets. */
    private fun splitPipes(line: String): List<String> {
        val res = ArrayList<String>()
        val sb = StringBuilder()
        var q = ' '
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                q != ' ' -> { sb.append(c); if (c == q) q = ' ' }
                c == '\'' || c == '"' -> { q = c; sb.append(c) }
                c == '\\' && i + 1 < line.length -> { sb.append(c).append(line[i + 1]); i++ }
                c == '|' -> { res.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        res.add(sb.toString())
        return res.filter { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ tubes

    private suspend fun runPipeline(text: String, out: Sink, err: Sink, stdin: StdIn): Int {
        val stages = splitPipes(text)
        // Si aucune etape n'est interne, mksh fait tout le travail (tubes compris).
        if (stages.all { !isInternalStage(it) }) {
            return Exec.runExternal(ExecContext(this, listOf("sh"), out, err, stdin, session), text)
        }
        var input = stdin
        var status = 0
        for ((idx, stage) in stages.withIndex()) {
            val last = idx == stages.size - 1
            val capture = if (last) null else StringSink()
            status = runStage(stage, capture ?: out, err, input)
            if (capture != null) input = TextStdIn(capture.text())
            if (exitRequested) break
        }
        return status
    }

    private fun resolveAlias(stage: String): String {
        val trimmed = stage.trim()
        val first = trimmed.substringBefore(' ')
        val alias = aliases[first] ?: return trimmed
        return alias + trimmed.substring(first.length)
    }

    private fun isInternalStage(stage: String): Boolean {
        val s = resolveAlias(stage)
        val words = tokenize(s).map { it.text }
        val name = words.firstOrNull() ?: return false
        // VAR=valeur : affectation gérée par notre shell
        if (name.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$"))) return true
        if (Builtins.names.contains(name) || Registry.find(name) != null ||
            File(Fs.bin, name).isFile) return true
        // commande inexistante : on la traite pour proposer le paquet correspondant
        return !name.contains('/') && Exec.which(this, name) == null
    }

    /** Execute une etape : commande interne, script installe, ou binaire systeme. */
    private suspend fun runStage(stageRaw: String, out: Sink, err: Sink, stdin: StdIn): Int {
        val stage = resolveAlias(stageRaw)
        val tokens = tokenize(stage)
        var argv = ArrayList<String>()
        var outSink = out
        var errSink = err
        var inSrc = stdin
        val toClose = ArrayList<Sink>()

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (!t.quoted && (t.text == ">" || t.text == ">>" || t.text == "<" ||
                    t.text == "2>" || t.text == "2>>")) {
                val target = tokens.getOrNull(i + 1)?.text
                if (target == null) { err.line("sh: erreur de syntaxe près de « ${t.text} »"); return 2 }
                val f = Fs.resolve(cwd, target)
                try {
                    when (t.text) {
                        ">" -> { val s = FileSink(f, false); toClose.add(s); outSink = s }
                        ">>" -> { val s = FileSink(f, true); toClose.add(s); outSink = s }
                        "2>" -> { val s = FileSink(f, false); toClose.add(s); errSink = s }
                        "2>>" -> { val s = FileSink(f, true); toClose.add(s); errSink = s }
                        "<" -> inSrc = TextStdIn(if (f.isFile) f.readText() else "")
                    }
                } catch (e: Exception) {
                    err.line("sh: $target : ${e.message}")
                    return 1
                }
                i += 2
                continue
            }
            argv.addAll(expand(t))
            i++
        }
        if (argv.isEmpty()) return 0

        // Affectation de variable en tete : VAR=valeur
        while (argv.isNotEmpty() && argv[0].matches(Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$"))) {
            val (k, v) = argv[0].split("=", limit = 2)
            env[k] = v
            argv = ArrayList(argv.drop(1))
            if (argv.isEmpty()) return 0
        }

        val ctx = ExecContext(this, argv, outSink, errSink, inSrc, session)
        try {
            val name = argv[0]
            // 1. commandes integrees au shell
            if (Builtins.names.contains(name)) return Builtins.run(ctx)
            // 2. scripts installes par apt dans $PREFIX/bin
            val script = File(Fs.bin, name)
            if (script.isFile) {
                val cmd = (listOf("/system/bin/sh", script.absolutePath) + argv.drop(1))
                    .joinToString(" ") { quote(it) }
                return Exec.runExternal(ctx, cmd)
            }
            // 3. commandes fournies par les paquets installes
            val internal = Registry.find(name)
            if (internal != null && Apt.isInstalled(internal.pkg)) return internal.exec(ctx)
            // 4. binaire reel du systeme (toybox et compagnie)
            if (Exec.which(this, name) != null) return Exec.runExternal(ctx, stage)
            // 5. connu mais non installe : message facon Ubuntu
            if (internal != null) {
                errSink.write(
                    "La commande « ${Ansi.BOLD}$name${Ansi.RESET} » n'a pas été trouvée, mais peut être installée avec :\n\n" +
                        "${Ansi.BOLD}apt install ${internal.pkg}${Ansi.RESET}\n\n"
                )
                return 127
            }
            val suggestion = Apt.packageProviding(name)
            if (suggestion != null) {
                errSink.write(
                    "La commande « ${Ansi.BOLD}$name${Ansi.RESET} » n'a pas été trouvée, mais peut être installée avec :\n\n" +
                        "${Ansi.BOLD}apt install $suggestion${Ansi.RESET}\n\n"
                )
                return 127
            }
            errSink.write("sh: $name : commande introuvable\n")
            return 127
        } finally {
            toClose.forEach { it.close() }
        }
    }

    // ------------------------------------------------------------- tokenisation

    data class Token(val text: String, val quoted: Boolean, val literal: Boolean = false)

    fun tokenize(line: String): List<Token> {
        val res = ArrayList<Token>()
        val sb = StringBuilder()
        var quoted = false
        var literal = false
        var has = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == ' ' || c == '\t' -> {
                    if (has) {
                        res.add(Token(sb.toString(), quoted, literal))
                        sb.setLength(0); quoted = false; literal = false; has = false
                    }
                }
                c == '\'' -> {
                    // guillemets simples : aucune substitution
                    has = true; quoted = true; literal = true
                    i++
                    while (i < line.length && line[i] != '\'') { sb.append(line[i]); i++ }
                }
                c == '"' -> {
                    has = true; quoted = true
                    i++
                    while (i < line.length && line[i] != '"') {
                        if (line[i] == '\\' && i + 1 < line.length) { sb.append(line[i + 1]); i += 2 }
                        else { sb.append(line[i]); i++ }
                    }
                }
                c == '\\' && i + 1 < line.length -> { has = true; sb.append(line[i + 1]); i++ }
                else -> { has = true; sb.append(c) }
            }
            i++
        }
        if (has) res.add(Token(sb.toString(), quoted, literal))
        return res
    }

    /** Substitution de variables, de ~ et des jokers. */
    private fun expand(t: Token): List<String> {
        if (t.literal) return listOf(t.text)
        if (t.quoted) return listOf(substitute(t.text))
        var s = substitute(t.text)
        if (s == "~") s = Fs.home.absolutePath
        else if (s.startsWith("~/")) s = Fs.home.absolutePath + s.substring(1)
        if (s.contains('*') || s.contains('?')) {
            val globbed = glob(s)
            if (globbed.isNotEmpty()) return globbed
        }
        return listOf(s)
    }

    private fun substitute(raw: String): String {
        if (!raw.contains('$')) return raw
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '$' && i + 1 < raw.length) {
                val n = raw[i + 1]
                when {
                    n == '{' -> {
                        val end = raw.indexOf('}', i)
                        if (end > 0) {
                            sb.append(env[raw.substring(i + 2, end)] ?: "")
                            i = end + 1; continue
                        } else sb.append(c)
                    }
                    n == '?' -> { sb.append(lastStatus); i += 2; continue }
                    n == '$' -> { sb.append(android.os.Process.myPid()); i += 2; continue }
                    n.isLetter() || n == '_' -> {
                        var j = i + 1
                        while (j < raw.length && (raw[j].isLetterOrDigit() || raw[j] == '_')) j++
                        sb.append(env[raw.substring(i + 1, j)] ?: "")
                        i = j; continue
                    }
                    else -> sb.append(c)
                }
            } else sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun glob(pattern: String): List<String> {
        val slash = pattern.lastIndexOf('/')
        val dirPart = if (slash >= 0) pattern.substring(0, slash + 1) else ""
        val namePart = if (slash >= 0) pattern.substring(slash + 1) else pattern
        if (namePart.isEmpty()) return emptyList()
        val dir = if (dirPart.isEmpty()) cwd else Fs.resolve(cwd, dirPart)
        val sb = StringBuilder("^")
        for (c in namePart) {
            when (c) {
                '*' -> sb.append("[^/]*")
                '?' -> sb.append("[^/]")
                else -> sb.append(Regex.escape(c.toString()))
            }
        }
        sb.append("$")
        val rx = Regex(sb.toString())
        val files = dir.listFiles() ?: return emptyList()
        return files.map { it.name }
            .filter { rx.matches(it) && (namePart.startsWith(".") || !it.startsWith(".")) }
            .sorted()
            .map { dirPart + it }
    }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Completion : commandes puis fichiers du repertoire courant. */
    fun complete(fragment: String): List<String> {
        if (fragment.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        val hasSlash = fragment.contains('/')
        if (!hasSlash) {
            Builtins.names.filter { it.startsWith(fragment) }.forEach { out.add(it) }
            Registry.commands.keys.filter { it.startsWith(fragment) }.forEach { out.add(it) }
            Fs.bin.list()?.filter { it.startsWith(fragment) }?.forEach { out.add(it) }
            File("/system/bin").list()?.filter { it.startsWith(fragment) }?.take(40)?.forEach { out.add(it) }
        }
        val slash = fragment.lastIndexOf('/')
        val dirPart = if (slash >= 0) fragment.substring(0, slash + 1) else ""
        val namePart = if (slash >= 0) fragment.substring(slash + 1) else fragment
        val dir = if (dirPart.isEmpty()) cwd else Fs.resolve(cwd, dirPart)
        dir.listFiles()?.forEach {
            if (it.name.startsWith(namePart)) out.add(dirPart + it.name + if (it.isDirectory) "/" else "")
        }
        return out.sorted()
    }
}
