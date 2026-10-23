package com.bentusk.engine

/**
 * Every Instagram dimension the app depends on lives here, so a platform change is a one-file update.
 * Feed posts are 1080 px wide; the profile grid shows posts as 3:4 thumbnails.
 */
object InstagramSpec {
    const val FEED_WIDTH = 1080
    const val MAX_CAROUSEL_SLIDES = 20
    const val MIN_CAROUSEL_SLIDES = 2
    const val GRID_COLUMNS = 3
    const val MAX_GRID_ROWS = 5

    val StorySize = PxSize(1080, 1920)

    /** Shape of a post's thumbnail on the profile grid. */
    val GridThumb = PxSize(3, 4)

    /** Posts a profile can pin to the top of its grid. */
    const val MAX_PINNED = 3

    const val CAPTION_MAX_CHARS = 2200

    /** Instagram has limited posts to five hashtags since December 2025. */
    const val MAX_HASHTAGS = 5

    /** Captions are cut off after about this many characters in the feed, so the hook must fit before it. */
    const val CAPTION_PREVIEW_CHARS = 125

    /** Diameter of the circle Instagram crops highlight covers to, as a fraction of story width. */
    const val HIGHLIGHT_CIRCLE_FRACTION = 0.62
}

/** Post shapes Instagram accepts for feed images and carousels. */
enum class PostRatio(val label: String, val size: PxSize) {
    Portrait("4:5", PxSize(1080, 1350)),
    Tall("3:4", PxSize(1080, 1440)),
    Square("1:1", PxSize(1080, 1080)),
    ;

    companion object {
        val CarouselDefault = Portrait
        val GridDefault = Tall
    }
}
