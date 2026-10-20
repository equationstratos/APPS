package com.survival.morse

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Tab(val label: String) {
    EMIT("Émettre"),
    DECODE("Décoder"),
    TABLE("Table"),
    LEARN("Apprendre")
}

/** Full-screen white flash used as a light output; shown above everything while active. */
@Stable
internal class ScreenFlash {
    var active by mutableStateOf(false)
    var lit by mutableStateOf(false)
    var onStop: () -> Unit = {}
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { MorsePrefs(context.applicationContext) }
    val flash = remember { ScreenFlash() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Each tab keeps its saved state (decoded text, message, scroll) while another tab is shown.
    val tabStates = rememberSaveableStateHolder()
    // The Koch session (level, score, recent answers) also survives a visit to another tab.
    val trainer = remember(prefs) { KochTrainer(prefs.kochLevel) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                Text("Morse", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Émettre, décoder et apprendre le code", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                SegmentedChoice(
                    options = Tab.entries.map { it.label },
                    selected = tab,
                    onSelect = { tab = it },
                    role = Role.Tab,
                    fontSize = 13
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                tabStates.SaveableStateProvider(tab) {
                    when (Tab.entries[tab]) {
                        Tab.EMIT -> EmitTab(prefs, flash)
                        Tab.DECODE -> DecodeTab(prefs)
                        Tab.TABLE -> TableTab(prefs)
                        Tab.LEARN -> LearnTab(prefs, trainer)
                    }
                }
            }
        }
        if (flash.active) FlashOverlay(flash)
    }
}

@Composable
private fun FlashOverlay(flash: ScreenFlash) {
    val context = LocalContext.current
    // Full brightness while the screen is used as a lamp, restored afterwards.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val previous = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
        onDispose {
            window?.let { it.attributes = it.attributes.apply { screenBrightness = previous } }
        }
    }
    BackHandler { flash.onStop() }
    Box(
        Modifier
            .fillMaxSize()
            .background(if (flash.lit) Color.White else Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Arrêter l'émission"
            ) { flash.onStop() }
            .semantics { contentDescription = "Écran clignotant en Morse. Touchez pour arrêter." }
    ) {
        Text(
            "Touchez l'écran pour arrêter",
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(24.dp)
        )
    }
}
