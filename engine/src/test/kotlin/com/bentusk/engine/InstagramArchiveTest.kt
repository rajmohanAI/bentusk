package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramArchiveTest {
    @Test
    fun `finds the post lists in every archive layout`() {
        assertTrue(InstagramArchive.isIndex("your_instagram_activity/content/posts_1.json"))
        assertTrue(InstagramArchive.isIndex("instagram-me-2024/your_instagram_activity/content/posts_12.json"))
        assertTrue(InstagramArchive.isIndex("content/posts_1.json"))
        assertTrue(InstagramArchive.isIndex("content/reels.json"))
        assertTrue(InstagramArchive.isIndex("media.json"))
        assertTrue(InstagramArchive.isIndex("me_20190501/media.json"))
        assertFalse(InstagramArchive.isIndex("your_instagram_activity/content/stories.json"))
        assertFalse(InstagramArchive.isIndex("your_instagram_activity/saved/saved_media.json"))
        assertFalse(InstagramArchive.isIndex("media/posts/202401/123.jpg"))
    }

    @Test
    fun `reads carousels and single posts, with paths inside a top folder`() {
        val text = """
            [
              {
                "media": [
                  {"uri": "media/posts/202401/a1.jpg", "creation_timestamp": 1704100000, "title": ""},
                  {"uri": "media/posts/202401/a2.mp4", "creation_timestamp": 1704100000, "title": ""}
                ],
                "title": "CafÃ© day ð\u009f\u0098\u008a",
                "creation_timestamp": 1704100000
              },
              {
                "media": [{"uri": "media/posts/202402/b.mp4", "creation_timestamp": 1706800000, "title": "Single"}]
              },
              {"media": []},
              "not a post"
            ]
        """.trimIndent()
        val posts = InstagramArchive.parse("me/your_instagram_activity/content/posts_1.json", text)
        assertEquals(2, posts.size)
        val carousel = posts[0]
        assertEquals(listOf("me/media/posts/202401/a1.jpg", "me/media/posts/202401/a2.mp4"), carousel.media)
        assertEquals("Café day 😊", carousel.caption)
        assertEquals(1704100000L, carousel.takenAt)
        assertFalse(carousel.isVideo)
        val single = posts[1]
        assertEquals("Single", single.caption)
        assertEquals(1706800000L, single.takenAt)
        assertTrue(single.isVideo)
    }

    @Test
    fun `reads reels`() {
        val text = """{"ig_reels_media": [{"media": [{"uri": "media/reels/202403/r.mp4", "creation_timestamp": 1709300000, "title": "Reel"}]}]}"""
        val posts = InstagramArchive.parse("content/reels.json", text)
        assertEquals(listOf(ArchivePost(1709300000, listOf("media/reels/202403/r.mp4"), "Reel")), posts)
    }

    @Test
    fun `reads archives from before 2020`() {
        val text = """
            {
              "photos": [{"caption": "Old", "taken_at": "2019-05-01T10:20:30+00:00", "path": "photos/201905/p.jpg"}],
              "videos": [{"caption": "", "taken_at": "2018-01-01T00:00:00", "path": "videos/201801/v.mp4"}]
            }
        """.trimIndent()
        val posts = InstagramArchive.parse("me_2019/media.json", text)
        assertEquals(ArchivePost(1556706030, listOf("me_2019/photos/201905/p.jpg"), "Old"), posts[0])
        assertEquals(1514764800L, posts[1].takenAt)
        assertTrue(posts[1].isVideo)
    }

    @Test
    fun `anything unreadable gives no posts`() {
        assertTrue(InstagramArchive.parse("content/posts_1.json", "<html>").isEmpty())
        assertTrue(InstagramArchive.parse("content/posts_1.json", "{}").isEmpty())
        assertTrue(InstagramArchive.parse("notes.txt", "[]").isEmpty())
    }

    @Test
    fun `posts come newest first, each once, up to the limit`() {
        val many = (1..100).map { ArchivePost(it.toLong(), listOf("p$it.jpg"), "") }
        val doubled = many + ArchivePost(100, listOf("p100.jpg"), "again")
        val result = InstagramArchive.newestFirst(doubled.shuffled(java.util.Random(7)))
        assertEquals(InstagramArchive.MAX_POSTS, result.size)
        assertEquals(100L, result.first().takenAt)
        assertEquals(result.map { it.cover }.distinct(), result.map { it.cover })
        assertEquals((100 downTo 41).map { it.toLong() }, result.map { it.takenAt })
    }

    @Test
    fun `fixing text leaves well-formed text alone`() {
        assertEquals("plain", InstagramArchive.fixText("plain"))
        assertEquals("Café 😊", InstagramArchive.fixText("Café 😊"))
        assertEquals("é", InstagramArchive.fixText("Ã©"))
        // Latin-1 text that is not escaped UTF-8 stays as it is.
        assertEquals("naïve", InstagramArchive.fixText("naïve"))
    }
}
