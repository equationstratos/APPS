package com.survival.torch

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Drives the camera flash in torch mode, which requires no CAMERA permission. */
class TorchController(private val cameraManager: CameraManager?) {

    val cameraId: String?

    /** Highest torch brightness level; 1 when the device cannot dim its torch. */
    val maxStrengthLevel: Int

    init {
        val camera = cameraManager?.let(::findFlashCamera)
        cameraId = camera?.id
        maxStrengthLevel = camera?.maxStrengthLevel ?: 1
    }

    val hasFlash: Boolean get() = cameraId != null

    val supportsStrength: Boolean get() = maxStrengthLevel > 1

    /** Returns false when the torch cannot be driven (camera busy or disconnected). */
    fun setTorch(on: Boolean, strengthLevel: Int = maxStrengthLevel): Boolean {
        val manager = cameraManager ?: return false
        val id = cameraId ?: return false
        return try {
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxStrengthLevel > 1) {
                manager.turnOnTorchWithStrengthLevel(id, strengthLevel.coerceIn(1, maxStrengthLevel))
            } else {
                manager.setTorchMode(id, on)
            }
            true
        } catch (e: CameraAccessException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun registerCallback(callback: CameraManager.TorchCallback) {
        if (hasFlash) cameraManager?.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun unregisterCallback(callback: CameraManager.TorchCallback) {
        if (hasFlash) cameraManager?.unregisterTorchCallback(callback)
    }
}

private class FlashCamera(val id: String, val maxStrengthLevel: Int)

private fun findFlashCamera(manager: CameraManager): FlashCamera? {
    val ids = try {
        manager.cameraIdList
    } catch (e: CameraAccessException) {
        return null
    }
    var fallback: FlashCamera? = null
    for (id in ids) {
        val characteristics = try {
            manager.getCameraCharacteristics(id)
        } catch (e: CameraAccessException) {
            continue
        } catch (e: IllegalArgumentException) {
            continue
        }
        if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
        val camera = FlashCamera(id, readMaxStrengthLevel(characteristics))
        if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
            return camera
        }
        if (fallback == null) fallback = camera
    }
    return fallback
}

private fun readMaxStrengthLevel(characteristics: CameraCharacteristics): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    } else {
        1
    }
