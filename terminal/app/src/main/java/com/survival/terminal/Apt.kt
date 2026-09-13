package com.survival.terminal

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PkgFile(val path: String, val asset: String?, val content: String?, val mode: String)

class Pkg(
    val name: String,
    val version: String,
    val section: String,
    val desc: String,
    val longDesc: String,
    val depends: List<String>,
    val size: Long,
    val installedSize: Long,
    val provides: List<String>,
    val files: List<PkgFile>,
    val origin: String
)

/**
 * Gestionnaire de paquets. Le dépôt par défaut est embarqué dans l'application
 * (hors-ligne) ; on peut en ajouter d'autres en HTTP dans $PREFIX/etc/apt/sources.list,
 * ils doivent servir un index.json au même format.
 */
object Apt {

    private val BASE = listOf("base-files", "libc6", "coreutils", "mksh", "apt", "dpkg")

    private var cache: LinkedHashMap<String, Pkg>? = null
    private var db: JSONObject? = null

    // ------------------------------------------------------------ base de données

    private fun dbFile() = File(Fs.dpkgDir, "status.json")

    private fun db(): JSONObject {
        db?.let { return it }
        val f = dbFile()
        val o = try {
            if (f.isFile) JSONObject(f.readText()) else JSONObject()
        } catch (_: Exception) { JSONObject() }
        db = o
        return o
    }

    private fun saveDb() {
        try {
            Fs.dpkgDir.mkdirs()
            dbFile().writeText(db().toString(1))
        } catch (_: Exception) { }
    }

    fun isInstalled(name: String): Boolean = db().has(name)

    fun installedNames(): List<String> = db().keys().asSequence().toList().sorted()

    private fun markInstalled(p: Pkg, files: List<String>) {
        val o = JSONObject()
        o.put("version", p.version)
        o.put("section", p.section)
        o.put("desc", p.desc)
        o.put("installed_size", p.installedSize)
        o.put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        o.put("files", files.joinToString(":"))
        o.put("provides", p.provides.joinToString(" "))
        db().put(p.name, o)
        saveDb()
    }

    /** Paquets de base marqués installés au premier démarrage. */
    fun ensureBase() {
        var changed = false
        for (n in BASE) {
            if (!db().has(n)) {
                val p = available()[n] ?: continue
                val o = JSONObject()
                o.put("version", p.version)
                o.put("section", p.section)
                o.put("desc", p.desc)
                o.put("installed_size", p.installedSize)
                o.put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                o.put("files", "")
                o.put("provides", p.provides.joinToString(" "))
                db().put(n, o)
                changed = true
            }
        }
        if (changed) saveDb()
    }

    // ------------------------------------------------------------------ dépôts

    fun sources(): List<String> {
        val f = File(Fs.aptEtc, "sources.list")
        if (!f.isFile) return listOf("asset://repo")
        return f.readLines()
            .map { it.trim() }
            .filter { it.startsWith("deb ") }
            .mapNotNull { it.split(Regex("\\s+")).getOrNull(1) }
    }

    fun available(): Map<String, Pkg> {
        cache?.let { return it }
        val map = LinkedHashMap<String, Pkg>()
        // dépôt embarqué : toujours présent
        try {
            val txt = Fs.assets.open("repo/index.json").bufferedReader().use { it.readText() }
            parseIndex(txt, "asset://repo").forEach { map[it.name] = it }
        } catch (_: Exception) { }
        // listes téléchargées par « apt update »
        Fs.aptLists.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { f ->
            try {
                parseIndex(f.readText(), f.nameWithoutExtension).forEach { map[it.name] = it }
            } catch (_: Exception) { }
        }
        cache = map
        return map
    }

    fun invalidate() { cache = null }

