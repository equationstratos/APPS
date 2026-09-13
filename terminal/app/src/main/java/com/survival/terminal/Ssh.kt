package com.survival.terminal

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64

/** Flux de sortie qui écrit dans un Sink du terminal. */
class SinkOutputStream(private val sink: Sink) : OutputStream() {
    private val buf = StringBuilder()
    override fun write(b: Int) { sink.write(byteArrayOf(b.toByte()).toString(Charsets.UTF_8)) }
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len > 0) sink.write(String(b, off, len, Charsets.UTF_8))
    }
}

/** Client SSH réel (protocole SSH-2), adossé à la bibliothèque JSch. */
object Ssh {

    fun register() {
        Registry.reg("ssh", "openssh-client", "client de connexion à distance",
            "ssh [-p port] [-i cle] [-o opt=val] [utilisateur@]hote [commande]") { ssh(it) }
        Registry.reg("scp", "openssh-client", "copie de fichiers par SSH",
            "scp [-P port] [-i cle] source destination  (hote distant : user@hote:/chemin)") { scp(it) }
        Registry.reg("sftp", "openssh-client", "transfert de fichiers interactif",
            "sftp [-P port] [utilisateur@]hote") { sftp(it) }
        Registry.reg("ssh-keygen", "openssh-client", "génération de clés SSH",
            "ssh-keygen [-t rsa|ecdsa|ed25519] [-b bits] [-f fichier] [-C commentaire]") { keygen(it) }
    }

    // ------------------------------------------------------------------ options

    private class Target(val user: String, val host: String, val port: Int)

    private fun parseTarget(spec: String, defUser: String, port: Int): Target {
        val at = spec.lastIndexOf('@')
        val user = if (at > 0) spec.substring(0, at) else defUser
        val host = if (at > 0) spec.substring(at + 1) else spec
        return Target(user, host, port)
    }

    private fun newJSch(ctx: ExecContext, identity: String?): JSch {
        val jsch = JSch()
        Fs.sshDir.mkdirs()
        val known = File(Fs.sshDir, "known_hosts")
        if (!known.exists()) known.writeText("")
        try { jsch.setKnownHosts(known.absolutePath) } catch (_: Exception) { }
        val keys = if (identity != null) listOf(ctx.resolve(identity))
        else listOf("id_ed25519", "id_ecdsa", "id_rsa").map { File(Fs.sshDir, it) }
        for (k in keys) {
            if (k.isFile) {
                try { jsch.addIdentity(k.absolutePath) } catch (_: Exception) {
                    // clé protégée par phrase de passe : JSch la redemandera via UserInfo
                    try { jsch.addIdentity(k.absolutePath, null as String?) } catch (_: Exception) { }
                }
            }
        }
        return jsch
    }

    private class TermUserInfo(val ctx: ExecContext) : UserInfo, UIKeyboardInteractive {
        private var pass: String? = null
        private var phrase: String? = null
        override fun getPassword(): String? = pass
        override fun getPassphrase(): String? = phrase

        override fun promptPassword(message: String?): Boolean {
            pass = runBlocking { ctx.ask((message ?: "Mot de passe") + " : ", echo = false) }
            return pass != null
        }

        override fun promptPassphrase(message: String?): Boolean {
            phrase = runBlocking { ctx.ask((message ?: "Phrase de passe") + " : ", echo = false) }
            return phrase != null
        }

        override fun promptYesNo(message: String?): Boolean {
            ctx.out.write((message ?: "") + "\n")
            val a = runBlocking { ctx.ask("Voulez-vous continuer ? (yes/no) ") }
            val v = a?.trim()?.lowercase()
            return v == "yes" || v == "y" || v == "oui" || v == "o"
        }

        override fun showMessage(message: String?) { ctx.out.write((message ?: "") + "\n") }

        override fun promptKeyboardInteractive(
            destination: String?, name: String?, instruction: String?,
            prompt: Array<out String>?, echo: BooleanArray?
        ): Array<String>? {
            if (!instruction.isNullOrBlank()) ctx.out.write(instruction + "\n")
            val answers = ArrayList<String>()
            prompt?.forEachIndexed { i, p ->
                val visible = echo?.getOrNull(i) ?: false
                val a = runBlocking { ctx.ask(p, echo = visible) } ?: return null
                answers.add(a)
            }
            return answers.toTypedArray()
        }
    }

    // --------------------------------------------------------------------- ssh

