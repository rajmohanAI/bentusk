package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DesignTest {

    private fun near(a: Double, b: Double) = abs(a - b) < 1e-9

    @Test
    fun `templates become designs with the same slots`() {
        for (t in Templates.All) {
            val d = t.toDesign()
            val layout = d.layout()
            assertEquals(t.photos.size, d.photos.size)
            t.photos.indices.forEach { assertEquals(t.slotRect(it, layout), d.rectPx(d.photos[it], layout)) }
            assertEquals(d.layers.size, d.layers.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `adding a slide keeps layers where they were in pixels`() {
        val d = Templates.byId("seamless-trio")!!.toDesign()
        val before = d.layers.map { d.rectPx(it) }
        val more = d.withCount(5)
        val after = more.layers.map { more.rectPx(it) }
        before.zip(after).forEach { (a, b) ->
            assertTrue("$a vs $b", abs(a.left - b.left) <= 1 && abs(a.right - b.right) <= 1 && a.top == b.top)
        }
    }

    @Test
    fun `removing slides drops layers that no longer fit`() {
        val d = Templates.byId("photo-dump")!!.toDesign()
        val fewer = d.withCount(2)
        assertEquals(2, fewer.layers.size)
    }

    @Test
    fun `moving stays on the canvas`() {
        val d = Templates.byId("before-after")!!.toDesign()
        val text = d.layers.last()
        val moved = d.move(text.id, 5.0, -5.0).layer(text.id)!!.rect
        assertTrue(near(moved.right, 1.0))
        assertTrue(near(moved.top, 0.0))
    }

    @Test
    fun `scaling text grows its font and keeps its centre`() {
        val d = Templates.byId("before-after")!!.toDesign()
        val text = d.layers.last() as TextLayer
        val r = text.rect
        val centre = Vec2((r.left + r.right) / 2, (r.top + r.bottom) / 2)
        val scaled = d.scale(text.id, 0.5, centre, 0.8).layer(text.id) as TextLayer
        assertTrue(near(scaled.size, text.size * 0.5))
        assertTrue(near((scaled.rect.left + scaled.rect.right) / 2, centre.x))
    }

    @Test
    fun `free photo keeps the photo's shape in pixels`() {
        val d = Design.blank(TemplateKind.Carousel, 3, PostRatio.Portrait)
        val rect = Design.freePhotoRect(d, 1, 4.0 / 3.0)
        val px = rect.toPx(d.layout().canvas)
        assertTrue(abs(px.width / px.height.toDouble() - 4.0 / 3.0) < 0.01)
        val tile = d.layout().tiles[1].rect
        assertTrue(px.left >= tile.left && px.right <= tile.right)
    }

    @Test
    fun `designs survive a round trip through json`() {
        val d = Templates.byId("magazine")!!.toDesign()
            .replace(PhotoLayer(1, NRect(0.0, 0.0, 0.3, 1.0), uri = "content://x/1", framing = Framing(10.0, 1.5, Vec2(3.0, -4.0))))
        assertEquals(d, Design.fromJson(Design.toJson(d)))
    }

    @Test
    fun `hit finds the topmost layer`() {
        val d = Templates.byId("before-after")!!.toDesign()
        val layout = d.layout()
        val text = d.layers.last()
        val r = d.rectPx(text, layout)
        assertEquals(text.id, d.hit(Vec2(r.left + 2.0, r.top + 2.0), layout)?.id)
    }
}
