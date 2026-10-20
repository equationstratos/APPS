package com.survival.whistle

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning as WarningGlyph
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.ln
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(16.dp)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val status by SignalHub.status.collectAsStateWithLifecycle()
    val options by SignalHub.options.collectAsStateWithLifecycle()
    val flashAvailable = remember { hasFlash(context) }
    var startFailed by remember { mutableStateOf(false) }

    // Clock of the running status; it only ticks while the screen is visible.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val running = status.mode != null
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(running, lifecycleOwner) {
        if (running) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now = SystemClock.elapsedRealtime()
                    delay(100)
                }
            }
        }
    }

    // Notifications (Android 13+): only needed for the "Arrêter" action, the signal plays anyway.
    val notificationsNeedPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val activity = remember(context) { context.findActivity() }
    // True while the system still shows its permission dialog (refused once, not "never ask again").
    val canAskNotifications = {
        notificationsNeedPermission && activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    }
    var notificationsOn by remember { mutableStateOf(notificationsEnabled(context)) }
    var notificationsAsked by remember { mutableStateOf(SignalHub.notificationsAsked(context)) }
    var canAskAgain by remember { mutableStateOf(canAskNotifications()) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = notificationsEnabled(context)
        canAskAgain = canAskNotifications()
    }
    LifecycleResumeEffect(Unit) {
        notificationsOn = notificationsEnabled(context)
        canAskAgain = canAskNotifications()
        onPauseOrDispose { }
    }
    val askNotificationsOnce = {
        if (notificationsNeedPermission && !notificationsOn && !notificationsAsked) {
            notificationsAsked = true
            SignalHub.markNotificationsAsked(context)
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val toggle: (SignalMode) -> Unit = { mode ->
        if (status.mode == mode && !status.hold) {
            SignalHub.stop(context)
        } else {
            startFailed = !SignalHub.start(context, mode, hold = false)
            if (!startFailed) askNotificationsOnce()
        }
    }
    val stop = { SignalHub.stop(context) }
    val holdStart = { startFailed = !SignalHub.start(context, SignalMode.Whistle, hold = true) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Sifflet", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Signaux sonores puissants pour se faire repérer, même écran éteint.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        HearingWarning()
        StatusCard(status, now, startFailed, onStop = stop)
        WhistleCard(status, onToggle = { toggle(SignalMode.Whistle) }, onHoldStart = holdStart, onHoldEnd = stop)
        SignalMode.entries.filter { it != SignalMode.Whistle }.forEach { mode ->
            ModeCard(mode, status, now, onToggle = { toggle(mode) })
        }
        OptionsCard(options, status, flashAvailable, onChange = { SignalHub.setOptions(context, it) })
        if (!notificationsOn && (!notificationsNeedPermission || notificationsAsked)) {
            NotificationCard(canAskAgain) {
                if (canAskAgain) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            }
        }
        InfoCard()
    }
}

@Composable
private fun HearingWarning() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .border(1.dp, Warning, CardShape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.WarningGlyph, contentDescription = "Attention", tint = Warning, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Ne collez pas le haut-parleur à l'oreille : risque pour l'audition.",
            color = Color.White,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun StatusCard(status: SignalStatus, now: Long, startFailed: Boolean, onStop: () -> Unit) {
    val mode = status.mode
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (mode == null) {
            Text("Prêt", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Touchez un signal pour le lancer. Il continue écran éteint, et la notification permet de l'arrêter.",
                color = TextSecondary,
                fontSize = 14.sp
            )
            if (startFailed || status.audioFailed) {
                Text("Impossible de lancer le son. Réessayez.", color = Danger, fontSize = 14.sp)
            }
        } else {
            val elapsed = (now - status.startedAt).coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Success)
                )
                Spacer(Modifier.width(8.dp))
                Text("En cours", color = Success, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(mode.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (status.hold) "Relâchez le bouton pour arrêter." else mode.statusAt(elapsed),
                color = Accent,
                fontSize = 16.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Durée", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        formatClock(elapsed),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        style = Tabular
                    )
                }
                if (status.autoStopAt > 0) {
                    Column(Modifier.weight(1f)) {
                        Text("Arrêt automatique", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            formatCountdown(status.autoStopAt - now),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            style = Tabular
                        )
                    }
                }
            }
            if (!status.hold) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Accent)
                        .clickable(role = Role.Button, onClick = onStop),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StopSquare(Bg, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Arrêter", color = Bg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WhistleCard(status: SignalStatus, onToggle: () -> Unit, onHoldStart: () -> Unit, onHoldEnd: () -> Unit) {
    val mode = SignalMode.Whistle
    val active = status.mode == mode
    val continuous = active && !status.hold
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (active) Navy else Card)
            .border(2.dp, if (active) Accent else Color.Transparent, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeTexts(mode)
        PatternStrip(mode, progress = null)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HoldPad(
                Modifier.weight(1f),
                enabled = status.mode == null || status.hold,
                onHoldStart = onHoldStart,
                onHoldEnd = onHoldEnd
            )
            Column(
                Modifier
                    .weight(1f)
                    .height(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Accent)
                    .clickable(
                        onClickLabel = if (continuous) "Arrêter le sifflet" else "Siffler en continu",
                        role = Role.Button,
                        onClick = onToggle
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                if (continuous) {
                    StopSquare(Bg, 22.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Bg, modifier = Modifier.size(32.dp))
                }
                Text(
                    if (continuous) "Arrêter" else "En continu",
                    color = Bg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** How long a touch on the hold pad must stay still before it whistles (a scroll moves sooner). */
private const val HOLD_SETTLE_MS = 100L

/** Length of the short blast given by a quick tap on the hold pad. */
private const val TAP_BLAST_MS = 500L

private enum class PadTouch { Hold, Tap, Scroll }

/**
 * Whistles while pressed. A touch that moves during its first [HOLD_SETTLE_MS] is a scroll and is
 * left to the page, so scrolling over the pad never makes a sound. A touch that stays still
 * whistles until the finger is lifted (the gesture then belongs to the pad: sliding the finger
 * keeps whistling), and a quick tap gives a short blast. The pad is inert while another signal
 * plays, so that a stray touch cannot replace a long distress code and then cut it at the release.
 */
@Composable
private fun HoldPad(modifier: Modifier, enabled: Boolean, onHoldStart: () -> Unit, onHoldEnd: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    // Number of the last touch that made a sound: a pending end of tap blast only ends its own sound.
    var soundTouch by remember { mutableIntStateOf(0) }
    val isEnabled by rememberUpdatedState(enabled)
    val start by rememberUpdatedState(onHoldStart)
    val end by rememberUpdatedState(onHoldEnd)
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) Accent else Bg)
            .border(2.dp, if (enabled) Accent else Navy, RoundedCornerShape(16.dp))
            .clearAndSetSemantics {
                contentDescription = if (enabled) {
                    "Siffler tant que le bouton est maintenu"
                } else {
                    "Sifflet à maintenir, indisponible pendant un autre signal"
                }
                role = Role.Button
                if (!enabled) disabled()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!isEnabled) return@awaitEachGesture
                    val kind = withTimeoutOrNull(HOLD_SETTLE_MS) {
                        var result: PadTouch? = null
                        while (result == null) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            result = when {
                                change == null -> PadTouch.Scroll
                                change.isConsumed -> PadTouch.Scroll
                                !change.pressed -> PadTouch.Tap
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop ->
                                    PadTouch.Scroll
                                else -> null
                            }
                        }
                        result
                    } ?: PadTouch.Hold
                    if (kind == PadTouch.Scroll) return@awaitEachGesture
                    val touch = soundTouch + 1
                    soundTouch = touch
                    pressed = true
                    start()
                    if (kind == PadTouch.Tap) {
                        scope.launch {
                            try {
                                delay(TAP_BLAST_MS)
                            } finally {
                                if (soundTouch == touch) {
                                    pressed = false
                                    end()
                                }
                            }
                        }
                    } else {
                        try {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            pressed = false
                            end()
                        }
                    }
                }
            }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            when {
                !enabled -> "Indisponible"
                pressed -> "Sifflement…"
                else -> "Maintenir"
            },
            color = when {
                !enabled -> TextSecondary
                pressed -> Bg
                else -> Color.White
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            if (enabled) "pour siffler" else "pendant un signal",
            color = if (pressed) Bg else TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ModeCard(mode: SignalMode, status: SignalStatus, now: Long, onToggle: () -> Unit) {
    val active = status.mode == mode && !status.hold
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (active) Navy else Card)
            .border(2.dp, if (active) Accent else Color.Transparent, CardShape)
            .clickable(onClickLabel = if (active) "Arrêter" else "Démarrer", role = Role.Button, onClick = onToggle)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { ModeTexts(mode) }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                if (active) {
                    StopSquare(Bg, 18.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Bg, modifier = Modifier.size(30.dp))
                }
            }
        }
        val progress = if (active && mode.cycleMs >= 4_000) {
            ((now - status.startedAt).coerceAtLeast(0) % mode.cycleMs).toFloat() / mode.cycleMs
        } else {
            null
        }
        PatternStrip(mode, progress)
    }
}

