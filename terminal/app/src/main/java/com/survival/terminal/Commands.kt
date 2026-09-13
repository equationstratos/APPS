package com.survival.terminal

/** Une commande fournie par un paquet (disponible seulement s'il est installe). */
class InternalCmd(
    val name: String,
    val pkg: String,
    val summary: String,
    val usage: String,
    val exec: suspend (ExecContext) -> Int
)

object Registry {
    val commands = LinkedHashMap<String, InternalCmd>()
    private var ready = false

    fun reg(name: String, pkg: String, summary: String, usage: String, fn: suspend (ExecContext) -> Int) {
        commands[name] = InternalCmd(name, pkg, summary, usage, fn)
    }

    fun find(name: String): InternalCmd? = commands[name]

    fun commandsOf(pkg: String): List<String> =
        commands.values.filter { it.pkg == pkg }.map { it.name }

    fun initAll() {
        if (ready) return
        ready = true
        Apt.register()
        Ssh.register()
        Net.register()
        Tools.register()
    }
}
