package com.survival.metronome

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlin.math.roundToInt

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Danger = Color(0xFFFF6B6B)

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private const val PREFS_NAME = "survie"
private const val KEY_BPM = "metronome_bpm"
private const val KEY_BEATS = "metronome_beats_per_bar"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Hardware volume keys control the media stream the clicks are played on.
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent { SurvieTheme { AppScreen() } }
    }
}

@Composable
fun SurvieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Bg,
            primaryContainer = Navy, onPrimaryContainer = Color.White,
            secondary = Accent, onSecondary = Bg,
            secondaryContainer = Navy, onSecondaryContainer = Color.White,
            background = Bg, onBackground = Color.White,
            surface = Card, onSurface = Color.White,
            surfaceVariant = Navy, onSurfaceVariant = TextSecondary,
            surfaceContainer = Card, surfaceContainerHigh = Card, surfaceContainerHighest = Navy,
            outline = Navy, outlineVariant = Navy,
            error = Danger, onError = Bg
        ),
        content = content
    )
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var bpm by rememberSaveable {
        mutableIntStateOf(prefs.getInt(KEY_BPM, DEFAULT_BPM).coerceIn(MIN_BPM, MAX_BPM))
    }
    var beatsPerBar by rememberSaveable {
        mutableIntStateOf(prefs.getInt(KEY_BEATS, DEFAULT_BEATS).coerceIn(1, MAX_BEATS))
    }
    var playing by rememberSaveable { mutableStateOf(false) }
    var audioError by rememberSaveable { mutableStateOf(false) }
    var currentBeat by remember { mutableIntStateOf(-1) }
    val tapTempo = remember { TapTempo() }
    val engine = remember {
        MetronomeEngine(
            onBeat = { currentBeat = it },
            onError = {
                playing = false
                audioError = true
            }
        )
    }

    SideEffect {
        engine.bpm = bpm
        engine.beatsPerBar = beatsPerBar
    }

    // Also covers activity destruction: leaving the composition stops the thread and releases the track.
    val view = LocalView.current
    DisposableEffect(playing) {
        if (playing) {
            currentBeat = -1
            engine.start(bpm, beatsPerBar)
        }
        view.keepScreenOn = playing
        onDispose {
            engine.stop()
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(bpm, beatsPerBar) {
        prefs.edit {
            putInt(KEY_BPM, bpm)
            putInt(KEY_BEATS, beatsPerBar)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Métronome", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Tempo précis, mesure et tempo au tap", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        TempoCard(bpm = bpm, beatsPerBar = beatsPerBar, currentBeat = if (playing) currentBeat else -1)
        Spacer(Modifier.height(16.dp))

        TempoButtons(
            onAdjust = { delta -> bpm = (bpm + delta).coerceIn(MIN_BPM, MAX_BPM) },
            onTap = { tapTempo.tap(SystemClock.elapsedRealtime())?.let { bpm = it } }
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = bpm.toFloat(),
            onValueChange = { bpm = it.roundToInt().coerceIn(MIN_BPM, MAX_BPM) },
            valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Navy
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Text("Temps par mesure", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        BeatsSelector(selected = beatsPerBar, onSelect = { beatsPerBar = it })
        Spacer(Modifier.height(24.dp))

        StartStopButton(
            playing = playing,
            onClick = {
                if (!playing) audioError = false
                playing = !playing
            }
        )
        if (audioError) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Impossible de produire le son sur cet appareil. Réessayez.",
                color = Danger,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun TempoCard(bpm: Int, beatsPerBar: Int, currentBeat: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BeatDots(beatsPerBar = beatsPerBar, currentBeat = currentBeat)
        Spacer(Modifier.height(8.dp))
        Text(
            "$bpm",
            color = Color.White,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular
        )
        Text(
            "BPM · ${tempoName(bpm)}",
            color = Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BeatDots(beatsPerBar: Int, currentBeat: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clearAndSetSemantics { contentDescription = "Mesure à $beatsPerBar temps" },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(beatsPerBar) { index ->
            val accented = index == 0 && beatsPerBar > 1
            val color = when {
                index != currentBeat -> Navy
                accented -> Accent
                else -> Color.White
            }
            Box(
                Modifier
                    .size(if (accented) 32.dp else 24.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
private fun TempoButtons(onAdjust: (Int) -> Unit, onTap: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SecondaryButton("−5", Modifier.weight(1f)) { onAdjust(-5) }
        SecondaryButton("−1", Modifier.weight(1f)) { onAdjust(-1) }
        SecondaryButton("Tap", Modifier.weight(1.3f), contentColor = Accent, onClick = onTap)
        SecondaryButton("+1", Modifier.weight(1f)) { onAdjust(1) }
        SecondaryButton("+5", Modifier.weight(1f)) { onAdjust(5) }
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    modifier: Modifier,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = Tabular)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeatsSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (beats in 1..MAX_BEATS) {
            FilterChip(
                selected = beats == selected,
                onClick = { onSelect(beats) },
                label = {
                    Text(
                        "$beats",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Navy,
                    labelColor = Color.White,
                    selectedContainerColor = Accent,
                    selectedLabelColor = Bg
                ),
                border = null
            )
        }
    }
}

@Composable
private fun StartStopButton(playing: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = if (playing) {
            ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
        } else {
            ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
        }
    ) {
        Text(if (playing) "Arrêter" else "Démarrer", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
