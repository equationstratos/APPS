package com.survival.mirror

import android.Manifest
import android.app.Activity
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
import androidx.lifecycle.compose.LifecycleResumeEffect

private const val CameraPermission = Manifest.permission.CAMERA

/**
 * Shows [content] only once the camera permission is granted. The state is refreshed from the
 * launcher result and on every resume, since the user may grant or revoke it from the settings.
 */
@Composable
internal fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var autoRequested by rememberSaveable { mutableStateOf(false) }

    fun update(isGranted: Boolean) {
        granted = isGranted
        if (isGranted) {
            denied = false
            permanentlyDenied = false
        } else if (denied) {
            // After a refusal, no rationale means "ne plus demander": only the settings can help.
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, CameraPermission)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) denied = true
        update(isGranted)
    }

    LifecycleResumeEffect(Unit) {
        update(context.hasCameraPermission())
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        if (!granted && !autoRequested) {
            autoRequested = true
            launcher.launch(CameraPermission)
        }
    }

    if (granted) {
        content()
    } else if (permanentlyDenied) {
        MessageScreen(
            "L'accès à la caméra a été refusé. Pour utiliser le miroir, autorisez la caméra " +
                "dans les paramètres de l'application, rubrique «\u00A0Autorisations\u00A0»."
        ) {
            ToolButton("Ouvrir les paramètres", { context.openAppSettings() }, Modifier.fillMaxWidth())
        }
    } else {
        MessageScreen(
            "Le miroir a besoin de la caméra frontale pour afficher votre reflet. " +
                "Aucune image n'est enregistrée ni envoyée."
        ) {
            ToolButton("Autoriser", { launcher.launch(CameraPermission) }, Modifier.fillMaxWidth())
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, CameraPermission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
    if (findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
