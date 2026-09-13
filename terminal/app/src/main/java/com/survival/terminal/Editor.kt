package com.survival.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Éditeur plein écran, à la manière de nano. */
@Composable
fun EditorOverlay(session: Session, state: EditorState, fontSize: Int) {
    var value by remember(state) { mutableStateOf(TextFieldValue(state.initial)) }
    val modified = value.text != state.initial
    val bar = Color(0xFFD3D7CF)
    val barText = Color(0xFF1C0016)

    Column(Modifier.fillMaxSize().background(Ansi.background)) {
        // barre de titre
        Row(
            Modifier.fillMaxWidth().background(bar).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GNU nano 7.2", color = barText, fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(Fs.pretty(state.file), color = barText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(if (modified) "Modifié" else "", color = barText,
                fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }

        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = Ansi.defaultFg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 5).sp
                ),
                cursorBrush = SolidColor(Ansi.accent)
            )
        }

        // pied de page facon nano
        Column(Modifier.fillMaxWidth().padding(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NanoKey("^O", "Écrire", Modifier.weight(1f)) {
                    session.closeEditor(true, value.text)
                }
                NanoKey("^X", "Quitter", Modifier.weight(1f)) {
                    session.closeEditor(false, value.text)
                }
                NanoKey("^K", "Couper ligne", Modifier.weight(1f)) {
                    val t = value.text
                    val pos = value.selection.start.coerceIn(0, t.length)
                    val start = t.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                    var end = t.indexOf('\n', pos)
                    if (end < 0) end = t.length else end += 1
                    val nt = t.removeRange(start, end)
                    value = TextFieldValue(nt, androidx.compose.ui.text.TextRange(start.coerceAtMost(nt.length)))
                }
            }
            Text(
                "  ${state.file.name} — ^O enregistre, ^X quitte",
                color = Color(0xFF888888), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun NanoKey(key: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(Color(0xFF2A2A2A))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(key, color = Ansi.accent, fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(" $label", color = Color(0xFFDDDDDD), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}
