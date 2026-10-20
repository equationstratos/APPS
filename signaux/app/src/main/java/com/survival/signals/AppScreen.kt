package com.survival.signals

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(16.dp)
private val BoxShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(24.dp)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")
private val BodyText = Color.White.copy(alpha = 0.92f)

private const val CADENCE_TICK_MS = 100L
private const val NOT_STARTED = -1L

/** What a screen can ask for: open a card, dial, copy, show in large type. */
private class Actions(
    val open: (Entry) -> Unit,
    val dial: (Entry) -> Unit,
    val copy: (String) -> Unit,
    val showBig: (List<String>) -> Unit,
)

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var bigText by rememberSaveable { mutableStateOf<String?>(null) }

    val section = Section.entries[sectionIndex.coerceIn(0, Section.entries.lastIndex)]
    val sectionState = rememberSaveable(sectionIndex, saver = LazyListState.Saver) { LazyListState() }
    val searchState = rememberSaveable(query, saver = LazyListState.Saver) { LazyListState() }
    val openEntry = openId?.let { Catalog.byId(it) }

    val actions = remember(context, clipboard, focusManager) {
        Actions(
            open = { entry ->
                focusManager.clearFocus()
                openId = entry.id
            },
            dial = { entry -> openDialer(context, entry) },
            copy = { text -> copyText(context, clipboard, text) },
            showBig = { lines ->
                focusManager.clearFocus()
                bigText = lines.joinToString("\n")
            },
        )
    }

    BackHandler(enabled = bigText != null || openEntry != null || query.isNotEmpty()) {
        when {
            bigText != null -> bigText = null
            openEntry != null -> openId = null
            else -> query = ""
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
    ) {
        if (openEntry != null) {
            key(openEntry.id) {
                DetailScreen(openEntry, actions, onBack = { openId = null })
            }
        } else {
            HomeScreen(
                section = section,
                query = query,
                onQueryChange = { query = it.take(SEARCH_MAX_LENGTH) },
                onSelectSection = { index ->
                    sectionIndex = index
                    query = ""
                    focusManager.clearFocus()
                },
                sectionState = sectionState,
                searchState = searchState,
                actions = actions,
            )
        }
        bigText?.let { text ->
            BigDisplay(text.split('\n'), onClose = { bigText = null }, onCopy = actions.copy)
        }
    }
}

/* ---------- Home: header, search, tabs, list ---------- */

@Composable
private fun HomeScreen(
    section: Section,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectSection: (Int) -> Unit,
    sectionState: LazyListState,
    searchState: LazyListState,
    actions: Actions,
) {
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text("Signaux", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Aide-mémoire des signaux de secours", color = TextSecondary, fontSize = 14.sp)
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("112, SOS, sirène, canal 16…", maxLines = 1) },
                leadingIcon = { SearchIcon(TextSecondary) },
                trailingIcon = if (query.isNotEmpty()) {
                    { ClearButton("Effacer la recherche") { onQueryChange("") } }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = CardShape,
                colors = fieldColors(),
            )
        }
        SectionTabs(selected = if (query.isBlank()) section.ordinal else -1, onSelect = onSelectSection)
        if (query.isBlank()) {
            SectionList(section, sectionState, actions)
        } else {
            SearchResults(query, searchState, actions)
        }
    }
}

