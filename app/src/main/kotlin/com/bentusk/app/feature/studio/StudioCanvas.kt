package com.bentusk.app.feature.studio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import com.bentusk.app.core.image.DesignRenderer
import com.bentusk.app.core.image.EmptySlots
import com.bentusk.app.core.image.SlotPhoto
import com.bentusk.app.ui.components.drawSafeZones
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.Design
import com.bentusk.engine.Motions
import com.bentusk.engine.PhotoLayer
import com.bentusk.engine.PxRect
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.TemplateKind
import com.bentusk.engine.TextLayer
import com.bentusk.engine.Vec2
import kotlin.math.abs

/** Callbacks for direct manipulation. All points are in layout-canvas pixels. */
class CanvasGestures(
    /** A finger went down at a point; returns true when it landed on a layer. */
    val onPress: (Vec2) -> Boolean,
    val onTap: (Vec2) -> Unit,
    val onStart: () -> Unit,
    val onTransform: (centroid: Vec2, pan: Vec2, zoom: Double, rotation: Double) -> Unit,
    val onEnd: () -> Unit,
)

/**
 * Shows [region] of a design through the exporter's own renderer, so the screen matches the saved files.
 * One finger on a layer moves it; two fingers zoom and rotate the selected layer. A drag that starts on empty
 * space is left alone, so a surrounding pager can still be swiped.
 */
@Composable
fun StudioCanvas(
    design: Design,
    layout: SliceLayout,
    photos: Map<Long, SlotPhoto>,
    modifier: Modifier = Modifier,
    region: PxRect = PxRect(0, 0, layout.canvas.width, layout.canvas.height),
    selected: Long? = null,
    showGuides: Boolean = false,
    /** Marks where Instagram's icons cover the post and where the grid and square views crop it. */
    showSafeZones: Boolean = false,
    empty: EmptySlots = EmptySlots.Placeholder,
    gestures: CanvasGestures? = null,
) {
    val currentRegion by rememberUpdatedState(region)
    val currentGestures by rememberUpdatedState(gestures)
    val hasSelection by rememberUpdatedState(selected != null)
    val slop = LocalViewConfiguration.current.touchSlop
    // Moving text plays its loop on screen, just as it will in the exported video.
    val clock = if (Motions.moves(design)) {
        rememberInfiniteTransition(label = "motion").animateFloat(
            initialValue = 0f,
            targetValue = Motions.LOOP_MS.toFloat(),
            animationSpec = infiniteRepeatable(tween(Motions.LOOP_MS.toInt(), easing = LinearEasing)),
            label = "motion-time",
        )
    } else {
        null
    }

    val input = if (gestures == null) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            fun toCanvas(p: Offset): Vec2 {
                val r = currentRegion
                val k = size.width / r.width.toDouble()
                return Vec2(r.left + p.x / k, r.top + p.y / k)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val g = currentGestures ?: return@awaitEachGesture
                val start = toCanvas(down.position)
                val grabbed = g.onPress(start)
                var travelled = 0f
                var moving = false
                var started = false
                do {
                    val event = awaitPointerEvent()
                    val fingers = event.changes.count { it.pressed }
                    val pan = event.calculatePan()
                    travelled += abs(pan.x) + abs(pan.y)
                    if (!moving && (travelled > slop || fingers >= 2)) moving = true
                    val mine = grabbed || (fingers >= 2 && hasSelection)
                    if (moving && mine && fingers > 0) {
                        if (!started) {
                            g.onStart()
                            started = true
                        }
                        val k = size.width / currentRegion.width.toDouble()
                        val centroid = toCanvas(event.calculateCentroid(useCurrent = false))
                        g.onTransform(
                            centroid,
                            Vec2(pan.x / k, pan.y / k),
                            event.calculateZoom().toDouble(),
                            event.calculateRotation().toDouble(),
                        )
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (started) g.onEnd()
                if (!moving) g.onTap(start)
            }
        }
    }

    Canvas(
        modifier
            .aspectRatio(region.width.toFloat() / region.height)
            .clipToBounds()
            .then(input),
    ) {
        val k = size.width / region.width
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(k, k)
            native.translate(-region.left.toFloat(), -region.top.toFloat())
            val shown = clock?.let { Motions.frame(design, it.value.toLong()) } ?: design
            DesignRenderer.draw(native, region, shown, layout, photos, empty)
            native.restore()
        }

        fun box(r: PxRect) = Offset((r.left - region.left) * k, (r.top - region.top) * k) to Size(r.width * k, r.height * k)

        if (gestures != null) {
            // A plus in each empty slot invites a tap to fill it.
            for (layer in design.layersOn(region, layout)) {
                if (layer !is PhotoLayer || layer.uri != null) continue
                val (topLeft, s) = box(design.rectPx(layer, layout))
                val c = topLeft + Offset(s.width / 2, s.height / 2)
                val arm = minOf(s.width, s.height, 120f * density) * 0.12f
                val plus = Color.Black.copy(alpha = 0.35f)
                drawLine(plus, c - Offset(arm, 0f), c + Offset(arm, 0f), strokeWidth = 2.5f * density)
                drawLine(plus, c - Offset(0f, arm), c + Offset(0f, arm), strokeWidth = 2.5f * density)
            }
            // A play badge marks slides that export as video, since the canvas shows only the poster frame.
            for (layer in design.layersOn(region, layout)) {
                if (layer !is PhotoLayer || layer.video == null) continue
                val (topLeft, _) = box(design.rectPx(layer, layout))
                val r = 14f * density
                val c = topLeft + Offset(r + 10f * density, r + 10f * density)
                drawCircle(Color.Black.copy(alpha = 0.55f), r, c)
                val play = Path().apply {
                    moveTo(c.x - r * 0.3f, c.y - r * 0.42f)
                    lineTo(c.x + r * 0.45f, c.y)
                    lineTo(c.x - r * 0.3f, c.y + r * 0.42f)
                    close()
                }
                drawPath(play, Color.White)
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

        if (showSafeZones) {
            val carousel = design.kind == TemplateKind.Carousel
            for (tile in layout.tiles) {
                val r = tile.rect
                if (r.right <= region.left || r.left >= region.right || r.bottom <= region.top || r.top >= region.bottom) continue
                val (topLeft, s) = box(r)
                // The cover is what the grid shows; on a grid every tile is its own cover.
                drawSafeZones(topLeft, s, carousel = carousel, cover = !carousel || tile.postOrder == 1)
            }
        }

        val layer = selected?.let(design::layer)
        if (layer != null) {
            val (topLeft, s) = box(design.rectPx(layer, layout))
            val w = 2f * density
            val corner = if (layer is PhotoLayer) (layer.corner * layout.tileSize.width * k).toFloat() else 6f * density
            val effect = if (layer is TextLayer) PathEffect.dashPathEffect(floatArrayOf(8f * density, 5f * density)) else null
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = topLeft + Offset(w, w),
                size = Size(s.width - w, s.height - w),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = w, pathEffect = effect),
            )
            drawRoundRect(
                color = BentuskColors.Gold,
                topLeft = topLeft + Offset(w / 2, w / 2),
                size = Size(s.width - w, s.height - w),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = w, pathEffect = effect),
            )
        }
    }
}
