package com.survival.pulse

import java.nio.ByteBuffer
import kotlin.math.sqrt

/** One plane of a camera frame in YUV 4:2:0 layout (planar or interleaved chroma). */
internal class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

/** Share of the frame width and height, centred, over which the colour is averaged. */
private const val REGION_FRACTION = 0.5

/** Sampling step in luma pixels, in both directions (a fingertip image is blurred anyway). */
private const val SAMPLE_STEP = 4

/**
 * Mean luma, spatial spread of the luma and mean chroma (Cb, Cr) over the central part of a
 * YUV 4:2:0 frame of [width] x [height] pixels. Returns null for a frame too small to measure or
 * whose buffers are shorter than the strides imply (the frame is then simply skipped).
 */
internal fun measureFrame(timestampNs: Long, width: Int, height: Int, y: Plane, u: Plane, v: Plane): FrameStats? {
    if (width < 2 * SAMPLE_STEP || height < 2 * SAMPLE_STEP) return null
    if (y.rowStride <= 0 || y.pixelStride <= 0 || u.rowStride <= 0 || u.pixelStride <= 0 || v.rowStride <= 0 || v.pixelStride <= 0) {
        return null
    }
    // Even bounds, so that luma and chroma positions stay aligned.
    val left = ((width * (1.0 - REGION_FRACTION) / 2.0).toInt() / 2) * 2
    val top = ((height * (1.0 - REGION_FRACTION) / 2.0).toInt() / 2) * 2
    val right = width - left
    val bottom = height - top
    val yLimit = y.buffer.limit()
    val uLimit = u.buffer.limit()
    val vLimit = v.buffer.limit()

    var sumY = 0L
    var sumYSquared = 0L
    var sumU = 0L
    var sumV = 0L
    var count = 0
    var row = top
    while (row < bottom) {
        var col = left
        while (col < right) {
            val yIndex = row * y.rowStride + col * y.pixelStride
            val uIndex = (row / 2) * u.rowStride + (col / 2) * u.pixelStride
            val vIndex = (row / 2) * v.rowStride + (col / 2) * v.pixelStride
            if (yIndex >= yLimit || uIndex >= uLimit || vIndex >= vLimit) return null
            val luma = y.buffer.get(yIndex).toInt() and 0xFF
            sumY += luma
            sumYSquared += luma * luma
            sumU += u.buffer.get(uIndex).toInt() and 0xFF
            sumV += v.buffer.get(vIndex).toInt() and 0xFF
            count++
            col += SAMPLE_STEP
        }
        row += SAMPLE_STEP
    }
    if (count == 0) return null
    val meanY = sumY.toDouble() / count
    val variance = (sumYSquared.toDouble() / count - meanY * meanY).coerceAtLeast(0.0)
    return FrameStats(
        timestampNs = timestampNs,
        luma = meanY,
        lumaSpread = sqrt(variance),
        cb = sumU.toDouble() / count,
        cr = sumV.toDouble() / count,
    )
}
