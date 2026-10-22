package com.bentusk.engine

/**
 * Built-in templates. Coordinates are written in slide units for carousels (x from 0 to slides) and tile units
 * for grids, then normalised, so each layout reads like the picture it draws.
 */
object Templates {
    private const val CREAM = 0xFFF4EFE6.toInt()
    private const val INK = 0xFF1B1C20.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val MIST = 0xFFE9E6E1.toInt()
    private const val BLUSH = 0xFFF6D9D5.toInt()
    private const val SAGE = 0xFFD5DFCF.toInt()
    private const val SAND = 0xFFF3E3B5.toInt()
    private const val TERRACOTTA = 0xFFC96F53.toInt()
    private const val NAVY = 0xFF1F3B5C.toInt()
    private const val GOLD = 0xFFE6C17E.toInt()

    const val TRENDING = "Trending"
    const val PHOTO_DUMP = "Photo dump"
    const val SEAMLESS = "Seamless"
    const val TRAVEL = "Travel"
    const val MINIMAL = "Minimal"
    const val BUSINESS = "Business"
    const val GRID = "Grid layouts"

    /** Carousel themes in the order the gallery shows them. */
    val CarouselCategories = listOf(TRENDING, PHOTO_DUMP, SEAMLESS, TRAVEL, MINIMAL, BUSINESS)

    // Portrait slides are 1080x1350, so a margin of 0.06 slide widths is 0.048 slide heights.
    private const val MX = 0.06
    private const val MY = 0.048

    /** Carousel rect in slide units: x across all [n] slides, y within the slide height (0..1). */
    private fun c(n: Int, l: Double, t: Double, r: Double, b: Double) = NRect(l / n, t, r / n, b)

    /** Grid rect in tile units: 3 columns, [rows] rows. */
    private fun g(rows: Int, l: Double, t: Double, r: Double, b: Double) = NRect(l / 3, t / rows, r / 3, b / rows)

    private fun carousel(
        id: String,
        name: String,
        slides: Int,
        background: Int,
        photos: List<PhotoSlot>,
        texts: List<TextSlot> = emptyList(),
        premium: Boolean = false,
        category: String = "",
    ) = Template(id, name, TemplateKind.Carousel, slides, PostRatio.Portrait, background, photos, texts, premium, category)

    private fun grid(
        id: String,
        name: String,
        rows: Int,
        background: Int,
        photos: List<PhotoSlot>,
        texts: List<TextSlot> = emptyList(),
        premium: Boolean = false,
    ) = Template(id, name, TemplateKind.Grid, rows, PostRatio.Tall, background, photos, texts, premium, GRID)

