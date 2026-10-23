package com.bentusk.engine

/** Where a ready-made sticker sits on the slide the user is looking at. */
enum class StickerPlacement {
    /** A large arrow cut in half by the right-hand seam, so the slide visibly continues. */
    SeamArrow,

    /** A short line of text that runs across the right-hand seam and is finished on the next slide. */
    SeamText,

    /** A small "swipe" pill in the bottom-right corner. */
    CornerPill,

    /** A wide call-to-action banner across the bottom, for a closing slide. */
    Banner,
}

/**
 * Ready-made swipe nudges and calls to action, placed as ordinary text layers so they stay fully editable.
 * Seam stickers need a neighbour on the right; on the last slide of a row they move inside the slide instead.
 */
object Stickers {
    private const val PILL = 0xE6111114.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun layer(design: Design, page: Int, placement: StickerPlacement, text: String, ink: Int): TextLayer {
        val style = when (placement) {
            StickerPlacement.SeamArrow -> Triple(0.14, ink, null)
            StickerPlacement.SeamText -> Triple(0.05, ink, null)
            StickerPlacement.CornerPill -> Triple(0.04, WHITE, PILL)
            StickerPlacement.Banner -> Triple(0.05, WHITE, PILL)
        }
        return TextLayer(
            id = design.nextId(),
            rect = rect(design, page, placement),
            text = text,
            size = style.first,
            argb = style.second,
            align = TextAlign.Center,
            bold = true,
            pill = style.third,
        )
    }

    /** Whether the slide or tile at [page] has another one to its right for a seam sticker to run onto. */
    fun hasRightNeighbour(design: Design, page: Int): Boolean {
        val layout = design.layout()
        val tile = layout.tiles[page.coerceIn(0, layout.count - 1)]
        return tile.column < layout.columns - 1
    }

    fun rect(design: Design, page: Int, placement: StickerPlacement): NRect {
        val t = design.tileRect(page)
        val w = t.right - t.left
        val h = t.bottom - t.top
        val seam = hasRightNeighbour(design, page)
        fun box(x0: Double, y0: Double, x1: Double, y1: Double) =
            NRect(x0.coerceIn(0.0, 1.0), y0.coerceIn(0.0, 1.0), x1.coerceIn(0.0, 1.0), y1.coerceIn(0.0, 1.0))
        return when (placement) {
            StickerPlacement.SeamArrow ->
                if (seam) box(t.right - w * 0.1, t.top + h * 0.4, t.right + w * 0.1, t.top + h * 0.6)
                else box(t.right - w * 0.22, t.top + h * 0.4, t.right - w * 0.02, t.top + h * 0.6)
            StickerPlacement.SeamText ->
                if (seam) box(t.right - w * 0.32, t.top + h * 0.72, t.right + w * 0.32, t.top + h * 0.82)
                else box(t.right - w * 0.66, t.top + h * 0.72, t.right - w * 0.04, t.top + h * 0.82)
            StickerPlacement.CornerPill -> box(t.left + w * 0.56, t.top + h * 0.85, t.left + w * 0.95, t.top + h * 0.95)
            StickerPlacement.Banner -> box(t.left + w * 0.08, t.top + h * 0.76, t.left + w * 0.92, t.top + h * 0.92)
        }
    }
}
