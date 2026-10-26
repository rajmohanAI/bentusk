package com.bentusk.app.feature.editor

import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.bentusk.app.core.image.TileFrame
import com.bentusk.app.ui.components.drawSafeZones
import com.bentusk.engine.CarouselLayout
import com.bentusk.engine.Framing
import com.bentusk.engine.FramingMath
import com.bentusk.engine.PxRect
import com.bentusk.engine.PxSize
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.Vec2

enum class GestureMode {
    None,
    /** One finger pans, two fingers zoom and rotate. */
    All,
    /** Only two-finger gestures, so a surrounding pager can still be swiped with one finger. */
    MultiTouch,
}

/**
 * Draws [region] of the layout canvas (the whole canvas by default) with the same transform, backdrop and frame
 * code the exporter uses, so the preview is exactly what will be saved.
 */
@Composable
fun SliceCanvas(
    preview: PreviewPhoto,
    layout: SliceLayout,
    framing: Framing,
    frame: TileFrame?,
    showGuides: Boolean,
    modifier: Modifier = Modifier,
    /** Marks where Instagram's icons cover each slide and where the grid and square views crop it. */
    showSafeZones: Boolean = false,
    region: PxRect = PxRect(0, 0, layout.canvas.width, layout.canvas.height),
    gestureMode: GestureMode = GestureMode.All,
    onGesture: (centroid: Vec2, pan: Vec2, zoom: Double, rotation: Double) -> Unit = { _, _, _, _ -> },
) {
    val textMeasurer = rememberTextMeasurer()
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val currentRegion by rememberUpdatedState(region)
    val currentCanvas by rememberUpdatedState(layout.canvas)
    val currentOnGesture by rememberUpdatedState(onGesture)

    // Converts a gesture in display pixels into export pixels, relative to the canvas centre.
    fun dispatch(widthPx: Int, centroid: Offset, pan: Offset, zoom: Float, rotation: Float) {
        val r = currentRegion
        val k = widthPx / r.width.toDouble()
        val c = Vec2(
            r.left + centroid.x / k - currentCanvas.width / 2.0,
            r.top + centroid.y / k - currentCanvas.height / 2.0,
        )
        currentOnGesture(c, Vec2(pan.x / k, pan.y / k), zoom.toDouble(), rotation.toDouble())
    }

    val gestureModifier = when (gestureMode) {
        GestureMode.None -> Modifier
        GestureMode.All -> Modifier.pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                dispatch(size.width, centroid, pan, zoom, rotation)
            }
        }
        GestureMode.MultiTouch -> Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val rotation = event.calculateRotation()
                        if (zoom != 1f || pan != Offset.Zero || rotation != 0f) {
                            dispatch(size.width, event.calculateCentroid(useCurrent = false), pan, zoom, rotation)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }

    Canvas(
        modifier
            .aspectRatio(region.width.toFloat() / region.height)
            .clipToBounds()
            .then(gestureModifier),
    ) {
        val k = size.width / region.width
        val bitmap = preview.bitmap
        val transform = preview.geometry.decodedToCanvas(PxSize(bitmap.width, bitmap.height), layout.canvas, framing)
        val matrix = Matrix().apply { setValues(FramingMath.tileTransform(transform, region).toMatrixValues()) }

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(k, k)
            native.clipRect(0f, 0f, region.width.toFloat(), region.height.toFloat())
            preview.backdrop.drawInto(native, region)
            native.drawBitmap(bitmap, matrix, paint)
            native.restore()
        }

        val tiles = layout.tiles.filter { overlaps(it.rect, region) }
        if (frame != null && frame.widthPx > 0) {
            val w = frame.widthPx * k
            for (tile in tiles) {
                drawRect(
                    color = Color(frame.argb),
                    topLeft = Offset((tile.rect.left - region.left) * k + w / 2, (tile.rect.top - region.top) * k + w / 2),
                    size = Size(tile.rect.width * k - w, tile.rect.height * k - w),
                    style = Stroke(width = w),
                )
            }
        }

        if (showSafeZones) {
            val carousel = layout is CarouselLayout
            for (tile in tiles) {
                drawSafeZones(
                    Offset((tile.rect.left - region.left) * k, (tile.rect.top - region.top) * k),
                    Size(tile.rect.width * k, tile.rect.height * k),
                    carousel = carousel,
                    cover = !carousel || tile.postOrder == 1,
                )
            }
        }

        if (showGuides) {
            val line = Color.White.copy(alpha = 0.75f)
            val shadow = Color.Black.copy(alpha = 0.35f)
            for (tile in tiles) {
                val x = (tile.rect.left - region.left) * k
                val y = (tile.rect.top - region.top) * k
                if (tile.rect.left > region.left) {
                    drawLine(shadow, Offset(x + 1, 0f), Offset(x + 1, size.height), strokeWidth = 1.5f)
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                }
                if (tile.rect.top > region.top) {
                    drawLine(shadow, Offset(0f, y + 1), Offset(size.width, y + 1), strokeWidth = 1.5f)
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                }
                val label = textMeasurer.measure(
                    tile.postOrder.toString(),
                    TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                )
                val pad = 6f
                val badge = Size(label.size.width + pad * 2, label.size.height + pad / 2)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset(x + 8f, y + 8f),
                    size = badge,
                    cornerRadius = CornerRadius(badge.height / 2),
                )
                drawText(label, topLeft = Offset(x + 8f + pad, y + 8f + pad / 4))
            }
        }
    }
}

private fun overlaps(a: PxRect, b: PxRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
