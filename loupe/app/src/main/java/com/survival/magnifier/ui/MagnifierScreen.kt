package com.survival.magnifier.ui

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

private val BG = Color(0xFF1A1A2E)
private val ACCENT = Color(0xFF4A9FFF)
private val NAVY = Color(0xFF0F3460)

@Composable
fun LoupeScreen(hasPermission: Boolean, lifecycleOwner: LifecycleOwner) {
    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(BG), Alignment.Center) {
            Text("Permission caméra requise", color = Color.White, fontSize = 18.sp)
        }
        return
    }

    var zoom by remember { mutableFloatStateOf(0f) }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    Box(Modifier.fillMaxSize().background(BG)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                        try {
                            provider.unbindAll()
                            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(BG.copy(alpha = 0.8f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Zoom: ${"%.1f".format(1f + zoom * 9f)}x", color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = zoom, onValueChange = { zoom = it; camera?.cameraControl?.setLinearZoom(it) },
                colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = NAVY)
            )
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { torchOn = !torchOn; camera?.cameraControl?.enableTorch(torchOn) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (torchOn) ACCENT else NAVY),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                ) { Text(if (torchOn) "Lampe ON" else "Lampe OFF", color = Color.White) }
            }
        }
    }
}
