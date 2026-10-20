package com.survival.stocks

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private const val NO_EDIT = -1L
private const val NEW_ITEM = 0L
private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

private const val SOURCES =
    "Sources. Eau : kit d'urgence recommandé par le Gouvernement (guide « Tous responsables », " +
        "SGDSN, 2025), 6 L par personne pour tenir 72 h, soit 2 L par jour. Énergie : apport " +
        "de référence pour un adulte, 2 000 kcal par jour (règlement (UE) n° 1169/2011). Ce sont " +
        "des repères minimaux : prévoyez davantage par forte chaleur ou en cas d'effort, et pensez aux " +
        "besoins particuliers (bébés, traitements, animaux)."

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private class TooLargeException : IOException("too large")

@Composable
fun AppScreen(store: InventoryStore) {
    val inventory = store.inventory
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notifier = remember(snackbar, scope) { Notifier(snackbar, scope) }
    val listState = rememberLazyListState()

    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    // Statuses change at midnight even if the app stays open.
    LaunchedEffect(today) {
        val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay()).toMillis()
        delay(untilMidnight.coerceAtLeast(0L) + 1_000L)
        today = LocalDate.now()
    }

    var editingId by rememberSaveable { mutableStateOf(NO_EDIT) }
    var categoryKey by rememberSaveable { mutableStateOf("") }
    var alertsOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Inventory?>(null) }

    // An article that disappeared (undo, import…) closes its editor.
    LaunchedEffect(inventory, editingId) {
        if (inventory != null && editingId > 0 && inventory.item(editingId) == null) editingId = NO_EDIT
    }

    fun delete(id: Long) {
        val removed = store.mutate { inv -> inv.remove(id)?.let { (next, item, index) -> next to (item to index) } }
            ?: return
        val (item, index) = removed
        notifier.show("«$NBSP${item.name}$NBSP» supprimé", undo = { store.update { it.restore(item, index) } })
    }

    val appContext = context.applicationContext
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val inv = store.inventory
        if (uri != null && inv != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        writeBytes(appContext, uri, InventoryStore.encode(inv, pretty = true).toByteArray(Charsets.UTF_8))
                    }.isSuccess
                }
                notifier.show(
                    if (ok) "Sauvegarde enregistrée (${countLabel(inv.items.size, "article", "articles")})"
                    else "Impossible d'écrire le fichier."
                )
            }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val inv = store.inventory
        if (uri != null && inv != null) {
            val day = today
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        writeBytes(appContext, uri, buildCsv(inv.items, day, inv.settings.soonDays).toByteArray(Charsets.UTF_8))
                    }.isSuccess
                }
                notifier.show(if (ok) "Tableau CSV enregistré" else "Impossible d'écrire le fichier.")
            }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { InventoryStore.decode(readText(appContext, uri)) }
                }
                val decoded = result.getOrNull()
                val error = result.exceptionOrNull()
                if (decoded != null) {
                    if (decoded.items.isEmpty()) {
                        notifier.show("Ce fichier ne contient aucun article.")
                    } else {
                        pendingImport = decoded
                    }
                } else {
                    notifier.show(
                        when (error) {
                            is TooLargeException -> "Fichier trop volumineux pour une sauvegarde Stocks."
                            is JSONException, is IllegalArgumentException -> "Ce fichier n'est pas une sauvegarde Stocks."
                            else -> "Lecture du fichier impossible."
                        }
                    )
                }
            }
        }
    }

    fun launchSafely(action: () -> Unit) {
        try {
            action()
        } catch (e: ActivityNotFoundException) {
            notifier.show("Aucun gestionnaire de fichiers n'est disponible.")
        }
    }

    val actions = MenuActions(
        onSettings = { showSettings = true },
        onExportJson = { launchSafely { exportJson.launch(exportFileName(today, "json")) } },
        onImport = { launchSafely { importJson.launch(arrayOf("*/*")) } },
        onExportCsv = { launchSafely { exportCsv.launch(exportFileName(today, "csv")) } },
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
    ) {
        Scaffold(
            containerColor = Bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(data, containerColor = Navy, contentColor = Color.White, actionColor = Accent)
                }
            },
            floatingActionButton = {
                if (inventory != null && editingId == NO_EDIT) {
                    ExtendedFloatingActionButton(
                        text = { Text("Ajouter", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { editingId = NEW_ITEM },
                        containerColor = Accent,
                        contentColor = Bg
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when {
                    inventory == null -> Column(Modifier.padding(16.dp)) {
                        Header(actions, hasItems = false, enabled = false)
                        Spacer(Modifier.height(16.dp))
                        Text("Chargement…", color = TextSecondary, fontSize = 15.sp)
                    }
                    editingId != NO_EDIT -> key(editingId) {
                        val editing = if (editingId == NEW_ITEM) null else inventory.item(editingId)
                        ItemEditor(
                            initial = editing,
                            defaultCategory = Category.entries.firstOrNull { it.key == categoryKey } ?: Category.FOOD,
                            today = today,
                            soonDays = inventory.settings.soonDays,
                            onSave = { item ->
                                if (editing == null) {
                                    store.mutate { it.add(item) }
                                    notifier.show("«$NBSP${item.name}$NBSP» ajouté")
                                } else {
                                    store.update { it.replace(item) }
                                    notifier.show("Modifications enregistrées")
                                }
                                editingId = NO_EDIT
                            },
                            onDelete = if (editing == null) {
                                null
                            } else {
                                {
                                    editingId = NO_EDIT
                                    delete(editing.id)
                                }
                            },
                            onClose = { editingId = NO_EDIT }
                        )
                    }
                    else -> InventoryList(
                        inventory = inventory,
                        today = today,
                        listState = listState,
                        filter = ListFilter(Category.entries.firstOrNull { it.key == categoryKey }, alertsOnly, query),
                        actions = actions,
                        onQuery = { query = it },
                        onCategory = { c -> categoryKey = c?.key.orEmpty() },
                        onAlertsOnly = { alertsOnly = it },
                        onClearFilters = {
                            categoryKey = ""
                            alertsOnly = false
                            query = ""
                        },
                        onPersons = { n -> store.update { it.withSettings(it.settings.copy(persons = n)) } },
                        onAdd = { editingId = NEW_ITEM },
                        onEdit = { editingId = it },
                        onDelta = { id, delta -> store.update { it.adjustQuantity(id, delta) } },
                        onDelete = ::delete
                    )
                }
            }
        }
    }

    if (showSettings && inventory != null) {
        SettingsDialog(
            settings = inventory.settings,
            onDismiss = { showSettings = false },
            onSave = { s ->
                showSettings = false
                store.update { it.withSettings(s) }
                notifier.show("Réglages enregistrés")
            }
        )
    }

    val incoming = pendingImport
    if (incoming != null && inventory != null) {
        ImportDialog(
            incoming = incoming,
            currentCount = inventory.items.size,
            onDismiss = { pendingImport = null },
            onMerge = {
                pendingImport = null
                val result = store.mutate { inv -> mergeInto(inv, incoming.items).let { it.inventory to it } }
                if (result != null) {
                    notifier.show(mergeSummary(result.added, result.skipped))
                } else {
                    notifier.show(mergeSummary(0, incoming.items.size))
                }
            },
            onReplace = {
                pendingImport = null
                val previous = store.inventory
                store.update { replaceWith(incoming) }
                editingId = NO_EDIT
                val message = "Inventaire remplacé (${countLabel(incoming.items.size, "article", "articles")})"
                if (previous != null) {
                    notifier.show(message, undo = { store.update { previous } })
                } else {
                    notifier.show(message)
                }
            }
        )
    }
}

private class MenuActions(
    val onSettings: () -> Unit,
    val onExportJson: () -> Unit,
    val onImport: () -> Unit,
    val onExportCsv: () -> Unit,
)

private fun writeBytes(context: Context, uri: Uri, bytes: ByteArray) {
    val resolver = context.contentResolver
    // "wt" truncates an existing document; a few providers only accept "w".
    val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: resolver.openOutputStream(uri)
        ?: throw IOException("no output stream")
    out.use { it.write(bytes) }
}

private fun readText(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no input stream")
    val bytes = input.use { stream ->
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = stream.read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
            if (buffer.size() > MAX_IMPORT_BYTES) throw TooLargeException()
        }
        buffer.toByteArray()
    }
    return String(bytes, Charsets.UTF_8)
}