@Composable
private fun ModeTexts(mode: SignalMode) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(mode.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(mode.purpose, color = Color.White, fontSize = 15.sp)
        Text(mode.rhythm, color = TextSecondary, fontSize = 13.sp)
    }
}

/** Timeline of the cycle: pitch of each tone (higher = more acute), gaps = silences. */
@Composable
private fun PatternStrip(mode: SignalMode, progress: Float?) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val stroke = 4.dp.toPx()
        val repeats = (4_000 / mode.cycleMs).coerceIn(1, 10)
        val total = (mode.cycleMs * repeats).toFloat()
        val left = stroke / 2
        val span = size.width - stroke
        val bottom = size.height - stroke / 2
        fun x(ms: Int): Float = left + ms / total * span
        fun y(hz: Double): Float {
            val t = (ln(hz / 800.0) / ln(4_000.0 / 800.0)).toFloat().coerceIn(0f, 1f)
            return bottom - t * (size.height - stroke)
        }
        drawLine(Navy, Offset(left, bottom), Offset(left + span, bottom), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val path = Path()
        var lastEnd = -1
        for (r in 0 until repeats) {
            for (tone in mode.toneSpans) {
                val start = tone.startMs + r * mode.cycleMs
                val end = tone.endMs + r * mode.cycleMs
                if (start == lastEnd) path.lineTo(x(start), y(tone.startHz)) else path.moveTo(x(start), y(tone.startHz))
                path.lineTo(x(end), y(tone.endHz))
                lastEnd = end
            }
        }
        drawPath(path, Accent, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (progress != null) {
            val px = left + progress.coerceIn(0f, 1f) * span
            drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun OptionsCard(
    options: SignalOptions,
    status: SignalStatus,
    flashAvailable: Boolean,
    onChange: (SignalOptions) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Options", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val volumeBlocked = options.maxVolume && status.mode != null && status.volumeBlocked
        SwitchRow(
            title = "Volume au maximum",
            detail = if (volumeBlocked) {
                "Refusé par le mode «\u00A0Ne pas déranger\u00A0»\u00A0: montez le volume des alarmes avec les touches."
            } else {
                "Monte le volume des alarmes pendant le signal, puis rétablit votre réglage."
            },
            detailColor = if (volumeBlocked) Warning else TextSecondary,
            checked = options.maxVolume,
            enabled = true
        ) { onChange(options.copy(maxVolume = it)) }
        val torchBusy = flashAvailable && options.flash && status.mode != null && status.torch == TorchState.Unavailable
        SwitchRow(
            title = "Clignoter la lampe avec le son",
            detail = when {
                !flashAvailable -> "Aucune lampe sur cet appareil."
                torchBusy -> "Lampe indisponible\u00A0: elle est peut-être utilisée par une autre application."
                else -> "La lampe s'allume au rythme du signal, visible de loin la nuit."
            },
            detailColor = if (torchBusy) Warning else TextSecondary,
            checked = options.flash && flashAvailable,
            enabled = flashAvailable
        ) { onChange(options.copy(flash = it)) }
        Spacer(Modifier.height(8.dp))
        Text("Arrêt automatique", color = Color.White, fontSize = 16.sp)
        Text(
            "Coupe le signal après la durée choisie, pour économiser la batterie.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AUTO_STOP_CHOICES.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { minutes ->
                        val selected = minutes == options.autoStopMinutes
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Accent else Navy)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onChange(options.copy(autoStopMinutes = minutes)) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                autoStopLabel(minutes),
                                color = if (selected) Bg else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    detailColor: Color,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (enabled) Color.White else TextSecondary, fontSize = 16.sp)
            Text(detail, color = detailColor, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
                checkedBorderColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Navy,
                uncheckedBorderColor = TextSecondary
            )
        )
    }
}

