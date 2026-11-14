package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * Text or a picture shown over a video maker timeline from [startMs] to [endMs] (timeline time, not clip time).
 * [rect] is a fraction of the frame, so it survives a change of shape. A text overlay has [text]; an image overlay
 * has [imageUri] and keeps its picture's shape inside [rect].
 */
@Serializable
data class VideoOverlay(
    val id: Long,
    val rect: NRect,
    val startMs: Long,
    val endMs: Long,
    val text: String? = null,
    /** Text height as a fraction of the frame's height. */
    val size: Double = DEFAULT_TEXT_SIZE,
    val argb: Int = WHITE,
    val font: TextFont = TextFont.Sans,
    val bold: Boolean = true,
    /** A rounded label behind the text, or null for none. */
    val pill: Int? = null,
    val imageUri: String? = null,
) {
    val isText: Boolean get() = text != null

    fun visibleAt(timeMs: Long): Boolean = timeMs in startMs until endMs

    /** The text as a design text layer, so the photo editor's renderer draws it the same way. */
    fun toTextLayer(): TextLayer = TextLayer(id, rect, text.orEmpty(), size, argb, TextAlign.Center, bold, font, pill)

    companion object {
        const val DEFAULT_TEXT_SIZE = 0.05
        const val MIN_TEXT_SIZE = 0.025
        const val MAX_TEXT_SIZE = 0.14
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}

/** Edits of a timeline's overlays. Times stay at least [VideoTimeline.MIN_PIECE_MS] apart and never negative. */
object VideoOverlays {

    const val MAX_OVERLAYS = 20
    const val DEFAULT_SHOW_MS = 3_000L

    /** A new text overlay across the middle of the frame, showing from [atMs] for a few seconds. */
    fun addText(timeline: VideoTimeline, text: String, atMs: Long): VideoTimeline {
        if (timeline.overlays.size >= MAX_OVERLAYS) return timeline
        val rect = NRect(0.08, 0.42, 0.92, 0.58)
        return add(timeline, VideoOverlay(nextId(timeline), rect, 0, 0, text = text), atMs)
    }

    /**
     * A new picture overlay, [imageAspect] wide per unit tall, sized to a third of the frame's width and centred.
     * Its rect has the picture's shape in the current frame.
     */
    fun addImage(timeline: VideoTimeline, uri: String, imageAspect: Double, atMs: Long): VideoTimeline {
        if (timeline.overlays.size >= MAX_OVERLAYS) return timeline
        val frame = timeline.shape.size.aspect
        var w = 0.34
        var h = w * frame / imageAspect
        if (h > 0.6) {
            w *= 0.6 / h
            h = 0.6
        }
        val rect = NRect(0.5 - w / 2, 0.5 - h / 2, 0.5 + w / 2, 0.5 + h / 2)
        return add(timeline, VideoOverlay(nextId(timeline), rect, 0, 0, imageUri = uri), atMs)
    }

    private fun add(timeline: VideoTimeline, overlay: VideoOverlay, atMs: Long): VideoTimeline {
        val end = max(timeline.totalMs, VideoTimeline.MIN_PIECE_MS)
        val start = atMs.coerceIn(0, max(0, end - VideoTimeline.MIN_PIECE_MS))
        val stop = min(end, start + DEFAULT_SHOW_MS).coerceAtLeast(start + VideoTimeline.MIN_PIECE_MS)
        return timeline.copy(overlays = timeline.overlays + overlay.copy(startMs = start, endMs = stop))
    }

    private fun nextId(timeline: VideoTimeline): Long = (timeline.overlays.maxOfOrNull { it.id } ?: 0L) + 1

    fun update(timeline: VideoTimeline, id: Long, change: (VideoOverlay) -> VideoOverlay): VideoTimeline =
        timeline.copy(overlays = timeline.overlays.map { if (it.id == id) change(it) else it })

    fun remove(timeline: VideoTimeline, id: Long): VideoTimeline = timeline.copy(overlays = timeline.overlays.filterNot { it.id == id })

    /** Shows the overlay from [start] to [end], kept inside the video and at least the shortest piece long. */
    fun setTimes(timeline: VideoTimeline, id: Long, start: Long, end: Long): VideoTimeline = update(timeline, id) { o ->
        val total = max(timeline.totalMs, VideoTimeline.MIN_PIECE_MS)
        val s = start.coerceIn(0, total - VideoTimeline.MIN_PIECE_MS)
        o.copy(startMs = s, endMs = end.coerceIn(s + VideoTimeline.MIN_PIECE_MS, total))
    }

    /** Moves the overlay by a fraction of the frame, keeping it on the frame. */
    fun move(timeline: VideoTimeline, id: Long, dx: Double, dy: Double): VideoTimeline = update(timeline, id) { o ->
        val r = o.rect
        val x = dx.coerceIn(-r.left, 1 - r.right)
        val y = dy.coerceIn(-r.top, 1 - r.bottom)
        o.copy(rect = NRect(r.left + x, r.top + y, r.right + x, r.bottom + y))
    }

    /** Scales a picture overlay about its centre by [k], keeping its shape and keeping it on the frame. */
    fun scale(timeline: VideoTimeline, id: Long, k: Double): VideoTimeline = update(timeline, id) { o ->
        val r = o.rect
        val w = r.right - r.left
        val h = r.bottom - r.top
        // Never past the frame and never smaller than a twentieth of it, either way keeping the shape.
        val fit = min(1.0 / w, 1.0 / h)
        val least = max(0.05 / w, 0.05 / h)
        val f = k.coerceIn(least, fit)
        val nw = w * f
        val nh = h * f
        val cx = ((r.left + r.right) / 2).coerceIn(nw / 2, 1 - nw / 2)
        val cy = ((r.top + r.bottom) / 2).coerceIn(nh / 2, 1 - nh / 2)
        o.copy(rect = NRect(cx - nw / 2, cy - nh / 2, cx + nw / 2, cy + nh / 2))
    }

    /** Overlays showing at [timeMs], bottom to top. */
    fun visibleAt(timeline: VideoTimeline, timeMs: Long): List<VideoOverlay> = timeline.overlays.filter { it.visibleAt(timeMs) }

    /** When the overlays on screen change, so an encoder redraws only then. */
    fun changeTimes(timeline: VideoTimeline): List<Long> =
        timeline.overlays.flatMap { listOf(it.startMs, it.endMs) }.distinct().sorted()
}
