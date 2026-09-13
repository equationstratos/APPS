package com.survival.terminal

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object Tools {

    fun register() {
        Registry.reg("ls", "coreutils", "liste le contenu d'un répertoire",
            "ls [-l] [-a] [-h] [-t] [-S] [-1] [chemin...]") { ls(it) }
        Registry.reg("ll", "coreutils", "liste détaillée", "ll [chemin...]") { ls(it) }
        Registry.reg("tree", "tree", "affichage arborescent", "tree [-a] [-d] [-L n] [chemin]") { tree(it) }
        Registry.reg("nano", "nano", "éditeur de texte", "nano [fichier]") { nano(it) }
        Registry.reg("neofetch", "neofetch", "informations système", "neofetch") { neofetch(it) }
        Registry.reg("htop", "htop", "moniteur de processus", "htop") { htop(it) }
        Registry.reg("figlet", "figlet", "grandes lettres", "figlet texte") { figlet(it) }
        Registry.reg("cowsay", "cowsay", "une vache qui parle", "cowsay texte") { cowsay(it) }
        Registry.reg("fortune", "fortune-mod", "citation aléatoire", "fortune") { fortune(it) }
        Registry.reg("cmatrix", "cmatrix", "pluie de caractères", "cmatrix (Ctrl+C pour arrêter)") { cmatrix(it) }
        Registry.reg("sl", "sl", "locomotive", "sl") { sl(it) }
        Registry.reg("zip", "zip", "crée une archive ZIP", "zip archive.zip fichier...") { zip(it) }
        Registry.reg("unzip", "unzip", "extrait une archive ZIP", "unzip [-l] archive.zip [-d dossier]") { unzip(it) }
    }

    // ---------------------------------------------------------------------- ls

    private fun colorOf(f: File): String = when {
        f.isDirectory -> Ansi.BOLD + Ansi.BBLUE
        f.canExecute() -> Ansi.BOLD + Ansi.BGREEN
        f.name.endsWith(".zip") || f.name.endsWith(".gz") || f.name.endsWith(".tar") -> Ansi.BRED
        f.name.startsWith(".") -> Ansi.GREY
        else -> ""
    }

    private fun ls(ctx: ExecContext): Int {
        val long = ctx.name == "ll" || ctx.flag("-l")
        val all = ctx.name == "ll" || ctx.flag("-a", "--all")
        val human = ctx.flag("-h")
        val one = ctx.flag("-1")
        val targets = ctx.operands().ifEmpty { listOf(".") }
        var status = 0
        // comme le vrai ls : les fichiers d'abord, puis chaque dossier avec son titre
        val files = ArrayList<String>()
        val dirs = ArrayList<String>()
        for (t in targets) {
            val f = ctx.resolve(t)
            when {
                !f.exists() -> {
                    ctx.err.write("ls: impossible d'accéder à '$t': Aucun fichier ou dossier de ce type\n")
                    status = 2
                }
                f.isDirectory -> dirs.add(t)
                else -> files.add(t)
            }
        }
        val groups = ArrayList<Pair<String?, List<String>>>()
        if (files.isNotEmpty()) groups.add(Pair(null, files))
        dirs.forEach { groups.add(Pair(it, listOf(it))) }
        for ((idx, group) in groups.withIndex()) {
            val title = group.first
            val f = ctx.resolve(group.second.first())
            if (title != null && groups.size > 1) {
                if (idx > 0) ctx.println()
                ctx.println("$title :")
            }
            val entries = if (title != null) (f.listFiles() ?: emptyArray()).toMutableList()
            else group.second.map { ctx.resolve(it) }.toMutableList()
            var list = entries.filter { title == null || all || !it.name.startsWith(".") }
            list = when {
                ctx.flag("-t") -> list.sortedByDescending { it.lastModified() }
                ctx.flag("-S") -> list.sortedByDescending { it.length() }
                else -> list.sortedBy { it.name.lowercase() }
            }
            if (long) {
                if (f.isDirectory) ctx.println("total ${list.sumOf { (it.length() + 1023) / 1024 }}")
                val df = SimpleDateFormat("d MMM HH:mm", Locale.FRANCE)
                for (e in list) {
                    val perms = StringBuilder(if (e.isDirectory) "d" else "-")
                    perms.append(if (e.canRead()) "r" else "-")
                    perms.append(if (e.canWrite()) "w" else "-")
                    perms.append(if (e.canExecute()) "x" else "-")
                    perms.append("r--r--")
                    val size = if (human) Apt.fmtSize(e.length()).padStart(9) else e.length().toString().padStart(9)
                    ctx.println("$perms  1 ${Distro.USER} ${Distro.USER} $size ${df.format(Date(e.lastModified()))} " +
                        colorOf(e) + e.name + Ansi.RESET)
                }
            } else if (one) {
                list.forEach { ctx.println(colorOf(it) + it.name + Ansi.RESET) }
            } else {
                val width = ctx.session?.cols ?: 80
                val colw = (list.maxOfOrNull { it.name.length } ?: 10) + 2
                val cols = (width / colw).coerceAtLeast(1)
                var n = 0
                val sb = StringBuilder()
                for (e in list) {
                    sb.append(colorOf(e)).append(e.name.padEnd(colw)).append(Ansi.RESET)
                    n++
                    if (n % cols == 0) { ctx.println(sb.toString().trimEnd()); sb.setLength(0) }
                }
                if (sb.isNotEmpty()) ctx.println(sb.toString().trimEnd())
            }
        }
        return status
    }

    // -------------------------------------------------------------------- tree

    private fun tree(ctx: ExecContext): Int {
        val root = ctx.resolve(ctx.operands("-L").firstOrNull() ?: ".")
        if (!root.exists()) { ctx.error("tree: ${root.name} : dossier introuvable"); return 1 }
        val all = ctx.flag("-a")
        val dirsOnly = ctx.flag("-d")
        val maxDepth = ctx.option("-L")?.toIntOrNull() ?: 4
        var dirs = 0
        var files = 0
        ctx.println(Ansi.BOLD + Ansi.BBLUE + Fs.pretty(root) + Ansi.RESET)
        fun walk(dir: File, prefix: String, depth: Int) {
            if (depth > maxDepth) return
            val list = (dir.listFiles() ?: emptyArray())
                .filter { all || !it.name.startsWith(".") }
                .filter { !dirsOnly || it.isDirectory }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            list.forEachIndexed { i, f ->
                val last = i == list.size - 1
                ctx.println(prefix + (if (last) "└── " else "├── ") + colorOf(f) + f.name + Ansi.RESET)
                if (f.isDirectory) {
                    dirs++
                    walk(f, prefix + (if (last) "    " else "│   "), depth + 1)
                } else files++
            }
        }
        walk(root, "", 1)
        ctx.println()
        ctx.println("$dirs répertoire(s), $files fichier(s)")
        return 0
    }

    // -------------------------------------------------------------------- nano

    private suspend fun nano(ctx: ExecContext): Int {
        val session = ctx.session
        if (session == null) { ctx.error("nano : nécessite un terminal interactif"); return 1 }
        val name = ctx.operands().firstOrNull()
        val file = if (name != null) ctx.resolve(name) else File(Fs.home, "sans-titre.txt")
        if (file.isDirectory) { ctx.error("nano : ${file.name} est un dossier"); return 1 }
        val saved = session.openEditor(file)
        ctx.println(if (saved) "[ Écrit dans ${Fs.pretty(file)} ]" else "[ Quitté sans enregistrer ]")
        return 0
    }

    // ---------------------------------------------------------------- neofetch

    private fun neofetch(ctx: ExecContext): Int {
        val logo = listOf(
            "        ${Ansi.ORANGE}.-/+oossssoo+/-.${Ansi.RESET}",
            "    ${Ansi.ORANGE}`:+ssssssssssssssssss+:`${Ansi.RESET}",
            "  ${Ansi.ORANGE}-+ssssssssssssssssssyyssss+-${Ansi.RESET}",
            " ${Ansi.ORANGE}.ossssssssssssssssss${Ansi.WHITE}dMMMNy${Ansi.ORANGE}sssso.${Ansi.RESET}",
            "${Ansi.ORANGE}/sssssssssss${Ansi.WHITE}hdmmNNmmyNMMMMh${Ansi.ORANGE}ssssss/${Ansi.RESET}",
            "${Ansi.ORANGE}+ssssssss${Ansi.WHITE}hmydMMMMMMMNddddy${Ansi.ORANGE}ssssssss+${Ansi.RESET}",
            "${Ansi.ORANGE}ossyN${Ansi.WHITE}MMMNyMMhsssssssssssssss${Ansi.ORANGE}hmmmh${Ansi.RESET}",
            "${Ansi.ORANGE}+sssshhhyNMMNyssssssssssssssss${Ansi.ORANGE}yNMMMy${Ansi.RESET}",
            " ${Ansi.ORANGE}.ssssssssdMMMNysssssssssssshmydMMy.${Ansi.RESET}",
            "  ${Ansi.ORANGE}-+sssssssssdmydMMMMMMMMddddyssssso-${Ansi.RESET}",
            "    ${Ansi.ORANGE}`:+ssssssssssssssssss+:`${Ansi.RESET}",
            "        ${Ansi.ORANGE}.-/+oossssoo+/-.${Ansi.RESET}"
        )
        val mem = readMeminfo()
        val info = ArrayList<String>()
        val u = ctx.shell.env["USER"] ?: Distro.USER
        info.add("${Ansi.ORANGE}${Ansi.BOLD}$u@${Distro.HOST}${Ansi.RESET}")
        info.add("-".repeat(u.length + Distro.HOST.length + 1))
        info.add("${Ansi.ORANGE}OS${Ansi.RESET}: ${Distro.PRETTY} ${Build.SUPPORTED_ABIS.firstOrNull() ?: ""}")
        info.add("${Ansi.ORANGE}Hôte${Ansi.RESET}: ${Build.MANUFACTURER} ${Build.MODEL}")
        info.add("${Ansi.ORANGE}Noyau${Ansi.RESET}: ${Fs.osKernel()}")
        info.add("${Ansi.ORANGE}Android${Ansi.RESET}: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        info.add("${Ansi.ORANGE}Disponibilité${Ansi.RESET}: ${uptime()}")
        info.add("${Ansi.ORANGE}Paquets${Ansi.RESET}: ${Apt.installedNames().size} (apt)")
        info.add("${Ansi.ORANGE}Shell${Ansi.RESET}: ${Distro.NAME} sh ${Distro.SHELL_VERSION}")
        info.add("${Ansi.ORANGE}Processeur${Ansi.RESET}: ${cpuName()} (${Runtime.getRuntime().availableProcessors()} cœurs)")
        info.add("${Ansi.ORANGE}Mémoire${Ansi.RESET}: $mem")
        info.add("${Ansi.ORANGE}Disque${Ansi.RESET}: ${diskInfo()}")
        info.add("")
        info.add(Ansi.palette.indices.take(8).joinToString("") { Ansi.ESC + "[4${it}m   " } + Ansi.RESET)
        val rows = maxOf(logo.size, info.size)
        for (i in 0 until rows) {
            val l = logo.getOrNull(i) ?: ""
            val plainLen = Ansi.strip(l).length
            ctx.println(l + " ".repeat((42 - plainLen).coerceAtLeast(2)) + (info.getOrNull(i) ?: ""))
        }
        return 0
    }

    private fun readMeminfo(): String = try {
        val lines = File("/proc/meminfo").readLines()
        val total = lines.firstOrNull { it.startsWith("MemTotal") }?.filter { it.isDigit() }?.toLongOrNull() ?: 0
        val avail = lines.firstOrNull { it.startsWith("MemAvailable") }?.filter { it.isDigit() }?.toLongOrNull() ?: 0
        "${(total - avail) / 1024} Mo / ${total / 1024} Mo"
    } catch (_: Exception) {
        val r = Runtime.getRuntime()
        "${(r.totalMemory() - r.freeMemory()) / 1048576} Mo (JVM)"
    }

    private fun cpuName(): String = try {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }
            ?.substringAfter(":")?.trim() ?: Build.HARDWARE
    } catch (_: Exception) { Build.HARDWARE }

    private fun uptime(): String = try {
        val s = File("/proc/uptime").readText().substringBefore(' ').toDouble().toLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        if (h > 0) "$h h $m min" else "$m min"
    } catch (_: Exception) { "inconnue" }

    private fun diskInfo(): String = try {
        val f = Fs.root
        "${Apt.fmtSize(f.totalSpace - f.freeSpace)} utilisés / ${Apt.fmtSize(f.totalSpace)}"
    } catch (_: Exception) { "inconnu" }

    // -------------------------------------------------------------------- htop

    private fun htop(ctx: ExecContext): Int {
        val load = try { File("/proc/loadavg").readText().trim() } catch (_: Exception) { "?" }
        ctx.println("${Ansi.BGREEN}${Ansi.BOLD}  htop 3.3.0${Ansi.RESET}   charge : ${load.split(" ").take(3).joinToString(" ")}   disponibilité : ${uptime()}")
        ctx.println("  Mémoire : ${readMeminfo()}")
        ctx.println()
        ctx.println("${Ansi.ESC}[7m  PID  UTIL.      RSS   ÉTAT  COMMANDE" + " ".repeat(30) + Ansi.RESET)
        var n = 0
        val procs = File("/proc").listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } } ?: emptyArray()
        for (p in procs.sortedBy { it.name.toIntOrNull() ?: 0 }) {
            try {
                val stat = File(p, "stat").readText().split(" ")
                val name = stat.getOrNull(1)?.trim('(', ')') ?: continue
                val state = stat.getOrNull(2) ?: "?"
                val rss = (stat.getOrNull(23)?.toLongOrNull() ?: 0) * 4096
                val cmdline = try {
                    File(p, "cmdline").readText().replace('\u0000', ' ').trim().ifBlank { name }
                } catch (_: Exception) { name }
                ctx.println("${p.name.padStart(5)}  ${Distro.USER.padEnd(9)} ${Apt.fmtSize(rss).padStart(8)}  ${state.padEnd(4)}  ${cmdline.take(40)}")
                n++
            } catch (_: Exception) { }
        }
        if (n == 0) ctx.println("${Ansi.GREY}(aucun processus lisible)${Ansi.RESET}")
        ctx.println()
        ctx.println("${Ansi.GREY}Android masque les processus des autres applications : seuls ceux de ce terminal sont visibles.${Ansi.RESET}")
        return 0
    }

    // ------------------------------------------------------------------ figlet

    private val FONT: Map<Char, List<String>> = mapOf(
        'A' to listOf(" ## ", "#  #", "####", "#  #", "#  #"),
        'B' to listOf("### ", "#  #", "### ", "#  #", "### "),
        'C' to listOf(" ###", "#   ", "#   ", "#   ", " ###"),
        'D' to listOf("### ", "#  #", "#  #", "#  #", "### "),
        'E' to listOf("####", "#   ", "### ", "#   ", "####"),
        'F' to listOf("####", "#   ", "### ", "#   ", "#   "),
        'G' to listOf(" ###", "#   ", "# ##", "#  #", " ###"),
        'H' to listOf("#  #", "#  #", "####", "#  #", "#  #"),
        'I' to listOf("###", " # ", " # ", " # ", "###"),
        'J' to listOf("####", "   #", "   #", "#  #", " ## "),
        'K' to listOf("#  #", "# # ", "##  ", "# # ", "#  #"),
        'L' to listOf("#   ", "#   ", "#   ", "#   ", "####"),
        'M' to listOf("#   #", "## ##", "# # #", "#   #", "#   #"),
        'N' to listOf("#  #", "## #", "# ##", "#  #", "#  #"),
        'O' to listOf(" ## ", "#  #", "#  #", "#  #", " ## "),
        'P' to listOf("### ", "#  #", "### ", "#   ", "#   "),
        'Q' to listOf(" ## ", "#  #", "#  #", "# ##", " ###"),
        'R' to listOf("### ", "#  #", "### ", "# # ", "#  #"),
        'S' to listOf(" ###", "#   ", " ## ", "   #", "### "),
        'T' to listOf("#####", "  #  ", "  #  ", "  #  ", "  #  "),
        'U' to listOf("#  #", "#  #", "#  #", "#  #", " ## "),
        'V' to listOf("#   #", "#   #", "#   #", " # # ", "  #  "),
        'W' to listOf("#   #", "#   #", "# # #", "## ##", "#   #"),
        'X' to listOf("#  #", " ## ", " ## ", " ## ", "#  #"),
        'Y' to listOf("#   #", " # # ", "  #  ", "  #  ", "  #  "),
        'Z' to listOf("####", "   #", "  # ", " #  ", "####"),
        '0' to listOf(" ## ", "#  #", "#  #", "#  #", " ## "),
        '1' to listOf(" # ", "## ", " # ", " # ", "###"),
        '2' to listOf("### ", "   #", " ## ", "#   ", "####"),
        '3' to listOf("### ", "   #", " ## ", "   #", "### "),
        '4' to listOf("#  #", "#  #", "####", "   #", "   #"),
        '5' to listOf("####", "#   ", "### ", "   #", "### "),
        '6' to listOf(" ###", "#   ", "### ", "#  #", " ## "),
        '7' to listOf("####", "   #", "  # ", " #  ", " #  "),
        '8' to listOf(" ## ", "#  #", " ## ", "#  #", " ## "),
        '9' to listOf(" ## ", "#  #", " ###", "   #", "### "),
        ' ' to listOf("  ", "  ", "  ", "  ", "  "),
        '!' to listOf("#", "#", "#", " ", "#"),
        '?' to listOf("### ", "   #", " ## ", "    ", " #  "),
        '.' to listOf(" ", " ", " ", " ", "#"),
        ',' to listOf(" ", " ", " ", "#", "#"),
        '-' to listOf("    ", "    ", "####", "    ", "    "),
        ':' to listOf(" ", "#", " ", "#", " "),
        '/' to listOf("   #", "  # ", " #  ", "#   ", "#   ")
    )

    private fun figlet(ctx: ExecContext): Int {
        val text = ctx.args.joinToString(" ").uppercase()
        if (text.isBlank()) { ctx.println("utilisation : figlet texte"); return 1 }
        val rows = Array(5) { StringBuilder() }
        for (ch in text) {
            val g = FONT[ch] ?: FONT['?']!!
            for (r in 0 until 5) rows[r].append(g.getOrElse(r) { "" }).append(" ")
        }
        rows.forEach { ctx.println(Ansi.ORANGE + it.toString().trimEnd() + Ansi.RESET) }
        return 0
    }

    // ------------------------------------------------------------------ cowsay

    private suspend fun cowsay(ctx: ExecContext): Int {
        var text = ctx.args.joinToString(" ")
        if (text.isBlank()) text = ctx.stdin.readAll().trim()
        if (text.isBlank()) text = "Meuh !"
        val lines = text.chunked(38)
        val w = lines.maxOf { it.length }
        ctx.println(" " + "_".repeat(w + 2))
        if (lines.size == 1) ctx.println("< ${lines[0].padEnd(w)} >")
        else lines.forEachIndexed { i, l ->
            val (a, b) = when (i) {
                0 -> Pair("/", "\\")
                lines.size - 1 -> Pair("\\", "/")
                else -> Pair("|", "|")
            }
            ctx.println("$a ${l.padEnd(w)} $b")
        }
        ctx.println(" " + "-".repeat(w + 2))
        ctx.println("        \\   ^__^")
        ctx.println("         \\  (oo)\\_______")
        ctx.println("            (__)\\       )\\/\\")
        ctx.println("                ||----w |")
        ctx.println("                ||     ||")
        return 0
    }

    private val FORTUNES = listOf(
        "Un ordinateur, c'est comme un climatiseur : ça ne marche plus quand on ouvre Windows.",
        "Il y a 10 sortes de gens : ceux qui comprennent le binaire et les autres.",
        "rm -rf / : la commande qui règle tous les problèmes, définitivement.",
        "La patience est une vertu, surtout pendant un apt upgrade.",
        "Un bon administrateur est un administrateur qui s'ennuie.",
        "sudo : parce que « s'il vous plaît » ne suffit pas toujours.",
        "Le nuage, c'est juste l'ordinateur de quelqu'un d'autre.",
        "Ne jamais faire confiance à un ordinateur qu'on ne peut pas jeter par la fenêtre.",
        "Un programme n'est jamais fini, il est seulement livré.",
        "Ctrl+S : le geste qui sauve, littéralement.",
        "grep : parce que chercher à la main, c'est pour les touristes.",
        "En informatique, il n'y a que deux problèmes difficiles : l'invalidation du cache, le nommage, et les erreurs de décalage.",
        "La documentation, c'est comme le sexe : quand c'est bien, c'est très bien ; quand c'est mauvais, c'est mieux que rien.",
        "Il ne s'agit pas d'un bogue, mais d'une fonctionnalité non documentée.",
        "Le plus court chemin entre deux points est souvent un tube."
    )

    private fun fortune(ctx: ExecContext): Int {
        ctx.println(FORTUNES.random())
        return 0
    }

    // ----------------------------------------------------------------- cmatrix

    private suspend fun cmatrix(ctx: ExecContext): Int = coroutineScope {
        val width = (ctx.session?.cols ?: 60).coerceIn(20, 120)
        val chars = "アイウエオカキクケコサシスセソタチツテトナニヌネノ0123456789:.=*+-<>"
        val rnd = java.util.Random()
        var frames = 0
        while (isActive && frames < 400) {
            val sb = StringBuilder()
            for (c in 0 until width) {
                sb.append(
                    when (rnd.nextInt(10)) {
                        0 -> Ansi.BGREEN + chars[rnd.nextInt(chars.length)]
                        1, 2 -> Ansi.GREEN + chars[rnd.nextInt(chars.length)]
                        else -> " "
                    }
                )
            }
            ctx.println(sb.toString() + Ansi.RESET)
            frames++
            delay(70)
        }
        ctx.println(Ansi.RESET)
        0
    }

    private suspend fun sl(ctx: ExecContext): Int = coroutineScope {
        val train = listOf(
            "      ====        ________                ___________",
            "  _D _|  |_______/        \\__I_I_____===__|_________|",
            "   |(_)---  |   H\\________/ |   |        =|___ ___|  ",
            "   /     |  |   H  |  |     |   |         ||_| |_||  ",
            "  |      |  |   H  |__--------------------| [___] |  ",
            "  | ________|___H__/__|_____/[][]~\\_______|       |  ",
            "  |/ |   |-----------I_____I [][] []  D   |=======|__",
            "__/ =| o |=-~~\\  /~~\\  /~~\\  /~~\\ ____Y___________|__",
            " |/-=|___|=O=====O=====O=====O   |_____/~\\___/        ",
            "  \\_/      \\__/  \\__/  \\__/  \\__/      \\_/            "
        )
        val width = (ctx.session?.cols ?: 60).coerceIn(30, 120)
        var pos = width
        while (isActive && pos > -55) {
            train.forEach { line ->
                val shifted = if (pos >= 0) " ".repeat(pos) + line.take((width - pos).coerceAtLeast(0))
                else line.drop(-pos).take(width)
                ctx.println(Ansi.BYELLOW + shifted + Ansi.RESET)
            }
            pos -= 8
            delay(160)
        }
        ctx.println("${Ansi.GREY}(vous vouliez sûrement taper « ls »)${Ansi.RESET}")
        0
    }

    // -------------------------------------------------------------- zip/unzip

    private suspend fun zip(ctx: ExecContext): Int = withContext(Dispatchers.IO) {
        val ops = ctx.operands()
        if (ops.size < 2) { ctx.println("utilisation : zip archive.zip fichier..."); return@withContext 2 }
        val archive = ctx.resolve(ops[0])
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zos ->
                for (name in ops.drop(1)) {
                    val f = ctx.resolve(name)
                    if (!f.exists()) { ctx.error("zip : ${name} introuvable"); continue }
                    fun add(file: File, base: String) {
                        if (file.isDirectory) {
                            file.listFiles()?.forEach { add(it, "$base${file.name}/") }
                        } else {
                            zos.putNextEntry(ZipEntry(base + file.name))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                            ctx.println("  ajout : $base${file.name} (${Apt.fmtSize(file.length())})")
                        }
                    }
                    add(f, "")
                }
            }
            ctx.println("Archive ${Fs.pretty(archive)} — ${Apt.fmtSize(archive.length())}")
            0
        } catch (e: Exception) { ctx.error("zip : ${e.message}"); 1 }
    }

    private suspend fun unzip(ctx: ExecContext): Int = withContext(Dispatchers.IO) {
        val ops = ctx.operands("-d")
        val name = ops.firstOrNull()
        if (name == null) { ctx.println("utilisation : unzip [-l] archive.zip [-d dossier]"); return@withContext 2 }
        val archive = ctx.resolve(name)
        if (!archive.isFile) { ctx.error("unzip : impossible de trouver ${name}"); return@withContext 9 }
        val dest = ctx.option("-d")?.let { ctx.resolve(it) } ?: ctx.shell.cwd
        return@withContext try {
            ZipFile(archive).use { zf ->
                if (ctx.flag("-l")) {
                    ctx.println("Archive:  ${Fs.pretty(archive)}")
                    ctx.println("  Length      Date    Time    Name")
                    ctx.println("---------  ---------- -----   ----")
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE)
                    zf.entries().toList().forEach {
                        ctx.println("${it.size.toString().padStart(9)}  ${df.format(Date(it.time))}   ${it.name}")
                    }
                } else {
                    ctx.println("Archive:  ${Fs.pretty(archive)}")
                    zf.entries().toList().forEach { e ->
                        val out = File(dest, e.name)
                        if (e.isDirectory) out.mkdirs()
                        else {
                            out.parentFile?.mkdirs()
                            zf.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                            ctx.println("  extraction : ${e.name}")
                        }
                    }
                }
            }
            0
        } catch (e: Exception) { ctx.error("unzip : ${e.message}"); 1 }
    }
}
