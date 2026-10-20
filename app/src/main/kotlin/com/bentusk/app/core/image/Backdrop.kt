package com.bentusk.app.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.bentusk.engine.Framing
import com.bentusk.engine.PxRect
import com.bentusk.engine.PxSize
import com.bentusk.engine.SourceGeometry
import kotlin.math.max
import kotlin.math.roundToInt

/** What fills the canvas where the photo does not reach. */
sealed interface Backdrop {
    data class Solid(val argb: Int) : Backdrop
    /** A soft, blurred copy of the photo itself. */
    data object Blur : Backdrop

    companion object {
        val White = Solid(0xFFFFFFFF.toInt())
        val Black = Solid(0xFF000000.toInt())
    }
}

/**
 * A backdrop prepared once per render and drawn identically into every tile, so the fill is seamless too.
 */
class PreparedBackdrop private constructor(
    private val solid: Int?,
    private val blurred: Bitmap?,
    private val canvas: PxSize,
) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val solidPaint = solid?.let { argb -> Paint().apply { color = argb } }

    /** Draws the backdrop for [tile] into [target], whose origin is the tile's top-left corner. */
    fun drawInto(target: Canvas, tile: PxRect) {
        solidPaint?.let { target.drawRect(0f, 0f, tile.width.toFloat(), tile.height.toFloat(), it) }
        val blur = blurred ?: return
        val m = Matrix().apply {
            setScale(canvas.width / blur.width.toFloat(), canvas.height / blur.height.toFloat())
            postTranslate(-tile.left.toFloat(), -tile.top.toFloat())
        }
        target.drawBitmap(blur, m, paint)
    }

    fun recycle() {
        blurred?.recycle()
    }

    companion object {
        fun prepare(backdrop: Backdrop, photo: Bitmap, geometry: SourceGeometry, canvas: PxSize): PreparedBackdrop =
            when (backdrop) {
                is Backdrop.Solid -> PreparedBackdrop(backdrop.argb, null, canvas)
                Backdrop.Blur -> PreparedBackdrop(0xFF000000.toInt(), blurredCover(photo, geometry, canvas), canvas)
            }

        /** Draws the photo covering a tiny version of the canvas, then blurs it. Upscaling smooths it further. */
        private fun blurredCover(photo: Bitmap, geometry: SourceGeometry, canvas: PxSize): Bitmap {
            val longSide = 96
            val k = longSide.toDouble() / max(canvas.width, canvas.height)
            val small = PxSize(max(8, (canvas.width * k).roundToInt()), max(8, (canvas.height * k).roundToInt()))
            val transform = geometry.decodedToCanvas(PxSize(photo.width, photo.height), small, Framing.Default)
            val bitmap = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawBitmap(
                photo,
                Matrix().apply { setValues(transform.toMatrixValues()) },
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
            BoxBlur.apply(bitmap, radius = 3, passes = 3, dim = 0.82f)
            return bitmap
        }
    }
}

/** Separable box blur; three passes approximate a Gaussian. Runs on a tiny bitmap so it is instant. */
internal object BoxBlur {
    fun apply(bitmap: Bitmap, radius: Int, passes: Int, dim: Float) {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(passes) {
            blurLine(px, tmp, w, h, radius, horizontal = true)
            blurLine(tmp, px, w, h, radius, horizontal = false)
        }
        if (dim < 1f) {
            for (i in px.indices) {
                val c = px[i]
                val r = (((c shr 16) and 0xFF) * dim).toInt()
                val g = (((c shr 8) and 0xFF) * dim).toInt()
                val b = ((c and 0xFF) * dim).toInt()
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val c = src[idx(i)]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                val out = src[idx(i - r)]
                val inc = src[idx(i + r + 1)]
                sr += ((inc shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inc shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inc and 0xFF) - (out and 0xFF)
            }
        }
    }
}
