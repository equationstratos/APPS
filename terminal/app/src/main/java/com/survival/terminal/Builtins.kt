package com.survival.terminal

import java.io.File

/** Commandes integrees au shell (toujours disponibles, aucun paquet requis). */
object Builtins {

    val names = setOf(
        "cd", "pwd", "exit", "logout", "clear", "export", "unset", "env", "printenv",
        "alias", "unalias", "history", "echo", "source", ".", "which", "type", "help",
        "true", "false", "read", "whoami", "motd", "lsb_release", "version", "man",
        "compgen", "commandes"
    )

    suspend fun run(ctx: ExecContext): Int {
        val sh = ctx.shell
        return when (ctx.name) {
            "cd" -> cd(ctx)
            "pwd" -> { ctx.println(sh.cwd.absolutePath); 0 }
            "exit", "logout" -> { sh.exitRequested = true; ctx.println("déconnexion"); 0 }
            "clear" -> { ctx.session?.buffer?.clear(); 0 }
            "export" -> export(ctx)
            "unset" -> { ctx.args.forEach { sh.env.remove(it) }; 0 }
            "env", "printenv" -> { sh.env.forEach { (k, v) -> ctx.println("$k=$v") }; 0 }
            "alias" -> alias(ctx)
            "unalias" -> { ctx.args.forEach { sh.aliases.remove(it) }; 0 }
            "history" -> history(ctx)
            "echo" -> echo(ctx)
            "source", "." -> source(ctx)
            "which", "type" -> which(ctx)
            "true" -> 0
            "false" -> 1
            "read" -> read(ctx)
            "whoami" -> { ctx.println(sh.env["USER"] ?: Distro.USER); 0 }
            "motd" -> { ctx.print(Fs.motd()); 0 }
            "lsb_release" -> lsbRelease(ctx)
            "version" -> { ctx.println("${Distro.NAME} ${Distro.VERSION} — shell ${Distro.SHELL_VERSION}"); 0 }
            "help" -> help(ctx)
            "compgen", "commandes" -> compgen(ctx)
            "man" -> man(ctx)
            else -> 127
        }
    }

    private fun cd(ctx: ExecContext): Int {
        val sh = ctx.shell
        val arg = ctx.args.firstOrNull()
        val target = when (arg) {
            null, "~" -> Fs.home
            "-" -> sh.prevCwd
            else -> Fs.resolve(sh.cwd, arg)
        }
        if (!target.exists()) { ctx.error("cd: ${arg}: aucun fichier ou dossier de ce nom"); return 1 }
        if (!target.isDirectory) { ctx.error("cd: ${arg}: n'est pas un dossier"); return 1 }
        if (!target.canRead()) { ctx.error("cd: ${arg}: permission refusée"); return 1 }
        sh.prevCwd = sh.cwd
        sh.cwd = target
        sh.env["PWD"] = target.absolutePath
        if (arg == "-") ctx.println(target.absolutePath)
        return 0
    }

    private fun export(ctx: ExecContext): Int {
        if (ctx.args.isEmpty()) {
            ctx.shell.env.forEach { (k, v) -> ctx.println("declare -x $k=\"$v\"") }
            return 0
        }
        for (a in ctx.args) {
            if (a.contains('=')) {
                val (k, v) = a.split("=", limit = 2)
                ctx.shell.env[k] = v
            }
        }
        return 0
    }

    private fun alias(ctx: ExecContext): Int {
        if (ctx.args.isEmpty()) {
            ctx.shell.aliases.forEach { (k, v) -> ctx.println("alias $k='$v'") }
            return 0
        }
        for (a in ctx.args) {
            if (a.contains('=')) {
                val (k, v) = a.split("=", limit = 2)
                ctx.shell.aliases[k] = v.trim('\'', '"')
            } else ctx.shell.aliases[a]?.let { ctx.println("alias $a='$it'") }
        }
        return 0
    }

    private fun history(ctx: ExecContext): Int {
        if (ctx.args.firstOrNull() == "-c") { ctx.shell.history.clear(); return 0 }
        ctx.shell.history.forEachIndexed { i, h ->
            ctx.println(String.format("%5d  %s", i + 1, h))
        }
        return 0
    }

