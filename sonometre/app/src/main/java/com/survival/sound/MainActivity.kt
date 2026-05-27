package com.survival.sound

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.survival.sound.ui.SoundLevelScreen

class MainActivity : ComponentActivity() {

    private var audioLevelMeter: AudioLevelMeter? = null
    private var currentLevel by mutableFloatStateOf(0f)
    private var minLevel by mutableFloatStateOf(0f)
    private var maxLevel by mutableFloatStateOf(0f)
    private var averageLevel by mutableFloatStateOf(0f)
    private var isRecording by mutableStateOf(false)
    private var hasPermission by mutableStateOf(false)

    private var sampleCount = 0
    private var sumLevels = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAudioPermission()

        audioLevelMeter = AudioLevelMeter { level ->
            currentLevel = level
            updateStats(level)
        }

        setContent {
            SoundLevelScreen(
                currentLevel = currentLevel,
                minLevel = minLevel,
                maxLevel = maxLevel,
                averageLevel = averageLevel,
                isRecording = isRecording,
                hasPermission = hasPermission,
                onStartRecording = { startRecording() },
                onStopRecording = { stopRecording() },
                onReset = { resetStats() }
            )
        }
    }

    private fun checkAudioPermission() {
        hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasPermission) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            hasPermission = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startRecording() {
        if (!hasPermission) {
            checkAudioPermission()
            return
        }
        resetStats()
        audioLevelMeter?.start()
        isRecording = true
    }

    private fun stopRecording() {
        audioLevelMeter?.stop()
        isRecording = false
    }

    private fun updateStats(level: Float) {
        if (minLevel == 0f || level < minLevel) {
            minLevel = level
        }
        if (level > maxLevel) {
            maxLevel = level
        }
        sampleCount++
        sumLevels += level
        averageLevel = sumLevels / sampleCount
    }

    private fun resetStats() {
        minLevel = 0f
        maxLevel = 0f
        averageLevel = 0f
        sampleCount = 0
        sumLevels = 0f
        currentLevel = 0f
    }

    override fun onResume() {
        super.onResume()
        if (isRecording) {
            audioLevelMeter?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        audioLevelMeter?.stop()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }
}
