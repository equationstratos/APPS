package com.survival.checklist

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")
private val RowShape = RoundedCornerShape(12.dp)

/** Id used by the « Ajouter un article » dialog for « Nouvelle section ». */
private const val NEW_SECTION = -1L
private const val NONE_ID = -1L

private enum class HomeDialog { NONE, CHOOSE, NAME, RENAME, DELETE }

private enum class ListDialog { NONE, ADD, EDIT_ITEM, RENAME_LIST, RENAME_SECTION, DELETE_SECTION, UNCHECK }

/** Short messages at the bottom of the screen, with an optional « Annuler » action. */
private class Notifier(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, undo: (() -> Unit)? = null) {
        host.currentSnackbarData?.dismiss()
        scope.launch {
            val result = host.showSnackbar(
                message = message,
                actionLabel = if (undo != null) "Annuler" else null,
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }
}

@Composable
fun AppScreen(store: ChecklistStore) {
    val library = store.library
    var openId by rememberSaveable { mutableStateOf(NONE_ID) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notifier = remember(snackbar, scope) { Notifier(snackbar, scope) }
    val homeState = rememberLazyListState()

    // A list that disappeared (deleted elsewhere, unreadable file…) sends back to the home screen.
    LaunchedEffect(library, openId) {
        if (library != null && openId != NONE_ID && library.list(openId) == null) openId = NONE_ID
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
    ) {
        val open = if (library != null && openId != NONE_ID) library.list(openId) else null
        when {
            library == null -> LoadingScreen()
            open != null -> key(open.id) {
                ListScreen(open, store, notifier, onBack = { openId = NONE_ID })
            }
            else -> HomeScreen(library, store, notifier, homeState, onOpen = { openId = it })
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) { data ->
            Snackbar(data, containerColor = Navy, contentColor = Color.White, actionColor = Accent)
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun AppHeader(subtitle: String) {
    Column {
        Text(
            "Check-lists",
            color = Accent,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(subtitle, color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun LoadingScreen() {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        AppHeader("Chargement…")
    }
}

@Composable
private fun HomeScreen(
    lib: Library,
    store: ChecklistStore,
    notifier: Notifier,
    state: LazyListState,
    onOpen: (Long) -> Unit
) {
    var dialog by rememberSaveable { mutableStateOf(HomeDialog.NONE) }
    var targetId by rememberSaveable { mutableStateOf(NONE_ID) }
    val target = lib.list(targetId)

    LaunchedEffect(dialog, target) {
        if ((dialog == HomeDialog.RENAME || dialog == HomeDialog.DELETE) && target == null) dialog = HomeDialog.NONE
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            AppHeader("Préparation et urgences · ${countLabel(lib.lists.size, "liste", "listes")}")
        }
        item(key = "new") {
            PrimaryButton(
                label = "Nouvelle liste",
                icon = Icons.Default.Add,
                onClick = { dialog = HomeDialog.CHOOSE },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (lib.lists.isEmpty()) {
            item(key = "empty") {
                MessageCard(
                    "Aucune liste",
                    "Créez une liste vide ou partez d'un modèle : kit d'urgence 72 h, sac d'évacuation, " +
                        "trousse de premiers secours, départ en randonnée…"
                )
            }
        }
        items(lib.lists, key = { it.id }) { list ->
            ListCard(
                list = list,
                onOpen = { onOpen(list.id) },
                onRename = {
                    targetId = list.id
                    dialog = HomeDialog.RENAME
                },
                onDuplicate = {
                    val copyId = store.mutate { it.duplicate(list.id) }
                    val copy = if (copyId != null) store.library?.list(copyId) else null
                    if (copy != null) notifier.show("Copie créée : ${quoted(copy.name)}")
                },
                onDelete = {
                    targetId = list.id
                    dialog = HomeDialog.DELETE
                }
            )
        }
    }

    val close = { dialog = HomeDialog.NONE }
    when (dialog) {
        HomeDialog.NONE -> Unit
        HomeDialog.CHOOSE -> NewListDialog(
            onEmpty = { dialog = HomeDialog.NAME },
            onTemplate = { template ->
                close()
                val id = store.mutate { it.createFromTemplate(template) }
                if (id != null) onOpen(id)
            },
            onDismiss = close
        )
        HomeDialog.NAME -> TextInputDialog(
            title = "Liste vide",
            label = "Nom de la liste",
            initial = "",
            confirmLabel = "Créer",
            maxLength = Limits.NAME_MAX,
            onConfirm = { name ->
                close()
                val id = store.mutate { it.createEmpty(name) }
                if (id != null) onOpen(id)
            },
            onDismiss = close,
            validate = { if (lib.nameTaken(it)) "Une liste porte déjà ce nom." else null }
        )
        HomeDialog.RENAME -> if (target != null) {
            TextInputDialog(
                title = "Renommer la liste",
                label = "Nom de la liste",
                initial = target.name,
                confirmLabel = "Renommer",
                maxLength = Limits.NAME_MAX,
                onConfirm = { name ->
                    close()
                    store.update { it.rename(target.id, name) }
                },
                onDismiss = close,
                validate = { if (lib.nameTaken(it, target.id)) "Une autre liste porte déjà ce nom." else null }
            )
        }
        HomeDialog.DELETE -> if (target != null) {
            val total = target.progress.total
            ConfirmDialog(
                title = "Supprimer la liste ?",
                text = if (total == 0) {
                    "La liste ${quoted(target.name)} sera supprimée."
                } else {
                    "La liste ${quoted(target.name)} et ${itemsPhrase(total)} seront supprimés."
                },
                confirmLabel = "Supprimer",
                onConfirm = {
                    close()
                    val removed = store.mutate { it.removeList(target.id) }
                    if (removed != null) {
                        notifier.show("Liste ${quoted(removed.list.name)} supprimée") {
                            store.update { it.restoreList(removed) }
                        }
                    }
                },
                onDismiss = close
            )
        }
    }
}

@Composable
private fun ListCard(
    list: Checklist,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val p = list.progress
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .clickable(onClickLabel = "Ouvrir", role = Role.Button, onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 6.dp)
            ) {
                Text(
                    list.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (p.isComplete) "Complète · ${progressLabel(p)}" else progressLabel(p),
                    color = if (p.isComplete) Success else TextSecondary,
                    fontSize = 14.sp,
                    style = TabularDigits
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options de la liste ${quoted(list.name)}",
                        tint = TextSecondary
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        onClick = {
                            menu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Dupliquer") },
                        onClick = {
                            menu = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = Danger) },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(
            fraction = p.fraction,
            color = if (p.isComplete) Success else Accent,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

@Composable
private fun NewListDialog(onEmpty: () -> Unit, onTemplate: (Template) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Nouvelle liste") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceRow(
                    title = "Liste vide",
                    body = "À remplir vous-même, section par section.",
                    meta = null,
                    onClick = onEmpty
                )
                Text(
                    "Depuis un modèle",
                    color = Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() }
                )
                Text(
                    "Contenus repris des recommandations officielles (gouvernement, Croix-Rouge, " +
                        "Sécurité routière…). Tout reste modifiable.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                for (t in Templates.all) {
                    ChoiceRow(
                        title = t.name,
                        body = t.summary,
                        meta = countLabel(t.itemCount, "article", "articles"),
                        onClick = { onTemplate(t) }
                    )
                }
            }
        }
    )
}

@Composable
private fun ChoiceRow(title: String, body: String, meta: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RowShape)
            .background(Navy)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        if (meta != null) {
            Text(meta, color = Accent, fontSize = 12.sp, style = TabularDigits)
        }
    }
}

// ---------------------------------------------------------------- One list

@Composable
private fun ListScreen(list: Checklist, store: ChecklistStore, notifier: Notifier, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val listId = list.id
    var organize by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(ListDialog.NONE) }
    var targetId by rememberSaveable { mutableStateOf(NONE_ID) }
    val template = Templates.byKey(list.templateKey)

    // Always applied to the latest stored version of the list, never to a stale copy.
    fun change(f: (Checklist) -> Checklist) = store.update { it.update(listId, f) }

    val targetItem = list.findItem(targetId)
    val targetSection = list.findSection(targetId)
    LaunchedEffect(dialog, targetItem, targetSection) {
        val missing = when (dialog) {
            ListDialog.EDIT_ITEM -> targetItem == null
            ListDialog.RENAME_SECTION, ListDialog.DELETE_SECTION -> targetSection == null
            else -> false
        }
        if (missing) dialog = ListDialog.NONE
    }

    fun deleteSection(sectionId: Long) {
        val removed = store.mutate { it.removeSection(listId, sectionId) }
        if (removed != null) {
            notifier.show("Section ${quoted(removed.section.title)} supprimée") {
                store.update { it.restoreSection(removed) }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = list.name, onBack = onBack) {
            IconButton(onClick = { shareList(context, list) }) {
                Icon(Icons.Default.Share, contentDescription = "Partager la liste", tint = Color.White)
            }
            IconButton(onClick = { dialog = ListDialog.RENAME_LIST }) {
                Icon(Icons.Default.Edit, contentDescription = "Renommer la liste", tint = Color.White)
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "summary") {
                SummaryCard(
                    list = list,
                    organize = organize,
                    onHideChecked = { hide -> change { it.copy(hideChecked = hide) } },
                    onOrganize = { organize = it },
                    onUncheckAll = { dialog = ListDialog.UNCHECK }
                )
            }
            if (list.sections.isEmpty()) {
                item(key = "empty") {
                    MessageCard(
                        "Liste vide",
                        "Ajoutez un premier article avec le bouton ci-dessous et rangez-le dans une section " +
                            "(par exemple « Eau et nourriture »)."
                    )
                }
            }
            items(list.sections, key = { it.id }) { section ->
                SectionCard(
                    section = section,
                    organize = organize,
                    hideChecked = list.hideChecked,
                    onToggle = { itemId -> change { it.toggle(itemId) } },
                    onAdd = {
                        targetId = section.id
                        dialog = ListDialog.ADD
                    },
                    onRename = {
                        targetId = section.id
                        dialog = ListDialog.RENAME_SECTION
                    },
                    onDelete = {
                        if (section.items.isEmpty()) {
                            deleteSection(section.id)
                        } else {
                            targetId = section.id
                            dialog = ListDialog.DELETE_SECTION
                        }
                    },
                    onMove = { itemId, delta -> change { it.moveItem(itemId, delta) } },
                    onEdit = { itemId ->
                        targetId = itemId
                        dialog = ListDialog.EDIT_ITEM
                    }
                )
            }
            item(key = "add") {
                PrimaryButton(
                    label = "Ajouter un article",
                    icon = Icons.Default.Add,
                    onClick = {
                        targetId = NONE_ID
                        dialog = ListDialog.ADD
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (template != null) {
                item(key = "sources") { SourcesCard(template) }
            }
        }
    }

    val close = { dialog = ListDialog.NONE }
    when (dialog) {
        ListDialog.NONE -> Unit
        ListDialog.ADD -> AddItemDialog(
            list = list,
            initialSectionId = targetId,
            onAdd = { sectionId, sectionTitle, text ->
                close()
                store.update {
                    if (sectionId == NEW_SECTION) it.addItemInSection(listId, sectionTitle, text)
                    else it.addItem(listId, sectionId, text)
                }
            },
            onDismiss = close
        )
        ListDialog.EDIT_ITEM -> if (targetItem != null) {
            TextInputDialog(
                title = "Modifier l'article",
                label = "Article",
                initial = targetItem.text,
                confirmLabel = "Enregistrer",
                maxLength = Limits.ITEM_MAX,
                onConfirm = { text ->
                    close()
                    change { it.editItem(targetItem.id, text) }
                },
                onDismiss = close,
                validate = {
                    if (list.containsText(it, exceptItemId = targetItem.id)) "Cet article existe déjà dans la liste." else null
                },
                singleLine = false,
                onDelete = {
                    close()
                    val removed = store.mutate { it.removeItem(listId, targetItem.id) }
                    if (removed != null) {
                        notifier.show("Article supprimé") { store.update { it.restoreItem(removed) } }
                    }
                }
            )
        }
        ListDialog.RENAME_LIST -> TextInputDialog(
            title = "Renommer la liste",
            label = "Nom de la liste",
            initial = list.name,
            confirmLabel = "Renommer",
            maxLength = Limits.NAME_MAX,
            onConfirm = { name ->
                close()
                store.update { it.rename(listId, name) }
            },
            onDismiss = close,
            validate = {
                if (store.library?.nameTaken(it, listId) == true) "Une autre liste porte déjà ce nom." else null
            }
        )
        ListDialog.RENAME_SECTION -> if (targetSection != null) {
            TextInputDialog(
                title = "Renommer la section",
                label = "Nom de la section",
                initial = targetSection.title,
                confirmLabel = "Renommer",
                maxLength = Limits.SECTION_MAX,
                onConfirm = { title ->
                    close()
                    change { it.renameSection(targetSection.id, title) }
                },
                onDismiss = close,
                validate = {
                    if (list.sectionTitleTaken(it, targetSection.id)) "Une autre section porte déjà ce nom." else null
                }
            )
        }
        ListDialog.DELETE_SECTION -> if (targetSection != null) {
            ConfirmDialog(
                title = "Supprimer la section ?",
                text = "La section ${quoted(targetSection.title)} et " +
                    "${itemsPhrase(targetSection.items.size)} seront supprimés.",
                confirmLabel = "Supprimer",
                onConfirm = {
                    close()
                    deleteSection(targetSection.id)
                },
                onDismiss = close
            )
        }
        ListDialog.UNCHECK -> ConfirmDialog(
            title = "Tout décocher ?",
            text = uncheckMessage(list.progress.done),
            confirmLabel = "Tout décocher",
            onConfirm = {
                close()
                val checked = store.library?.list(listId)?.checkedIds().orEmpty()
                if (checked.isNotEmpty()) {
                    change { it.uncheckAll() }
                    notifier.show("Toutes les cases sont décochées") { change { it.recheck(checked) } }
                }
            },
            onDismiss = close
        )
    }
}

@Composable
private fun SummaryCard(
    list: Checklist,
    organize: Boolean,
    onHideChecked: (Boolean) -> Unit,
    onOrganize: (Boolean) -> Unit,
    onUncheckAll: () -> Unit
) {
    val p = list.progress
    val color = if (p.isComplete) Success else Accent
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${p.done}",
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                style = TabularDigits
            )
            Text(
                " / ${p.total}",
                color = TextSecondary,
                fontSize = 22.sp,
                style = TabularDigits,
                modifier = Modifier.padding(bottom = 5.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${p.percent} %",
                color = color,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                style = TabularDigits,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Text(
            when {
                p.total == 0 -> "Aucun article pour l'instant"
                p.isComplete -> "Tout est prêt !"
                else -> "${countLabel(p.total - p.done, "article", "articles")} à cocher"
            },
            color = if (p.isComplete) Success else TextSecondary,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        ProgressBar(fraction = p.fraction, color = color)
        Spacer(Modifier.height(14.dp))
        // Same height for both buttons when one label wraps on a narrow screen.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleButton(
                label = "Masquer les cochés",
                checked = list.hideChecked,
                onChange = onHideChecked,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            ToggleButton(
                label = "Réorganiser",
                checked = organize,
                onChange = onOrganize,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            label = "Tout décocher",
            onClick = onUncheckAll,
            enabled = p.done > 0,
            modifier = Modifier.fillMaxWidth()
        )
        if (organize) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Flèches pour déplacer un article dans sa section, crayon pour le modifier ou le supprimer. " +
                    "Tous les articles sont affichés.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SectionCard(
    section: Section,
    organize: Boolean,
    hideChecked: Boolean,
    onToggle: (Long) -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Long, Int) -> Unit,
    onEdit: (Long) -> Unit
) {
    val sp = section.progress
    // Reordering works on the real positions, so every item is shown while organizing.
    val visible = if (organize) section.items else section.visibleItems(hideChecked)
    val title = quoted(section.title)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                section.title,
                color = Accent,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .semantics { heading() }
            )
            Text(
                "${sp.done}/${sp.total}",
                color = if (sp.isComplete) Success else TextSecondary,
                fontSize = 14.sp,
                style = TabularDigits,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            if (organize) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Renommer la section $title", tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer la section $title", tint = TextSecondary)
                }
            } else {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter un article dans $title", tint = Accent)
                }
            }
        }
        when {
            section.items.isEmpty() -> SectionNote("Aucun article dans cette section.", TextSecondary)
            visible.isEmpty() -> SectionNote("Tout est coché dans cette section.", Success)
        }
        visible.forEachIndexed { index, item ->
            HorizontalDivider(color = Navy, thickness = 1.dp)
            if (organize) {
                OrganizeRow(
                    item = item,
                    canUp = index > 0,
                    canDown = index < visible.lastIndex,
                    onUp = { onMove(item.id, -1) },
                    onDown = { onMove(item.id, +1) },
                    onEdit = { onEdit(item.id) }
                )
            } else {
                CheckRow(item = item, onToggle = { onToggle(item.id) })
            }
        }
    }
}

@Composable
private fun SectionNote(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
    )
}

@Composable
private fun CheckRow(item: CheckItem, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = Accent,
                uncheckedColor = TextSecondary,
                checkmarkColor = Bg
            )
        )
        Spacer(Modifier.width(14.dp))
        Text(
            item.text,
            color = if (item.checked) TextSecondary else Color.White,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OrganizeRow(
    item: CheckItem,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit
) {
    val name = quoted(item.text)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.text,
            color = if (item.checked) TextSecondary else Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        )
        IconButton(onClick = onUp, enabled = canUp) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "Monter $name",
                tint = if (canUp) Color.White else TextSecondary.copy(alpha = 0.25f)
            )
        }
        IconButton(onClick = onDown, enabled = canDown) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Descendre $name",
                tint = if (canDown) Color.White else TextSecondary.copy(alpha = 0.25f)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Modifier ou supprimer $name", tint = Accent)
        }
    }
}

