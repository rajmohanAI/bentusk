package com.bentusk.engine

import kotlin.math.max
import kotlin.math.min

/** A studio background: a seamless sweep from [top] to [bottom], lit by a soft glow behind the subject. */
data class StudioBackdrop(val id: String, val name: String, val top: Int, val bottom: Int) {
    /** Dark sweeps take a fainter glow and a lighter shadow, as they would under real lights. */
    val dark: Boolean get() = Contrast.luminance(bottom) < 0.18
}

/**
 * Where the cut-out subject sits on the backdrop, in output pixels. [shadow] is the soft contact shadow's oval,
 * or null when the subject runs off the bottom of the photo (a portrait cut at the waist has no feet to stand on).
 */
data class StudioPlacement(val scale: Double, val left: Double, val top: Double, val shadow: DRect?, val glow: DRect)

/** A rectangle in pixels with fractional edges. */
data class DRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2
    val centerY: Double get() = (top + bottom) / 2
}

/**
 * Pure maths for the on-device studio shot: the subject is cut out on the phone (ML Kit subject segmentation),
 * then placed on a [StudioBackdrop] with a contact shadow. Lighting and camera angle stay as photographed.
 */
object StudioShot {

    /** A pixel counts as subject at or above this alpha. */
    const val ALPHA_THRESHOLD = 128

    /** A subject this close to the photo's bottom edge is treated as cut off by it. */
    const val CROPPED_EDGE = 0.97

    /** The fraction of the frame a standing subject fills, and where its feet land. */
    const val GROUNDED_HEIGHT = 0.72
    const val FLOOR = 0.86
    const val MAX_WIDTH = 0.86

    /** A subject cut by the bottom edge stays on it and fills up to this much of the frame. */
    const val CROPPED_HEIGHT = 0.86
    const val CROPPED_WIDTH = 0.9

    /** Never enlarge a small cut-out past this, so it stays sharp. */
    const val MAX_SCALE = 3.0

    val Backdrops: List<StudioBackdrop> = listOf(
        StudioBackdrop("white", "White seamless", 0xFFF7F7F5.toInt(), 0xFFE2E2DE.toInt()),
        StudioBackdrop("grey", "Soft grey", 0xFFDCDDDF.toInt(), 0xFFAEB0B4.toInt()),
        StudioBackdrop("paper", "Warm paper", 0xFFF3E8D9.toInt(), 0xFFD8C2A4.toInt()),
        StudioBackdrop("blush", "Blush", 0xFFF7E0DB.toInt(), 0xFFE0B5AC.toInt()),
        StudioBackdrop("sage", "Sage", 0xFFDDE6D7.toInt(), 0xFFB3C3AA.toInt()),
        StudioBackdrop("sky", "Sky", 0xFFDEEAF4.toInt(), 0xFFAAC3DB.toInt()),
        StudioBackdrop("sunset", "Sunset", 0xFFFFD6A8.toInt(), 0xFFF2768A.toInt()),
        StudioBackdrop("charcoal", "Charcoal", 0xFF46474C.toInt(), 0xFF1A1B1E.toInt()),
        StudioBackdrop("midnight", "Midnight", 0xFF2B3A57.toInt(), 0xFF0E1626.toInt()),
    )

    fun backdrop(id: String): StudioBackdrop? = Backdrops.firstOrNull { it.id == id }

    /**
     * The box around the subject in a cut-out's alpha values (row-major, [width] by [height]), or null when the
     * segmenter found nothing.
     */
    fun subjectBounds(alpha: IntArray, width: Int, height: Int): PxRect? {
        require(alpha.size == width * height) { "Alpha size ${alpha.size} is not $width x $height" }
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (alpha[row + x] >= ALPHA_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        if (right < 0) return null
        return PxRect(left, top, right + 1, bottom + 1)
    }

    /** True when the subject runs off the bottom of its photo. */
    fun cropped(bounds: PxRect, photoHeight: Int): Boolean = bounds.bottom >= photoHeight * CROPPED_EDGE

    /** Where the subject in [bounds] (cut-out pixels) goes on an [out]-sized backdrop. */
    fun place(bounds: PxRect, photoHeight: Int, out: PxSize): StudioPlacement {
        val w = bounds.width.toDouble()
        val h = bounds.height.toDouble()
        val cropped = cropped(bounds, photoHeight)
        val fitH = if (cropped) CROPPED_HEIGHT else GROUNDED_HEIGHT
        val fitW = if (cropped) CROPPED_WIDTH else MAX_WIDTH
        val scale = min(MAX_SCALE, min(out.height * fitH / h, out.width * fitW / w))
        val sw = w * scale
        val sh = h * scale
        val left = (out.width - sw) / 2
        val floor = if (cropped) out.height.toDouble() else out.height * FLOOR
        val top = floor - sh
        val shadow = if (cropped) null else {
            val sx = sw * 0.55
            val sy = max(out.height * 0.012, sw * 0.07)
            DRect(out.width / 2.0 - sx, floor - sy * 0.6, out.width / 2.0 + sx, floor + sy * 0.4)
        }
        val glowR = max(sw, sh) * 0.75
        val glowCy = top + sh * 0.4
        val glow = DRect(out.width / 2.0 - glowR, glowCy - glowR, out.width / 2.0 + glowR, glowCy + glowR)
        // Offsets are of the cut-out's top-left corner, so the renderer can draw the whole cut-out bitmap.
        return StudioPlacement(scale, left - bounds.left * scale, top - bounds.top * scale, shadow, glow)
    }

    /** The output size for a frame of [slot] pixels, longest side at most [maxSide]. */
    fun outputSize(slot: PxSize, maxSide: Int = MAX_SIDE): PxSize {
        val k = min(1.0, maxSide.toDouble() / max(slot.width, slot.height))
        return PxSize(max(1, (slot.width * k).toInt()), max(1, (slot.height * k).toInt()))
    }

    const val MAX_SIDE = 2160
}
