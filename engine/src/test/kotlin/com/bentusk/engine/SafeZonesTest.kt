package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeZonesTest {

    @Test
    fun `grid crop trims the sides of a 4 by 5 post`() {
        val r = SafeZones.crop(PostRatio.Portrait, InstagramSpec.GridThumb)
        // 3:4 is 0.75 wide, 4:5 is 0.8: the thumbnail keeps 0.75 / 0.8 = 93.75% of the width.
        assertEquals(0.03125, r.left, 1e-9)
        assertEquals(0.96875, r.right, 1e-9)
        assertEquals(0.0, r.top, 1e-9)
        assertEquals(1.0, r.bottom, 1e-9)
    }

    @Test
    fun `a 3 by 4 post shows whole on the grid`() {
        assertEquals(NRect(0.0, 0.0, 1.0, 1.0), SafeZones.crop(PostRatio.Tall, InstagramSpec.GridThumb))
    }

    @Test
    fun `square view trims the top and bottom of tall posts`() {
        val r = SafeZones.crop(PostRatio.Portrait, PxSize(1, 1))
        assertEquals(0.1, r.top, 1e-9)
        assertEquals(0.9, r.bottom, 1e-9)
        val square = SafeZones.coverCrops(PostRatio.Square).first { it.kind == ZoneKind.SquareCrop }
        assertEquals(NRect(0.0, 0.0, 1.0, 1.0), square.rect)
    }

    @Test
    fun `overlay icons keep the same on-screen size whatever the post shape`() {
        for (ratio in PostRatio.entries) {
            val counter = SafeZones.overlays(ratio, carousel = true).first { it.kind == ZoneKind.SlideCounter }.rect
            val heightPx = (counter.bottom - counter.top) * ratio.size.height
            val widthPx = (counter.right - counter.left) * ratio.size.width
            assertEquals(0.065 * 1080, heightPx, 1e-6)
            assertEquals(0.14 * 1080, widthPx, 1e-6)
            assertTrue(counter.right < 1.0 && counter.top > 0.0)
        }
    }

    @Test
    fun `single posts have no slide counter`() {
        assertFalse(SafeZones.overlays(PostRatio.Portrait, carousel = false).any { it.kind == ZoneKind.SlideCounter })
    }

    @Test
    fun `pinned posts lead the grid in their own order`() {
        val items = listOf("a" to false, "b" to true, "c" to false, "d" to true)
        assertEquals(listOf("b", "d", "a", "c"), PinnedGrid.order(items) { it.second }.map { it.first })
        assertTrue(PinnedGrid.canPin(2))
        assertFalse(PinnedGrid.canPin(InstagramSpec.MAX_PINNED))
    }
}
