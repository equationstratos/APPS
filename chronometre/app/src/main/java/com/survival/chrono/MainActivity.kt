package com.survival.chrono

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.survival.chrono.ui.ChronometerScreen

class MainActivity : ComponentActivity() {

    val stopwatchMs = mutableLongStateOf(0L)
    val timerMs = mutableLongStateOf(0L)
    val isStopwatchRunning = mutableStateOf(false)
    val isTimerRunning = mutableStateOf(false)
    val lapTimes = mutableStateListOf<Long>()
    val selectedTab = mutableStateOf(0)

    private var stopwatchThread: Thread? = null
    private var timerThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChronometerScreen(
                stopwatchMs = stopwatchMs.longValue,
                timerMs = timerMs.longValue,
                isStopwatchRunning = isStopwatchRunning.value,
                isTimerRunning = isTimerRunning.value,
                lapTimes = lapTimes,
                selectedTab = selectedTab.value,
                onTabChanged = { selectedTab.value = it },
                onStopwatchStart = { startStopwatch() },
                onStopwatchStop = { stopStopwatch() },
                onStopwatchReset = { resetStopwatch() },
                onStopwatchLap = { lapTimes.add(0, stopwatchMs.longValue) },
                onTimerStart = { startTimer() },
                onTimerPause = { pauseTimer() },
                onTimerReset = { pauseTimer(); timerMs.longValue = 0L },
                onTimerDurationChanged = { timerMs.longValue = it }
            )
        }
    }

    private fun startStopwatch() {
        if (isStopwatchRunning.value) return
        isStopwatchRunning.value = true
        stopwatchThread = Thread {
            val base = System.currentTimeMillis() - stopwatchMs.longValue
            while (isStopwatchRunning.value) {
                stopwatchMs.longValue = System.currentTimeMillis() - base
                Thread.sleep(10)
            }
        }.apply { start() }
    }

    private fun stopStopwatch() {
        isStopwatchRunning.value = false
    }

    private fun resetStopwatch() {
        stopStopwatch()
        stopwatchMs.longValue = 0L
        lapTimes.clear()
    }

    private fun startTimer() {
        if (isTimerRunning.value || timerMs.longValue <= 0) return
        isTimerRunning.value = true
        timerThread = Thread {
            val start = System.currentTimeMillis()
            val duration = timerMs.longValue
            while (isTimerRunning.value) {
                val remaining = maxOf(0L, duration - (System.currentTimeMillis() - start))
                timerMs.longValue = remaining
                if (remaining == 0L) {
                    isTimerRunning.value = false
                    runOnUiThread {
                        try {
                            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        } catch (_: Exception) {}
                    }
                    break
                }
                Thread.sleep(10)
            }
        }.apply { start() }
    }

    private fun pauseTimer() {
        isTimerRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isStopwatchRunning.value = false
        isTimerRunning.value = false
    }
}
