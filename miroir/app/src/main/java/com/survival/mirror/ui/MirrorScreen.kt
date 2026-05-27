package com.survival.mirror.ui

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

private val BG = Color(0xFF1A1A2E)
private val ACCENT = Color(0xFF4A9FFF)
private val NAVY = Color(0xFF0F3460)

@Composable
fun MirrorScreen(hasPermission: Boolean, lifecycleOwner: LifecycleOwner) {
    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(BG), Alignment.Center) {
            Text("Permission caméra requise", color = Color.White, fontSize = 18.sp)
        }
        return
    }

    var mirrored by remember { mutableStateOf(true) }

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
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = if (mirrored) -1f else 1f)
        )

        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(BG.copy(alpha = 0.7f)).padding(16.dp),
            Arrangement.Center
        ) {
            Button(
                onClick = { mirrored = !mirrored },
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (mirrored) "Miroir: ON" else "Miroir: OFF", color = BG) }
        }
    }
}