// ---------------------------------------------------------------- Header

@Composable
private fun Header(actions: MenuActions, hasItems: Boolean, enabled: Boolean = true) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Stocks",
                color = Accent,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text("Réserves, péremptions et autonomie", color = TextSecondary, fontSize = 14.sp)
        }
        Box {
            IconButton(onClick = { menu = true }, enabled = enabled) {
                Icon(Icons.Default.MoreVert, contentDescription = "Plus d'options", tint = Color.White)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Réglages du foyer") },
                    onClick = {
                        menu = false
                        actions.onSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Sauvegarder (fichier JSON)") },
                    enabled = hasItems,
                    onClick = {
                        menu = false
                        actions.onExportJson()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Restaurer ou importer (JSON)") },
                    onClick = {
                        menu = false
                        actions.onImport()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Exporter en tableau CSV") },
                    enabled = hasItems,
                    onClick = {
                        menu = false
                        actions.onExportCsv()
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- List

@Composable
private fun InventoryList(
    inventory: Inventory,
    today: LocalDate,
    listState: androidx.compose.foundation.lazy.LazyListState,
    filter: ListFilter,
    actions: MenuActions,
    onQuery: (String) -> Unit,
    onCategory: (Category?) -> Unit,
    onAlertsOnly: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onPersons: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelta: (Long, Double) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val settings = inventory.settings
    val autonomy = remember(inventory, today) { computeAutonomy(inventory.items, settings, today) }
    val visible = remember(inventory, today, filter) {
        applyFilter(sortByUrgency(inventory.items, today, settings.soonDays), filter, today, settings.soonDays)
    }
    val counts = remember(inventory) { inventory.items.groupingBy { it.category }.eachCount() }
    val focus = LocalFocusManager.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") { Header(actions, hasItems = inventory.items.isNotEmpty()) }
        item(key = "dashboard") {
            Dashboard(
                settings = settings,
                autonomy = autonomy,
                alertsOnly = filter.alertsOnly,
                onPersons = onPersons,
                onSettings = actions.onSettings,
                onAlerts = { onAlertsOnly(!filter.alertsOnly) }
            )
        }
        if (inventory.items.isEmpty()) {
            item(key = "empty") { EmptyState(onAdd = onAdd, onImport = actions.onImport) }
        } else {
            item(key = "search") {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { onQuery(it.take(60)) },
                    placeholder = { Text("Rechercher (nom, lieu, note…)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    trailingIcon = if (filter.query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer la recherche", tint = TextSecondary)
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "chips") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SurvieChip("Tous (${inventory.items.size})", filter.category == null && !filter.alertsOnly) {
                        onCategory(null)
                        onAlertsOnly(false)
                    }
                    SurvieChip("À surveiller (${autonomy.expiredCount + autonomy.soonCount})", filter.alertsOnly) {
                        onAlertsOnly(!filter.alertsOnly)
                    }
                    for (c in Category.entries) {
                        val n = counts[c] ?: 0
                        if (n > 0 || filter.category == c) {
                            SurvieChip("${c.label} ($n)", filter.category == c) {
                                onCategory(if (filter.category == c) null else c)
                            }
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item(key = "nomatch") {
                    SurvieCard {
                        Text("Aucun article ne correspond.", color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Modifiez la recherche ou les filtres pour voir vos autres articles.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        TextButton(onClick = onClearFilters) { Text("Effacer les filtres", color = Accent) }
                    }
                }
            } else if (filter.isActive) {
                item(key = "count") {
                    Text(
                        "${visible.size} sur ${countLabel(inventory.items.size, "article", "articles")}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
            items(visible, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    today = today,
                    soonDays = settings.soonDays,
                    onEdit = { onEdit(item.id) },
                    onDelta = { onDelta(item.id, it) },
                    onDelete = { onDelete(item.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, onImport: () -> Unit) {
    SurvieCard {
        Text("Votre inventaire est vide", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Notez vos réserves d'eau, de nourriture, de médicaments, de piles… Stocks calcule l'autonomie " +
                "de votre foyer et vous prévient avant la péremption.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Le kit d'urgence recommandé permet de tenir 72\u00A0heures\u00A0: eau (6\u00A0L par personne), nourriture non " +
                "périssable, médicaments, trousse de secours, lampe, radio et piles de rechange.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) { Text("Ajouter un premier article", fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Restaurer une sauvegarde", color = Accent)
        }
    }
}

@Composable
private fun ItemCard(
    item: Item,
    today: LocalDate,
    soonDays: Int,
    onEdit: () -> Unit,
    onDelta: (Double) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = statusOf(item, today, soonDays)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable(onClickLabel = "Modifier", onClick = onEdit)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(item.category.label, item.location).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (status != Status.NO_DATE) {
                StatusBadge(status, Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp))
            }
        }
        val expiry = item.expiry
        if (expiry != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Péremption$NBSP: ${expiryLine(expiry, today)}",
                color = if (isAlert(status)) statusColor(status) else TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        val water = waterLiters(item)
        val energy = itemKcal(item)
        val detail = when {
            water != null -> "Soit ${formatDecimal(water, 2)}${NBSP}L d'eau"
            needsLitersPerUnit(item.category, item.unit) -> "Contenance non renseignée\u00A0: non comptée"
            energy != null -> "Soit ${formatDecimal(energy, 0)}${NBSP}kcal"
            else -> null
        }
        if (detail != null) Text(detail, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
        if (item.note.isNotEmpty()) {
            Text(
                item.note,
                color = TextSecondary,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                quantityLabel(item),
                color = if (item.quantity > 0) Color.White else TextSecondary,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                modifier = Modifier.weight(1f)
            )
            StepButton("−", "Diminuer la quantité de «$NBSP${item.name}$NBSP»", item.quantity > 0) { onDelta(-1.0) }
            Spacer(Modifier.width(8.dp))
            StepButton("+", "Augmenter la quantité de «$NBSP${item.name}$NBSP»", item.quantity < MAX_QUANTITY) { onDelta(1.0) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer «$NBSP${item.name}$NBSP»", tint = TextSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------- Dashboard

@Composable
private fun Dashboard(
    settings: Settings,
    autonomy: Autonomy,
    alertsOnly: Boolean,
    onPersons: (Int) -> Unit,
    onSettings: () -> Unit,
    onAlerts: () -> Unit,
) {
    val target = settings.targetDays
    SurvieCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Autonomie du foyer",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Réglages du foyer", tint = Accent)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Personnes", color = Color.White, fontSize = 15.sp)
                Text("Objectif$NBSP: ${targetLabel(target)}", color = TextSecondary, fontSize = 13.sp)
            }
            StepButton("−", "Retirer une personne", settings.persons > 1) { onPersons(settings.persons - 1) }
            Text(
                "${settings.persons}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(48.dp)
                    .semantics { contentDescription = countLabel(settings.persons, "personne", "personnes") }
            )
            StepButton("+", "Ajouter une personne", settings.persons < MAX_PERSONS) { onPersons(settings.persons + 1) }
        }
        Spacer(Modifier.height(14.dp))

        val waterNotes = buildList<String> {
            if (autonomy.waterWithoutVolume > 0) {
                add(
                    notCountedNote(
                        autonomy.waterWithoutVolume,
                        "article d'eau sans contenance",
                        "articles d'eau sans contenance"
                    )
                )
            }
        }
        AutonomyBlock(
            label = "Eau",
            days = autonomy.waterDays,
            target = target,
            detail = "${formatDecimal(autonomy.waterLiters, 1)}${NBSP}L en stock · besoin " +
                "${formatDecimal(settings.persons * settings.waterPerDay, 1)}${NBSP}L par jour",
            notes = waterNotes
        )
        Spacer(Modifier.height(14.dp))
        val foodDays = autonomy.foodDays
        val foodNotes = buildList<String> {
            if (foodDays != null && autonomy.foodWithoutKcal > 0) {
                add(notCountedNote(autonomy.foodWithoutKcal, "aliment sans calories", "aliments sans calories"))
            }
        }
        AutonomyBlock(
            label = "Nourriture",
            days = foodDays,
            target = target,
            detail = when {
                foodDays != null ->
                    "${formatDecimal(autonomy.foodKcal, 0)}${NBSP}kcal en stock · besoin " +
                        "${formatDecimal(settings.persons * settings.kcalPerDay, 0)}${NBSP}kcal par jour"
                autonomy.foodItems == 0 ->
                    "Ajoutez vos aliments avec leurs calories (kcal) pour estimer cette autonomie."
                else -> "Indiquez les calories (kcal) de vos aliments pour estimer cette autonomie."
            },
            notes = foodNotes
        )
        if (autonomy.expiredExcluded > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                notCountedNote(autonomy.expiredExcluded, "article périmé", "articles périmés"),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        AlertsRow(autonomy.expiredCount, autonomy.soonCount, settings.soonDays, alertsOnly, onAlerts)
        Spacer(Modifier.height(10.dp))
        Text(
            if (settings.usesDefaultNeeds) {
                "Repères par personne et par jour\u00A0: 2${NBSP}L d'eau (kit d'urgence du Gouvernement) et " +
                    "2${THIN_NBSP}000${NBSP}kcal (apport de référence d'un adulte). Sources dans les réglages."
            } else {
                "Vos besoins par personne et par jour\u00A0: ${formatDecimal(settings.waterPerDay)}${NBSP}L d'eau et " +
                    "${formatDecimal(settings.kcalPerDay, 0)}${NBSP}kcal."
            },
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun AutonomyBlock(label: String, days: Double?, target: Int, detail: String, notes: List<String>) {
    val color = when {
        days == null -> TextSecondary
        days >= target -> Success
        days > 0 -> Warning
        else -> Danger
    }
    Column(Modifier.semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                if (days == null) "—" else formatDays(days),
                color = color,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(
            fraction = if (days == null || target <= 0) 0f else (days / target).coerceIn(0.0, 1.0).toFloat(),
            color = color
        )
        Spacer(Modifier.height(6.dp))
        Text(detail, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        for (n in notes) Text(n, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun AlertsRow(expired: Int, soon: Int, soonDays: Int, alertsOnly: Boolean, onClick: () -> Unit) {
    if (expired == 0 && soon == 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Success)
            )
            Spacer(Modifier.width(10.dp))
            Text(noAlertLabel(soonDays), color = TextSecondary, fontSize = 14.sp)
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Navy)
            .clickable(onClickLabel = if (alertsOnly) "Afficher tous les articles" else "Afficher ces articles", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            if (expired > 0) {
                Text(
                    countLabel(expired, "article périmé", "articles périmés"),
                    color = Danger,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (soon > 0) {
                Text(
                    countLabel(soon, "article bientôt périmé", "articles bientôt périmés"),
                    color = Warning,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(if (alertsOnly) "Tout voir" else "Voir", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- Dialogs

@Composable
private fun SettingsDialog(settings: Settings, onDismiss: () -> Unit, onSave: (Settings) -> Unit) {
    var water by rememberSaveable { mutableStateOf(formatDecimal(settings.waterPerDay, 2, grouping = false)) }
    var kcal by rememberSaveable { mutableStateOf(formatDecimal(settings.kcalPerDay, 0, grouping = false)) }
    var target by rememberSaveable { mutableStateOf(settings.targetDays.toString()) }
    var soon by rememberSaveable { mutableStateOf(settings.soonDays.toString()) }

    val w = parseDecimal(water)?.takeIf { it in Settings.MIN_WATER_PER_DAY..Settings.MAX_WATER_PER_DAY }
    val k = parseDecimal(kcal)?.takeIf { it in Settings.MIN_KCAL_PER_DAY..Settings.MAX_KCAL_PER_DAY }
    val t = parseWhole(target)?.takeIf { it in 1..Settings.MAX_TARGET_DAYS }
    val s = parseWhole(soon)?.takeIf { it in 0..Settings.MAX_SOON_DAYS }
    val valid = w != null && k != null && t != null && s != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages du foyer") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Besoins par personne et par jour", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                SettingField(
                    water, { water = it.take(8) }, "Eau", "L", w == null,
                    "Entre 0,1 et 20${NBSP}L.", KeyboardType.Decimal
                )
                SettingField(
                    kcal, { kcal = it.take(8) }, "Énergie", "kcal", k == null,
                    "Entre 100 et 10${THIN_NBSP}000${NBSP}kcal.", KeyboardType.Decimal
                )
                Text("Objectif d'autonomie", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (d in Settings.TARGET_CHOICES) {
                        SurvieChip(if (d == 3) "72${NBSP}h" else "$d${NBSP}jours", t == d) { target = d.toString() }
                    }
                }
                SettingField(target, { target = it.take(3) }, "Objectif", "jours", t == null, "Entre 1 et 365 jours.", KeyboardType.Number)
                Text("Péremption", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                SettingField(
                    soon, { soon = it.take(3) }, "Alerte «\u00A0bientôt périmé\u00A0»", "jours avant", s == null,
                    "Entre 0 et 365 jours.", KeyboardType.Number
                )
                Text(SOURCES, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                TextButton(onClick = {
                    water = formatDecimal(Settings.DEFAULT_WATER_PER_DAY, 2, grouping = false)
                    kcal = formatDecimal(Settings.DEFAULT_KCAL_PER_DAY, 0, grouping = false)
                    target = Settings.DEFAULT_TARGET_DAYS.toString()
                    soon = Settings.DEFAULT_SOON_DAYS.toString()
                }) { Text("Valeurs recommandées", color = Accent) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (w != null && k != null && t != null && s != null) {
                        onSave(settings.copy(waterPerDay = w, kcalPerDay = k, targetDays = t, soonDays = s))
                    }
                }
            ) { Text("Enregistrer", color = if (valid) Accent else TextSecondary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = Accent) }
        },
        containerColor = Card,
        titleContentColor = Color.White,
        textContentColor = TextSecondary
    )
}

@Composable
private fun SettingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    error: Boolean,
    errorText: String,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        isError = error,
        supportingText = if (error) {
            { Text(errorText) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ImportDialog(
    incoming: Inventory,
    currentCount: Int,
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importer la sauvegarde\u202F?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Le fichier contient ${countLabel(incoming.items.size, "article", "articles")}.",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text("Fusionner\u00A0: ajoute les articles absents de votre inventaire.", fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (currentCount == 0) {
                        "Remplacer\u00A0: reprend aussi les réglages du foyer enregistrés dans le fichier."
                    } else {
                        "Remplacer\u00A0: supprime vos ${countLabel(currentCount, "article actuel", "articles actuels")} " +
                            "et reprend les réglages du fichier."
                    },
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onMerge) { Text("Fusionner", color = Accent) }
                TextButton(onClick = onReplace) { Text("Remplacer", color = Accent) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = Accent) }
        },
        containerColor = Card,
        titleContentColor = Color.White,
        textContentColor = TextSecondary
    )
}
