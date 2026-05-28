package com.survival.network

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NetworkApp() }
    }
}

@Composable
fun NetworkApp() {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Prêt") }
    var downloadMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Vitesse Réseau", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Box(Modifier.size(260.dp).background(navy, CircleShape), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.1f".format(downloadMbps), color = accent, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text("Mbps", color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Ping", color = Color.White, fontSize = 14.sp)
                    Text("${pingMs} ms", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Statut", color = Color.White, fontSize = 14.sp)
                    Text(status, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(Modifier.background(if (testing) navy else accent, RoundedCornerShape(8.dp))
            .clickable(!testing) {
                scope.launch {
                    testing = true
                    status = "Ping..."
                    val ping = measurePing()
                    pingMs = ping
                    status = "Téléchargement..."
                    downloadMbps = measureDownload()
                    status = if (downloadMbps > 0) "Terminé" else "Erreur"
                    testing = false
                }
            }.padding(horizontal = 48.dp, vertical = 16.dp)) {
            Text(if (testing) status else "Tester", color = bg, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
        }
    }
}

suspend fun measurePing(): Int = withContext(Dispatchers.IO) {
    try {
        val start = System.currentTimeMillis()
        val url = URL("https://www.google.com")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.requestMethod = "HEAD"
        conn.connect()
        val ping = (System.currentTimeMillis() - start).toInt()
        conn.disconnect()
        ping
    } catch (_: Exception) { 0 }
}

suspend fun measureDownload(): Float = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://speed.cloudflare.com/__down?bytes=10000000")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        val start = System.currentTimeMillis()
        val input = conn.inputStream
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (System.currentTimeMillis() - start > 10000) break
        }
        val seconds = (System.currentTimeMillis() - start) / 1000f
        input.close()
        conn.disconnect()
        if (seconds > 0) (total * 8 / seconds / 1_000_000f) else 0f
    } catch (_: Exception) { 0f }
}
