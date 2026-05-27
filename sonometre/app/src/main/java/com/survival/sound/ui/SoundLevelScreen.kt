package com.survival.sound.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG = Color(0xFF1A1A2E)
private val ACCENT = Color(0xFF4A9FFF)
private val NAVY = Color(0xFF0F3460)

@Composable
fun SoundLevelScreen(
    currentLevel: Float, minLevel: Float, maxLevel: Float, averageLevel: Float,
    isRecording: Boolean, hasPermission: Boolean,
    onStartRecording: () -> Unit, onStopRecording: () -> Unit, onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Sonomètre", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ACCENT)

        Box(Modifier.size(200.dp).background(NAVY, CircleShape).padding(16.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.1f".format(currentLevel), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ACCENT)
                Text("dB", fontSize = 18.sp, color = Color.White)
            }
        }

        val norm = (currentLevel / 140f).coerceIn(0f, 1f)
        val animLevel by animateFloatAsState(norm, tween(100, easing = LinearEasing), label = "level")
        val barColor by animateColorAsState(
            when { currentLevel < 50 -> ACCENT; currentLevel < 80 -> Color(0xFF7ED321)
                currentLevel < 100 -> Color(0xFFFFD700); else -> Color(0xFFFF6B6B) },
            tween(300), label = "color"
        )
        Box(Modifier.fillMaxWidth().height(28.dp).background(NAVY, RoundedCornerShape(14.dp)).padding(3.dp)) {
            Box(Modifier.fillMaxWidth(animLevel).fillMaxSize().background(barColor, RoundedCornerShape(11.dp)))
        }

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
            StatBox("Min", if (minLevel == 0f) "-" else "%.1f".format(minLevel), Modifier.weight(1f))
            StatBox("Max", if (maxLevel == 0f) "-" else "%.1f".format(maxLevel), Modifier.weight(1f))
            StatBox("Moy", if (averageLevel == 0f) "-" else "%.1f".format(averageLevel), Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth().background(NAVY, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Référence", color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Ref("Chuchotement", "~30 dB"); Ref("Conversation", "~60 dB")
            Ref("Trafic", "~80 dB"); Ref("Concert", "~110 dB")
        }

        if (!hasPermission) Text("Permission audio requise", color = Color(0xFFFF6B6B), fontSize = 14.sp, textAlign = TextAlign.Center)

        if (isRecording) {
            Button(onStopRecording, Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))) {
                Text("Arrêter", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        } else {
            Button(onStartRecording, Modifier.fillMaxWidth().height(48.dp), enabled = hasPermission,
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT)) {
                Text("Démarrer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BG) }
        }
        Button(onReset, Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NAVY)) {
            Text("Reset", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ACCENT) }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Box(modifier.background(NAVY, RoundedCornerShape(12.dp)).padding(12.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White, fontSize = 12.sp)
            Text(value, color = ACCENT, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Ref(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(value, color = ACCENT, fontSize = 12.sp)
    }
}
