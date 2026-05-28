package com.survival.pedometer

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private val steps = mutableIntStateOf(0)
    private val initialSteps = mutableIntStateOf(-1)
    private val accelSteps = mutableIntStateOf(0)
    private val useAccel = mutableStateOf(false)
    private var lastMagnitude = 0f
    private var lastStepTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        if (android.os.Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        setContent {
            PedometerApp(
                steps = if (useAccel.value) accelSteps.intValue else steps.intValue,
                onReset = {
                    initialSteps.intValue = -1
                    steps.intValue = 0
                    accelSteps.intValue = 0
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager?.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            useAccel.value = true
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onPause() { super.onPause(); sensorManager?.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val total = e.values[0].toInt()
                if (initialSteps.intValue < 0) initialSteps.intValue = total
                steps.intValue = total - initialSteps.intValue
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                val delta = m - lastMagnitude
                val now = System.currentTimeMillis()
                if (delta > 6f && now - lastStepTime > 250) {
                    accelSteps.intValue++
                    lastStepTime = now
                }
                lastMagnitude = m
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

@Composable
fun PedometerApp(steps: Int, onReset: () -> Unit) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    val km = steps * 0.7f / 1000f
    val cal = (steps * 0.04f).toInt()

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Podomètre", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Box(Modifier.size(260.dp).background(navy, CircleShape), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$steps", color = accent, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text("pas", color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.2f".format(km), color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("km", color = Color.White, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$cal", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("kcal", color = Color.White, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(Modifier.background(accent, RoundedCornerShape(8.dp))
            .clickable { onReset() }.padding(horizontal = 32.dp, vertical = 12.dp)) {
            Text("Reset", color = bg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
