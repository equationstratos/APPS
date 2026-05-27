package com.survival.torch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FlashManager(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null

    init {
        try {
            val cameraIds = cameraManager.cameraIdList
            if (cameraIds.isNotEmpty()) {
                cameraId = cameraIds[0]
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun isFlashAvailable(): Boolean = cameraId != null

    suspend fun toggleFlash(enabled: Boolean) = withContext(Dispatchers.Main) {
        cameraId?.let {
            try {
                cameraManager.setTorchMode(it, enabled)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    fun release() {
        try {
            cameraId?.let {
                cameraManager.setTorchMode(it, false)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
}
