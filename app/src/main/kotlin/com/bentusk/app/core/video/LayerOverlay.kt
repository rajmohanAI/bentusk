package com.bentusk.app.core.video

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.bentusk.engine.PxSize

/**
 * A full-slide overlay drawn by [draw] for each frame's time in milliseconds. A still overlay is drawn once;
 * a moving one is drawn every frame into two bitmaps in turn, so the frame being uploaded is never the one
 * being drawn and each new frame is a new bitmap to the effect.
 */
@OptIn(UnstableApi::class)
class LayerOverlay(
    size: PxSize,
    private val moving: Boolean,
    private val draw: (canvas: Canvas, timeMs: Long) -> Unit,
) : BitmapOverlay() {
    private val buffers = Array(if (moving) 2 else 1) { Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888) }
    private var next = 0
    private var drawn = false

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (!moving) {
            if (!drawn) {
                draw(Canvas(buffers[0]), 0)
                drawn = true
            }
            return buffers[0]
        }
        val bitmap = buffers[next]
        next = (next + 1) % buffers.size
        bitmap.eraseColor(0)
        draw(Canvas(bitmap), presentationTimeUs / 1000)
        return bitmap
    }

    /** Frees the bitmaps once the encode has finished. */
    fun recycle() = buffers.forEach(Bitmap::recycle)
}
