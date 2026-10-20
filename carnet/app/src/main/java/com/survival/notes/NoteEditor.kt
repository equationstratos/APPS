package com.survival.notes

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Pause in typing after which the note is written. */
private const val AUTOSAVE_DELAY_MS = 500L

/** Below this accuracy a fix is precise enough not to blame the "approximate" permission. */
private const val APPROXIMATE_WARNING_M = 100.0

/** Lets both action buttons fit side by side on a 360 dp wide screen. */
private val CompactPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

/**
 * Edits one note. Title and text are saved 500 ms after the last keystroke, when the app goes to
 * the background and when the editor closes; pin and position are saved at once. A new note is
 * only created once it holds something.
 */
@Composable
internal fun NoteEditor(
    store: NoteStore,
    noteId: String,
    isNew: Boolean,
    messenger: Messenger,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openedAt = rememberSaveable { System.currentTimeMillis() }
    val initial = remember { store.find(noteId) }
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = initial?.title.orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    var body by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = initial?.body.orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    var draftPinned by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Set once the note has been deleted: nothing may write it back afterwards.
    var discarded by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }

    val stored = store.notes?.firstOrNull { it.id == noteId }
    val pinned = stored?.pinned ?: draftPinned
    val location = stored?.location

    fun currentNote(): Note {
        val base = store.find(noteId) ?: Note(id = noteId, createdAt = openedAt, pinned = draftPinned)
        return base.withContent(title.text, body.text, System.currentTimeMillis())
    }

    fun commit() {
        if (!discarded) store.save(currentNote())
    }

    LaunchedEffect(title.text, body.text) {
        delay(AUTOSAVE_DELAY_MS)
        commit()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { commit() }
    DisposableEffect(Unit) {
        onDispose { commit() }
    }
    LaunchedEffect(Unit) {
        if (isNew && initial == null) runCatching { titleFocus.requestFocus() }
    }

    fun close() {
        commit()
        val saved = store.find(noteId)
        if (saved != null && saved.isEmpty) {
            discarded = true
            store.delete(noteId)
            messenger.show("Note vide supprimée.")
        }
        onClose()
    }
    BackHandler { close() }

    // ---- Position ----
    var searching by remember { mutableStateOf(false) }
    var elapsedS by remember { mutableIntStateOf(0) }
    var provisional by remember { mutableStateOf<NoteLocation?>(null) }
    var failure by remember { mutableStateOf<LocateFailure?>(null) }
    var locateJob by remember { mutableStateOf<Job?>(null) }
    var approximateOnly by remember { mutableStateOf(context.isApproximateOnly()) }
    var permissionRefused by remember { mutableStateOf(false) }
    var permanentlyRefused by remember { mutableStateOf(false) }

    fun attach(found: NoteLocation) {
        store.save(currentNote().withLocation(found, System.currentTimeMillis()))
    }

    fun startLocate() {
        if (searching) return
        failure = null
        provisional = null
        permissionRefused = false
        searching = true
        locateJob = scope.launch {
            try {
                when (val result = locateOnce(context) { provisional = it }) {
                    is LocateResult.Found -> attach(result.location)
                    is LocateResult.Failed -> {
                        if (result.reason == LocateFailure.NO_PERMISSION) {
                            permissionRefused = true
                        } else {
                            failure = result.reason
                        }
                    }
                }
            } finally {
                searching = false
                provisional = null
                locateJob = null
            }
        }
    }

    fun useProvisional() {
        val candidate = provisional ?: return
        locateJob?.cancel()
        attach(candidate)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        approximateOnly = context.isApproximateOnly()
        if (context.hasLocationPermission()) {
            startLocate()
        } else {
            permissionRefused = true
            permanentlyRefused = !context.canAskLocationAgain()
        }
    }

    fun requestPosition() {
        failure = null
        if (context.hasLocationPermission()) {
            startLocate()
        } else {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    // Back from the settings with the permission granted: finish what the user asked for.
    LifecycleResumeEffect(Unit) {
        approximateOnly = context.isApproximateOnly()
        if (permissionRefused && context.hasLocationPermission()) startLocate()
        onPauseOrDispose { }
    }
    // No location request may outlive the visible screen.
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            if (searching) {
                locateJob?.cancel()
                failure = LocateFailure.INTERRUPTED
            }
        }
    }
    LaunchedEffect(searching) {
        elapsedS = 0
        if (searching) {
            val start = SystemClock.elapsedRealtime()
            while (true) {
                delay(1_000L)
                elapsedS = ((SystemClock.elapsedRealtime() - start) / 1_000L).toInt()
            }
        }
    }

    // ---- Actions ----
    fun togglePin() {
        val next = !pinned
        draftPinned = next
        store.save(currentNote().withPinned(next))
    }

    fun insertTimestamp() {
        val stamp = formatTimestampStamp(System.currentTimeMillis(), ZoneId.systemDefault())
        val edit = insertOnOwnLine(body.text, body.selection.start, body.selection.end, stamp)
        body = TextFieldValue(edit.text, TextRange(edit.cursor))
        runCatching { bodyFocus.requestFocus() }
    }

    fun share() {
        commit()
        val note = currentNote()
        if (note.isEmpty) {
            messenger.show("La note est vide : rien à partager.")
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, displayTitle(note))
            putExtra(Intent.EXTRA_TEXT, shareText(note, ZoneId.systemDefault()))
        }
        if (!context.launchSafely(Intent.createChooser(send, "Partager la note"))) {
            messenger.show("Aucune application ne permet de partager cette note.")
        }
    }

    fun copyLocation(place: NoteLocation) {
        val copied = context.copyText("Position", coordinatesForClipboard(place.latitude, place.longitude))
        if (!copied) {
            messenger.show("Impossible de copier les coordonnées.")
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ confirms the copy itself.
            messenger.show("Coordonnées copiées.")
        }
    }

    fun removeLocation() {
        val before = currentNote()
        val old = before.location ?: return
        store.save(before.withLocation(null, System.currentTimeMillis()))
        messenger.show("Position retirée.", "Annuler") {
            store.save((store.find(noteId) ?: before).withLocation(old, System.currentTimeMillis()))
        }
    }

    fun deleteNote() {
        val snapshot = currentNote()
        locateJob?.cancel()
        discarded = true
        store.delete(noteId)
        onClose()
        if (!snapshot.isEmpty) {
            messenger.show("Note supprimée.", "Annuler") { store.save(snapshot) }
        }
    }

    // ---- Layout ----
    Column(Modifier.fillMaxSize()) {
        EditorTopBar(
            pinned = pinned,
            onBack = { close() },
            onTogglePin = { togglePin() },
            onShare = { share() },
            onDelete = { confirmDelete = true },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton(
                text = "Horodater",
                onClick = { insertTimestamp() },
                icon = Icons.Default.DateRange,
                fontSize = 14.sp,
                contentPadding = CompactPadding,
            )
            SecondaryButton(
                text = if (location == null) "Joindre ma position" else "Nouvelle position",
                onClick = { requestPosition() },
                modifier = Modifier.weight(1f),
                enabled = !searching,
                icon = Icons.Default.LocationOn,
                fontSize = 14.sp,
                contentPadding = CompactPadding,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Card, CardShape)
                    .padding(16.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { value ->
                        // The title stays on one logical line: the Enter key moves on to the text
                        // (keyboards that ignore "Suivant"), a pasted line break becomes a space.
                        when {
                            '\n' !in value.text -> title = value
                            value.text.replace("\n", "") == title.text -> runCatching { bodyFocus.requestFocus() }
                            else -> title = value.copy(text = value.text.replace('\n', ' '))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus),
                    textStyle = TextStyle(color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { runCatching { bodyFocus.requestFocus() } }),
                    decorationBox = { innerTextField ->
                        Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.CenterStart) {
                            if (title.text.isEmpty()) {
                                Text("Titre", color = TextSecondary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            }
                            innerTextField()
                        }
                    },
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Navy)
                BasicTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .focusRequester(bodyFocus),
                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp, lineHeight = 25.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { innerTextField ->
                        Box {
                            if (body.text.isEmpty()) {
                                Text("Écrivez votre note…", color = TextSecondary, fontSize = 17.sp, lineHeight = 25.sp)
                            }
                            innerTextField()
                        }
                    },
                )
            }

            val currentFailure = failure
            when {
                searching -> SearchingCard(
                    elapsedS = elapsedS,
                    provisional = provisional,
                    onUse = { useProvisional() },
                    onCancel = { locateJob?.cancel() },
                )
                permissionRefused -> NoticeCard(
                    title = "Accès à la position refusé",
                    titleColor = Warning,
                    message = if (permanentlyRefused) {
                        "Pour joindre votre position à une note, autorisez « Position » dans les paramètres " +
                            "de l'application. La position reste sur l'appareil."
                    } else {
                        "Carnet utilise la position uniquement pour l'ajouter à cette note, à votre demande. " +
                            "Elle n'est jamais envoyée."
                    },
                    actionLabel = if (permanentlyRefused) "Ouvrir les paramètres" else "Autoriser",
                    onAction = {
                        if (permanentlyRefused) {
                            context.openAppSettings()
                        } else {
                            permissionLauncher.launch(LOCATION_PERMISSIONS)
                        }
                    },
                    dismissLabel = "Fermer",
                    onDismiss = { permissionRefused = false },
                )
                currentFailure != null -> FailureCard(
                    failure = currentFailure,
                    onRetry = { requestPosition() },
                    onOpenLocationSettings = { context.openLocationSettings() },
                    onDismiss = { failure = null },
                )
            }

            if (location != null) {
                LocationCard(
                    place = location,
                    approximateOnly = approximateOnly,
                    onCopy = { copyLocation(location) },
                    onRemove = { removeLocation() },
                )
            }

            DatesFooter(stored)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        deleteNote()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger),
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            },
            title = { Text("Supprimer cette note ?") },
            text = { Text("La note disparaîtra du carnet. Vous pourrez encore annuler la suppression juste après.") },
            containerColor = Card,
            titleContentColor = Color.White,
            textContentColor = TextSecondary,
        )
    }
}

