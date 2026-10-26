package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/** How a text layer moves. Movement is gentle and loops, so it draws the eye without looking like an ad. */
@Serializable
enum class Motion {
    /** Slides a little to the right and back, pointing at the next slide. */
    Nudge,

    /** Grows a little and back, like a heartbeat. */
    Pulse,
}

/**
 * The math behind moving text. One loop lasts [LOOP_MS]; an exported slide holds a whole number of loops,
 * so it repeats seamlessly when Instagram plays it on a loop.
 */
object Motions {
    const val LOOP_MS = 1_200L

    /** Length of a still slide exported as video: four loops, comfortably over Instagram's three-second minimum. */
    const val SLIDE_MS = 4 * LOOP_MS

    /** Furthest a nudge travels, in slide widths. */
    const val NUDGE_TRAVEL = 0.04

    /** Largest growth of a pulse, as a fraction of the text's size. */
    const val PULSE_GROWTH = 0.08

    /** Eased position in the loop at [timeMs]: 0 at rest, 1 at the far point, back to 0 at the loop's end. */
    fun wave(timeMs: Long): Double {
        val t = Math.floorMod(timeMs, LOOP_MS).toDouble() / LOOP_MS
        return (1 - cos(2 * PI * t)) / 2
    }

    /** [layer] as drawn at [timeMs] into the loop, kept on the canvas. Layers without motion are returned as they are. */
    fun at(layer: TextLayer, design: Design, timeMs: Long): TextLayer {
        val motion = layer.motion ?: return layer
        val w = wave(timeMs)
        val r = layer.rect
        return when (motion) {
            Motion.Nudge -> {
                val slideWidth = 1.0 / design.layout().columns
                val right = min(r.right + NUDGE_TRAVEL * slideWidth * w, 1.0)
                layer.copy(rect = NRect(r.left + (right - r.right), r.top, right, r.bottom))
            }
            Motion.Pulse -> {
                val k = 1 + PULSE_GROWTH * w
                val cx = (r.left + r.right) / 2
                val cy = (r.top + r.bottom) / 2
                val hw = (r.right - r.left) / 2 * k
                val hh = (r.bottom - r.top) / 2 * k
                val grown = NRect(
                    (cx - hw).coerceAtLeast(0.0),
                    (cy - hh).coerceAtLeast(0.0),
                    (cx + hw).coerceAtMost(1.0),
                    (cy + hh).coerceAtMost(1.0),
                )
                layer.copy(rect = grown, size = layer.size * k)
            }
        }
    }

    /** The whole design as drawn at [timeMs]. */
    fun frame(design: Design, timeMs: Long): Design =
        if (!moves(design)) design else design.copy(layers = design.layers.map { if (it is TextLayer) at(it, design, timeMs) else it })

    fun moves(design: Design): Boolean = design.layers.any { it is TextLayer && it.motion != null && it.text.isNotBlank() }

    /** Layout indices of the slides or tiles that moving text reaches at any point in its loop. */
    fun movingTiles(design: Design): Set<Int> {
        val layout = design.layout()
        val moving = design.layers.filterIsInstance<TextLayer>().filter { it.motion != null && it.text.isNotBlank() }
        if (moving.isEmpty()) return emptySet()
        val reach = moving.flatMap { listOf(design.rectPx(it, layout), design.rectPx(at(it, design, LOOP_MS / 2), layout)) }
        return layout.tiles.filter { tile -> reach.any { overlaps(it, tile.rect) } }.map { it.index }.toSet()
    }

    /** Index in [Design.layers] of the lowest moving layer, or -1. Everything below it can be drawn once. */
    fun firstMoving(design: Design): Int =
        design.layers.indexOfFirst { it is TextLayer && it.motion != null && it.text.isNotBlank() }

    private fun overlaps(a: PxRect, b: PxRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
