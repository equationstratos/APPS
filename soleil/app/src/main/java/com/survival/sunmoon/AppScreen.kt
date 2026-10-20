package com.survival.sunmoon

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private const val PREFS_NAME = "survie"
private const val KEY_PLACE = "soleil_position"
private const val KEY_GOLDEN = "soleil_heures_dorees"
private const val MAX_FIELD_LENGTH = 24

/** Dates offered: the range of the date picker, where the ΔT table of the algorithms holds. */
private val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
private val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)

/** Current instant and phone time zone, refreshed every second while the app is visible. */
@Stable
private class Clock {
    var now: Instant by mutableStateOf(Instant.now())
    var zone: ZoneId by mutableStateOf(ZoneId.systemDefault())
}

private enum class LocateStatus { IDLE, SEARCHING, DENIED, DENIED_FOREVER, UNAVAILABLE, DISABLED, TIMEOUT }

/** Ephemerides with the inputs they were computed for (null when the computation failed). */
private class Computed(
    val latitude: Double,
    val longitude: Double,
    val date: LocalDate,
    val zone: ZoneId,
    val ephemeris: Ephemeris?,
)

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val clock = rememberClock()
    val zone = clock.zone
    val today by remember(clock) { derivedStateOf { clock.now.atZone(clock.zone).toLocalDate() } }

    var place by remember { mutableStateOf(prefs.readPlace()) }
    var showGolden by remember { mutableStateOf(prefs.getBoolean(KEY_GOLDEN, false)) }
    // Null follows today, also across midnight; otherwise the epoch day chosen by the user.
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val date = pickedDay?.let { LocalDate.ofEpochDay(it) } ?: today

    fun selectDate(value: LocalDate) {
        val clamped = when {
            value.isBefore(MIN_DATE) -> MIN_DATE
            value.isAfter(MAX_DATE) -> MAX_DATE
            else -> value
        }
        pickedDay = if (clamped == today) null else clamped.toEpochDay()
    }

    fun savePlace(value: SavedPlace) {
        place = value
        prefs.edit { putString(KEY_PLACE, encodePlace(value)) }
    }

    var status by remember { mutableStateOf(LocateStatus.IDLE) }
    var locateJob by remember { mutableStateOf<Job?>(null) }

    fun startLocating() {
        locateJob?.cancel()
        status = LocateStatus.SEARCHING
        locateJob = scope.launch {
            when (val result = locateOnce(context)) {
                is LocateResult.Found -> {
                    savePlace(result.place)
                    status = LocateStatus.IDLE
                }
                is LocateResult.Failed -> status = when (result.reason) {
                    LocateFailure.NO_PERMISSION -> LocateStatus.DENIED
                    LocateFailure.UNAVAILABLE -> LocateStatus.UNAVAILABLE
                    LocateFailure.DISABLED -> LocateStatus.DISABLED
                    LocateFailure.TIMEOUT -> LocateStatus.TIMEOUT
                }
            }
        }
    }

    fun cancelLocating() {
        locateJob?.cancel()
        locateJob = null
        if (status == LocateStatus.SEARCHING) status = LocateStatus.IDLE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it } || context.hasLocationPermission()) {
            startLocating()
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            status = if (canAskAgain) LocateStatus.DENIED else LocateStatus.DENIED_FOREVER
        }
    }

    fun requestLocation() {
        if (context.hasLocationPermission()) startLocating() else permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    // Back from the system settings: once the permission or the location switch the user went
    // to change is on, the search they asked for starts again by itself.
    LifecycleResumeEffect(Unit) {
        val resume = when (status) {
            LocateStatus.DENIED, LocateStatus.DENIED_FOREVER -> context.hasLocationPermission()
            LocateStatus.DISABLED -> context.hasLocationPermission() && context.isLocationOn()
            else -> false
        }
        if (resume) startLocating()
        onPauseOrDispose { }
    }
    // The location request never outlives the visible screen.
    LifecycleStartEffect(Unit) {
        onStopOrDispose { cancelLocating() }
    }

    var computed by remember { mutableStateOf<Computed?>(null) }
    val latitude = place?.latitude
    val longitude = place?.longitude
    LaunchedEffect(latitude, longitude, date, zone) {
        if (latitude == null || longitude == null) {
            computed = null
            return@LaunchedEffect
        }
        val ephemeris = try {
            withContext(Dispatchers.Default) {
                computeEphemeris(latitude, longitude, date, zone, followLive = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            null
        }
        computed = Computed(latitude, longitude, date, zone, ephemeris)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()
        PositionCard(
            place = place,
            today = today,
            zone = zone,
            status = status,
            onLocate = { requestLocation() },
            onCancel = { cancelLocating() },
            onOpenAppSettings = { context.openAppSettings() },
            onOpenLocationSettings = { context.openLocationSettings() },
            onManual = { lat, lon ->
                cancelLocating()
                status = LocateStatus.IDLE
                savePlace(SavedPlace(lat, lon, PlaceSource.MANUAL, System.currentTimeMillis(), null))
            },
        )
        val current = place
        if (current == null) {
            WelcomeCard()
        } else {
            DateCard(date = date, today = today, onSelect = { selectDate(it) })
            val data = computed
            val ephemeris = data?.ephemeris
            when {
                data == null -> LoadingCard()
                ephemeris == null -> SurvieCard {
                    NoticeCard(
                        title = "Calcul impossible",
                        message = "Les éphémérides n'ont pas pu être calculées pour cette date. Essayez une autre date.",
                    )
                }
                else -> {
                    // While new inputs are being computed, the previous results stay visible but dimmed.
                    val upToDate = data.date == date && data.zone == zone &&
                        data.latitude == current.latitude && data.longitude == current.longitude
                    val isToday = data.date == today
                    Column(
                        Modifier.alpha(if (upToDate) 1f else 0.5f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SunCard(
                            ephemeris = ephemeris,
                            isToday = isToday,
                            clock = clock,
                            showGolden = showGolden,
                            onShowGoldenChange = { value ->
                                showGolden = value
                                prefs.edit { putBoolean(KEY_GOLDEN, value) }
                            },
                        )
                        SunChartCard(ephemeris = ephemeris, isToday = isToday, clock = clock)
                        MoonCard(ephemeris = ephemeris, isToday = isToday, clock = clock)
                    }
                }
            }
            FooterNote(zone = zone, date = date)
        }
    }
}

@Composable
private fun rememberClock(): Clock {
    val clock = remember { Clock() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = Instant.now()
                clock.now = now
                val zone = ZoneId.systemDefault()
                if (zone != clock.zone) clock.zone = zone
                // Tick just after each new second.
                delay(1_000L - now.toEpochMilli() % 1_000L)
            }
        }
    }
    return clock
}

@Composable
private fun Header() {
    Column {
        Text("Soleil et Lune", color = Accent, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
        Text("Éphémérides hors ligne", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun PositionCard(
    place: SavedPlace?,
    today: LocalDate,
    zone: ZoneId,
    status: LocateStatus,
    onLocate: () -> Unit,
    onCancel: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onManual: (Double, Double) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SurvieCard {
        CardTitle("Position")
        Spacer(Modifier.height(6.dp))
        if (place == null) {
            Text("Aucune position enregistrée", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Caption("Utilisez la position de l'appareil ou saisissez des coordonnées.")
        } else {
            Text(
                formatCoordinates(place.latitude, place.longitude),
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                style = Tabular,
            )
            Caption(describePlace(place, today, zone))
        }
        Spacer(Modifier.height(12.dp))
        if (status == LocateStatus.SEARCHING) {
            SearchingPanel(onCancel)
        } else {
            PrimaryButton("Utiliser ma position", onLocate)
            LocateProblem(status, onOpenAppSettings, onOpenLocationSettings)
        }
        Spacer(Modifier.height(8.dp))
        if (editing) {
            ManualEntry(
                initial = place,
                onCancel = { editing = false },
                onValidate = { lat, lon ->
                    editing = false
                    onManual(lat, lon)
                },
            )
        } else {
            SecondaryButton("Saisir des coordonnées", onClick = { editing = true })
        }
    }
}

@Composable
private fun SearchingPanel(onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Navy, InnerShape)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), color = Accent, strokeWidth = 2.5.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Recherche de la position…", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Caption("Jusqu'à 30${NBSP}s. À découvert, le GPS répond plus vite.")
        }
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Annuler", color = Accent, fontSize = 15.sp)
        }
    }
}

@Composable
private fun LocateProblem(status: LocateStatus, onOpenAppSettings: () -> Unit, onOpenLocationSettings: () -> Unit) {
    val manual = "Vous pouvez aussi saisir les coordonnées ci-dessous."
    when (status) {
        LocateStatus.IDLE, LocateStatus.SEARCHING -> Unit
        LocateStatus.DENIED -> NoticeCardSpaced(
            title = "Autorisation refusée",
            message = "Sans accès à la position, l'application ne peut pas vous localiser. $manual",
        )
        LocateStatus.DENIED_FOREVER -> NoticeCardSpaced(
            title = "Accès à la position refusé",
            message = "Autorisez « Position » dans les paramètres de l'application. $manual",
            actionLabel = "Ouvrir les paramètres",
            onAction = onOpenAppSettings,
        )
        LocateStatus.UNAVAILABLE -> NoticeCardSpaced(
            title = "Localisation indisponible",
            message = "Aucun service de localisation utilisable n'a été trouvé sur cet appareil. $manual",
        )
        LocateStatus.DISABLED -> NoticeCardSpaced(
            title = "Localisation désactivée",
            message = "Activez la localisation de l'appareil, puis réessayez. $manual",
            actionLabel = "Activer la localisation",
            onAction = onOpenLocationSettings,
        )
        LocateStatus.TIMEOUT -> NoticeCardSpaced(
            title = "Aucune position obtenue",
            message = "Pas de position en 30${NBSP}s. Placez-vous à découvert et réessayez. $manual",
        )
    }
}

@Composable
private fun NoticeCardSpaced(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Spacer(Modifier.height(8.dp))
    NoticeCard(title = title, message = message, actionLabel = actionLabel, onAction = onAction)
}

@Composable
private fun ManualEntry(initial: SavedPlace?, onCancel: () -> Unit, onValidate: (Double, Double) -> Unit) {
    val focusManager = LocalFocusManager.current
    var latText by rememberSaveable { mutableStateOf(initial?.let { coordinateFieldText(it.latitude) } ?: "") }
    var lonText by rememberSaveable { mutableStateOf(initial?.let { coordinateFieldText(it.longitude) } ?: "") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val latitude = parseCoordinate(latText, isLatitude = true)
    val longitude = parseCoordinate(lonText, isLatitude = false)

    fun submit() {
        attempted = true
        if (latitude is CoordinateInput.Valid && longitude is CoordinateInput.Valid) {
            focusManager.clearFocus()
            onValidate(latitude.value, longitude.value)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CoordinateField(
            label = "Latitude",
            value = latText,
            onValueChange = { latText = it.take(MAX_FIELD_LENGTH) },
            parsed = latitude,
            showError = attempted,
            isLatitude = true,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        CoordinateField(
            label = "Longitude",
            value = lonText,
            onValueChange = { lonText = it.take(MAX_FIELD_LENGTH) },
            parsed = longitude,
            showError = attempted,
            isLatitude = false,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
        )
        Caption(
            "Degrés décimaux, virgule ou point. Nord et est positifs, sud et ouest négatifs " +
                "(bouton ±). Exemple pour Paris$NBSP: 48,8566 et 2,3522."
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Annuler", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton("Valider", onClick = { submit() }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CoordinateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    parsed: CoordinateInput,
    showError: Boolean,
    isLatitude: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    val error = (parsed as? CoordinateInput.Invalid)?.message
    val isError = showError && error != null
    val supporting = when {
        isError -> error.orEmpty()
        parsed is CoordinateInput.Valid ->
            if (isLatitude) formatLatitude(parsed.value) else formatLongitude(parsed.value)
        isLatitude -> "Nord positif, sud négatif"
        else -> "Est positif, ouest négatif"
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontFeatureSettings = "tnum"),
        label = { Text("$label (degrés)") },
        placeholder = { Text(if (isLatitude) "48,8566" else "2,3522") },
        trailingIcon = {
            IconButton(
                onClick = { onValueChange(toggleSign(value, isLatitude)) },
                modifier = Modifier.semantics {
                    contentDescription = if (isLatitude) {
                        "Changer d'hémisphère, nord ou sud"
                    } else {
                        "Changer d'hémisphère, est ou ouest"
                    }
                },
            ) {
                Text("±", fontSize = 24.sp, color = Accent)
            }
        },
        supportingText = { Text(supporting) },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        singleLine = true,
        shape = InnerShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Accent.copy(alpha = 0.45f),
            focusedLabelColor = Accent,
            cursorColor = Accent,
            unfocusedSupportingTextColor = TextSecondary,
            focusedSupportingTextColor = TextSecondary,
        ),
    )
}

@Composable
private fun WelcomeCard() {
    SurvieCard {
        CardTitle("Éphémérides")
        Spacer(Modifier.height(6.dp))
        Text(
            "Choisissez une position pour afficher le lever et le coucher du soleil et de la lune, " +
                "les crépuscules, la durée du jour et la phase de la lune.",
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(6.dp))
        Caption("Tout est calculé sur l'appareil, sans connexion.")
    }
}

@Composable
private fun LoadingCard() {
    SurvieCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Accent, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(12.dp))
            Text("Calcul des éphémérides…", color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
private fun DateCard(date: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SurvieCard(Modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChevronButton(pointsLeft = true, description = "Jour précédent", enabled = date.isAfter(MIN_DATE)) {
                onSelect(date.minusDays(1))
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(InnerShape)
                    .clickable(onClickLabel = "Choisir une date", role = Role.Button) { picking = true }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    formatLongDate(date),
                    color = Color.White,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    relativeDay(date, today),
                    color = if (date == today) Accent else TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            ChevronButton(pointsLeft = false, description = "Jour suivant", enabled = date.isBefore(MAX_DATE)) {
                onSelect(date.plusDays(1))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "Aujourd'hui",
                onClick = { onSelect(today) },
                modifier = Modifier.weight(1f),
                enabled = date != today,
            )
            SecondaryButton("Choisir une date", onClick = { picking = true }, modifier = Modifier.weight(1f))
        }
    }
    if (picking) {
        DateDialog(
            initial = date,
            onDismiss = { picking = false },
            onPick = { picked ->
                picking = false
                onSelect(picked)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // The Material date picker works on UTC midnights.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = MIN_DATE.year..MAX_DATE.year,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text("Valider", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = Accent) }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun SunCard(
    ephemeris: Ephemeris,
    isToday: Boolean,
    clock: Clock,
    showGolden: Boolean,
    onShowGoldenChange: (Boolean) -> Unit,
) {
    val sun = ephemeris.sun
    val window = sun.window
    val riseDirection = remember(sun) {
        sun.sunrises.firstOrNull()?.let {
            formatDirection(sunHorizontal(it.seconds(), ephemeris.latitude, ephemeris.longitude).azimuth)
        }
    }
    val setDirection = remember(sun) {
        sun.sunsets.firstOrNull()?.let {
            formatDirection(sunHorizontal(it.seconds(), ephemeris.latitude, ephemeris.longitude).azimuth)
        }
    }
    SurvieCard {
        CardTitle("Soleil")
        Spacer(Modifier.height(10.dp))
        if (isToday) {
            SunLivePanel(ephemeris, clock)
            Spacer(Modifier.height(10.dp))
        }
        when (sun.polar) {
            PolarState.POLAR_DAY -> NoticeCard(
                title = "Jour polaire",
                message = "Le soleil ne se couche pas de la journée$NBSP: il reste au-dessus de l'horizon.",
                titleColor = Accent,
            )
            PolarState.POLAR_NIGHT -> NoticeCard(
                title = "Nuit polaire",
                message = "Le soleil ne se lève pas de la journée$NBSP: il reste sous l'horizon.",
                titleColor = Accent,
            )
            null -> Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EventTile(
                    label = "Lever",
                    value = formatTimes(sun.sunrises, window),
                    detail = riseDirection ?: "pas de lever ce jour",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                EventTile(
                    label = "Coucher",
                    value = formatTimes(sun.sunsets, window),
                    detail = setDirection ?: "pas de coucher ce jour",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoRow(
            "Midi solaire",
            sun.noon?.let { "${formatTimeOfDay(it, window)}, hauteur ${formatAltitude(sun.noonAltitude)}" } ?: NO_VALUE,
        )
        InfoRow("Durée du jour", formatDayLength(sun.daylightSeconds))
        InfoRow("Écart avec la veille", formatDayLengthChange(sun.daylightSeconds - ephemeris.previousDaylightSeconds))

        Spacer(Modifier.height(10.dp))
        Text("Aube et crépuscule", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        TwilightHeader()
        TwilightRow("Civil (${MINUS}6°)", sun.civil, window)
        TwilightRow("Nautique (${MINUS}12°)", sun.nautical, window)
        TwilightRow("Astronomique (${MINUS}18°)", sun.astronomical, window)
        Spacer(Modifier.height(4.dp))
        Caption(
            "Jusqu'au crépuscule civil, la lumière suffit pour se déplacer sans lampe$NBSP; jusqu'au " +
                "nautique, l'horizon reste visible$NBSP; après l'astronomique, c'est la nuit noire."
        )

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = showGolden, role = Role.Switch, onValueChange = onShowGoldenChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Heures dorées et bleues",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            Switch(
                checked = showGolden,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Bg,
                    checkedTrackColor = Accent,
                    checkedBorderColor = Accent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = Navy,
                    uncheckedBorderColor = TextSecondary,
                ),
            )
        }
        if (showGolden) {
            InfoRow("Heure dorée", formatSpans(sun.goldenHours, window))
            InfoRow("Heure bleue", formatSpans(sun.blueHours, window))
            Caption(
                "Heure dorée$NBSP: soleil entre ${MINUS}4° et +6°, lumière chaude et rasante. " +
                    "Heure bleue$NBSP: soleil entre ${MINUS}6° et ${MINUS}4°."
            )
        }
    }
}

/** Clock times of the day's events, or a dash when there is none. */
private fun formatTimes(events: List<Instant>, window: DayWindow): String =
    if (events.isEmpty()) NO_VALUE else joinFrench(events.map { formatTimeOfDay(it, window) })

@Composable
private fun SunLivePanel(ephemeris: Ephemeris, clock: Clock) {
    val now = clock.now
    val zone = ephemeris.sun.window.zone
    val live = sunLive(now, ephemeris.latitude, ephemeris.longitude, ephemeris.horizonAhead)
    val text = describeSunLive(live, now, zone)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Navy, InnerShape)
            .padding(12.dp)
    ) {
        Text(
            text.headline,
            color = Color.White,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular,
        )
        Text(text.detail, color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, style = Tabular)
        Spacer(Modifier.height(6.dp))
        Text("Position actuelle", color = TextSecondary, fontSize = 13.sp)
        Text(
            describePosition(live.azimuth, live.altitude, live.up),
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            style = Tabular,
        )
    }
}

@Composable
private fun TwilightHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Spacer(Modifier.weight(1.3f))
        Text("Aube", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(
            "Crépuscule",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TwilightRow(label: String, twilight: Twilight, window: DayWindow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1.3f))
        when {
            twilight.alwaysAbove || twilight.alwaysBelow -> Text(
                if (twilight.alwaysAbove) "jamais atteint, soleil plus haut" else "jamais atteint, soleil plus bas",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(2f),
            )
            else -> {
                TimeCell(twilight.dawn, window, Modifier.weight(1f))
                TimeCell(twilight.dusk, window, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TimeCell(events: List<Instant>, window: DayWindow, modifier: Modifier) {
    Text(
        formatTimes(events, window),
        color = Color.White,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        style = Tabular,
        modifier = modifier,
    )
}

@Composable
private fun SunChartCard(ephemeris: Ephemeris, isToday: Boolean, clock: Clock) {
    val sun = ephemeris.sun
    val description = remember(sun) { describeChart(sun) }
    val now: (() -> Instant)? = remember(clock, isToday) { if (isToday) { { clock.now } } else null }
    SurvieCard {
        CardTitle("Hauteur du soleil")
        Caption(if (isToday) "Sur la journée, heure locale. Le point marque l'instant présent." else "Sur la journée, heure locale.")
        Spacer(Modifier.height(10.dp))
        SunChart(
            day = sun,
            latitude = ephemeris.latitude,
            longitude = ephemeris.longitude,
            now = now,
            description = description,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(DayBand, "Jour")
            LegendItem(CivilBand, "Civil")
            LegendItem(NauticalBand, "Nautique")
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(AstronomicalBand, "Astronomique")
            LegendItem(NightBand, "Nuit")
        }
    }
}

@Composable
private fun MoonCard(ephemeris: Ephemeris, isToday: Boolean, clock: Clock) {
    val moon = ephemeris.moon
    val window = moon.window
    val zone = window.zone
    // Today the phase follows the clock minute by minute; another day is shown at noon.
    val minute by remember(clock) { derivedStateOf { clock.now.epochSecond / 60L } }
    val at = if (isToday) Instant.ofEpochSecond(minute * 60L) else window.noon
    val snapshot = remember(moon, at) { moonSnapshot(moon, at) }
    val lightOnRight = litOnRight(snapshot.waxing, ephemeris.latitude)
    val reference = if (isToday) at else window.start
    val nextNew = moon.next(MoonPhase.NEW, reference)
    val nextFull = moon.next(MoonPhase.FULL, reference)
    val riseDirection = remember(moon) {
        moon.rises.firstOrNull()?.let {
            formatDirection(moonHorizontal(it.seconds(), ephemeris.latitude, ephemeris.longitude).azimuth)
        }
    }
    val setDirection = remember(moon) {
        moon.sets.firstOrNull()?.let {
            formatDirection(moonHorizontal(it.seconds(), ephemeris.latitude, ephemeris.longitude).azimuth)
        }
    }

    SurvieCard {
        CardTitle("Lune")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonDisc(
                fraction = snapshot.fraction,
                litOnRight = lightOnRight,
                description = "Dessin de la lune$NBSP: ${snapshot.phase.label}, éclairée à ${formatPercent(snapshot.fraction)}",
                modifier = Modifier.size(88.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    snapshot.phase.label,
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Éclairée à ${formatPercent(snapshot.fraction)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    style = Tabular,
                )
                Text(
                    "Âge$NBSP: ${formatAgeDays(snapshot.ageDays)}",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    style = Tabular,
                )
                Caption(if (isToday) "En ce moment" else "À 12:00 ce jour-là")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EventTile(
                label = "Lever de lune",
                value = formatTimes(moon.rises, window),
                detail = riseDirection ?: "pas de lever ce jour",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            EventTile(
                label = "Coucher de lune",
                value = formatTimes(moon.sets, window),
                detail = setDirection ?: "pas de coucher ce jour",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        if (moon.alwaysAbove || moon.alwaysBelow) {
            Spacer(Modifier.height(6.dp))
            Caption(
                if (moon.alwaysAbove) {
                    "La lune reste au-dessus de l'horizon toute la journée."
                } else {
                    "La lune reste sous l'horizon toute la journée."
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        if (isToday) MoonPositionRow(ephemeris, clock)
        InfoRow("Prochaine nouvelle lune", nextNew?.let { formatEventDate(it.time, zone, window.date.year) } ?: NO_VALUE)
        InfoRow("Prochaine pleine lune", nextFull?.let { formatEventDate(it.time, zone, window.date.year) } ?: NO_VALUE)
    }
}

@Composable
private fun MoonPositionRow(ephemeris: Ephemeris, clock: Clock) {
    val position = moonLive(clock.now, ephemeris.latitude, ephemeris.longitude)
    InfoRow("Position actuelle", describePosition(position.azimuth, position.altitude, position.up))
}

@Composable
private fun FooterNote(zone: ZoneId, date: LocalDate) {
    val offset = remember(zone, date) {
        zone.rules.getOffset(DayWindow(date, zone).noon).totalSeconds
    }
    Caption(
        "Heures locales du téléphone$NBSP: ${zone.id} (${formatUtcOffset(offset)} ce jour-là). " +
            "Calculs faits sur l'appareil pour un horizon dégagé au niveau de la mer$NBSP; " +
            "le relief peut retarder un lever ou avancer un coucher de plusieurs minutes.",
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

private fun SharedPreferences.readPlace(): SavedPlace? = try {
    decodePlace(getString(KEY_PLACE, null))
} catch (e: ClassCastException) {
    null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    launchSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

private fun Context.openLocationSettings() {
    if (!launchSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) launchSafely(Intent(Settings.ACTION_SETTINGS))
}

private fun Context.launchSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
}
