package com.survival.terminal

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Sequences d'echappement usuelles, pour colorer nos propres sorties. */
object Ansi {
    const val ESC_CODE = 27
    val ESC: String = ESC_CODE.toChar().toString()
    val RESET = ESC + "[0m"
    val BOLD = ESC + "[1m"
    val DIM = ESC + "[2m"
    val UNDER = ESC + "[4m"
    val RED = ESC + "[31m"
    val GREEN = ESC + "[32m"
    val YELLOW = ESC + "[33m"
    val BLUE = ESC + "[34m"
    val MAGENTA = ESC + "[35m"
    val CYAN = ESC + "[36m"
    val WHITE = ESC + "[37m"
    val GREY = ESC + "[90m"
    val BRED = ESC + "[91m"
    val BGREEN = ESC + "[92m"
    val BYELLOW = ESC + "[93m"
    val BBLUE = ESC + "[94m"
    val BCYAN = ESC + "[96m"
    val ORANGE = ESC + "[38;5;208m"
    val CLEAR = ESC + "[2J"

    fun strip(s: String): String = s.replace(Regex("\\x1B\\[[0-9;?]*[a-zA-Z]"), "")

    /** Palette Tango, celle des terminaux Ubuntu. */
    val palette = arrayOf(
        Color(0xFF2E3436), Color(0xFFCC0000), Color(0xFF4E9A06), Color(0xFFC4A000),
        Color(0xFF3465A4), Color(0xFF75507B), Color(0xFF06989A), Color(0xFFD3D7CF),
        Color(0xFF555753), Color(0xFFEF2929), Color(0xFF8AE234), Color(0xFFFCE94F),
        Color(0xFF729FCF), Color(0xFFAD7FA8), Color(0xFF34E2E2), Color(0xFFEEEEEC)
    )

    val defaultFg = Color(0xFFDDDDDD)
    val background = Color(0xFF1C0016)
    val accent = Color(0xFFE95420)

    /** Couleur xterm 256 : 16 fixes, cube 6x6x6, puis 24 gris. */
    fun color256(n: Int): Color = when {
        n < 0 -> defaultFg
        n < 16 -> palette[n]
        n < 232 -> {
            val i = n - 16
            val lv = intArrayOf(0, 95, 135, 175, 215, 255)
            Color(lv[i / 36], lv[(i / 6) % 6], lv[i % 6])
        }
        n < 256 -> { val g = 8 + (n - 232) * 10; Color(g, g, g) }
        else -> defaultFg
    }
}

/**
 * Tampon d'ecran : transforme un flux de texte (avec sequences ANSI) en lignes
 * stylees pour Compose. L'analyse est incrementale : une sequence peut etre
 * coupee entre deux ecritures.
 */
class TerminalBuffer(private val maxLines: Int = 3000) {
    val lines = mutableStateListOf<AnnotatedString>()
    var current by mutableStateOf(AnnotatedString(""))
        private set
    var version by mutableStateOf(0)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val pending = StringBuilder()
    private var scheduled = false

    // etat de l'analyseur
    private val segs = ArrayList<Pair<String, SpanStyle>>()
    private val cur = StringBuilder()
    private var style = SpanStyle(color = Ansi.defaultFg)
    private var bold = false
    private var mode = 0 // 0 texte, 1 apres ESC, 2 CSI, 3 OSC, 4 charset
    private val escBuf = StringBuilder()
    private var pendingCr = false

    /** Appelable depuis n'importe quel thread : le rendu est regroupe sur le thread UI. */
    fun write(s: String) {
        if (s.isEmpty()) return
        synchronized(pending) {
            pending.append(s)
            if (!scheduled) {
                scheduled = true
                handler.post { flush() }
            }
        }
    }

    fun writeLine(s: String) = write(s + "\n")

    private fun flush() {
        val chunk: String
        synchronized(pending) {
            chunk = pending.toString()
            pending.setLength(0)
            scheduled = false
        }
        if (chunk.isEmpty()) return
        for (ch in chunk) feed(ch)
        current = build()
        version++
    }

    fun clear() {
        val doIt = {
            lines.clear(); segs.clear(); cur.setLength(0)
            pendingCr = false
            current = AnnotatedString(""); version++
        }
        if (Looper.myLooper() == Looper.getMainLooper()) doIt() else handler.post { doIt() }
    }

