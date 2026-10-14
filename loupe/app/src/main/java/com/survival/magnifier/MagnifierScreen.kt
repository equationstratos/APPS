package com.survival.magnifier

import android.content.Context
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

private const val MAX_FROZEN_SCALE = 6f

/** Colour matrices (4x5, offsets in 0..255) shared by the live preview and the frozen image. */
private enum class ReadingFilter(val label: String, val matrix: FloatArray?) {
    Normal("Normal", null),
    Contrast("Contraste", highContrastGrayMatrix()),
    Negative(
        "Négatif",
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    );

    val composeFilter: ColorFilter? = matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it.copyOf())) }
}

private fun highContrastGrayMatrix(): FloatArray {
    val contrast = 1.8f
    val offset = 128f * (1f - contrast)
    val r = 0.2126f * contrast
    val g = 0.7152f * contrast
    val b = 0.0722f * contrast
    return floatArrayOf(
        r, g, b, 0f, offset,
        r, g, b, 0f, offset,
        r, g, b, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** A hardware layer paint is the only way to colour-filter the TextureView inside PreviewView on API 26. */
private fun View.applyReadingFilter(filter: ReadingFilter) {
    val matrix = filter.matrix
    if (matrix == null) {
        setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
    }
}

private fun createPreviewView(context: Context): PreviewView = PreviewView(context).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    scaleType = PreviewView.ScaleType.FILL_CENTER
}

private fun cameraIssueMessage(code: Int): String = when (code) {
    CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE ->
        "Caméra utilisée par une autre application. Fermez-la pour reprendre."
    CameraState.ERROR_CAMERA_DISABLED -> "La caméra est désactivée sur cet appareil."
    else -> "Caméra momentanément indisponible, nouvelle tentative…"
}

private fun formatZoom(ratio: Float): String = String.format(Locale.FRANCE, "×%.1f", ratio)

@Composable
internal fun MagnifierScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) { createPreviewView(context) }

    var bindAttempt by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var startError by remember { mutableStateOf<String?>(null) }

    var cameraOpen by remember { mutableStateOf(false) }
    var cameraIssue by remember { mutableStateOf<String?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var sliderDragging by remember { mutableStateOf(false) }
    // Zoom chosen by the user; CameraX resets zoom whenever the camera closes, so it is re-applied on reopen.
    var savedLinearZoom by rememberSaveable { mutableFloatStateOf(0f) }
    var filter by rememberSaveable { mutableStateOf(ReadingFilter.Normal) }

    var frozenImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var frozenScale by remember { mutableFloatStateOf(1f) }
    var frozenOffset by remember { mutableStateOf(Offset.Zero) }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    val focusProgress = remember { Animatable(0f) }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(lifecycleOwner, previewView, bindAttempt) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var active = true
        providerFuture.addListener({
            if (active) {
                try {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
                    if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    } else {
                        startError = "Caméra arrière non disponible sur cet appareil."
                    }
                } catch (e: Exception) {
                    startError = "Impossible de démarrer la caméra. Elle est peut-être utilisée par une autre application."
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            provider?.unbindAll()
            camera = null
            cameraOpen = false
            torchOn = false
        }
    }

    DisposableEffect(camera, lifecycleOwner) {
        val cam = camera ?: return@DisposableEffect onDispose { }
        val info = cam.cameraInfo
        val zoomObserver = Observer<ZoomState> { state ->
            zoomRatio = state.zoomRatio
            minZoom = state.minZoomRatio
            maxZoom = state.maxZoomRatio
            if (!sliderDragging) sliderValue = state.linearZoom
            if (cameraOpen && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                savedLinearZoom = state.linearZoom
            }
        }
        val torchObserver = Observer<Int> { state -> torchOn = state == TorchState.ON }
        val stateObserver = Observer<CameraState> { state ->
            val open = state.type == CameraState.Type.OPEN
            if (open && !cameraOpen && savedLinearZoom > 0f) cam.cameraControl.setLinearZoom(savedLinearZoom)
            cameraOpen = open
            cameraIssue = state.error?.let { cameraIssueMessage(it.code) }
        }
        info.zoomState.observe(lifecycleOwner, zoomObserver)
        info.torchState.observe(lifecycleOwner, torchObserver)
        info.cameraState.observe(lifecycleOwner, stateObserver)
        onDispose {
            info.zoomState.removeObserver(zoomObserver)
            info.torchState.removeObserver(torchObserver)
            info.cameraState.removeObserver(stateObserver)
        }
    }

    DisposableEffect(previewView, lifecycleOwner) {
        val observer = Observer<PreviewView.StreamState> { state ->
            streaming = state == PreviewView.StreamState.STREAMING
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        focusProgress.snapTo(0f)
        focusProgress.animateTo(1f, tween(durationMillis = 250))
        delay(700)
        focusPoint = null
    }

    val isFrozen = frozenImage != null
    BackHandler(enabled = isFrozen) { frozenImage = null }

    Box(Modifier.fillMaxSize().background(Bg)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { it.applyReadingFilter(filter) },
        )

        frozenImage?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Image figée",
                modifier = Modifier
                    .fillMaxSize()
                    .background(Bg)
                    .graphicsLayer {
                        scaleX = frozenScale
                        scaleY = frozenScale
                        translationX = frozenOffset.x
                        translationY = frozenOffset.y
                    },
                contentScale = ContentScale.Crop,
                colorFilter = filter.composeFilter,
            )
        }

        // Gesture layer, same bounds as the PreviewView so tap coordinates map 1:1 to its metering points.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(camera, isFrozen) {
                    val cam = camera
                    if (isFrozen) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val scale = (frozenScale * zoom).coerceIn(1f, MAX_FROZEN_SCALE)
                            val maxX = size.width * (scale - 1f) / 2f
                            val maxY = size.height * (scale - 1f) / 2f
                            frozenScale = scale
                            frozenOffset = Offset(
                                (frozenOffset.x + pan.x).coerceIn(-maxX, maxX),
                                (frozenOffset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                        }
                    } else if (cam != null) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val state = cam.cameraInfo.zoomState.value
                            if (state != null) {
                                val target = (state.zoomRatio * zoom).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                                cam.cameraControl.setZoomRatio(target)
                            }
                        }
                    }
                }
                .pointerInput(camera, isFrozen) {
                    val cam = camera
                    if (isFrozen) {
                        detectTapGestures(onDoubleTap = {
                            frozenScale = 1f
                            frozenOffset = Offset.Zero
                        })
                    } else if (cam != null) {
                        detectTapGestures(onTap = { offset ->
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            cam.cameraControl.startFocusAndMetering(action)
                            focusPoint = offset
                            focusTick++
                        })
                    }
                }
        )

        focusPoint?.let { point ->
            Canvas(Modifier.fillMaxSize()) {
                val radius = 34.dp.toPx() * (1.35f - 0.35f * focusProgress.value)
                drawCircle(color = Color.White, radius = radius, center = point, style = Stroke(width = 2.dp.toPx()))
            }
        }

        val error = startError
        if (error != null) {
            InfoScreen(title = "Caméra indisponible", message = error, titleColor = Danger) {
                SurvieButton(
                    "Réessayer",
                    onClick = {
                        startError = null
                        bindAttempt++
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                HeaderCard(
                    subtitle = if (isFrozen) {
                        "Image figée : pincez pour agrandir, touchez deux fois pour réinitialiser"
                    } else {
                        "Pincez pour zoomer, touchez pour faire la mise au point"
                    }
                )
                cameraIssue?.let { StatusBanner(it, Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.weight(1f))
                val cam = camera
                ControlPanel(
                    zoomLabel = formatZoom(zoomRatio),
                    sliderValue = sliderValue,
                    zoomEnabled = cam != null && maxZoom > minZoom && !isFrozen,
                    zoomNote = if (cam != null && maxZoom <= minZoom) "Zoom non pris en charge par cette caméra." else null,
                    onZoomChange = { value ->
                        sliderDragging = true
                        sliderValue = value
                        cam?.cameraControl?.setLinearZoom(value)
                    },
                    onZoomChangeFinished = { sliderDragging = false },
                    filter = filter,
                    onFilterChange = { filter = it },
                    showTorch = cam?.cameraInfo?.hasFlashUnit() == true,
                    torchOn = torchOn,
                    onTorchToggle = { cam?.cameraControl?.enableTorch(!torchOn) },
                    frozen = isFrozen,
                    canFreeze = isFrozen || streaming,
                    onFreezeToggle = {
                        if (isFrozen) {
                            frozenImage = null
                        } else {
                            previewView.bitmap?.let { bitmap ->
                                frozenScale = 1f
                                frozenOffset = Offset.Zero
                                focusPoint = null
                                frozenImage = bitmap.asImageBitmap()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(subtitle: String) {
    ScreenHeader(
        subtitle = subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun StatusBanner(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = Warning,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Card.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ControlPanel(
    zoomLabel: String,
    sliderValue: Float,
    zoomEnabled: Boolean,
    zoomNote: String?,
    onZoomChange: (Float) -> Unit,
    onZoomChangeFinished: () -> Unit,
    filter: ReadingFilter,
    onFilterChange: (ReadingFilter) -> Unit,
    showTorch: Boolean,
    torchOn: Boolean,
    onTorchToggle: () -> Unit,
    frozen: Boolean,
    canFreeze: Boolean,
    onFreezeToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card.copy(alpha = 0.92f))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Zoom", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                zoomLabel,
                color = Accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onZoomChange,
            onValueChangeFinished = onZoomChangeFinished,
            enabled = zoomEnabled,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Navy,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Zoom" },
        )
        if (zoomNote != null) {
            Text(zoomNote, color = TextSecondary, fontSize = 13.sp)
        }
        Text("Filtre de lecture", color = TextSecondary, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingFilter.entries.forEach { option ->
                SurvieButton(
                    option.label,
                    onClick = { onFilterChange(option) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { selected = option == filter },
                    primary = option == filter,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showTorch) {
                SurvieButton(
                    if (torchOn) "Éteindre la lampe" else "Allumer la lampe",
                    onClick = onTorchToggle,
                    modifier = Modifier.weight(1f),
                    primary = false,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            SurvieButton(
                if (frozen) "Reprendre" else "Figer",
                onClick = onFreezeToggle,
                modifier = Modifier.weight(1f),
                enabled = canFreeze,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}
