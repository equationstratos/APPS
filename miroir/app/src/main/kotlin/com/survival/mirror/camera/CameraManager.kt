package com.survival.mirror.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture

class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
    }

    fun setupCamera(
        lifecycleOwner: LifecycleOwner,
        previewSurfaceProvider: Preview.SurfaceProvider
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    // Build preview use case
                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewSurfaceProvider)
                        }

                    // Select front camera
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    // Bind to lifecycle
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )

                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            },
            context.mainExecutor
        )
    }
}
