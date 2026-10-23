package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Which part of a video clip a slide plays, and whether its sound is kept. Times are in milliseconds. */
@Serializable
data class VideoClip(
    val durationMs: Long,
    val startMs: Long = 0,
    val endMs: Long = min(durationMs, InstagramSpec.CAROUSEL_VIDEO_MAX_MS),
    val muted: Boolean = false,
) {
    val lengthMs: Long get() = endMs - startMs

    /**
     * The clip trimmed to [start]..[end], kept inside the video and between the shortest and longest allowed lengths.
     * When the range is too long, the end the user moved stays where they put it and the other end follows.
     */
    fun trimmed(start: Long, end: Long): VideoClip {
        val shortest = min(InstagramSpec.VIDEO_MIN_MS, durationMs)
        var s = start.coerceIn(0, max(0, durationMs - shortest))
        var e = end.coerceIn(s + shortest, durationMs)
        if (e - s > InstagramSpec.CAROUSEL_VIDEO_MAX_MS) {
            if (start != startMs) e = s + InstagramSpec.CAROUSEL_VIDEO_MAX_MS else s = e - InstagramSpec.CAROUSEL_VIDEO_MAX_MS
        }
        return copy(startMs = s, endMs = e)
    }
}

/**
 * Video slides: a clip fills one whole slide, under that slide's text and stickers, and the slide exports as a video
 * while the others stay photos. Framing is zoom and pan only, so preview and encoder agree exactly.
 */
object VideoSlides {

    /** The video layer filling slide [tile], if there is one. */
    fun videoOn(design: Design, tile: Int): PhotoLayer? {
        val r = design.tileRect(tile)
        return design.photos.firstOrNull { it.video != null && sameRect(it.rect, r) }
    }

    /** Whether any slide of [design] is a video. */
    fun hasVideo(design: Design): Boolean = design.photos.any { it.video != null }

    /** Puts a clip on slide [page], replacing any clip already there, beneath everything else on the slide. */
    fun place(design: Design, page: Int, uri: String, durationMs: Long): Design {
        val cleared = videoOn(design, page)?.let { design.remove(it.id) } ?: design
        val layer = PhotoLayer(id = cleared.nextId(), rect = design.tileRect(page), uri = uri, video = VideoClip(durationMs))
        return cleared.copy(layers = listOf<Layer>(layer) + cleared.layers)
    }

    /** Video framing: no rotation, and never zoomed out past the slide's edges. */
    fun clamp(source: PxSize, slide: PxSize, framing: Framing): Framing =
        FramingMath.clamp(source, slide, Framing(0.0, max(1.0, framing.zoom), framing.offset))

    /**
     * The zoom and pan of [framing] as a matrix on normalised device coordinates (x right, y up, -1..1), applied after
     * the clip has been scaled to cover a [slide]-sized frame. Values are in android.graphics.Matrix order.
     */
    fun ndcMatrix(framing: Framing, slide: PxSize): FloatArray {
        val z = framing.zoom
        val tx = 2 * framing.offset.x / slide.width
        val ty = -2 * framing.offset.y / slide.height
        return Affine(a = z, tx = tx, d = z, ty = ty).toMatrixValues()
    }

    private fun sameRect(a: NRect, b: NRect) =
        listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom).all { abs(it) < 1e-6 }
}
