package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {

    @Test
    fun `ids are unique`() {
        assertEquals(Templates.All.size, Templates.All.map { it.id }.toSet().size)
    }

    @Test
    fun `every template has a valid layout and at most ten photos`() {
        for (t in Templates.All) {
            val layout = t.layout()
            assertTrue(t.id, t.photos.size <= 10)
            for (i in t.photos.indices) {
                val r = t.slotRect(i, layout)
                assertTrue("${t.id} slot $i inside canvas", r.left >= 0 && r.top >= 0 && r.right <= layout.canvas.width && r.bottom <= layout.canvas.height)
            }
        }
    }

    @Test
    fun `photo slots never overlap`() {
        for (t in Templates.All) {
            val layout = t.layout()
            val rects = t.photos.indices.map { t.slotRect(it, layout) }
            for (a in rects.indices) for (b in a + 1 until rects.size) {
                val x = rects[a]
                val y = rects[b]
                val overlap = x.left < y.right && y.left < x.right && x.top < y.bottom && y.top < x.bottom
                assertFalse("${t.id} slots $a and $b overlap", overlap)
            }
        }
    }

    @Test
    fun `grid slots line up with tile edges`() {
        for (t in Templates.Grid) {
            val layout = t.layout()
            for (i in t.photos.indices) {
                val r = t.slotRect(i, layout)
                assertEquals(t.id, 0, r.left % layout.tileSize.width)
                assertEquals(t.id, 0, r.top % layout.tileSize.height)
                assertEquals(t.id, 0, r.right % layout.tileSize.width)
                assertEquals(t.id, 0, r.bottom % layout.tileSize.height)
            }
        }
    }

    @Test
    fun `seamless templates carry a photo across a slide edge`() {
        for (id in listOf("seamless-duo", "seamless-trio", "bridge")) {
            val t = Templates.byId(id)!!
            val layout = t.layout()
            val w = layout.tileSize.width
            assertTrue(id, t.photos.indices.any { t.slotRect(it, layout).let { r -> r.left / w != (r.right - 1) / w } })
        }
    }

    @Test
    fun `slot framing covers its slot and lands on the canvas`() {
        val t = Templates.byId("seamless-trio")!!
        val layout = t.layout()
        val slot = t.slotRect(0, layout)
        val geometry = SourceGeometry(PxSize(4000, 3000), ExifOrientation.Rotate90)
        val transform = SlotMath.decodedToCanvas(geometry, geometry.raw, slot, Framing.Default)
        val inverse = transform.inverse()
        for (corner in listOf(Vec2(slot.left.toDouble(), slot.top.toDouble()), Vec2(slot.right.toDouble(), slot.bottom.toDouble()))) {
            val p = inverse.map(corner)
            assertTrue(p.x >= -1e-6 && p.x <= 4000 + 1e-6 && p.y >= -1e-6 && p.y <= 3000 + 1e-6)
        }
        assertEquals(listOf(0, 1), t.slotsOn(layout.tiles[0].rect, layout))
    }

    @Test
    fun `every carousel template sits in a gallery theme`() {
        assertTrue(Templates.Carousel.all { it.category in Templates.CarouselCategories })
        assertEquals(Templates.Carousel.size, Templates.CarouselByCategory.sumOf { it.second.size })
    }

    @Test
    fun `round corners on square slots make circles`() {
        val t = Templates.byId("circle-stories")!!
        val layout = t.layout()
        val r = t.slotRect(0, layout)
        assertTrue(kotlin.math.abs(r.width - r.height) <= 1)
        assertTrue(kotlin.math.abs(t.cornerPx(0, layout) * 2 - r.width) <= 2)
    }
}
