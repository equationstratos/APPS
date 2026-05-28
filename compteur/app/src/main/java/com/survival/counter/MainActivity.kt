package com.survival.counter

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CounterApp() }
    }
}

@Composable
fun CounterApp() {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    var count by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Compteur", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Box(
            Modifier.size(280.dp).background(navy, CircleShape).clickable { count++ },
            Alignment.Center
        ) {
            Text("$count", color = accent, fontSize = 96.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))
        Text("Touchez pour incrémenter", color = Color.White.copy(0.6f), fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.background(navy, RoundedCornerShape(8.dp))
                .clickable { if (count > 0) count-- }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("- 1", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.background(accent, RoundedCornerShape(8.dp))
                .clickable { count = 0 }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("Reset", color = bg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
