package com.survival.timezones

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TimezonesApp() }
    }
}

data class City(val name: String, val tz: String)

@Composable
fun TimezonesApp() {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    val cities = listOf(
        City("Paris", "Europe/Paris"),
        City("Londres", "Europe/London"),
        City("New York", "America/New_York"),
        City("Los Angeles", "America/Los_Angeles"),
        City("Tokyo", "Asia/Tokyo"),
        City("Sydney", "Australia/Sydney"),
        City("Moscou", "Europe/Moscow"),
        City("Dubai", "Asia/Dubai"),
        City("Shanghai", "Asia/Shanghai"),
        City("Mumbai", "Asia/Kolkata"),
        City("Sao Paulo", "America/Sao_Paulo"),
        City("Le Caire", "Africa/Cairo")
    )

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000) }
    }

    Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
        Text("Fuseaux Horaires", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cities) { city ->
                val tz = TimeZone.getTimeZone(city.tz)
                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).apply { timeZone = tz }
                val dateFmt = SimpleDateFormat("EEE d MMM", Locale.FRANCE).apply { timeZone = tz }

                Row(
                    Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(16.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Column {
                        Text(city.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(dateFmt.format(now), color = Color.White.copy(0.6f), fontSize = 12.sp)
                    }
                    Text(timeFmt.format(now), color = accent, fontSize = 22.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}
