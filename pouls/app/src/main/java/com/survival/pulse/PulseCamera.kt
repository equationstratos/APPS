package com.survival.pulse

import android.content.Context
import android.graphics.ImageFormat
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Small frames are plenty to average a colour, and cheap to read 30 times per second. */
private val ANALYSIS_SIZE = Size(320, 240)

private const val START_FAILED =
    "Impossible de démarrer la caméra. Elle est peut-être utilisée par une autre application."
private const val NO_CAMERA = "Aucune caméra utilisable sur cet appareil : utilisez le mode manuel."

/**
 * One camera measurement: binds an [ImageAnalysis] use case to [lifecycleOwner], turns the torch
 * on when there is one, averages the centre of each frame on a background thread, feeds a
 * [PulseSession] and posts its snapshots to the main thread. Create one per measurement; [stop]
 * turns the torch off and releases the camera. [start] and [stop] must be called on the main
 * thread; the callbacks run on it.
 */
internal class PulseCamera(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onStarted: (lens: Lens, torch: Boolean) -> Unit,
    private val onSnapshot: (SessionSnapshot) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val session = PulseSession()

    @Volatile
    private var active = false
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var camera: Camera? = null

    /** Analysis thread only: the final snapshot is posted once. */
    private var finishedPosted = false

    fun start() {
        active = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (active) {
                try {
                    bind(future.get())
                } catch (e: Exception) {
                    release()
                    fail(START_FAILED)
                }
            }
        }, mainExecutor)
    }

    fun stop() {
        active = false
        release()
        analysisExecutor.shutdown()
    }

    /** Torch off, analyzer removed and use case unbound; safe to call several times. */
    private fun release() {
        val cam = camera
        if (cam != null) {
            try {
                if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(false)
            } catch (e: RuntimeException) {
                // The camera is already closed: its torch is off.
            }
        }
        val useCase = analysis
        if (useCase != null) {
            useCase.clearAnalyzer()
            try {
                provider?.unbind(useCase)
            } catch (e: RuntimeException) {
                // Already released with the lifecycle.
            }
        }
        analysis = null
        camera = null
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        provider = cameraProvider
        val lens = when {
            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> Lens.BACK
            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> Lens.FRONT
            else -> null
        }
        if (lens == null) {
            fail(NO_CAMERA)
            return
        }
        val selector = if (lens == Lens.BACK) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        val useCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
        useCase.setAnalyzer(analysisExecutor) { image -> analyze(image) }
        cameraProvider.unbindAll()
        analysis = useCase
        val bound = cameraProvider.bindToLifecycle(lifecycleOwner, selector, useCase)
        camera = bound
        val torch = bound.cameraInfo.hasFlashUnit()
        if (torch) bound.cameraControl.enableTorch(true)
        onStarted(lens, torch)
    }

    private fun fail(message: String) {
        if (!active) return
        active = false
        onError(message)
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!active) return
            val stats = statsOf(image) ?: return
            session.onFrame(stats)
            val finished = session.isFinished
            if (finished && finishedPosted) return
            if (finished) finishedPosted = true
            val snapshot = session.snapshot()
            mainExecutor.execute { if (active) onSnapshot(snapshot) }
        } catch (e: RuntimeException) {
            // A frame that cannot be read is skipped: the session bridges short gaps.
        } finally {
            image.close()
        }
    }

    private fun statsOf(image: ImageProxy): FrameStats? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val planes = image.planes
        if (planes.size < 3) return null
        return measureFrame(
            timestampNs = image.imageInfo.timestamp,
            width = image.width,
            height = image.height,
            y = Plane(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride),
            u = Plane(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride),
            v = Plane(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride),
        )
    }
}
