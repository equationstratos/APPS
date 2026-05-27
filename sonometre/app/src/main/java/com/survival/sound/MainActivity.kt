package com.survival.sound

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

        setContent { SonometreApp(this, permLauncher) }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun SonometreApp(
    activity: ComponentActivity,
    permLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    var db by remember { mutableFloatStateOf(0f) }
    var minDb by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var maxDb by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf(false) }
    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    LaunchedEffect(recording) {
        if (!recording || !hasPerm) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val sampleRate = 44100
            val bufSize = maxOf(
                AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                4096
            )
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) return@withContext
                recorder.startRecording()
                val buf = ShortArray(bufSize)
                while (isActive && recording) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) { val s = buf[i].toDouble(); sum += s * s }
                        val rms = sqrt(sum / read)
                        val level = if (rms > 0) (20 * log10(rms)).toFloat().coerceIn(0f, 140f) else 0f
                        db = level
                        if (level > 0f && level < minDb) minDb = level
                        if (level > maxDb) maxDb = level
                    }
                }
            } finally {
                try { recorder?.stop() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sonomètre", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Box(Modifier.size(200.dp).background(navy, CircleShape), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.1f".format(db), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = accent)
                Text("dB", fontSize = 16.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatBox("Min", if (minDb == Float.MAX_VALUE) "-" else "%.1f".format(minDb), accent, navy)
            StatBox("Max", if (maxDb == 0f) "-" else "%.1f".format(maxDb), accent, navy)
        }
        Spacer(Modifier.height(24.dp))

        Column(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text("Référence", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            RefRow("Chuchotement", "~30 dB", accent)
            RefRow("Conversation", "~60 dB", accent)
            RefRow("Trafic", "~80 dB", accent)
            RefRow("Concert", "~110 dB", accent)
        }
        Spacer(Modifier.height(24.dp))

        if (!hasPerm) {
            Button(
                onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO); hasPerm = true },
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Autoriser micro", color = bg, fontWeight = FontWeight.Bold) }
        } else if (recording) {
            Button(
                onClick = { recording = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Arrêter", color = Color.White, fontWeight = FontWeight.Bold) }
        } else {
            Button(
                onClick = { db = 0f; minDb = Float.MAX_VALUE; maxDb = 0f; recording = true },
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Démarrer", color = bg, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, accent: Color, navy: Color) {
    Column(
        Modifier.background(navy, RoundedCornerShape(8.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RefRow(label: String, value: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(value, color = accent, fontSize = 12.sp)
    }
}
