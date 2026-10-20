package com.survival.pulse

import kotlin.math.roundToInt

/** Placeholder for a value not available yet. */
internal const val NO_VALUE = "—"

/** Indicator line: short text and its tone. */
internal class Indicator(val text: String, val tone: Tone)

/** What the camera readout shows: big value inside the progress ring, then status lines. */
internal class Readout(
    val value: String,
    val progress: Float,
    val tone: Tone,
    val status: String,
    val detail: String?,
    val note: String?,
    /** Spoken description of the ring and its value. */
    val description: String,
)

internal fun cameraReadout(
    running: Boolean,
    snapshot: SessionSnapshot?,
    resultBpm: Int?,
    resultQuality: Double?,
    failed: Boolean,
    context: MeasureContext,
): Readout = when {
    running && snapshot == null -> Readout(
        value = NO_VALUE,
        progress = 0f,
        tone = Tone.ACCENT,
        status = "Démarrage de la caméra…",
        detail = null,
        note = null,
        description = "Démarrage de la mesure",
    )
    running && snapshot != null -> {
        val measuring = snapshot.phase == Phase.MEASURING
        val live = if (measuring) snapshot.liveBpm else null
        Readout(
            value = live?.toString() ?: NO_VALUE,
            progress = snapshot.progress,
            tone = Tone.ACCENT,
            status = cameraMessage(snapshot),
            detail = if (measuring) formatElapsed(snapshot.elapsedS, MEASURE_DURATION_S) else null,
            note = null,
            description = if (live != null) {
                "Estimation en cours : $live battements par minute"
            } else {
                "Mesure en cours, ${(snapshot.progress * 100).roundToInt()}${NBSP}%"
            },
        )
    }
    resultBpm != null -> Readout(
        value = resultBpm.toString(),
        progress = 1f,
        tone = Tone.SUCCESS,
        status = "Mesure terminée.",
        detail = resultQuality?.let { "Qualité du signal : ${signalQuality(it).label}" },
        note = assessment(resultBpm, context),
        description = "Résultat : $resultBpm battements par minute",
    )
    failed -> Readout(
        value = NO_VALUE,
        progress = 1f,
        tone = Tone.WARNING,
        status = "$UNRELIABLE_MESSAGE Appuyez légèrement, ne bougez pas, ou utilisez le mode manuel.",
        detail = null,
        note = null,
        description = "Mesure peu fiable",
    )
    else -> Readout(
        value = NO_VALUE,
        progress = 0f,
        tone = Tone.NEUTRAL,
        status = FINGER_INSTRUCTION,
        detail = "Appuyez sur ${quoted("Démarrer")} : la mesure dure ${MEASURE_DURATION_S.toInt()}${NBSP}secondes.",
        note = null,
        description = "Aucune mesure en cours",
    )
}

internal fun fingerIndicator(status: FingerStatus): Indicator = when (status) {
    FingerStatus.PRESENT -> Indicator("Doigt détecté", Tone.SUCCESS)
    FingerStatus.ABSENT -> Indicator("Doigt non détecté", Tone.WARNING)
    FingerStatus.TOO_DARK -> Indicator("Image trop sombre", Tone.WARNING)
}

internal fun qualityIndicator(quality: Double?): Indicator =
    if (quality == null) {
        Indicator("Qualité du signal : en cours d'évaluation", Tone.NEUTRAL)
    } else {
        val level = signalQuality(quality)
        Indicator("Qualité du signal : ${level.label}", level.tone)
    }

/** "0 battement", "1 battement", "12 battements" (French singular for 0 and 1). */
internal fun beatsText(count: Int): String = if (count > 1) "$count${NBSP}battements" else "$count${NBSP}battement"

/** What the manual count shows inside and under the tap target. */
internal class ManualView(
    val value: String,
    val caption: String,
    val progress: Float,
    val tone: Tone,
    val status: String,
    val detail: String?,
    val finished: Boolean,
    val rate: TapRate?,
)

internal fun manualView(session: TapSession, nowMs: Long): ManualView {
    val durationS = (session.durationMs / 1000L).toInt()
    if (!session.started) {
        return ManualView(
            value = NO_VALUE,
            caption = "Touchez à chaque battement",
            progress = 0f,
            tone = Tone.NEUTRAL,
            status = "Le minuteur de $durationS${NBSP}s démarre au premier battement.",
            detail = null,
            finished = false,
            rate = null,
        )
    }
    if (!session.isFinished(nowMs)) {
        val live = session.liveRate()
        return ManualView(
            value = session.beatCount.toString(),
            caption = if (session.beatCount > 1) "battements" else "battement",
            progress = session.progress(nowMs),
            tone = Tone.ACCENT,
            status = "Temps restant : ${remainingSeconds(session.remainingMs(nowMs))}${NBSP}s",
            detail = live?.let { "Estimation : ${bpmLabel(it.bpm.roundToInt())}" } ?: "Continuez à toucher à chaque battement…",
            finished = false,
            rate = null,
        )
    }
    val rate = session.finalRate()
    return if (rate == null) {
        ManualView(
            value = NO_VALUE,
            caption = "BPM",
            progress = 1f,
            tone = Tone.WARNING,
            status = "Pas assez de battements réguliers (${MIN_TAP_INTERVALS + 1} au minimum). Recommencez.",
            detail = null,
            finished = true,
            rate = null,
        )
    } else {
        ManualView(
            value = rate.bpm.roundToInt().toString(),
            caption = "BPM",
            progress = 1f,
            tone = Tone.SUCCESS,
            status = "${beatsText(session.beatCount)} en $durationS${NBSP}s.",
            detail = if (rate.irregular) {
                "Rythme irrégulier ou battements manqués : valeur approximative."
            } else {
                null
            },
            finished = true,
            rate = rate,
        )
    }
}
