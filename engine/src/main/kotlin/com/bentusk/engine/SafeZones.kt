package com.bentusk.engine

/** What covers or cuts a part of a post once it is on Instagram. */
enum class ZoneKind {
    /** The "1/10" pill Instagram draws over the top-right corner of a carousel. */
    SlideCounter,

    /** The person icon in the bottom-left corner when people are tagged. */
    TagIcon,

    /** What the profile grid shows of the post: a centred 3:4 thumbnail. */
    GridCrop,

    /** A centred square, still used by some embeds, shares and older app versions. */
    SquareCrop,

    /** A story's progress bar and account name, and the top of a Reel's screen. */
    ScreenTop,

    /** A Reel's caption and account name, or a story's reply bar. */
    ScreenBottom,

    /** A Reel's like, comment, share and audio buttons down the right edge. */
    ReelButtons,
}

data class SafeZone(val kind: ZoneKind, val rect: NRect)

/**
 * Where Instagram's own interface sits on top of a feed post, so creators can keep text and faces clear of it.
 *
 * Overlay icons are a fixed size on screen, and a feed post always fills the screen width, so they are measured
 * in fractions of the post's width and converted for each post shape. The values are approximate measurements of
 * the Android and iOS apps and live here so an Instagram redesign is a one-file update.
 */
object SafeZones {
    /** Slide counter pill: inset from the top-right corner, in post widths. */
    private const val COUNTER_INSET = 0.03
    private const val COUNTER_WIDTH = 0.14
    private const val COUNTER_HEIGHT = 0.065

    /** Tagged-people icon: inset from the bottom-left corner, in post widths. */
    private const val TAG_INSET = 0.03
    private const val TAG_SIZE = 0.08

    fun overlays(ratio: PostRatio, carousel: Boolean): List<SafeZone> = overlays(ratio.size.aspect, carousel)

    /** Zones for one slide or tile [aspect] (width / height). Carousels show the slide counter; single posts do not. */
    fun overlays(aspect: Double, carousel: Boolean): List<SafeZone> {
        // Converts a length in post widths to a fraction of the post's height.
        fun h(widths: Double) = widths * aspect
        return buildList {
            if (carousel) {
                add(
                    SafeZone(
                        ZoneKind.SlideCounter,
                        NRect(1 - COUNTER_INSET - COUNTER_WIDTH, h(COUNTER_INSET), 1 - COUNTER_INSET, h(COUNTER_INSET + COUNTER_HEIGHT)),
                    ),
                )
            }
            add(SafeZone(ZoneKind.TagIcon, NRect(TAG_INSET, 1 - h(TAG_INSET + TAG_SIZE), TAG_INSET + TAG_SIZE, 1 - h(TAG_INSET))))
        }
    }

    /** The centred part of a post of shape [post] that a thumbnail of shape [thumb] shows, as fractions of the post. */
    fun crop(post: PostRatio, thumb: PxSize): NRect = crop(post.size.aspect, thumb.aspect)

    fun crop(postAspect: Double, thumbAspect: Double): NRect {
        val p = postAspect
        val t = thumbAspect
        return if (t < p - 1e-9) {
            // Thumbnail is narrower: the sides are cut.
            val w = t / p
            NRect((1 - w) / 2, 0.0, (1 + w) / 2, 1.0)
        } else {
            val hh = (p / t).coerceAtMost(1.0)
            NRect(0.0, (1 - hh) / 2, 1.0, (1 + hh) / 2)
        }
    }

    /** Top band covered on a 9:16 story or Reel, in screen heights. */
    private const val SCREEN_TOP = 0.13

    /** Bottom band covered by a Reel's caption or a story's reply bar, in screen heights. */
    private const val SCREEN_BOTTOM = 0.20

    /** The Reel button column, in screen widths and heights. */
    private const val BUTTONS_WIDTH = 0.13
    private const val BUTTONS_TOP = 0.45

    /** What Instagram draws over a full-screen 9:16 story or Reel. Approximate, like the feed overlays. */
    fun story(): List<SafeZone> = listOf(
        SafeZone(ZoneKind.ScreenTop, NRect(0.0, 0.0, 1.0, SCREEN_TOP)),
        SafeZone(ZoneKind.ScreenBottom, NRect(0.0, 1 - SCREEN_BOTTOM, 1.0, 1.0)),
        SafeZone(ZoneKind.ReelButtons, NRect(1 - BUTTONS_WIDTH, BUTTONS_TOP, 1.0, 1 - SCREEN_BOTTOM)),
    )

    /** The part of a Reel cover the profile grid shows: a centred 3:4. */
    fun storyCrops(): List<SafeZone> =
        listOf(SafeZone(ZoneKind.GridCrop, crop(InstagramSpec.StorySize.aspect, InstagramSpec.GridThumb.aspect)))

    /** Both crops for the cover of a post: the profile grid (3:4) and the square view. */
    fun coverCrops(post: PostRatio): List<SafeZone> = coverCrops(post.size.aspect)

    fun coverCrops(postAspect: Double): List<SafeZone> = listOf(
        SafeZone(ZoneKind.GridCrop, crop(postAspect, InstagramSpec.GridThumb.aspect)),
        SafeZone(ZoneKind.SquareCrop, crop(postAspect, 1.0)),
    )
}
