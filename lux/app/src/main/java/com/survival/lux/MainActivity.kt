package com.survival.lux

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

class MainActivity : ComponentActivity(), SensorEventListener {
    private var sm: SensorManager? = null
    private val lux = mutableFloatStateOf(0f)
    private val hasSensor = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        hasSensor.value = sm?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
        setContent { LuxApp(lux.floatValue, hasSensor.value) }
    }

    override fun onResume() {
        super.onResume()
        sm?.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() { super.onPause(); sm?.unregisterListener(this) }
    override fun onSensorChanged(e: SensorEvent) { lux.floatValue = e.values[0] }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

@Composable
fun LuxApp(lux: Float, hasSensor: Boolean) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    val desc = when {
        lux < 1 -> "Nuit noire"
        lux < 50 -> "Très sombre"
        lux < 200 -> "Pièce sombre"
        lux < 500 -> "Pièce éclairée"
        lux < 1000 -> "Bureau"
        lux < 5000 -> "Jour nuageux"
        lux < 25000 -> "Jour clair"
        else -> "Plein soleil"
    }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Luminosité (Lux)", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        if (!hasSensor) {
            Text("Capteur de lumière\nnon disponible", color = Color.White, fontSize = 18.sp)
        } else {
            Box(Modifier.size(260.dp).background(navy, CircleShape), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.0f".format(lux), color = accent, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    Text("lx", color = Color.White, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            Box(Modifier.background(navy, RoundedCornerShape(12.dp)).padding(16.dp)) {
                Text(desc, color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
