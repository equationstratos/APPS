package com.survival.tuner

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Second-order IIR section (transposed direct form II, double precision) with Butterworth
 * low-pass and high-pass designs from the RBJ "Audio EQ Cookbook". A linear filter keeps the
 * period of a periodic signal unchanged, so filtering never biases the measured pitch.
 */
internal class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    companion object {
        /** Q of a second-order Butterworth section. */
        val BUTTERWORTH_Q = 1.0 / sqrt(2.0)

        /** Q of the two sections of a fourth-order Butterworth filter. */
        val BUTTERWORTH_Q4 = doubleArrayOf(0.5411961001461970, 1.3065629648763764)

        fun lowPass(sampleRate: Int, cutoff: Double, q: Double = BUTTERWORTH_Q): Biquad {
            val w0 = 2.0 * PI * cutoff / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = (1.0 - cosW) / 2.0 / a0,
                b1 = (1.0 - cosW) / a0,
                b2 = (1.0 - cosW) / 2.0 / a0,
                a1 = -2.0 * cosW / a0,
                a2 = (1.0 - alpha) / a0
            )
        }

        fun highPass(sampleRate: Int, cutoff: Double): Biquad {
            val w0 = 2.0 * PI * cutoff / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * BUTTERWORTH_Q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = (1.0 + cosW) / 2.0 / a0,
                b1 = -(1.0 + cosW) / a0,
                b2 = (1.0 + cosW) / 2.0 / a0,
                a1 = -2.0 * cosW / a0,
                a2 = (1.0 - alpha) / a0
            )
        }
    }
}
