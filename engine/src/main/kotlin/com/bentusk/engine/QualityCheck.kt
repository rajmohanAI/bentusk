package com.bentusk.engine

import kotlin.math.max
import kotlin.math.min

/** Something in a design that will probably look worse on Instagram than the creator expects. */
enum class IssueKind {
    /** A template frame with no photo, which exports as bare background. */
    EmptyFrame,

    /** A photo enlarged so far past its own resolution that it will look soft. */
    SoftPhoto,

    /** Text under the slide counter or the tagged-people icon. */
    UnderIcons,

    /** Text too small to read on a phone at feed size. */
    SmallText,

    /** A clip shorter than Instagram accepts in a carousel. */
    ShortClip,
}

/**
 * One finding. [tile] is the slide or tile it shows on (layout index). [amount] carries the number behind
 * the finding where there is one: the enlargement for [IssueKind.SoftPhoto], the on-phone text height in
 * points for [IssueKind.SmallText], the clip length in milliseconds for [IssueKind.ShortClip].
 */
data class QualityIssue(val kind: IssueKind, val layerId: Long, val tile: Int, val amount: Double = 0.0)

/**
 * Checks a design before export for the problems creators most often only spot after posting.
 * Pure geometry: it needs only the design and each photo's upright size.
 */
object QualityCheck {
    /** Past this enlargement a photo visibly loses detail at full size. */
    const val SOFT_SCALE = 1.5

    /** A feed post fills about this many points of phone width, so 1080 px shrink to roughly a third. */
    const val FEED_POINTS = 390.0

    /** Below this height in points (at feed size) text is hard to read. */
    const val MIN_TEXT_POINTS = 11.0

    /** Instagram rejects carousel videos shorter than this. */
    const val MIN_CLIP_MS = 3_000L

    /** Line height as a multiple of the text size, used to estimate how tall a block of text really is. */
    private const val LINE_HEIGHT = 1.2

    /**
     * Every issue in [design], ordered by slide or tile. [sources] gives the upright size of each photo layer's
     * picture by layer id; photos missing from it (still loading, or video posters) skip the sharpness check.
     */
    fun run(design: Design, sources: Map<Long, PxSize>): List<QualityIssue> {
        val layout = design.layout()
        val carousel = design.kind == TemplateKind.Carousel
        val issues = ArrayList<QualityIssue>()
        for (layer in design.layers) {
            val rect = design.rectPx(layer, layout)
            val home = homeTile(rect, layout)
            when (layer) {
                is PhotoLayer -> {
                    val clip = layer.video
                    when {
                        clip != null -> if (clip.lengthMs < MIN_CLIP_MS) {
                            issues += QualityIssue(IssueKind.ShortClip, layer.id, home, clip.lengthMs.toDouble())
                        }
                        layer.uri == null -> if (!layer.free) issues += QualityIssue(IssueKind.EmptyFrame, layer.id, home)
                        else -> sources[layer.id]?.let { source ->
                            val scale = FramingMath.scale(source, rect.size, layer.framing)
                            if (scale > SOFT_SCALE) issues += QualityIssue(IssueKind.SoftPhoto, layer.id, home, scale)
                        }
                    }
                }
                is TextLayer -> {
                    if (layer.text.isBlank()) continue
                    val points = textPoints(layer, layout)
                    if (points < MIN_TEXT_POINTS) issues += QualityIssue(IssueKind.SmallText, layer.id, home, points)
                    val covered = coveredTile(layer, rect, layout, carousel)
                    if (covered != null) issues += QualityIssue(IssueKind.UnderIcons, layer.id, covered)
                }
            }
        }
        return issues.sortedWith(compareBy({ layout.tiles[it.tile].postOrder }, { it.kind.ordinal }))
    }

    /** How tall [layer]'s letters are on a phone, in points, when the post fills the screen width. */
    fun textPoints(layer: TextLayer, layout: SliceLayout): Double =
        layer.size * layout.tileSize.height * FEED_POINTS / layout.tileSize.width

    /** The tile that holds most of [rect]. */
    private fun homeTile(rect: PxRect, layout: SliceLayout): Int =
        layout.tiles.maxBy { overlapArea(rect, it.rect) }.index

    /**
     * The first tile where [layer]'s text sits under one of Instagram's icons, or null. Only the band the lines
     * actually fill counts, since text is centred vertically in a box that is often taller than the words.
     */
    private fun coveredTile(layer: TextLayer, rect: PxRect, layout: SliceLayout, carousel: Boolean): Int? {
        val lines = max(1, layer.text.trimEnd().lines().size)
        val textHeight = min(rect.height.toDouble(), lines * layer.size * layout.tileSize.height * LINE_HEIGHT)
        val mid = (rect.top + rect.bottom) / 2.0
        val top = mid - textHeight / 2
        val bottom = mid + textHeight / 2
        for (tile in layout.tiles) {
            val t = tile.rect
            val zones = SafeZones.overlays(t.width.toDouble() / t.height, carousel)
            for (zone in zones) {
                val z = zone.rect
                val zl = t.left + z.left * t.width
                val zr = t.left + z.right * t.width
                val zt = t.top + z.top * t.height
                val zb = t.top + z.bottom * t.height
                if (rect.left < zr && zl < rect.right && top < zb && zt < bottom) return tile.index
            }
        }
        return null
    }

    private fun overlapArea(a: PxRect, b: PxRect): Long {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        return if (w <= 0 || h <= 0) 0 else w.toLong() * h
    }
}
