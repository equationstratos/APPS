package com.survival.morse

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Camera flash in torch mode: needs no CAMERA permission. */
internal class TorchController(private val manager: CameraManager?) {
    private val cameraId: String? = manager?.let(::findFlashCamera)

    val hasFlash: Boolean get() = cameraId != null

    /** Returns false when the torch cannot be driven (busy camera, disconnected flash). */
    fun set(on: Boolean): Boolean {
        val cameraManager = manager ?: return false
        val id = cameraId ?: return false
        return try {
            cameraManager.setTorchMode(id, on)
            true
        } catch (e: CameraAccessException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

private fun findFlashCamera(manager: CameraManager): String? {
    val ids = try {
        manager.cameraIdList
    } catch (e: CameraAccessException) {
        return null
    } catch (e: RuntimeException) {
        return null
    }
    var fallback: String? = null
    for (id in ids) {
        val characteristics = try {
            manager.getCameraCharacteristics(id)
        } catch (e: CameraAccessException) {
            continue
        } catch (e: IllegalArgumentException) {
            continue
        }
        if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
        if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
            return id
        }
        if (fallback == null) fallback = id
    }
    return fallback
}

/** Vibration motor, if the device has one. */
internal class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }

    val available: Boolean = vibrator?.hasVibrator() == true

    fun pulse(durationMs: Long) {
        val target = vibrator ?: return
        if (!available || durationMs <= 0L) return
        try {
            target.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: RuntimeException) {
            // Vibration refused by the system: the other outputs keep going.
        }
    }

    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (e: RuntimeException) {
            // Nothing to stop.
        }
    }
}
