package com.survival.stocks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Full-screen form to add ([initial] null) or edit an article. Every field survives
 * recreation; leaving with unsaved changes asks for confirmation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemEditor(
    initial: Item?,
    defaultCategory: Category,
    today: LocalDate,
    soonDays: Int,
    onSave: (Item) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val start = remember { initial?.let(::draftOf) ?: newDraft(defaultCategory) }
    var name by rememberSaveable { mutableStateOf(start.name) }
    var categoryKey by rememberSaveable { mutableStateOf(start.category.key) }
    var quantity by rememberSaveable { mutableStateOf(start.quantity) }
    var unit by rememberSaveable { mutableStateOf(start.unit) }
    var expiryIso by rememberSaveable { mutableStateOf(start.expiry?.iso().orEmpty()) }
    var monthOnly by rememberSaveable { mutableStateOf(start.expiry?.monthOnly ?: false) }
    var location by rememberSaveable { mutableStateOf(start.location) }
    var note by rememberSaveable { mutableStateOf(start.note) }
    var liters by rememberSaveable { mutableStateOf(start.litersPerUnit) }
    var kcal by rememberSaveable { mutableStateOf(start.kcal) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val category = Category.fromKey(categoryKey)
    val expiry = Expiry.parseIso(expiryIso)
    val draft = Draft(name, category, quantity, unit, expiry, location, note, liters, kcal)
    val check = checkDraft(draft, initial?.id ?: 0L)
    val dirty = draft != start
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    val leave: () -> Unit = {
        if (dirty) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(onBack = leave)

    fun setExpiry(date: LocalDate) {
        expiryIso = Expiry.of(date, monthOnly).iso()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = leave) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour à la liste", tint = Color.White)
            }
            Text(
                if (initial == null) "Nouvel article" else "Modifier l'article",
                color = Accent,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
        }
        Column(
            Modifier.padding(start = 8.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val nameError = check.nameError?.takeIf { showErrors }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = { Text("Nom") },
                placeholder = { Text("Ex. riz, eau de source, piles AA") },
                isError = nameError != null,
                supportingText = if (nameError != null) {
                    { Text(nameError) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Catégorie")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in Category.entries) {
                    SurvieChip(c.label, c == category) {
                        if (c != category) {
                            unit = unitAfterCategoryChange(category, c, unit)
                            categoryKey = c.key
                        }
                    }
                }
            }
            Text(category.hint, color = TextSecondary, fontSize = 13.sp)

            SectionTitle("Quantité")
            val quantityError = check.quantityError?.takeIf { showErrors || quantity.isNotBlank() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.take(14) },
                    label = { Text("Quantité") },
                    isError = quantityError != null,
                    supportingText = if (quantityError != null) {
                        { Text(quantityError) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it.take(MAX_UNIT) },
                    label = { Text("Unité") },
                    placeholder = { Text(category.defaultUnit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (u in category.units) SurvieChip(u, unit.trim() == u) { unit = u }
            }

            if (category == Category.WATER) {
                if (needsLitersPerUnit(category, unit)) {
                    val litersError = check.litersError?.takeIf { liters.isNotBlank() }
                    OutlinedTextField(
                        value = liters,
                        onValueChange = { liters = it.take(10) },
                        label = { Text("Contenance par ${singularUnit(unit.ifBlank { category.defaultUnit })}") },
                        placeholder = { Text("Ex. 1,5") },
                        suffix = { Text("L") },
                        isError = litersError != null,
                        supportingText = {
                            Text(litersError ?: "Nécessaire pour compter cette eau dans l'autonomie.")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                check.item?.let(::waterLiters)?.let {
                    Text("Soit ${formatDecimal(it, 2)}${NBSP}L d'eau potable", color = Accent, fontSize = 15.sp)
                }
            }

            if (category == Category.FOOD) {
                val kcalError = check.kcalError?.takeIf { kcal.isNotBlank() }
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it.take(10) },
                    label = { Text("${kcalFieldLabel(unit)} (facultatif)") },
                    placeholder = { Text("Ex. 350") },
                    isError = kcalError != null,
                    supportingText = {
                        Text(kcalError ?: "Valeur du tableau nutritionnel, pour estimer l'autonomie.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                check.item?.let(::itemKcal)?.let {
                    Text("Soit ${formatDecimal(it, 0)}${NBSP}kcal au total", color = Accent, fontSize = 15.sp)
                }
            }

            SectionTitle("Date de péremption")
            Text(
                "Facultatif\u00A0: DLC «\u00A0à consommer jusqu'au\u00A0» ou DDM «\u00A0de préférence avant\u00A0».",
                color = TextSecondary,
                fontSize = 13.sp
            )
            if (expiry != null) {
                val status = expiryStatus(expiry, today, soonDays)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Navy)
                        .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (expiry.monthOnly) "Fin ${expiry.long()}" else expiry.long(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            relativeExpiry(expiry, today).replaceFirstChar { it.uppercase() },
                            color = if (status == Status.OK) TextSecondary else statusColor(status),
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = { expiryIso = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Retirer la date", tint = TextSecondary)
                    }
                }
            } else {
                Text("Aucune date", color = Color.White, fontSize = 15.sp)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { picking = true },
                    border = BorderStroke(1.dp, Accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (expiry == null) "Choisir…" else "Modifier…")
                }
                SurvieChip("+6 mois", false) { setExpiry(today.plusMonths(6)) }
                SurvieChip("+1 an", false) { setExpiry(today.plusYears(1)) }
                SurvieChip("+2 ans", false) { setExpiry(today.plusYears(2)) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .toggleable(value = monthOnly, role = Role.Checkbox) { checked ->
                        monthOnly = checked
                        expiry?.let { expiryIso = it.withMonthOnly(checked).iso() }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = monthOnly,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Accent,
                        uncheckedColor = TextSecondary,
                        checkmarkColor = Bg
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Mois et année seulement", color = Color.White, fontSize = 15.sp)
                    Text(
                        "Pour une date du type «\u00A0fin 03/2027\u00A0»\u00A0: valable jusqu'au dernier jour du mois.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            SectionTitle("Rangement")
            OutlinedTextField(
                value = location,
                onValueChange = { location = it.take(MAX_LOCATION) },
                label = { Text("Emplacement (facultatif)") },
                placeholder = { Text("Ex. cave, placard de la cuisine") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(MAX_NOTE) },
                label = { Text("Note (facultatif)") },
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.size(4.dp))
            Button(
                onClick = {
                    val item = check.item
                    if (item != null) {
                        onSave(item)
                    } else {
                        showErrors = true
                        scope.launch { scroll.animateScrollTo(0) }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    if (initial == null) "Ajouter à l'inventaire" else "Enregistrer",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Supprimer l'article")
                }
            }
        }
    }

    if (picking) {
        DateDialog(
            initial = expiry?.lastDay() ?: today,
            monthOnly = monthOnly,
            onDismiss = { picking = false },
            onPick = {
                picking = false
                setExpiry(it)
            }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Abandonner les modifications\u202F?") },
            text = { Text("Les changements apportés à cet article ne seront pas enregistrés.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text("Abandonner", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Continuer", color = Accent) }
            },
            containerColor = Card,
            titleContentColor = Color.White,
            textContentColor = TextSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, monthOnly: Boolean, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // The Material date picker works on UTC midnights, and throws if the initial date lies outside
    // its year range: widen the range for a far date read from an imported file.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = minOf(PICKER_FIRST_YEAR, initial.year)..maxOf(PICKER_LAST_YEAR, initial.year),
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
        DatePicker(
            state = state,
            title = {
                Text(
                    if (monthOnly) "Mois de péremption (le jour est ignoré)" else "Date de péremption",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            }
        )
    }
}

private const val PICKER_FIRST_YEAR = 2000
private const val PICKER_LAST_YEAR = 2100
