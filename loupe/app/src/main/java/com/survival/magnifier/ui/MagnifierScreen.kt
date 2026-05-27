package com.survival.magnifier.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

@Composable
fun MagnifierScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var isFrozen by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    // Get max zoom from device camera
    LaunchedEffect(Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val backCameraId = cameraManager.cameraIdList.firstOrNull() ?: return@LaunchedEffect

        try {
            val characteristics = cameraManager.getCameraCharacteristics(backCameraId)
            maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                ?.toFloat() ?: 4f
        } catch (e: Exception) {
            maxZoom = 4f
        }
    }

    // Setup camera provider
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@LaunchedEffect
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    // Update zoom when slider changes
    LaunchedEffect(zoom, isFrozen, camera) {
        if (!isFrozen && camera != null) {
            try {
                camera!!.cameraControl.setZoomRatio(zoom)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Update flashlight
    LaunchedEffect(flashEnabled, camera) {
        camera?.let {
            try {
                it.cameraControl.enableTorch(flashEnabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Camera preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            CameraPreview(cameraProvider, lifecycleOwner) { boundCamera ->
                camera = boundCamera
            }

            // Circle overlay for magnifier effect
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.Center)
                    .border(4.dp, Color(0xFF4A9FFF), CircleShape)
            )
        }

        // Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color(0xFF1A1A2E))
        ) {
            // Zoom slider
            Text(
                "Zoom: ${"%.1f".format(zoom)}x",
                color = Color(0xFF4A9FFF),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = zoom,
                onValueChange = { newZoom ->
                    zoom = newZoom
                },
                valueRange = 1f..maxZoom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4A9FFF),
                    activeTrackColor = Color(0xFF4A9FFF),
                    inactiveTrackColor = Color(0xFF0F3460)
                )
            )

            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash toggle
                Button(
                    onClick = { flashEnabled = !flashEnabled },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (flashEnabled) Color(0xFF4A9FFF) else Color(0xFF0F3460)
                    )
                ) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                        contentDescription = "Toggle Flash",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Freeze button
                Button(
                    onClick = { isFrozen = !isFrozen },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFrozen) Color(0xFF4A9FFF) else Color(0xFF0F3460)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Freeze/Capture",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Info text
            Text(
                text = if (isFrozen) "View frozen" else "Live preview",
                color = Color(0xFF4A9FFF),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun CameraPreview(
    cameraProvider: ProcessCameraProvider?,
    lifecycleOwner: LifecycleOwner,
    onCameraReady: (androidx.camera.core.Camera) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(cameraProvider) {
        if (cameraProvider == null) return@LaunchedEffect

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@LaunchedEffect
        }

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            onCameraReady(camera)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}
