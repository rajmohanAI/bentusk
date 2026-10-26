package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioShotTest {

    private fun mask(w: Int, h: Int, box: PxRect): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (x >= box.left && x < box.right && y >= box.top && y < box.bottom) 255 else 0
    }

    @Test
    fun findsTheSubjectBox() {
        val box = PxRect(10, 20, 30, 45)
        assertEquals(box, StudioShot.subjectBounds(mask(50, 60, box), 50, 60))
    }

    @Test
    fun faintEdgesAreNotSubject() {
        val alpha = IntArray(16) { 40 }
        assertNull(StudioShot.subjectBounds(alpha, 4, 4))
    }

    @Test
    fun aStandingProductSitsOnTheFloorWithAShadow() {
        val bounds = PxRect(100, 50, 300, 450)
        val out = PxSize(1080, 1350)
        val p = StudioShot.place(bounds, 600, out)
        val subjectBottom = p.top + bounds.bottom * p.scale
        val subjectTop = p.top + bounds.top * p.scale
        assertEquals(out.height * StudioShot.FLOOR, subjectBottom, 1e-6)
        assertEquals(out.height * StudioShot.GROUNDED_HEIGHT, subjectBottom - subjectTop, 1e-6)
        val centre = p.left + (bounds.left + bounds.right) / 2.0 * p.scale
        assertEquals(out.width / 2.0, centre, 1e-6)
        assertNotNull(p.shadow)
        val shadow = p.shadow!!
        assertTrue(shadow.top < subjectBottom && shadow.bottom > subjectBottom)
    }

    @Test
    fun aWideSubjectIsLimitedByWidth() {
        val bounds = PxRect(0, 200, 1000, 400)
        val out = PxSize(1080, 1080)
        val p = StudioShot.place(bounds, 1000, out)
        assertEquals(out.width * StudioShot.MAX_WIDTH, bounds.width * p.scale, 1e-6)
    }

    @Test
    fun aPortraitCutAtTheWaistStaysOnTheBottomEdge() {
        val bounds = PxRect(200, 100, 800, 1000)
        assertTrue(StudioShot.cropped(bounds, 1000))
        val out = PxSize(1080, 1350)
        val p = StudioShot.place(bounds, 1000, out)
        assertNull(p.shadow)
        assertEquals(out.height.toDouble(), p.top + bounds.bottom * p.scale, 1e-6)
    }

    @Test
    fun smallCutOutsAreNotBlownUp() {
        val bounds = PxRect(0, 0, 20, 30)
        val p = StudioShot.place(bounds, 200, PxSize(1080, 1350))
        assertEquals(StudioShot.MAX_SCALE, p.scale, 1e-9)
    }

    @Test
    fun outputKeepsTheFrameShape() {
        assertEquals(PxSize(1080, 1350), StudioShot.outputSize(PxSize(1080, 1350)))
        val big = StudioShot.outputSize(PxSize(3240, 1350))
        assertEquals(StudioShot.MAX_SIDE, big.width)
        assertEquals(3240.0 / 1350.0, big.aspect, 0.01)
    }

    @Test
    fun backdropsAreUniqueAndKnowWhenTheyAreDark() {
        assertEquals(StudioShot.Backdrops.size, StudioShot.Backdrops.map { it.id }.toSet().size)
        assertFalse(StudioShot.backdrop("white")!!.dark)
        assertTrue(StudioShot.backdrop("charcoal")!!.dark)
        assertTrue(StudioShot.backdrop("midnight")!!.dark)
    }
}
