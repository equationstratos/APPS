package com.survival.morse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_MESSAGE_LENGTH = 300
private const val SOS_MESSAGE = "<SOS>"
private const val LAMP_SPEED_LIMIT = 15
private const val TORCH_ERROR =
    "Lampe indisponible : elle est peut-être utilisée par une autre application. Les autres sorties continuent."

@Composable
internal fun EmitTab(prefs: MorsePrefs, flash: ScreenFlash) {
    val context = LocalContext.current
    val transmitter = remember(context) { Transmitter(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    var text by rememberSaveable { mutableStateOf(prefs.message) }
    var wpm by remember { mutableIntStateOf(prefs.wpm) }
    var toneHz by remember { mutableIntStateOf(prefs.toneHz) }
    var lamp by remember { mutableStateOf(prefs.lamp && transmitter.hasFlash) }
    var sound by remember { mutableStateOf(prefs.sound) }
    var vibration by remember { mutableStateOf(prefs.vibration && transmitter.hasVibrator) }
    var screen by remember { mutableStateOf(prefs.screen) }
    var repeat by remember { mutableStateOf(prefs.repeat) }

    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf<Encoded?>(null) }
    var activeToken by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }

    val encoded = remember(text) { encode(text) }
    val anyOutput = lamp || sound || vibration || screen

    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    // Everything goes dark when the app is paused or the tab is left.
    LifecycleResumeEffect(transmitter) {
        onPauseOrDispose {
            job?.cancel()
            transmitter.allOff()
        }
    }

    fun start(message: String) {
        val toSend = encode(message)
        if (toSend.isEmpty || !anyOutput) return
        focusManager.clearFocus()
        prefs.message = message
        val plan = TxPlan(
            timeline = timelineOf(toSend),
            unitMs = unitMs(wpm),
            repeat = repeat,
            lamp = lamp,
            sound = sound,
            vibration = vibration,
            screen = screen,
            toneHz = toneHz
        )
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin()
            error = null
            sending = toSend
            activeToken = -1
            running = true
            if (plan.screen) {
                flash.onStop = { job?.cancel() }
                flash.lit = false
                flash.active = true
            }
            try {
                transmitter.run(
                    plan,
                    onToken = { activeToken = it },
                    onLight = { flash.lit = it },
                    onTorchError = { error = TORCH_ERROR }
                )
            } finally {
                running = false
                activeToken = -1
                flash.active = false
                flash.lit = false
            }
        }
    }

    TabContent {
        SurvieCard {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_MESSAGE_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message à émettre") },
                placeholder = { Text("Ex. : BESOIN AIDE") },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer le message", tint = TextSecondary)
                        }
                    }
                },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { start(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Accent.copy(alpha = 0.45f),
                    focusedLabelColor = Accent,
                    cursorColor = Accent
                )
            )
            Text("Messages rapides", color = TextSecondary, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, message) in listOf("SOS" to SOS_MESSAGE, "OK" to "OK", "AIDE" to "AIDE")) {
                    ActionButton(
                        text = label,
                        onClick = {
                            text = message
                            start(message)
                        },
                        modifier = Modifier.weight(1f),
                        primary = false,
                        enabled = anyOutput
                    )
                }
            }
            Hint(
                "SOS part d'un seul bloc (${prettyCode("...---...")}), sans pause entre les lettres. " +
                    "Astuce : écrivez <SK> ou <SOS> pour enchaîner des lettres."
            )
        }

        CodeCard(
            shown = if (running) sending else encoded,
            activeToken = if (running) activeToken else -1,
            running = running,
            wpm = wpm,
            ignored = encoded.ignored
        )

        ActionButton(
            text = if (running) "Arrêter" else if (repeat) "Émettre en boucle" else "Émettre",
            onClick = { if (running) job?.cancel() else start(text) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            enabled = running || (!encoded.isEmpty && anyOutput)
        )
        error?.let { Text(it, color = Danger, fontSize = 14.sp) }

        SurvieCard {
            CardTitle("Sorties")
            Row(Modifier.fillMaxWidth()) {
                CheckOption(
                    label = "Lampe",
                    checked = lamp,
                    onCheckedChange = {
                        lamp = it
                        prefs.lamp = it
                    },
                    modifier = Modifier.weight(1f),
                    enabled = transmitter.hasFlash && !running,
                    detail = if (transmitter.hasFlash) "Flash arrière" else "Pas de flash"
                )
                CheckOption(
                    label = "Son",
                    checked = sound,
                    onCheckedChange = {
                        sound = it
                        prefs.sound = it
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !running,
                    detail = formatHz(toneHz)
                )
            }
            Row(Modifier.fillMaxWidth()) {
                CheckOption(
                    label = "Vibration",
                    checked = vibration,
                    onCheckedChange = {
                        vibration = it
                        prefs.vibration = it
                    },
                    modifier = Modifier.weight(1f),
                    enabled = transmitter.hasVibrator && !running,
                    detail = if (transmitter.hasVibrator) "Discrète" else "Indisponible"
                )
                CheckOption(
                    label = "Écran",
                    checked = screen,
                    onCheckedChange = {
                        screen = it
                        prefs.screen = it
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !running,
                    detail = "Flash blanc"
                )
            }
            if (!anyOutput) Hint("Choisissez au moins une sortie.", Warning)
            if (lamp && wpm > LAMP_SPEED_LIMIT) {
                Hint("Au-delà de $LAMP_SPEED_LIMIT mots/min, le flash de certains téléphones ne suit plus le rythme.")
            }
        }

        SurvieCard {
            CardTitle("Réglages")
            LabeledSlider(
                title = "Vitesse",
                valueText = formatWpm(wpm),
                value = wpm.toFloat(),
                range = MIN_WPM.toFloat()..MAX_WPM.toFloat(),
                steps = MAX_WPM - MIN_WPM - 1,
                description = "Vitesse d'émission en mots par minute",
                onValueChange = { wpm = it.roundToInt().coerceIn(MIN_WPM, MAX_WPM) },
                onValueChangeFinished = { prefs.wpm = wpm },
                enabled = !running
            )
            Hint("${formatUnit(wpm)}, trait = ${unitMs(wpm).times(DASH_UNITS).roundToInt()} ms (norme PARIS)")
            LabeledSlider(
                title = "Tonalité",
                valueText = formatHz(toneHz),
                value = toneHz.toFloat(),
                range = MIN_TONE_HZ.toFloat()..MAX_TONE_HZ.toFloat(),
                steps = (MAX_TONE_HZ - MIN_TONE_HZ) / TONE_STEP_HZ - 1,
                description = "Fréquence du son en hertz",
                onValueChange = {
                    val stepped = (it / TONE_STEP_HZ).roundToInt() * TONE_STEP_HZ
                    toneHz = stepped.coerceIn(MIN_TONE_HZ, MAX_TONE_HZ)
                },
                onValueChangeFinished = { prefs.toneHz = toneHz },
                enabled = sound && !running
            )
            CheckOption(
                label = "Répéter",
                checked = repeat,
                onCheckedChange = {
                    repeat = it
                    prefs.repeat = it
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                detail = "En boucle, avec une pause de 7 points entre deux envois"
            )
        }
    }
}

@Composable
private fun CodeCard(shown: Encoded?, activeToken: Int, running: Boolean, wpm: Int, ignored: List<String>) {
    SurvieCard {
        CardTitle("Code")
        if (shown == null || shown.isEmpty) {
            Hint("Saisissez un texte ou choisissez un message rapide.")
        } else {
            val current = shown.tokens.getOrNull(activeToken)
            if (running) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        current?.label ?: "…",
                        color = Accent,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        current?.let { prettyCode(it.code) } ?: "Émission en cours",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }
            Text(
                highlightedCode(shown, activeToken),
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 30.sp,
                fontFamily = FontFamily.Monospace
            )
            val durationMs = timelineOf(shown).endUnit * unitMs(wpm)
            Hint("${shown.tokens.size} signes · ${formatDuration(durationMs)} à ${formatWpm(wpm)}")
        }
        if (!running && ignored.isNotEmpty()) {
            Hint("Ignorés (sans équivalent Morse) : ${ignored.joinToString(" ")}", Warning)
        }
    }
}

/** Codes with letters separated by a space and words by " / "; the sign being sent is highlighted. */
private fun highlightedCode(encoded: Encoded, activeToken: Int): AnnotatedString = buildAnnotatedString {
    var index = 0
    encoded.words.forEachIndexed { w, word ->
        if (w > 0) append(WORD_SEPARATOR)
        word.forEachIndexed { t, token ->
            if (t > 0) append(' ')
            if (index == activeToken) {
                withStyle(SpanStyle(color = Bg, background = Accent, fontWeight = FontWeight.Bold)) {
                    append(prettyCode(token.code))
                }
            } else {
                append(prettyCode(token.code))
            }
            index++
        }
    }
}
