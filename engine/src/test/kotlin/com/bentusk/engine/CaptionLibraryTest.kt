package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CaptionLibraryTest {
    @Test
    fun `saves tags newest first, tidied and capped at the limit`() {
        val library = CaptionLibrary()
            .saveTags("Goa trip", listOf("#Goa", "beach", "#goa"), id = 1)
            .saveTags("", listOf("#a", "#b", "#c", "#d", "#e", "#f"), id = 2)
        assertEquals(listOf(2L, 1L), library.tags.map { it.id })
        assertEquals(listOf("#goa", "#beach"), library.tags[1].tags)
        assertEquals(InstagramSpec.MAX_HASHTAGS, library.tags[0].tags.size)
        assertEquals("#a #b #c #d #e", library.tags[0].name)
    }

    @Test
    fun `saving under a used name replaces that entry`() {
        val library = CaptionLibrary()
            .saveTags("Shop", listOf("#sale"), id = 1)
            .saveTags("shop", listOf("#newarrivals"), id = 2)
        assertEquals(listOf(SavedTags(2, "shop", listOf("#newarrivals"))), library.tags)
    }

    @Test
    fun `nothing to save leaves the library as it is`() {
        val empty = CaptionLibrary()
        assertSame(empty, empty.saveTags("x", emptyList(), id = 1))
        assertSame(empty, empty.saveCaption("x", "  \n ", id = 1))
    }

    @Test
    fun `captions get a name from their first line`() {
        val library = CaptionLibrary().saveCaption("", "  Order on WhatsApp, link in bio\nFree delivery in Chennai ", id = 7)
        val saved = library.captions.single()
        assertEquals("Order on WhatsApp, link…", saved.name)
        assertEquals(CaptionLibrary.NAME_CHARS, saved.name.length)
        assertEquals("Order on WhatsApp, link in bio\nFree delivery in Chennai", saved.text)
        assertEquals(emptyList<SavedCaption>(), library.removeCaption(7).captions)
    }

    @Test
    fun `keeps the newest twenty of each kind`() {
        var library = CaptionLibrary()
        for (i in 1..25L) library = library.saveCaption("c$i", "text $i", id = i)
        assertEquals(CaptionLibrary.MAX_ITEMS, library.captions.size)
        assertEquals(25L, library.captions.first().id)
        assertEquals(6L, library.captions.last().id)
    }

    @Test
    fun `survives saving and shrugs off bad data`() {
        val library = CaptionLibrary().saveTags("Food", listOf("#food"), 1).saveCaption("Sign-off", "See you soon", 2)
        assertEquals(library, CaptionLibrary.fromJson(CaptionLibrary.toJson(library)))
        assertEquals(CaptionLibrary(), CaptionLibrary.fromJson("not json"))
        assertEquals(CaptionLibrary(), CaptionLibrary.fromJson(null))
    }

    @Test
    fun `adding a saved set skips tags already there and stops at the limit`() {
        assertEquals("Sunset\n\n#goa #beach", Caption.addHashtags("Sunset\n\n#goa", listOf("#goa", "#beach")))
        val full = Caption.addHashtags("Hi", listOf("#a", "#b", "#c", "#d"))
        assertEquals("Hi\n\n#a #b #c #d #e", Caption.addHashtags(full, listOf("#e", "#f")))
    }

    @Test
    fun `a snippet goes before the hashtag line`() {
        assertEquals("Link in bio", Caption.insertSnippet("", "  Link in bio "))
        assertEquals("Sunset\n\nLink in bio", Caption.insertSnippet("Sunset", "Link in bio"))
        assertEquals("Sunset\n\nLink in bio\n\n#goa #beach", Caption.insertSnippet("Sunset\n\n#goa #beach", "Link in bio"))
        assertEquals("Link in bio\n\n#goa", Caption.insertSnippet("#goa", "Link in bio"))
        assertEquals("Sunset", Caption.insertSnippet("Sunset", "  "))
    }
}
