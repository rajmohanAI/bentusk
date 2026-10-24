package com.bentusk.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

/** A post's background: one colour, or a top-to-bottom gradient when [bottom] differs. */
@Serializable
data class Fill(val top: Int, val bottom: Int = top) {
    val isGradient: Boolean get() = top != bottom
}

/** Something drawn on a design. Rects are fractions of the whole canvas, so they survive ratio changes. */
@Serializable
sealed interface Layer {
    val id: Long
    val rect: NRect
}

/**
 * A photo. In a template slot ([free] false) the frame is fixed and gestures move the picture inside it.
 * A free photo's frame matches the picture's shape and gestures move and resize the frame itself.
 */
@Serializable
@SerialName("photo")
data class PhotoLayer(
    override val id: Long,
    override val rect: NRect,
    /** Corner radius as a fraction of one tile's width. */
    val corner: Double = 0.0,
    /** Content URI of the photo, or null for an empty slot. */
    val uri: String? = null,
    /** In slot pixels at export resolution. */
    val framing: Framing = Framing.Default,
    val free: Boolean = false,
    /** Set when [uri] is a video clip; the layer then fills one whole slide and exports as a video. */
    val video: VideoClip? = null,
) : Layer

/** Text wrapped to the width of [rect] and centred in it vertically. */
@Serializable
@SerialName("text")
data class TextLayer(
    override val id: Long,
    override val rect: NRect,
    val text: String,
    /** Text size as a fraction of one tile's height. */
    val size: Double,
    val argb: Int,
    val align: TextAlign = TextAlign.Center,
    val bold: Boolean = true,
    val font: TextFont = TextFont.Sans,
    val pill: Int? = null,
    /** Set to make the text move; a slide holding moving text exports as a short looping video. */
    val motion: Motion? = null,
) : Layer

/**
 * Everything the user is making: the post shape, a background and layers drawn bottom to top.
 * Templates are designs with layers already placed; a blank design starts with none.
 */
