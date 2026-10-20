package com.survival.whistle

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread

/** Id of the camera with a flash (back camera preferred), or null when the device has none. */
fun findFlashCamera(manager: CameraManager?): String? {
    if (manager == null) return null
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
        if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) return id
        if (fallback == null) fallback = id
    }
    return fallback
}

fun hasFlash(context: Context): Boolean =
    findFlashCamera(context.getSystemService(CameraManager::class.java)) != null

/**
 * Drives the torch (no CAMERA permission needed) on its own thread: setTorchMode is a binder call
 * that must never delay the audio thread. Commands carry the playback session, so a late "off"
 * from a finished playback cannot switch off the torch of the next one.
 */
class TorchWorker(context: Context, private val onState: (TorchState) -> Unit) {
    private val manager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("sifflet-torch").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var session = 0

    // Torch thread only.
    private var cameraId: String? = null
    private var searched = false
    private var lit = false

    /** Starts a new playback session with the torch off. */
    fun begin(newSession: Int) {
        session = newSession
        handler.post {
            apply(false)
            onState(TorchState.Off)
        }
    }

    fun set(forSession: Int, on: Boolean) {
        handler.post { if (forSession == session) apply(on) }
    }

    /** Switches the torch off and ends the thread. */
    fun shutdown() {
        session = -1
        handler.post { apply(false) }
        thread.quitSafely()
    }

    private fun apply(on: Boolean) {
        if (on == lit) return
        if (!searched) {
            searched = true
            cameraId = findFlashCamera(manager)
        }
        val id = cameraId
        val camera = manager
        if (id == null || camera == null) {
            if (on) onState(TorchState.Unavailable)
            return
        }
        try {
            camera.setTorchMode(id, on)
            lit = on
            onState(if (on) TorchState.Active else TorchState.Off)
        } catch (e: CameraAccessException) {
            // Camera in use by another app, or disconnected.
            lit = false
            if (on) onState(TorchState.Unavailable)
        } catch (e: IllegalArgumentException) {
            lit = false
            if (on) onState(TorchState.Unavailable)
        }
    }
}
