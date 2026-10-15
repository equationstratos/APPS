package com.survival.chrono

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay

internal enum class TimerPhase { Idle, Running, Paused, Ringing }

internal const val DEFAULT_TIMER_SECONDS = 5 * 60
private const val MAX_TIMER_SECONDS = 24 * 3_600 - 1
private const val ALARM_MAX_MS = 60_000L
private const val REPEAT_DELAY_MS = 450L
private const val REPEAT_INTERVAL_MS = 90L
private val PRESET_MINUTES = listOf(1, 3, 5, 10, 15, 30)

/** Countdown whose end is an elapsedRealtime target, so it stays exact while paused, rotated or backgrounded. */
@Stable
internal class CountdownTimer(
    hours: Int,
    minutes: Int,
    seconds: Int,
    phase: TimerPhase = TimerPhase.Idle,
    endAt: Long = 0L,
    total: Long = 0L,
    pausedRemaining: Long = 0L
) {
    var hours by mutableIntStateOf(hours)
        private set
    var minutes by mutableIntStateOf(minutes)
        private set
    var seconds by mutableIntStateOf(seconds)
        private set
    var phase by mutableStateOf(phase)
        private set
    private var endAt by mutableLongStateOf(endAt)

    /** Duration of the countdown in progress, used for the progress ring. */
    var total by mutableLongStateOf(total)
        private set
    private var pausedRemaining by mutableLongStateOf(pausedRemaining)

    /** Start of the current run, so the readout is exact before the UI clock's first tick. */
    private var runStartedAt by mutableLongStateOf(0L)

    val configuredSeconds: Int get() = hours * 3_600 + minutes * 60 + seconds
    val configuredMs: Long get() = configuredSeconds * 1_000L

    /** The alarm rings at most this long after the countdown reached zero. */
    val alarmDeadline: Long get() = endAt + ALARM_MAX_MS

    fun addHours(delta: Int) {
        if (phase == TimerPhase.Idle) hours = (hours + delta).mod(24)
    }

    fun addMinutes(delta: Int) {
        if (phase == TimerPhase.Idle) minutes = (minutes + delta).mod(60)
    }

    fun addSeconds(delta: Int) {
        if (phase == TimerPhase.Idle) seconds = (seconds + delta).mod(60)
    }

    fun applyPreset(presetMinutes: Int) {
        if (phase != TimerPhase.Idle) return
        hours = presetMinutes / 60
        minutes = presetMinutes % 60
        seconds = 0
    }

    fun remainingAt(now: Long): Long = when (phase) {
        TimerPhase.Idle -> configuredMs
        TimerPhase.Running -> (endAt - maxOf(now, runStartedAt)).coerceIn(0L, total)
        TimerPhase.Paused -> pausedRemaining
        TimerPhase.Ringing -> 0L
    }

    /** Returns true when the countdown actually started. */
    fun start(): Boolean {
        val duration = configuredMs
        if (phase != TimerPhase.Idle || duration <= 0L) return false
        total = duration
        runStartedAt = SystemClock.elapsedRealtime()
        endAt = runStartedAt + duration
        phase = TimerPhase.Running
        return true
    }

    fun pause() {
        if (phase != TimerPhase.Running) return
        val left = endAt - SystemClock.elapsedRealtime()
        if (left <= 0L) {
            phase = TimerPhase.Ringing
        } else {
            pausedRemaining = left
            phase = TimerPhase.Paused
        }
    }

    fun resume() {
        if (phase != TimerPhase.Paused) return
        runStartedAt = SystemClock.elapsedRealtime()
        endAt = runStartedAt + pausedRemaining
        phase = TimerPhase.Running
    }

    /** Back to the duration editor; also silences the alarm. */
    fun reset() {
        phase = TimerPhase.Idle
    }

    fun checkExpired(now: Long) {
        if (phase == TimerPhase.Running && now >= endAt) phase = TimerPhase.Ringing
    }

    companion object {
        fun fromSeconds(totalSeconds: Int): CountdownTimer {
            val s = totalSeconds.coerceIn(0, MAX_TIMER_SECONDS)
            return CountdownTimer(hours = s / 3_600, minutes = s / 60 % 60, seconds = s % 60)
        }

        val Saver: Saver<CountdownTimer, Any> = listSaver<CountdownTimer, Any>(
            save = {
                listOf(it.hours, it.minutes, it.seconds, it.phase.ordinal, it.endAt, it.total, it.pausedRemaining)
            },
            restore = {
                CountdownTimer(
                    hours = it[0] as Int,
                    minutes = it[1] as Int,
                    seconds = it[2] as Int,
                    phase = TimerPhase.entries[it[3] as Int],
                    endAt = it[4] as Long,
                    total = it[5] as Long,
                    pausedRemaining = it[6] as Long
                )
            }
        )
    }
}

@Composable
internal fun TimerPane(
    timer: CountdownTimer,
    nowMs: () -> Long,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val ringDiameter = minOf(maxWidth, viewportHeight - 140.dp, 300.dp).coerceAtLeast(180.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (timer.phase == TimerPhase.Idle) {
                TimerSetup(timer, onStart)
            } else {
                TimerProgress(timer, nowMs, ringDiameter)
            }
        }
    }
}

