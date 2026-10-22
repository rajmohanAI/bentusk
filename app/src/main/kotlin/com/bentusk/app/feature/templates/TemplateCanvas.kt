package com.bentusk.app.feature.templates

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.bentusk.app.core.image.SlotPhoto
import com.bentusk.app.core.image.TemplateRenderer
import com.bentusk.app.feature.editor.GestureMode
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.PxRect
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.Template
import com.bentusk.engine.Vec2

/**
 * Shows [region] of a template through the exporter's own renderer, so what is on screen is what gets saved.
 * Points passed to [onTap] and [onGesture] are in layout-canvas pixels.
 */
@Composable
fun TemplateCanvas(
    template: Template,
    layout: SliceLayout,
    background: Int,
    photos: List<SlotPhoto?>,
    texts: List<String>,
    modifier: Modifier = Modifier,
    region: PxRect = PxRect(0, 0, layout.canvas.width, layout.canvas.height),
    selected: Int? = null,
    showGuides: Boolean = false,
    interactive: Boolean = true,
    gestureMode: GestureMode = GestureMode.All,
    onTap: (Vec2) -> Unit = {},
    onGesture: (centroid: Vec2, pan: Vec2, zoom: Double, rotation: Double) -> Unit = { _, _, _, _ -> },
) {
    val currentRegion by rememberUpdatedState(region)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnGesture by rememberUpdatedState(onGesture)

    fun toCanvas(widthPx: Int, p: Offset): Vec2 {
        val r = currentRegion
        val k = widthPx / r.width.toDouble()
        return Vec2(r.left + p.x / k, r.top + p.y / k)
    }

    fun dispatch(widthPx: Int, centroid: Offset, pan: Offset, zoom: Float, rotation: Float) {
        val k = widthPx / currentRegion.width.toDouble()
        currentOnGesture(toCanvas(widthPx, centroid), Vec2(pan.x / k, pan.y / k), zoom.toDouble(), rotation.toDouble())
    }

    val gestures = if (!interactive) {
        Modifier
    } else {
        val tap = Modifier.pointerInput(Unit) { detectTapGestures { currentOnTap(toCanvas(size.width, it)) } }
        val transform = when (gestureMode) {
            GestureMode.None -> Modifier
            GestureMode.All -> Modifier.pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation -> dispatch(size.width, centroid, pan, zoom, rotation) }
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
        tap.then(transform)
    }

    Canvas(
        modifier
            .aspectRatio(region.width.toFloat() / region.height)
            .clipToBounds()
            .then(gestures),
    ) {
        val k = size.width / region.width
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(k, k)
            native.translate(-region.left.toFloat(), -region.top.toFloat())
            TemplateRenderer.draw(native, region, template, layout, background, photos, texts, showEmptySlots = true)
            native.restore()
        }

        fun rectOf(r: PxRect) = Offset((r.left - region.left) * k, (r.top - region.top) * k) to Size(r.width * k, r.height * k)

        if (interactive) {
            // A plus in each empty slot invites a tap to fill it.
            for (index in template.slotsOn(region, layout)) {
                if (photos.getOrNull(index) != null) continue
                val (topLeft, s) = rectOf(template.slotRect(index, layout))
                val c = topLeft + Offset(s.width / 2, s.height / 2)
                val arm = minOf(s.width, s.height, 120f * density) * 0.12f
                val plus = Color.Black.copy(alpha = 0.35f)
                drawLine(plus, c - Offset(arm, 0f), c + Offset(arm, 0f), strokeWidth = 2.5f * density)
                drawLine(plus, c - Offset(0f, arm), c + Offset(0f, arm), strokeWidth = 2.5f * density)
            }
        }

        if (showGuides) {
            val line = Color.White.copy(alpha = 0.8f)
            val shadow = Color.Black.copy(alpha = 0.35f)
            for (tile in layout.tiles) {
                val x = (tile.rect.left - region.left) * k
                val y = (tile.rect.top - region.top) * k
                if (tile.rect.left > region.left && tile.rect.left < region.right) {
                    drawLine(shadow, Offset(x + 1, 0f), Offset(x + 1, size.height), strokeWidth = 1.5f)
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                }
                if (tile.rect.top > region.top && tile.rect.top < region.bottom) {
                    drawLine(shadow, Offset(0f, y + 1), Offset(size.width, y + 1), strokeWidth = 1.5f)
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                }
            }
        }

        if (selected != null && selected in template.photos.indices) {
            val (topLeft, s) = rectOf(template.slotRect(selected, layout))
            val w = 2.5f * density
            drawRoundRect(
                color = BentuskColors.Gold,
                topLeft = topLeft + Offset(w / 2, w / 2),
                size = Size(s.width - w, s.height - w),
                cornerRadius = CornerRadius((template.cornerPx(selected, layout) * k).toFloat()),
                style = Stroke(width = w),
            )
        }
    }
}
