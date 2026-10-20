package com.survival.morse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val NEXT_DELAY_MS = 1200L

private data class Score(
    val correct: Int = 0,
    val total: Int = 0,
    val streak: Int = 0,
    val recentCorrect: Int = 0,
    val recentCount: Int = 0
)

private fun KochTrainer.score() = Score(correct, total, streak, recentCorrect, recentCount)

@Composable
internal fun LearnTab(prefs: MorsePrefs) {
    val tone = remember { ToneOutput() }
    val toneHz = remember { prefs.toneHz }
    val trainer = remember { KochTrainer(prefs.kochLevel) }
    var level by remember { mutableIntStateOf(trainer.level) }
    var wpm by remember { mutableIntStateOf(prefs.learnWpm) }
    var typing by remember { mutableStateOf(prefs.learnTyping) }
    var question by remember { mutableStateOf<KochQuestion?>(null) }
    var given by remember { mutableStateOf<Char?>(null) }
    var typed by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(trainer.score()) }
    var unlocked by remember { mutableStateOf<Char?>(null) }

    LifecycleResumeEffect(tone) {
        onPauseOrDispose { tone.stop() }
    }

    fun play(char: Char) {
        val code = Morse.codeOf(char) ?: return
        tone.play(timelineOf(listOf(listOf(MorseToken(char.toString(), code)))), unitMs(wpm), toneHz, repeat = false)
    }

    fun ask() {
        val next = trainer.next(question?.answer)
        question = next
        given = null
        typed = ""
        play(next.answer)
    }

    fun answer(char: Char) {
        val current = question ?: return
        if (given != null) return
        given = char
        val result = trainer.answer(current, char)
        if (result.leveledUp) {
            level = trainer.level
            prefs.kochLevel = level
            unlocked = trainer.newest
        }
        score = trainer.score()
    }

    fun changeLevel(newLevel: Int) {
        trainer.changeLevel(newLevel)
        level = trainer.level
        prefs.kochLevel = level
        unlocked = null
        score = trainer.score()
        if (question != null) ask()
    }

    // A right answer moves on by itself; after a mistake the learner reads the correction first.
    LaunchedEffect(question, given) {
        val current = question ?: return@LaunchedEffect
        if (given == current.answer) {
            delay(NEXT_DELAY_MS)
            ask()
        }
    }

    TabContent {
        SurvieCard {
            CardTitle("Méthode de Koch")
            Hint(
                "Écoutez chaque caractère à vitesse réelle et reconnaissez-le à l'oreille, sans compter " +
                    "les points et les traits. Un nouveau caractère s'ajoute après $PASS_CORRECT bonnes réponses " +
                    "sur les $PASS_WINDOW dernières."
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Niveau $level / $MAX_KOCH_LEVEL",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = Tabular,
                    modifier = Modifier.weight(1f)
                )
                LevelButton("−", "Retirer le dernier caractère", enabled = level > MIN_KOCH_LEVEL) { changeLevel(level - 1) }
                LevelButton("+", "Ajouter le caractère suivant", enabled = level < MAX_KOCH_LEVEL) { changeLevel(level + 1) }
            }
            Text(
                buildAnnotatedString {
                    KOCH_ORDER.take(level).forEachIndexed { index, char ->
                        if (index > 0) append("  ")
                        if (index == level - 1) {
                            withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) { append(char) }
                        } else {
                            append(char)
                        }
                    }
                },
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 26.sp
            )
            Hint("En bleu : le dernier caractère ajouté (${trainer.newest} = ${prettyCode(Morse.codeOf(trainer.newest) ?: "")}).")
            LabeledSlider(
                title = "Vitesse des caractères",
                valueText = formatWpm(wpm),
                value = wpm.toFloat(),
                range = MIN_WPM.toFloat()..MAX_WPM.toFloat(),
                steps = MAX_WPM - MIN_WPM - 1,
                description = "Vitesse des caractères en mots par minute",
                onValueChange = { wpm = it.roundToInt().coerceIn(MIN_WPM, MAX_WPM) },
                onValueChangeFinished = { prefs.learnWpm = wpm }
            )
            SegmentedChoice(
                options = listOf("Choix multiple", "Clavier"),
                selected = if (typing) 1 else 0,
                onSelect = {
                    typing = it == 1
                    prefs.learnTyping = typing
                }
            )
        }

        unlocked?.let { char ->
            SurvieCard {
                Text(
                    "Bravo ! Nouveau caractère : $char = ${prettyCode(Morse.codeOf(char) ?: "")}",
                    color = Success,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ActionButton(
                    text = "Écouter $char",
                    onClick = { play(char) },
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )
            }
        }

        SurvieCard {
            CardTitle("Exercice")
            val current = question
            if (current == null) {
                Hint("Un caractère va être joué au son : retrouvez-le parmi les propositions ou tapez-le.")
                ActionButton(text = "Commencer", onClick = { ask() }, modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (given == null) "Quel est ce caractère ?" else "Réponse",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { play(current.answer) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = ButtonShape,
                        colors = secondaryButtonColors()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Réécouter", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (typing) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { value ->
                            val char = value.lastOrNull()?.let { normalizeChar(it).firstOrNull() }
                            typed = value.takeLast(1)
                            if (char != null && char != ' ') answer(char)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Tapez le caractère entendu") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Accent.copy(alpha = 0.45f),
                            focusedLabelColor = Accent,
                            cursorColor = Accent
                        )
                    )
                } else {
                    for (row in current.choices.chunked(2)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (choice in row) {
                                ChoiceButton(
                                    char = choice,
                                    state = choiceState(choice, current.answer, given),
                                    onClick = { answer(choice) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                given?.let { answered ->
                    val answerCode = prettyCode(Morse.codeOf(current.answer) ?: "")
                    val ok = answered == current.answer
                    Text(
                        if (ok) {
                            "Exact : ${current.answer} = $answerCode"
                        } else {
                            "Non : c'était ${current.answer} ($answerCode). " +
                                "Vous avez répondu $answered (${prettyCode(Morse.codeOf(answered) ?: "?")})."
                        },
                        color = if (ok) Success else Danger,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    if (!ok) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton(
                                text = "Réécouter ${current.answer}",
                                onClick = { play(current.answer) },
                                modifier = Modifier.weight(1f),
                                primary = false
                            )
                            ActionButton(text = "Suivant", onClick = { ask() }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        SurvieCard {
            CardTitle("Score")
            ScoreLine("Bonnes réponses", "${score.correct} / ${score.total} (${formatPercent(score.correct, score.total)})")
            ScoreLine("Série en cours", "${score.streak}")
            ScoreLine("$PASS_WINDOW dernières réponses", "${score.recentCorrect} / ${score.recentCount}")
            Hint("Objectif : $PASS_CORRECT bonnes réponses sur $PASS_WINDOW pour débloquer le caractère suivant.")
            ActionButton(
                text = "Remettre le score à zéro",
                onClick = {
                    trainer.resetScore()
                    score = trainer.score()
                },
                modifier = Modifier.fillMaxWidth(),
                primary = false,
                enabled = score.total > 0
            )
        }
    }
}

private enum class ChoiceState { Neutral, Right, Wrong, Dimmed }

private fun choiceState(choice: Char, answer: Char, given: Char?): ChoiceState = when {
    given == null -> ChoiceState.Neutral
    choice == answer -> ChoiceState.Right
    choice == given -> ChoiceState.Wrong
    else -> ChoiceState.Dimmed
}

@Composable
private fun ChoiceButton(char: Char, state: ChoiceState, onClick: () -> Unit, modifier: Modifier) {
    val container = when (state) {
        ChoiceState.Neutral -> Navy
        ChoiceState.Right -> Success
        ChoiceState.Wrong -> Danger
        ChoiceState.Dimmed -> Navy.copy(alpha = 0.5f)
    }
    val content = when (state) {
        ChoiceState.Neutral -> Color.White
        ChoiceState.Right, ChoiceState.Wrong -> Bg
        ChoiceState.Dimmed -> TextSecondary
    }
    Button(
        onClick = { if (state == ChoiceState.Neutral) onClick() },
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics { contentDescription = "Réponse $char" },
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) {
        Text(char.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LevelButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp)
            .semantics { contentDescription = description },
        shape = ButtonShape,
        colors = secondaryButtonColors(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScoreLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
    }
}
