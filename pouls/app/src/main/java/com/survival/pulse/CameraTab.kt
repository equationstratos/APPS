package com.survival.pulse

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val CAMERA_PERMISSION = Manifest.permission.CAMERA

/** Without a finger for this long, the measurement stops (torch and camera off). */
private const val NO_FINGER_TIMEOUT_MS = 120_000L

/** Without any frame for this long (camera busy or failing), the measurement stops with a message. */
private const val NO_FRAME_TIMEOUT_MS = 8_000L

private const val NO_FRAME_MESSAGE =
    "La caméra ne répond pas : elle est peut-être utilisée par une autre application. " +
        "Réessayez ou utilisez le mode manuel."

/** State of the camera tab, kept above the tabs so that a result survives a tab change. */
internal class CameraUiState {
    var running by mutableStateOf(false)
        private set
    var snapshot by mutableStateOf<SessionSnapshot?>(null)
        private set
    var resultBpm by mutableStateOf<Int?>(null)
        private set
    var resultQuality by mutableStateOf<Double?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var lens by mutableStateOf<Lens?>(null)
        private set
    var torch by mutableStateOf<Boolean?>(null)
        private set
    var context by mutableStateOf(MeasureContext.NONE)
        private set
    private var savedId: Long? = null
    private var waitingSinceMs: Long? = null
    private var lastFrameMs = 0L

    fun start() {
        running = true
        snapshot = null
        resultBpm = null
        resultQuality = null
        failed = false
        notice = null
        lens = null
        torch = null
        context = MeasureContext.NONE
        savedId = null
        waitingSinceMs = null
        lastFrameMs = SystemClock.elapsedRealtime()
    }

    fun stop() {
        running = false
        snapshot = null
    }

    /** The app left the foreground: the measurement cannot go on without the camera. */
    fun interrupt() {
        if (!running) return
        stop()
        notice = INTERRUPTED_MESSAGE
    }

    fun onStarted(lens: Lens, torch: Boolean) {
        if (!running) return
        this.lens = lens
        this.torch = torch
    }

    fun onError(message: String) {
        if (!running) return
        stop()
        notice = message
    }

    /** Stops a measurement that receives no frame at all (the camera never opened or froze). */
    fun checkFrames(now: Long) {
        if (!running || now - lastFrameMs <= NO_FRAME_TIMEOUT_MS) return
        stop()
        notice = NO_FRAME_MESSAGE
    }

    fun onSnapshot(value: SessionSnapshot, store: HistoryStore) {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        lastFrameMs = now
        if (value.phase == Phase.WAITING) {
            val since = waitingSinceMs ?: now.also { waitingSinceMs = it }
            if (now - since > NO_FINGER_TIMEOUT_MS) {
                stop()
                notice = "Mesure arrêtée : aucun doigt détecté pendant 2${NBSP}minutes."
                return
            }
        } else {
            waitingSinceMs = null
        }
        snapshot = value
        when (value.phase) {
            Phase.DONE -> {
                stop()
                val result = value.result
                if (result == null) {
                    failed = true
                } else {
                    val bpm = result.bpm.roundToInt()
                    resultBpm = bpm
                    resultQuality = result.quality
                    savedId = store.add(HistoryEntry(System.currentTimeMillis(), bpm, context, MeasureMethod.CAMERA))
                }
            }
            Phase.FAILED -> {
                stop()
                failed = true
            }
            else -> Unit
        }
    }

    fun updateContext(value: MeasureContext, store: HistoryStore) {
        context = value
        savedId?.let { store.setContext(it, value) }
    }
}

