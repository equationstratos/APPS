package com.survival.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity(), LocationListener {
    private var lm: LocationManager? = null
    private val speedKmh = mutableFloatStateOf(0f)
    private val maxSpeed = mutableFloatStateOf(0f)
    private val accuracy = mutableFloatStateOf(0f)
    private val hasFix = mutableStateOf(false)
    private val hasPerm = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        hasPerm.value = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasPerm.value = it
            if (it) startGps()
        }

        if (!hasPerm.value) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        else startGps()

        setContent {
            GpsApp(speedKmh.floatValue, maxSpeed.floatValue, accuracy.floatValue, hasFix.value, hasPerm.value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        try {
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, this)
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(loc: Location) {
        speedKmh.floatValue = loc.speed * 3.6f
        if (speedKmh.floatValue > maxSpeed.floatValue) maxSpeed.floatValue = speedKmh.floatValue
        accuracy.floatValue = loc.accuracy
        hasFix.value = true
    }

    override fun onPause() { super.onPause(); lm?.removeUpdates(this) }
}

@Composable
fun GpsApp(speed: Float, max: Float, accuracy: Float, hasFix: Boolean, hasPerm: Boolean) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Vitesse GPS", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        if (!hasPerm) {
            Text("Permission localisation requise", color = Color.White, fontSize = 16.sp)
            return@Column
        }

        Box(Modifier.size(280.dp).background(navy, CircleShape), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (hasFix) "%.0f".format(speed) else "--",
                    color = accent, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = Color.White, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Vitesse max", color = Color.White, fontSize = 14.sp)
                    Text("%.0f km/h".format(max), color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Précision GPS", color = Color.White, fontSize = 14.sp)
                    Text(if (hasFix) "±%.0f m".format(accuracy) else "Recherche...",
                        color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
