package com.survival.tuner

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT of a fixed power-of-two [size], with precomputed twiddles. */
internal class Fft(val size: Int) {
    private val cosTable: DoubleArray
    private val sinTable: DoubleArray
    private val reversed: IntArray

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
        sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
        val bits = Integer.numberOfTrailingZeros(size)
        reversed = IntArray(size) { Integer.reverse(it) ushr (32 - bits) }
    }

    /** Forward transform (e^(-2πi·kn/N)), unscaled. */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    /** Inverse transform (e^(+2πi·kn/N)), scaled by 1/N so that inverse(forward(x)) == x. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, inverse = true)
        val scale = 1.0 / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        require(re.size == size && im.size == size) { "Arrays must match the FFT size" }
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                var k = 0
                for (j in 0 until half) {
                    val wr = cosTable[k]
                    val wi = sign * sinTable[k]
                    val a = start + j
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    k += step
                }
                start += length
            }
            length *= 2
        }
    }
}
