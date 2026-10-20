package com.survival.morse

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

private const val SOUND_DRAIN_MS = 1500L
private const val POLL_MS = 20L

internal class TxPlan(
    val timeline: Timeline,
    val unitMs: Double,
    val repeat: Boolean,
    val lamp: Boolean,
    val sound: Boolean,
    val vibration: Boolean,
    val screen: Boolean,
    val toneHz: Int
)

/**
 * Sends a [TxPlan] on every selected output. One coroutine waits for absolute deadlines computed
 * from the start time (start + units × unit), so delays never add up. The sound follows the same
 * timeline on the audio clock (see [ToneOutput]).
 */
internal class Transmitter(context: Context) {
    private val torch = TorchController(context.getSystemService(CameraManager::class.java))
    private val haptics = Haptics(context)
    private val tone = ToneOutput()

    @Volatile
    private var torchLit = false

    val hasFlash: Boolean get() = torch.hasFlash
    val hasVibrator: Boolean get() = haptics.available

    /**
     * Runs until the message ends (or forever when repeating) and turns everything off when done
     * or cancelled. [onTorchError] is called once if the flash cannot be driven.
     */
    suspend fun run(
        plan: TxPlan,
        onToken: (Int) -> Unit,
        onLight: (Boolean) -> Unit,
        onTorchError: () -> Unit
    ) {
        withContext(Dispatchers.Default) {
            try {
                transmit(plan, onToken, onLight, onTorchError)
            } finally {
                allOff()
            }
        }
    }

    private suspend fun transmit(
        plan: TxPlan,
        onToken: (Int) -> Unit,
        onLight: (Boolean) -> Unit,
        onTorchError: () -> Unit
    ) {
        val timeline = plan.timeline
        var useTorch = plan.lamp
        if (plan.sound) tone.play(timeline, plan.unitMs, plan.toneHz, plan.repeat)
        val start = SystemClock.uptimeMillis()
        var cycle = 0L
        while (true) {
            val base = cycle * timeline.totalUnits
            for (mark in timeline.marks) {
                waitUntil(start + ((base + mark.startUnit) * plan.unitMs).roundToLong())
                onToken(mark.token)
                if (useTorch) {
                    torchLit = true
                    if (!torch.set(true)) {
                        useTorch = false
                        torchLit = false
                        onTorchError()
                    }
                }
                if (plan.vibration) haptics.pulse((mark.units * plan.unitMs).roundToLong())
                if (plan.screen) onLight(true)
                waitUntil(start + ((base + mark.endUnit) * plan.unitMs).roundToLong())
                if (useTorch) {
                    torch.set(false)
                    torchLit = false
                }
                if (plan.screen) onLight(false)
            }
            if (!plan.repeat) break
            cycle++
        }
        // The sound lags behind by the audio latency: let its end play before releasing it.
        val limit = SystemClock.uptimeMillis() + SOUND_DRAIN_MS
        while (plan.sound && tone.isBusy && SystemClock.uptimeMillis() < limit) delay(POLL_MS)
    }

    /** Turns every output off; the torch only if this app lit it. */
    fun allOff() {
        tone.stop()
        haptics.cancel()
        if (torchLit) {
            torch.set(false)
            torchLit = false
        }
    }

    private suspend fun waitUntil(time: Long) {
        val wait = time - SystemClock.uptimeMillis()
        if (wait > 0L) delay(wait)
    }
}
