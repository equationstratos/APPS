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

    private val currentLevel = mutableFloatStateOf(0f)
    private val minLevel = mutableFloatStateOf(0f)
    private val maxLevel = mutableFloatStateOf(0f)
    private val avgLevel = mutableFloatStateOf(0f)
    private val isRecording = mutableStateOf(false)
    private val hasPermission = mutableStateOf(false)

    private var audioMeter: AudioLevelMeter? = null
    private var sampleCount = 0
    private var sumLevels = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermission()

        audioMeter = AudioLevelMeter { level ->
            currentLevel.floatValue = level
            if (minLevel.floatValue == 0f || level < minLevel.floatValue) minLevel.floatValue = level
            if (level > maxLevel.floatValue) maxLevel.floatValue = level
            sampleCount++
            sumLevels += level
            avgLevel.floatValue = sumLevels / sampleCount
        }

        setContent {
            SoundLevelScreen(
                currentLevel = currentLevel.floatValue,
                minLevel = minLevel.floatValue,
                maxLevel = maxLevel.floatValue,
                averageLevel = avgLevel.floatValue,
                isRecording = isRecording.value,
                hasPermission = hasPermission.value,
                onStartRecording = { startRecording() },
                onStopRecording = { stopRecording() },
                onReset = { resetStats() }
            )
        }
    }

    private fun checkPermission() {
        hasPermission.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecording() {
        if (!hasPermission.value) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }
        resetStats()
        audioMeter?.start()
        isRecording.value = true
    }

    private fun stopRecording() {
        audioMeter?.stop()
        isRecording.value = false
    }

    private fun resetStats() {
        currentLevel.floatValue = 0f
        minLevel.floatValue = 0f
        maxLevel.floatValue = 0f
        avgLevel.floatValue = 0f
        sampleCount = 0
        sumLevels = 0f
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            hasPermission.value = true
            startRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }
}
