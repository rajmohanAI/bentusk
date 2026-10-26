package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickersTest {
    private val carousel = Design.blank(TemplateKind.Carousel, 3, PostRatio.Portrait)

    @Test
    fun `seam arrow is cut in half by the seam`() {
        val r = Stickers.rect(carousel, 0, StickerPlacement.SeamArrow)
        val seam = carousel.tileRect(0).right
        assertEquals(seam - r.left, r.right - seam, 1e-9)
        assertTrue(r.left < seam && r.right > seam)
    }

    @Test
    fun `seam stickers stay inside the last slide`() {
        val last = carousel.tileRect(2)
        for (p in listOf(StickerPlacement.SeamArrow, StickerPlacement.SeamText)) {
            val r = Stickers.rect(carousel, 2, p)
            assertTrue(r.left >= last.left && r.right <= last.right)
        }
    }

    @Test
    fun `grid tiles only run onto a neighbour in the same row`() {
        val grid = Design.blank(TemplateKind.Grid, 2, PostRatio.Tall)
        assertTrue(Stickers.hasRightNeighbour(grid, 1))
        assertTrue(!Stickers.hasRightNeighbour(grid, 2))
        val r = Stickers.rect(grid, 2, StickerPlacement.SeamArrow)
        assertTrue(r.right <= grid.tileRect(2).right)
    }

    @Test
    fun `corner pills and banners sit inside their slide`() {
        for (page in 0 until 3) {
            val t = carousel.tileRect(page)
            for (p in listOf(StickerPlacement.CornerPill, StickerPlacement.Banner)) {
                val r = Stickers.rect(carousel, page, p)
                assertTrue(r.left >= t.left && r.right <= t.right && r.top >= t.top && r.bottom <= t.bottom)
            }
        }
    }

    @Test
    fun `a sticker is an ordinary editable text layer with a fresh id`() {
        val layer = Stickers.layer(carousel, 1, StickerPlacement.Banner, "Save this for later", 0xFF000000.toInt())
        assertEquals(carousel.nextId(), layer.id)
        assertEquals("Save this for later", layer.text)
        assertTrue(layer.pill != null)
    }
}
