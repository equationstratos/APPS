package com.survival.morse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private const val COLUMNS = 4
private const val HIGHLIGHT_TAIL_MS = 150L

@Composable
internal fun TableTab(prefs: MorsePrefs) {
    val tone = remember { ToneOutput() }
    val wpm = remember { prefs.wpm }
    val toneHz = remember { prefs.toneHz }
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf<String?>(null) }
    var highlightJob by remember { mutableStateOf<Job?>(null) }

    LifecycleResumeEffect(tone) {
        onPauseOrDispose {
            tone.stop()
            highlightJob?.cancel()
            playing = null
        }
    }

    fun play(key: String, token: MorseToken) {
        val timeline = timelineOf(listOf(listOf(token)))
        tone.play(timeline, unitMs(wpm), toneHz, repeat = false)
        playing = key
        highlightJob?.cancel()
        highlightJob = scope.launch {
            delay((timeline.endUnit * unitMs(wpm)).roundToLong() + HIGHLIGHT_TAIL_MS)
            playing = null
        }
    }

    TabContent {
        Hint(
            "Touchez un signe pour l'écouter à ${formatWpm(wpm)}. La vitesse et la tonalité se règlent dans l'onglet Émettre."
        )
        for (group in MorseGroup.entries) {
            val signs = Morse.signs.filter { it.group == group }
            SurvieCard {
                CardTitle(group.title)
                for (row in signs.chunked(COLUMNS)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (sign in row) {
                            val key = sign.char.toString()
                            SignCell(
                                label = key,
                                code = sign.code,
                                active = playing == key,
                                onClick = { play(key, MorseToken(key, sign.code)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (group == MorseGroup.LETTERS) {
                    Hint("Les autres lettres accentuées s'émettent comme leur lettre de base (À = A, Ç = C).")
                }
                if (group == MorseGroup.EXTRA) {
                    Hint("Usages courants, absents de la recommandation UIT-R M.1677-1.")
                }
            }
        }
        SurvieCard {
            CardTitle("Signaux de procédure")
            Hint("Lettres enchaînées sans pause, notées entre chevrons : <SK>.")
            for (prosign in Morse.prosigns) {
                val key = "<${prosign.name}>"
                ProsignRow(
                    prosign = prosign,
                    active = playing == key,
                    onClick = { play(key, MorseToken(prosign.name, prosign.code)) }
                )
            }
        }
        SurvieCard {
            CardTitle("Durées")
            Hint(
                "Point = 1 unité, trait = 3 unités. Pause entre les éléments d'un signe = 1 unité, " +
                    "entre deux lettres = 3 unités, entre deux mots = 7 unités. " +
                    "À ${formatWpm(wpm)}, une unité dure ${unitMs(wpm).roundToLong()} ms (mot type « PARIS » = 50 unités)."
            )
        }
    }
}

@Composable
private fun SignCell(label: String, code: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(CellShape)
            .background(if (active) Accent else Navy)
            .clickable(onClickLabel = "Écouter", onClick = onClick)
            .semantics { contentDescription = "$label : ${spokenCode(code)}" }
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (active) Bg else Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            prettyCode(code),
            color = if (active) Bg else Accent,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ProsignRow(prosign: Prosign, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CellShape)
            .background(if (active) Accent else Navy)
            .clickable(onClickLabel = "Écouter", onClick = onClick)
            .semantics { contentDescription = "${prosign.name} : ${prosign.meaning}, ${spokenCode(prosign.code)}" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            prosign.name,
            color = if (active) Bg else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp)
        )
        Text(
            prettyCode(prosign.code),
            color = if (active) Bg else Accent,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(104.dp)
        )
        Text(
            prosign.meaning,
            color = if (active) Bg else TextSecondary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/** "point trait trait" for screen readers. */
private fun spokenCode(code: String): String = code.map { if (it == '.') "point" else "trait" }.joinToString(" ")
