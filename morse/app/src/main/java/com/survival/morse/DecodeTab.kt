package com.survival.morse

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val SILENCE_TICK_MS = 30L
private const val SILENCE_WATCH_MS = 10_000L
private const val SHOWN_TEXT = 1000
private const val SHOWN_CODE = 400

/** Level meter scale: 0 to 60 dB above the noise floor. */
private const val METER_RANGE_DB = 60.0

private val DecoderSaver = listSaver<MorseDecoder, Any>(
    save = { listOf(it.unitMs, it.text, it.raw) },
    restore = { MorseDecoder(it[0] as Double, it[1] as String, it[2] as String) }
)

@Composable
internal fun DecodeTab(prefs: MorsePrefs) {
    val decoder = rememberSaveable(saver = DecoderSaver) { MorseDecoder(prefs.decodeUnitMs) }
    var snapshot by remember { mutableStateOf(decoder.snapshot()) }
    val refresh: () -> Unit = { snapshot = decoder.snapshot() }
    var mode by rememberSaveable { mutableIntStateOf(0) }

    // The estimated speed is the starting point of the next session (S and TTT differ only by speed).
    LifecycleResumeEffect(decoder) {
        onPauseOrDispose { prefs.decodeUnitMs = decoder.unitMs }
    }

    TabContent {
        SegmentedChoice(
            options = listOf("Manipulateur", "Écoute au micro"),
            selected = mode,
            onSelect = { mode = it }
        )
        DecodedCard(
            snapshot = snapshot,
            onClear = {
                decoder.clear()
                refresh()
            }
        )
        if (mode == 0) KeyPanel(prefs, decoder, refresh) else MicPanel(decoder, refresh)
    }
}

