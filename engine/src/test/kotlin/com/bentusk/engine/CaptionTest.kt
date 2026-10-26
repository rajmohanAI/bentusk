package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CaptionTest {

    @Test
    fun `finds hashtags once each in any script`() {
        assertEquals(listOf("#goa", "#सफ़र", "#sunset_2026"), Caption.hashtags("Hi #Goa #सफ़र #goa #sunset_2026!"))
    }

    @Test
    fun `adds tags on their own line and toggles them off`() {
        val one = Caption.toggleHashtag("Golden hour", "travel")
        assertEquals("Golden hour\n\n#travel", one)
        val two = Caption.toggleHashtag(one, "#Goa")
        assertEquals("Golden hour\n\n#travel #goa", two)
        assertEquals("Golden hour\n\n#goa", Caption.toggleHashtag(two, "travel"))
    }

    @Test
    fun `stops at the hashtag limit`() {
        var text = "Hello"
        for (tag in listOf("a", "b", "c", "d", "e", "f")) text = Caption.toggleHashtag(text, tag)
        assertEquals(InstagramSpec.MAX_HASHTAGS, Caption.hashtags(text).size)
        assertFalse(Caption.canAddHashtag(text))
    }

    @Test
    fun `bundled hashtags are valid and unique per topic`() {
        for (topic in HashtagSets.Topics) {
            for (tag in topic.tags) assertEquals(listOf(tag), Caption.hashtags(tag))
            assertEquals(topic.tags.size, topic.tags.distinct().size)
        }
    }

    @Test
    fun `keeps paragraph breaks with an invisible line`() {
        val text = "\n\nHook line   \n\nSecond paragraph\r\n\n\nThird\n\n"
        val b = Caption.BLANK_LINE
        assertEquals("Hook line\n$b\nSecond paragraph\n$b\n$b\nThird", Caption.keepLineBreaks(text))
        assertEquals("One line", Caption.keepLineBreaks("One line"))
        assertEquals("", Caption.keepLineBreaks(" \n \n"))
    }
}