@Composable
private fun SectionTabs(selected: Int, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected >= 0) state.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(Section.entries) { index, item ->
            Pill(item.tab, selected = index == selected, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun SectionList(section: Section, state: LazyListState, actions: Actions) {
    val groups = remember(section) { groupsOf(Catalog.inSection(section)) }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    frenchTypography(section.heading),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(frenchTypography(section.intro), color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        if (section == Section.ALPHABET) {
            item(key = "spell") { SpellCard(actions) }
        }
        groups.forEachIndexed { groupIndex, group ->
            item(key = "group-$groupIndex") { GroupTitle(group.title) }
            if (section == Section.ALPHABET) {
                items(group.entries.chunked(2), key = { pair -> "pair-" + pair.first().id }) { pair ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { entry ->
                            CodeWordCard(entry, actions, Modifier.weight(1f).fillMaxHeight())
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            } else {
                items(group.entries, key = { it.id }) { entry -> EntryCard(entry, showSection = false, actions) }
            }
        }
        item(key = "footer") {
            Text(
                frenchTypography(
                    "Chaque fiche indique sa source officielle. Les usages courants non officiels sont signalés comme tels."
                ),
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun SearchResults(query: String, state: LazyListState, actions: Actions) {
    val results = remember(query) { Catalog.index.search(query) }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (results.isEmpty()) {
            item(key = "empty") {
                SurvieCard {
                    Text(
                        frenchTypography("Aucun résultat pour « ${query.trim()} »"),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        frenchTypography(
                            "Essayez un numéro (112), un mot (sirène, fusée, balise), une lettre (Y) ou une fréquence (121,5)."
                        ),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            item(key = "count") {
                Text(
                    if (results.size == 1) "1 résultat" else "${results.size} résultats",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics { heading() }
                )
            }
            items(results, key = { it.id }) { entry -> EntryCard(entry, showSection = true, actions) }
        }
    }
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        title,
        color = Accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() }
    )
}

/* ---------- Cards ---------- */

/** What touching a card does, with the label read by accessibility services. */
private class Primary(val label: String, val opensDetail: Boolean, val onClick: () -> Unit)

/** Numbers open the dialer, code words the large display, every other card its full page. */
private fun primaryAction(entry: Entry, actions: Actions): Primary {
    val big = entry.bigLines
    return when {
        entry.dial != null -> Primary(
            if (entry.sms) "Écrire au ${entry.shownNumber} par SMS" else "Appeler le ${entry.shownNumber}",
            opensDetail = false
        ) { actions.dial(entry) }
        !entry.hasDetail && big != null -> Primary("Afficher en grand", opensDetail = false) { actions.showBig(big) }
        else -> Primary("Ouvrir la fiche", opensDetail = true) { actions.open(entry) }
    }
}

@Composable
private fun EntryCard(entry: Entry, showSection: Boolean, actions: Actions) {
    val primary = primaryAction(entry, actions)
    val number = entry.shownNumber
    val sketch = remember(entry.id) { entry.figure?.let { sketchOf(it) } }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .clickable(onClickLabel = primary.label, role = Role.Button, onClick = primary.onClick)
            .heightIn(min = 64.dp)
            .padding(start = 14.dp, end = if (entry.dial != null) 4.dp else 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (number == null) {
            when {
                sketch != null -> SketchCanvas(sketch, Modifier.size(52.dp))
                entry.glyph != null -> GlyphBox(entry.glyph)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showSection) {
                Text(
                    "${entry.section.tab} · ${entry.group}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            if (number != null) {
                Text(
                    number,
                    color = Accent,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp,
                    style = Tabular
                )
            }
            Text(
                entry.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp
            )
            Text(entry.summary, color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp)
        }
        if (entry.dial != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Ouvrir la fiche", role = Role.Button) { actions.open(entry) }
                    .semantics { contentDescription = "Détails : ${entry.title}" },
                contentAlignment = Alignment.Center
            ) {
                InfoIcon(Accent)
            }
        } else if (primary.opensDetail) {
            ChevronIcon(TextSecondary)
        }
    }
}

/** Compact card of the alphabet grid: touching it shows the letter and its word in large type. */
@Composable
private fun CodeWordCard(entry: Entry, actions: Actions, modifier: Modifier) {
    val primary = primaryAction(entry, actions)
    Row(
        modifier
            .clip(CardShape)
            .background(Card)
            .clickable(onClickLabel = primary.label, role = Role.Button, onClick = primary.onClick)
            .heightIn(min = 64.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        entry.glyph?.let { GlyphBox(it, size = 44.dp) }
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
            Text(entry.summary, color = TextSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun GlyphBox(text: String, size: Dp = 52.dp) {
    val fontSize = when {
        text.length <= 2 -> 24.sp
        text.length <= 3 -> 19.sp
        text.length <= 5 -> 15.sp
        else -> 12.sp
    }
    Box(
        Modifier
            .size(size)
            .clip(BoxShape)
            .background(Navy),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Accent,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = Tabular
        )
    }
}

/* ---------- Detail page ---------- */

@Composable
private fun DetailScreen(entry: Entry, actions: Actions, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Retour" }) {
                BackArrowIcon(Color.White)
            }
            Text(
                frenchTypography(entry.section.heading),
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Headline(entry)
            EntryActions(entry, actions)
            entry.figure?.let { FigureCard(it) }
            if (entry.details.isNotEmpty()) DetailsCard(entry)
            when (entry.tool) {
                Tool.ALPINE_CADENCE -> AlpineCadenceCard()
                Tool.MAYDAY_FORM -> MaydayFormCard(actions)
                null -> Unit
            }
            entry.unofficial?.let { UnofficialNote(it) }
            Text(
                frenchTypography("Source : ${entry.source}"),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun Headline(entry: Entry) {
    SurvieCard {
        val number = entry.shownNumber
        when {
            number != null -> Text(
                number,
                color = Accent,
                fontSize = if (number.length > 8) 32.sp else 44.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular
            )
            entry.glyph != null -> Text(
                entry.glyph,
                color = Accent,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular
            )
        }
        Text(
            entry.title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            modifier = Modifier.semantics { heading() }
        )
        Text(entry.summary, color = BodyText, fontSize = 17.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun EntryActions(entry: Entry, actions: Actions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entry.dial != null) {
            PrimaryButton(
                if (entry.sms) "Écrire au ${entry.shownNumber} (SMS)" else "Appeler le ${entry.shownNumber}",
                Modifier.fillMaxWidth()
            ) { actions.dial(entry) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val big = entry.bigLines
            if (big != null) {
                SecondaryButton("Afficher en grand", Modifier.weight(1f)) { actions.showBig(big) }
            }
            SecondaryButton("Copier", Modifier.weight(1f)) { actions.copy(entry.asPlainText()) }
        }
    }
}

@Composable
private fun FigureCard(figure: Figure) {
    val sketch = remember(figure) { sketchOf(figure) }
    val timeline = remember(figure) { timelineOf(figure) }
    SurvieCard {
        if (sketch != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SketchCanvas(
                    sketch,
                    Modifier
                        .size(200.dp)
                        .semantics { contentDescription = figure.description }
                )
            }
        }
        if (timeline != null) {
            TimelineCanvas(
                timeline,
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = figure.description }
            )
            TimelineMarks(timeline)
            Text(timeline.caption, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun DetailsCard(entry: Entry) {
    SurvieCard {
        Text(
            if (entry.steps) "Dans l'ordre" else "À savoir",
            color = Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        entry.details.forEachIndexed { index, line ->
            Row {
                Text(
                    if (entry.steps) "${index + 1}." else "•",
                    color = Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp),
                    style = Tabular
                )
                Text(line, color = BodyText, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UnofficialNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(BoxShape)
            .background(Warning.copy(alpha = 0.10f))
            .border(1.dp, Warning.copy(alpha = 0.55f), BoxShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("!", color = Warning, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
    }
}

/* ---------- Spelling tool ---------- */

@Composable
private fun SpellCard(actions: Actions) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val rows = remember(text) { spellRows(text) }
    SurvieCard {
        Text("Épeler", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Tapez un nom, une immatriculation ou un indicatif pour obtenir son épellation.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(SPELL_MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Texte à épeler") },
            trailingIcon = if (text.isNotEmpty()) {
                { ClearButton("Effacer le texte") { text = "" } }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            shape = BoxShape,
            colors = fieldColors(),
        )
        if (rows.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(BoxShape)
                    .background(Navy)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { row ->
                    Column {
                        Text(row.typed, color = TextSecondary, fontSize = 13.sp)
                        Text(
                            row.spoken,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Copier", Modifier.weight(1f)) { actions.copy(spellingForClipboard(rows)) }
                SecondaryButton("Afficher en grand", Modifier.weight(1f)) { actions.showBig(rows.map { it.spoken }) }
            }
        }
    }
}

/* ---------- Alpine distress signal guide ---------- */

@Composable
private fun AlpineCadenceCard() {
    var modeIndex by rememberSaveable { mutableIntStateOf(0) }
    var startedAt by rememberSaveable { mutableLongStateOf(NOT_STARTED) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val mode = CadenceMode.entries[modeIndex.coerceIn(0, CadenceMode.entries.lastIndex)]
    val running = startedAt != NOT_STARTED

    if (running) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(startedAt, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now = SystemClock.elapsedRealtime()
                    delay(CADENCE_TICK_MS)
                }
            }
        }
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    val step = if (running) cadenceAt(now - startedAt, mode) else null
    val flashing = step?.flashing == true

    fun start() {
        val t = SystemClock.elapsedRealtime()
        now = t
        startedAt = t
    }

    SurvieCard {
        Text("Guide de cadence", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Donnez un signal (coup de sifflet, éclat de lampe, cri) chaque fois que le cercle s'allume. " +
                "Le guide est silencieux.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CadenceMode.entries.forEachIndexed { index, item ->
                Pill(
                    "${item.label}\n${item.signalsPerMinute} par minute",
                    selected = index == modeIndex,
                    onClick = {
                        if (index != modeIndex) {
                            modeIndex = index
                            if (running) start()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    idle = Navy,
                    role = Role.RadioButton,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val lamp = when {
                flashing -> Accent
                step?.inPause == true -> Card
                else -> Navy
            }
            Box(
                Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(lamp)
                    .border(3.dp, if (flashing) Color.White else Accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    step == null -> Text("Prêt", color = TextSecondary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    step.inPause -> Text("Pause", color = TextSecondary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    flashing -> Text(
                        "${step.signal}",
                        color = Bg,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        style = Tabular
                    )
                    else -> Text(
                        "${step.signal}/${step.total}",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        style = Tabular
                    )
                }
            }
        }
        Text(
            if (step == null) {
                frenchTypography("Le premier signal est à donner dès le départ.")
            } else {
                cadenceStatus(step)
            },
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.fillMaxWidth(),
            style = Tabular
        )
        if (step != null) {
            Text(
                "Cycle ${step.cycle + 1}",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = Tabular
            )
        }
        PrimaryButton(if (running) "Arrêter" else "Démarrer", Modifier.fillMaxWidth()) {
            if (running) startedAt = NOT_STARTED else start()
        }
    }
}

/* ---------- MAYDAY message builder ---------- */

@Composable
private fun MaydayFormCard(actions: Actions) {
    var vessel by rememberSaveable { mutableStateOf("") }
    var callSign by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var nature by rememberSaveable { mutableStateOf("") }
    var assistance by rememberSaveable { mutableStateOf("") }
    var persons by rememberSaveable { mutableStateOf("") }
    var extra by rememberSaveable { mutableStateOf("") }
    val lines = maydayLines(MaydayInput(vessel, callSign, position, nature, assistance, persons, extra))

    SurvieCard {
        Text("Préparer son message", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            frenchTypography(
                "Remplissez ce que vous savez : le message se compose dans l'ordre. " +
                    "Parlez lentement et distinctement, sur le canal 16."
            ),
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        FormField("Nom du navire", vessel, { vessel = it }, capitalization = KeyboardCapitalization.Characters)
        FormField(
            "Indicatif d'appel (facultatif)",
            callSign,
            { callSign = it },
            capitalization = KeyboardCapitalization.Characters
        )
        FormField("Position", position, { position = it }, placeholder = "Coordonnées, ou distance d'un point connu")
        FormField("Nature de la détresse", nature, { nature = it }, placeholder = "Incendie, voie d'eau, homme à la mer…")
        FormField("Assistance demandée", assistance, { assistance = it })
        FormField(
            "Personnes à bord",
            persons,
            { value -> persons = value.filter { it.isDigit() }.take(3) },
            keyboardType = KeyboardType.Number
        )
        FormField("Intentions, autres informations", extra, { extra = it }, last = true)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(BoxShape)
                .background(Navy)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lines.forEach { line ->
                Text(line, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Copier", Modifier.weight(1f)) { actions.copy(lines.joinToString("\n")) }
            SecondaryButton("Afficher en grand", Modifier.weight(1f)) { actions.showBig(lines) }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
    last: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(FIELD_MAX_LENGTH)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = if (last) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = if (last) KeyboardActions(onDone = { focusManager.clearFocus() }) else KeyboardActions.Default,
        shape = BoxShape,
        colors = fieldColors(),
    )
}

private const val FIELD_MAX_LENGTH = 80

/* ---------- Large display ---------- */

@Composable
private fun BigDisplay(lines: List<String>, onClose: () -> Unit, onCopy: (String) -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .clickable(onClickLabel = "Fermer l'affichage en grand", role = Role.Button, onClick = onClose)
            .padding(16.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val fontScale = LocalDensity.current.fontScale
            val width = maxWidth.value
            val height = maxHeight.value
            val headline = lines.size > 1 && lines.first().length <= HEADLINE_MAX_CHARS
            val firstSize = if (headline) {
                bigFontSizeSp(width, height * 0.55f, lines.take(1), fontScale)
            } else {
                bigFontSizeSp(width, height, lines, fontScale)
            }
            val restSize = if (headline) {
                minOf(bigFontSizeSp(width, height * 0.4f, lines.drop(1), fontScale), firstSize * 0.5f)
                    .coerceAtLeast(BIG_MIN_SP)
            } else {
                firstSize
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lines.forEachIndexed { index, line ->
                    val size = if (index == 0) firstSize else restSize
                    Text(
                        line,
                        color = if (headline && index == 0) Accent else Color.White,
                        fontSize = size.sp,
                        lineHeight = (size * 1.15f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        style = Tabular
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Toucher l'écran pour fermer",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton("Copier") { onCopy(lines.joinToString("\n")) }
        }
    }
}

private const val HEADLINE_MAX_CHARS = 8

/* ---------- Small building blocks ---------- */

@Composable
private fun SurvieCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idle: Color = Card,
    role: Role = Role.Tab,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .background(if (selected) Accent else idle)
            .selectable(selected = selected, role = role, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Bg else Color.White,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = BoxShape,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = BoxShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
        border = BorderStroke(1.dp, Accent),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ClearButton(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        ClearIcon(TextSecondary)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
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
)

/* ---------- System actions ---------- */

/** Opens the dialer (or the SMS app for the 114) with the number filled in; nothing is sent without the user. */
private fun openDialer(context: Context, entry: Entry) {
    val number = entry.dial ?: return
    if (!isDialable(number)) return
    val intent = if (entry.sms) {
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
    } else {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    }
    val shown = entry.shownNumber ?: number
    val failure = if (entry.sms) {
        "Aucune application de SMS sur cet appareil. Envoyez un SMS au $shown depuis un téléphone."
    } else {
        "Aucune application de téléphone sur cet appareil. Composez le $shown depuis un téléphone."
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, frenchTypography(failure), Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, frenchTypography(failure), Toast.LENGTH_LONG).show()
    }
}

/** Copies [text]; Android 13 and later confirm the copy themselves, so the toast is only shown before. */
private fun copyText(context: Context, clipboard: ClipboardManager, text: String) {
    clipboard.setText(AnnotatedString(text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
    }
}