    private fun echo(ctx: ExecContext): Int {
        var args = ctx.args
        var newline = true
        var escapes = false
        while (args.isNotEmpty() && args[0].matches(Regex("^-[neE]+$"))) {
            if (args[0].contains('n')) newline = false
            if (args[0].contains('e')) escapes = true
            args = args.drop(1)
        }
        var s = args.joinToString(" ")
        if (escapes) s = s.replace("\\n", "\n").replace("\\t", "\t")
            .replace("\\e", Ansi.ESC).replace("\\033", Ansi.ESC).replace("\\\\", "\\")
        ctx.print(if (newline) s + "\n" else s)
        return 0
    }

    private suspend fun source(ctx: ExecContext): Int {
        val path = ctx.args.firstOrNull() ?: run { ctx.error("source: argument manquant"); return 2 }
        val f = ctx.resolve(path)
        if (!f.isFile) { ctx.error("source: $path : fichier introuvable"); return 1 }
        var status = 0
        for (line in f.readLines()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            status = ctx.shell.execute(t, ctx.out, ctx.err, EmptyStdIn)
        }
        return status
    }

    private fun which(ctx: ExecContext): Int {
        var status = 0
        for (n in ctx.args) {
            when {
                names.contains(n) -> ctx.println("$n : commande intégrée au shell")
                File(Fs.bin, n).isFile -> ctx.println(File(Fs.bin, n).absolutePath)
                Registry.find(n)?.let { Apt.isInstalled(it.pkg) } == true ->
                    ctx.println("${Fs.bin.absolutePath}/$n (fourni par ${Registry.find(n)!!.pkg})")
                else -> {
                    val f = Exec.which(ctx.shell, n)
                    if (f != null) ctx.println(f.absolutePath)
                    else { ctx.err.write("$n : introuvable\n"); status = 1 }
                }
            }
        }
        return status
    }

    private suspend fun read(ctx: ExecContext): Int {
        val varName = ctx.operands().firstOrNull() ?: "REPLY"
        val prompt = ctx.option("-p") ?: ""
        val line = if (ctx.session != null) ctx.ask(prompt) else ctx.stdin.readLine()
        ctx.shell.env[varName] = line ?: return 1
        return 0
    }

    private fun lsbRelease(ctx: ExecContext): Int {
        val all = ctx.args.isEmpty() || ctx.flag("-a", "--all")
        if (all || ctx.flag("-i")) ctx.println("Distributor ID:\t${Distro.NAME}")
        if (all || ctx.flag("-d")) ctx.println("Description:\t${Distro.PRETTY}")
        if (all || ctx.flag("-r")) ctx.println("Release:\t${Distro.VERSION}")
        if (all || ctx.flag("-c")) ctx.println("Codename:\t${Distro.CODENAME}")
        return 0
    }

    private fun man(ctx: ExecContext): Int {
        val page = ctx.args.firstOrNull()
        if (page == null) { ctx.error("De quelle page de manuel avez-vous besoin ?"); return 1 }
        val cmd = Registry.find(page)
        if (cmd != null) {
            ctx.println("${Ansi.BOLD}NOM${Ansi.RESET}")
            ctx.println("       $page — ${cmd.summary}")
            ctx.println()
            ctx.println("${Ansi.BOLD}SYNOPSIS${Ansi.RESET}")
            ctx.println("       ${cmd.usage}")
            ctx.println()
            ctx.println("${Ansi.BOLD}PAQUET${Ansi.RESET}")
            ctx.println("       ${cmd.pkg}" + if (Apt.isInstalled(cmd.pkg)) " (installé)" else " (non installé)")
            return 0
        }
        if (names.contains(page)) {
            ctx.println("$page est une commande intégrée au shell. Voir « help ».")
            return 0
        }
        ctx.error("Pas d'entrée de manuel pour $page")
        return 1
    }

