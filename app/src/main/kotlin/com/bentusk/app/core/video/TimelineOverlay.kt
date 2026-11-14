package com.bentusk.app.core.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.bentusk.app.core.image.DesignRenderer
import com.bentusk.engine.PxSize
import com.bentusk.engine.VideoOverlay
import com.bentusk.engine.VideoOverlays
import com.bentusk.engine.VideoTimeline
import com.bentusk.engine.VideoTransitions
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws a video maker timeline's text and pictures, for the preview and the encoder alike. */
object VideoOverlayRenderer {

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Draws [overlays] on a [frame]-sized canvas. Pictures are fitted inside their rect, never stretched. */
    fun draw(canvas: Canvas, frame: PxSize, overlays: List<VideoOverlay>, images: Map<String, Bitmap>) {
        for (o in overlays) {
            val r = o.rect.toPx(frame)
            if (o.isText) {
                DesignRenderer.drawText(canvas, r, o.toTextLayer(), frame.height)
            } else {
                val bitmap = o.imageUri?.let(images::get) ?: continue
                val k = min(r.width.toFloat() / bitmap.width, r.height.toFloat() / bitmap.height)
                val w = bitmap.width * k
                val h = bitmap.height * k
                val left = r.left + (r.width - w) / 2
                val top = r.top + (r.height - h) / 2
                canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), imagePaint)
            }
        }
    }
}

/**
 * The timeline's overlays as one full-frame bitmap for the encoder, over the dip to black or white of any
 * transition playing. It is redrawn only when what is on screen changes, into the other of two bitmaps, so the
 * one being uploaded is never the one being drawn.
 */
@OptIn(UnstableApi::class)
class TimelineOverlay(
    private val timeline: VideoTimeline,
    private val images: Map<String, Bitmap>,
) : BitmapOverlay() {
    private val size = timeline.shape.size
    private val cuts = VideoTransitions.cuts(timeline)
    private val buffers = Array(2) { Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888) }
    private var current = 0
    private var shown: Pair<List<Long>, Int>? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = (presentationTimeUs / 1000.0).roundToInt().toLong()
        val visible = VideoOverlays.visibleAt(timeline, ms)
        val dip = VideoTransitions.frame(timeline, cuts, ms).dip
        val key = visible.map { it.id } to dip
        if (key != shown) {
            current = 1 - current
            val bitmap = buffers[current]
            bitmap.eraseColor(dip)
            VideoOverlayRenderer.draw(Canvas(bitmap), size, visible, images)
            shown = key
        }
        return buffers[current]
    }

    fun recycle() = buffers.forEach(Bitmap::recycle)
}
