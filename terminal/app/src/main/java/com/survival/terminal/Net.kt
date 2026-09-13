package com.survival.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Petit client HTTP partagé (utilisé aussi par apt pour les dépôts distants). */
object Http {
    fun get(url: String, timeout: Int = 15000): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "${Distro.NAME}/${Distro.VERSION} apt")
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }
        c.disconnect()
        if (code in 200..299) body else null
    } catch (_: Exception) { null }
}

object Net {

    fun register() {
        Registry.reg("curl", "curl", "transfert de données HTTP(S)",
            "curl [-I] [-X METHODE] [-H 'En-tete: valeur'] [-d donnees] [-o fichier] [-s] [-L] url") { curl(it) }
        Registry.reg("wget", "wget", "téléchargement de fichier",
            "wget [-O fichier] url") { wget(it) }
        Registry.reg("ping", "iputils-ping", "test d'accessibilité réseau",
            "ping [-c nombre] hote") { ping(it) }
        Registry.reg("ifconfig", "net-tools", "configuration des interfaces réseau", "ifconfig [-a]") { ifconfig(it) }
        Registry.reg("hostname", "net-tools", "nom de la machine", "hostname [-i]") { hostname(it) }
        Registry.reg("netstat", "net-tools", "connexions réseau", "netstat [-t] [-u] [-n]") { netstat(it) }
        Registry.reg("route", "net-tools", "table de routage", "route [-n]") { route(it) }
        Registry.reg("dig", "dnsutils", "interrogation DNS", "dig nom [A|AAAA|PTR]") { dig(it) }
        Registry.reg("nslookup", "dnsutils", "résolution de nom", "nslookup nom") { dig(it) }
        Registry.reg("host", "dnsutils", "résolution de nom simplifiée", "host nom") { hostCmd(it) }
        Registry.reg("nc", "netcat-openbsd", "connexion TCP brute",
            "nc [-l] [-z] [-w secondes] hote port") { nc(it) }
        Registry.reg("netcat", "netcat-openbsd", "connexion TCP brute",
            "netcat [-l] [-z] [-w secondes] hote port") { nc(it) }
    }

    // -------------------------------------------------------------------- curl

