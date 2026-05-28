package com.survival.metal

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private var sm: SensorManager? = null
    private val magneticField = mutableFloatStateOf(0f)
    private val hasSensor = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        hasSensor.value = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        setContent { MetalApp(magneticField.floatValue, hasSensor.value) }
    }

    override fun onResume() {
        super.onResume()
        sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }
    override fun onPause() { super.onPause(); sm?.unregisterListener(this) }
    override fun onSensorChanged(e: SensorEvent) {
        magneticField.floatValue = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

@Composable
fun MetalApp(magnitude: Float, hasSensor: Boolean) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    // Champ magnétique terrestre normal: 25-65 µT
    val isMetal = magnitude > 80
    val ratio = (magnitude / 200f).coerceIn(0f, 1f)

    val color = when {
        magnitude < 50 -> accent
        magnitude < 80 -> Color(0xFFFFD700)
        magnitude < 150 -> Color(0xFFFFA500)
        else -> Color(0xFFFF6B6B)
    }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Détecteur de Métaux", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        if (!hasSensor) {
            Text("Magnétomètre\nnon disponible", color = Color.White, fontSize = 18.sp)
            return@Column
        }

        Box(Modifier.size(260.dp).background(navy, CircleShape).padding(8.dp), Alignment.Center) {
            Box(Modifier.size((220 + ratio * 30).dp).background(color, CircleShape), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.0f".format(magnitude), color = bg, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    Text("µT", color = bg, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(Modifier.fillMaxWidth().background(if (isMetal) color else navy, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text(
                if (isMetal) "MÉTAL DÉTECTÉ" else "Aucune détection",
                color = if (isMetal) bg else accent,
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
