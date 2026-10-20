package com.survival.altimeter

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TICK_MS = 5_000L
private const val HOUR_MS = 3_600_000L

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    val sensorManager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val barometer = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    var unit by remember { mutableStateOf(prefs.unit) }
    val onUnitChange: (AltitudeUnit) -> Unit = { selected ->
        unit = selected
        prefs.unit = selected
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()
        if (sensorManager != null && barometer != null) {
            BarometerScreen(prefs, sensorManager, barometer, unit, onUnitChange)
        } else {
            NoBarometerScreen(unit, onUnitChange)
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text("Altimètre", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Altitude et baromètre hors ligne", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun BarometerScreen(
    prefs: AppPrefs,
    sensorManager: SensorManager,
    sensor: Sensor,
    unit: AltitudeUnit,
    onUnitChange: (AltitudeUnit) -> Unit,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val state = remember { AltimeterState(prefs, System.currentTimeMillis()) }
    var sensorFailed by remember { mutableStateOf(false) }
    var backgroundLogging by remember { mutableStateOf(prefs.backgroundLogging) }
    var chartHours by remember { mutableIntStateOf(prefs.chartHours) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(backgroundLogging) {
        if (backgroundLogging) PressureLogWorker.schedule(appContext) else PressureLogWorker.cancel(appContext)
    }

    // The barometer runs only while the screen is visible; the worker takes over in the background.
    LaunchedEffect(state, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            state.nowMs = System.currentTimeMillis()
            state.history = withContext(Dispatchers.IO) { HistoryStore.load(appContext) }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val sensorEvent = event ?: return
                    val hPa = sensorEvent.values.firstOrNull() ?: return
                    state.onSample(sensorEvent.timestamp, hPa.toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorFailed = !sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            try {
                while (true) {
                    delay(TICK_MS)
                    val now = System.currentTimeMillis()
                    state.nowMs = now
                    val sample = state.sampleToRecord(now)
                    if (sample != null) {
                        state.history = withContext(Dispatchers.IO) { HistoryStore.add(appContext, sample) }
                    }
                    state.persistSession()
                }
            } finally {
                sensorManager.unregisterListener(listener)
                state.onPause()
            }
        }
    }

    val now = state.nowMs
    val history = state.history
    val tendency = remember(history, now) { tendencyStatus(history, now) }

    if (sensorFailed) {
        NoticeCard(
            title = "Baromètre indisponible",
            titleColor = Warning,
            message = "Le capteur de pression ne répond pas. Fermez puis rouvrez l'application$NBSP; en attendant, " +
                "l'onglet GPS de l'étalonnage affiche l'altitude GPS.",
        )
    }
    if (state.calibration.isStale(now)) StaleCalibrationNotice(state.calibration, now)
    AltitudeCard(state, unit, onUnitChange)
    CalibrationCard(state, unit, now)
    SessionCard(state, unit, now)
    BarometerCard(state, tendency, unit, now)
    HistoryCard(
        history = history,
        now = now,
        hours = chartHours,
        onHoursChange = { hours ->
            chartHours = hours
            prefs.chartHours = hours
        },
        backgroundLogging = backgroundLogging,
        onBackgroundLoggingChange = { enabled ->
            backgroundLogging = enabled
            prefs.backgroundLogging = enabled
        },
        onClear = {
            scope.launch {
                withContext(Dispatchers.IO) { HistoryStore.clear(appContext) }
                state.history = emptyList()
            }
        },
    )
    TipsCard()
}

@Composable
private fun StaleCalibrationNotice(calibration: Calibration, now: Long) {
    val time = calibration.timeMs
    if (calibration.isStandard || time == null) {
        NoticeCard(
            title = "Altitude non étalonnée",
            titleColor = Warning,
            message = "Calcul avec la pression standard (${formatHpa(Isa.SEA_LEVEL_HPA, 2)})$NBSP: selon la météo, " +
                "l'erreur peut dépasser 100${NBSP}m. Étalonnez ci-dessous avec une altitude connue, le GPS ou le QNH.",
        )
    } else {
        val zone = remember { ZoneId.systemDefault() }
        NoticeCard(
            title = "Étalonnage à refaire",
            titleColor = Warning,
            message = "Dernier étalonnage ${formatDateTime(time, now, zone)}. Depuis, la météo a pu faire varier la " +
                "pression de plusieurs hectopascals, soit des dizaines de mètres d'erreur (1${NBSP}hPa ≈ 8${NBSP}m). " +
                "Réétalonnez dès que possible.",
        )
    }
}

@Composable
private fun AltitudeCard(state: AltimeterState, unit: AltitudeUnit, onUnitChange: (AltitudeUnit) -> Unit) {
    val altitude = state.altitudeM
    SectionCard {
        Text("Altitude barométrique", color = TextSecondary, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (altitude.isFinite()) formatAltitudeValue(altitude, unit) else NO_VALUE,
                color = Color.White,
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                unit.symbol,
                color = Accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Caption(
            if (!state.settled) {
                "Mesure de la pression en cours…"
            } else {
                "Pression mesurée ${formatHpa(state.stationHpa)}, référence " +
                    "${formatHpa(state.calibration.referenceHpa, 2)} au niveau de la mer."
            }
        )
        Spacer(Modifier.height(12.dp))
        Segmented(AltitudeUnit.entries.map { it.label }, unit.ordinal) { onUnitChange(AltitudeUnit.entries[it]) }
    }
}

@Composable
private fun CalibrationCard(state: AltimeterState, unit: AltitudeUnit, now: Long) {
    val focusManager = LocalFocusManager.current
    val zone = remember { ZoneId.systemDefault() }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var altitudeText by rememberSaveable { mutableStateOf("") }
    var qnhText by rememberSaveable { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val stationHpa = if (state.settled) state.stationHpa else Double.NaN

    fun applyResult(result: CalibrationResult) {
        when (result) {
            is CalibrationResult.Success -> {
                val calibration = result.calibration
                state.applyCalibration(calibration)
                val site = calibration.siteAltitudeM
                success = if (site != null) {
                    "Étalonné$NBSP: ${formatAltitude(site, unit)} (référence ${formatHpa(calibration.referenceHpa)})."
                } else {
                    "Pression standard rétablie."
                }
                failure = null
                fieldError = null
                altitudeText = ""
                qnhText = ""
                focusManager.clearFocus()
            }
            is CalibrationResult.Failure -> {
                failure = result.message
                success = null
            }
        }
    }
    val submitAltitude: () -> Unit = {
        val (meters, error) = parseAltitudeInput(altitudeText, unit)
        fieldError = error
        if (meters != null) applyResult(calibrateWithAltitude(stationHpa, meters, System.currentTimeMillis()))
    }
    val submitQnh: () -> Unit = {
        val (qnh, error) = parseQnhInput(qnhText)
        fieldError = error
        if (qnh != null) applyResult(calibrateWithQnh(stationHpa, qnh, System.currentTimeMillis()))
    }

    SectionCard(title = "Étalonnage") {
        Segmented(listOf("Altitude", "GPS", "QNH"), mode) { selected ->
            mode = selected
            fieldError = null
            failure = null
            success = null
        }
        Spacer(Modifier.height(12.dp))
        when (mode) {
            0 -> {
                DecimalField(
                    value = altitudeText,
                    onValueChange = {
                        altitudeText = sanitizeDecimalInput(it, allowNegative = true)
                        fieldError = null
                    },
                    label = "Altitude du lieu (${unit.symbol})",
                    placeholder = "ex. 1250",
                    error = fieldError,
                    onDone = submitAltitude,
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Étalonner sur cette altitude", submitAltitude, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Caption("Altitude lue sur une carte IGN, un panneau, une borne, un sommet ou un refuge.")
            }
            1 -> GpsAltitudePanel(
                unit = unit,
                autoRequest = false,
                onCalibrate = { estimate ->
                    applyResult(
                        calibrateWithAltitude(
                            stationHpa,
                            estimate.altitudeM,
                            System.currentTimeMillis(),
                            CalibrationMethod.GPS,
                        )
                    )
                },
            )
            else -> {
                DecimalField(
                    value = qnhText,
                    onValueChange = {
                        qnhText = sanitizeDecimalInput(it, allowNegative = false)
                        fieldError = null
                    },
                    label = "QNH (hPa)",
                    placeholder = "ex. 1018",
                    error = fieldError,
                    onDone = submitQnh,
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Appliquer le QNH", submitQnh, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Caption(
                    "QNH$NBSP: pression ramenée au niveau de la mer, donnée par un bulletin météo ou un " +
                        "aérodrome proche (METAR, ATIS)."
                )
            }
        }
        success?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, color = Success, fontSize = 15.sp)
        }
        failure?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, color = Warning, fontSize = 15.sp)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Navy)
        Spacer(Modifier.height(12.dp))
        val calibration = state.calibration
        val time = calibration.timeMs
        Text(
            if (calibration.isStandard || time == null) {
                "Référence actuelle$NBSP: pression standard ${formatHpa(Isa.SEA_LEVEL_HPA, 2)}, jamais étalonné."
            } else {
                "Référence actuelle$NBSP: ${formatHpa(calibration.referenceHpa)} au niveau de la mer " +
                    "(${calibration.method.label}, ${formatDateTime(time, now, zone)})."
            },
            color = Color.White,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(6.dp))
        val perHpa = if (stationHpa.isFinite()) metersPerHpa(stationHpa, calibration.referenceHpa) else 8.3
        Caption(
            "Ici, 1${NBSP}hPa ≈ ${formatAltitude(perHpa, unit)} d'altitude. La pression varie avec la météo, " +
                "souvent de plusieurs hectopascals en quelques heures$NBSP: l'altitude affichée dérive d'autant. " +
                "Réétalonnez à chaque point connu."
        )
        if (!calibration.isStandard) {
            TextButton(
                onClick = { applyResult(CalibrationResult.Success(Calibration.STANDARD)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Revenir à la pression standard", color = Accent)
            }
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String?,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        textStyle = Tabular.copy(fontSize = 18.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Card,
            unfocusedContainerColor = Card,
            cursorColor = Accent,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Navy,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextSecondary,
            focusedPlaceholderColor = TextSecondary,
            unfocusedPlaceholderColor = TextSecondary,
        ),
    )
}

@Composable
private fun SessionCard(state: AltimeterState, unit: AltitudeUnit, now: Long) {
    var confirm by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val session = state.session
    SectionCard(title = "Dénivelé") {
        Row(Modifier.fillMaxWidth()) {
            ValueCell("D+ (montée)", formatAltitude(session.gainM, unit), Modifier.weight(1f), Accent)
            ValueCell("D$MINUS (descente)", formatAltitude(session.lossM, unit), Modifier.weight(1f), Accent)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            ValueCell(
                "Altitude min.",
                if (session.hasData) formatAltitude(session.minM, unit) else NO_VALUE,
                Modifier.weight(1f),
            )
            ValueCell(
                "Altitude max.",
                if (session.hasData) formatAltitude(session.maxM, unit) else NO_VALUE,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Caption(
            "Depuis ${formatDateTime(session.startedAtMs, now, zone)} (${formatDuration(now - session.startedAtMs)}). " +
                "Les variations de moins de ${formatAltitude(ElevationSession.HYSTERESIS_M, unit)} sont ignorées " +
                "pour ne pas compter le bruit du capteur."
        )
        Spacer(Modifier.height(4.dp))
        Caption(
            "Mesure active quand l'application est affichée$NBSP; à votre retour, l'écart d'altitude depuis " +
                "la dernière mesure est compté."
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Réinitialiser", { confirm = true }, Modifier.fillMaxWidth())
    }
    if (confirm) {
        ConfirmDialog(
            title = "Réinitialiser le dénivelé$NBSP?",
            text = "Le D+, le D$MINUS et les altitudes min. et max. repartent de zéro.",
            confirmLabel = "Réinitialiser",
            onConfirm = {
                state.resetSession(System.currentTimeMillis())
                confirm = false
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun BarometerCard(state: AltimeterState, tendency: TendencyStatus, unit: AltitudeUnit, now: Long) {
    val station = if (state.settled) state.stationHpa else Double.NaN
    val site = state.calibration.siteAltitudeM
    val seaLevel = if (site != null && station.isFinite()) seaLevelPressure(station, site) else Double.NaN
    val history = state.history
    val change1h = remember(history, now) { pressureChange(history, now, HOUR_MS) }
    val change24h = remember(history, now) { pressureChange(history, now, 24 * HOUR_MS) }
    SectionCard(title = "Baromètre") {
        Row(Modifier.fillMaxWidth()) {
            ValueCell("Pression mesurée", formatHpa(station), Modifier.weight(1f))
            ValueCell("Au niveau de la mer", formatHpa(seaLevel), Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Caption(
            if (site != null) {
                "Ramenée au niveau de la mer pour ${formatAltitude(site, unit)}, l'altitude du dernier étalonnage$NBSP: " +
                    "comparable aux bulletins météo tant que vous êtes à cette altitude."
            } else {
                "Étalonnez l'altitude pour obtenir la pression au niveau de la mer, comparable aux bulletins météo."
            }
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Navy)
        Spacer(Modifier.height(12.dp))
        Text("Tendance sur 3${NBSP}h", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        when (tendency) {
            is TendencyStatus.Ready -> TendencyView(tendency)
            is TendencyStatus.Warming -> BodyText(
                "Tendance disponible dans environ ${formatDuration(tendency.remainingMs)}, après 3${NBSP}h de " +
                    "relevés au même endroit."
            )
            TendencyStatus.Gap -> BodyText(
                "Relevés manquants sur les 3 dernières heures$NBSP: laissez le suivi en arrière-plan activé " +
                    "ou l'application ouverte."
            )
            TendencyStatus.NoRecentReading -> BodyText(
                "Aucun relevé récent$NBSP: la tendance sera mise à jour d'ici une minute."
            )
        }
        if (change1h != null || change24h != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Sur 1${NBSP}h$NBSP: ${change1h?.let { formatHpaChange(it) } ?: NO_VALUE}   ·   " +
                    "sur 24${NBSP}h$NBSP: ${change24h?.let { formatHpaChange(it) } ?: NO_VALUE}",
                color = TextSecondary,
                fontSize = 14.sp,
                style = Tabular,
            )
        }
        Spacer(Modifier.height(8.dp))
        val perHpa = if (station.isFinite()) metersPerHpa(station, state.calibration.referenceHpa) else 8.3
        Caption(
            "Valable seulement si vous restez à la même altitude$NBSP: ${formatAltitude(10.0, unit)} de dénivelé " +
                "changent la pression d'environ ${formatNumber(10.0 / perHpa, 1)}${NBSP}hPa, autant qu'une vraie " +
                "variation météo."
        )
    }
}

@Composable
private fun TendencyView(status: TendencyStatus.Ready) {
    val trend = status.trend
    val color = when {
        trend.isAlert && trend.strength >= 4 -> Danger
        trend.isAlert -> Warning
        else -> Accent
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TrendArrow(trend, color, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(trend.label, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "${formatHpaChange(status.changeHpa)} en 3${NBSP}h",
                color = Color.White,
                fontSize = 15.sp,
                style = Tabular,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    BodyText(trend.advice)
}

@Composable
private fun HistoryCard(
    history: List<PressureSample>,
    now: Long,
    hours: Int,
    onHoursChange: (Int) -> Unit,
    backgroundLogging: Boolean,
    onBackgroundLoggingChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val count = remember(history, hours, now) { history.count { it.timeMs >= now - hours * HOUR_MS } }
    SectionCard(title = "Historique de la pression") {
        Segmented(listOf("24${NBSP}h", "72${NBSP}h"), if (hours == 72) 1 else 0) { onHoursChange(if (it == 1) 72 else 24) }
        Spacer(Modifier.height(12.dp))
        if (count >= 2) {
            PressureChart(
                samples = history,
                nowMs = now,
                hours = hours,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .background(Navy, ButtonShape)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Pas encore assez de relevés. La courbe se remplit d'un point par minute quand l'application " +
                        "est ouverte, et d'un point toutes les 15 minutes environ en arrière-plan.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Caption("Pression mesurée par le téléphone, en hPa. En abscisse$NBSP: heures avant maintenant.")
        history.lastOrNull()?.let { last ->
            val noun = if (count <= 1) "relevé" else "relevés"
            Caption("$count $noun sur $hours${NBSP}h · dernier relevé ${formatAgo(now - last.timeMs)}")
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            label = "Suivi en arrière-plan",
            description = "Un relevé toutes les 15 minutes environ, même application fermée. Android peut espacer " +
                "les relevés pour économiser la batterie.",
            checked = backgroundLogging,
            onCheckedChange = onBackgroundLoggingChange,
        )
        if (history.isNotEmpty()) {
            TextButton(onClick = { confirm = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Effacer l'historique", color = Accent)
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Effacer l'historique$NBSP?",
            text = "Les relevés de pression des 72 dernières heures seront supprimés. Utile après un changement " +
                "d'altitude (nouveau bivouac, refuge…), qui fausse la tendance.",
            confirmLabel = "Effacer",
            onConfirm = {
                onClear()
                confirm = false
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun TipsCard() {
    SectionCard(title = "Bon à savoir") {
        Tip(
            "L'altimètre mesure la pression de l'air$NBSP: précis à quelques mètres près sur un dénivelé, il dérive " +
                "avec la météo. Réétalonnez-le à chaque point d'altitude connue."
        )
        Tip(
            "Vent, courant d'air ou ventilation (voiture, tente) faussent la mesure$NBSP; en avion, la cabine " +
                "pressurisée la rend inutilisable."
        )
        Tip(
            "Au niveau de la mer, une pression basse (moins de ${formatHpa(1000.0, 0)}) qui baisse annonce souvent " +
                "du mauvais temps$NBSP; une pression haute (plus de ${formatHpa(1020.0, 0)}) et stable, du beau temps."
        )
        Tip("En montagne, une baisse rapide de la pression invite à redescendre ou à s'abriter avant l'orage.")
    }
}

@Composable
private fun NoBarometerScreen(unit: AltitudeUnit, onUnitChange: (AltitudeUnit) -> Unit) {
    NoticeCard(
        title = "Pas de baromètre",
        titleColor = Warning,
        message = "Cet appareil n'a pas de capteur de pression. L'altitude est donnée par le GPS, moins précise " +
            "(souvent ±10 à 20${NBSP}m)$NBSP; le dénivelé et la tendance météo ne sont pas disponibles.",
    )
    SectionCard(title = "Altitude GPS") {
        GpsAltitudePanel(unit = unit, autoRequest = true, onCalibrate = null)
        Spacer(Modifier.height(12.dp))
        Segmented(AltitudeUnit.entries.map { it.label }, unit.ordinal) { onUnitChange(AltitudeUnit.entries[it]) }
    }
    SectionCard(title = "Bon à savoir") {
        Tip(
            "L'altitude est la composante la moins précise d'une position GPS$NBSP: placez-vous à découvert et " +
                "attendez que la précision se stabilise."
        )
        Tip(
            "Elle est corrigée de l'écart entre l'ellipsoïde du GPS et le niveau moyen de la mer (géoïde EGM96) " +
                "pour correspondre aux altitudes des cartes."
        )
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, color = Color.White, fontSize = 15.sp)
}

@Composable
private fun Tip(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("•", color = Accent, fontSize = 15.sp, modifier = Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
