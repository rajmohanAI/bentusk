package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A rectangle in fractions of the whole layout canvas (0..1 on both axes), so templates work at any ratio. */
@Serializable
data class NRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(left in 0.0..1.0 && right in 0.0..1.0 && top in 0.0..1.0 && bottom in 0.0..1.0) { "Out of canvas: $this" }
        require(right > left && bottom > top) { "Empty rect: $this" }
    }

    /** Snaps to whole canvas pixels, so slots that share an edge in fractions share it exactly in pixels too. */
    fun toPx(canvas: PxSize) = PxRect(
        (left * canvas.width).roundToInt(),
        (top * canvas.height).roundToInt(),
        (right * canvas.width).roundToInt(),
        (bottom * canvas.height).roundToInt(),
    )
}

/** Where one photo goes. A slot may cross slide boundaries; that is what makes a carousel flow. */
data class PhotoSlot(
    val rect: NRect,
    /** Corner radius as a fraction of one tile's width. */
    val corner: Double = 0.0,
)

enum class TextAlign { Start, Center, End }

/** Typefaces every Android phone has, so text needs no bundled fonts. */
enum class TextFont { Sans, Serif, Mono, Script, Condensed }

/** An editable line or paragraph of text drawn on top of the photos. */
data class TextSlot(
    val rect: NRect,
    val defaultText: String,
    /** Text size as a fraction of one tile's height. */
    val size: Double,
    val argb: Int,
    val align: TextAlign = TextAlign.Center,
    val bold: Boolean = true,
    val font: TextFont = TextFont.Sans,
    /** Colour of a rounded label behind the text, or null for none. */
    val pill: Int? = null,
)

enum class TemplateKind {
    Carousel,
    Grid,

    /** One full-screen 9:16 picture: a Reel cover or a story. */
    Story,
}

data class Template(
    val id: String,
    val name: String,
    val kind: TemplateKind,
    /** Slides for a carousel, rows for a grid. */
    val count: Int,
    val ratio: PostRatio,
    val background: Int,
    val photos: List<PhotoSlot>,
    val texts: List<TextSlot> = emptyList(),
    val premium: Boolean = false,
    /** Theme the gallery groups this template under. */
    val category: String = "",
) {
    init {
        require(photos.isNotEmpty() || texts.isNotEmpty()) { "Template $id is empty" }
    }

    fun layout(ratio: PostRatio = this.ratio): SliceLayout = when (kind) {
        TemplateKind.Carousel -> CarouselLayout(count, ratio)
        TemplateKind.Grid -> GridLayout(count, ratio)
        TemplateKind.Story -> StoryLayout
    }

    /** Pixel rect of photo slot [index] on the canvas of [layout]. */
    fun slotRect(index: Int, layout: SliceLayout): PxRect = photos[index].rect.toPx(layout.canvas)

    fun cornerPx(index: Int, layout: SliceLayout): Double = photos[index].corner * layout.tileSize.width

    fun textRect(index: Int, layout: SliceLayout): PxRect = texts[index].rect.toPx(layout.canvas)

    fun textSizePx(index: Int, layout: SliceLayout): Double = texts[index].size * layout.tileSize.height

    /** Indices of photo slots that show on [tile], so a tile renders only what it needs. */
    fun slotsOn(tile: PxRect, layout: SliceLayout): List<Int> =
        photos.indices.filter { overlaps(slotRect(it, layout), tile) }

    companion object {
        private fun overlaps(a: PxRect, b: PxRect) =
            a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    }
}

/** Places a photo in a template slot: the slot is the canvas the photo is framed against. */
object SlotMath {
    /**
     * Transform from a decoded raw bitmap to layout-canvas pixels for a photo placed in [slot].
     * [framing] is in slot pixels, exactly like a single-photo editor framing, so the same gestures and clamping apply.
     */
    fun decodedToCanvas(geometry: SourceGeometry, decoded: PxSize, slot: PxRect, framing: Framing): Affine =
        geometry.decodedToCanvas(decoded, slot.size, framing)
            .then(Affine.translate(slot.left.toDouble(), slot.top.toDouble()))

    /** Source-to-slot scale, for planning how finely to decode the photo. */
    fun scale(geometry: SourceGeometry, slot: PxRect, framing: Framing): Double =
        FramingMath.scale(geometry.upright, slot.size, framing)

    /** A shared pixel budget split evenly between [photos] decoded at once. */
    fun budgetPerPhoto(totalPixels: Long, photos: Int): Long = totalPixels / maxOf(1, photos)
}
