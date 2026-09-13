package com.survival.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Terminal.init(this)
        setContent { TerminalApp() }
    }
}

@Composable
fun TerminalApp() {
    var fontSize by remember { mutableStateOf(13) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var histIndex by remember { mutableStateOf(-1) }

    val sessions = Terminal.sessions
    if (sessions.isEmpty()) return
    val session = sessions[Terminal.active.coerceIn(0, sessions.size - 1)]
    val editor = session.editor

    Column(
        Modifier
            .fillMaxSize()
            .background(Ansi.background)
            .safeDrawingPadding()
    ) {
        TabBar(
            fontSize = fontSize,
            onFontSize = { fontSize = it.coerceIn(8, 24) },
            session = session,
            input = input,
            onInput = { input = it }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            TerminalView(session, fontSize)
            if (editor != null) EditorOverlay(session, editor, fontSize)
        }
        if (editor == null) {
            KeyRow(session, input, { input = it }, { histIndex = it }, histIndex, fontSize)
            InputRow(session, input, { input = it }, fontSize) {
                val text = input.text
                input = TextFieldValue("")
                histIndex = -1
                if (text.contains('\n')) text.split("\n").forEach { session.submit(it) }
                else session.submit(text)
            }
        }
    }
}

// ------------------------------------------------------------------ barre d'onglets

@Composable
private fun TabBar(
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    session: Session,
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF2C001E)).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Terminal.sessions.forEachIndexed { i, s ->
                val selected = i == Terminal.active
                Box(
                    Modifier
                        .padding(2.dp)
                        .background(
                            if (selected) Ansi.accent else Color(0xFF45103A),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { Terminal.active = i }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        s.statusLine ?: "sh ${s.id}",
                        color = if (selected) Color(0xFF1C0016) else Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Box(
            Modifier.clickable { Terminal.newSession() }.padding(8.dp)
        ) { Icon(Icons.Default.Add, "nouvel onglet", tint = Color.White, modifier = Modifier.size(20.dp)) }
        Box {
            Box(
                Modifier.clickable { menu = true }.padding(8.dp)
            ) { Icon(Icons.Default.MoreVert, "menu", tint = Color.White, modifier = Modifier.size(20.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Nouvel onglet") }, onClick = {
                    menu = false; Terminal.newSession()
                })
                DropdownMenuItem(text = { Text("Fermer l'onglet") }, onClick = {
                    menu = false; Terminal.close(Terminal.active)
                })
                DropdownMenuItem(text = { Text("Coller") }, onClick = {
                    menu = false
                    clipboard.getText()?.text?.let { onInput(TextFieldValue(input.text + it)) }
                })
                DropdownMenuItem(text = { Text("Copier l'écran") }, onClick = {
                    menu = false
                    val all = session.buffer.lines.joinToString("\n") { it.text }
                    clipboard.setText(AnnotatedString(all))
                })
                DropdownMenuItem(text = { Text("Effacer l'écran") }, onClick = {
                    menu = false; session.buffer.clear()
                })
                DropdownMenuItem(text = { Text("Texte plus grand") }, onClick = { onFontSize(fontSize + 1) })
                DropdownMenuItem(text = { Text("Texte plus petit") }, onClick = { onFontSize(fontSize - 1) })
                DropdownMenuItem(text = { Text("Aide") }, onClick = {
                    menu = false; session.submit("help")
                })
            }
        }
    }
}

// ---------------------------------------------------------------------- écran

@Composable
private fun TerminalView(session: Session, fontSize: Int) {
    val listState = rememberLazyListState()
    val buffer = session.buffer
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthDp = maxWidth.value
        LaunchedEffect(widthDp, fontSize) {
            session.cols = (widthDp / (fontSize * 0.62f)).toInt().coerceIn(20, 200)
        }
        LaunchedEffect(buffer.version) {
            val last = buffer.lines.size
            if (last >= 0) runCatching { listState.scrollToItem(last) }
        }
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)
            ) {
                items(buffer.lines.size) { i ->
                    Text(
                        buffer.lines[i],
                        color = Ansi.defaultFg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 4).sp
                    )
                }
                item {
                    Text(
                        buffer.current,
                        color = Ansi.defaultFg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 4).sp
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ rangée de touches

private val KEYS = listOf(
    "ESC", "TAB", "^C", "^D", "↑", "↓", "←", "→",
    "|", "~", "/", "-", "_", "$", "*", "\"", "'", "(", ")", "[", "]", "{", "}", "&", ";", "<", ">", "#", "?"
)

@Composable
private fun KeyRow(
    session: Session,
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit,
    onHist: (Int) -> Unit,
    histIndex: Int,
    fontSize: Int
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF241021))
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp)
    ) {
        for (k in KEYS) {
            Box(
                Modifier
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .background(Color(0xFF3A2436), RoundedCornerShape(5.dp))
                    .clickable { onKey(k, session, input, onInput, onHist, histIndex) }
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Text(
                    k,
                    color = if (k.startsWith("^") || k.length > 1) Ansi.accent else Color(0xFFDDDDDD),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (fontSize - 1).coerceAtLeast(10).sp
                )
            }
        }
    }
}

