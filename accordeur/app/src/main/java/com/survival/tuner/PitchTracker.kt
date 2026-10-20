package com.survival.tuner

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/** Level (dBFS) a sound must reach to start a note, and the lower level that keeps it going. */
const val GATE_OPEN_DB = -65.0
const val GATE_HOLD_DB = -75.0

/**
 * Clarity (NSDF peak height) needed to start a note, and to keep following it. Pure noise stays
 * below 0,65 (brown noise, the most correlated), a clean note is above 0,95.
 */
const val CLARITY_OPEN = 0.85
const val CLARITY_HOLD = 0.80

/** Above this clarity an estimate is precise and the short smoothing is enough. */
private const val CLEAN_CLARITY = 0.95

/** Estimates smoothed for a clean note (about 0,25 s) and for a faint or noisy one (about 0,45 s). */
const val SHORT_HISTORY = 5
const val LONG_HISTORY = 9

/** Frames without a valid estimate that are bridged before the note is dropped (about 0,2 s). */
const val HOLD_FRAMES = 4

/** An estimate further than this from the running median (semitones) is a jump, not a drift. */
private const val JUMP_SEMITONES = 0.6

/** Two consecutive estimates within this distance (semitones) agree with each other. */
private const val AGREE_SEMITONES = 0.5

/** Output of the tracker for one analysis: input level and the smoothed pitch, if any. */
class TunerFrame(val levelDb: Double, val frequency: Double?)

/**
 * Turns raw per-window estimates into a stable reading:
 *  - noise gate on level and clarity, stricter to start a note than to follow it (hysteresis);
 *  - a note starts only after two consecutive estimates agree, so an isolated detection in noise
 *    never shows up;
 *  - an estimate far from the running median (an octave glitch, or a new string) is held back
 *    until the next one confirms it, then the history restarts on the new note;
 *  - the reported pitch is a median-type smoothing in log frequency: the mean of the central
 *    half of the last [SHORT_HISTORY] estimates (interquartile mean), widened to [LONG_HISTORY]
 *    when the note gets faint or noisy, so the end of a decaying string stays steady;
 *  - short dropouts (up to [HOLD_FRAMES]) keep the last value instead of blinking.
 *
 * Not thread-safe: use it from a single thread.
 */
class PitchTracker {
    private val history = ArrayDeque<Double>()
    private val sorted = DoubleArray(LONG_HISTORY)
    private var candidate = Double.NaN
    private var missing = 0
    private var current: Double? = null

    /** True while a note is being followed. */
    val isTracking: Boolean get() = history.isNotEmpty()

    fun reset() {
        history.clear()
        candidate = Double.NaN
        missing = 0
        current = null
    }

    fun update(frame: AnalysisFrame): TunerFrame {
        val estimate = frame.estimate
        val valid = estimate != null && passesGate(frame.levelDb, estimate.clarity) &&
            estimate.frequency in MIN_FREQUENCY..MAX_FREQUENCY
        if (!valid || estimate == null) {
            candidate = Double.NaN
            if (history.isNotEmpty() && ++missing <= HOLD_FRAMES) return TunerFrame(frame.levelDb, current)
            history.clear()
            missing = 0
            current = null
            return TunerFrame(frame.levelDb, null)
        }
        missing = 0
        accept(log2(estimate.frequency))
        current = if (history.isEmpty()) {
            null
        } else {
            val size = if (estimate.clarity >= CLEAN_CLARITY) SHORT_HISTORY else LONG_HISTORY
            2.0.pow(interquartileMean(size))
        }
        return TunerFrame(frame.levelDb, current)
    }

    private fun passesGate(levelDb: Double, clarity: Double): Boolean =
        if (isTracking) {
            levelDb >= GATE_HOLD_DB && clarity >= CLARITY_HOLD
        } else {
            levelDb >= GATE_OPEN_DB && clarity >= CLARITY_OPEN
        }

    /** [value] is log2 of the frequency. */
    private fun accept(value: Double) {
        if (history.isNotEmpty() && abs(value - median(SHORT_HISTORY)) * 12.0 <= JUMP_SEMITONES) {
            history.addLast(value)
            if (history.size > LONG_HISTORY) history.removeFirst()
            candidate = Double.NaN
            return
        }
        // Either no note yet, or a jump: wait for a second estimate that agrees.
        if (!candidate.isNaN() && abs(value - candidate) * 12.0 <= AGREE_SEMITONES) {
            history.clear()
            history.addLast(candidate)
            history.addLast(value)
            candidate = Double.NaN
        } else {
            candidate = value
        }
    }

    /** Sorts the last [size] values (or all, if fewer) into [sorted]; returns their count. */
    private fun sortLatest(size: Int): Int {
        val n = minOf(size, history.size)
        val start = history.size - n
        for (i in 0 until n) sorted[i] = history[start + i]
        sorted.sort(0, n)
        return n
    }

    private fun median(size: Int): Double {
        val n = sortLatest(size)
        return if (n % 2 == 1) sorted[n / 2] else 0.5 * (sorted[n / 2 - 1] + sorted[n / 2])
    }

    /** Mean of the central half of the last [size] values: robust like a median, smoother. */
    private fun interquartileMean(size: Int): Double {
        val n = sortLatest(size)
        val cut = n / 4
        var sum = 0.0
        for (i in cut until n - cut) sum += sorted[i]
        return sum / (n - 2 * cut)
    }
}
