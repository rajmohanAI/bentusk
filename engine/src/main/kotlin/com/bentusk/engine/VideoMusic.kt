package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlin.math.min

/**
 * A song or sound the user picked from their phone, played under a video maker timeline from [startMs] into the
 * file, looping from its beginning if the video runs longer. [volume] is 0..1; [fade] eases it in and out.
 */
@Serializable
data class VideoMusic(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val startMs: Long = 0,
    val volume: Double = VideoSoundtrack.DEFAULT_VOLUME,
    val fade: Boolean = true,
)

/** One stretch of the music file, [fromMs]..[toMs], played from [atMs] on the timeline. */
data class MusicPiece(val fromMs: Long, val toMs: Long, val atMs: Long) {
    val lengthMs: Long get() = toMs - fromMs
}

/** Pure timing and loudness for a timeline's music, shared by the preview and the encoder. */
object VideoSoundtrack {

    const val DEFAULT_VOLUME = 0.8
    const val FADE_MS = 1_500L

    /** Files shorter than this are not worth looping. */
    const val MIN_MUSIC_MS = 1_000L

    fun add(timeline: VideoTimeline, uri: String, name: String, durationMs: Long): VideoTimeline =
        if (durationMs < MIN_MUSIC_MS) timeline else timeline.copy(music = VideoMusic(uri, name, durationMs))

    fun remove(timeline: VideoTimeline): VideoTimeline = timeline.copy(music = null)

    /** Starts the music [startMs] into its file, leaving at least a second to play. */
    fun setStart(timeline: VideoTimeline, startMs: Long): VideoTimeline = change(timeline) {
        it.copy(startMs = startMs.coerceIn(0, it.durationMs - MIN_MUSIC_MS))
    }

    fun setVolume(timeline: VideoTimeline, volume: Double): VideoTimeline = change(timeline) { it.copy(volume = volume.coerceIn(0.0, 1.0)) }

    fun setFade(timeline: VideoTimeline, fade: Boolean): VideoTimeline = change(timeline) { it.copy(fade = fade) }

    fun setClipVolume(timeline: VideoTimeline, volume: Double): VideoTimeline = timeline.copy(clipVolume = volume.coerceIn(0.0, 1.0))

    private fun change(timeline: VideoTimeline, f: (VideoMusic) -> VideoMusic) = timeline.music?.let { timeline.copy(music = f(it)) } ?: timeline

    /** The stretches of the music file that cover the whole video, in order: from the start point, then looping. */
    fun pieces(timeline: VideoTimeline): List<MusicPiece> {
        val m = timeline.music ?: return emptyList()
        if (m.durationMs < MIN_MUSIC_MS) return emptyList()
        val total = timeline.totalMs
        val out = ArrayList<MusicPiece>()
        var at = 0L
        var from = m.startMs.coerceIn(0, m.durationMs - MIN_MUSIC_MS)
        while (at < total) {
            val length = min(m.durationMs - from, total - at)
            out += MusicPiece(from, from + length, at)
            at += length
            from = 0
        }
        return out
    }

    /** Where in the music file the video is at [timeMs], or null when there is no music then. */
    fun songPosition(timeline: VideoTimeline, timeMs: Long): Long? =
        pieces(timeline).firstOrNull { timeMs >= it.atMs && timeMs < it.atMs + it.lengthMs }?.let { it.fromMs + (timeMs - it.atMs) }

    /** How loud the music plays at [timeMs], 0..1: its volume, eased in at the start and out at the end. */
    fun gainAt(timeline: VideoTimeline, timeMs: Long): Double {
        val m = timeline.music ?: return 0.0
        val total = timeline.totalMs
        if (timeMs < 0 || timeMs >= total) return 0.0
        if (!m.fade) return m.volume
        val length = min(FADE_MS, total / 4).coerceAtLeast(1)
        val edge = min(timeMs.toDouble() / length, (total - timeMs).toDouble() / length).coerceIn(0.0, 1.0)
        return m.volume * edge
    }
}
