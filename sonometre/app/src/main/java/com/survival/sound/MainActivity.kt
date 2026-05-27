package com.survival.sound

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SonometreApp(this) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}

@Composable
fun SonometreApp(activity: ComponentActivity) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    var db by remember { mutableFloatStateOf(0f) }
    var minDb by remember { mutableFloatStateOf(0f) }
    var maxDb by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf(false) }
    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val bufSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            recorder.startRecording()
            val buf = ShortArray(bufSize)
            while (isActive && recording) {
                val read = recorder.read(buf, 0, bufSize)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) { val s = buf[i].toDouble(); sum += s * s }
                    val rms = sqrt(sum / read)
                    val level = (20 * log10(rms / 32768.0)).toFloat().coerceIn(0f, 140f)
                    db = level
                    if (minDb == 0f || level < minDb) minDb = level
                    if (level > maxDb) maxDb = level
                }
            }
            recorder.stop()
            recorder.release()
        }
    }

    Column(Modifier.fillMaxSize().background(bg).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Stat("Min", "%.1f".format(minDb), accent, navy)
            Stat("Max", "%.1f".format(maxDb), accent, navy)
        }
        Spacer(Modifier.height(24.dp))

        Column(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text("Référence", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Ref("Chuchotement", "~30 dB", accent)
            Ref("Conversation", "~60 dB", accent)
            Ref("Trafic", "~80 dB", accent)
            Ref("Concert", "~110 dB", accent)
        }
        Spacer(Modifier.height(24.dp))

        if (!hasPerm) {
            Button(onClick = { ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 101) },
                colors = ButtonDefaults.buttonColors(containerColor = accent), modifier = Modifier.fillMaxWidth()) {
                Text("Autoriser micro", color = bg)
            }
        } else if (recording) {
            Button(onClick = { recording = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B)), modifier = Modifier.fillMaxWidth()) {
                Text("Arrêter", color = Color.White)
            }
        } else {
            Button(onClick = { db = 0f; minDb = 0f; maxDb = 0f; recording = true },
                colors = ButtonDefaults.buttonColors(containerColor = accent), modifier = Modifier.fillMaxWidth()) {
                Text("Démarrer", color = bg)
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, accent: Color, navy: Color) {
    Column(Modifier.background(navy, RoundedCornerShape(8.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Ref(label: String, value: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(value, color = accent, fontSize = 12.sp)
    }
}
