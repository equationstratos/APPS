package com.survival.magnifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
        setContent { LoupeApp(this) }
    }
}

@Composable
fun LoupeApp(owner: ComponentActivity) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(owner, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var torch by remember { mutableStateOf(false) }
    var cam by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    if (!hasPerm) {
        Box(Modifier.fillMaxSize().background(bg), Alignment.Center) {
            Text("Permission caméra requise", color = Color.White, fontSize = 18.sp)
        }
        return
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        AndroidView(factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                    provider.unbindAll()
                    cam = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }, Modifier.fillMaxSize())

        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(bg.copy(alpha = 0.8f)).padding(horizontal = 16.dp, vertical = 32.dp)) {
            Text("Zoom: ${"%.1f".format(1f + zoom * 9f)}x", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Slider(value = zoom, onValueChange = { zoom = it; cam?.cameraControl?.setLinearZoom(it) },
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = navy))
            Button(onClick = { torch = !torch; cam?.cameraControl?.enableTorch(torch) },
                colors = ButtonDefaults.buttonColors(containerColor = if (torch) accent else navy),
                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(if (torch) "Lampe ON" else "Lampe OFF", color = Color.White)
            }
        }
    }
}