    private fun parseIndex(text: String, origin: String): List<Pkg> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("packages") ?: return emptyList()
        val out = ArrayList<Pkg>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val deps = ArrayList<String>()
            o.optJSONArray("depends")?.let { for (j in 0 until it.length()) deps.add(it.getString(j)) }
            val prov = ArrayList<String>()
            o.optJSONArray("provides")?.let { for (j in 0 until it.length()) prov.add(it.getString(j)) }
            val files = ArrayList<PkgFile>()
            o.optJSONArray("files")?.let {
                for (j in 0 until it.length()) {
                    val fo = it.getJSONObject(j)
                    files.add(PkgFile(
                        fo.getString("path"),
                        if (fo.has("asset")) fo.getString("asset") else null,
                        if (fo.has("content")) fo.getString("content") else null,
                        fo.optString("mode", "644")
                    ))
                }
            }
            out.add(Pkg(
                o.getString("name"),
                o.optString("version", "1.0"),
                o.optString("section", "utils"),
                o.optString("desc", ""),
                o.optString("long", o.optString("desc", "")),
                deps, o.optLong("size", 0L), o.optLong("installed_size", 0L),
                prov, files, origin
            ))
        }
        return out
    }

    fun packageProviding(cmd: String): String? =
        available().values.firstOrNull { it.provides.contains(cmd) && !isInstalled(it.name) }?.name

    // ----------------------------------------------------------------- commandes

    fun register() {
        Registry.reg("apt", "apt", "gestionnaire de paquets",
            "apt [update|list|search|show|install|remove|upgrade] ...") { apt(it) }
        Registry.reg("apt-get", "apt", "gestionnaire de paquets (interface historique)",
            "apt-get [update|install|remove|upgrade] ...") { apt(it) }
        Registry.reg("pkg", "apt", "raccourci du gestionnaire de paquets",
            "pkg [install|uninstall|search|list-all] ...") { apt(it) }
        Registry.reg("dpkg", "dpkg", "outil bas niveau de gestion des paquets",
            "dpkg [-l|-L paquet|-s paquet]") { dpkg(it) }
    }

    private suspend fun apt(ctx: ExecContext): Int {
        val ops = ctx.operands()
        val sub = ops.firstOrNull()
        val rest = ops.drop(1)
        val yes = ctx.flag("-y", "--yes") || ctx.args.contains("--assume-yes")
        return when (sub) {
            null -> { usage(ctx); 1 }
            "update" -> update(ctx)
            "upgrade", "full-upgrade", "dist-upgrade" -> upgrade(ctx)
            "install", "add" -> install(ctx, rest, yes)
            "remove", "uninstall", "purge", "autoremove" -> remove(ctx, rest, sub == "purge")
            "list", "list-all" -> list(ctx)
            "search" -> search(ctx, rest.joinToString(" "))
            "show", "info" -> show(ctx, rest)
            "clean", "autoclean" -> { ctx.println("Suppression du cache de téléchargement... Fait"); 0 }
            "--version", "-v", "version" -> { ctx.println("apt 2.7.14 (${Distro.NAME})"); 0 }
            "help" -> { usage(ctx); 0 }
            else -> { ctx.error("E: commande « $sub » inconnue"); usage(ctx); 1 }
        }
    }

    private fun usage(ctx: ExecContext) {
        ctx.println("apt 2.7.14 — gestionnaire de paquets de ${Distro.NAME}")
        ctx.println("Utilisation : apt <commande>")
        ctx.println()
        ctx.println("Commandes :")
        ctx.println("  update     met à jour la liste des paquets disponibles")
        ctx.println("  list       liste les paquets")
        ctx.println("  search     recherche dans les descriptions")
        ctx.println("  show       affiche le détail d'un paquet")
        ctx.println("  install    installe des paquets")
        ctx.println("  remove     désinstalle des paquets")
        ctx.println("  upgrade    met à jour les paquets installés")
    }

    private suspend fun update(ctx: ExecContext): Int {
        var n = 0
        for (src in sources()) {
            n++
            if (src.startsWith("asset://")) {
                ctx.println("Atteint :$n $src ${Distro.CODENAME} InRelease")
            } else {
                ctx.println("Réception de :$n $src ${Distro.CODENAME} InRelease")
                val url = src.trimEnd('/') + "/index.json"
                val body = Http.get(url, 15000)
                if (body == null) {
                    ctx.err.write("${Ansi.BYELLOW}W: Impossible de récupérer $url${Ansi.RESET}\n")
                } else {
                    try {
                        parseIndex(body, "remote") // validation
                        Fs.aptLists.mkdirs()
                        val safe = src.replace(Regex("[^A-Za-z0-9]"), "_")
                        File(Fs.aptLists, "$safe.json").writeText(body)
                        ctx.println("Réception de :$n $src ${Distro.CODENAME}/main Packages [${fmtSize(body.length.toLong())}]")
                    } catch (e: Exception) {
                        ctx.err.write("${Ansi.BYELLOW}W: index invalide sur $src : ${e.message}${Ansi.RESET}\n")
                    }
                }
            }
            delay(80)
        }
        invalidate()
        ctx.println("Lecture des listes de paquets... Fait")
        ctx.println("Construction de l'arbre des dépendances... Fait")
        ctx.println("Lecture des informations d'état... Fait")
        val up = upgradable()
        if (up.isEmpty()) ctx.println("Tous les paquets sont à jour.")
        else ctx.println("${up.size} paquet(s) peuvent être mis à jour. Lancez « apt list --upgradable » pour les voir.")
        return 0
    }

    private fun upgradable(): List<Pkg> = available().values.filter { p ->
        val cur = db().optJSONObject(p.name)?.optString("version")
        cur != null && cur != p.version
    }

    private suspend fun upgrade(ctx: ExecContext): Int {
        ctx.println("Lecture des listes de paquets... Fait")
        ctx.println("Construction de l'arbre des dépendances... Fait")
        ctx.println("Lecture des informations d'état... Fait")
        val up = upgradable()
        if (up.isEmpty()) {
            ctx.println("Calcul de la mise à jour... Fait")
            ctx.println("0 mis à jour, 0 nouvellement installés, 0 à enlever et 0 non mis à jour.")
            return 0
        }
        return install(ctx, up.map { it.name }, true)
    }

    /** Résolution récursive des dépendances. */
    private fun resolve(names: List<String>): Pair<List<Pkg>, String?> {
        val avail = available()
        val out = LinkedHashMap<String, Pkg>()
        val seen = HashSet<String>()
        fun add(n: String): String? {
            if (n in seen) return null
            seen.add(n)
            val p = avail[n] ?: return n
            for (d in p.depends) {
                if (isInstalled(d)) continue
                val miss = add(d)
                if (miss != null) return miss
            }
            if (!out.containsKey(n)) out[n] = p
            return null
        }
        for (n in names) {
            val miss = add(n)
            if (miss != null) return Pair(emptyList(), miss)
        }
        return Pair(out.values.toList(), null)
    }

    private suspend fun install(ctx: ExecContext, names: List<String>, yes: Boolean): Int {
        if (names.isEmpty()) { ctx.error("E: Aucun paquet indiqué. Essayez « apt install <paquet> »."); return 1 }
        ctx.println("Lecture des listes de paquets... Fait")
        delay(60)
        ctx.println("Construction de l'arbre des dépendances... Fait")
        delay(60)
        ctx.println("Lecture des informations d'état... Fait")

        val (plan, missing) = resolve(names)
        if (missing != null) {
            ctx.err.write("${Ansi.BRED}E: Impossible de trouver le paquet $missing${Ansi.RESET}\n")
            val near = available().keys.filter { it.contains(missing.take(3)) }.take(3)
            if (near.isNotEmpty()) ctx.err.write("   Vouliez-vous dire : ${near.joinToString(", ")} ?\n")
            return 100
        }
        val todo = plan.filter { !isInstalled(it.name) }
        val already = names.filter { isInstalled(it) }
        already.forEach { ctx.println("$it est déjà la version la plus récente (${db().getJSONObject(it).optString("version")}).") }
        if (todo.isEmpty()) {
            ctx.println("0 mis à jour, 0 nouvellement installés, 0 à enlever et 0 non mis à jour.")
            return 0
        }
        val extra = todo.filter { !names.contains(it.name) }
        if (extra.isNotEmpty()) {
            ctx.println("Les paquets supplémentaires suivants seront installés :")
            ctx.println("  " + extra.joinToString(" ") { it.name })
        }
        ctx.println("Les NOUVEAUX paquets suivants seront installés :")
        ctx.println("  " + todo.sortedBy { it.name }.joinToString(" ") { it.name })
        val dl = todo.sumOf { it.size }
        val disk = todo.sumOf { it.installedSize }
        ctx.println("0 mis à jour, ${todo.size} nouvellement installés, 0 à enlever et 0 non mis à jour.")
        ctx.println("Il est nécessaire de prendre ${fmtSize(dl)} dans les archives.")
        ctx.println("Après cette opération, ${fmtSize(disk)} d'espace disque supplémentaires seront utilisés.")

        if (extra.isNotEmpty() && !yes) {
            val answer = ctx.ask("Souhaitez-vous continuer ? [O/n] ")
            if (answer != null && answer.isNotBlank() &&
                !answer.trim().lowercase().startsWith("o") && !answer.trim().lowercase().startsWith("y")) {
                ctx.println("Abandon.")
                return 1
            }
        }

        var i = 0
        for (p in todo) {
            i++
            ctx.println("Réception de :$i ${p.origin} ${Distro.CODENAME}/main all ${p.name} ${p.version} [${fmtSize(p.size)}]")
            delay(90)
        }
        ctx.println("${fmtSize(dl)} réceptionnés en 1s (${fmtSize(dl)}/s)")
        for (p in todo) {
            ctx.println("Sélection du paquet ${Ansi.BOLD}${p.name}${Ansi.RESET} précédemment désélectionné.")
            ctx.println("(Lecture de la base de données... ${db().length() * 137 + 4211} fichiers et répertoires déjà installés.)")
            ctx.println("Préparation du dépaquetage de .../${p.name}_${p.version}_all.deb ...")
            ctx.println("Dépaquetage de ${p.name} (${p.version}) ...")
            delay(80)
            val written = unpack(p, ctx)
            markInstalled(p, written)
        }
        for (p in todo) {
            ctx.println("Paramétrage de ${p.name} (${p.version}) ...")
            delay(60)
        }
        ctx.println("Traitement des actions différées (« triggers ») pour man-db (2.12.0-4) ...")
        val newCmds = todo.flatMap { it.provides }
        if (newCmds.isNotEmpty()) {
            ctx.println()
            ctx.println("${Ansi.BGREEN}Nouvelles commandes disponibles :${Ansi.RESET} ${newCmds.joinToString(", ")}")
        }
        return 0
    }

    /** Écrit les fichiers d'un paquet dans $PREFIX. Retourne les chemins installés. */
    private fun unpack(p: Pkg, ctx: ExecContext): List<String> {
        val written = ArrayList<String>()
        for (f in p.files) {
            try {
                val target = File(Fs.prefix, f.path)
                target.parentFile?.mkdirs()
                val data = when {
                    f.content != null -> f.content
                    f.asset != null -> Fs.assets.open(f.asset).bufferedReader().use { it.readText() }
                    else -> ""
                }
                target.writeText(data)
                if (f.mode.endsWith("5") || f.mode.endsWith("7")) target.setExecutable(true, false)
                written.add(f.path)
            } catch (e: Exception) {
                ctx.err.write("dpkg: avertissement : ${f.path} : ${e.message}\n")
            }
        }
        // documentation minimale, comme un vrai paquet Debian
        try {
            val doc = File(Fs.docDir, p.name)
            doc.mkdirs()
            File(doc, "copyright").writeText(
                "Paquet: ${p.name}\nVersion: ${p.version}\nOrigine: ${p.origin}\n\n${p.longDesc}\n")
        } catch (_: Exception) { }
        return written
    }

    private suspend fun remove(ctx: ExecContext, names: List<String>, purge: Boolean): Int {
        if (names.isEmpty()) {
            ctx.println("Lecture des listes de paquets... Fait")
            ctx.println("0 mis à jour, 0 nouvellement installés, 0 à enlever et 0 non mis à jour.")
            return 0
        }
        ctx.println("Lecture des listes de paquets... Fait")
        ctx.println("Construction de l'arbre des dépendances... Fait")
        var status = 0
        val todo = ArrayList<String>()
        for (n in names) {
            when {
                BASE.contains(n) -> {
                    ctx.err.write("${Ansi.BRED}E: $n est essentiel et ne peut pas être supprimé.${Ansi.RESET}\n")
                    status = 1
                }
                !isInstalled(n) -> { ctx.println("Le paquet « $n » n'est pas installé, aucune suppression."); }
                else -> todo.add(n)
            }
        }
        if (todo.isEmpty()) return status
        ctx.println("Les paquets suivants seront ENLEVÉS :")
        ctx.println("  " + todo.joinToString(" "))
        ctx.println("0 mis à jour, 0 nouvellement installés, ${todo.size} à enlever et 0 non mis à jour.")
        for (n in todo) {
            val entry = db().optJSONObject(n)
            ctx.println("(Lecture de la base de données... ${db().length() * 137 + 4211} fichiers et répertoires déjà installés.)")
            ctx.println(if (purge) "Purge des fichiers de configuration de $n ..." else "Suppression de $n (${entry?.optString("version")}) ...")
            entry?.optString("files")?.split(":")?.filter { it.isNotBlank() }?.forEach {
                try { File(Fs.prefix, it).delete() } catch (_: Exception) { }
            }
            if (purge) try { File(Fs.docDir, n).deleteRecursively() } catch (_: Exception) { }
            db().remove(n)
            delay(70)
        }
        saveDb()
        return status
    }

    private fun list(ctx: ExecContext): Int {
        val onlyInstalled = ctx.args.contains("--installed")
        val onlyUpgradable = ctx.args.contains("--upgradable")
        ctx.println("En train de lister...")
        val all = available().values.sortedBy { it.name }
        for (p in all) {
            val inst = isInstalled(p.name)
            if (onlyInstalled && !inst) continue
            if (onlyUpgradable && !upgradable().contains(p)) continue
            val tag = if (inst) "${Ansi.BGREEN}[installé]${Ansi.RESET}" else ""
            ctx.println("${Ansi.BOLD}${p.name}${Ansi.RESET}/${Distro.CODENAME} ${p.version} all $tag")
            ctx.println("  ${Ansi.GREY}${p.desc}${Ansi.RESET}")
        }
        return 0
    }

    private fun search(ctx: ExecContext, query: String): Int {
        if (query.isBlank()) { ctx.error("E: Un motif de recherche est nécessaire."); return 1 }
        val q = query.lowercase()
        val hits = available().values.filter {
            it.name.lowercase().contains(q) || it.desc.lowercase().contains(q) ||
                it.provides.any { c -> c.lowercase().contains(q) }
        }.sortedBy { it.name }
        ctx.println("En train de trier...")
        ctx.println("Recherche en texte intégral...")
        if (hits.isEmpty()) { ctx.println("Aucun résultat pour « $query »."); return 1 }
        for (p in hits) {
            val tag = if (isInstalled(p.name)) " ${Ansi.BGREEN}[installé]${Ansi.RESET}" else ""
            ctx.println("${Ansi.BOLD}${Ansi.BGREEN}${p.name}${Ansi.RESET}/${Distro.CODENAME} ${p.version} all$tag")
            ctx.println("  ${p.desc}")
            ctx.println()
        }
        return 0
    }

    private fun show(ctx: ExecContext, names: List<String>): Int {
        if (names.isEmpty()) { ctx.error("E: Aucun paquet indiqué."); return 1 }
        var status = 0
        for (n in names) {
            val p = available()[n]
            if (p == null) { ctx.err.write("E: Impossible de trouver le paquet $n\n"); status = 1; continue }
            ctx.println("Package: ${p.name}")
            ctx.println("Version: ${p.version}")
            ctx.println("Priority: optional")
            ctx.println("Section: ${p.section}")
            ctx.println("Maintainer: ${Distro.NAME} <root@${Distro.HOST}>")
            ctx.println("Installed-Size: ${fmtSize(p.installedSize)}")
            if (p.depends.isNotEmpty()) ctx.println("Depends: ${p.depends.joinToString(", ")}")
            if (p.provides.isNotEmpty()) ctx.println("Commandes: ${p.provides.joinToString(", ")}")
            ctx.println("Download-Size: ${fmtSize(p.size)}")
            ctx.println("APT-Sources: ${p.origin} ${Distro.CODENAME}/main all Packages")
            ctx.println("État: " + if (isInstalled(p.name)) "installé" else "non installé")
            ctx.println("Description: ${p.desc}")
            p.longDesc.split("\n").forEach { ctx.println(" $it") }
            ctx.println()
        }
        return status
    }

    private fun dpkg(ctx: ExecContext): Int {
        val args = ctx.args
        return when {
            args.isEmpty() || args.contains("-l") || args.contains("--list") -> {
                ctx.println("Souhait=inconnU/Installé/suppRimé/Purgé/H=à garder")
                ctx.println("| État=Non/Installé/fichier-Config/dépaqUeté/échec-conFig/H=semi-installé")
                ctx.println("|/ Err?=(aucune)/besoin Réinstallation (État,Err: majuscule=mauvais)")
                ctx.println("||/ ${"Nom".padEnd(22)} ${"Version".padEnd(14)} ${"Arch".padEnd(8)} Description")
                ctx.println("+++-" + "-".repeat(22) + "-" + "-".repeat(14) + "-" + "-".repeat(8) + "-" + "-".repeat(30))
                for (n in installedNames()) {
                    val o = db().getJSONObject(n)
                    ctx.println("ii  ${n.padEnd(22)} ${o.optString("version").padEnd(14)} ${"all".padEnd(8)} ${o.optString("desc")}")
                }
                0
            }
            args.contains("-L") -> {
                val n = ctx.operands().firstOrNull() ?: return 1
                val o = db().optJSONObject(n) ?: run { ctx.error("dpkg : $n n'est pas installé"); return 1 }
                ctx.println("/.")
                ctx.println("/usr")
                ctx.println("/usr/bin")
                val files = o.optString("files").split(":").filter { it.isNotBlank() }
                val shown = HashSet<String>()
                files.forEach { if (shown.add(it)) ctx.println("${Fs.prefix}/$it") }
                o.optString("provides").split(" ").filter { it.isNotBlank() }
                    .forEach { if (shown.add("bin/$it")) ctx.println("${Fs.prefix}/bin/$it") }
                ctx.println("${Fs.prefix}/share/doc/$n")
                0
            }
            args.contains("-s") || args.contains("--status") -> {
                val n = ctx.operands().firstOrNull() ?: return 1
                val o = db().optJSONObject(n) ?: run {
                    ctx.error("dpkg-query : aucun paquet ne correspond à $n"); return 1
                }
                ctx.println("Package: $n")
                ctx.println("Status: install ok installed")
                ctx.println("Version: ${o.optString("version")}")
                ctx.println("Section: ${o.optString("section")}")
                ctx.println("Installed-Size: ${o.optLong("installed_size")}")
                ctx.println("Description: ${o.optString("desc")}")
                0
            }
            else -> { ctx.println("dpkg 1.22.6 — utilisation : dpkg [-l | -L paquet | -s paquet]"); 0 }
        }
    }

    fun fmtSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> String.format(Locale.FRANCE, "%.1f Mo", bytes / 1048576.0)
        bytes >= 1024 -> "${bytes / 1024} ko"
        else -> "$bytes o"
    }
}
