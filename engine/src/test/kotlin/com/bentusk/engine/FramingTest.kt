package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FramingTest {

    private val photo = PxSize(4000, 3000)
    private val canvas = CarouselLayout(3, PostRatio.Portrait).canvas

    @Test
    fun `default framing covers the canvas with no background`() {
        val f = FramingMath.clamp(photo, canvas, Framing.Default)
        assertFalse(FramingMath.needsBackground(photo, canvas, f))
        assertCovers(f)
    }

    @Test
    fun `clamped framing always covers the canvas at any rotation and pan`() {
        val random = Random(42)
        repeat(500) {
            val raw = Framing(
                rotationDegrees = random.nextDouble(-180.0, 180.0),
                zoom = random.nextDouble(1.0, 4.0),
                offset = Vec2(random.nextDouble(-5000.0, 5000.0), random.nextDouble(-5000.0, 5000.0)),
            )
            assertCovers(FramingMath.clamp(photo, canvas, raw))
        }
    }

    @Test
    fun `zooming out stops at fit and exposes the background`() {
        val f = FramingMath.clamp(photo, canvas, Framing(zoom = 0.01))
        assertEquals(FramingMath.minZoom(photo, canvas, 0.0), f.zoom, 1e-12)
        assertTrue(FramingMath.needsBackground(photo, canvas, f))
        // At fit, the whole photo is inside the canvas.
        val t = FramingMath.transform(photo, canvas, f)
        for (corner in corners(photo)) {
            val p = t.map(corner)
            assertTrue(p.x >= -1e-6 && p.x <= canvas.width + 1e-6 && p.y >= -1e-6 && p.y <= canvas.height + 1e-6)
        }
    }

    @Test
    fun `preview framing exports to the same composition`() {
        val preview = PxSize(810, 338)
        val export = PxSize(3240, 1352)
        val f = FramingMath.clamp(photo, preview, Framing(rotationDegrees = 7.0, zoom = 1.6, offset = Vec2(40.0, -12.0)))
        val tPreview = FramingMath.transform(photo, preview, f)
        val tExport = FramingMath.transform(photo, export, FramingMath.rescale(f, preview, export))
        val k = export.width.toDouble() / preview.width
        for (p in listOf(Vec2(0.0, 0.0), Vec2(1234.0, 2345.0), Vec2(4000.0, 3000.0))) {
            val a = tPreview.map(p)
            val b = tExport.map(p)
            assertEquals(a.x * k, b.x, 1e-6)
            assertEquals(a.y * k, b.y, 1e-6)
        }
    }

    @Test
    fun `subsampled decode maps to the same canvas positions`() {
        val f = Framing(rotationDegrees = 3.0, zoom = 1.3)
        val odd = PxSize(4001, 2999)
        val plan = DecodePlan.forRender(odd, 0.3, Long.MAX_VALUE)
        val sub = FramingMath.transform(odd, canvas, f, plan.decoded)
        val fullOdd = FramingMath.transform(odd, canvas, f)
        val decodedCorner = Vec2(plan.decoded.width.toDouble(), plan.decoded.height.toDouble())
        val sourceCorner = Vec2(odd.width.toDouble(), odd.height.toDouble())
        assertEquals(fullOdd.map(sourceCorner).x, sub.map(decodedCorner).x, 1e-6)
        assertEquals(fullOdd.map(sourceCorner).y, sub.map(decodedCorner).y, 1e-6)
    }

    private fun assertCovers(f: Framing) {
        val inverse = FramingMath.transform(photo, canvas, f).inverse()
        for (corner in corners(canvas)) {
            val p = inverse.map(corner)
            val eps = 1e-6
            assertTrue("canvas corner $corner maps to $p outside photo for $f",
                p.x >= -eps && p.x <= photo.width + eps && p.y >= -eps && p.y <= photo.height + eps)
        }
    }

    private fun corners(size: PxSize) = listOf(
        Vec2(0.0, 0.0),
        Vec2(size.width.toDouble(), 0.0),
        Vec2(0.0, size.height.toDouble()),
        Vec2(size.width.toDouble(), size.height.toDouble()),
    )
}
