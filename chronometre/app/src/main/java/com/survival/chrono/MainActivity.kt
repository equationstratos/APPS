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
    var timerTarget by remember { mutableLongStateOf(0L) }
    var timerRemaining by remember { mutableLongStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = System.currentTimeMillis() - ms
        while (running) {
            ms = System.currentTimeMillis() - start
            delay(16)
        }
    }

    LaunchedEffect(timerRunning) {
        if (!timerRunning) return@LaunchedEffect
        val start = System.currentTimeMillis()
        val duration = timerRemaining
        while (timerRunning) {
            val elapsed = System.currentTimeMillis() - start
            val remaining = (duration - elapsed).coerceAtLeast(0L)
            timerRemaining = remaining
            if (remaining <= 0L) {
                timerRunning = false
                break
            }
            delay(16)
        }
    }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Chronomètre", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Btn("Chrono", tab == 0, accent, navy, bg) { tab = 0 }
            Btn("Minuteur", tab == 1, accent, navy, bg) { tab = 1 }
        }
        Spacer(Modifier.height(40.dp))

        if (tab == 0) {
            Text(fmt(ms), color = accent, fontSize = 52.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Btn(if (running) "Stop" else "Start", true, accent, navy, bg) { running = !running }
                Btn("Reset", true, accent, navy, bg) { running = false; ms = 0L }
            }
        } else {
            Text(fmt(timerRemaining), color = accent, fontSize = 52.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(32.dp))

            if (!timerRunning && timerRemaining == 0L) {
                Text("Choisir durée", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Pair("30s", 30), Pair("1m", 60), Pair("2m", 120), Pair("5m", 300)).forEach { (label, secs) ->
                        Btn(label, true, accent, navy, bg) {
                            timerTarget = secs.toLong() * 1000L
                            timerRemaining = secs.toLong() * 1000L
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (timerRemaining > 0L) {
                    Btn("Démarrer", true, accent, navy, bg) { timerRunning = true }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Btn(if (timerRunning) "Pause" else "Reprendre", true, accent, navy, bg) { timerRunning = !timerRunning }
                    Btn("Reset", true, accent, navy, bg) { timerRunning = false; timerRemaining = 0L; timerTarget = 0L }
                }
            }
        }
    }
}

@Composable
fun Btn(label: String, enabled: Boolean, accent: Color, navy: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.clickable(enabled) { onClick() }
            .background(if (enabled) accent else navy, RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        Alignment.Center
    ) {
        Text(label, color = bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun fmt(ms: Long): String {
    val h = ms / 3600000
    val m = (ms % 3600000) / 60000
    val s = (ms % 60000) / 1000
    val c = (ms % 1000) / 10
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d.%02d".format(m, s, c)
}
