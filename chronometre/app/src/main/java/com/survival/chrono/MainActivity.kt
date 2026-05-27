package com.survival.chrono

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.survival.chrono.ui.ChronometerScreen

class MainActivity : ComponentActivity() {

    private var stopwatchMs by mutableLongStateOf(0L)
    private var timerMs by mutableLongStateOf(0L)
    private var isStopwatchRunning by mutableIntStateOf(0)
    private var isTimerRunning by mutableIntStateOf(0)
    private val lapTimes = mutableStateListOf<Long>()
    private var selectedTab by mutableIntStateOf(0)

    private var stopwatchThread: Thread? = null
    private var timerThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ChronometerScreen(
                stopwatchMs = stopwatchMs,
                timerMs = timerMs,
                isStopwatchRunning = isStopwatchRunning == 1,
                isTimerRunning = isTimerRunning == 1,
                lapTimes = lapTimes,
                selectedTab = selectedTab,
                onTabChanged = { tab -> selectedTab = tab },
                onStopwatchStart = { startStopwatch() },
                onStopwatchStop = { stopStopwatch() },
                onStopwatchReset = { resetStopwatch() },
                onStopwatchLap = { recordLap() },
                onTimerStart = { startTimer() },
                onTimerPause = { pauseTimer() },
                onTimerReset = { resetTimer() },
                onTimerDurationChanged = { ms -> timerMs = ms },
                context = this
            )
        }
    }

    private fun startStopwatch() {
        if (isStopwatchRunning == 1) return
        isStopwatchRunning = 1

        stopwatchThread = Thread {
            val startTime = System.currentTimeMillis() - stopwatchMs
            while (isStopwatchRunning == 1) {
                stopwatchMs = System.currentTimeMillis() - startTime
                Thread.sleep(10)
            }
        }.apply { start() }
    }

    private fun stopStopwatch() {
        isStopwatchRunning = 0
        stopwatchThread?.join(1000)
    }

    private fun resetStopwatch() {
        stopStopwatch()
        stopwatchMs = 0L
        lapTimes.clear()
    }

    private fun recordLap() {
        lapTimes.add(0, stopwatchMs)
    }

    private fun startTimer() {
        if (isTimerRunning == 1) return
        if (timerMs <= 0) return
        isTimerRunning = 1

        timerThread = Thread {
            val startTime = System.currentTimeMillis()
            val duration = timerMs
            while (isTimerRunning == 1) {
                val elapsed = System.currentTimeMillis() - startTime
                timerMs = maxOf(0, duration - elapsed)
                if (timerMs == 0L) {
                    isTimerRunning = 0
                    onTimerFinished()
                    break
                }
                Thread.sleep(10)
            }
        }.apply { start() }
    }

    private fun pauseTimer() {
        isTimerRunning = 0
        timerThread?.join(1000)
    }

    private fun resetTimer() {
        pauseTimer()
        timerMs = 0L
    }

    private fun onTimerFinished() {
        try {
            val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // Vibrator not available
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStopwatch()
        pauseTimer()
    }
}