    val Carousel: List<Template> = listOf(
        carousel(
            "photo-dump", "Photo dump", 5, CREAM,
            List(5) { i -> PhotoSlot(c(5, i + MX, MY, i + 1 - MX, 1 - MY), corner = 0.02) },
            category = PHOTO_DUMP,
        ),
        carousel(
            "seamless-duo", "Seamless duo", 2, WHITE,
            listOf(PhotoSlot(c(2, 0.0, 0.0, 1.29, 1.0)), PhotoSlot(c(2, 1.31, 0.0, 2.0, 1.0))),
            category = SEAMLESS,
        ),
        carousel(
            "seamless-trio", "Seamless trio", 3, CREAM,
            listOf(
                PhotoSlot(c(3, MX, MY, 2 - MX, 0.6), corner = 0.02),
                PhotoSlot(c(3, MX, 0.64, 1 - MX, 1 - MY), corner = 0.02),
                PhotoSlot(c(3, 1 + MX, 0.64, 3 - MX, 1 - MY), corner = 0.02),
            ),
            category = SEAMLESS,
        ),
        carousel(
            "before-after", "Before / after", 2, WHITE,
            listOf(
                PhotoSlot(c(2, MX, MY, 1 - MX, 0.84), corner = 0.02),
                PhotoSlot(c(2, 1 + MX, MY, 2 - MX, 0.84), corner = 0.02),
            ),
            listOf(
                TextSlot(c(2, MX, 0.86, 1 - MX, 0.96), "BEFORE", 0.05, INK),
                TextSlot(c(2, 1 + MX, 0.86, 2 - MX, 0.96), "AFTER", 0.05, INK),
            ),
            category = MINIMAL,
        ),
        carousel(
            "bridge", "Bridge", 3, CREAM,
            listOf(
                PhotoSlot(c(3, MX, 0.3, 3 - MX, 0.7), corner = 0.02),
                PhotoSlot(c(3, MX, MY, 1 - MX, 0.27), corner = 0.02),
                PhotoSlot(c(3, 2 + MX, 0.73, 3 - MX, 1 - MY), corner = 0.02),
            ),
            listOf(TextSlot(c(3, 1 + MX, 0.1, 2 - MX, 0.22), "Your story", 0.06, INK)),
            category = SEAMLESS,
        ),
        carousel(
            "polaroid", "Polaroid", 4, MIST,
            List(4) { i -> PhotoSlot(c(4, i + 0.1, 0.1, i + 0.9, 0.72)) },
            List(4) { i -> TextSlot(c(4, i + 0.1, 0.76, i + 0.9, 0.86), "Caption ${i + 1}", 0.04, INK, bold = false) },
            category = PHOTO_DUMP,
        ),
        carousel(
            "film-strip", "Film strip", 4, BLACK,
            List(4) { i -> PhotoSlot(c(4, i + 0.015, 0.2, i + 0.985, 0.8)) },
            listOf(TextSlot(c(4, MX, 0.08, 1 - MX, 0.15), "ROLL 01", 0.04, WHITE, TextAlign.Start)),
            premium = true,
            category = TRENDING,
        ),
        carousel(
            "hero-grid", "Hero + grid", 3, WHITE,
            buildList {
                add(PhotoSlot(c(3, 0.0, 0.0, 1.0, 1.0)))
                for (slide in 1..2) for (row in 0..1) for (col in 0..1) {
                    val l = slide + MX + col * (0.5 - MX * 0.5)
                    val t = MY + row * (0.5 - MY * 0.5)
                    add(PhotoSlot(c(3, l, t, l + 0.5 - MX * 1.5, t + 0.5 - MY * 1.5), corner = 0.015))
                }
            },
            premium = true,
            category = PHOTO_DUMP,
        ),
        carousel(
            "magazine", "Magazine", 3, WHITE,
            listOf(
                PhotoSlot(c(3, 0.0, 0.0, 1.0, 1.0)),
                PhotoSlot(c(3, 1 + MX, MY, 2 - MX, 0.6)),
                PhotoSlot(c(3, 2 + MX, MY, 3 - MX, 1 - MY)),
            ),
            listOf(
                TextSlot(c(3, MX, 0.06, 1 - MX, 0.2), "THE EDIT", 0.1, WHITE),
                TextSlot(c(3, 1 + MX, 0.66, 2 - MX, 0.94), "Write the story behind this post here.", 0.04, INK, TextAlign.Start, bold = false),
            ),
            premium = true,
            category = TRENDING,
        ),
        carousel(
            "mood-board", "Mood board", 2, BLUSH,
            listOf(
                PhotoSlot(c(2, MX, 0.2, 1 - MX, 0.62), corner = 0.03),
                PhotoSlot(c(2, MX, 0.65, 0.485, 1 - MY), corner = 0.03),
                PhotoSlot(c(2, 0.515, 0.65, 1 - MX, 1 - MY), corner = 0.03),
                PhotoSlot(c(2, 1 + MX, MY, 1.485, 0.49), corner = 0.03),
                PhotoSlot(c(2, 1.515, MY, 2 - MX, 0.49), corner = 0.03),
                PhotoSlot(c(2, 1 + MX, 0.51, 1.485, 1 - MY), corner = 0.03),
                PhotoSlot(c(2, 1.515, 0.51, 2 - MX, 1 - MY), corner = 0.03),
            ),
            listOf(TextSlot(c(2, MX, 0.05, 1 - MX, 0.17), "my current mood:", 0.06, INK, font = TextFont.Script, bold = false)),
            category = TRENDING,
        ),
        carousel(
            "circle-stories", "Story circles", 3, WHITE,
            buildList {
                for (k in 0..2) add(PhotoSlot(c(3, 0.06 + k * 0.31, 0.06, 0.32 + k * 0.31, 0.06 + 0.26 * 0.8), corner = 0.13))
                add(PhotoSlot(c(3, MX, 0.3, 1 - MX, 1 - MY), corner = 0.02))
                add(PhotoSlot(c(3, 1.0, 0.0, 2.0, 1.0)))
                add(PhotoSlot(c(3, 2.0, 0.0, 3.0, 1.0)))
            },
            premium = true,
            category = TRENDING,
        ),
        carousel(
            "grid-dump", "Nine + one", 2, CREAM,
            buildList {
                val g = 0.02
                val w = (1 - 2 * MX - 2 * g) / 3
                val h = (1 - 2 * MY - 2 * g) / 3
                for (row in 0..2) for (col in 0..2) {
                    val l = MX + col * (w + g)
                    val t = MY + row * (h + g)
                    add(PhotoSlot(c(2, l, t, l + w, t + h), corner = 0.015))
                }
                add(PhotoSlot(c(2, 1 + MX, MY, 2 - MX, 1 - MY), corner = 0.02))
            },
            category = PHOTO_DUMP,
        ),
        carousel(
            "daytrip", "Day trip", 4, WHITE,
            List(4) { i -> PhotoSlot(c(4, i.toDouble(), 0.0, i + 1.0, 1.0)) },
            List(4) { i ->
                TextSlot(c(4, i + 0.06, 0.05, i + 0.62, 0.12), "Stop ${i + 1}", 0.035, INK, TextAlign.Start, pill = WHITE)
            },
            category = TRAVEL,
        ),
        carousel(
            "postcard", "Postcard", 3, SAND,
            listOf(
                PhotoSlot(c(3, MX, 0.22, 1 - MX, 1 - MY)),
                PhotoSlot(c(3, 1.08, 0.08, 1.92, 0.82)),
                PhotoSlot(c(3, 2.08, 0.08, 2.92, 0.82)),
            ),
            listOf(
                TextSlot(c(3, MX, 0.04, 1 - MX, 0.11), "Greetings from", 0.05, INK, font = TextFont.Script, bold = false),
                TextSlot(c(3, MX, 0.11, 1 - MX, 0.2), "GOA", 0.09, TERRACOTTA),
                TextSlot(c(3, 1.08, 0.85, 1.92, 0.94), "Wish you were here", 0.045, INK, font = TextFont.Script, bold = false),
                TextSlot(c(3, 2.08, 0.85, 2.92, 0.94), "See you soon", 0.045, INK, font = TextFont.Script, bold = false),
            ),
            category = TRAVEL,
        ),
        carousel(
            "itinerary", "Itinerary", 4, NAVY,
            List(3) { k -> val i = k + 1; PhotoSlot(c(4, i + MX, MY + 0.1, i + 1 - MX, 1 - MY), corner = 0.02) },
            buildList {
                add(TextSlot(c(4, MX, 0.3, 1 - MX, 0.4), "3 DAYS IN", 0.06, WHITE))
                add(TextSlot(c(4, MX, 0.4, 1 - MX, 0.58), "JAIPUR", 0.14, GOLD, font = TextFont.Serif))
                add(TextSlot(c(4, MX, 0.84, 1 - MX, 0.92), "swipe →", 0.035, WHITE, bold = false))
                for (i in 1..3) add(TextSlot(c(4, i + MX, MY, i + 0.45, MY + 0.08), "DAY $i", 0.035, NAVY, TextAlign.Start, pill = GOLD))
            },
            premium = true,
            category = TRAVEL,
        ),
        carousel(
            "panorama-caption", "Panorama + words", 2, CREAM,
            listOf(PhotoSlot(c(2, 0.0, 0.0, 2.0, 0.78))),
            listOf(TextSlot(c(2, MX, 0.82, 2 - MX, 0.95), "Somewhere new", 0.06, INK, font = TextFont.Serif)),
            category = SEAMLESS,
        ),
        carousel(
            "gallery-wall", "Gallery wall", 3, WHITE,
            List(3) { i -> PhotoSlot(c(3, i + 0.15, 0.14, i + 0.85, 0.74)) },
            List(3) { i -> TextSlot(c(3, i + 0.15, 0.78, i + 0.85, 0.86), "No. ${i + 1}", 0.03, INK, font = TextFont.Serif, bold = false) },
            category = MINIMAL,
        ),
        carousel(
            "quote-cards", "Quote cards", 2, BLUSH,
            emptyList(),
            listOf(
                TextSlot(c(2, MX, 0.3, 1 - MX, 0.7), "“Collect moments, not things.”", 0.075, INK, font = TextFont.Serif),
                TextSlot(c(2, 1 + MX, 0.42, 2 - MX, 0.58), "— your name", 0.06, INK, font = TextFont.Script, bold = false),
            ),
            category = MINIMAL,
        ),
        carousel(
            "product-hero", "Product launch", 3, WHITE,
            listOf(
                PhotoSlot(c(3, 0.0, 0.0, 1.0, 1.0)),
                PhotoSlot(c(3, 1 + MX, MY, 2 - MX, 0.49), corner = 0.02),
                PhotoSlot(c(3, 1 + MX, 0.51, 2 - MX, 1 - MY), corner = 0.02),
                PhotoSlot(c(3, 2 + MX, MY, 3 - MX, 0.62), corner = 0.02),
            ),
            listOf(
                TextSlot(c(3, 0.25, 0.06, 0.75, 0.13), "NEW DROP", 0.04, WHITE, pill = INK),
                TextSlot(c(3, 2 + MX, 0.65, 3 - MX, 0.75), "Linen shirt", 0.06, INK, font = TextFont.Serif),
                TextSlot(c(3, 2 + MX, 0.75, 3 - MX, 0.85), "₹1,499", 0.08, TERRACOTTA),
                TextSlot(c(3, 2.2, 0.87, 2.8, 0.94), "Shop now · link in bio", 0.03, WHITE, pill = INK),
            ),
            category = BUSINESS,
        ),
        carousel(
            "tips", "Tips", 3, SAGE,
            listOf(
                PhotoSlot(c(3, 1 + MX, MY, 2 - MX, 0.55), corner = 0.02),
                PhotoSlot(c(3, 2 + MX, MY, 3 - MX, 0.55), corner = 0.02),
            ),
            listOf(
                TextSlot(c(3, MX, 0.28, 1 - MX, 0.62), "3 tips for better photos", 0.09, INK, font = TextFont.Serif),
                TextSlot(c(3, MX, 0.84, 1 - MX, 0.92), "save this for later", 0.035, INK, bold = false),
                TextSlot(c(3, 1 + MX, 0.59, 2 - MX, 0.68), "1. Shoot at golden hour", 0.05, INK, TextAlign.Start),
                TextSlot(c(3, 1 + MX, 0.69, 2 - MX, 0.9), "Soft light just before sunset flatters every face.", 0.038, INK, TextAlign.Start, bold = false),
                TextSlot(c(3, 2 + MX, 0.59, 3 - MX, 0.68), "2. Keep the horizon level", 0.05, INK, TextAlign.Start),
                TextSlot(c(3, 2 + MX, 0.69, 3 - MX, 0.9), "Turn on grid lines and line the horizon up with one.", 0.038, INK, TextAlign.Start, bold = false),
            ),
            premium = true,
            category = BUSINESS,
        ),
    )