@Serializable
data class Design(
    val kind: TemplateKind,
    /** Slides for a carousel, rows for a grid. */
    val count: Int,
    val ratio: PostRatio,
    val fill: Fill,
    val layers: List<Layer> = emptyList(),
    val templateId: String? = null,
    /** Id of the [Palette] last applied, whose colours the text tools offer first. */
    val palette: String? = null,
) {
    fun layout(): SliceLayout = when (kind) {
        TemplateKind.Carousel -> CarouselLayout(count, ratio)
        TemplateKind.Grid -> GridLayout(count, ratio)
    }

    val photos: List<PhotoLayer> get() = layers.filterIsInstance<PhotoLayer>()

    /** Photo layers holding still pictures, which the photo pipeline decodes. */
    val stills: List<PhotoLayer> get() = photos.filter { it.video == null }

    fun layer(id: Long): Layer? = layers.firstOrNull { it.id == id }

    fun rectPx(layer: Layer, layout: SliceLayout = layout()): PxRect = layer.rect.toPx(layout.canvas)

    /** Layers that show on [region] of the canvas, bottom to top. */
    fun layersOn(region: PxRect, layout: SliceLayout = layout()): List<Layer> =
        layers.filter { overlaps(rectPx(it, layout), region) }

    /** Topmost layer under [point] (canvas pixels), or null. */
    fun hit(point: Vec2, layout: SliceLayout = layout()): Layer? =
        layers.lastOrNull { rectPx(it, layout).contains(point.x.toInt(), point.y.toInt()) }

    fun nextId(): Long = (layers.maxOfOrNull { it.id } ?: 0L) + 1

    /** One tile's area as a fraction rect, for placing new layers on the slide the user is looking at. */
    fun tileRect(index: Int): NRect {
        val layout = layout()
        val r = layout.tiles[index.coerceIn(0, layout.count - 1)].rect
        val c = layout.canvas
        return NRect(r.left / c.width.toDouble(), r.top / c.height.toDouble(), r.right / c.width.toDouble(), r.bottom / c.height.toDouble())
    }

    fun replace(layer: Layer): Design = copy(layers = layers.map { if (it.id == layer.id) layer else it })

    fun remove(id: Long): Design = copy(layers = layers.filterNot { it.id == id })

    fun add(layer: Layer): Design = copy(layers = layers + layer)

    /** Moves a layer one step up (towards the viewer) or down. */
    fun restack(id: Long, up: Boolean): Design {
        val i = layers.indexOfFirst { it.id == id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in layers.indices) return this
        val list = layers.toMutableList()
        list[i] = layers[j]
        list[j] = layers[i]
        return copy(layers = list)
    }

    /**
     * Changes the number of slides or rows while keeping every layer at the same pixel position.
     * Layers that no longer fit are dropped; those cut by the new edge are trimmed.
     */
    fun withCount(newCount: Int): Design {
        if (newCount == count) return this
        val k = count.toDouble() / newCount
        val moved = layers.mapNotNull { layer ->
            val r = layer.rect
            val scaled = when (kind) {
                TemplateKind.Carousel -> Box(r.left * k, r.top, min(1.0, r.right * k), r.bottom)
                TemplateKind.Grid -> Box(r.left, r.top * k, r.right, min(1.0, r.bottom * k))
            }
            if (scaled.right - scaled.left < MIN_SIZE || scaled.bottom - scaled.top < MIN_SIZE) null else layer.withRect(scaled.toNRect())
        }
        return copy(count = newCount, layers = moved)
    }

    /** Moves a layer by a fraction of the canvas, keeping it on the canvas. */
    fun move(id: Long, dx: Double, dy: Double): Design {
        val layer = layer(id) ?: return this
        val r = layer.rect
        val x = dx.coerceIn(-r.left, 1.0 - r.right)
        val y = dy.coerceIn(-r.top, 1.0 - r.bottom)
        return replace(layer.withRect(NRect(r.left + x, r.top + y, r.right + x, r.bottom + y)))
    }

    /**
     * Resizes a free photo or text layer by [factor] around [pivot] (canvas fractions), keeping its shape,
     * with sizes limited so it stays usable and on the canvas. Text grows its font with the box.
     */
    fun scale(id: Long, factor: Double, pivot: Vec2, canvasAspect: Double): Design {
        val layer = layer(id) ?: return this
        val r = layer.rect
        val w = r.right - r.left
        val h = r.bottom - r.top
        // Smallest side at least MIN_SIZE (in canvas-width units for x), largest no bigger than the canvas.
        val minK = max(MIN_SIZE / w, MIN_SIZE / h)
        val maxK = min(1.0 / w, 1.0 / h)
        val k = factor.coerceIn(min(minK, 1.0), max(maxK, 1.0))
        var b = Box(
            pivot.x + (r.left - pivot.x) * k,
            pivot.y + (r.top - pivot.y) * k,
            pivot.x + (r.right - pivot.x) * k,
            pivot.y + (r.bottom - pivot.y) * k,
        )
        b = b.shiftedInside()
        val resized = layer.withRect(b.toNRect())
        return replace(if (resized is TextLayer) resized.copy(size = resized.size * k) else resized)
    }

    companion object {
        /** Smallest layer side, as a fraction of the canvas. */
        const val MIN_SIZE = 0.02

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun toJson(design: Design): String = json.encodeToString(serializer(), design)

        fun fromJson(text: String): Design = json.decodeFromString(serializer(), text)

        /** An empty design to build freely. */
        fun blank(kind: TemplateKind, count: Int, ratio: PostRatio, fill: Fill = Fill(0xFFFFFFFF.toInt())) =
            Design(kind, count, ratio, fill)

        /**
         * A free photo of upright shape [aspect] (width/height) centred on tile [tile], sized to sit comfortably inside it.
         */
        fun freePhotoRect(design: Design, tile: Int, aspect: Double): NRect {
            val t = design.tileRect(tile)
            val canvas = design.layout().canvas
            // Work in pixels so the frame keeps the photo's exact shape.
            val tw = (t.right - t.left) * canvas.width
            val th = (t.bottom - t.top) * canvas.height
            val scale = min(tw * 0.7 / aspect, th * 0.7)
            val w = scale * aspect / canvas.width
            val h = scale / canvas.height
            val cx = (t.left + t.right) / 2
            val cy = (t.top + t.bottom) / 2
            return NRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        }

        private fun overlaps(a: PxRect, b: PxRect) =
            a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    }
}

/** An unchecked rect used while computing, before it becomes a valid [NRect]. */
private data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun shiftedInside(): Box {
        val dx = when {
            left < 0 -> -left
            right > 1 -> 1 - right
            else -> 0.0
        }
        val dy = when {
            top < 0 -> -top
            bottom > 1 -> 1 - bottom
            else -> 0.0
        }
        return Box(left + dx, top + dy, right + dx, bottom + dy)
    }

    fun toNRect() = NRect(left.coerceIn(0.0, 1.0), top.coerceIn(0.0, 1.0), right.coerceIn(0.0, 1.0), bottom.coerceIn(0.0, 1.0))
}

fun Layer.withRect(rect: NRect): Layer = when (this) {
    is PhotoLayer -> copy(rect = rect)
    is TextLayer -> copy(rect = rect)
}

/** The template as an editable design, with its slots empty and its text as written. */
fun Template.toDesign(): Design {
    var id = 0L
    val layers = buildList<Layer> {
        photos.forEach { add(PhotoLayer(++id, it.rect, it.corner)) }
        texts.forEach { add(TextLayer(++id, it.rect, it.defaultText, it.size, it.argb, it.align, it.bold, it.font, it.pill)) }
    }
    return Design(kind, count, ratio, Fill(background), layers, templateId = this.id)
}
