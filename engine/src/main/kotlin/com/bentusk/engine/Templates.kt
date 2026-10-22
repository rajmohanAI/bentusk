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
    ) = Template(id, name, TemplateKind.Carousel, slides, PostRatio.Portrait, background, photos, texts, premium)

    private fun grid(
        id: String,
        name: String,
        rows: Int,
        background: Int,
        photos: List<PhotoSlot>,
        texts: List<TextSlot> = emptyList(),
        premium: Boolean = false,
    ) = Template(id, name, TemplateKind.Grid, rows, PostRatio.Tall, background, photos, texts, premium)

    val Carousel: List<Template> = listOf(
        carousel(
            "photo-dump", "Photo dump", 5, CREAM,
            List(5) { i -> PhotoSlot(c(5, i + MX, MY, i + 1 - MX, 1 - MY), corner = 0.02) },
        ),
        carousel(
            "seamless-duo", "Seamless duo", 2, WHITE,
            listOf(PhotoSlot(c(2, 0.0, 0.0, 1.29, 1.0)), PhotoSlot(c(2, 1.31, 0.0, 2.0, 1.0))),
        ),
        carousel(
            "seamless-trio", "Seamless trio", 3, CREAM,
            listOf(
                PhotoSlot(c(3, MX, MY, 2 - MX, 0.6), corner = 0.02),
                PhotoSlot(c(3, MX, 0.64, 1 - MX, 1 - MY), corner = 0.02),
                PhotoSlot(c(3, 1 + MX, 0.64, 3 - MX, 1 - MY), corner = 0.02),
            ),
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
        ),
        carousel(
            "bridge", "Bridge", 3, CREAM,
            listOf(
                PhotoSlot(c(3, MX, 0.3, 3 - MX, 0.7), corner = 0.02),
                PhotoSlot(c(3, MX, MY, 1 - MX, 0.27), corner = 0.02),
                PhotoSlot(c(3, 2 + MX, 0.73, 3 - MX, 1 - MY), corner = 0.02),
            ),
            listOf(TextSlot(c(3, 1 + MX, 0.1, 2 - MX, 0.22), "Your story", 0.06, INK)),
        ),
        carousel(
            "polaroid", "Polaroid", 4, MIST,
            List(4) { i -> PhotoSlot(c(4, i + 0.1, 0.1, i + 0.9, 0.72)) },
            List(4) { i -> TextSlot(c(4, i + 0.1, 0.76, i + 0.9, 0.86), "Caption ${i + 1}", 0.04, INK, bold = false) },
        ),
        carousel(
            "film-strip", "Film strip", 4, BLACK,
            List(4) { i -> PhotoSlot(c(4, i + 0.015, 0.2, i + 0.985, 0.8)) },
            listOf(TextSlot(c(4, MX, 0.08, 1 - MX, 0.15), "ROLL 01", 0.04, WHITE, TextAlign.Start)),
            premium = true,
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
    )

    val All: List<Template> = Carousel + Grid

    fun byId(id: String): Template? = All.firstOrNull { it.id == id }
}