private fun onKey(
    k: String,
    session: Session,
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit,
    onHist: (Int) -> Unit,
    histIndex: Int
) {
    val text = input.text
    val pos = input.selection.start.coerceIn(0, text.length)
    when (k) {
        "^C" -> session.interrupt()
        "^D" -> session.sendEof()
        "ESC" -> session.sendRaw(Ansi.ESC)
        "TAB" -> {
            val start = text.lastIndexOf(' ', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val fragment = text.substring(start, pos)
            val matches = session.shell.complete(fragment)
            when {
                matches.isEmpty() -> {}
                matches.size == 1 -> {
                    val done = matches[0] + if (matches[0].endsWith("/")) "" else " "
                    val nt = text.substring(0, start) + done + text.substring(pos)
                    onInput(TextFieldValue(nt, TextRange(start + done.length)))
                }
                else -> {
                    val common = commonPrefix(matches)
                    session.buffer.write(session.shell.promptText() + text + "\n")
                    session.buffer.write(matches.joinToString("  ") + "\n")
                    if (common.length > fragment.length) {
                        val nt = text.substring(0, start) + common + text.substring(pos)
                        onInput(TextFieldValue(nt, TextRange(start + common.length)))
                    }
                }
            }
        }
        "↑" -> {
            val h = session.shell.history
            if (h.isNotEmpty()) {
                val i = (if (histIndex < 0) h.size - 1 else (histIndex - 1).coerceAtLeast(0))
                onHist(i)
                onInput(TextFieldValue(h[i], TextRange(h[i].length)))
            }
        }
        "↓" -> {
            val h = session.shell.history
            if (h.isNotEmpty() && histIndex >= 0) {
                val i = histIndex + 1
                if (i >= h.size) { onHist(-1); onInput(TextFieldValue("")) }
                else { onHist(i); onInput(TextFieldValue(h[i], TextRange(h[i].length))) }
            }
        }
        "←" -> onInput(input.copy(selection = TextRange((pos - 1).coerceAtLeast(0))))
        "→" -> onInput(input.copy(selection = TextRange((pos + 1).coerceAtMost(text.length))))
        else -> {
            val nt = text.substring(0, pos) + k + text.substring(pos)
            onInput(TextFieldValue(nt, TextRange(pos + k.length)))
        }
    }
}

private fun commonPrefix(list: List<String>): String {
    if (list.isEmpty()) return ""
    var p = list[0]
    for (s in list) {
        var i = 0
        while (i < p.length && i < s.length && p[i] == s[i]) i++
        p = p.substring(0, i)
    }
    return p
}

// ------------------------------------------------------------- ligne de saisie

@Composable
private fun InputRow(
    session: Session,
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit,
    fontSize: Int,
    onSubmit: () -> Unit
) {
    val hidden = session.echoMode == EchoMode.HIDDEN
    val prompt = when {
        session.promptOverride != null -> "» "
        session.running -> "» "
        else -> session.shell.promptPlain()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF241021))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            prompt,
            color = if (session.running) Ansi.accent else Color(0xFF8AE234),
            fontFamily = FontFamily.Monospace,
            fontSize = (fontSize - 1).coerceAtLeast(10).sp,
            fontWeight = FontWeight.Bold
        )
        BasicTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            textStyle = TextStyle(
                color = Ansi.defaultFg,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp
            ),
            cursorBrush = SolidColor(Ansi.accent),
            singleLine = true,
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { onSubmit() })
        )
        Box(
            Modifier
                .clickable { if (session.running) session.interrupt() else onSubmit() }
                .padding(6.dp)
        ) {
            Text(
                if (session.running) "■" else "▶",
                color = if (session.running) Color(0xFFEF2929) else Ansi.accent,
                fontSize = (fontSize + 3).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
