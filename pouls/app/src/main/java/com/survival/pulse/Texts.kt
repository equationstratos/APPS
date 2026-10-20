package com.survival.pulse

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Non-breaking space: keeps a number with its unit and French quotes with their word. */
internal const val NBSP = ' '

/** Usual resting range for an adult (Mayo Clinic, MedlinePlus, CHUV): 60 to 100 beats per minute. */
internal const val REST_MIN_BPM = 60
internal const val REST_MAX_BPM = 100

internal const val FINGER_INSTRUCTION =
    "Posez le bout de l'index sur l'objectif arrière et le flash, sans appuyer fort, ne bougez plus."

internal const val DISCLAIMER =
    "Valeur indicative : ce n'est pas un appareil médical. En cas de malaise, appelez le 15 ou le 112."

internal const val UNRELIABLE_MESSAGE = "Mesure peu fiable, recommencez."

internal const val INTERRUPTED_MESSAGE =
    "Mesure interrompue : l'application est passée en arrière-plan. Relancez la mesure."

/** Tone of an on-screen element, mapped to the palette by the UI. */
internal enum class Tone { NEUTRAL, ACCENT, SUCCESS, WARNING }

internal enum class SignalQuality(val label: String, val tone: Tone) {
    GOOD("Bonne", Tone.SUCCESS),
    FAIR("Correcte", Tone.ACCENT),
    POOR("Faible", Tone.WARNING),
}

internal fun signalQuality(score: Double): SignalQuality = when {
    score >= 0.8 -> SignalQuality.GOOD
    score >= 0.6 -> SignalQuality.FAIR
    else -> SignalQuality.POOR
}

/** Main status line under the camera readout. */
internal fun cameraMessage(snapshot: SessionSnapshot): String = when (snapshot.phase) {
    Phase.WAITING -> when {
        snapshot.finger == FingerStatus.TOO_DARK ->
            "Image trop sombre : appuyez moins fort, ou éclairez le doigt si le flash ne s'allume pas."
        snapshot.interrupted -> "Doigt retiré : mesure remise à zéro. Reposez le doigt pour recommencer."
        else -> FINGER_INSTRUCTION
    }
    Phase.STABILIZING -> "Doigt détecté. Ne bougez plus…"
    Phase.MEASURING -> when {
        snapshot.liveBpm != null -> "Mesure en cours, restez immobile…"
        snapshot.quality != null -> "Signal faible : appuyez moins fort et ne bougez pas."
        else -> "Analyse du pouls, restez immobile…"
    }
    Phase.DONE -> "Mesure terminée."
    Phase.FAILED -> "$UNRELIABLE_MESSAGE Appuyez légèrement, ne bougez pas, ou utilisez le mode manuel."
}

/** "12 s / 30 s" during the measurement. */
internal fun formatElapsed(elapsedS: Double, totalS: Double): String =
    "${elapsedS.toInt().coerceIn(0, totalS.toInt())}${NBSP}s / ${totalS.toInt()}${NBSP}s"

/** Remaining whole seconds, rounded up so that 0 only shows at the end. */
internal fun remainingSeconds(remainingMs: Long): Int = ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toInt()

/** Short comparison with the usual resting range, adapted to the context. */
internal fun assessment(bpm: Int, context: MeasureContext): String = when {
    context == MeasureContext.EFFORT ->
        "Après un effort, le pouls est plus rapide qu'au repos ; il redescend progressivement."
    bpm < REST_MIN_BPM ->
        "Plus lent que la plage habituelle au repos ($REST_MIN_BPM à $REST_MAX_BPM). " +
            "Cela peut être normal chez une personne sportive."
    bpm <= REST_MAX_BPM -> "Dans la plage habituelle au repos ($REST_MIN_BPM à $REST_MAX_BPM)."
    else ->
        "Plus rapide que la plage habituelle au repos ($REST_MIN_BPM à $REST_MAX_BPM). " +
            "Reposez-vous quelques minutes et refaites la mesure."
}

/** Rounded BPM for display. */
internal fun bpmText(bpm: Double): String = bpm.roundToInt().toString()

/** "72 BPM", with a non-breaking space. */
internal fun bpmLabel(bpm: Int): String = "$bpm${NBSP}BPM"

/** Text inside French quotes, with the non-breaking spaces they require: « Démarrer ». */
internal fun quoted(text: String): String = "«$NBSP$text$NBSP»"
