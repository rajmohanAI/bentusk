package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTest {
    private val story = Design.blank(TemplateKind.Story, 1, PostRatio.Portrait)

    @Test
    fun `a story is one full-screen 1080 by 1920 picture`() {
        val layout = story.layout()
        assertEquals(PxSize(1080, 1920), layout.canvas)
        assertEquals(listOf(Tile(0, 0, 0, PxRect(0, 0, 1080, 1920), postOrder = 1)), layout.tiles)
    }

    @Test
    fun `its size cannot change`() {
        assertSame(story, story.withCount(3))
    }

    @Test
    fun `the profile grid shows the middle three quarters of a Reel cover`() {
        val crop = SafeZones.storyCrops().single()
        assertEquals(ZoneKind.GridCrop, crop.kind)
        assertEquals(0.125, crop.rect.top, 1e-9)
        assertEquals(0.875, crop.rect.bottom, 1e-9)
        assertEquals(0.0, crop.rect.left, 1e-9)
        assertEquals(1.0, crop.rect.right, 1e-9)
    }

    @Test
    fun `text under the caption or reply bar is reported, text in the middle is not`() {
        val caption = TextLayer(1, NRect(0.1, 0.86, 0.9, 0.94), "Shop now", 0.03, 0xFFFFFFFF.toInt())
        assertEquals(listOf(QualityIssue(IssueKind.UnderIcons, 1, 0)), QualityCheck.run(story.add(caption), emptyMap()))
        val middle = TextLayer(2, NRect(0.1, 0.4, 0.8, 0.5), "Day 1", 0.03, 0xFFFFFFFF.toInt())
        assertTrue(QualityCheck.run(story.add(middle), emptyMap()).isEmpty())
    }

    @Test
    fun `a story survives saving`() {
        val saved = story.add(PhotoLayer(1, NRect(0.0, 0.0, 1.0, 1.0)))
        assertEquals(saved, Design.fromJson(Design.toJson(saved)))
    }
}
