package com.survival.chrono.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlin.math.floor

@Composable
fun ChronometerScreen(
    stopwatchMs: Long,
    timerMs: Long,
    isStopwatchRunning: Boolean,
    isTimerRunning: Boolean,
    lapTimes: List<Long>,
    selectedTab: Int,
    onTabChanged: (Int) -> Unit,
    onStopwatchStart: () -> Unit,
    onStopwatchStop: () -> Unit,
    onStopwatchReset: () -> Unit,
    onStopwatchLap: () -> Unit,
    onTimerStart: () -> Unit,
    onTimerPause: () -> Unit,
    onTimerReset: () -> Unit,
    onTimerDurationChanged: (Long) -> Unit,
    context: Context
) {
    val bgColor = Color(0xFF1A1A2E)
    val accentColor = Color(0xFF4A9FFF)
    val navyColor = Color(0xFF0F3460)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Tab selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabButton(
                    label = "Stopwatch",
                    isSelected = selectedTab == 0,
                    onClicked = { onTabChanged(0) },
                    accentColor = accentColor,
                    navyColor = navyColor
                )
                TabButton(
                    label = "Timer",
                    isSelected = selectedTab == 1,
                    onClicked = { onTabChanged(1) },
                    accentColor = accentColor,
                    navyColor = navyColor
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    0 -> StopwatchTab(
                        stopwatchMs = stopwatchMs,
                        isRunning = isStopwatchRunning,
                        lapTimes = lapTimes,
                        onStart = onStopwatchStart,
                        onStop = onStopwatchStop,
                        onReset = onStopwatchReset,
                        onLap = onStopwatchLap,
                        accentColor = accentColor,
                        navyColor = navyColor
                    )
                    1 -> TimerTab(
                        timerMs = timerMs,
                        isRunning = isTimerRunning,
                        onStart = onTimerStart,
                        onPause = onTimerPause,
                        onReset = onTimerReset,
                        onDurationChanged = onTimerDurationChanged,
                        accentColor = accentColor,
                        navyColor = navyColor
                    )
                }
            }
        }
    }
}

@Composable
fun TabButton(
    label: String,
    isSelected: Boolean,
    onClicked: () -> Unit,
    accentColor: Color,
    navyColor: Color
) {
    Box(
        modifier = Modifier
            .clickable { onClicked() }
            .background(
                color = if (isSelected) accentColor else navyColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFF1A1A2E) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StopwatchTab(
    stopwatchMs: Long,
    isRunning: Boolean,
    lapTimes: List<Long>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onLap: () -> Unit,
    accentColor: Color,
    navyColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Time display
        Text(
            text = formatTime(stopwatchMs),
            color = accentColor,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light
        )

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                label = if (isRunning) "Stop" else "Start",
                onClick = if (isRunning) onStop else onStart,
                accentColor = accentColor,
                navyColor = navyColor
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                label = "Lap",
                onClick = onLap,
                accentColor = accentColor,
                navyColor = navyColor,
                enabled = isRunning
            )
            ControlButton(
                label = "Reset",
                onClick = onReset,
                accentColor = accentColor,
                navyColor = navyColor
            )
        }

        // Lap times list
        if (lapTimes.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(navyColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(lapTimes) { lapTime ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lap ${lapTimes.indexOf(lapTime) + 1}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = formatTime(lapTime),
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimerTab(
    timerMs: Long,
    isRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onDurationChanged: (Long) -> Unit,
    accentColor: Color,
    navyColor: Color
) {
    var hours by remember { mutableLongStateOf(0) }
    var minutes by remember { mutableLongStateOf(0) }
    var seconds by remember { mutableLongStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Time display
        Text(
            text = formatTime(timerMs),
            color = accentColor,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light
        )

        // Duration input (only when not running)
        if (!isRunning && timerMs == 0L) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DurationInput(
                    label = "H",
                    value = hours,
                    onValueChanged = { hours = it },
                    accentColor = accentColor,
                    navyColor = navyColor
                )
                DurationInput(
                    label = "M",
                    value = minutes,
                    onValueChanged = { minutes = it },
                    accentColor = accentColor,
                    navyColor = navyColor
                )
                DurationInput(
                    label = "S",
                    value = seconds,
                    onValueChanged = { seconds = it },
                    accentColor = accentColor,
                    navyColor = navyColor
                )
            }
        }

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isRunning && timerMs == 0L) {
                ControlButton(
                    label = "Set",
                    onClick = {
                        val totalMs = (hours * 3600 + minutes * 60 + seconds) * 1000
                        onDurationChanged(totalMs)
                    },
                    accentColor = accentColor,
                    navyColor = navyColor,
                    enabled = hours > 0 || minutes > 0 || seconds > 0
                )
            } else {
                ControlButton(
                    label = if (isRunning) "Pause" else "Start",
                    onClick = if (isRunning) onPause else onStart,
                    accentColor = accentColor,
                    navyColor = navyColor
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                label = "Reset",
                onClick = onReset,
                accentColor = accentColor,
                navyColor = navyColor
            )
        }
    }
}

@Composable
fun ControlButton(
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    navyColor: Color,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .background(
                color = if (enabled) accentColor else navyColor.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) Color(0xFF1A1A2E) else Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DurationInput(
    label: String,
    value: Long,
    onValueChanged: (Long) -> Unit,
    accentColor: Color,
    navyColor: Color
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(navyColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = textValue.padStart(2, '0'),
                color = accentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            SmallButton(
                label = "+",
                onClick = {
                    val newValue = (value + 1).coerceAtMost(59)
                    textValue = newValue.toString()
                    onValueChanged(newValue)
                },
                accentColor = accentColor,
                navyColor = navyColor
            )
            SmallButton(
                label = "-",
                onClick = {
                    val newValue = (value - 1).coerceAtLeast(0)
                    textValue = newValue.toString()
                    onValueChanged(newValue)
                },
                accentColor = accentColor,
                navyColor = navyColor
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SmallButton(
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    navyColor: Color
) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .background(accentColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFF1A1A2E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 10

    return if (hours > 0) {
        String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, millis)
    } else {
        String.format("%02d:%02d.%02d", minutes, seconds, millis)
    }
}