    val Grid: List<Template> = listOf(
        grid(
            "row-panoramas", "Row panoramas", 3, WHITE,
            List(3) { r -> PhotoSlot(g(3, 0.0, r.toDouble(), 3.0, r + 1.0)) },
        ),
        grid(
            "column-stripes", "Column stripes", 3, WHITE,
            List(3) { col -> PhotoSlot(g(3, col.toDouble(), 0.0, col + 1.0, 3.0)) },
        ),
        grid(
            "checkerboard", "Checkerboard", 3, CREAM,
            (0 until 9).filter { (it / 3 + it % 3) % 2 == 0 }.map { i ->
                PhotoSlot(g(3, (i % 3).toDouble(), (i / 3).toDouble(), i % 3 + 1.0, i / 3 + 1.0))
            },
            (0 until 9).filter { (it / 3 + it % 3) % 2 == 1 }.mapIndexed { n, i ->
                val words = listOf("NEW", "IN", "SHOP", "NOW")
                TextSlot(g(3, i % 3 + 0.1, i / 3 + 0.4, i % 3 + 0.9, i / 3 + 0.6), words[n], 0.12, INK)
            },
        ),
        grid(
            "centre-stage", "Centre stage", 3, WHITE,
            buildList {
                add(PhotoSlot(g(3, 0.0, 1.0, 3.0, 2.0)))
                for (row in listOf(0, 2)) for (col in 0..2) {
                    add(PhotoSlot(g(3, col.toDouble(), row.toDouble(), col + 1.0, row + 1.0)))
                }
            },
        ),
        grid(
            "frame", "Frame", 3, CREAM,
            (0 until 9).filter { it != 4 }.map { i ->
                PhotoSlot(g(3, (i % 3).toDouble(), (i / 3).toDouble(), i % 3 + 1.0, i / 3 + 1.0))
            },
            listOf(TextSlot(g(3, 1.1, 1.35, 1.9, 1.65), "Your brand", 0.1, INK)),
            premium = true,
        ),
        grid(
            "hero-corner", "Hero corner", 4, WHITE,
            buildList {
                add(PhotoSlot(g(4, 0.0, 0.0, 2.0, 2.0)))
                add(PhotoSlot(g(4, 2.0, 0.0, 3.0, 1.0)))
                add(PhotoSlot(g(4, 2.0, 1.0, 3.0, 2.0)))
                for (row in 2..3) for (col in 0..2) {
                    add(PhotoSlot(g(4, col.toDouble(), row.toDouble(), col + 1.0, row + 1.0)))
                }
            },
            premium = true,
        ),
        grid(
            "headline-row", "Headline row", 2, CREAM,
            listOf(PhotoSlot(g(2, 0.0, 0.0, 3.0, 1.0)), PhotoSlot(g(2, 1.0, 1.0, 2.0, 2.0))),
            listOf(
                TextSlot(g(2, 0.1, 1.35, 0.9, 1.65), "NEW", 0.14, INK, font = TextFont.Serif),
                TextSlot(g(2, 2.1, 1.35, 2.9, 1.65), "SEASON", 0.1, INK, font = TextFont.Serif),
            ),
        ),
        grid(
            "pillars", "Pillars", 3, WHITE,
            listOf(
                PhotoSlot(g(3, 0.0, 0.0, 1.0, 3.0)),
                PhotoSlot(g(3, 1.0, 0.0, 2.0, 1.0)),
                PhotoSlot(g(3, 1.0, 1.0, 2.0, 2.0)),
                PhotoSlot(g(3, 1.0, 2.0, 2.0, 3.0)),
                PhotoSlot(g(3, 2.0, 0.0, 3.0, 3.0)),
            ),
        ),
    )

    val All: List<Template> = Carousel + Grid

    /** Carousel templates grouped by theme, in gallery order. */
    val CarouselByCategory: List<Pair<String, List<Template>>>
        get() = CarouselCategories.map { cat -> cat to Carousel.filter { it.category == cat } }.filter { it.second.isNotEmpty() }

    fun byId(id: String): Template? = All.firstOrNull { it.id == id }
}
