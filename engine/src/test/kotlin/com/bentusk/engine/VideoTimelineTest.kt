package com.bentusk.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTimelineTest {

    private val three = VideoTimeline()
        .addVideo("a", 10_000)
        .addPhoto("b")
        .addVideo("c", 5_000)

    @Test
    fun piecesPlayInOrderAndAddUp() {
        assertEquals(listOf("a", "b", "c"), three.clips.map { it.uri })
        assertEquals(18_000, three.totalMs)
        assertEquals(listOf(1L, 2L, 3L), three.clips.map { it.id })
    }

    @Test
    fun findsWhatPlaysAtATime() {
        assertEquals("a" to 0L, three.at(0)!!.let { it.first.uri to it.second })
        assertEquals("b" to 500L, three.at(10_500)!!.let { it.first.uri to it.second })
        assertEquals("c" to 4_999L, three.at(17_999)!!.let { it.first.uri to it.second })
        assertNull(three.at(18_000))
        assertEquals(13_000L, three.startOf(3))
    }

    @Test
    fun trimStaysInsideTheClip() {
        val t = three.trim(1, -50, 99_000).clip(1)!!
        assertEquals(0L, t.startMs)
        assertEquals(10_000L, t.endMs)
        val short = three.trim(1, 4_000, 4_100).clip(1)!!
        assertEquals(VideoTimeline.MIN_PIECE_MS, short.lengthMs)
    }

    @Test
    fun photoTrimSetsItsTimeOnScreen() {
        assertEquals(7_000L, three.trim(2, 0, 7_000).clip(2)!!.lengthMs)
        assertEquals(VideoTimeline.PHOTO_MAX_MS, three.trim(2, 0, 60_000).clip(2)!!.lengthMs)
    }

    @Test
    fun splitCutsOneClipIntoTwo() {
        val s = three.split(1, 4_000)
        assertEquals(4, s.clips.size)
        val (first, second) = s.clips[0] to s.clips[1]
        assertEquals(0L to 4_000L, first.startMs to first.endMs)
        assertEquals(4_000L to 10_000L, second.startMs to second.endMs)
        assertEquals(three.totalMs, s.totalMs)
        assertEquals(s, s.split(1, 100))
        assertEquals(three, three.split(2, 1_000))
    }

    @Test
    fun moveDuplicateAndRemove() {
        assertEquals(listOf("b", "a", "c"), three.move(1, 1).clips.map { it.uri })
        assertEquals(three, three.move(1, -1))
        val d = three.duplicate(2)
        assertEquals(listOf("a", "b", "b", "c"), d.clips.map { it.uri })
        assertEquals(4, d.clips.map { it.id }.toSet().size)
        assertEquals(listOf("a", "c"), three.remove(2).clips.map { it.uri })
    }

    @Test
    fun addAfterTheSelectedPiece() {
        assertEquals(listOf("a", "x", "b", "c"), three.addPhoto("x", afterId = 1).clips.map { it.uri })
    }

    @Test
    fun mutingSkipsPhotos() {
        assertTrue(three.setMuted(1, true).clip(1)!!.muted)
        assertFalse(three.setMuted(2, true).clip(2)!!.muted)
    }

    @Test
    fun limits() {
        var t = VideoTimeline()
        repeat(VideoTimeline.MAX_CLIPS + 5) { t = t.addPhoto("p$it") }
        assertEquals(VideoTimeline.MAX_CLIPS, t.clips.size)
        assertFalse(t.canAdd)
        assertFalse(three.tooLong)
        assertTrue(VideoTimeline().addVideo("long", 200_000).tooLong)
    }

    @Test
    fun survivesSaving() {
        val t = three.copy(shape = VideoShape.Square).split(1, 3_000)
        assertEquals(t, Json.decodeFromString(VideoTimeline.serializer(), Json.encodeToString(VideoTimeline.serializer(), t)))
    }
}
