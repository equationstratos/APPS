package com.survival.sound

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.survival.sound.ui.SoundLevelScreen

class MainActivity : ComponentActivity() {

    val currentDb = mutableFloatStateOf(0f)
    val minDb = mutableFloatStateOf(0f)
    val maxDb = mutableFloatStateOf(0f)
    val avgDb = mutableFloatStateOf(0f)
    val isRecording = mutableStateOf(false)
    val hasPermission = mutableStateOf(false)

    private var meter: AudioLevelMeter? = null
    private var count = 0
    private var sum = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasPermission.value = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        setContent {
            SoundLevelScreen(
                currentLevel = currentDb.floatValue,
                minLevel = minDb.floatValue,
                maxLevel = maxDb.floatValue,
                averageLevel = avgDb.floatValue,
                isRecording = isRecording.value,
                hasPermission = hasPermission.value,
                onStartRecording = { startRec() },
                onStopRecording = { stopRec() },
                onReset = { resetStats() }
            )
        }
    }

    private fun startRec() {
        if (!hasPermission.value) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }
        resetStats()
        meter = AudioLevelMeter { level ->
            currentDb.floatValue = level
            if (minDb.floatValue == 0f || level < minDb.floatValue) minDb.floatValue = level
            if (level > maxDb.floatValue) maxDb.floatValue = level
            count++; sum += level; avgDb.floatValue = sum / count
        }
        meter?.start()
        isRecording.value = true
    }

    private fun stopRec() {
        meter?.stop()
        isRecording.value = false
    }

    private fun resetStats() {
        stopRec()
        currentDb.floatValue = 0f; minDb.floatValue = 0f; maxDb.floatValue = 0f; avgDb.floatValue = 0f
        count = 0; sum = 0f
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            hasPermission.value = true; startRec()
        }
    }

    override fun onDestroy() { super.onDestroy(); stopRec() }
}