@Composable
private fun NotificationCard(canAsk: Boolean, onFix: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("Notifications désactivées", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Le signal fonctionne quand même, mais sans le bouton «\u00A0Arrêter\u00A0» dans le volet des notifications.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Navy)
                .clickable(role = Role.Button, onClick = onFix),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (canAsk) "Autoriser les notifications" else "Ouvrir les paramètres",
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("Bon à savoir", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Bullet("Si vous avez du réseau, appelez d'abord le 112, numéro d'urgence européen.")
        Bullet(
            "Détresse en montagne\u00A0: 6 signaux par minute, un toutes les 10\u00A0s, puis 1\u00A0minute de " +
                "silence pour écouter. Recommencez jusqu'à l'arrivée des secours."
        )
        Bullet("Réponse\u00A0: 3 signaux par minute, un toutes les 20\u00A0s, signifient «\u00A0message reçu\u00A0».")
        Bullet("La nuit, une lumière au même rythme est aussi un signal de détresse reconnu.")
        Bullet("Placez-vous à découvert, haut-parleur dégagé\u00A0: un son aigu porte loin mais traverse mal les obstacles.")
        Bullet("Éteignez l'écran\u00A0: le signal continue et la batterie dure plus longtemps.")
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun StopSquare(color: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openNotificationSettings(context: Context) {
    val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    for (intent in listOf(notificationSettings, appDetails, Intent(Settings.ACTION_SETTINGS))) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next, more generic screen.
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
