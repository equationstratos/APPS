package com.survival.mirror

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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
        setContent { MiroirApp(this) }
    }
}

@Composable
fun MiroirApp(owner: ComponentActivity) {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    var mirrored by remember { mutableStateOf(true) }
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(owner, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }

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
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }, Modifier.fillMaxSize().graphicsLayer(scaleX = if (mirrored) -1f else 1f))

        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(bg.copy(alpha = 0.7f)).padding(16.dp), Arrangement.Center) {
            Button(onClick = { mirrored = !mirrored },
                colors = ButtonDefaults.buttonColors(containerColor = accent), shape = RoundedCornerShape(8.dp)) {
                Text(if (mirrored) "Miroir: ON" else "Miroir: OFF", color = bg)
            }
        }
    }
}
