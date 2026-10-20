package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodePlanTest {

    private val photo50mp = PxSize(8160, 6120)

    @Test
    fun `never subsamples below the output resolution`() {
        for (scale in listOf(1.0, 0.75, 0.5, 0.49, 0.26, 0.25, 0.1)) {
            val plan = DecodePlan.forRender(photo50mp, scale, Long.MAX_VALUE)
            assertTrue("scale $scale sample ${plan.sampleSize}", scale * plan.sampleSize <= 1.0)
            assertTrue("scale $scale sample ${plan.sampleSize}", scale * plan.sampleSize * 2 > 1.0)
        }
    }

    @Test
    fun `memory budget forces further subsampling`() {
        val plan = DecodePlan.forRender(photo50mp, 1.0, 10_000_000)
        assertEquals(4, plan.sampleSize)
        assertTrue(plan.decoded.pixels <= 10_000_000)
        assertEquals(2, DecodePlan.forRender(photo50mp, 1.0, 16_000_000).sampleSize)
    }

    @Test
    fun `decoded size rounds up like BitmapFactory`() {
        assertEquals(PxSize(3, 2), DecodePlan.decodedSize(PxSize(9, 5), 4))
    }

    @Test
    fun `preview keeps at least the requested long side`() {
        val plan = DecodePlan.forPreview(photo50mp, 2048)
        assertEquals(2, plan.sampleSize)
        assertTrue(plan.decoded.width >= 2048)
    }

    @Test
    fun `reorder moves items`() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a"), listOf("a").moved(0, 5))
    }

    @Test
    fun `cover icon is centred inside the circle`() {
        val cover = CoverLayout(iconFraction = 0.5)
        val icon = cover.iconRect
        val circle = cover.circle
        assertEquals(1080 - icon.right, icon.left)
        assertEquals(1920 - icon.bottom, icon.top)
        assertTrue(icon.left >= circle.left && icon.right <= circle.right)
    }
}
