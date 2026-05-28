package com.survival.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MetronomeApp() }
    }
}

@Composable
fun MetronomeApp() {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    var bpm by remember { mutableIntStateOf(120) }
    var running by remember { mutableStateOf(false) }
    var pulse by remember { mutableIntStateOf(0) }

    LaunchedEffect(running, bpm) {
        if (!running) return@LaunchedEffect
        val interval = 60000L / bpm
        while (running) {
            pulse++
            withContext(Dispatchers.IO) { playTick() }
            delay(interval)
        }
    }

    val pulseSize = if (pulse % 2 == 0) 220.dp else 240.dp

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Métronome", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Box(Modifier.size(pulseSize).background(if (running) accent else navy, CircleShape), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$bpm", color = if (running) bg else accent, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                Text("BPM", color = if (running) bg else Color.White, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        Slider(
            value = bpm.toFloat(), onValueChange = { bpm = it.toInt().coerceIn(40, 240) },
            valueRange = 40f..240f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = navy),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(60, 80, 100, 120, 160).forEach { v ->
                Box(Modifier.background(navy, RoundedCornerShape(8.dp))
                    .clickable { bpm = v }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("$v", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(Modifier.background(accent, RoundedCornerShape(8.dp))
            .clickable { running = !running }.padding(horizontal = 48.dp, vertical = 16.dp)) {
            Text(if (running) "STOP" else "START", color = bg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun playTick() {
    try {
        val sampleRate = 44100
        val duration = 40 // ms
        val numSamples = sampleRate * duration / 1000
        val samples = ShortArray(numSamples)
        for (i in samples.indices) {
            val t = i.toDouble() / sampleRate
            val env = (1.0 - i.toDouble() / numSamples)
            samples[i] = (sin(2 * Math.PI * 1000 * t) * env * Short.MAX_VALUE).toInt().toShort()
        }
        val bufferSize = numSamples * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(samples, 0, numSamples)
        track.play()
        Thread.sleep(duration.toLong())
        track.release()
    } catch (_: Exception) {}
}
