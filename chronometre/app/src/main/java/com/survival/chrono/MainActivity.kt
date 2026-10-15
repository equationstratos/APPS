package com.survival.chrono

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val FRAME_MS = 33L
private const val BACKGROUND_TICK_MS = 250L
private const val WAKE_LOCK_TAG = "chronometre:minuteur"
private const val WAKE_LOCK_MARGIN_MS = 5_000L
private const val PREFS = "survie"
private const val KEY_TIMER_SECONDS = "chrono_minuteur_secondes"
private const val TAB_STOPWATCH = 0
private const val TAB_TIMER = 1
private val TAB_LABELS = listOf("Chrono", "Minuteur")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Volume keys adjust the alarm volume, the only sound this app makes.
        volumeControlStream = AudioManager.STREAM_ALARM
        setContent { SurvieTheme { AppScreen() } }
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_STOPWATCH) }
    val stopwatch = rememberSaveable(saver = Stopwatch.Saver) { Stopwatch() }
    val timer = rememberSaveable(saver = CountdownTimer.Saver) {
        CountdownTimer.fromSeconds(prefs.getInt(KEY_TIMER_SECONDS, DEFAULT_TIMER_SECONDS))
    }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Driven by snapshotFlow rather than recomposition: Compose pauses recomposition while the
    // activity is stopped, but the countdown must still reach zero and ring in the background.
    LaunchedEffect(stopwatch, timer, lifecycle) {
        snapshotFlow { stopwatch.running || timer.phase == TimerPhase.Running }.collectLatest { active ->
            if (!active) return@collectLatest
            while (true) {
                val t = SystemClock.elapsedRealtime()
                now = t
                timer.checkExpired(t)
                val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                delay(if (visible) FRAME_MS else BACKGROUND_TICK_MS)
            }
        }
    }
    LaunchedEffect(timer, context) {
        val alarm = Alarm(context.applicationContext)
        snapshotFlow { timer.phase == TimerPhase.Ringing }.collectLatest { ringing ->
            if (ringing) {
                alarm.ringUntil(timer.alarmDeadline)
                timer.reset()
            }
        }
    }

    val ringing = timer.phase == TimerPhase.Ringing
    LaunchedEffect(ringing) {
        if (ringing) tab = TAB_TIMER
    }
    KeepScreenOn(stopwatch.running || timer.phase == TimerPhase.Running || ringing)
    KeepCpuAwake(timer.phase == TimerPhase.Running || ringing, timer)

    val nowMs = { now }
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Chronomètre",
            color = Accent,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        SegmentedControl(
            selected = tab,
            badges = listOf(
                if (stopwatch.running) Success else null,
                when (timer.phase) {
                    TimerPhase.Running -> Success
                    TimerPhase.Ringing -> Danger
                    TimerPhase.Idle, TimerPhase.Paused -> null
                }
            ),
            onSelect = { tab = it }
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (tab == TAB_TIMER) {
                TimerPane(
                    timer = timer,
                    nowMs = nowMs,
                    onStart = {
                        if (timer.start()) prefs.edit { putInt(KEY_TIMER_SECONDS, timer.configuredSeconds) }
                    }
                )
            } else {
                StopwatchPane(stopwatch = stopwatch, nowMs = nowMs)
            }
        }
    }
}

/** Two-option switch; a dot on the hidden tab tells that its tool is still running. */
@Composable
private fun SegmentedControl(selected: Int, badges: List<Color?>, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Navy)
            .padding(4.dp)
            .selectableGroup()
    ) {
        TAB_LABELS.forEachIndexed { index, label ->
            val isSelected = index == selected
            val badge = badges.getOrNull(index)
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Accent else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Bg else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (badge != null && !isSelected) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(badge, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Keeps the CPU running while the countdown runs or rings, so the end is still detected on time
 * after the user turns the screen off. The timeout covers the case where the app stays in the
 * background past the alarm, when no recomposition can change [active].
 */
@Composable
private fun KeepCpuAwake(active: Boolean, timer: CountdownTimer) {
    val context = LocalContext.current
    DisposableEffect(context, active) {
        val lock = if (active) {
            context.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                ?.apply {
                    setReferenceCounted(false)
                    val untilAlarmEnd = (timer.alarmDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    acquire(untilAlarmEnd + WAKE_LOCK_MARGIN_MS)
                }
        } else {
            null
        }
        onDispose { lock?.release() }
    }
}
