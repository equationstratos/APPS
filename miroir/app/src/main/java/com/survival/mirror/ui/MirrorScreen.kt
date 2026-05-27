package com.survival.mirror.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.survival.mirror.camera.CameraManager

@Composable
fun MirrorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isFrozen by remember { mutableStateOf(false) }
    var isMirrored by remember { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            // Permission request would be handled by the activity
            cameraPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        if (cameraPermissionGranted) {
            CameraPreview(
                isFrozen = isFrozen,
                isMirrored = isMirrored,
                brightness = brightness,
                lifecycleOwner = lifecycleOwner,
                context = context,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PermissionDeniedScreen(modifier = Modifier.fillMaxSize())
        }

        // Controls overlay at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Brightness slider
            BrightnessControl(brightness) { brightness = it }

            // Control buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Freeze button
                Button(
                    onClick = { isFrozen = !isFrozen },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4A9FFF),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFrozen) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isFrozen) "Resume" else "Freeze",
                        modifier = Modifier.width(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isFrozen) "Resume" else "Freeze")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Mirror toggle button
                Button(
                    onClick = { isMirrored = !isMirrored },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F3460),
                        contentColor = Color(0xFF4A9FFF)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlipToFront,
                        contentDescription = "Toggle Mirror",
                        modifier = Modifier.width(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mirror")
                }
            }
        }
    }
}

@Composable
private fun BrightnessControl(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Brightness: ${(brightness * 100).toInt()}%",
            color = Color.White,
            fontSize = 12.sp
        )
        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = 0.3f..1.5f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4A9FFF),
                activeTrackColor = Color(0xFF4A9FFF),
                inactiveTrackColor = Color(0xFF0F3460)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun CameraPreview(
    isFrozen: Boolean,
    isMirrored: Boolean,
    brightness: Float,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    context: Context,
    modifier: Modifier = Modifier
) {
    val cameraManager = remember { CameraManager(context) }
    var cachedFrame by remember { mutableStateOf<Surface?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = if (isMirrored) -1f else 1f,
                    alpha = brightness
                ),
            update = { previewView ->
                if (!isFrozen) {
                    cameraManager.setupCamera(lifecycleOwner, previewView.surfaceProvider)
                }
            }
        )
    }
}

@Composable
private fun PermissionDeniedScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF1A1A2E))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera Permission Required",
                color = Color.White,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Please grant camera permission to use the mirror feature.",
                color = Color(0xFF4A9FFF),
                fontSize = 14.sp
            )
        }
    }
}
