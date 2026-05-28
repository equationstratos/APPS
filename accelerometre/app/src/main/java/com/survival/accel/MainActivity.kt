package com.survival.accel

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
    private val x = mutableFloatStateOf(0f)
    private val y = mutableFloatStateOf(0f)
    private val z = mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        setContent { AccelApp(x.floatValue, y.floatValue, z.floatValue) }
    }

    override fun onResume() {
        super.onResume()
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }
    override fun onPause() { super.onPause(); sm?.unregisterListener(this) }
    override fun onSensorChanged(e: SensorEvent) {
        x.floatValue = e.values[0]; y.floatValue = e.values[1]; z.floatValue = e.values[2]
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

@Composable
fun AccelApp(x: Float, y: Float, z: Float) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)
    val mag = sqrt(x * x + y * y + z * z)

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Accéléromètre", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        AxisBar("X", x, accent, navy)
        Spacer(Modifier.height(16.dp))
        AxisBar("Y", y, accent, navy)
        Spacer(Modifier.height(16.dp))
        AxisBar("Z", z, accent, navy)
        Spacer(Modifier.height(32.dp))

        Box(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Magnitude", color = Color.White, fontSize = 12.sp)
                Text("%.2f m/s²".format(mag), color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AxisBar(label: String, value: Float, accent: Color, navy: Color) {
    val norm = (abs(value) / 20f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("%.2f".format(value), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(20.dp).background(navy, RoundedCornerShape(10.dp))) {
            Box(Modifier.fillMaxWidth(norm).fillMaxHeight().background(accent, RoundedCornerShape(10.dp)))
        }
    }
}
