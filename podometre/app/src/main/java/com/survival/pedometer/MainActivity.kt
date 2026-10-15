package com.survival.pedometer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Success = Color(0xFF00D084)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

@Composable
fun SurvieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Bg,
            primaryContainer = Navy, onPrimaryContainer = Color.White,
            secondary = Accent, onSecondary = Bg,
            secondaryContainer = Navy, onSecondaryContainer = Color.White,
            background = Bg, onBackground = Color.White,
            surface = Card, onSurface = Color.White,
            surfaceVariant = Navy, onSurfaceVariant = TextSecondary,
            surfaceContainer = Card, surfaceContainerHigh = Card, surfaceContainerHighest = Navy,
            outline = Navy, outlineVariant = Navy,
            error = Danger, onError = Bg
        ),
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { SurvieTheme { AppScreen() } }
    }
}

private val Tnum = TextStyle(fontFeatureSettings = "tnum")
private val PanelShape = RoundedCornerShape(16.dp)
private val ControlShape = RoundedCornerShape(12.dp)
private val RingSize = 210.dp
private val IntegerFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.FRANCE)

private const val KCAL_PER_STEP = 0.04
private const val DAY_CHECK_INTERVAL_MS = 60_000L

private fun formatInt(value: Int): String = IntegerFormat.format(value.toLong())

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val store = remember { StepStore(context.applicationContext) }
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val mode = remember { TrackingMode.detect(sensorManager) }

    var steps by remember { mutableIntStateOf(store.todaySteps()) }
    var goal by remember { mutableIntStateOf(store.goal) }
    var strideCm by remember { mutableIntStateOf(store.strideCm) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val permission = rememberActivityRecognitionPermission(required = mode.usesStepSensor)
    val tracker = remember { StepTracker(sensorManager, mode, store) { steps = it } }
    val tracking = mode != TrackingMode.Unavailable && permission.granted
    TrackStepsWhileResumed(tracker, enabled = tracking)
    // Fallback sources are unregistered on pause, so a screen timeout would stop the count.
    KeepScreenOn(active = tracking && mode != TrackingMode.StepCounter)

    // Rolls the display over to a new day at midnight even when no step arrives.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(DAY_CHECK_INTERVAL_MS)
                steps = store.todaySteps()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Podomètre", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(mode.subtitle, color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        if (mode == TrackingMode.Unavailable) {
            SensorMissingPanel()
        } else {
            if (!permission.granted) {
                PermissionPanel(
                    permanentlyDenied = permission.permanentlyDenied,
                    onRequest = permission.request,
                    onOpenSettings = { openAppSettings(context) }
                )
                Spacer(Modifier.height(16.dp))
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(steps = steps, goal = goal)
            }
            Spacer(Modifier.height(16.dp))

            ModeNote(mode)
            Spacer(Modifier.height(16.dp))

            StatsRow(steps = steps, strideCm = strideCm)
            Spacer(Modifier.height(12.dp))

            GoalPanel(goal = goal, onGoalChange = { value ->
                goal = value
                store.goal = value
            })
            Spacer(Modifier.height(12.dp))

            StridePanel(strideCm = strideCm, onStrideChange = { value ->
                strideCm = value.coerceIn(StepStore.MIN_STRIDE_CM, StepStore.MAX_STRIDE_CM)
                store.strideCm = strideCm
            })
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { confirmReset = true },
                enabled = steps > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = ControlShape,
                colors = secondaryButtonColors()
            ) {
                Text("Réinitialiser aujourd'hui", fontSize = 16.sp)
            }
        }
    }

    if (confirmReset) {
        ResetDialog(
            onConfirm = {
                steps = store.resetToday()
                confirmReset = false
            },
            onDismiss = { confirmReset = false }
        )
    }
}

private val TrackingMode.subtitle: String
    get() = when (this) {
        TrackingMode.StepCounter -> "Capteur de pas du téléphone"
        TrackingMode.StepDetector -> "Détecteur de pas du téléphone"
        TrackingMode.Accelerometer -> "Accéléromètre (mode de secours)"
        TrackingMode.Unavailable -> "Aucun capteur de pas"
    }

@Composable
private fun TrackStepsWhileResumed(tracker: StepTracker, enabled: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, tracker, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (enabled) tracker.start() else tracker.refresh()
                Lifecycle.Event.ON_PAUSE -> tracker.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }
}

@Composable
private fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

private class PermissionState(
    val granted: Boolean,
    val permanentlyDenied: Boolean,
    val request: () -> Unit,
)

