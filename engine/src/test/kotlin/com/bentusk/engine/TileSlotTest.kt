package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileSlotTest {
    private val carousel = Design.blank(TemplateKind.Carousel, 3, PostRatio.Portrait)

    @Test
    fun `photos fill slides one each, starting from the slide in view`() {
        var design = carousel
        val first = design.emptyTile(from = 1)!!
        assertEquals(1, first)
        design = design.withTileSlot(first, PhotoLayer(1, NRect(0.0, 0.0, 1.0, 1.0), uri = "a"))
        assertEquals(NRect(1.0 / 3, 0.0, 2.0 / 3, 1.0), design.layer(1)!!.rect)
        assertEquals(2, design.emptyTile(from = 1))
        design = design.withTileSlot(2, PhotoLayer(2, NRect(0.0, 0.0, 1.0, 1.0), uri = "b"))
        // Wraps round to the slides before the one in view.
        assertEquals(0, design.emptyTile(from = 1))
        design = design.withTileSlot(0, PhotoLayer(3, NRect(0.0, 0.0, 1.0, 1.0), uri = "c"))
        assertNull(design.emptyTile(from = 1))
    }

    @Test
    fun `a photo spanning slides covers them all`() {
        val panorama = carousel.add(PhotoLayer(1, NRect(0.0, 0.0, 1.0, 1.0), uri = "wide"))
        assertNull(panorama.emptyTile())
    }

    @Test
    fun `new slots go under text`() {
        val text = TextLayer(1, NRect(0.1, 0.1, 0.3, 0.2), "Hi", 0.05, 0)
        val design = carousel.add(text).withTileSlot(0, PhotoLayer(2, NRect(0.0, 0.0, 1.0, 1.0), free = true))
        assertEquals(listOf(2L, 1L), design.layers.map { it.id })
        assertEquals(false, (design.layer(2) as PhotoLayer).free)
    }

    @Test
    fun `a post grows by one slide or row up to its limit`() {
        val grown = carousel.add(PhotoLayer(1, NRect(0.0, 0.0, 1.0 / 3, 1.0), uri = "a")).grown()!!
        assertEquals(4, grown.count)
        // The photo stays on the first slide, which is now a quarter of the canvas.
        assertEquals(0.25, grown.layer(1)!!.rect.right, 1e-9)
        assertEquals(1, grown.emptyTile(from = 0))
        assertNull(carousel.grown(maxSlides = 3))
        assertNull(Design.blank(TemplateKind.Grid, InstagramSpec.MAX_GRID_ROWS, PostRatio.Tall).grown())
        assertEquals(2, Design.blank(TemplateKind.Grid, 1, PostRatio.Tall).grown()!!.count)
        assertNull(Design.blank(TemplateKind.Story, 1, PostRatio.Portrait).grown())
    }
}