    private suspend fun ssh(ctx: ExecContext): Int {
        val args = ctx.args
        if (args.isEmpty() || ctx.flag("-h", "--help")) {
            ctx.println("utilisation : ssh [-p port] [-i identite] [-o option=valeur] [utilisateur@]hote [commande]")
            return if (args.isEmpty()) 2 else 0
        }
        var port = 22
        var identity: String? = null
        var user = ctx.shell.env["USER"] ?: Distro.USER
        val options = LinkedHashMap<String, String>()
        var target: String? = null
        val remote = ArrayList<String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                target != null -> remote.add(a)
                a == "-p" -> { port = args.getOrNull(++i)?.toIntOrNull() ?: 22 }
                a == "-i" -> { identity = args.getOrNull(++i) }
                a == "-l" -> { user = args.getOrNull(++i) ?: user }
                a == "-o" -> {
                    val kv = args.getOrNull(++i) ?: ""
                    val p = kv.split("=", limit = 2)
                    if (p.size == 2) options[p[0]] = p[1]
                }
                a == "-v" || a == "-q" || a == "-t" || a == "-T" || a == "-C" -> {}
                a.startsWith("-") -> {}
                else -> target = a
            }
            i++
        }
        if (target == null) { ctx.error("ssh : hôte manquant"); return 2 }
        val t = parseTarget(target, user, port)
        val command = if (remote.isEmpty()) null else remote.joinToString(" ")

        return withContext(Dispatchers.IO) {
            val jsch = newJSch(ctx, identity)
            var sess: com.jcraft.jsch.Session? = null
            try {
                sess = jsch.getSession(t.user, t.host, t.port)
                sess.setUserInfo(TermUserInfo(ctx))
                sess.setConfig("StrictHostKeyChecking", options["StrictHostKeyChecking"] ?: "ask")
                sess.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
                for ((k, v) in options) if (k != "StrictHostKeyChecking") sess.setConfig(k, v)
                sess.connect(25000)
                ctx.session?.buffer?.write(
                    "${Ansi.GREY}Connecté à ${t.host} (${sess.serverVersion})${Ansi.RESET}\n")
                if (command != null) execRemote(ctx, sess, command)
                else interactive(ctx, sess, t)
            } catch (e: Exception) {
                val m = e.message ?: "erreur inconnue"
                when {
                    m.contains("Auth fail", true) || m.contains("Auth cancel", true) ->
                        ctx.error("${t.user}@${t.host} : permission refusée (publickey, password).")
                    m.contains("UnknownHost", true) || m.contains("resolve", true) ->
                        ctx.error("ssh : impossible de résoudre le nom d'hôte ${t.host}")
                    m.contains("Connection refused", true) ->
                        ctx.error("ssh : connexion refusée par ${t.host}:${t.port}")
                    m.contains("reject HostKey", true) ->
                        ctx.error("Clé d'hôte refusée : connexion abandonnée.")
                    else -> ctx.error("ssh : $m")
                }
                255
            } finally {
                try { sess?.disconnect() } catch (_: Exception) { }
            }
        }
    }

    private suspend fun execRemote(ctx: ExecContext, sess: com.jcraft.jsch.Session, command: String): Int =
        coroutineScope {
            val ch = sess.openChannel("exec") as ChannelExec
            ch.setCommand(command)
            ch.setErrStream(SinkOutputStream(ctx.err))
            val ins = ch.inputStream
            ch.connect(15000)
            val buf = ByteArray(4096)
            while (isActive) {
                while (ins.available() > 0) {
                    val n = ins.read(buf); if (n < 0) break
                    ctx.out.write(String(buf, 0, n, Charsets.UTF_8))
                }
                if (ch.isClosed) break
                delay(40)
            }
            val code = ch.exitStatus
            ch.disconnect()
            if (code < 0) 0 else code
        }

    private suspend fun interactive(ctx: ExecContext, sess: com.jcraft.jsch.Session, t: Target): Int =
        coroutineScope {
            val ch = sess.openChannel("shell") as ChannelShell
            val cols = (ctx.session?.cols ?: 80).coerceIn(20, 200)
            ch.setPtyType("xterm-256color", cols, 40, 0, 0)
            val ins = ch.inputStream
            val outs = ch.outputStream
            ch.connect(15000)
            val term = ctx.session
            term?.echoMode = EchoMode.NONE
            term?.rawSink = { s ->
                try { outs.write(s.toByteArray(Charsets.UTF_8)); outs.flush() } catch (_: Exception) { }
            }
            term?.statusLine = "ssh ${t.user}@${t.host}"
            val reader = launch(Dispatchers.IO) {
                val buf = ByteArray(4096)
                try {
                    while (isActive) {
                        while (ins.available() > 0) {
                            val n = ins.read(buf); if (n < 0) return@launch
                            ctx.out.write(String(buf, 0, n, Charsets.UTF_8))
                        }
                        if (ch.isClosed) return@launch
                        delay(30)
                    }
                } catch (_: Exception) { }
            }
            // Envoi du clavier vers le serveur, dans sa propre tâche : quand le shell
            // distant se termine (« exit »), le lecteur s'arrête et libère la session,
            // sans attendre que l'utilisateur tape encore quelque chose.
            val writer = launch {
                while (isActive) {
                    val line = term?.readRaw() ?: break
                    try {
                        // Entrée = retour chariot, comme un vrai terminal (le pty distant le convertit)
                        outs.write((line + "\r").toByteArray(Charsets.UTF_8)); outs.flush()
                    } catch (_: Exception) { break }
                }
            }
            try {
                reader.join()
            } finally {
                writer.cancel()
                reader.cancel()
                term?.rawSink = null
                term?.echoMode = EchoMode.FULL
                term?.statusLine = null
                try { ch.disconnect() } catch (_: Exception) { }
            }
            ctx.out.write("${Ansi.GREY}Connexion à ${t.host} fermée.${Ansi.RESET}\n")
            0
        }

    // --------------------------------------------------------------------- scp

    private suspend fun scp(ctx: ExecContext): Int {
        val ops = ctx.operands("-P", "-i", "-o", "-l", "-c")
        if (ops.size < 2) {
            ctx.println("utilisation : scp [-P port] [-i cle] source destination")
            ctx.println("  exemple : scp notes.txt user@serveur:/tmp/")
            ctx.println("  exemple : scp user@serveur:/etc/hostname .")
            return 2
        }
        val port = ctx.option("-P")?.toIntOrNull() ?: 22
        val identity = ctx.option("-i")
        val src = ops[0]
        val dst = ops[1]
        val srcRemote = isRemoteSpec(src)
        val dstRemote = isRemoteSpec(dst)
        if (srcRemote == dstRemote) {
            ctx.error("scp : indiquez exactement un chemin distant (user@hote:/chemin)")
            return 2
        }
        val spec = if (srcRemote) src else dst
        val hostPart = spec.substringBefore(':')
        val remotePath = spec.substringAfter(':')
        val t = parseTarget(hostPart, ctx.shell.env["USER"] ?: Distro.USER, port)

        return withContext(Dispatchers.IO) {
            val jsch = newJSch(ctx, identity)
            var sess: com.jcraft.jsch.Session? = null
            try {
                sess = jsch.getSession(t.user, t.host, t.port)
                sess.setUserInfo(TermUserInfo(ctx))
                sess.setConfig("StrictHostKeyChecking", "ask")
                sess.connect(25000)
                val sftpCh = sess.openChannel("sftp") as ChannelSftp
                sftpCh.connect(15000)
                if (srcRemote) {
                    val localFile = ctx.resolve(dst).let {
                        if (it.isDirectory) File(it, remotePath.substringAfterLast('/')) else it
                    }
                    sftpCh.get(remotePath, localFile.absolutePath)
                    ctx.println("${remotePath.substringAfterLast('/')}  100%  ${Apt.fmtSize(localFile.length())}  -> ${Fs.pretty(localFile)}")
                } else {
                    val localFile = ctx.resolve(src)
                    if (!localFile.isFile) { ctx.error("scp : ${src} : fichier introuvable"); return@withContext 1 }
                    val target = if (remotePath.isEmpty()) localFile.name else remotePath
                    sftpCh.put(localFile.absolutePath, target)
                    ctx.println("${localFile.name}  100%  ${Apt.fmtSize(localFile.length())}  -> ${t.host}:$target")
                }
                sftpCh.exit()
                0
            } catch (e: Exception) {
                ctx.error("scp : ${e.message}")
                1
            } finally {
                try { sess?.disconnect() } catch (_: Exception) { }
            }
        }
    }

    private fun isRemoteSpec(s: String): Boolean {
        val c = s.indexOf(':')
        return c > 0 && !s.startsWith("/") && !s.startsWith(".")
    }

    // -------------------------------------------------------------------- sftp

    private suspend fun sftp(ctx: ExecContext): Int {
        val ops = ctx.operands("-P", "-i", "-o", "-l")
        if (ops.isEmpty()) { ctx.println("utilisation : sftp [-P port] [utilisateur@]hote"); return 2 }
        val port = ctx.option("-P")?.toIntOrNull() ?: 22
        val t = parseTarget(ops[0], ctx.shell.env["USER"] ?: Distro.USER, port)
        val term = ctx.session ?: run { ctx.error("sftp : session interactive requise"); return 1 }
        return withContext(Dispatchers.IO) {
            val jsch = newJSch(ctx, ctx.option("-i"))
            var sess: com.jcraft.jsch.Session? = null
            try {
                sess = jsch.getSession(t.user, t.host, t.port)
                sess.setUserInfo(TermUserInfo(ctx))
                sess.setConfig("StrictHostKeyChecking", "ask")
                sess.connect(25000)
                val c = sess.openChannel("sftp") as ChannelSftp
                c.connect(15000)
                ctx.println("Connecté à ${t.host}.")
                var localDir = ctx.shell.cwd
                while (true) {
                    val line = term.readInput("sftp> ", true)?.trim() ?: break
                    if (line.isEmpty()) continue
                    val parts = line.split(Regex("\\s+"))
                    when (parts[0]) {
                        "quit", "exit", "bye" -> break
                        "pwd" -> ctx.println("Répertoire distant : ${c.pwd()}")
                        "lpwd" -> ctx.println("Répertoire local : ${localDir.absolutePath}")
                        "cd" -> try { c.cd(parts.getOrElse(1) { "." }) } catch (e: Exception) { ctx.error("${e.message}") }
                        "lcd" -> {
                            val d = Fs.resolve(localDir, parts.getOrElse(1) { "~" })
                            if (d.isDirectory) localDir = d else ctx.error("lcd : ${d.name} : dossier introuvable")
                        }
                        "ls", "dir" -> try {
                            c.ls(parts.getOrElse(1) { "." })
                                .sortedBy { it.filename }
                                .forEach { ctx.println(it.longname) }
                        } catch (e: Exception) { ctx.error("${e.message}") }
                        "lls" -> localDir.listFiles()?.sortedBy { it.name }?.forEach { ctx.println(it.name) }
                        "get" -> {
                            val r = parts.getOrNull(1)
                            if (r == null) ctx.error("get : fichier manquant") else {
                                val lf = File(localDir, parts.getOrElse(2) { r.substringAfterLast('/') })
                                try { c.get(r, lf.absolutePath); ctx.println("Récupéré $r -> ${lf.name}") }
                                catch (e: Exception) { ctx.error("${e.message}") }
                            }
                        }
                        "put" -> {
                            val l = parts.getOrNull(1)
                            if (l == null) ctx.error("put : fichier manquant") else {
                                val lf = Fs.resolve(localDir, l)
                                try { c.put(lf.absolutePath, parts.getOrElse(2) { lf.name }); ctx.println("Envoyé ${lf.name}") }
                                catch (e: Exception) { ctx.error("${e.message}") }
                            }
                        }
                        "rm" -> try { c.rm(parts.getOrElse(1) { "" }) } catch (e: Exception) { ctx.error("${e.message}") }
                        "mkdir" -> try { c.mkdir(parts.getOrElse(1) { "" }) } catch (e: Exception) { ctx.error("${e.message}") }
                        "help", "?" -> ctx.println("commandes : ls, lls, cd, lcd, pwd, lpwd, get, put, rm, mkdir, quit")
                        else -> ctx.error("commande inconnue : ${parts[0]} (essayez « help »)")
                    }
                }
                c.exit()
                0
            } catch (e: Exception) {
                ctx.error("sftp : ${e.message}")
                1
            } finally {
                try { sess?.disconnect() } catch (_: Exception) { }
            }
        }
    }

    // ------------------------------------------------------------- ssh-keygen

    private suspend fun keygen(ctx: ExecContext): Int {
        val type = (ctx.option("-t") ?: "rsa").lowercase()
        val bits = ctx.option("-b")?.toIntOrNull()
        val comment = ctx.option("-C") ?: "${ctx.shell.env["USER"]}@${Distro.HOST}"
        val kind = when (type) {
            "rsa" -> KeyPair.RSA
            "ecdsa" -> KeyPair.ECDSA
            "ed25519" -> KeyPair.ED25519
            "dsa" -> KeyPair.DSA
            else -> { ctx.error("ssh-keygen : type de clé inconnu « $type » (rsa, ecdsa, ed25519)"); return 1 }
        }
        val size = bits ?: when (kind) {
            KeyPair.RSA -> 3072
            KeyPair.ECDSA -> 256
            else -> 256
        }
        Fs.sshDir.mkdirs()
        val defaultPath = File(Fs.sshDir, "id_$type")
        val given = ctx.option("-f")
        val path = if (given != null) ctx.resolve(given) else {
            val a = ctx.ask("Indiquez le fichier où enregistrer la clé (${Fs.pretty(defaultPath)}) : ")
            if (a.isNullOrBlank()) defaultPath else ctx.resolve(a)
        }
        if (path.exists()) {
            val a = ctx.ask("${Fs.pretty(path)} existe déjà. L'écraser ? (o/n) ")
            if (a?.trim()?.lowercase()?.startsWith("o") != true && a?.trim()?.lowercase()?.startsWith("y") != true) {
                ctx.println("Abandon."); return 1
            }
        }
        val passphrase = ctx.ask("Entrez une phrase de passe (vide pour aucune) : ", echo = false) ?: ""
        if (passphrase.isNotEmpty()) {
            val again = ctx.ask("Entrez à nouveau la même phrase de passe : ", echo = false) ?: ""
            if (again != passphrase) { ctx.error("Les phrases de passe diffèrent. Abandon."); return 1 }
        }
        ctx.println("Génération d'une paire de clés $type de $size bits...")
        return withContext(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val kp = KeyPair.genKeyPair(jsch, kind, size)
                path.parentFile?.mkdirs()
                if (passphrase.isEmpty()) kp.writePrivateKey(path.absolutePath)
                else kp.writePrivateKey(path.absolutePath, passphrase.toByteArray(Charsets.UTF_8))
                kp.writePublicKey(path.absolutePath + ".pub", comment)
                path.setReadable(false, false); path.setReadable(true, true)
                val blob = kp.publicKeyBlob
                val fp = sha256Fingerprint(blob)
                kp.dispose()
                ctx.println("Votre identification a été enregistrée dans ${Fs.pretty(path)}")
                ctx.println("Votre clé publique a été enregistrée dans ${Fs.pretty(path)}.pub")
                ctx.println("L'empreinte de la clé est :")
                ctx.println("$fp $comment")
                ctx.println("L'image aléatoire de la clé est :")
                ctx.print(randomArt(blob, type.uppercase(), size))
                ctx.println()
                ctx.println("${Ansi.GREY}Copiez la clé publique sur le serveur :${Ansi.RESET}")
                ctx.println("  cat ${Fs.pretty(path)}.pub")
                0
            } catch (e: Exception) {
                ctx.error("ssh-keygen : ${e.message ?: "génération impossible"}")
                if (type == "ed25519") ctx.error("Cet appareil ne gère peut-être pas Ed25519 : essayez -t rsa.")
                1
            }
        }
    }

    private fun sha256Fingerprint(blob: ByteArray?): String {
        if (blob == null) return "SHA256:?"
        val d = MessageDigest.getInstance("SHA-256").digest(blob)
        val b64 = Base64.getEncoder().encodeToString(d).trimEnd('=')
        return "SHA256:$b64"
    }

    /** Image aléatoire « drunken bishop », comme OpenSSH. */
    private fun randomArt(blob: ByteArray?, type: String, bits: Int): String {
        val w = 17; val h = 9
        val field = Array(h) { IntArray(w) }
        var x = w / 2; var y = h / 2
        val digest = MessageDigest.getInstance("SHA-256").digest(blob ?: ByteArray(0))
        for (b in digest) {
            var v = b.toInt() and 0xFF
            for (s in 0 until 4) {
                x += if (v and 1 == 0) -1 else 1
                y += if (v and 2 == 0) -1 else 1
                x = x.coerceIn(0, w - 1); y = y.coerceIn(0, h - 1)
                field[y][x]++
                v = v shr 2
            }
        }
        val chars = " .o+=*BOX@%&#/^"
        val sb = StringBuilder()
        val head = "[$type $bits]"
        sb.append("+" + head.padStart((w + head.length) / 2, '-').padEnd(w, '-') + "+\n")
        for (r in 0 until h) {
            sb.append("|")
            for (c in 0 until w) {
                val v = field[r][c]
                sb.append(when {
                    r == h / 2 && c == w / 2 -> 'S'
                    r == y && c == x -> 'E'
                    else -> chars[v.coerceAtMost(chars.length - 1)]
                })
            }
            sb.append("|\n")
        }
        sb.append("+" + "[SHA256]".padStart((w + 8) / 2, '-').padEnd(w, '-') + "+\n")
        return sb.toString()
    }
}
