package com.survival.tuner

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CancellationException

private const val NO_VALUE = "—"

/** Half range of the gauge (cents) and the angle it spans either side of the vertical. */
private const val GAUGE_CENTS = 50.0
private const val GAUGE_HALF_ANGLE_DEG = 60.0

/** Range of the microphone level bar (dBFS). */
private const val LEVEL_MIN_DB = -80.0
private const val LEVEL_MAX_DB = -10.0

/** Consecutive frames of digital silence (about 2 s) after which the mic is reported as muted. */
private const val MUTED_FRAMES = 40

/** Below this note the pure sine is hard to hear on a phone speaker (Do3, about 131 Hz). */
private const val LOW_TONE_MIDI = 48

private val REFERENCE_PRESETS = listOf(415, 432, 440, 442)

private val ButtonShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private val GaugeHeight = 136.dp
private val GaugeStroke = 10.dp
private val GaugePivotInset = 12.dp
private val GaugeLabelWidth = 44.dp

private enum class MicPermission { Granted, Denied, PermanentlyDenied }

private enum class Chevron { Left, Right, Down }

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val settings = remember(context) {
        TunerSettings(context.getSharedPreferences(TunerSettings.PREFS_NAME, Context.MODE_PRIVATE))
    }
    val hasMicrophone = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    // --- Microphone permission (asked on first launch, re-read on every resume).
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var autoRequested by rememberSaveable { mutableStateOf(false) }
    var permission by remember { mutableStateOf(readMicPermission(context, deniedOnce)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) deniedOnce = true
        permission = readMicPermission(context, deniedOnce)
    }
    LaunchedEffect(Unit) {
        if (hasMicrophone && permission != MicPermission.Granted && !autoRequested) {
            autoRequested = true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // --- Reference tone.
    var toneMidi by rememberSaveable { mutableIntStateOf(REFERENCE_MIDI) }
    var tonePlaying by remember { mutableStateOf(false) }
    var toneError by remember { mutableStateOf(false) }
    val player = remember {
        TonePlayer(
            onError = {
                tonePlaying = false
                toneError = true
            }
        )
    }
    SideEffect {
        player.frequency = frequencyOf(toneMidi, settings.referenceHz.toDouble())
        player.rich = settings.richTone
    }

    LifecycleResumeEffect(Unit) {
        permission = readMicPermission(context, deniedOnce)
        // The tone never keeps playing in the background.
        onPauseOrDispose {
            player.stop()
            tonePlaying = false
        }
    }

    // --- Live tuning.
    var liveFrequency by remember { mutableStateOf<Double?>(null) }
    var lastFrequency by remember { mutableStateOf<Double?>(null) }
    var levelDb by remember { mutableDoubleStateOf(SILENCE_DB) }
    var silentFrames by remember { mutableIntStateOf(0) }
    var micError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var manualString by rememberSaveable { mutableStateOf<Int?>(null) }
    var autoString by remember { mutableStateOf<Int?>(null) }

    val instrument = settings.instrument
    val reference = settings.referenceHz.toDouble()
    val listening = hasMicrophone && permission == MicPermission.Granted && micError == null
    val currentStrings by rememberUpdatedState(instrument.strings)
    val currentReference by rememberUpdatedState(reference)

    // The capture runs only while the screen is resumed: repeatOnLifecycle stops it on pause
    // (recomposition is suspended in the background, so a composition flag would not).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(listening, retryKey, lifecycleOwner) {
        if (!listening) return@LaunchedEffect
        try {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    MicrophoneInput.frames(context.applicationContext).collect { frame ->
                        levelDb = frame.levelDb
                        silentFrames = if (frame.levelDb <= SILENCE_DB) silentFrames + 1 else 0
                        val frequency = frame.frequency
                        liveFrequency = frequency
                        if (frequency != null) {
                            lastFrequency = frequency
                            if (manualString == null) {
                                autoString = closestString(frequency, currentStrings, currentReference, autoString)
                            }
                        }
                    }
                } finally {
                    liveFrequency = null
                    levelDb = SILENCE_DB
                    silentFrames = 0
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            micError = (e as? MicUnavailableException)?.message ?: MIC_UNAVAILABLE
        }
    }

    val view = LocalView.current
    val keepAwake = listening || tonePlaying
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    val shownFrequency = liveFrequency ?: lastFrequency
    val reading = shownFrequency?.let { tuningReading(it, reference, instrument.strings, manualString ?: autoString) }
    val highlightedString = manualString ?: reading?.stringIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header()
        val error = micError
        when {
            !hasMicrophone -> NoMicrophoneCard()
            permission != MicPermission.Granted -> PermissionCard(
                permanentlyDenied = permission == MicPermission.PermanentlyDenied,
                onClick = {
                    if (permission == MicPermission.PermanentlyDenied) {
                        openAppSettings(context)
                    } else {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
            error != null -> MicErrorCard(
                message = error,
                onRetry = {
                    micError = null
                    retryKey++
                }
            )
            else -> TunerCard(
                frequency = shownFrequency,
                reading = reading,
                live = liveFrequency != null,
                instrument = instrument,
                notation = settings.notation,
                referenceHz = reference,
                levelDb = levelDb,
                muted = silentFrames >= MUTED_FRAMES
            )
        }
        InstrumentCard(
            instrument = instrument,
            notation = settings.notation,
            selectedString = highlightedString,
            manual = manualString != null,
            onSelectInstrument = {
                settings.selectInstrument(it)
                manualString = null
                autoString = null
            },
            onSelectString = { index ->
                manualString = index
                instrument.strings.getOrNull(index)?.let { toneMidi = clampToneMidi(it) }
            },
            onAuto = {
                manualString = null
                autoString = null
            }
        )
        ToneCard(
            midi = toneMidi,
            notation = settings.notation,
            referenceHz = settings.referenceHz,
            playing = tonePlaying,
            rich = settings.richTone,
            error = toneError,
            onPrevious = { toneMidi = clampToneMidi(toneMidi - 1) },
            onNext = { toneMidi = clampToneMidi(toneMidi + 1) },
            onToggle = {
                if (tonePlaying) {
                    player.stop()
                    tonePlaying = false
                } else {
                    toneError = false
                    player.frequency = frequencyOf(toneMidi, settings.referenceHz.toDouble())
                    player.rich = settings.richTone
                    player.start()
                    tonePlaying = true
                }
            },
            onRichChange = settings::selectRichTone
        )
        SettingsCard(
            referenceHz = settings.referenceHz,
            notation = settings.notation,
            onReferenceChange = settings::selectReference,
            onNotationChange = settings::selectNotation
        )
        TipsCard()
    }
}

@Composable
private fun Header() {
    Column {
        Text("Accordeur", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Accordage par le micro et diapason", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun TunerCard(
    frequency: Double?,
    reading: TuningReading?,
    live: Boolean,
    instrument: Instrument,
    notation: Notation,
    referenceHz: Double,
    levelDb: Double,
    muted: Boolean
) {
    val status = reading?.let { tuningStatus(it.cents) }
    val accent = when {
        reading == null || !live -> TextSecondary
        status == TuningStatus.InTune -> Success
        else -> Warning
    }
    SurvieCard {
        val stringIndex = reading?.stringIndex
        val heading = when {
            reading == null -> if (instrument.isChromatic) "Note la plus proche" else instrument.name
            stringIndex != null -> "Corde ${instrument.stringNumber(stringIndex)} · " +
                "${noteLabel(reading.targetMidi, notation)} à " +
                formatFrequency(frequencyOf(reading.targetMidi, referenceHz))
            else -> "Note la plus proche"
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(heading, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (reading != null && !live) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "Dernière note",
                    color = Bg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .background(TextSecondary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        NoteDisplay(midi = reading?.targetMidi, notation = notation, color = if (live) Color.White else TextSecondary)
        Text(
            frequency?.let(::formatFrequency) ?: "$NO_VALUE${NBSP}Hz",
            color = if (live) Color.White else TextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            style = Tabular,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val cents = reading?.cents
        TuningGauge(
            cents = cents,
            live = live,
            description = if (cents == null || status == null) {
                "Jauge d'accord : aucune note"
            } else {
                "Écart ${spokenCents(roundedCents(cents))}, ${statusLabel(status).lowercase()}"
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (cents == null || status == null) {
                "Jouez une note sur l'instrument"
            } else {
                "${formatCentsWithUnit(roundedCents(cents))} · ${statusLabel(status)}"
            },
            color = accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (reading != null && stringIndex != null && reading.playedMidi != reading.targetMidi) {
            Text(
                "Note jouée : ${noteLabel(reading.playedMidi, notation)}",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
        LevelBar(levelDb)
        if (muted) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Aucun son ne parvient au micro. S'il est coupé dans les réglages rapides du téléphone, " +
                    "réactivez-le.",
                color = Warning,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun NoteDisplay(midi: Int?, notation: Notation, color: Color) {
    val description = midi?.let { "Note ${spokenNoteLabel(it, notation)}" } ?: "Aucune note"
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            midi?.let { pitchClassName(it, notation) } ?: NO_VALUE,
            color = color,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.alignByBaseline()
        )
        if (midi != null) {
            Text(
                octaveOf(midi).toString(),
                color = color,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

/** Arc gauge from −50 to +50 cents with the ±5 cents zone in green and an animated needle. */
@Composable
private fun TuningGauge(cents: Double?, live: Boolean, description: String) {
    val target = (cents ?: 0.0).coerceIn(-GAUGE_CENTS, GAUGE_CENTS).toFloat()
    val needle by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "aiguille"
    )
    val inTune = cents != null && tuningStatus(cents) == TuningStatus.InTune
    val needleColor = when {
        cents == null -> Navy
        !live -> TextSecondary
        inTune -> Success
        else -> Accent
    }
    val halfAngle = Math.toRadians(GAUGE_HALF_ANGLE_DEG)
    val sinHalf = sin(halfAngle).toFloat()
    val cosHalf = cos(halfAngle).toFloat()
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(GaugeHeight)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        val radius: Dp = min((maxWidth / 2 - GaugeStroke) / sinHalf, GaugeHeight - GaugePivotInset - GaugeStroke)
        val endX: Dp = maxWidth / 2 - radius * sinHalf
        val endY: Dp = GaugeHeight - GaugePivotInset - radius * cosHalf
        Canvas(Modifier.fillMaxSize()) {
            val r = radius.toPx()
            val stroke = GaugeStroke.toPx()
            val pivot = Offset(size.width / 2f, size.height - GaugePivotInset.toPx())
            val topLeft = Offset(pivot.x - r, pivot.y - r)
            val arcSize = Size(2f * r, 2f * r)
            val half = GAUGE_HALF_ANGLE_DEG.toFloat()
            drawArc(
                color = Navy,
                startAngle = -90f - half,
                sweepAngle = 2f * half,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val zone = (IN_TUNE_CENTS / GAUGE_CENTS * GAUGE_HALF_ANGLE_DEG).toFloat()
            drawArc(
                color = if (live && cents != null) Success else Success.copy(alpha = 0.45f),
                startAngle = -90f - zone,
                sweepAngle = 2f * zone,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            for (mark in -50..50 step 10) {
                val angle = Math.toRadians(mark / GAUGE_CENTS * GAUGE_HALF_ANGLE_DEG)
                val major = mark % 50 == 0
                val outer = r - stroke / 2f - 3.dp.toPx()
                val inner = outer - (if (major) 10.dp else 6.dp).toPx()
                drawLine(
                    color = if (major) Color.White.copy(alpha = 0.7f) else TextSecondary.copy(alpha = 0.5f),
                    start = pivot + polar(inner, angle),
                    end = pivot + polar(outer, angle),
                    strokeWidth = (if (major) 2.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }
            if (cents != null) {
                val angle = Math.toRadians(needle / GAUGE_CENTS * GAUGE_HALF_ANGLE_DEG)
                drawLine(
                    color = needleColor,
                    start = pivot,
                    end = pivot + polar(r - stroke / 2f - 2.dp.toPx(), angle),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = needleColor, radius = 7.dp.toPx(), center = pivot)
        }
        GaugeLabel("−50", centerX = endX, top = endY + GaugeStroke)
        GaugeLabel("+50", centerX = maxWidth - endX, top = endY + GaugeStroke)
    }
}

@Composable
private fun GaugeLabel(text: String, centerX: Dp, top: Dp) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 12.sp,
        style = Tabular,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .offset(x = centerX - GaugeLabelWidth / 2, y = top)
            .width(GaugeLabelWidth)
    )
}

/** Point at [length] from the pivot, [angle] radians clockwise from straight up. */
private fun polar(length: Float, angle: Double): Offset =
    Offset((length * sin(angle)).toFloat(), (-length * cos(angle)).toFloat())

@Composable
private fun LevelBar(levelDb: Double) {
    val fraction = ((levelDb - LEVEL_MIN_DB) / (LEVEL_MAX_DB - LEVEL_MIN_DB)).toFloat().coerceIn(0f, 1f)
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Niveau du micro", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Navy)
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(if (levelDb >= GATE_OPEN_DB) Accent else TextSecondary)
                )
            }
        }
    }
}

@Composable
private fun InstrumentCard(
    instrument: Instrument,
    notation: Notation,
    selectedString: Int?,
    manual: Boolean,
    onSelectInstrument: (Instrument) -> Unit,
    onSelectString: (Int) -> Unit,
    onAuto: () -> Unit
) {
    SurvieCard {
        Text("Instrument", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InstrumentSelector(selected = instrument, notation = notation, onSelect = onSelectInstrument)
        Spacer(Modifier.height(12.dp))
        if (instrument.isChromatic) {
            Text(
                "Toutes les notes sont reconnues. Choisissez un instrument pour accorder corde par corde.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        } else {
            val labelSize = if (instrument.strings.size > 4) 14.sp else 16.sp
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                instrument.strings.forEachIndexed { index, midi ->
                    StringButton(
                        label = noteLabel(midi, notation),
                        number = instrument.stringNumber(index),
                        description = "Corde ${instrument.stringNumber(index)}, ${spokenNoteLabel(midi, notation)}",
                        selected = index == selectedString,
                        fontSize = labelSize,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectString(index) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (manual) {
                        "Corde choisie à la main. Le diapason joue sa note."
                    } else {
                        "Corde détectée automatiquement. Touchez une corde pour la choisir et l'entendre au diapason."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (manual) {
                    Spacer(Modifier.width(8.dp))
                    ChoiceChip(label = "Auto", selected = false, onClick = onAuto)
                }
            }
        }
    }
}

@Composable
private fun InstrumentSelector(selected: Instrument, notation: Notation, onSelect: (Instrument) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(ButtonShape)
                .background(Navy)
                .clickable(role = Role.Button, onClickLabel = "Choisir l'instrument") { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(selected.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (!selected.isChromatic) {
                    Text(tuningSummary(selected, notation), color = TextSecondary, fontSize = 13.sp)
                }
            }
            Canvas(
                Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
            ) { drawChevron(Chevron.Down, TextSecondary) }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            INSTRUMENTS.forEach { instrument ->
                val isSelected = instrument.id == selected.id
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                instrument.name,
                                color = if (isSelected) Accent else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                if (instrument.isChromatic) "Toutes les notes" else tuningSummary(instrument, notation),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    },
                    onClick = {
                        onSelect(instrument)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StringButton(
    label: String,
    number: Int,
    description: String,
    selected: Boolean,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(ButtonShape)
            .background(if (selected) Accent else Navy)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            number.toString(),
            color = if (selected) Bg else TextSecondary,
            fontSize = 11.sp,
            maxLines = 1
        )
        Text(
            label,
            color = if (selected) Bg else Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ToneCard(
    midi: Int,
    notation: Notation,
    referenceHz: Int,
    playing: Boolean,
    rich: Boolean,
    error: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
    onRichChange: (Boolean) -> Unit
) {
    SurvieCard {
        Text("Diapason", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Jouez une note de référence pour accorder à l'oreille.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundChevronButton(
                chevron = Chevron.Left,
                description = "Note précédente",
                enabled = midi > MIN_TONE_MIDI,
                onClick = onPrevious
            )
            Column(
                Modifier
                    .weight(1f)
                    .clearAndSetSemantics {
                        contentDescription = "Note du diapason : ${spokenNoteLabel(midi, notation)}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    noteLabel(midi, notation),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    formatFrequency(frequencyOf(midi, referenceHz.toDouble())),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    style = Tabular
                )
            }
            RoundChevronButton(
                chevron = Chevron.Right,
                description = "Note suivante",
                enabled = midi < MAX_TONE_MIDI,
                onClick = onNext
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = ButtonShape,
            colors = if (playing) secondaryButtonColors() else primaryButtonColors()
        ) {
            val color = LocalContentColor.current
            Canvas(Modifier.size(14.dp)) {
                if (playing) {
                    drawRect(
                        color = color,
                        topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
                        size = Size(size.width * 0.8f, size.height * 0.8f)
                    )
                } else {
                    val triangle = Path().apply {
                        moveTo(size.width * 0.15f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(size.width * 0.15f, size.height)
                        close()
                    }
                    drawPath(triangle, color = color)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (playing) "Arrêter" else "Jouer ${noteLabel(midi, notation)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Timbre", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                label = "Sinus pur",
                selected = !rich,
                modifier = Modifier.weight(1f),
                onClick = { onRichChange(false) }
            )
            ChoiceChip(
                label = "Riche (harmoniques)",
                selected = rich,
                modifier = Modifier.weight(1f),
                onClick = { onRichChange(true) }
            )
        }
        if (!rich && midi < LOW_TONE_MIDI) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Note grave : peu audible sur le haut-parleur du téléphone. " +
                    "Choisissez le timbre riche ou utilisez un casque.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        if (error) {
            Spacer(Modifier.height(8.dp))
            Text("Sortie audio indisponible : impossible de jouer la note.", color = Danger, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingsCard(
    referenceHz: Int,
    notation: Notation,
    onReferenceChange: (Int) -> Unit,
    onNotationChange: (Notation) -> Unit
) {
    SurvieCard {
        Text("Réglages", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("La de référence (La4)", color = TextSecondary, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundTextButton(
                text = "−",
                description = "Baisser le La de référence d'un hertz",
                enabled = referenceHz > MIN_REFERENCE_HZ,
                onClick = { onReferenceChange(referenceHz - 1) }
            )
            Text(
                formatReference(referenceHz),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            RoundTextButton(
                text = "+",
                description = "Monter le La de référence d'un hertz",
                enabled = referenceHz < MAX_REFERENCE_HZ,
                onClick = { onReferenceChange(referenceHz + 1) }
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REFERENCE_PRESETS.forEach { hz ->
                ChoiceChip(
                    label = hz.toString(),
                    selected = hz == referenceHz,
                    modifier = Modifier.weight(1f),
                    onClick = { onReferenceChange(hz) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "De $MIN_REFERENCE_HZ à $MAX_REFERENCE_HZ${NBSP}Hz. La norme est 440${NBSP}Hz ; " +
                "442${NBSP}Hz est courant en orchestre, 415${NBSP}Hz en musique baroque.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        Text("Notation", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                label = "Do Ré Mi",
                selected = notation == Notation.French,
                modifier = Modifier.weight(1f),
                onClick = { onNotationChange(Notation.French) }
            )
            ChoiceChip(
                label = "C D E",
                selected = notation == Notation.English,
                modifier = Modifier.weight(1f),
                onClick = { onNotationChange(Notation.English) }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Octaves en numérotation internationale : La4 = 440${NBSP}Hz, Do4 = do central du piano. " +
                "Le solfège français traditionnel les appelle La3 et Do3.",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun TipsCard() {
    SurvieCard {
        Text("Conseils", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        listOf(
            "Placez le téléphone près de l'instrument, dans un endroit calme.",
            "Jouez une seule corde à la fois et laissez-la sonner.",
            "Accordez en montant vers la note : si la corde est trop haute, détendez-la " +
                "sous la note puis remontez.",
            "La note devient verte quand l'écart est de 5${NBSP}cents ou moins."
        ).forEach { tip ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•", color = Accent, fontSize = 14.sp, modifier = Modifier.width(16.dp))
                Text(tip, color = TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PermissionCard(permanentlyDenied: Boolean, onClick: () -> Unit) {
    SurvieCard {
        Text(
            "Accès au micro nécessaire",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied) {
                "L'accès au micro a été refusé. Activez-le dans les paramètres de l'application, " +
                    "rubrique Autorisations > Micro. Le diapason reste utilisable."
            } else {
                "L'accordeur écoute l'instrument avec le micro du téléphone pour mesurer la note jouée. " +
                    "Aucun son n'est enregistré ni envoyé."
            },
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = ButtonShape,
            colors = primaryButtonColors()
        ) {
            Text(
                if (permanentlyDenied) "Ouvrir les paramètres" else "Autoriser",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NoMicrophoneCard() {
    SurvieCard {
        Text(
            "Aucun micro détecté sur cet appareil.",
            color = Danger,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "L'accordage automatique est indisponible, mais le diapason ci-dessous reste utilisable.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun MicErrorCard(message: String, onRetry: () -> Unit) {
    SurvieCard {
        Text(message, color = Danger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Fermez l'application qui utilise peut-être le micro (appel, enregistreur), puis réessayez.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = ButtonShape,
            colors = primaryButtonColors()
        ) {
            Text("Réessayer", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .background(if (selected) Accent else Navy)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Bg else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            style = Tabular
        )
    }
}

@Composable
private fun RoundChevronButton(chevron: Chevron, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(Navy)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) { drawChevron(chevron, Color.White) }
    }
}

@Composable
private fun RoundTextButton(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(Navy)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

private fun DrawScope.drawChevron(chevron: Chevron, color: Color) {
    val w = size.width
    val h = size.height
    val path = Path()
    when (chevron) {
        Chevron.Left -> {
            path.moveTo(w * 0.62f, h * 0.22f)
            path.lineTo(w * 0.36f, h * 0.5f)
            path.lineTo(w * 0.62f, h * 0.78f)
        }
        Chevron.Right -> {
            path.moveTo(w * 0.38f, h * 0.22f)
            path.lineTo(w * 0.64f, h * 0.5f)
            path.lineTo(w * 0.38f, h * 0.78f)
        }
        Chevron.Down -> {
            path.moveTo(w * 0.22f, h * 0.38f)
            path.lineTo(w * 0.5f, h * 0.64f)
            path.lineTo(w * 0.78f, h * 0.38f)
        }
    }
    drawPath(
        path,
        color = color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun SurvieCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun primaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Accent,
    contentColor = Bg,
    disabledContainerColor = Navy,
    disabledContentColor = TextSecondary
)

@Composable
private fun secondaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Navy,
    contentColor = Color.White,
    disabledContainerColor = Navy.copy(alpha = 0.5f),
    disabledContentColor = TextSecondary.copy(alpha = 0.4f)
)

private fun readMicPermission(context: Context, deniedOnce: Boolean): MicPermission {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return MicPermission.Granted
    }
    // After a denial, the rationale flag turns false only when the system will no longer show the dialog.
    val activity = context.findActivity()
    val blocked = deniedOnce && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    return if (blocked) MicPermission.PermanentlyDenied else MicPermission.Denied
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