@Composable
internal fun CameraTab(state: CameraUiState, store: HistoryStore, onManual: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        denied = !isGranted
        // After a refusal, a false rationale means the system will no longer show the dialog.
        val activity = context.findActivity()
        permanentlyDenied = !isGranted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION)
        if (isGranted) state.start()
    }

    // The permission may be changed in the system settings while the app is in the background.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.hasCameraPermission()
                if (granted) {
                    denied = false
                    permanentlyDenied = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.running) {
        // One camera session per measurement; leaving the composition turns the torch off and frees the camera.
        DisposableEffect(Unit) {
            val camera = PulseCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                onStarted = state::onStarted,
                onSnapshot = { state.onSnapshot(it, store) },
                onError = state::onError,
            )
            // Torch and camera off as soon as the app leaves the foreground, without waiting for a recomposition.
            val pauseObserver = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) camera.stop()
            }
            lifecycleOwner.lifecycle.addObserver(pauseObserver)
            camera.start()
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(pauseObserver)
                camera.stop()
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000L)
                state.checkFrames(SystemClock.elapsedRealtime())
            }
        }
    }

    val view = LocalView.current
    DisposableEffect(view, state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    val readout = cameraReadout(
        running = state.running,
        snapshot = state.snapshot,
        resultBpm = state.resultBpm,
        resultQuality = state.resultQuality,
        failed = state.failed,
        context = state.context,
        notice = state.notice,
        light = lightNote(state.lens, state.torch),
    )

    SurvieCard {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ValueRing(
                value = readout.value,
                caption = "BPM",
                progress = readout.progress,
                tone = readout.tone,
                modifier = Modifier.clearAndSetSemantics { contentDescription = readout.description },
            )
            Text(
                text = readout.status,
                color = if (readout.tone == Tone.WARNING) Warning else Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            readout.detail?.let {
                Text(it, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, style = Tabular)
            }
            readout.note?.let {
                Text(it, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            }
            val snapshot = state.snapshot
            if (state.running && snapshot != null) {
                val finger = fingerIndicator(snapshot.finger)
                StatusLabel(finger.text, toneColor(finger.tone))
                if (snapshot.phase == Phase.MEASURING) {
                    val quality = qualityIndicator(snapshot.quality)
                    StatusLabel(quality.text, toneColor(quality.tone))
                }
            }
        }
        SurvieButton(
            text = if (state.running) "Arrêter" else "Démarrer",
            onClick = {
                when {
                    state.running -> state.stop()
                    granted -> state.start()
                    else -> launcher.launch(CAMERA_PERMISSION)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = !state.running,
        )
    }

    if (!granted && denied && !state.running) {
        SurvieCard {
            CardTitle("Accès à la caméra refusé")
            BodyText(
                "La mesure automatique a besoin de la caméra arrière et du flash. Aucune image n'est " +
                    "enregistrée ni envoyée. Sans caméra, comptez votre pouls avec le mode manuel."
            )
            if (permanentlyDenied) {
                BodyText("Ouvrez les paramètres de l'application, puis Autorisations > Caméra > Autoriser.")
                SurvieButton("Ouvrir les paramètres", onClick = { context.openAppSettings() }, modifier = Modifier.fillMaxWidth())
            } else {
                SurvieButton("Autoriser la caméra", onClick = { launcher.launch(CAMERA_PERMISSION) }, modifier = Modifier.fillMaxWidth())
            }
            SurvieButton("Mode manuel", onClick = onManual, modifier = Modifier.fillMaxWidth(), primary = false)
        }
    }

    if (state.running) {
        SurvieCard {
            CardTitle("Signal en direct")
            WavePlot(
                wave = state.snapshot?.wave,
                placeholder = "La courbe s'affiche quand le doigt couvre l'objectif et le flash.",
            )
        }
    }

    if (state.resultBpm != null) {
        ContextCard(selected = state.context, onSelect = { state.updateContext(it, store) })
    }

    ReferenceCard()

    if (!state.running) {
        SurvieCard {
            CardTitle("Pour une bonne mesure")
            BodyText("• Asseyez-vous et restez au calme quelques minutes avant de mesurer.")
            BodyText("• Couvrez entièrement l'objectif et le flash avec le bout du doigt, sans appuyer : un appui fort chasse le sang et efface le pouls.")
            BodyText("• Mains froides : réchauffez-les, le signal sera bien meilleur.")
            BodyText("• Le flash peut chauffer un peu : retirez le doigt si c'est désagréable.")
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    if (findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
