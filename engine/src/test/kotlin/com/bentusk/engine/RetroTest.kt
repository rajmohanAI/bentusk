package com.bentusk.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroTest {

    private fun rgb(argb: Int) = Triple(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)

    @Test
    fun zeroStrengthLeavesThePhotoAsShot() {
        for (style in RetroStyle.entries) {
            val e = Retro.effects(Look(style, strength = 0.0))
            val id = DoubleArray(20) { if (it % 6 == 0) 1.0 else 0.0 }
            assertArrayEquals(id, e.matrix, 1e-9)
            assertEquals(0.0, e.grain, 0.0)
            assertEquals(0.0, e.vignette, 0.0)
            e.leak?.let { assertEquals(0, it ushr 24) }
        }
    }

    @Test
    fun blackAndWhiteStylesHaveNoColour() {
        val noir = Retro.effects(Look(RetroStyle.Noir)).matrix
        for (c in listOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt())) {
            val (r, g, b) = rgb(Retro.apply(noir, c))
            assertTrue(r == g && g == b)
        }
    }

    @Test
    fun sepiaIsWarm() {
        val (r, g, b) = rgb(Retro.apply(Retro.effects(Look(RetroStyle.Sepia)).matrix, 0xFF808080.toInt()))
        assertTrue(r > g && g > b)
    }

    @Test
    fun fadedLiftsTheBlacks() {
        val (r, g, b) = rgb(Retro.apply(Retro.effects(Look(RetroStyle.Faded)).matrix, 0xFF000000.toInt()))
        assertTrue(minOf(r, g, b) >= 25)
    }

    @Test
    fun everyLookKeepsTheImageReadable() {
        // White stays light and black stays dark, so no look washes a photo out or blacks it out.
        for (style in RetroStyle.entries) {
            val m = Retro.effects(Look(style)).matrix
            val white = rgb(Retro.apply(m, 0xFFFFFFFF.toInt()))
            val black = rgb(Retro.apply(m, 0xFF000000.toInt()))
            assertTrue("$style white", white.toList().average() > 170)
            assertTrue("$style black", black.toList().average() < 80)
            assertTrue("$style alpha", Retro.apply(m, 0x80FFFFFF.toInt()) ushr 24 == 0x80)
        }
    }

    @Test
    fun strengthScalesTheOverlays() {
        val full = Retro.effects(Look(RetroStyle.HomeMovie))
        val half = Retro.effects(Look(RetroStyle.HomeMovie, strength = 0.5))
        assertEquals(full.grain / 2, half.grain, 1e-9)
        assertEquals(full.vignette / 2, half.vignette, 1e-9)
        assertTrue((half.leak!! ushr 24) < (full.leak!! ushr 24))
        assertNull(Retro.effects(Look(RetroStyle.Noir)).leak)
    }

    @Test
    fun stampLooksLikeA90sCamera() {
        assertEquals("'98 07 04", Retro.stamp(1998, 7, 4))
        assertEquals("'26 10 26", Retro.stamp(2026, 10, 26))
    }

    @Test
    fun grainIsRepeatableAndCentred() {
        val a = Retro.grainTile(64)
        assertArrayEquals(a, Retro.grainTile(64))
        assertEquals(64 * 64, a.size)
        assertTrue(a.all { it in 0..255 })
        assertEquals(128.0, a.average(), 6.0)
    }

    @Test
    fun lookSurvivesSavingAndOldDesignsStillLoad() {
        val layer = PhotoLayer(1, NRect(0.0, 0.0, 1.0, 1.0), uri = "content://x", look = Look(RetroStyle.Instant, 0.7, "'26 10 26"))
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(layer, json.decodeFromString(PhotoLayer.serializer(), json.encodeToString(PhotoLayer.serializer(), layer)))
        assertNull(json.decodeFromString(PhotoLayer.serializer(), """{"id":1,"rect":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0}}""").look)
    }
}
