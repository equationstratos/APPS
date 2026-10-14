package com.survival.mirror

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner

@Stable
internal class FrontCameraState {
    var camera: Camera? by mutableStateOf(null)
    var bindFailed by mutableStateOf(false)
    var bindAttempt by mutableIntStateOf(0)
    var streaming by mutableStateOf(false)
    var zoomRatio by mutableFloatStateOf(1f)
    var maxZoomRatio by mutableFloatStateOf(1f)
    var errorCode: Int? by mutableStateOf(null)

    val zoomAvailable: Boolean
        get() = camera != null && maxZoomRatio > 1.01f

    fun retry() {
        bindFailed = false
        errorCode = null
        bindAttempt++
    }
}

/**
 * Binds the front camera to [previewView] once for the current lifecycle owner and exposes its
 * live state (zoom range, stream state, errors such as "camera in use").
 */
@Composable
internal fun rememberFrontCamera(previewView: PreviewView): FrontCameraState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { FrontCameraState() }

    DisposableEffect(lifecycleOwner, previewView, state.bindAttempt) {
        val preview = Preview.Builder().build()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                preview.setSurfaceProvider(previewView.surfaceProvider)
                state.camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview
                )
                state.bindFailed = false
            } catch (e: Exception) {
                state.camera = null
                state.bindFailed = true
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbind(preview)
            state.camera = null
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val streamObserver = Observer<PreviewView.StreamState> { streamState ->
            state.streaming = streamState == PreviewView.StreamState.STREAMING
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        onDispose { previewView.previewStreamState.removeObserver(streamObserver) }
    }

    val camera = state.camera
    DisposableEffect(lifecycleOwner, camera) {
        if (camera == null) return@DisposableEffect onDispose { }
        val cameraInfo = camera.cameraInfo
        val zoomObserver = Observer<ZoomState> { zoom ->
            state.zoomRatio = zoom.zoomRatio
            state.maxZoomRatio = zoom.maxZoomRatio
        }
        val stateObserver = Observer<CameraState> { cameraState ->
            state.errorCode = cameraState.error?.code
        }
        cameraInfo.zoomState.observe(lifecycleOwner, zoomObserver)
        cameraInfo.cameraState.observe(lifecycleOwner, stateObserver)
        onDispose {
            cameraInfo.zoomState.removeObserver(zoomObserver)
            cameraInfo.cameraState.removeObserver(stateObserver)
        }
    }

    return state
}
