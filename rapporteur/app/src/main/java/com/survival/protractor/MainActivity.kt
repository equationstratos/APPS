package com.survival.protractor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private val angleX = mutableFloatStateOf(0f)
    private val angleY = mutableFloatStateOf(0f)
    private val gravity = FloatArray(3)
    private val alpha = 0.85f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        setContent {
            ProtractorApp(angleX.floatValue, angleY.floatValue)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        gravity[0] = alpha * gravity[0] + (1 - alpha) * e.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * e.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * e.values[2]
        angleX.floatValue = Math.toDegrees(atan2(gravity[1].toDouble(), gravity[2].toDouble())).toFloat()
        angleY.floatValue = Math.toDegrees(atan2(gravity[0].toDouble(), gravity[2].toDouble())).toFloat()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

@Composable
fun ProtractorApp(angleX: Float, angleY: Float) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    Column(
        Modifier.fillMaxSize().background(bg).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Rapporteur", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Angle: ${angleX.toInt()}°", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(24.dp))

        Canvas(modifier = Modifier.size(320.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radius = size.minDimension / 2 - 20f

            drawCircle(navy, radius, Offset(cx, cy))
            drawCircle(accent, radius, Offset(cx, cy), style = Stroke(3f))

            // Graduations 360°
            for (deg in 0 until 360 step 5) {
                val rad = Math.toRadians(deg.toDouble() - 90)
                val isMajor = deg % 30 == 0
                val isMid = deg % 10 == 0
                val len = when { isMajor -> 20f; isMid -> 12f; else -> 6f }
                val x1 = cx + (radius - len) * cos(rad).toFloat()
                val y1 = cy + (radius - len) * sin(rad).toFloat()
                val x2 = cx + radius * cos(rad).toFloat()
                val y2 = cy + radius * sin(rad).toFloat()
                drawLine(if (isMajor) accent else Color.White.copy(0.5f),
                    Offset(x1, y1), Offset(x2, y2), strokeWidth = if (isMajor) 2f else 1f)
            }

            // Aiguille
            rotate(angleX, Offset(cx, cy)) {
                drawLine(Color(0xFFFF6B6B), Offset(cx, cy + radius - 30f), Offset(cx, cy - radius + 30f), strokeWidth = 4f)
            }

            drawCircle(accent, 10f, Offset(cx, cy))
        }
    }
}
