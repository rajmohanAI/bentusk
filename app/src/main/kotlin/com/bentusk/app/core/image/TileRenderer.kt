package com.bentusk.app.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.bentusk.engine.Affine
import com.bentusk.engine.FramingMath
import com.bentusk.engine.PxRect

/** Optional frame drawn inside every tile, in export pixels. */
data class TileFrame(val widthPx: Int, val argb: Int) {
    companion object {
        val None: TileFrame? = null
    }
}

/**
 * Renders one tile directly from the source bitmap using the shared canvas transform shifted to the tile.
 * Each tile is rendered independently (no giant intermediate canvas), so memory stays flat no matter how many
 * slides there are, and neighbouring tiles agree to the pixel along every seam.
 */
object TileRenderer {

    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    fun render(
        photo: Bitmap,
        decodedToCanvas: Affine,
        tile: PxRect,
        backdrop: PreparedBackdrop,
        frame: TileFrame?,
    ): Bitmap {
        val out = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        backdrop.drawInto(canvas, tile)
        val matrix = Matrix().apply { setValues(FramingMath.tileTransform(decodedToCanvas, tile).toMatrixValues()) }
        canvas.drawBitmap(photo, matrix, photoPaint)
        if (frame != null && frame.widthPx > 0) {
            val half = frame.widthPx / 2f
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = frame.widthPx.toFloat()
                color = frame.argb
            }
            canvas.drawRect(half, half, tile.width - half, tile.height - half, stroke)
        }
        return out
    }
}
