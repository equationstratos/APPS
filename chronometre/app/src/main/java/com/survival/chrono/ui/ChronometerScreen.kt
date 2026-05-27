package com.survival.chrono.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG = Color(0xFF1A1A2E)
private val ACCENT = Color(0xFF4A9FFF)
private val NAVY = Color(0xFF0F3460)

@Composable
fun ChronometerScreen(
    stopwatchMs: Long, timerMs: Long,
    isStopwatchRunning: Boolean, isTimerRunning: Boolean,
    lapTimes: List<Long>, selectedTab: Int,
    onTabChanged: (Int) -> Unit,
    onStopwatchStart: () -> Unit, onStopwatchStop: () -> Unit,
    onStopwatchReset: () -> Unit, onStopwatchLap: () -> Unit,
    onTimerStart: () -> Unit, onTimerPause: () -> Unit,
    onTimerReset: () -> Unit, onTimerDurationChanged: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BG).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Chronomètre", color = ACCENT, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            Btn("Chrono", selectedTab == 0, { onTabChanged(0) })
            Btn("Minuteur", selectedTab == 1, { onTabChanged(1) })
        }

        if (selectedTab == 0) {
            Text(formatTime(stopwatchMs), color = ACCENT, fontSize = 56.sp, fontWeight = FontWeight.Light)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
                Btn(if (isStopwatchRunning) "Stop" else "Start", true,
                    if (isStopwatchRunning) onStopwatchStop else onStopwatchStart)
                if (isStopwatchRunning) Btn("Tour", true, onStopwatchLap)
                Btn("Reset", true, onStopwatchReset)
            }
            if (lapTimes.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    .background(NAVY, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    itemsIndexed(lapTimes) { i, t ->
                        Row(Modifier.fillMaxWidth().padding(4.dp), Arrangement.SpaceBetween) {
                            Text("Tour ${i + 1}", color = Color.White, fontSize = 14.sp)
                            Text(formatTime(t), color = ACCENT, fontSize = 14.sp)
                        }
                    }
                }
            }
        } else {
            Text(formatTime(timerMs), color = ACCENT, fontSize = 56.sp, fontWeight = FontWeight.Light)
            if (!isTimerRunning && timerMs == 0L) {
                var h by remember { mutableLongStateOf(0L) }
                var m by remember { mutableLongStateOf(0L) }
                var s by remember { mutableLongStateOf(0L) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    NumPicker("H", h, { h = it })
                    NumPicker("M", m, { m = it })
                    NumPicker("S", s, { s = it })
                }
                Btn("Démarrer", h > 0 || m > 0 || s > 0, { onTimerDurationChanged((h * 3600 + m * 60 + s) * 1000); onTimerStart() },
                    Modifier.padding(top = 16.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
                    Btn(if (isTimerRunning) "Pause" else "Reprendre", true,
                        if (isTimerRunning) onTimerPause else onTimerStart)
                    Btn("Reset", true, onTimerReset)
                }
            }
        }
    }
}

@Composable
private fun Btn(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.clickable(enabled) { onClick() }
        .background(if (enabled) ACCENT else NAVY.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        .padding(horizontal = 20.dp, vertical = 10.dp), Alignment.Center) {
        Text(label, color = if (enabled) BG else Color.White.copy(0.5f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NumPicker(label: String, value: Long, onChange: (Long) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clickable { onChange((value + 1).coerceAtMost(59)) }
            .background(ACCENT, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("+", color = BG, fontWeight = FontWeight.Bold)
        }
        Text(value.toString().padStart(2, '0'), color = ACCENT, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp))
        Box(Modifier.clickable { onChange((value - 1).coerceAtLeast(0)) }
            .background(ACCENT, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("-", color = BG, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

private fun formatTime(ms: Long): String {
    val h = ms / 3600000; val m = (ms % 3600000) / 60000; val s = (ms % 60000) / 1000; val cs = (ms % 1000) / 10
    return if (h > 0) "%02d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
}
