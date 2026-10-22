package com.bentusk.app.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bentusk.engine.Design
import com.bentusk.engine.PhotoLayer
import com.bentusk.engine.PxRect
import com.bentusk.engine.PxSize
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.SlotMath
import com.bentusk.engine.SourceGeometry
import com.bentusk.engine.TextAlign
import com.bentusk.engine.TextFont
import com.bentusk.engine.TextLayer
import com.bentusk.engine.Framing

/** A decoded photo for one photo layer. [framing] is in slot pixels at export resolution. */
class SlotPhoto(val bitmap: Bitmap, val geometry: SourceGeometry, val framing: Framing)

/** What to draw in a photo layer that has no photo yet. */
enum class EmptySlots { Nothing, Placeholder, SampleArt }

/**
 * Draws a design in layout-canvas pixels. The caller translates [canvas] so the region it wants (a tile for
 * export, the whole strip or one slide on screen) starts at the origin, so preview and export share every line.
 */
object DesignRenderer {

    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1F000000 }

    fun draw(
        canvas: Canvas,
        region: PxRect,
        design: Design,
        layout: SliceLayout,
        photos: Map<Long, SlotPhoto>,
        empty: EmptySlots,
    ) {
        canvas.save()
        val bounds = RectF(region.left.toFloat(), region.top.toFloat(), region.right.toFloat(), region.bottom.toFloat())
        canvas.clipRect(bounds)
        fill(canvas, bounds, design, layout)
        for (layer in design.layersOn(region, layout)) {
            when (layer) {
                is PhotoLayer -> drawPhoto(canvas, design, layout, layer, photos[layer.id], empty)
                is TextLayer -> drawText(canvas, design.rectPx(layer, layout), layer, layout)
            }
        }
        canvas.restore()
    }

    private fun fill(canvas: Canvas, bounds: RectF, design: Design, layout: SliceLayout) {
        val paint = Paint()
        if (design.fill.isGradient) {
            // One gradient across the whole canvas, so it flows over seams like a photo does.
            paint.shader = LinearGradient(
                0f, 0f, 0f, layout.canvas.height.toFloat(), design.fill.top, design.fill.bottom, Shader.TileMode.CLAMP,
            )
        } else {
            paint.color = design.fill.top
        }
        canvas.drawRect(bounds, paint)
    }

    private fun drawPhoto(
        canvas: Canvas,
        design: Design,
        layout: SliceLayout,
        layer: PhotoLayer,
        photo: SlotPhoto?,
        empty: EmptySlots,
    ) {
        val slot = design.rectPx(layer, layout)
        val corner = (layer.corner * layout.tileSize.width).toFloat()
        val rect = RectF(slot.left.toFloat(), slot.top.toFloat(), slot.right.toFloat(), slot.bottom.toFloat())
        canvas.save()
        if (corner > 0f) canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }) else canvas.clipRect(rect)
        when {
            photo != null -> {
                val transform = SlotMath.decodedToCanvas(photo.geometry, PxSize(photo.bitmap.width, photo.bitmap.height), slot, photo.framing)
                canvas.drawBitmap(photo.bitmap, Matrix().apply { setValues(transform.toMatrixValues()) }, photoPaint)
            }
            empty == EmptySlots.Placeholder -> canvas.drawRect(rect, emptyPaint)
            empty == EmptySlots.SampleArt -> MockArt.draw(canvas, rect, (layer.id * 7 + (design.templateId?.hashCode() ?: 0)).toInt())
            else -> Unit
        }
        canvas.restore()
    }

    fun typeface(font: TextFont, bold: Boolean): Typeface {
        val family = when (font) {
            TextFont.Sans -> Typeface.SANS_SERIF
            TextFont.Serif -> Typeface.SERIF
            TextFont.Mono -> Typeface.MONOSPACE
            TextFont.Script -> Typeface.create("cursive", Typeface.NORMAL)
            TextFont.Condensed -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        }
        return Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun drawText(canvas: Canvas, rect: PxRect, layer: TextLayer, layout: SliceLayout) {
        if (layer.text.isBlank()) return
        val size = (layer.size * layout.tileSize.height).toFloat()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.argb
            textSize = size
            typeface = typeface(layer.font, layer.bold)
            letterSpacing = if (layer.bold && layer.font == TextFont.Sans) 0.04f else 0f
        }
        val alignment = when (layer.align) {
            TextAlign.Start -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
            TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val pad = if (layer.pill != null) size * 0.45f else 0f
        val width = (rect.width - 2 * pad).toInt().coerceAtLeast(1)
        val text = StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.1f)
            .setMaxLines(8)
            .build()
        val top = rect.top + (rect.height - text.height) / 2f
        canvas.save()
        layer.pill?.let { pill ->
            var lineLeft = Float.MAX_VALUE
            var lineRight = 0f
            for (i in 0 until text.lineCount) {
                lineLeft = minOf(lineLeft, text.getLineLeft(i))
                lineRight = maxOf(lineRight, text.getLineRight(i))
            }
            val box = RectF(
                rect.left + pad + lineLeft - pad, top - pad * 0.6f,
                rect.left + pad + lineRight + pad, top + text.height + pad * 0.6f,
            )
            val r = box.height() / 2
            canvas.drawRoundRect(box, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pill })
        }
        canvas.translate(rect.left + pad, top)
        text.draw(canvas)
        canvas.restore()
    }

    /** Renders one export tile. */
    fun renderTile(tile: PxRect, design: Design, layout: SliceLayout, photos: Map<Long, SlotPhoto>): Bitmap {
        val out = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.translate(-tile.left.toFloat(), -tile.top.toFloat())
        draw(canvas, tile, design, layout, photos, EmptySlots.Nothing)
        return out
    }
}