    private fun feed(ch: Char) {
        val code = ch.code
        // Un pseudo-terminal (SSH) termine ses lignes par CR+LF : le CR ne doit
        // effacer la ligne que s'il n'est pas suivi d'un saut de ligne.
        if (pendingCr) {
            pendingCr = false
            if (ch == '\n') { newline(); return }
            cur.setLength(0); segs.clear()
        }
        when (mode) {
            1 -> when (ch) {
                '[' -> { mode = 2; escBuf.setLength(0) }
                ']' -> { mode = 3; escBuf.setLength(0) }
                '(', ')' -> mode = 4
                else -> mode = 0
            }
            2 -> if (ch in '@'..'~') { csi(escBuf.toString(), ch); mode = 0 } else escBuf.append(ch)
            3 -> if (code == 7 || code == Ansi.ESC_CODE) mode = 0
            4 -> mode = 0
            else -> when {
                code == Ansi.ESC_CODE -> mode = 1
                ch == '\n' -> newline()
                ch == '\r' -> pendingCr = true
                ch == '\t' -> cur.append(" ".repeat(8 - (lineLength() % 8)))
                code == 8 -> if (cur.isNotEmpty()) cur.setLength(cur.length - 1)
                code >= 32 -> cur.append(ch)
                else -> {} // autres caracteres de controle ignores
            }
        }
    }

    private fun lineLength(): Int {
        var n = cur.length
        for (s in segs) n += s.first.length
        return n
    }

    private fun newline() {
        flushSeg()
        lines.add(buildFrom(segs))
        segs.clear()
        while (lines.size > maxLines) lines.removeAt(0)
    }

    private fun flushSeg() {
        if (cur.isNotEmpty()) {
            segs.add(Pair(cur.toString(), style))
            cur.setLength(0)
        }
    }

    private fun build(): AnnotatedString {
        val b = AnnotatedString.Builder()
        for ((t, s) in segs) { b.pushStyle(s); b.append(t); b.pop() }
        if (cur.isNotEmpty()) { b.pushStyle(style); b.append(cur.toString()); b.pop() }
        return b.toAnnotatedString()
    }

    private fun buildFrom(list: List<Pair<String, SpanStyle>>): AnnotatedString {
        val b = AnnotatedString.Builder()
        for ((t, s) in list) { b.pushStyle(s); b.append(t); b.pop() }
        return b.toAnnotatedString()
    }

    private fun csi(params: String, final: Char) {
        when (final) {
            'm' -> sgr(params)
            'J' -> if (params == "2" || params == "3") { lines.clear(); segs.clear(); cur.setLength(0) }
            'K' -> if (params == "2" || params == "1") { segs.clear(); cur.setLength(0) }
            else -> {} // deplacements de curseur ignores (terminal en mode flux)
        }
    }

    private fun sgr(params: String) {
        flushSeg()
        val codes = if (params.isEmpty()) listOf(0) else params.split(';').map { it.toIntOrNull() ?: 0 }
        var i = 0
        while (i < codes.size) {
            when (val c = codes[i]) {
                0 -> { style = SpanStyle(color = Ansi.defaultFg); bold = false }
                1 -> { bold = true; style = style.copy(fontWeight = FontWeight.Bold) }
                2 -> style = style.copy(color = style.color.copy(alpha = 0.65f))
                3 -> style = style.copy(fontStyle = FontStyle.Italic)
                4 -> style = style.copy(textDecoration = TextDecoration.Underline)
                7 -> style = style.copy(background = style.color, color = Ansi.background)
                21, 22 -> { bold = false; style = style.copy(fontWeight = FontWeight.Normal) }
                23 -> style = style.copy(fontStyle = FontStyle.Normal)
                24 -> style = style.copy(textDecoration = TextDecoration.None)
                27 -> style = style.copy(background = Color.Unspecified, color = Ansi.defaultFg)
                in 30..37 -> style = style.copy(color = Ansi.palette[(c - 30) + if (bold) 8 else 0])
                38 -> {
                    if (i + 2 < codes.size && codes[i + 1] == 5) {
                        style = style.copy(color = Ansi.color256(codes[i + 2])); i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                        style = style.copy(color = Color(codes[i + 2], codes[i + 3], codes[i + 4])); i += 4
                    }
                }
                39 -> style = style.copy(color = Ansi.defaultFg)
                in 40..47 -> style = style.copy(background = Ansi.palette[c - 40])
                48 -> {
                    if (i + 2 < codes.size && codes[i + 1] == 5) {
                        style = style.copy(background = Ansi.color256(codes[i + 2])); i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                        style = style.copy(background = Color(codes[i + 2], codes[i + 3], codes[i + 4])); i += 4
                    }
                }
                49 -> style = style.copy(background = Color.Unspecified)
                in 90..97 -> style = style.copy(color = Ansi.palette[(c - 90) + 8])
                in 100..107 -> style = style.copy(background = Ansi.palette[(c - 100) + 8])
            }
            i++
        }
    }
}
