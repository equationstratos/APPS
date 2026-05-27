package com.survival.chrono

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import com.survival.chrono.ui.ChronometerScreen

class MainActivity : ComponentActivity() {

    private val stopwatchMsState = mutableLongStateOf(0L)
    private val timerMsState = mutableLongStateOf(0L)
    private val isStopwatchRunningState = mutableIntStateOf(0)
    private val isTimerRunningState = mutableIntStateOf(0)
    private val lapTimes = mutableStateListOf<Long>()
    private val selectedTabState = mutableIntStateOf(0)

    private var stopwatchThread: Thread? = null
    private var timerThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ChronometerScreen(
                stopwatchMs = stopwatchMsState.longValue,
                timerMs = timerMsState.longValue,
                isStopwatchRunning = isStopwatchRunningState.intValue == 1,
                isTimerRunning = isTimerRunningState.intValue == 1,
                lapTimes = lapTimes,
                selectedTab = selectedTabState.intValue,
                onTabChanged = { selectedTabState.intValue = it },
                onStopwatchStart = { startStopwatch() },
                onStopwatchStop = { stopStopwatch() },
                onStopwatchReset = { resetStopwatch() },
                onStopwatchLap = { lapTimes.add(0, stopwatchMsState.longValue) },
                onTimerStart = { startTimer() },
                onTimerPause = { pauseTimer() },
                onTimerReset = { pauseTimer(); timerMsState.longValue = 0L },
                onTimerDurationChanged = { timerMsState.longValue = it }
            )
        }
    }

    private fun startStopwatch() {
        if (isStopwatchRunningState.intValue == 1) return
        isStopwatchRunningState.intValue = 1
        stopwatchThread = Thread {
            val startTime = System.currentTimeMillis() - stopwatchMsState.longValue
            while (isStopwatchRunningState.intValue == 1) {
                stopwatchMsState.longValue = System.currentTimeMillis() - startTime
                Thread.sleep(10)
            }
        }.apply { start() }
    }

    private fun stopStopwatch() {
        isStopwatchRunningState.intValue = 0
        stopwatchThread?.join(1000)
    }

    private fun resetStopwatch() {
        stopStopwatch()
        stopwatchMsState.longValue = 0L
        lapTimes.clear()
    }

    private fun startTimer() {
        if (isTimerRunningState.intValue == 1) return
        if (timerMsState.longValue <= 0) return
        isTimerRunningState.intValue = 1
        timerThread = Thread {
            val startTime = System.currentTimeMillis()
            val duration = timerMsState.longValue
            while (isTimerRunningState.intValue == 1) {
                val elapsed = System.currentTimeMillis() - startTime
                timerMsState.longValue = maxOf(0, duration - elapsed)
                if (timerMsState.longValue == 0L) {
                    isTimerRunningState.intValue = 0
                    try {
                        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } catch (_: Exception) {}
                    break
                }
                Thread.sleep(10)
            }
        }.apply { start() }
    }

    private fun pauseTimer() {
        isTimerRunningState.intValue = 0
        timerThread?.join(1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStopwatch()
        pauseTimer()
    }
}
