package com.survival.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId

@Composable
internal fun HistoryTab(store: HistoryStore) {
    val entries = store.entries
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    if (entries.isEmpty()) {
        SurvieCard {
            CardTitle("Aucune mesure enregistrée")
            BodyText(
                "Les résultats des mesures, à la caméra ou manuelles, s'affichent ici automatiquement. " +
                    "Les $HISTORY_SIZE dernières sont conservées sur le téléphone."
            )
        }
    } else {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        Text(
            text = "${historyCountText(entries.size)} ($HISTORY_SIZE au maximum)",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        for (entry in entries) {
            HistoryRow(
                entry = entry,
                date = formatEntryDate(entry.timeMs, now, zone),
                deleteLabel = "Supprimer la mesure de ${entry.bpm} battements par minute, " +
                    "${spokenDay(entry.timeMs, now, zone)} à ${formatEntryTime(entry.timeMs, zone)}",
                onDelete = { store.delete(entry.timeMs) },
            )
        }
        SurvieButton(
            text = "Tout effacer",
            onClick = { confirmClear = true },
            modifier = Modifier.fillMaxWidth(),
            primary = false,
        )
    }

    ReferenceCard()

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    store.clear()
                }) { Text("Effacer", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Annuler", color = Accent) }
            },
            title = { Text("Effacer l'historique ?") },
            text = { Text(clearHistoryText(entries.size)) },
            containerColor = Card,
            titleContentColor = Color.White,
            textContentColor = TextSecondary,
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, date: String, deleteLabel: String, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Card, CardShape)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.padding(end = 14.dp)) {
            Text(
                text = entry.bpm.toString(),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                style = Tabular,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = "BPM",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = 4.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(date, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp, style = Tabular)
            Text(entryDetails(entry), color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = deleteLabel },
        ) {
            TrashGlyph(color = TextSecondary)
        }
    }
}