@Composable
private fun DecodedCard(snapshot: DecodeSnapshot, onClear: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val text = snapshot.text.trimEnd()
    val raw = snapshot.raw.trimEnd(' ', '/')
    SurvieCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Texte décodé",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text("≈ ${formatWpm(snapshot.wpm)}", color = Accent, fontSize = 14.sp, style = Tabular)
        }
        Text(
            if (text.isEmpty()) "—" else text.takeLast(SHOWN_TEXT),
            color = if (text.isEmpty()) TextSecondary else Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (snapshot.pending.isNotEmpty()) {
            Text(
                "Lettre en cours : ${prettyCode(snapshot.pending)}",
                color = Accent,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        if (raw.isNotEmpty()) {
            Text(
                prettyCode(raw.takeLast(SHOWN_CODE)),
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        if (UNKNOWN_SIGN in text) Hint("$UNKNOWN_SIGN : suite de points et de traits qui ne correspond à aucun signe.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = "Effacer",
                onClick = onClear,
                modifier = Modifier.weight(1f),
                primary = false,
                enabled = text.isNotEmpty() || raw.isNotEmpty() || snapshot.pending.isNotEmpty()
            )
            ActionButton(
                text = "Copier",
                onClick = { clipboard.setText(AnnotatedString(text)) },
                modifier = Modifier.weight(1f),
                primary = false,
                enabled = text.isNotEmpty()
            )
        }
    }
}

@Composable
private fun KeyPanel(prefs: MorsePrefs, decoder: MorseDecoder, refresh: () -> Unit) {
    val tone = remember { ToneOutput() }
    val toneHz = remember { prefs.toneHz }
    var sidetone by remember { mutableStateOf(prefs.sidetone) }
    var pressed by remember { mutableStateOf(false) }
    var pressedAt by remember { mutableLongStateOf(0L) }
    var releasedAt by remember { mutableLongStateOf(0L) }

    // The side tone runs only while the app is in the foreground.
    LifecycleResumeEffect(sidetone) {
        if (sidetone) tone.startLive(toneHz)
        onPauseOrDispose {
            tone.stop()
            pressed = false
        }
    }

    // Closes letters and words while the key stays up.
    LaunchedEffect(pressed, releasedAt) {
        if (pressed || releasedAt == 0L) return@LaunchedEffect
        while (true) {
            val silent = SystemClock.uptimeMillis() - releasedAt
            decoder.silence(silent.toDouble())
            refresh()
            if (silent > SILENCE_WATCH_MS) break
            delay(SILENCE_TICK_MS)
        }
    }

    SurvieCard {
        CardTitle("Manipulateur")
        Hint("Appui bref = point, appui long = trait. La vitesse s'adapte à votre rythme ; marquez bien les pauses entre lettres et entre mots.")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(if (pressed) Accent else Navy)
                    .border(3.dp, Accent, CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Manipulateur Morse : appuyez brièvement pour un point, longuement pour un trait"
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val downAt = down.uptimeMillis
                            if (releasedAt > 0L) decoder.gap((downAt - releasedAt).toDouble())
                            pressedAt = downAt
                            pressed = true
                            tone.setKey(true)
                            val up = waitForUpOrCancellation()
                            up?.consume()
                            val upAt = up?.uptimeMillis ?: SystemClock.uptimeMillis()
                            tone.setKey(false)
                            pressed = false
                            decoder.mark((upAt - pressedAt).toDouble())
                            releasedAt = upAt
                            refresh()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (pressed) "Signal" else "Appuyer",
                    color = if (pressed) Bg else Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        CheckOption(
            label = "Bip du manipulateur",
            checked = sidetone,
            onCheckedChange = {
                sidetone = it
                prefs.sidetone = it
            },
            modifier = Modifier.fillMaxWidth(),
            detail = "Son à ${formatHz(toneHz)} pendant l'appui"
        )
    }
}

@Composable
private fun MicPanel(decoder: MorseDecoder, refresh: () -> Unit) {
    val context = LocalContext.current
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var permission by remember { mutableStateOf(readMicPermission(context, deniedOnce)) }
    var listening by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf<MicEvent.Level?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) deniedOnce = true
        permission = readMicPermission(context, deniedOnce)
        if (granted) {
            error = null
            listening = true
        }
    }

    // The permission may be granted or revoked in the system settings while the app is paused.
    LifecycleResumeEffect(Unit) {
        permission = readMicPermission(context, deniedOnce)
        if (permission != MicPermission.Granted) listening = false
        onPauseOrDispose { }
    }

    val view = LocalView.current
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose { view.keepScreenOn = false }
    }

    // repeatOnLifecycle releases the microphone when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(listening, lifecycleOwner) {
        if (!listening) return@LaunchedEffect
        try {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    MicListener.events(context.applicationContext).collect { event ->
                        when (event) {
                            is MicEvent.Key -> {
                                decoder.key(event.event, event.durationMs)
                                refresh()
                            }
                            is MicEvent.Level -> level = event
                        }
                    }
                } finally {
                    level = null
                    decoder.flush()
                    refresh()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? MicUnavailableException)?.message ?: MIC_UNAVAILABLE
            listening = false
        }
    }

    SurvieCard {
        CardTitle("Écoute au micro")
        Hint(
            "Décode un bip ou un sifflet net, entre 300 et 3 000 Hz, dans un endroit calme. " +
                "Approchez le téléphone de la source. Rien n'est enregistré."
        )
        if (listening) MicMeter(level)
        when {
            permission == MicPermission.PermanentlyDenied -> {
                Hint(
                    "L'accès au micro a été refusé. Activez-le dans les paramètres de l'application, " +
                        "rubrique Autorisations > Micro.",
                    Warning
                )
                ActionButton(
                    text = "Ouvrir les paramètres",
                    onClick = { openAppSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            listening -> ActionButton(
                text = "Arrêter l'écoute",
                onClick = { listening = false },
                modifier = Modifier.fillMaxWidth()
            )
            else -> {
                if (permission == MicPermission.Denied && deniedOnce) {
                    Hint("Le micro est nécessaire pour écouter les signaux sonores.", Warning)
                }
                ActionButton(
                    text = if (permission == MicPermission.Granted) "Écouter" else "Autoriser le micro et écouter",
                    onClick = {
                        error = null
                        if (permission == MicPermission.Granted) {
                            listening = true
                        } else {
                            launcher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        error?.let { Text(it, color = Danger, fontSize = 14.sp) }
    }
}

@Composable
private fun MicMeter(level: MicEvent.Level?) {
    val on = level?.toneOn == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) Success else Navy)
        )
        Text(
            when {
                level == null -> "Démarrage du micro…"
                on -> "Signal détecté · ≈ ${formatHz(level.toneHz)}"
                else -> "En attente d'un signal…"
            },
            color = if (on) Success else TextSecondary,
            fontSize = 14.sp,
            style = Tabular
        )
    }
    val fill = level?.let { ((it.levelDb - it.noiseDb) / METER_RANGE_DB).toFloat().coerceIn(0f, 1f) } ?: 0f
    val mark = level?.let { ((it.thresholdDb - it.noiseDb) / METER_RANGE_DB).toFloat().coerceIn(0f, 1f) }
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .semantics { contentDescription = "Niveau du signal reçu" }
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = Navy, cornerRadius = radius)
            if (fill > 0f) {
                drawRoundRect(
                    color = if (on) Success else Accent,
                    size = Size(size.width * fill, size.height),
                    cornerRadius = radius
                )
            }
            if (mark != null) {
                val x = size.width * mark
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
        }
        Hint("Le trait blanc indique le seuil de détection, qui s'adapte au bruit ambiant.")
    }
}
