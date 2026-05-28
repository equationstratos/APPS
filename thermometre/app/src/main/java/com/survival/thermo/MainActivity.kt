package com.survival.thermo

import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity(), SensorEventListener {
    private var sm: SensorManager? = null
    private val ambientTemp = mutableFloatStateOf(Float.NaN)
    private val hasAmbient = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        hasAmbient.value = sm?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null
        setContent { ThermoApp(ambientTemp.floatValue, hasAmbient.value) }
    }

    override fun onResume() {
        super.onResume()
        sm?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)?.let {
            sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    override fun onPause() { super.onPause(); sm?.unregisterListener(this) }
    override fun onSensorChanged(e: SensorEvent) { ambientTemp.floatValue = e.values[0] }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

@Composable
fun ThermoApp(ambient: Float, hasAmbient: Boolean) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)
    val context = LocalContext.current

    var batteryTemp by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryTemp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            delay(2000)
        }
    }

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Thermomètre", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        if (hasAmbient && !ambient.isNaN()) {
            Box(Modifier.size(220.dp).background(navy, CircleShape), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f°".format(ambient), color = accent, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    Text("Ambiant", color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        } else {
            Text("Capteur ambiant non disponible", color = Color.White.copy(0.6f), fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Température batterie", color = Color.White, fontSize = 14.sp)
                Text("%.1f °C".format(batteryTemp), color = accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
