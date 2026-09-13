package com.survival.terminal

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Identité de la distribution embarquée. */
object Distro {
    const val NAME = "AndroLinux"
    const val VERSION = "24.04"
    const val CODENAME = "noble-mobile"
    const val PRETTY = "AndroLinux 24.04 LTS (noble-mobile)"
    const val USER = "user"
    const val HOST = "localhost"
    const val SHELL_VERSION = "1.0"
}

/**
 * Arborescence façon Unix, posée dans le stockage privé de l'application.
 * On n'invente pas de faux "/" : comme sur Termux, les chemins sont réels et
 * $PREFIX pointe sur le répertoire privé de l'app. Le reste du système de
 * fichiers Android (/system, /sdcard...) reste accessible normalement.
 */
object Fs {
    lateinit var root: File; private set
    lateinit var prefix: File; private set
    lateinit var home: File; private set
    lateinit var assets: AssetManager; private set

    val bin: File get() = File(prefix, "bin")
    val etc: File get() = File(prefix, "etc")
    val tmp: File get() = File(prefix, "tmp")
    val aptEtc: File get() = File(etc, "apt")
    val aptLists: File get() = File(prefix, "var/lib/apt/lists")
    val dpkgDir: File get() = File(prefix, "var/lib/dpkg")
    val logDir: File get() = File(prefix, "var/log")
    val docDir: File get() = File(prefix, "share/doc")
    val sshDir: File get() = File(home, ".ssh")

    fun init(ctx: Context) {
        root = ctx.filesDir
        prefix = File(root, "usr")
        home = File(root, "home")
        assets = ctx.assets
    }

    /** Crée l'arborescence au premier lancement. Retourne true si c'est un premier démarrage. */
    fun bootstrap(): Boolean {
        val marker = File(prefix, ".bootstrapped")
        val first = !marker.exists()
        listOf(
            bin, etc, tmp, aptEtc, aptLists, dpkgDir, logDir, docDir,
            File(prefix, "lib"), File(prefix, "share/man"), File(prefix, "var/cache/apt/archives"),
            home, sshDir, File(home, "Documents"), File(home, "Téléchargements")
        ).forEach { it.mkdirs() }

        writeIfAbsent(File(etc, "os-release"), """
            PRETTY_NAME="${Distro.PRETTY}"
            NAME="${Distro.NAME}"
            VERSION_ID="${Distro.VERSION}"
            VERSION="${Distro.VERSION} LTS (${Distro.CODENAME})"
            ID=androlinux
            ID_LIKE=debian
            HOME_URL="https://github.com/equationstratos/APPS"
            SUPPORT_URL="commande: help"
            UBUNTU_CODENAME=${Distro.CODENAME}
        """.trimIndent() + "\n")

        writeIfAbsent(File(etc, "hostname"), Distro.HOST + "\n")
        writeIfAbsent(File(etc, "hosts"), "127.0.0.1\tlocalhost\n::1\tlocalhost ip6-localhost\n")
        writeIfAbsent(File(etc, "shells"), "$prefix/bin/sh\n/system/bin/sh\n")
        writeIfAbsent(File(etc, "passwd"),
            "root:x:0:0:root:/root:/system/bin/sh\n" +
            "${Distro.USER}:x:1000:1000:${Distro.USER},,,:$home:/system/bin/sh\n")
        writeIfAbsent(File(etc, "group"), "root:x:0:\n${Distro.USER}:x:1000:\n")
        writeIfAbsent(File(etc, "resolv.conf"), "nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        writeIfAbsent(File(aptEtc, "sources.list"), """
            # Dépôt embarqué dans l'application (hors-ligne, toujours disponible)
            deb asset://repo ${Distro.CODENAME} main
            # Ajoutez ici vos propres dépôts HTTP servant un index.json :
            # deb https://exemple.tld/monrepo ${Distro.CODENAME} main
        """.trimIndent() + "\n")
        writeIfAbsent(File(home, ".profile"), """
            # ~/.profile : exécuté à l'ouverture de chaque session
            export EDITOR=nano
            alias ll='ls -alF'
            alias la='ls -A'
            alias l='ls -CF'
            alias ..='cd ..'
        """.trimIndent() + "\n")
        writeIfAbsent(File(home, "README.txt"), """
            Bienvenue dans votre dossier personnel.

            Quelques pistes :
              apt list              liste les paquets disponibles
              apt install openssh-client
              ssh utilisateur@serveur
              nano notes.txt        éditeur de texte
              help                  aide complète
        """.trimIndent() + "\n")

        if (first) marker.writeText(now() + "\n")
        return first
    }

    fun motd(): String {
        val a = Ansi
        val sb = StringBuilder()
        sb.append("${a.BOLD}Bienvenue dans ${Distro.PRETTY}${a.RESET}\n")
        sb.append(" * Noyau     : ${osKernel()}\n")
        sb.append(" * Appareil  : ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
        sb.append(" * Arch      : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "inconnue"}\n")
        sb.append("\n")
        sb.append(" * Paquets   : ${a.CYAN}apt install <paquet>${a.RESET}   (apt list pour tout voir)\n")
        sb.append(" * SSH       : ${a.CYAN}apt install openssh-client${a.RESET} puis ${a.CYAN}ssh user@hote${a.RESET}\n")
        sb.append(" * Aide      : ${a.CYAN}help${a.RESET}\n")
        sb.append("\n")
        sb.append("Dernière connexion : ${now()}\n\n")
        return sb.toString()
    }

    fun osKernel(): String = try {
        System.getProperty("os.version") ?: "Linux"
    } catch (_: Exception) { "Linux" }

    fun now(): String =
        SimpleDateFormat("EEE d MMM yyyy 'à' HH:mm:ss", Locale.FRANCE).format(Date())

    private fun writeIfAbsent(f: File, content: String) {
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            try { f.writeText(content) } catch (_: Exception) { }
        }
    }

    /** Rend un chemin lisible : $HOME -> ~, $PREFIX -> $PREFIX. */
    fun pretty(f: File): String {
        val p = f.absolutePath
        return when {
            p == home.absolutePath -> "~"
            p.startsWith(home.absolutePath + "/") -> "~" + p.substring(home.absolutePath.length)
            p.startsWith(prefix.absolutePath) -> "\$PREFIX" + p.substring(prefix.absolutePath.length)
            else -> p
        }
    }

    /** Résout un chemin utilisateur (relatif, ~, $PREFIX) en fichier réel. */
    fun resolve(cwd: File, path: String): File {
        var p = path
        if (p == "~") return home
        if (p.startsWith("~/")) p = home.absolutePath + p.substring(1)
        if (p.startsWith("\$PREFIX")) p = prefix.absolutePath + p.substring(7)
        val f = if (p.startsWith("/")) File(p) else File(cwd, p)
        return File(normalize(f.absolutePath))
    }

    private fun normalize(path: String): String {
        val parts = ArrayList<String>()
        for (seg in path.split("/")) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return "/" + parts.joinToString("/")
    }
}
