package com.survival.battery

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BatteryApp() }
    }
}

@Composable
fun BatteryApp() {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)
    val context = LocalContext.current

    var level by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var health by remember { mutableStateOf("") }
    var temp by remember { mutableFloatStateOf(0f) }
    var voltage by remember { mutableIntStateOf(0) }
    var technology by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level = if (lvl > 0 && scale > 0) (lvl * 100 / scale) else 0
                status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "En charge"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Décharge"
                    BatteryManager.BATTERY_STATUS_FULL -> "Pleine"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Non en charge"
                    else -> "Inconnu"
                }
                health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Bonne"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surchauffe"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "HS"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Surtension"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Froide"
                    else -> "Inconnue"
                }
                temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
            }
            delay(2000)
        }
    }

    val color = when {
        level > 50 -> Color(0xFF00D084)
        level > 20 -> Color(0xFFFFD700)
        else -> Color(0xFFFF6B6B)
    }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Batterie", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Box(Modifier.fillMaxWidth().height(60.dp).background(navy, RoundedCornerShape(12.dp))) {
            Box(Modifier.fillMaxWidth(level / 100f).fillMaxHeight().background(color, RoundedCornerShape(12.dp)))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("$level%", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        Column(
            Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow("Statut", status, accent)
            InfoRow("Santé", health, accent)
            InfoRow("Température", "%.1f °C".format(temp), accent)
            InfoRow("Voltage", "${voltage} mV", accent)
            InfoRow("Technologie", technology, accent)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, accent: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
