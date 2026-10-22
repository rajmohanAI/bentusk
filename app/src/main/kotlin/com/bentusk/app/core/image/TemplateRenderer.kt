package com.bentusk.app.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bentusk.engine.Framing
import com.bentusk.engine.PxRect
import com.bentusk.engine.PxSize
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.SlotMath
import com.bentusk.engine.SourceGeometry
import com.bentusk.engine.Template
import com.bentusk.engine.TextAlign

/** A decoded photo placed in one template slot. [framing] is in slot pixels at export resolution. */
class SlotPhoto(val bitmap: Bitmap, val geometry: SourceGeometry, val framing: Framing)

/**
 * Draws a template in layout-canvas pixels. The caller translates [canvas] so that the region it wants
 * (a tile for export, the whole strip or one slide for preview) starts at the origin, so preview and export
 * share every line of drawing code.
 */
object TemplateRenderer {

    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }

    fun draw(
        canvas: Canvas,
        region: PxRect,
        template: Template,
        layout: SliceLayout,
        background: Int,
        photos: List<SlotPhoto?>,
        texts: List<String>,
        showEmptySlots: Boolean,
    ) {
        canvas.save()
        canvas.clipRect(region.left.toFloat(), region.top.toFloat(), region.right.toFloat(), region.bottom.toFloat())
        canvas.drawRect(
            region.left.toFloat(), region.top.toFloat(), region.right.toFloat(), region.bottom.toFloat(),
            Paint().apply { color = background },
        )
        for (index in template.slotsOn(region, layout)) {
            val slot = template.slotRect(index, layout)
            val corner = template.cornerPx(index, layout).toFloat()
            val photo = photos.getOrNull(index)
            canvas.save()
            canvas.clipPath(
                Path().apply {
                    addRoundRect(RectF(slot.left.toFloat(), slot.top.toFloat(), slot.right.toFloat(), slot.bottom.toFloat()), corner, corner, Path.Direction.CW)
                },
            )
            if (photo != null) {
                val transform = SlotMath.decodedToCanvas(
                    photo.geometry,
                    PxSize(photo.bitmap.width, photo.bitmap.height),
                    slot,
                    photo.framing,
                )
                canvas.drawBitmap(photo.bitmap, Matrix().apply { setValues(transform.toMatrixValues()) }, photoPaint)
            } else if (showEmptySlots) {
                canvas.drawRect(slot.left.toFloat(), slot.top.toFloat(), slot.right.toFloat(), slot.bottom.toFloat(), emptyPaint)
            }
            canvas.restore()
        }
        template.texts.forEachIndexed { index, slot ->
            val text = texts.getOrNull(index) ?: slot.defaultText
            if (text.isBlank()) return@forEachIndexed
            val rect = template.textRect(index, layout)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = slot.argb
                textSize = template.textSizePx(index, layout).toFloat()
                typeface = if (slot.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                letterSpacing = if (slot.bold) 0.04f else 0f
            }
            val alignment = when (slot.align) {
                TextAlign.Start -> Layout.Alignment.ALIGN_NORMAL
                TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
                TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
            }
            val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, rect.width)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.1f)
                .setMaxLines(6)
                .build()
            canvas.save()
            val top = rect.top + (rect.height - staticLayout.height).coerceAtLeast(0) / 2f
            canvas.translate(rect.left.toFloat(), top)
            staticLayout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
    }

    /** Renders one export tile. */
    fun renderTile(
        tile: PxRect,
        template: Template,
        layout: SliceLayout,
        background: Int,
        photos: List<SlotPhoto?>,
        texts: List<String>,
    ): Bitmap {
        val out = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.translate(-tile.left.toFloat(), -tile.top.toFloat())
        draw(canvas, tile, template, layout, background, photos, texts, showEmptySlots = false)
        return out
    }
}
