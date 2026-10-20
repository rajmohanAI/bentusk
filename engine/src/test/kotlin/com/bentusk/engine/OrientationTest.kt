package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTest {

    private val raw = PxSize(400, 300)

    @Test
    fun `every orientation maps the raw image onto the upright rectangle`() {
        for (o in ExifOrientation.entries) {
            val geometry = SourceGeometry(raw, o)
            val t = geometry.decodedToUpright(raw)
            val mapped = listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(0.0, 300.0), Vec2(400.0, 300.0)).map(t::map)
            assertEquals("$o min x", 0.0, mapped.minOf { it.x }, 1e-9)
            assertEquals("$o min y", 0.0, mapped.minOf { it.y }, 1e-9)
            assertEquals("$o max x", geometry.upright.width.toDouble(), mapped.maxOf { it.x }, 1e-9)
            assertEquals("$o max y", geometry.upright.height.toDouble(), mapped.maxOf { it.y }, 1e-9)
        }
    }

    @Test
    fun `rotate 90 turns the top-left corner to the top-right`() {
        val t = SourceGeometry(raw, ExifOrientation.Rotate90).decodedToUpright(raw)
        assertEquals(Vec2(300.0, 0.0), t.map(Vec2(0.0, 0.0)))
        assertEquals(Vec2(0.0, 400.0), t.map(Vec2(400.0, 300.0)))
    }

    @Test
    fun `subsampled decode lands on the same upright corners`() {
        val geometry = SourceGeometry(raw, ExifOrientation.Rotate270)
        val decoded = DecodePlan.decodedSize(raw, 4)
        val t = geometry.decodedToUpright(decoded)
        val full = geometry.decodedToUpright(raw)
        assertEquals(full.map(Vec2(400.0, 300.0)), t.map(Vec2(decoded.width.toDouble(), decoded.height.toDouble())))
    }
}