@Composable
private fun EditorTopBar(
    pinned: Boolean,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour à la liste", tint = Color.White)
        }
        Text(
            "Carnet",
            color = Accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconToggleButton(checked = pinned, onCheckedChange = { onTogglePin() }) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Épingler en haut de la liste",
                tint = if (pinned) Accent else Color.White.copy(alpha = 0.45f),
            )
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = "Partager la note", tint = Color.White)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Supprimer la note", tint = Color.White)
        }
    }
}

@Composable
private fun SearchingCard(
    elapsedS: Int,
    provisional: NoteLocation?,
    onUse: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Accent, strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "Recherche de la position… ${elapsedS.coerceIn(0, LOCATION_TIMEOUT_S)} s",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                style = Tabular,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Le GPS peut demander jusqu'à $LOCATION_TIMEOUT_S s ; il capte mieux à découvert, loin des bâtiments.",
            color = TextSecondary,
            fontSize = 14.sp,
        )
        if (provisional != null) {
            Spacer(Modifier.height(12.dp))
            val accuracy = provisional.accuracyM?.let { formatAccuracy(it) } ?: "précision inconnue"
            Text(
                "Position approchée disponible ($accuracy) :",
                color = Color.White,
                fontSize = 14.sp,
            )
            Text(
                formatDecimal(provisional.latitude, provisional.longitude),
                color = Color.White,
                fontSize = 16.sp,
                style = Tabular,
            )
            Spacer(Modifier.height(8.dp))
            AccentButton("Utiliser cette position", onUse, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Annuler la recherche", onCancel, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FailureCard(
    failure: LocateFailure,
    onRetry: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, message) = when (failure) {
        LocateFailure.UNAVAILABLE -> "Position indisponible" to
            "Cet appareil ne dispose d'aucun moyen de localisation utilisable."
        LocateFailure.DISABLED -> "Localisation désactivée" to
            "Activez la localisation de l'appareil, puis réessayez. Aucune connexion n'est nécessaire pour le GPS."
        LocateFailure.TIMEOUT -> "Aucune position obtenue" to
            "Aucune position en $LOCATION_TIMEOUT_S s. Placez-vous à découvert, loin des bâtiments, puis réessayez."
        LocateFailure.INTERRUPTED -> "Recherche interrompue" to
            "La recherche s'arrête quand Carnet passe en arrière-plan. Réessayez."
        LocateFailure.NO_PERMISSION -> "Accès à la position refusé" to
            "Autorisez l'accès à la position pour la joindre à la note."
    }
    NoticeCard(
        title = title,
        titleColor = Warning,
        message = message,
        actionLabel = when (failure) {
            LocateFailure.UNAVAILABLE -> null
            LocateFailure.DISABLED -> "Activer la localisation"
            else -> "Réessayer"
        },
        onAction = if (failure == LocateFailure.DISABLED) onOpenLocationSettings else onRetry,
        dismissLabel = "Fermer",
        onDismiss = onDismiss,
    )
}