@Composable
private fun rememberActivityRecognitionPermission(required: Boolean): PermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val needed = required && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    var granted by remember { mutableStateOf(!needed || isActivityRecognitionGranted(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var canAskAgain by remember { mutableStateOf(true) }
    var autoRequested by rememberSaveable { mutableStateOf(false) }

    fun refreshRationale() {
        canAskAgain = activity?.let { shouldShowActivityRecognitionRationale(it) } ?: true
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        refreshRationale()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, needed) {
        val observer = LifecycleEventObserver { _, event ->
            if (needed && event == Lifecycle.Event.ON_RESUME) {
                granted = isActivityRecognitionGranted(context)
                refreshRationale()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(needed) {
        if (needed && !granted && !autoRequested) {
            autoRequested = true
            launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    return PermissionState(
        granted = granted,
        permanentlyDenied = denied && !granted && !canAskAgain,
        request = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
    )
}

private fun isActivityRecognitionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

private fun shouldShowActivityRecognitionRationale(activity: Activity): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Some stripped-down ROMs have no app details screen; there is nothing else to offer then.
    runCatching { context.startActivity(intent) }
}

@Composable
private fun primaryButtonColors() =
    ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)

@Composable
private fun secondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Navy,
    contentColor = Color.White,
    disabledContainerColor = Navy.copy(alpha = 0.4f),
    disabledContentColor = TextSecondary
)

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(PanelShape)
            .background(Card)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun PanelTitle(text: String, color: Color = Color.White) {
    Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SensorMissingPanel() {
    Panel(Modifier.fillMaxWidth()) {
        PanelTitle("Capteur de pas non disponible sur cet appareil.", color = Danger)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ce téléphone n'a ni podomètre ni accéléromètre : le comptage des pas est impossible.",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun PermissionPanel(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Panel(Modifier.fillMaxWidth()) {
        PanelTitle("Autorisation requise", color = Warning)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied) {
                "L'autorisation « Activité physique » a été refusée. " +
                    "Activez-la dans les paramètres de l'application pour compter vos pas."
            } else {
                "Le podomètre a besoin de l'autorisation « Activité physique » " +
                    "pour lire le capteur de pas du téléphone."
            },
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onRequest,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = ControlShape,
            colors = primaryButtonColors()
        ) {
            Text(if (permanentlyDenied) "Ouvrir les paramètres" else "Autoriser", fontSize = 16.sp)
        }
    }
}

@Composable
private fun ProgressRing(steps: Int, goal: Int) {
    val safeGoal = goal.coerceAtLeast(1)
    val reached = steps >= safeGoal
    val progress by animateFloatAsState(
        targetValue = (steps.toFloat() / safeGoal).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "progression"
    )
    val ringColor = if (reached) Success else Accent
    val stepsText = formatInt(steps)

    Box(Modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = Navy,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            if (progress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stepsText,
                color = Color.White,
                fontSize = if (stepsText.length > 6) 34.sp else 46.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = Tnum
            )
            Text("pas", color = TextSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (reached) {
                    "Objectif atteint !"
                } else {
                    "${formatInt((steps.toLong() * 100 / safeGoal).toInt())} % de ${formatInt(safeGoal)}"
                },
                color = if (reached) Success else TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                style = Tnum
            )
        }
    }
}

@Composable
private fun ModeNote(mode: TrackingMode) {
    val (text, color) = when (mode) {
        TrackingMode.StepCounter ->
            "Le téléphone compte vos pas en continu, même application fermée, " +
                "à partir de la première ouverture de la journée." to TextSecondary
        TrackingMode.StepDetector ->
            "Détecteur de pas : le comptage n'a lieu que lorsque l'application est ouverte ; " +
                "l'écran reste allumé pendant le comptage." to Warning
        TrackingMode.Accelerometer ->
            "Aucun capteur de pas : comptage approximatif par accéléromètre, uniquement " +
                "lorsque l'application est ouverte. L'écran reste allumé ; " +
                "gardez le téléphone sur vous." to Warning
        TrackingMode.Unavailable -> return
    }
    Text(text, color = color, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StatsRow(steps: Int, strideCm: Int) {
    val km = steps.toDouble() * strideCm / 100_000.0
    val kcal = (steps * KCAL_PER_STEP).roundToInt()
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatPanel(
            label = "Distance",
            value = String.format(Locale.FRANCE, "%.2f km", km),
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        StatPanel(
            label = "Calories",
            value = "≈ ${formatInt(kcal)} kcal",
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun StatPanel(label: String, value: String, modifier: Modifier) {
    Panel(modifier) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = Tnum)
    }
}

@Composable
private fun GoalPanel(goal: Int, onGoalChange: (Int) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        PanelTitle("Objectif quotidien")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StepStore.GOALS.forEach { option ->
                GoalChip(
                    label = formatInt(option),
                    selected = option == goal,
                    onClick = { onGoalChange(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GoalChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .background(if (selected) Accent else Navy)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Bg else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            style = Tnum
        )
    }
}

@Composable
private fun StridePanel(strideCm: Int, onStrideChange: (Int) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        PanelTitle("Longueur de pas")
        Text("Sert au calcul de la distance", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StepperButton(
                symbol = "−",
                description = "Diminuer la longueur de pas",
                enabled = strideCm > StepStore.MIN_STRIDE_CM,
                onClick = { onStrideChange(strideCm - 1) }
            )
            Text(
                "$strideCm cm",
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = Tnum
            )
            StepperButton(
                symbol = "+",
                description = "Augmenter la longueur de pas",
                enabled = strideCm < StepStore.MAX_STRIDE_CM,
                onClick = { onStrideChange(strideCm + 1) }
            )
        }
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
        shape = ControlShape,
        colors = secondaryButtonColors(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réinitialiser aujourd'hui ?") },
        text = { Text("Le compteur de pas du jour sera remis à zéro. Cette action est irréversible.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Réinitialiser") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
        containerColor = Card
    )
}
