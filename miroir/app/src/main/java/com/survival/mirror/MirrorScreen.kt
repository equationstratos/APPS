package com.survival.mirror

import android.view.Window
import android.view.WindowManager
import androidx.camera.core.CameraState
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import java.util.Locale

private val RingLightWidth = 28.dp

@Composable
internal fun MirrorScreen() {
    val context = LocalContext.current
    // COMPATIBLE = TextureView: unlike SurfaceView, it honours the graphicsLayer flip below.
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Card.toArgb())
        }
    }
    val cameraState = rememberFrontCamera(previewView)

    var trueView by rememberSaveable { mutableStateOf(false) }
    var lightOn by rememberSaveable { mutableStateOf(false) }
    var linearZoom by rememberSaveable { mutableFloatStateOf(0f) }
    var frozenFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    KeepScreenOn()
    RingLightWindowEffect(lightOn)

    // CameraX resets the zoom whenever the camera closes, so re-apply it once frames flow again.
    val camera = cameraState.camera
    val streaming = cameraState.streaming
    LaunchedEffect(camera, streaming, linearZoom) {
        if (streaming) camera?.cameraControl?.setLinearZoom(linearZoom)
    }

    val ring by animateDpAsState(if (lightOn) RingLightWidth else 0.dp, label = "ringLight")
    val ringInsets = WindowInsets(left = ring, top = ring, right = ring, bottom = ring)
    val subtitle = when {
        frozenFrame != null -> "Image figée"
        trueView -> "Vue réelle : comme les autres vous voient"
        else -> "Reflet inversé, comme un vrai miroir"
    }

    // When the ring light is on, the white border is the light source; the UI stays on Bg inside.
    Box(
        Modifier
            .fillMaxSize()
            .background(if (lightOn || ring > 0.dp) Color.White else Bg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(ring)
                .clip(RoundedCornerShape(ring))
                .background(Bg)
                .windowInsetsPadding(WindowInsets.safeDrawing.exclude(ringInsets))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            ScreenHeader(AppTitle, subtitle)
            Spacer(Modifier.height(16.dp))
            MirrorView(
                previewView = previewView,
                frozenFrame = frozenFrame,
                trueView = trueView,
                cameraState = cameraState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            ControlPanel(
                trueView = trueView,
                onTrueViewChange = { trueView = it },
                frozen = frozenFrame != null,
                canFreeze = streaming,
                onFreezeToggle = {
                    frozenFrame = if (frozenFrame != null) null else previewView.bitmap?.asImageBitmap()
                },
                lightOn = lightOn,
                onLightToggle = { lightOn = !lightOn },
                zoom = linearZoom,
                onZoomChange = { linearZoom = it.coerceIn(0f, 1f) },
                zoomRatio = cameraState.zoomRatio,
                zoomAvailable = cameraState.zoomAvailable
            )
        }
    }
}

@Composable
private fun MirrorView(
    previewView: PreviewView,
    frozenFrame: ImageBitmap?,
    trueView: Boolean,
    cameraState: FrontCameraState,
    modifier: Modifier = Modifier
) {
    // PreviewView already mirrors the front camera: no transform = real mirror, -1 = true view.
    val flip = Modifier.graphicsLayer { scaleX = if (trueView) -1f else 1f }
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Card),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .then(flip)
        )
        if (frozenFrame != null) {
            Image(
                bitmap = frozenFrame,
                contentDescription = "Image figée",
                modifier = Modifier
                    .fillMaxSize()
                    .then(flip),
                contentScale = ContentScale.Crop
            )
        }
        val message = cameraMessage(cameraState)
        if (message != null) {
            CameraNotice(message, onRetry = cameraState::retry)
        } else if (frozenFrame == null && !cameraState.streaming) {
            Text("Ouverture de la caméra…", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CameraNotice(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Bg.copy(alpha = 0.9f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        ToolButton("Réessayer", onRetry)
    }
}

private fun cameraMessage(state: FrontCameraState): String? {
    if (state.bindFailed) return "Impossible d'ouvrir la caméra frontale."
    return when (state.errorCode) {
        null -> null
        CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE ->
            "La caméra est utilisée par une autre application. Fermez-la pour retrouver le miroir."
        CameraState.ERROR_CAMERA_DISABLED ->
            "La caméra est désactivée sur cet appareil."
        else -> "La caméra ne répond pas."
    }
}

@Composable
private fun ControlPanel(
    trueView: Boolean,
    onTrueViewChange: (Boolean) -> Unit,
    frozen: Boolean,
    canFreeze: Boolean,
    onFreezeToggle: () -> Unit,
    lightOn: Boolean,
    onLightToggle: () -> Unit,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    zoomRatio: Float,
    zoomAvailable: Boolean
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolButton(
                text = "Miroir",
                onClick = { onTrueViewChange(false) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { selected = !trueView },
                primary = !trueView
            )
            ToolButton(
                text = "Vue réelle",
                onClick = { onTrueViewChange(true) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { selected = trueView },
                primary = trueView
            )
        }
        ZoomControl(
            zoom = zoom,
            onZoomChange = onZoomChange,
            zoomRatio = zoomRatio,
            available = zoomAvailable,
            enabled = zoomAvailable && !frozen
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolButton(
                text = if (frozen) "Reprendre" else "Figer",
                onClick = onFreezeToggle,
                modifier = Modifier.weight(1f),
                primary = frozen,
                enabled = frozen || canFreeze
            )
            ToolButton(
                text = "Éclairage",
                onClick = onLightToggle,
                modifier = Modifier
                    .weight(1f)
                    .semantics { stateDescription = if (lightOn) "Activé" else "Désactivé" },
                primary = lightOn
            )
        }
    }
}

@Composable
private fun ZoomControl(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    zoomRatio: Float,
    available: Boolean,
    enabled: Boolean
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zoom", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (available) String.format(Locale.FRANCE, "×%.1f", zoomRatio) else "Non disponible",
                color = if (available) Accent else TextSecondary,
                fontSize = 14.sp,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        Slider(
            value = zoom,
            onValueChange = onZoomChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Zoom" }
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Maximum brightness + dark system bar icons (they sit on the white ring) while the light is on. */
@Composable
private fun RingLightWindowEffect(enabled: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    DisposableEffect(window, enabled) {
        if (window == null || !enabled) return@DisposableEffect onDispose { }
        val systemBars = WindowCompat.getInsetsController(window, view)
        window.setScreenBrightness(1f)
        systemBars.isAppearanceLightStatusBars = true
        systemBars.isAppearanceLightNavigationBars = true
        onDispose {
            window.setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            systemBars.isAppearanceLightStatusBars = false
            systemBars.isAppearanceLightNavigationBars = false
        }
    }
}

private fun Window.setScreenBrightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}