@Composable
private fun TimerSetup(timer: CountdownTimer, onStart: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Stepper(value = timer.hours, unit = "h", target = "les heures", onStep = timer::addHours)
        Stepper(value = timer.minutes, unit = "min", target = "les minutes", onStep = timer::addMinutes)
        Stepper(value = timer.seconds, unit = "s", target = "les secondes", onStep = timer::addSeconds)
    }
    Spacer(Modifier.height(20.dp))
    Text("Préréglages", color = TextSecondary, fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    PresetGrid(timer)
    Spacer(Modifier.height(20.dp))
    SurvieButton(
        text = "Démarrer",
        onClick = onStart,
        enabled = timer.configuredMs > 0L,
        minHeight = 64.dp,
        fontSize = 20.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Stepper(value: Int, unit: String, target: String, onStep: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RepeatingStepButton(symbol = "+", description = "Augmenter $target", onStep = { onStep(1) })
        Text(
            text = String.format(Locale.FRANCE, "%02d", value),
            color = Color.White,
            fontSize = 44.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Light,
            style = Tnum
        )
        Text(unit, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        RepeatingStepButton(symbol = "−", description = "Diminuer $target", onStep = { onStep(-1) })
    }
}

/** Steps once on tap and keeps stepping while held. */
@Composable
private fun RepeatingStepButton(symbol: String, description: String, onStep: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val currentOnStep by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) {
            repeated = false
            return@LaunchedEffect
        }
        delay(REPEAT_DELAY_MS)
        repeated = true
        while (true) {
            currentOnStep()
            delay(REPEAT_INTERVAL_MS)
        }
    }

    Button(
        // The release that ends an auto-repeat must not add one more step.
        onClick = { if (!repeated) currentOnStep() },
        modifier = Modifier
            .size(width = 72.dp, height = 48.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource
    ) {
        Text(symbol, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PresetGrid(timer: CountdownTimer) {
    val configured = timer.configuredSeconds
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESET_MINUTES.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { presetMinutes ->
                    val selected = configured == presetMinutes * 60
                    Button(
                        onClick = { timer.applyPreset(presetMinutes) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Navy,
                            contentColor = if (selected) Accent else Color.White
                        ),
                        border = if (selected) BorderStroke(2.dp, Accent) else null,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("$presetMinutes min", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerProgress(timer: CountdownTimer, nowMs: () -> Long, ringDiameter: Dp) {
    TimerRing(timer, nowMs, ringDiameter)
    Spacer(Modifier.height(28.dp))
    when (timer.phase) {
        TimerPhase.Ringing -> SurvieButton(
            text = "Arrêter l'alarme",
            onClick = timer::reset,
            containerColor = Danger,
            contentColor = Bg,
            minHeight = 72.dp,
            fontSize = 22.sp,
            modifier = Modifier.fillMaxWidth()
        )
        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = "Réinitialiser", onClick = timer::reset, modifier = Modifier.weight(1f))
            if (timer.phase == TimerPhase.Running) {
                SurvieButton(text = "Pause", onClick = timer::pause, modifier = Modifier.weight(1f))
            } else {
                SurvieButton(text = "Reprendre", onClick = timer::resume, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Reads the ticking clock in its own scope so only the ring and digits redraw each frame. */
@Composable
private fun TimerRing(timer: CountdownTimer, nowMs: () -> Long, diameter: Dp) {
    val phase = timer.phase
    val total = timer.total
    val remaining = timer.remainingAt(nowMs())
    val fraction = if (total > 0L) (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val ringing = phase == TimerPhase.Ringing
    val pulse = if (ringing) {
        val transition = rememberInfiniteTransition(label = "alarme")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 500), RepeatMode.Reverse),
            label = "pulsation"
        ).value
    } else {
        1f
    }
    val arcColor = when (phase) {
        TimerPhase.Ringing -> Danger
        TimerPhase.Paused -> Accent.copy(alpha = 0.5f)
        TimerPhase.Idle, TimerPhase.Running -> Accent
    }
    val caption = when (phase) {
        TimerPhase.Ringing -> "Temps écoulé !"
        TimerPhase.Paused -> "En pause"
        TimerPhase.Idle, TimerPhase.Running -> "sur ${formatCountdown(total, showHours = false)}"
    }

    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Navy,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            val sweep = if (ringing) 360f else 360f * fraction
            if (sweep > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = pulse,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FittedReadout(
                text = formatCountdown(remaining, showHours = total >= 3_600_000L),
                color = if (ringing) Danger else Color.White,
                maxGlyph = diameter * 0.22f,
                modifier = Modifier.width(diameter * 0.72f)
            )
            Text(
                text = caption,
                color = if (ringing) Danger else TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (ringing) FontWeight.SemiBold else FontWeight.Normal,
                style = Tnum
            )
        }
    }
}