@Composable
private fun SourcesCard(template: Template) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .padding(16.dp)
    ) {
        Text("Sources du modèle", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        for (source in template.sources) {
            Text("• $source", color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Liste indicative : adaptez-la à votre foyer et suivez toujours les consignes des autorités.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun AddItemDialog(
    list: Checklist,
    initialSectionId: Long,
    onAdd: (sectionId: Long, sectionTitle: String, text: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var newTitle by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var sectionId by rememberSaveable {
        mutableStateOf(
            when {
                list.sections.any { it.id == initialSectionId } -> initialSectionId
                else -> list.sections.lastOrNull()?.id ?: NEW_SECTION
            }
        )
    }
    // The chosen section may have been deleted meanwhile (undo of another action…).
    val section = if (sectionId == NEW_SECTION) NEW_SECTION else list.findSection(sectionId)?.id ?: NEW_SECTION
    val clean = cleanText(text.text, Limits.ITEM_MAX)
    val duplicate = clean.isNotEmpty() && list.containsText(clean)
    val canAdd = clean.isNotEmpty() && !duplicate
    val confirm: () -> Unit = {
        if (canAdd) onAdd(section, cleanText(newTitle.text, Limits.SECTION_MAX), clean)
    }
    val focus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = confirm, enabled = canAdd) { Text("Ajouter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Ajouter un article") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = limit(it, Limits.ITEM_MAX) },
                    label = { Text("Article") },
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text("Cet article existe déjà dans la liste.") }
                    } else {
                        null
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
                // Inside the dialog's own composition, so the field is attached when focus is requested.
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Section",
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                for (s in list.sections) {
                    SectionChoice(s.title, selected = section == s.id, onClick = { sectionId = s.id })
                }
                SectionChoice("Nouvelle section", selected = section == NEW_SECTION, onClick = { sectionId = NEW_SECTION })
                if (section == NEW_SECTION) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = limit(it, Limits.SECTION_MAX) },
                        label = { Text("Nom de la section") },
                        placeholder = { Text(DEFAULT_SECTION) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

@Composable
private fun SectionChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RowShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = TextSecondary)
        )
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
    }
}

private fun shareList(context: Context, list: Checklist) {
    val text = list.toShareText(Templates.byKey(list.templateKey)?.sources.orEmpty())
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, list.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Partager la liste"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application ne permet de partager ce texte.", Toast.LENGTH_LONG).show()
    }
}
