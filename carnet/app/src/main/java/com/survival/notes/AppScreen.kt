package com.survival.notes

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId

/** Larger backups are refused rather than risking running out of memory. */
private const val MAX_IMPORT_BYTES = 32L * 1024L * 1024L
private const val CLOCK_REFRESH_MS = 20_000L
private const val NOT_LOADED = "Les notes sont encore en cours de chargement. Réessayez dans un instant."
private const val WRITE_FAILED = "Échec de l'enregistrement du fichier. Vérifiez l'emplacement choisi et réessayez."
private const val NO_FILE_APP = "Aucune application de gestion de fichiers n'est disponible sur cet appareil."

/** Short messages at the bottom of the screen, optionally with an action such as "Annuler". */
internal class Messenger(private val scope: CoroutineScope, private val host: SnackbarHostState) {
    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = false,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val store = remember(context) { NoteStore.get(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = remember(scope, snackbarHostState) { Messenger(scope, snackbarHostState) }

    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var openIsNew by rememberSaveable { mutableStateOf(false) }
    // Kept here so the search and the scroll position survive a visit to a note.
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var pendingRestore by remember { mutableStateOf<List<Note>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val current = store.notes
            if (current == null) {
                messenger.show(NOT_LOADED)
            } else {
                val count = current.count { !it.isEmpty }
                scope.launch {
                    val ok = writeDocument(context, uri) {
                        exportText(current, System.currentTimeMillis(), ZoneId.systemDefault())
                    }
                    messenger.show(if (ok) "Export terminé : ${countNotes(count).lowercase()}." else WRITE_FAILED)
                }
            }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val current = store.notes
            if (current == null) {
                messenger.show(NOT_LOADED)
            } else {
                scope.launch {
                    val ok = writeDocument(context, uri) { NoteStore.encode(current) }
                    messenger.show(
                        if (ok) "Sauvegarde enregistrée : ${countNotes(current.size).lowercase()}." else WRITE_FAILED
                    )
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val incoming = readBackup(context, uri)
                if (incoming == null) {
                    messenger.show("Ce fichier n'est pas une sauvegarde Carnet lisible.")
                } else if (incoming.all { it.isEmpty }) {
                    messenger.show("La sauvegarde ne contient aucune note.")
                } else {
                    pendingRestore = incoming
                }
            }
        }
    }

    fun launchFilePicker(launch: () -> Unit) {
        try {
            launch()
        } catch (e: ActivityNotFoundException) {
            messenger.show(NO_FILE_APP)
        }
    }

    val notes = store.notes
    val editingId = openId
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
    ) {
        Scaffold(
            containerColor = Bg,
            contentColor = Color.White,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(data, containerColor = Navy, contentColor = Color.White, actionColor = Accent)
                }
            },
            floatingActionButton = {
                if (editingId == null && notes != null) {
                    FloatingActionButton(
                        onClick = {
                            // A new note must not come back hidden by a search that excludes it.
                            query = ""
                            openIsNew = true
                            openId = newNoteId()
                        },
                        containerColor = Accent,
                        contentColor = Bg,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nouvelle note")
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when {
                    notes == null -> LoadingState()
                    editingId != null -> key(editingId) {
                        NoteEditor(
                            store = store,
                            noteId = editingId,
                            isNew = openIsNew,
                            messenger = messenger,
                            onClose = {
                                openId = null
                                openIsNew = false
                            },
                        )
                    }
                    else -> NoteListScreen(
                        notes = notes,
                        query = query,
                        onQueryChange = { query = it },
                        listState = listState,
                        saveFailed = store.saveFailed,
                        loadFailed = store.loadFailed,
                        onRetrySave = { store.retrySave() },
                        onDismissLoadFailure = { store.dismissLoadFailure() },
                        onOpen = { id ->
                            openIsNew = false
                            openId = id
                        },
                        onExportText = {
                            launchFilePicker {
                                exportLauncher.launch(exportFileName(System.currentTimeMillis(), ZoneId.systemDefault()))
                            }
                        },
                        onBackup = {
                            launchFilePicker {
                                backupLauncher.launch(backupFileName(System.currentTimeMillis(), ZoneId.systemDefault()))
                            }
                        },
                        onRestore = { launchFilePicker { restoreLauncher.launch(arrayOf("*/*")) } },
                    )
                }
            }
        }
    }

    pendingRestore?.let { incoming ->
        val count = incoming.count { !it.isEmpty }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    val before = store.notes
                    val result = store.merge(incoming)
                    if (result == null || before == null) {
                        messenger.show(NOT_LOADED)
                    } else if (result.added + result.updated == 0) {
                        messenger.show(restoreSummary(result))
                    } else {
                        messenger.show(restoreSummary(result), "Annuler") {
                            if (!store.revert(before, result.notes)) {
                                messenger.show("Impossible d'annuler : des notes ont changé depuis la restauration.")
                            }
                        }
                    }
                }) { Text("Restaurer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Annuler") }
            },
            title = { Text("Restaurer la sauvegarde ?") },
            text = {
                Text(
                    "Ce fichier contient ${countNotes(count).lowercase()}. Les notes absentes du carnet seront " +
                        "ajoutées ; pour une note présente des deux côtés, la version la plus récente est " +
                        "conservée. Aucune note ne sera supprimée."
                )
            },
            containerColor = Card,
            titleContentColor = Color.White,
            textContentColor = TextSecondary,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}

