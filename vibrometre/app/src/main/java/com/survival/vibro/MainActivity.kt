package com.survival.vibro

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private var sm: SensorManager? = null
    private val current = mutableFloatStateOf(0f)
    private val maxV = mutableFloatStateOf(0f)
    private var lastMag = 9.8f
    private val alpha = 0.8f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        setContent {
            VibroApp(current.floatValue, maxV.floatValue, onReset = { maxV.floatValue = 0f })
        }
    }

    override fun onResume() {
        super.onResume()
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    override fun onPause() { super.onPause(); sm?.unregisterListener(this) }
    override fun onSensorChanged(e: SensorEvent) {
        val mag = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        val vibration = abs(mag - lastMag)
        lastMag = alpha * lastMag + (1 - alpha) * mag
        current.floatValue = vibration
        if (vibration > maxV.floatValue) maxV.floatValue = vibration
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

@Composable
fun VibroApp(current: Float, max: Float, onReset: () -> Unit) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    val intensity = when {
        current < 0.5 -> "Stable"
        current < 2 -> "Légère"
        current < 5 -> "Modérée"
        current < 10 -> "Forte"
        else -> "Très forte"
    }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Vibromètre", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Box(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(16.dp)).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("%.2f".format(current), color = accent, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text("m/s²", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Text(intensity, color = accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(24.dp))

        val norm = (current / 15f).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(20.dp).background(navy, RoundedCornerShape(10.dp))) {
            Box(Modifier.fillMaxWidth(norm).fillMaxHeight().background(accent, RoundedCornerShape(10.dp)))
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Pic max: %.2f m/s²".format(max), color = Color.White, fontSize = 14.sp)
            Box(Modifier.background(accent, RoundedCornerShape(8.dp))
                .clickable { onReset() }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Reset", color = bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