@Composable
private fun LocationCard(
    place: NoteLocation,
    approximateOnly: Boolean,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Position jointe", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            formatDecimal(place.latitude, place.longitude),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            style = Tabular,
        )
        Spacer(Modifier.height(2.dp))
        Text(formatDms(place.latitude, place.longitude), color = Accent, fontSize = 16.sp, style = Tabular)
        Spacer(Modifier.height(6.dp))
        Text(locationDetails(place, ZoneId.systemDefault()), color = TextSecondary, fontSize = 13.sp)
        if (approximateOnly && (place.accuracyM ?: Double.MAX_VALUE) > APPROXIMATE_WARNING_M) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Seule la position approximative est autorisée : l'écart peut atteindre plusieurs kilomètres.",
                color = Warning,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentButton("Copier", onCopy, Modifier.weight(1f))
            SecondaryButton("Retirer", onRemove, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DatesFooter(stored: Note?) {
    val zone = ZoneId.systemDefault()
    val text = if (stored == null) {
        "Nouvelle note : elle est enregistrée automatiquement dès que vous écrivez."
    } else {
        "Créée le ${formatFullDateTime(stored.createdAt, zone)}\n" +
            "Modifiée le ${formatFullDateTime(stored.modifiedAt, zone)}\n" +
            "Enregistrement automatique sur l'appareil."
    }
    Text(
        text,
        color = TextSecondary,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