/** Current wall-clock time, refreshed while the screen is visible so relative dates stay right. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(CLOCK_REFRESH_MS)
            }
        }
    }
    return now
}

@Composable
private fun NoteListScreen(
    notes: List<Note>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    saveFailed: Boolean,
    loadFailed: Boolean,
    onRetrySave: () -> Unit,
    onDismissLoadFailure: () -> Unit,
    onOpen: (String) -> Unit,
    onExportText: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val sorted = remember(notes) { sortNotes(notes) }
    val index = remember(sorted) { SearchIndex(sorted) }
    val shown = remember(index, query) { index.filter(query) }
    val searching = query.isNotBlank()
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    BackHandler(enabled = query.isNotEmpty()) { onQueryChange("") }

    Column(Modifier.fillMaxSize()) {
        ListHeader(
            subtitle = listSubtitle(notes.size, shown.size, searching),
            hasNotes = notes.isNotEmpty(),
            onExportText = onExportText,
            onBackup = onBackup,
            onRestore = onRestore,
        )
        if (notes.isNotEmpty() || query.isNotEmpty()) {
            SearchField(
                query = query,
                onChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (saveFailed) {
                item(key = "save-failed") {
                    NoticeCard(
                        title = "Enregistrement impossible",
                        titleColor = Danger,
                        message = "Les dernières modifications n'ont pas pu être écrites (stockage plein ?). " +
                            "Elles restent en mémoire tant que Carnet est ouvert : libérez de l'espace puis " +
                            "touchez « Réessayer », ou exportez vos notes.",
                        actionLabel = "Réessayer",
                        onAction = onRetrySave,
                    )
                }
            }
            if (loadFailed) {
                item(key = "load-failed") {
                    NoticeCard(
                        title = "Fichier des notes illisible",
                        titleColor = Warning,
                        message = "Le fichier des notes était endommagé. Une copie a été mise de côté et le carnet " +
                            "est reparti vide. Restaurez une sauvegarde si vous en avez une.",
                        dismissLabel = "Fermer",
                        onDismiss = onDismissLoadFailure,
                    )
                }
            }
            when {
                notes.isEmpty() -> item(key = "empty") { EmptyState() }
                shown.isEmpty() -> item(key = "no-result") { NoResult(query) }
                else -> {
                    val pinned = shown.filter { it.pinned }
                    val others = shown.filterNot { it.pinned }
                    if (pinned.isNotEmpty()) {
                        item(key = "label-pinned") { SectionLabel("Épinglées") }
                        items(pinned, key = { "note:" + it.id }) { note -> NoteRow(note, now, zone, onOpen) }
                    }
                    if (others.isNotEmpty()) {
                        if (pinned.isNotEmpty()) item(key = "label-others") { SectionLabel("Autres notes") }
                        items(others, key = { "note:" + it.id }) { note -> NoteRow(note, now, zone, onOpen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListHeader(
    subtitle: String,
    hasNotes: Boolean,
    onExportText: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Carnet", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 14.sp)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Exporter, sauvegarder ou restaurer", tint = Color.White)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Exporter en texte (.txt)") },
                    onClick = {
                        menuOpen = false
                        onExportText()
                    },
                    enabled = hasNotes,
                )
                DropdownMenuItem(
                    text = { Text("Sauvegarder (.json)") },
                    onClick = {
                        menuOpen = false
                        onBackup()
                    },
                    enabled = hasNotes,
                )
                DropdownMenuItem(
                    text = { Text("Restaurer une sauvegarde") },
                    onClick = {
                        menuOpen = false
                        onRestore()
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(Card, CardShape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            decorationBox = { innerTextField ->
                Box(Modifier.padding(vertical = 15.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Rechercher dans les notes",
                            color = TextSecondary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Clear, contentDescription = "Effacer la recherche", tint = TextSecondary)
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun NoteRow(note: Note, now: Long, zone: ZoneId, onOpen: (String) -> Unit) {
    val title = displayTitle(note)
    val preview = previewText(note)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .clickable(onClickLabel = "Ouvrir la note") { onOpen(note.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(preview, color = TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatRelative(note.modifiedAt, now, zone),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    style = Tabular,
                )
                if (note.location != null) {
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Position jointe",
                        tint = Accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (note.pinned) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Épinglée",
                tint = Accent,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(20.dp)
    ) {
        Text("Aucune note pour l'instant", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Touchez le bouton + pour écrire votre première note. Elle est enregistrée automatiquement " +
                "sur cet appareil, sans connexion ni compte.",
            color = TextSecondary,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun NoResult(query: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(20.dp)
    ) {
        Text(
            "Aucune note ne contient «\u00A0${query.trim()}\u00A0».",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "La recherche porte sur les titres et les textes, sans tenir compte des accents ni des majuscules.",
            color = TextSecondary,
            fontSize = 14.sp,
        )
    }
}

/** Writes the text made by [produce] (on Dispatchers.IO) to a document chosen by the user. */
private suspend fun writeDocument(context: Context, uri: Uri, produce: () -> String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val bytes = produce().toByteArray(Charsets.UTF_8)
            val stream = context.contentResolver.openForWrite(uri)
            if (stream == null) {
                false
            } else {
                stream.use {
                    it.write(bytes)
                    it.flush()
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

/** Truncating mode first ("wt"), which some providers do not support; plain "w" otherwise. */
private fun ContentResolver.openForWrite(uri: Uri): OutputStream? {
    val truncating = try {
        openOutputStream(uri, "wt")
    } catch (e: Exception) {
        null
    }
    return truncating ?: openOutputStream(uri, "w")
}

/** Notes of a backup file, or null when it cannot be read or is not a Carnet backup. */
private suspend fun readBackup(context: Context, uri: Uri): List<Note>? = withContext(Dispatchers.IO) {
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it) }
        if (bytes == null) null else NoteStore.decodeNotes(String(bytes, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }
}

private fun readLimited(input: InputStream): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > MAX_IMPORT_BYTES) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
