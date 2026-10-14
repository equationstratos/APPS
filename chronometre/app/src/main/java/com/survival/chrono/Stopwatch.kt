package com.survival.chrono

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_LAPS = 999
private const val MIN_LAPS_FOR_EXTREMES = 3

/** Stopwatch based on elapsedRealtime: accumulated time of past runs + time since the current start. */
@Stable
internal class Stopwatch(
    running: Boolean = false,
    startedAt: Long = 0L,
    accumulated: Long = 0L,
    laps: LongArray = LongArray(0)
) {
    var running by mutableStateOf(running)
        private set
    private var startedAt by mutableLongStateOf(startedAt)
    private var accumulated by mutableLongStateOf(accumulated)

    /** Cumulative total at each lap, oldest first. */
    var laps by mutableStateOf(laps)
        private set

    val isReset: Boolean get() = !running && accumulated == 0L && laps.isEmpty()
    val canLap: Boolean get() = running && laps.size < MAX_LAPS

    fun elapsedAt(now: Long): Long =
        if (running) accumulated + (now - startedAt).coerceAtLeast(0L) else accumulated

    fun start() {
        if (running) return
        startedAt = SystemClock.elapsedRealtime()
        running = true
    }

    fun pause() {
        if (!running) return
        accumulated = elapsedAt(SystemClock.elapsedRealtime())
        running = false
    }

    fun lap() {
        if (!canLap) return
        laps = laps + elapsedAt(SystemClock.elapsedRealtime())
    }

    fun reset() {
        running = false
        accumulated = 0L
        laps = LongArray(0)
    }

    companion object {
        val Saver: Saver<Stopwatch, Any> = listSaver<Stopwatch, Any>(
            save = { listOf(it.running, it.startedAt, it.accumulated, it.laps) },
            restore = {
                Stopwatch(
                    running = it[0] as Boolean,
                    startedAt = it[1] as Long,
                    accumulated = it[2] as Long,
                    laps = it[3] as LongArray
                )
            }
        )
    }
}

@Composable
internal fun StopwatchPane(stopwatch: Stopwatch, nowMs: () -> Long, modifier: Modifier = Modifier) {
    val laps = stopwatch.laps
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StopwatchReadout(stopwatch, nowMs)
            Spacer(Modifier.height(24.dp))
            StopwatchControls(stopwatch)
        }
        if (laps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            LapList(
                laps = laps,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

/** Reads the ticking clock in its own scope so only the digits recompose ~30 times per second. */
@Composable
private fun StopwatchReadout(stopwatch: Stopwatch, nowMs: () -> Long) {
    val elapsed = stopwatch.elapsedAt(nowMs())
    val laps = stopwatch.laps
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FittedReadout(
            text = formatStopwatch(elapsed),
            color = Color.White,
            maxGlyph = 76.dp,
            modifier = Modifier.fillMaxWidth()
        )
        if (laps.isNotEmpty()) {
            Text(
                text = "Tour ${laps.size + 1} · ${formatStopwatch(elapsed - laps.last())}",
                color = TextSecondary,
                fontSize = 16.sp,
                style = Tnum
            )
        }
    }
}

@Composable
private fun StopwatchControls(stopwatch: Stopwatch) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            stopwatch.running -> {
                SecondaryButton(
                    text = "Tour",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        stopwatch.lap()
                    },
                    enabled = stopwatch.canLap,
                    modifier = Modifier.weight(1f)
                )
                SurvieButton(text = "Pause", onClick = stopwatch::pause, modifier = Modifier.weight(1f))
            }
            stopwatch.isReset -> {
                SurvieButton(text = "Démarrer", onClick = stopwatch::start, modifier = Modifier.weight(1f))
            }
            else -> {
                SecondaryButton(text = "Réinitialiser", onClick = stopwatch::reset, modifier = Modifier.weight(1f))
                SurvieButton(text = "Reprendre", onClick = stopwatch::start, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LapList(laps: LongArray, modifier: Modifier = Modifier) {
    val extremes = remember(laps) { lapExtremes(laps) }
    val listState = rememberLazyListState()
    LaunchedEffect(laps.size) { listState.animateScrollToItem(0) }

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        LapRow(label = "Tour", lap = "Temps", total = "Total", lapColor = TextSecondary, header = true)
        HorizontalDivider(color = Navy)
        LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            items(count = laps.size) { position ->
                val index = laps.lastIndex - position
                val duration = lapDuration(laps, index)
                val color = when {
                    extremes == null -> Color.White
                    duration == extremes.first -> Success
                    duration == extremes.second -> Danger
                    else -> Color.White
                }
                LapRow(
                    label = "Tour ${index + 1}",
                    lap = formatStopwatch(duration),
                    total = formatStopwatch(laps[index]),
                    lapColor = color
                )
            }
        }
    }
}

@Composable
private fun LapRow(label: String, lap: String, total: String, lapColor: Color, header: Boolean = false) {
    val size = if (header) 13.sp else 15.sp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (header) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.8f),
            color = if (header) TextSecondary else Color.White,
            fontSize = size,
            maxLines = 1
        )
        Text(
            text = lap,
            modifier = Modifier.weight(1f),
            color = lapColor,
            fontSize = size,
            fontWeight = if (header) FontWeight.Normal else FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            style = Tnum
        )
        Text(
            text = total,
            modifier = Modifier.weight(1f),
            color = TextSecondary,
            fontSize = size,
            textAlign = TextAlign.End,
            maxLines = 1,
            style = Tnum
        )
    }
}

private fun lapDuration(laps: LongArray, index: Int): Long =
    laps[index] - if (index == 0) 0L else laps[index - 1]

/** Fastest and slowest lap durations, or null when there are too few laps or all are equal. */
private fun lapExtremes(laps: LongArray): Pair<Long, Long>? {
    if (laps.size < MIN_LAPS_FOR_EXTREMES) return null
    val durations = LongArray(laps.size) { lapDuration(laps, it) }
    val best = durations.min()
    val worst = durations.max()
    return if (best == worst) null else best to worst
}
