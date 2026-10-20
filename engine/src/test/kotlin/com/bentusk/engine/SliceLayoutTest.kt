package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SliceLayoutTest {

    @Test
    fun `carousel tiles cover the canvas exactly once`() {
        for (ratio in PostRatio.entries) {
            for (slides in InstagramSpec.MIN_CAROUSEL_SLIDES..InstagramSpec.MAX_CAROUSEL_SLIDES) {
                assertPartition(CarouselLayout(slides, ratio))
            }
        }
    }

    @Test
    fun `grid tiles cover the canvas exactly once`() {
        for (ratio in PostRatio.entries) {
            for (rows in 1..InstagramSpec.MAX_GRID_ROWS) assertPartition(GridLayout(rows, ratio))
        }
    }

    @Test
    fun `carousel is posted left to right`() {
        val layout = CarouselLayout(5, PostRatio.Portrait)
        assertEquals(PxSize(5400, 1350), layout.canvas)
        assertEquals(listOf(0, 1, 2, 3, 4), layout.postingSequence.map { it.column })
    }

    @Test
    fun `grid is posted from bottom right to top left`() {
        val layout = GridLayout(3, PostRatio.Tall)
        assertEquals(PxSize(3240, 4320), layout.canvas)
        val first = layout.postingSequence.first()
        val last = layout.postingSequence.last()
        assertEquals(2 to 2, first.row to first.column)
        assertEquals(0 to 0, last.row to last.column)
        assertEquals((1..9).toList(), layout.postingSequence.map { it.postOrder })
    }

    @Test
    fun `out of range counts are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { CarouselLayout(1, PostRatio.Square) }
        assertThrows(IllegalArgumentException::class.java) { CarouselLayout(21, PostRatio.Square) }
        assertThrows(IllegalArgumentException::class.java) { GridLayout(0, PostRatio.Square) }
    }

    @Test
    fun `file names sort in posting order`() {
        val layout = GridLayout(4, PostRatio.Tall)
        val names = layout.tiles.map { ExportNaming.tileFileName("grid", "20261020", it, layout.count, "jpg") }
        val sortedByName = layout.tiles.zip(names).sortedBy { it.second }.map { it.first.postOrder }
        assertEquals((1..12).toList(), sortedByName)
        assertEquals("grid_20261020_01of12.jpg", ExportNaming.tileFileName("grid", "20261020", layout.postingSequence.first(), 12, "jpg"))
    }

    private fun assertPartition(layout: SliceLayout) {
        val canvas = layout.canvas
        val hits = IntArray(canvas.width * canvas.height)
        // Sampling every pixel of a 20-slide canvas is slow; stride through rows and check every column.
        for (tile in layout.tiles) {
            assertEquals(layout.tileSize, tile.rect.size)
            for (y in (tile.rect.top + 6) / 7 * 7 until tile.rect.bottom step 7) {
                for (x in tile.rect.left until tile.rect.right) hits[y * canvas.width + x]++
            }
        }
        for (y in 0 until canvas.height step 7) {
            for (x in 0 until canvas.width) {
                assertEquals("pixel $x,$y covered once", 1, hits[y * canvas.width + x])
            }
        }
        assertTrue(layout.tiles.map { it.postOrder }.sorted() == (1..layout.count).toList())
    }
}