    /**
     * Liste ce qui est réellement exécutable : commandes internes, alias,
     * commandes des paquets installés, et tous les programmes du PATH.
     * Équivalent de « compgen -c » sur bash.
     */
    private fun compgen(ctx: ExecContext): Int {
        val args = ctx.args
        val onlyBuiltins = args.contains("-b")
        val onlyAliases = args.contains("-a")
        val all = !onlyBuiltins && !onlyAliases
        val prefix = ctx.operands().firstOrNull() ?: ""
        val found = java.util.TreeSet<String>()

        if (onlyBuiltins || all) found.addAll(names)
        if (onlyAliases || all) found.addAll(ctx.shell.aliases.keys)
        if (all) {
            Registry.commands.values
                .filter { Apt.isInstalled(it.pkg) }
                .forEach { found.add(it.name) }
            Fs.bin.list()?.forEach { found.add(it) }
            for (dir in (ctx.shell.env["PATH"] ?: "").split(':')) {
                if (dir.isBlank()) continue
                java.io.File(dir).list()?.forEach { found.add(it) }
            }
        }
        val result = found.filter { it.startsWith(prefix) }
        if (ctx.name == "commandes") {
            // présentation lisible, en colonnes
            val width = ctx.session?.cols ?: 80
            // largeur de colonne bornée : un nom très long ne doit pas tout aplatir
            val colw = ((result.maxOfOrNull { it.length } ?: 10) + 2).coerceIn(10, 22)
            val cols = (width / colw).coerceAtLeast(1)
            val sb = StringBuilder()
            result.forEachIndexed { i, n ->
                sb.append(n.padEnd(colw))
                if ((i + 1) % cols == 0) { ctx.println(sb.toString().trimEnd()); sb.setLength(0) }
            }
            if (sb.isNotEmpty()) ctx.println(sb.toString().trimEnd())
            ctx.println()
            ctx.println("${Ansi.BOLD}${result.size}${Ansi.RESET} commandes disponibles.")
        } else {
            result.forEach { ctx.println(it) }
        }
        return if (result.isEmpty()) 1 else 0
    }

    private fun help(ctx: ExecContext): Int {
        val a = Ansi
        ctx.println("${a.BOLD}${a.ORANGE}${Distro.PRETTY}${a.RESET} — aide")
        ctx.println()
        ctx.println("${a.BOLD}Commandes du shell${a.RESET}")
        ctx.println("  cd, pwd, echo, export, unset, alias, history, read, clear, exit")
        ctx.println("  source, which, type, man, whoami, lsb_release, commandes, help")
        ctx.println()
        ctx.println("${a.BOLD}Gestion des paquets${a.RESET}")
        ctx.println("  apt update                met à jour la liste des paquets")
        ctx.println("  apt list                  affiche les paquets disponibles")
        ctx.println("  apt search <mot>          recherche un paquet")
        ctx.println("  apt show <paquet>         détails d'un paquet")
        ctx.println("  apt install <paquet>      installe un paquet")
        ctx.println("  apt remove <paquet>       désinstalle un paquet")
        ctx.println("  dpkg -l                   paquets installés")
        ctx.println()
        ctx.println("${a.BOLD}Commandes fournies par les paquets${a.RESET}")
        val byPkg = Registry.commands.values.distinctBy { it.name }.groupBy { it.pkg }
        for ((pkg, cmds) in byPkg.toSortedMap()) {
            val mark = if (Apt.isInstalled(pkg)) "${a.BGREEN}[installé]${a.RESET}" else "${a.GREY}[absent]${a.RESET}"
            ctx.println("  ${pkg.padEnd(18)} $mark  ${cmds.joinToString(", ") { it.name }}")
        }
        ctx.println()
        ctx.println("${a.BOLD}Programmes du système${a.RESET}")
        ctx.println("  ls, cat, grep, sed, find, ps, top, df, du, tar, gzip, chmod, mount...")
        ctx.println("  Ce ne sont pas des commandes du shell mais de vrais programmes de")
        ctx.println("  /system/bin : c'est pourquoi ils n'apparaissent pas dans la liste")
        ctx.println("  ci-dessus (bash fait pareil avec son propre « help »).")
        ctx.println("  ${a.CYAN}commandes${a.RESET} affiche tout ce qui est exécutable ici,")
        ctx.println("  ${a.CYAN}compgen -c${a.RESET} fait la même chose en une colonne.")
        ctx.println("  Tubes, redirections et jokers sont gérés : ${a.CYAN}ps -A | grep term${a.RESET}")
        ctx.println()
        ctx.println("${a.BOLD}Raccourcis${a.RESET}")
        ctx.println("  Flèches haut/bas : historique    TAB : complétion")
        ctx.println("  Ctrl+C : interrompt             Ctrl+D : fin de saisie")
        ctx.println()
        return 0
    }
}
