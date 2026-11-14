package com.bentusk.engine

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How one piece of a video maker timeline hands over to the next. */
@Serializable
enum class CutTransition {
    /** A straight cut. */
    None,

    /** Dips through black. */
    Fade,

    /** Dips through white, like a camera flash. */
    Flash,

    /** Pushes in on the outgoing piece and settles back out on the incoming one. */
    Zoom,

    /** A fast sideways swipe: the outgoing piece leaves to the left, the incoming one arrives from the right. */
    Whip,
}

/**
 * What a transition does to one frame: the picture is scaled by [scale] about its centre and shifted sideways by
 * [shift] (a fraction of the frame's width, positive to the right), then covered by [dip], an ARGB colour whose
 * alpha says how strongly. Text and pictures on top are drawn after this, so they stay put.
 */
data class TransitionFrame(val scale: Double = 1.0, val shift: Double = 0.0, val dip: Int = 0) {
    val dipAlpha: Int get() = dip ushr 24
    val isStill: Boolean get() = scale == 1.0 && shift == 0.0 && dipAlpha == 0

    companion object {
        val Still = TransitionFrame()
    }
}

/** A cut between two pieces at [atMs] on the timeline, its transition playing from [atMs] - [halfMs] to [atMs] + [halfMs]. */
data class TimelineCut(val atMs: Long, val transition: CutTransition, val halfMs: Long)

/** Pure timing for transitions, shared by the preview and the encoder so both show the same frames. */
object VideoTransitions {

    /** Each transition takes this long either side of its cut, or less when a piece is short. */
    const val HALF_MS = 300L

    /** How long the fade in at the start and the fade out at the end take. */
    const val END_FADE_MS = 600L

    const val ZOOM = 0.3
    const val WHIP = 0.6

    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF

    /** The cuts that have a transition, in timeline order. */
    fun cuts(timeline: VideoTimeline): List<TimelineCut> {
        val out = ArrayList<TimelineCut>()
        var t = 0L
        timeline.clips.forEachIndexed { i, c ->
            if (i > 0 && c.transition != CutTransition.None) {
                val before = timeline.clips[i - 1]
                out += TimelineCut(t, c.transition, minOf(HALF_MS, before.lengthMs / 2, c.lengthMs / 2))
            }
            t += c.lengthMs
        }
        return out
    }

    /** True when anything in the timeline moves or dims the picture, so the encoder needs to draw transitions. */
    fun any(timeline: VideoTimeline): Boolean =
        !timeline.isEmpty && (timeline.fadeEnds || timeline.clips.drop(1).any { it.transition != CutTransition.None })

    /** The frame at [timeMs] on the timeline. */
    fun frame(timeline: VideoTimeline, timeMs: Long): TransitionFrame = frame(timeline, cuts(timeline), timeMs)

    /** As [frame], with the [cuts] worked out once, for callers that ask for every frame. */
    fun frame(timeline: VideoTimeline, cuts: List<TimelineCut>, timeMs: Long): TransitionFrame {
        var scale = 1.0
        var shift = 0.0
        var dipColor = BLACK
        var dip = 0.0
        for (cut in cuts) {
            if (cut.halfMs <= 0) continue
            val d = (timeMs - cut.atMs).toDouble() / cut.halfMs
            if (d <= -1.0 || d >= 1.0) continue
            // 0 at either end of the transition, 1 right on the cut.
            val w = 1 - abs(d)
            when (cut.transition) {
                CutTransition.None -> Unit
                CutTransition.Fade -> dip = smooth(w)
                CutTransition.Flash -> {
                    dipColor = WHITE
                    dip = smooth(w)
                }
                CutTransition.Zoom -> {
                    scale = 1 + ZOOM * w * w
                    dipColor = WHITE
                    dip = 0.35 * w * w * w * w
                }
                CutTransition.Whip -> {
                    shift = (if (d < 0) -WHIP else WHIP) * w * w
                    dip = 0.85 * w * w
                }
            }
            break
        }
        if (timeline.fadeEnds && !timeline.isEmpty) {
            val total = timeline.totalMs
            val length = min(END_FADE_MS, max(1L, total / 4))
            val edge = when {
                timeMs < length -> 1 - timeMs.toDouble() / length
                timeMs > total - length -> 1 - (total - timeMs).toDouble() / length
                else -> 0.0
            }.coerceIn(0.0, 1.0)
            if (edge > dip) {
                dip = edge
                dipColor = BLACK
            }
        }
        val alpha = (dip * 255).toInt().coerceIn(0, 255)
        if (scale == 1.0 && shift == 0.0 && alpha == 0) return TransitionFrame.Still
        return TransitionFrame(scale, shift, if (alpha == 0) 0 else (alpha shl 24) or dipColor)
    }

    private fun smooth(x: Double) = x * x * (3 - 2 * x)
}