    private suspend fun curl(ctx: ExecContext): Int = withContext(Dispatchers.IO) {
        val args = ctx.args
        if (args.isEmpty()) { ctx.println("curl : essayez « curl --help » pour plus d'informations"); return@withContext 2 }
        if (ctx.flag("--help")) {
            ctx.println("utilisation : curl [options] <url>")
            ctx.println("  -I            en-têtes seulement (HEAD)")
            ctx.println("  -X METHODE    méthode HTTP (GET, POST, PUT, DELETE...)")
            ctx.println("  -H 'A: B'     ajoute un en-tête (répétable)")
            ctx.println("  -d DONNEES    corps de requête (implique POST)")
            ctx.println("  -o FICHIER    écrit la réponse dans un fichier")
            ctx.println("  -L            suit les redirections")
            ctx.println("  -s            silencieux   -v  détaillé")
            return@withContext 0
        }
        var method: String? = null
        var data: String? = null
        var outFile: String? = null
        var url: String? = null
        val headers = ArrayList<Pair<String, String>>()
        val headOnly = ctx.flag("-I", "--head")
        val silent = ctx.flag("-s", "--silent")
        val verbose = ctx.flag("-v", "--verbose")
        val follow = ctx.flag("-L", "--location")
        var i = 0
        while (i < args.size) {
            when (val a = args[i]) {
                "-X", "--request" -> method = args.getOrNull(++i)
                "-d", "--data" -> data = args.getOrNull(++i)
                "-o", "--output" -> outFile = args.getOrNull(++i)
                "-H", "--header" -> args.getOrNull(++i)?.let {
                    val p = it.split(":", limit = 2)
                    if (p.size == 2) headers.add(Pair(p[0].trim(), p[1].trim()))
                }
                "-A", "--user-agent" -> args.getOrNull(++i)?.let { headers.add(Pair("User-Agent", it)) }
                else -> if (!a.startsWith("-")) url = a
            }
            i++
        }
        if (url == null) { ctx.error("curl : aucune URL indiquée"); return@withContext 2 }
        val full = if (url.startsWith("http")) url else "https://$url"
        try {
            val c = URL(full).openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.instanceFollowRedirects = follow
            c.requestMethod = method ?: if (headOnly) "HEAD" else if (data != null) "POST" else "GET"
            c.setRequestProperty("User-Agent", "curl/8.5.0")
            headers.forEach { c.setRequestProperty(it.first, it.second) }
            if (verbose) {
                ctx.err.write("${Ansi.GREY}> ${c.requestMethod} $full${Ansi.RESET}\n")
                headers.forEach { ctx.err.write("${Ansi.GREY}> ${it.first}: ${it.second}${Ansi.RESET}\n") }
            }
            if (data != null) {
                c.doOutput = true
                if (headers.none { it.first.equals("Content-Type", true) })
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (headOnly || verbose) {
                ctx.println("HTTP/1.1 $code ${c.responseMessage ?: ""}")
                c.headerFields.forEach { (k, v) -> if (k != null) ctx.println("$k: ${v.joinToString(", ")}") }
                if (headOnly) { c.disconnect(); return@withContext 0 }
                ctx.println()
            }
            val stream: InputStream? = if (code in 200..399) c.inputStream else c.errorStream
            if (outFile != null) {
                val f = ctx.resolve(outFile)
                val bytes = stream?.readBytes() ?: ByteArray(0)
                f.writeBytes(bytes)
                if (!silent) ctx.println("${Apt.fmtSize(bytes.size.toLong())} écrits dans ${Fs.pretty(f)}")
            } else {
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                ctx.print(if (body.endsWith("\n") || body.isEmpty()) body else body + "\n")
            }
            c.disconnect()
            if (code >= 400) 22 else 0
        } catch (e: Exception) {
            ctx.error("curl : (6) ${e.message}")
            6
        }
    }

    // -------------------------------------------------------------------- wget

    private suspend fun wget(ctx: ExecContext): Int = withContext(Dispatchers.IO) {
        val url = ctx.operands("-O", "--output-document", "-P", "-T").firstOrNull()
        if (url == null) { ctx.println("wget : URL manquante"); return@withContext 2 }
        val full = if (url.startsWith("http")) url else "https://$url"
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())
        val name = ctx.option("-O", "--output-document")
            ?: full.substringAfterLast('/').substringBefore('?').ifBlank { "index.html" }
        val target = ctx.resolve(name)
        ctx.println("--$stamp--  $full")
        try {
            val hostName = URL(full).host
            val addr = InetAddress.getByName(hostName)
            ctx.println("Résolution de $hostName... ${addr.hostAddress}")
            val defPort = if (full.startsWith("https")) 443 else 80
            ctx.println("Connexion à $hostName|${addr.hostAddress}|:$defPort... connecté.")
            val c = URL(full).openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 60000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Wget/1.21.4")
            val code = c.responseCode
            ctx.println("requête HTTP transmise, en attente de la réponse... $code ${c.responseMessage ?: ""}")
            if (code !in 200..299) { ctx.error("Erreur $code"); c.disconnect(); return@withContext 8 }
            val len = c.contentLengthLong
            ctx.println("Taille : ${if (len > 0) "$len (${Apt.fmtSize(len)})" else "non spécifié"} [${c.contentType ?: "inconnu"}]")
            ctx.println("Enregistre : « ${target.name} »")
            ctx.println()
            val start = System.currentTimeMillis()
            var total = 0L
            val buf = ByteArray(16384)
            c.inputStream.use { input ->
                target.outputStream().use { output ->
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        total += n
                        if (len > 0) {
                            val pct = (total * 100 / len).toInt()
                            if (pct != lastPct && pct % 5 == 0) {
                                lastPct = pct
                                val bars = pct * 20 / 100
                                ctx.print("\r${target.name.take(16).padEnd(16)} ${pct.toString().padStart(3)}%[" +
                                    "=".repeat(bars) + ">".padEnd(20 - bars) + "] ${Apt.fmtSize(total)}")
                            }
                        }
                    }
                }
            }
            val secs = ((System.currentTimeMillis() - start) / 1000.0).coerceAtLeast(0.001)
            ctx.print("\r${target.name.take(16).padEnd(16)} 100%[" + "=".repeat(20) + "] ${Apt.fmtSize(total)}\n")
            ctx.println()
            ctx.println("$stamp (${Apt.fmtSize((total / secs).toLong())}/s) - « ${target.name} » enregistré [$total/$total]")
            c.disconnect()
            0
        } catch (e: Exception) {
            ctx.error("wget : ${e.message}")
            4
        }
    }

    // -------------------------------------------------------------------- ping

    private suspend fun ping(ctx: ExecContext): Int {
        val host = ctx.operands("-c", "-i", "-W", "-s", "-t").firstOrNull()
        if (host == null) { ctx.println("utilisation : ping [-c nombre] hote"); return 2 }
        val count = ctx.option("-c")?.toIntOrNull() ?: 4
        // Le binaire ping du système fait du vrai ICMP : on l'utilise s'il existe.
        val sysPing = File("/system/bin/ping")
        if (sysPing.exists()) {
            val extra = ctx.args.filter { it != host }.joinToString(" ")
            val hasCount = extra.contains("-c")
            return Exec.runExternal(ctx, "ping ${if (hasCount) extra else "-c $count $extra"} ${ctx.shell.quote(host)}")
        }
        return tcpPing(ctx, host, count)
    }

    /** Repli sans ICMP : mesure du temps d'établissement d'une connexion TCP. */
    private suspend fun tcpPing(ctx: ExecContext, host: String, count: Int): Int =
        withContext(Dispatchers.IO) {
            val addr = try { InetAddress.getByName(host) } catch (e: Exception) {
                ctx.error("ping : ${host} : nom ou service inconnu"); return@withContext 2
            }
            ctx.println("PING $host (${addr.hostAddress}) — mesure TCP (ICMP indisponible)")
            var ok = 0
            val times = ArrayList<Long>()
            for (i in 1..count) {
                val ports = intArrayOf(443, 80, 22)
                var done = false
                for (p in ports) {
                    val t0 = System.nanoTime()
                    try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(addr, p), 3000)
                            val ms = (System.nanoTime() - t0) / 1e6
                            times.add(ms.toLong())
                            ctx.println("réponse de ${addr.hostAddress}:$p : seq=$i temps=%.1f ms".format(ms))
                            ok++; done = true
                        }
                    } catch (_: Exception) { }
                    if (done) break
                }
                if (!done) ctx.println("délai dépassé pour la demande seq=$i")
            }
            ctx.println()
            ctx.println("--- statistiques ping $host ---")
            val loss = (count - ok) * 100 / count
            ctx.println("$count paquets transmis, $ok reçus, $loss% de perte")
            if (times.isNotEmpty())
                ctx.println("rtt min/moy/max = ${times.min()}/${times.average().toInt()}/${times.max()} ms")
            if (ok == 0) 1 else 0
        }

    // ---------------------------------------------------------------- ifconfig

    private fun ifconfig(ctx: ExecContext): Int {
        val all = ctx.flag("-a")
        val ifaces = try { NetworkInterface.getNetworkInterfaces().toList() } catch (e: Exception) {
            ctx.error("ifconfig : ${e.message}"); return 1
        }
        for (nif in ifaces.sortedBy { it.name }) {
            val up = try { nif.isUp } catch (_: Exception) { false }
            if (!up && !all) continue
            val addrs = nif.inetAddresses.toList()
            val flags = buildList {
                if (up) add("UP")
                if (try { nif.isLoopback } catch (_: Exception) { false }) add("LOOPBACK")
                else add("BROADCAST")
                if (try { nif.supportsMulticast() } catch (_: Exception) { false }) add("MULTICAST")
                add("RUNNING")
            }
            ctx.println("${Ansi.BOLD}${nif.name}${Ansi.RESET}: flags=<${flags.joinToString(",")}>  mtu ${try { nif.mtu } catch (_: Exception) { 0 }}")
            addrs.filterIsInstance<Inet4Address>().forEach {
                ctx.println("        inet ${it.hostAddress}  netmask ${maskOf(nif, it)}")
            }
            addrs.filterIsInstance<Inet6Address>().forEach {
                ctx.println("        inet6 ${it.hostAddress?.substringBefore('%')}  prefixlen 64  scopeid ${if (it.isLinkLocalAddress) "0x20<link>" else "0x0<global>"}")
            }
            val mac = try { nif.hardwareAddress } catch (_: Exception) { null }
            if (mac != null) ctx.println("        ether ${mac.joinToString(":") { String.format("%02x", it) }}")
            ctx.println()
        }
        return 0
    }

    private fun maskOf(nif: NetworkInterface, addr: InetAddress): String = try {
        val len = nif.interfaceAddresses.firstOrNull { it.address == addr }?.networkPrefixLength?.toInt() ?: 24
        val m = -1L shl (32 - len)
        "${(m shr 24) and 255}.${(m shr 16) and 255}.${(m shr 8) and 255}.${m and 255}"
    } catch (_: Exception) { "255.255.255.0" }

    private fun hostname(ctx: ExecContext): Int {
        if (ctx.flag("-i", "-I")) {
            val ips = try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { try { it.isUp && !it.isLoopback } catch (_: Exception) { false } }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
            } catch (_: Exception) { emptyList() }
            ctx.println(ips.joinToString(" "))
        } else ctx.println(Distro.HOST)
        return 0
    }

    // ----------------------------------------------------------------- netstat

    private fun netstat(ctx: ExecContext): Int {
        ctx.println("Connexions Internet actives (serveurs et établies)")
        ctx.println("Proto ${"Adresse locale".padEnd(30)} ${"Adresse distante".padEnd(30)} État")
        var n = 0
        for ((proto, path) in listOf(
            "tcp" to "/proc/net/tcp", "tcp6" to "/proc/net/tcp6",
            "udp" to "/proc/net/udp", "udp6" to "/proc/net/udp6"
        )) {
            if (proto.startsWith("udp") && !ctx.flag("-u", "-a")) continue
            if (proto.startsWith("tcp") && ctx.flag("-u") && !ctx.flag("-t", "-a")) continue
            val f = File(path)
            if (!f.canRead()) continue
            try {
                f.readLines().drop(1).forEach { line ->
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size > 3) {
                        val st = tcpState(p[3])
                        ctx.println("${proto.padEnd(5)} ${hexAddr(p[1]).padEnd(30)} ${hexAddr(p[2]).padEnd(30)} $st")
                        n++
                    }
                }
            } catch (_: Exception) { }
        }
        if (n == 0) ctx.println("${Ansi.GREY}(Android restreint la lecture de /proc/net aux applications non privilégiées)${Ansi.RESET}")
        return 0
    }

    private fun hexAddr(s: String): String = try {
        val (a, p) = s.split(":")
        val port = p.toInt(16)
        val ip = if (a.length <= 8) {
            val v = a.chunked(2).reversed().map { it.toInt(16) }
            v.joinToString(".")
        } else "[" + a.chunked(4).joinToString(":") + "]"
        "$ip:$port"
    } catch (_: Exception) { s }

    private fun tcpState(code: String): String = when (code.uppercase()) {
        "01" -> "ESTABLISHED"
        "02" -> "SYN_SENT"
        "03" -> "SYN_RECV"
        "04" -> "FIN_WAIT1"
        "05" -> "FIN_WAIT2"
        "06" -> "TIME_WAIT"
        "07" -> "CLOSE"
        "08" -> "CLOSE_WAIT"
        "09" -> "LAST_ACK"
        "0A" -> "LISTEN"
        "0B" -> "CLOSING"
        else -> code
    }

    private fun route(ctx: ExecContext): Int {
        ctx.println("Table de routage IP du noyau")
        ctx.println("${"Destination".padEnd(18)}${"Passerelle".padEnd(18)}${"Genmask".padEnd(18)}Indic Iface")
        val f = File("/proc/net/route")
        if (f.canRead()) {
            try {
                f.readLines().drop(1).forEach { l ->
                    val p = l.split(Regex("\\s+"))
                    if (p.size > 7) {
                        ctx.println("${leHex(p[1]).padEnd(18)}${leHex(p[2]).padEnd(18)}${leHex(p[7]).padEnd(18)}UG    ${p[0]}")
                    }
                }
                return 0
            } catch (_: Exception) { }
        }
        ctx.println("${Ansi.GREY}(table non lisible sans privilèges — voir « ifconfig »)${Ansi.RESET}")
        return 0
    }

    private fun leHex(s: String): String = try {
        val v = s.toLong(16)
        "${v and 255}.${(v shr 8) and 255}.${(v shr 16) and 255}.${(v shr 24) and 255}"
    } catch (_: Exception) { s }

    // ------------------------------------------------------------------- DNS

    private suspend fun dig(ctx: ExecContext): Int = withContext(Dispatchers.IO) {
        val name = ctx.operands().firstOrNull()
        if (name == null) { ctx.println("utilisation : dig nom"); return@withContext 2 }
        val t0 = System.currentTimeMillis()
        try {
            val addrs = InetAddress.getAllByName(name)
            ctx.println("; <<>> dig ${Distro.NAME} <<>> $name")
            ctx.println(";; QUESTION SECTION:")
            ctx.println(";$name.\t\t\tIN\tA")
            ctx.println()
            ctx.println(";; ANSWER SECTION:")
            addrs.forEach {
                val type = if (it is Inet6Address) "AAAA" else "A"
                ctx.println("$name.\t\t300\tIN\t$type\t${it.hostAddress}")
            }
            ctx.println()
            ctx.println(";; Query time: ${System.currentTimeMillis() - t0} msec")
            ctx.println(";; WHEN: ${Fs.now()}")
            0
        } catch (e: Exception) {
            ctx.error(";; communications error: ${name} : hôte introuvable")
            9
        }
    }

    private suspend fun hostCmd(ctx: ExecContext): Int = withContext(Dispatchers.IO) {
        val name = ctx.operands().firstOrNull()
        if (name == null) { ctx.println("utilisation : host nom"); return@withContext 2 }
        try {
            InetAddress.getAllByName(name).forEach {
                if (it is Inet6Address) ctx.println("$name has IPv6 address ${it.hostAddress}")
                else ctx.println("$name has address ${it.hostAddress}")
            }
            0
        } catch (_: Exception) {
            ctx.error("Host $name not found: 3(NXDOMAIN)")
            1
        }
    }

    // --------------------------------------------------------------------- nc

    private suspend fun nc(ctx: ExecContext): Int = coroutineScope {
        val ops = ctx.operands("-w", "-p", "-s", "-q")
        val listen = ctx.flag("-l")
        val scan = ctx.flag("-z")
        val wait = (ctx.option("-w")?.toIntOrNull() ?: 5) * 1000
        if (listen) {
            val port = ops.lastOrNull()?.toIntOrNull()
            if (port == null) { ctx.error("nc : port d'écoute manquant"); return@coroutineScope 2 }
            return@coroutineScope withContext(Dispatchers.IO) {
                try {
                    ServerSocket(port).use { server ->
                        ctx.println("En écoute sur 0.0.0.0:$port ... (Ctrl+C pour arrêter)")
                        server.soTimeout = 1000
                        var sock: Socket? = null
                        while (isActive && sock == null) {
                            sock = try { server.accept() } catch (_: Exception) { null }
                        }
                        if (sock == null) return@withContext 1
                        ctx.println("Connexion depuis ${sock.inetAddress.hostAddress}")
                        val reader = sock.getInputStream().bufferedReader()
                        while (isActive) {
                            val l = reader.readLine() ?: break
                            ctx.println(l)
                        }
                        sock.close()
                    }
                    0
                } catch (e: Exception) { ctx.error("nc : ${e.message}"); 1 }
            }
        }
        val host = ops.getOrNull(0)
        val port = ops.getOrNull(1)?.toIntOrNull()
        if (host == null || port == null) { ctx.println("utilisation : nc [-l] [-z] [-w s] hote port"); return@coroutineScope 2 }
        withContext(Dispatchers.IO) {
            try {
                val t0 = System.currentTimeMillis()
                val s = Socket()
                s.connect(InetSocketAddress(host, port), wait)
                val ms = System.currentTimeMillis() - t0
                if (scan) {
                    ctx.println("Connexion à $host port $port [tcp/${serviceName(port)}] réussie en $ms ms !")
                    s.close()
                    return@withContext 0
                }
                ctx.println("${Ansi.GREY}Connecté à $host:$port${Ansi.RESET}")
                val reader = s.getInputStream().bufferedReader()
                val writer = s.getOutputStream()
                val readJob = launch(Dispatchers.IO) {
                    try {
                        while (isActive) {
                            val l = reader.readLine() ?: break
                            ctx.println(l)
                        }
                    } catch (_: Exception) { }
                }
                while (isActive && !s.isClosed) {
                    val line = ctx.session?.readRaw() ?: ctx.stdin.readLine() ?: break
                    writer.write((line + "\r\n").toByteArray()); writer.flush()
                }
                s.close()
                readJob.cancel()
                0
            } catch (e: Exception) {
                if (scan) ctx.println("nc : connexion à $host port $port (tcp) échouée : ${e.message}")
                else ctx.error("nc : ${e.message}")
                1
            }
        }
    }

    private fun serviceName(port: Int): String = when (port) {
        22 -> "ssh"
        80 -> "http"
        443 -> "https"
        21 -> "ftp"
        25 -> "smtp"
        53 -> "domain"
        3306 -> "mysql"
        5432 -> "postgresql"
        8080 -> "http-alt"
        else -> "*"
    }
}
