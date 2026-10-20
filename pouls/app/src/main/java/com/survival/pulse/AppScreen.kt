package com.survival.pulse

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val TABS = listOf("Caméra", "Manuel", "Historique")
private const val TAB_CAMERA = 0
private const val TAB_MANUAL = 1

@Composable
internal fun AppScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { HistoryStore(context.getSharedPreferences(HistoryStore.PREFS_NAME, Context.MODE_PRIVATE)) }
    val camera = remember { CameraUiState() }
    val manual = remember { ManualUiState() }
    var tab by rememberSaveable { mutableIntStateOf(TAB_CAMERA) }

    // Camera, torch and manual timer only run in the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                camera.interrupt()
                manual.interrupt()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()
        // Locked during a measurement, so that a stray touch while holding the phone does not cancel it.
        SegmentedControl(
            options = TABS,
            selected = tab,
            onSelect = { tab = it },
            enabled = !camera.running && !manual.counting,
            role = Role.Tab,
        )
        when (tab) {
            TAB_CAMERA -> CameraTab(camera, store, onManual = { tab = TAB_MANUAL })
            TAB_MANUAL -> ManualTab(manual, store)
            else -> HistoryTab(store)
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text("Pouls", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Fréquence cardiaque au bout du doigt", color = TextSecondary, fontSize = 14.sp)
    }
}

/** Optional context of the last measurement; changing it updates the saved entry. */
@Composable
internal fun ContextCard(selected: MeasureContext, onSelect: (MeasureContext) -> Unit) {
    SurvieCard {
        CardTitle("Contexte de la mesure")
        BodyText("Facultatif. La mesure est enregistrée dans l'historique avec ce contexte.")
        SegmentedControl(
            options = MeasureContext.entries.map { it.label },
            selected = selected.ordinal,
            onSelect = { onSelect(MeasureContext.entries[it]) },
            fontSize = 13,
        )
    }
}

/** Usual resting range and the medical warning. */
@Composable
internal fun ReferenceCard() {
    SurvieCard {
        CardTitle("Repère")
        BodyText(
            "Au repos, le cœur d'un adulte bat habituellement entre $REST_MIN_BPM et $REST_MAX_BPM fois " +
                "par minute. Il est plus rapide après un effort, avec la fièvre, le stress ou la caféine, " +
                "et peut être plus lent chez les personnes sportives."
        )
        Row(verticalAlignment = Alignment.Top) {
            WarningGlyph(Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(10.dp))
            Text(DISCLAIMER, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}
