package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/** Shapes the video maker exports, all 1080 wide. */
enum class VideoShape(val label: String, val size: PxSize) {
    Reel("9:16", InstagramSpec.StorySize),
    Portrait("4:5", PostRatio.Portrait.size),
    Square("1:1", PostRatio.Square.size),
}

/**
 * One piece of a video maker timeline: part of a video clip ([startMs]..[endMs] of a [durationMs]-long file), or a
 * photo held for [endMs] - [startMs]. Every piece is scaled to cover the frame, so nothing is letterboxed.
 */
@Serializable
data class TimelineClip(
    val id: Long,
    val uri: String,
    val photo: Boolean,
    val durationMs: Long,
    val startMs: Long = 0,
    val endMs: Long = durationMs,
    val muted: Boolean = false,
    /** How this piece comes in from the one before it. Ignored on the first piece. */
    val transition: CutTransition = CutTransition.None,
) {
    val lengthMs: Long get() = endMs - startMs
}

/**
 * A video built from clips and photos played one after another. Pure data and edits; the app previews and encodes
 * it. Every edit keeps each piece at least [VideoTimeline.MIN_PIECE_MS] long and inside its source.
 */
@Serializable
data class VideoTimeline(
    val clips: List<TimelineClip> = emptyList(),
    val shape: VideoShape = VideoShape.Reel,
    /** Text and pictures over the video, bottom to top. */
    val overlays: List<VideoOverlay> = emptyList(),
    /** Fades in from black at the start and out to black at the end. */
    val fadeEnds: Boolean = false,
) {
    val totalMs: Long get() = clips.sumOf { it.lengthMs }

    val isEmpty: Boolean get() = clips.isEmpty()

    /** True when the video is longer than Instagram takes for a Reel, so the export button can warn. */
    val tooLong: Boolean get() = totalMs > MAX_TOTAL_MS

    val canAdd: Boolean get() = clips.size < MAX_CLIPS

    fun clip(id: Long): TimelineClip? = clips.firstOrNull { it.id == id }

    private fun nextId(): Long = (clips.maxOfOrNull { it.id } ?: 0L) + 1

    /** Adds a video clip at the end (or after [afterId]), played whole. */
    fun addVideo(uri: String, durationMs: Long, afterId: Long? = null): VideoTimeline =
        insert(TimelineClip(nextId(), uri, photo = false, durationMs = max(durationMs, MIN_PIECE_MS)), afterId)

    /** Adds a photo shown for [holdMs] at the end (or after [afterId]). */
    fun addPhoto(uri: String, holdMs: Long = PHOTO_DEFAULT_MS, afterId: Long? = null): VideoTimeline {
        val hold = holdMs.coerceIn(MIN_PIECE_MS, PHOTO_MAX_MS)
        return insert(TimelineClip(nextId(), uri, photo = true, durationMs = PHOTO_MAX_MS, endMs = hold), afterId)
    }

    private fun insert(clip: TimelineClip, afterId: Long?): VideoTimeline {
        if (!canAdd) return this
        val at = afterId?.let { id -> clips.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) } ?: clips.size
        return copy(clips = clips.toMutableList().apply { add(at, clip) })
    }

    fun remove(id: Long): VideoTimeline = copy(clips = clips.filterNot { it.id == id })

    /** Moves a piece one place earlier ([by] = -1) or later (+1), or further. */
    fun move(id: Long, by: Int): VideoTimeline {
        val from = clips.indexOfFirst { it.id == id }
        if (from < 0) return this
        val to = (from + by).coerceIn(0, clips.size - 1)
        if (to == from) return this
        return copy(clips = clips.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Trims a video clip to [start]..[end] of its file, or sets a photo's time on screen to [end] - [start]. */
    fun trim(id: Long, start: Long, end: Long): VideoTimeline = update(id) { c ->
        if (c.photo) {
            c.copy(startMs = 0, endMs = (end - start).coerceIn(MIN_PIECE_MS, PHOTO_MAX_MS))
        } else {
            val shortest = min(MIN_PIECE_MS, c.durationMs)
            val s = start.coerceIn(0, max(0, c.durationMs - shortest))
            c.copy(startMs = s, endMs = end.coerceIn(s + shortest, c.durationMs))
        }
    }

    /** Sets how piece [id] comes in from the one before it. */
    fun setTransition(id: Long, transition: CutTransition): VideoTimeline = update(id) { it.copy(transition = transition) }

    /** Uses [transition] on every cut between pieces. */
    fun setAllTransitions(transition: CutTransition): VideoTimeline =
        copy(clips = clips.mapIndexed { i, c -> if (i == 0) c else c.copy(transition = transition) })

    fun setMuted(id: Long, muted: Boolean): VideoTimeline = update(id) { if (it.photo) it else it.copy(muted = muted) }

    /** Copies a piece and puts the copy right after it. */
    fun duplicate(id: Long): VideoTimeline {
        val c = clip(id) ?: return this
        return insert(c.copy(id = nextId()), id)
    }

    /**
     * Cuts the video clip [id] in two at [atMs] into the clip's own played part (0 is its first frame on the
     * timeline). Both halves keep the same file and sound settings. No-op when either half would be too short.
     */
    fun split(id: Long, atMs: Long): VideoTimeline {
        val index = clips.indexOfFirst { it.id == id }
        if (index < 0) return this
        val c = clips[index]
        if (c.photo || atMs < MIN_PIECE_MS || c.lengthMs - atMs < MIN_PIECE_MS) return this
        val cut = c.startMs + atMs
        val first = c.copy(endMs = cut)
        // A split is a cut inside one shot, so the second half comes straight in.
        val second = c.copy(id = nextId(), startMs = cut, transition = CutTransition.None)
        return copy(clips = clips.toMutableList().apply { set(index, first); add(index + 1, second) })
    }

    /** Where piece [id] starts on the timeline, in milliseconds. */
    fun startOf(id: Long): Long? {
        var t = 0L
        for (c in clips) {
            if (c.id == id) return t
            t += c.lengthMs
        }
        return null
    }

    /** The piece playing at [timeMs] on the timeline and how far into it, or null past the end. */
    fun at(timeMs: Long): Pair<TimelineClip, Long>? {
        if (timeMs < 0) return null
        var t = 0L
        for (c in clips) {
            if (timeMs < t + c.lengthMs) return c to (timeMs - t)
            t += c.lengthMs
        }
        return null
    }

    private fun update(id: Long, change: (TimelineClip) -> TimelineClip) =
        copy(clips = clips.map { if (it.id == id) change(it) else it })

    companion object {
        const val MIN_PIECE_MS = 500L
        const val PHOTO_DEFAULT_MS = 3_000L
        const val PHOTO_MAX_MS = 10_000L
        const val MAX_CLIPS = 30

        /** Instagram takes Reels of up to three minutes from the app. */
        const val MAX_TOTAL_MS = 180_000L
    }
}
