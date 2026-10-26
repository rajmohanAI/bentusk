package com.bentusk.engine

import com.bentusk.engine.CaptionIdeas.Language
import com.bentusk.engine.CaptionIdeas.Request
import com.bentusk.engine.CaptionIdeas.Theme
import com.bentusk.engine.CaptionIdeas.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionIdeasTest {

    @Test
    fun `labels choose the theme`() {
        assertEquals(Theme.Travel, CaptionIdeas.theme(listOf("Sky", "Beach", "Person")))
        assertEquals(Theme.Food, CaptionIdeas.theme(listOf("Coffee", "Cup")))
        assertEquals(Theme.Everyday, CaptionIdeas.theme(listOf("Thing")))
    }

    @Test
    fun `every tone and language gives three distinct finished captions`() {
        for (tone in Tone.entries) for (language in Language.entries) for (variant in 0..3) {
            val ideas = CaptionIdeas.write(Request(listOf("beach"), tone, language, emoji = true, slides = 3, variant = variant))
            assertEquals(3, ideas.size)
            assertEquals(3, ideas.toSet().size)
            for (text in ideas) {
                assertFalse(text, text.contains('{'))
                assertFalse(Caption.isTooLong(text))
                assertTrue(text.isNotBlank())
            }
        }
    }

    @Test
    fun `emoji can be left out`() {
        val ideas = CaptionIdeas.write(Request(listOf("coffee"), Tone.Minimal, Language.English, emoji = false))
        assertTrue(ideas.none { it.contains("🍽") })
        assertTrue(ideas.none { it.contains(" .") })
    }

    @Test
    fun `writing again gives different ideas`() {
        val a = CaptionIdeas.write(Request(listOf("dog"), Tone.Fun, Language.English, true, variant = 0))
        val b = CaptionIdeas.write(Request(listOf("dog"), Tone.Fun, Language.English, true, variant = 1))
        assertTrue(a != b)
    }
}
