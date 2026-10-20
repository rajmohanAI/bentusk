package com.bentusk.engine

/** One output image cut from the layout canvas. */
data class Tile(
    /** Position in the layout, row-major from the top-left. */
    val index: Int,
    val row: Int,
    val column: Int,
    /** Region of the layout canvas this tile covers. */
    val rect: PxRect,
    /** 1-based order in which the user posts this tile. */
    val postOrder: Int,
)

/** A canvas split into tiles of identical size with no gaps and no overlap. */
sealed interface SliceLayout {
    val tileSize: PxSize
    val columns: Int
    val rows: Int
    val tiles: List<Tile>

    val canvas: PxSize get() = PxSize(tileSize.width * columns, tileSize.height * rows)
    val count: Int get() = columns * rows

    /** Tiles sorted in the order the user should post them. */
    val postingSequence: List<Tile> get() = tiles.sortedBy { it.postOrder }
}

/**
 * A wide image split into side-by-side slides that line up edge to edge when swiped.
 * Slides are posted in a single carousel, left to right.
 */
data class CarouselLayout(val slides: Int, val ratio: PostRatio) : SliceLayout {
    init {
        require(slides in InstagramSpec.MIN_CAROUSEL_SLIDES..InstagramSpec.MAX_CAROUSEL_SLIDES) {
            "Carousel needs ${InstagramSpec.MIN_CAROUSEL_SLIDES}-${InstagramSpec.MAX_CAROUSEL_SLIDES} slides, got $slides"
        }
    }

    override val tileSize get() = ratio.size
    override val columns get() = slides
    override val rows get() = 1
    override val tiles: List<Tile> = buildTiles(columns, rows, tileSize) { index -> index + 1 }
}

/**
 * A photo split into a 3-column grid that reassembles on the profile page.
 * Instagram shows the newest post first, so the bottom-right tile is posted first and the top-left last.
 */
data class GridLayout(override val rows: Int, val ratio: PostRatio) : SliceLayout {
    init {
        require(rows in 1..InstagramSpec.MAX_GRID_ROWS) { "Grid needs 1-${InstagramSpec.MAX_GRID_ROWS} rows, got $rows" }
    }

    override val tileSize get() = ratio.size
    override val columns get() = InstagramSpec.GRID_COLUMNS
    override val tiles: List<Tile> = buildTiles(columns, rows, tileSize) { index -> columns * rows - index }
}

private fun buildTiles(columns: Int, rows: Int, tile: PxSize, postOrder: (Int) -> Int): List<Tile> =
    List(columns * rows) { index ->
        val row = index / columns
        val column = index % columns
        val left = column * tile.width
        val top = row * tile.height
        Tile(
            index = index,
            row = row,
            column = column,
            rect = PxRect(left, top, left + tile.width, top + tile.height),
            postOrder = postOrder(index),
        )
    }
