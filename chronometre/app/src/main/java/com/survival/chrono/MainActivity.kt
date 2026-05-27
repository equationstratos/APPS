package com.survival.chrono

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChronoApp() }
    }
}

@Composable
fun ChronoApp() {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    var ms by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var timerMs by remember { mutableLongStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerSet by remember { mutableLongStateOf(60000L) }

    LaunchedEffect(running) {
        val start = System.currentTimeMillis() - ms
        while (running) { ms = System.currentTimeMillis() - start; delay(16) }
    }

    LaunchedEffect(timerRunning) {
        val start = System.currentTimeMillis()
        val dur = timerMs
        while (timerRunning && timerMs > 0) {
            timerMs = maxOf(0, dur - (System.currentTimeMillis() - start))
            if (timerMs == 0L) timerRunning = false
            delay(16)
        }
    }

    Column(Modifier.fillMaxSize().background(bg).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Chronomètre", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Btn("Chrono", tab == 0, accent, navy, bg) { tab = 0 }
            Btn("Minuteur", tab == 1, accent, navy, bg) { tab = 1 }
        }
        Spacer(Modifier.height(32.dp))

        if (tab == 0) {
            Text(fmt(ms), color = accent, fontSize = 52.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn(if (running) "Stop" else "Start", true, accent, navy, bg) { running = !running }
                Btn("Reset", true, accent, navy, bg) { running = false; ms = 0L }
            }
        } else {
            Text(fmt(timerMs), color = accent, fontSize = 52.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(24.dp))
            if (!timerRunning && timerMs == 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 120, 300).forEach { s ->
                        Btn("${s}s", true, accent, navy, bg) { timerMs = s * 1000L; timerSet = s * 1000L }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (timerMs > 0L) Btn(if (timerRunning) "Pause" else "Go", true, accent, navy, bg) { timerRunning = !timerRunning }
                Btn("Reset", true, accent, navy, bg) { timerRunning = false; timerMs = 0L }
            }
        }
    }
}

@Composable
fun Btn(label: String, enabled: Boolean, accent: Color, navy: Color, bg: Color, onClick: () -> Unit) {
    Box(Modifier.clickable(enabled) { onClick() }
        .background(if (enabled) accent else navy, RoundedCornerShape(8.dp))
        .padding(horizontal = 16.dp, vertical = 10.dp), Alignment.Center) {
        Text(label, color = bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun fmt(ms: Long): String {
    val m = (ms / 60000); val s = (ms % 60000) / 1000; val c = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(m, s, c)
}
