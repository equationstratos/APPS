package com.survival.pulse

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TICK_MS = 100L

/** State of the manual count, kept above the tabs. Times come from [SystemClock.elapsedRealtime]. */
internal class ManualUiState {
    var session by mutableStateOf(TapSession(TAP_DURATIONS_S.first() * 1000L))
        private set
    var nowMs by mutableLongStateOf(0L)
    var notice by mutableStateOf<String?>(null)
        private set
    var context by mutableStateOf(MeasureContext.NONE)
        private set
    private var savedId: Long? = null
    private var handled = false

    /** A count is in progress (the tabs are locked meanwhile). */
    val counting: Boolean get() = session.started && !session.isFinished(nowMs)

    val durationS: Int get() = (session.durationMs / 1000L).toInt()

    fun tap(now: Long) {
        if (session.isFinished(now)) return
        notice = null
        nowMs = now
        session = session.tap(now)
    }

    fun selectDuration(seconds: Int) {
        // Touching the duration already selected keeps the result on screen.
        if (counting || seconds * 1000L == session.durationMs) return
        session = TapSession(seconds * 1000L)
        clear()
    }

    fun reset() {
        session = TapSession(session.durationMs)
        clear()
    }

    /** The app left the foreground: an unfinished count is discarded. */
    fun interrupt() {
        if (!session.started || session.isFinished(SystemClock.elapsedRealtime())) return
        reset()
        notice = "Comptage interrompu : l'application est passée en arrière-plan. Recommencez."
    }

    /** Saves the result once, when the timer of the current count ends. */
    fun onFinished(store: HistoryStore) {
        if (handled || !session.started) return
        handled = true
        val rate = session.finalRate() ?: return
        savedId = store.add(HistoryEntry(System.currentTimeMillis(), rate.bpm.roundToInt(), context, MeasureMethod.MANUAL))
    }

    fun updateContext(value: MeasureContext, store: HistoryStore) {
        context = value
        savedId?.let { store.setContext(it, value) }
    }

    private fun clear() {
        savedId = null
        handled = false
        notice = null
        context = MeasureContext.NONE
    }
}

@Composable
internal fun ManualTab(state: ManualUiState, store: HistoryStore) {
    val session = state.session
    // Clock of the count: runs from the first tap to the end of the timer, then saves the result.
    LaunchedEffect(session) {
        if (!session.started) return@LaunchedEffect
        while (true) {
            val now = SystemClock.elapsedRealtime()
            state.nowMs = now
            if (session.isFinished(now)) break
            delay(session.remainingMs(now).coerceIn(1L, TICK_MS))
        }
        state.onFinished(store)
    }

    // A 30 s count outlasts the shortest screen timeouts: the screen stays on while counting.
    val hostView = LocalView.current
    val counting = state.counting
    DisposableEffect(hostView, counting) {
        hostView.keepScreenOn = counting
        onDispose { hostView.keepScreenOn = false }
    }

    val view = manualView(session, state.nowMs)

    SurvieCard {
        CardTitle("Compter au poignet ou au cou")
        BodyText(
            "Posez l'index et le majeur à l'intérieur du poignet, côté pouce, ou sur le côté du cou, " +
                "sous l'angle de la mâchoire (un seul côté, sans appuyer fort). N'utilisez pas le pouce : " +
                "il a son propre pouls."
        )
        BodyText("Touchez le cercle à chaque battement ressenti.", color = Color.White)
        Text("Durée du comptage", color = TextSecondary, fontSize = 14.sp)
        SegmentedControl(
            options = TAP_DURATIONS_S.map { "$it${NBSP}s" },
            selected = TAP_DURATIONS_S.indexOf(state.durationS).coerceAtLeast(0),
            onSelect = { state.selectDuration(TAP_DURATIONS_S[it]) },
            enabled = !counting,
        )
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TapTarget(view = view, onTap = { state.tap(SystemClock.elapsedRealtime()) })
            Text(
                text = view.status,
                color = if (view.tone == Tone.WARNING) Warning else Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = Tabular,
            )
            view.detail?.let {
                Text(it, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, style = Tabular)
            }
            view.rate?.let { rate ->
                Text(assessment(rate.bpm.roundToInt(), state.context), color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            }
            state.notice?.let {
                Text(it, color = Warning, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            }
        }
        if (session.started) {
            SurvieButton(
                text = "Recommencer",
                onClick = { state.reset() },
                modifier = Modifier.fillMaxWidth(),
                primary = view.finished,
            )
        }
    }

    if (view.finished && view.rate != null) {
        ContextCard(selected = state.context, onSelect = { state.updateContext(it, store) })
    }

    ReferenceCard()
}

/** Big round target tapped at each beat; the count reacts on touch-down, with a short flash. */
@Composable
private fun TapTarget(view: ManualView, onTap: () -> Unit) {
    val currentOnTap by rememberUpdatedState(onTap)
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val press = remember(scope, flash) {
        {
            currentOnTap()
            scope.launch {
                flash.snapTo(1f)
                flash.animateTo(0f, animationSpec = tween(durationMillis = 250))
            }
            Unit
        }
    }
    val description = when {
        view.finished && view.rate == null -> "Comptage terminé : pas assez de battements réguliers"
        view.finished -> "Comptage terminé : ${view.value} battements par minute"
        view.value == NO_VALUE -> "Zone de comptage. Touchez à chaque battement."
        else -> "Zone de comptage : ${view.value} ${view.caption}. Touchez à chaque battement."
    }
    Box(
        Modifier
            .size(220.dp)
            .background(Bg, CircleShape)
            .drawBehind { drawCircle(Accent.copy(alpha = 0.3f * flash.value)) }
            .pointerInput(Unit) { detectTapGestures(onPress = { press() }) }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = "Compter un battement") {
                    press()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ValueRing(
            value = view.value,
            caption = view.caption,
            progress = view.progress,
            tone = view.tone,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
