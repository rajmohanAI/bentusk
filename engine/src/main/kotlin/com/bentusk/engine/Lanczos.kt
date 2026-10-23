package com.bentusk.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lanczos-3 resampling for downscaling photos before they are drawn.
 *
 * A bilinear draw at well under 1:1 skips source pixels, which turns fine detail (hair, fabric, foliage, text)
 * into jagged noise. Shrinking the photo first with a windowed-sinc filter, then drawing it at about 1:1,
 * keeps that detail clean. Rows are streamed through a small ring buffer, so memory stays at a few rows
 * of output no matter how large the photo is.
 */
object Lanczos {
    const val RADIUS = 3

    /** Below this much shrinking, the bilinear draw is already clean and the extra pass is skipped. */
    const val MIN_SHRINK = 0.95

    fun kernel(x: Double): Double {
        val ax = abs(x)
        if (ax < 1e-9) return 1.0
        if (ax >= RADIUS) return 0.0
        val px = PI * ax
        return RADIUS * sin(px) * sin(px / RADIUS) / (px * px)
    }

    /**
     * The size to shrink a raw bitmap decoded at [decoded] to, when a photo of full raw size [raw] will be drawn at
     * [rawToCanvas] canvas pixels per raw pixel; null when no shrinking is needed. The result never drops below one
     * output pixel per canvas pixel, so nothing is lost.
     */
    fun targetSize(raw: PxSize, decoded: PxSize, rawToCanvas: Double): PxSize? {
        val w = min(decoded.width, max(1, ceil(raw.width * rawToCanvas - 1e-9).toInt()))
        val h = min(decoded.height, max(1, ceil(raw.height * rawToCanvas - 1e-9).toInt()))
        return if (w < decoded.width * MIN_SHRINK && h < decoded.height * MIN_SHRINK) PxSize(w, h) else null
    }

    /** Filter taps for one axis: output pixel i reads source pixels start[i] until start[i] + weights[i].size. */
    class Axis(val source: Int, val target: Int) {
        val start = IntArray(target)
        val weights = arrayOfNulls<FloatArray>(target)

        init {
            require(source > 0 && target > 0) { "Sizes must be positive" }
            val scale = source.toDouble() / target
            val stretch = max(1.0, scale)
            val support = RADIUS * stretch
            for (i in 0 until target) {
                val centre = (i + 0.5) * scale - 0.5
                val lo = floor(centre - support).toInt() + 1
                val hi = ceil(centre + support).toInt() - 1
                // Taps past an edge repeat the edge pixel, so fold their weight onto it.
                val first = lo.coerceIn(0, source - 1)
                val last = hi.coerceIn(0, source - 1)
                val w = DoubleArray(last - first + 1)
                var sum = 0.0
                for (j in lo..hi) {
                    val k = kernel((j - centre) / stretch)
                    w[j.coerceIn(0, source - 1) - first] += k
                    sum += k
                }
                start[i] = first
                weights[i] = FloatArray(w.size) { (w[it] / sum).toFloat() }
            }
        }

        val maxTaps: Int get() = weights.maxOf { it!!.size }
    }

    /**
     * Resizes a [srcW]×[srcH] image to [dstW]×[dstH]. [readRow] fills a buffer of [srcW] ARGB pixels for a source row;
     * [writeRow] receives each finished output row in order. Colours are filtered premultiplied, so transparent edges
     * do not bleed, and clamped, so the filter's slight overshoot never wraps around.
     */
    fun resize(
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        readRow: (y: Int, pixels: IntArray) -> Unit,
        writeRow: (y: Int, pixels: IntArray) -> Unit,
    ) {
        val horizontal = Axis(srcW, dstW)
        val vertical = Axis(srcH, dstH)
        val capacity = vertical.maxTaps
        // Horizontally filtered rows, four premultiplied channels each, kept for as long as output rows need them.
        val ring = Array(capacity) { FloatArray(dstW * 4) }
        val held = IntArray(capacity) { -1 }
        val source = IntArray(srcW)
        val premul = FloatArray(srcW * 4)
        val out = IntArray(dstW)

        fun row(y: Int): FloatArray {
            val slot = y % capacity
            val buf = ring[slot]
            if (held[slot] == y) return buf
            readRow(y, source)
            for (x in 0 until srcW) {
                val p = source[x]
                val a = (p ushr 24) / 255f
                premul[x * 4] = a
                premul[x * 4 + 1] = ((p shr 16) and 0xFF) * a
                premul[x * 4 + 2] = ((p shr 8) and 0xFF) * a
                premul[x * 4 + 3] = (p and 0xFF) * a
            }
            for (x in 0 until dstW) {
                val w = horizontal.weights[x]!!
                var s = horizontal.start[x] * 4
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in w) {
                    a += premul[s] * k
                    r += premul[s + 1] * k
                    g += premul[s + 2] * k
                    b += premul[s + 3] * k
                    s += 4
                }
                buf[x * 4] = a
                buf[x * 4 + 1] = r
                buf[x * 4 + 2] = g
                buf[x * 4 + 3] = b
            }
            held[slot] = y
            return buf
        }

        val acc = FloatArray(dstW * 4)
        for (y in 0 until dstH) {
            acc.fill(0f)
            val w = vertical.weights[y]!!
            val first = vertical.start[y]
            for (t in w.indices) {
                val k = w[t]
                val src = row(first + t)
                for (i in acc.indices) acc[i] += src[i] * k
            }
            for (x in 0 until dstW) {
                val a = acc[x * 4].coerceIn(0f, 1f)
                out[x] = if (a <= 0f) {
                    0
                } else {
                    val alpha = (a * 255f).roundToInt()
                    val r = (acc[x * 4 + 1] / a).roundToInt().coerceIn(0, 255)
                    val g = (acc[x * 4 + 2] / a).roundToInt().coerceIn(0, 255)
                    val b = (acc[x * 4 + 3] / a).roundToInt().coerceIn(0, 255)
                    (alpha shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            writeRow(y, out)
        }
    }
}
