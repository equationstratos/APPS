package com.survival.magnifier

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val CAMERA = Manifest.permission.CAMERA

/**
 * Shows [content] once the camera permission is granted. The request is made automatically on
 * first launch, and the state is refreshed on every resume because the user may change it in
 * the system settings.
 */
@Composable
internal fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var autoRequested by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        // After a refusal, a false rationale means the system will no longer show the dialog.
        val activity = context.findActivity()
        permanentlyDenied = !isGranted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasCameraPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!granted && !autoRequested) {
            autoRequested = true
            launcher.launch(CAMERA)
        }
    }

    if (granted) {
        content()
    } else if (permanentlyDenied) {
        InfoScreen(
            title = "Accès à la caméra refusé",
            message = "La loupe a besoin de la caméra arrière pour agrandir l'image. " +
                "Ouvrez les paramètres de l'application, puis Autorisations > Caméra > Autoriser.",
            titleColor = Danger,
        ) {
            SurvieButton("Ouvrir les paramètres", onClick = { context.openAppSettings() }, modifier = Modifier.fillMaxWidth())
            SurvieButton("Autoriser", onClick = { launcher.launch(CAMERA) }, modifier = Modifier.fillMaxWidth(), primary = false)
        }
    } else {
        InfoScreen(
            title = "Autorisation de la caméra",
            message = "La loupe utilise la caméra arrière pour agrandir le texte et les petits objets. " +
                "Aucune image n'est enregistrée ni envoyée.",
        ) {
            SurvieButton("Autoriser", onClick = { launcher.launch(CAMERA) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, CAMERA) == PackageManager.PERMISSION_GRANTED

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
