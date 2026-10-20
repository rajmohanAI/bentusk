package com.bentusk.engine

import kotlin.math.roundToInt

/**
 * Highlight cover geometry. Covers are exported as full stories; Instagram then crops a centred circle,
 * so the icon is centred on the canvas and sized relative to that circle.
 */
data class CoverLayout(
    /** Icon size as a fraction of the visible circle's diameter. */
    val iconFraction: Double = 0.5,
    val canvas: PxSize = InstagramSpec.StorySize,
) {
    init {
        require(iconFraction in 0.1..1.0) { "Icon fraction must be within 0.1-1.0" }
    }

    val circleDiameter: Int get() = evenAtMost(canvas.width * InstagramSpec.HIGHLIGHT_CIRCLE_FRACTION)

    val circle: PxRect get() = centred(circleDiameter)

    val iconRect: PxRect get() = centred(evenAtMost(circleDiameter * iconFraction))

    /** Even sides keep shapes exactly centred on the even-sized story canvas. */
    private fun evenAtMost(value: Double): Int = maxOf(2, value.roundToInt() / 2 * 2)

    private fun centred(side: Int): PxRect {
        val left = (canvas.width - side) / 2
        val top = (canvas.height - side) / 2
        return PxRect(left, top, left + side, top + side)
    }
}
