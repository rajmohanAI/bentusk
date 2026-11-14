package com.bentusk.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOverlayTest {

    private val base = VideoTimeline().addVideo("a", 10_000).addPhoto("b") // 13 s

    @Test
    fun textShowsFromThePlayheadForAFewSeconds() {
        val t = VideoOverlays.addText(base, "Hello", 2_000)
        val o = t.overlays.single()
        assertEquals(2_000L to 5_000L, o.startMs to o.endMs)
        assertTrue(o.isText)
        assertEquals(listOf(o), VideoOverlays.visibleAt(t, 4_999))
        assertTrue(VideoOverlays.visibleAt(t, 5_000).isEmpty())
    }

    @Test
    fun nearTheEndItStillFits() {
        val o = VideoOverlays.addText(base, "End", 12_900).overlays.single()
        assertEquals(13_000L, o.endMs)
        assertEquals(VideoTimeline.MIN_PIECE_MS, o.endMs - o.startMs)
    }

    @Test
    fun timesStayInsideTheVideo() {
        val t = VideoOverlays.addText(base, "x", 0)
        val o = VideoOverlays.setTimes(t, 1, -100, 99_000).overlays.single()
        assertEquals(0L to 13_000L, o.startMs to o.endMs)
        val short = VideoOverlays.setTimes(t, 1, 6_000, 6_100).overlays.single()
        assertEquals(VideoTimeline.MIN_PIECE_MS, short.endMs - short.startMs)
    }

    @Test
    fun movingKeepsItOnTheFrame() {
        val t = VideoOverlays.addText(base, "x", 0)
        val r = VideoOverlays.move(t, 1, 5.0, -5.0).overlays.single().rect
        assertEquals(1.0, r.right, 1e-9)
        assertEquals(0.0, r.top, 1e-9)
        assertEquals(0.84, r.right - r.left, 1e-9)
    }

    @Test
    fun imagesKeepTheirShape() {
        val t = VideoOverlays.addImage(base, "img", 2.0, 0)
        val r = t.overlays.single().rect
        val size = t.shape.size
        assertEquals(2.0, (r.right - r.left) * size.width / ((r.bottom - r.top) * size.height), 1e-6)
        val big = VideoOverlays.scale(t, 1, 100.0).overlays.single().rect
        assertEquals(1.0, big.right - big.left, 1e-9)
        assertEquals(2.0, (big.right - big.left) * size.width / ((big.bottom - big.top) * size.height), 1e-6)
        val small = VideoOverlays.scale(t, 1, 0.001).overlays.single().rect
        assertTrue(small.right - small.left >= 0.05 - 1e-9 && small.bottom - small.top >= 0.05 - 1e-9)
    }

    @Test
    fun limitAndRemove() {
        var t = base
        repeat(VideoOverlays.MAX_OVERLAYS + 3) { t = VideoOverlays.addText(t, "$it", 0) }
        assertEquals(VideoOverlays.MAX_OVERLAYS, t.overlays.size)
        assertEquals(VideoOverlays.MAX_OVERLAYS - 1, VideoOverlays.remove(t, 3).overlays.size)
    }

    @Test
    fun changeTimesAreSorted() {
        val t = VideoOverlays.setTimes(VideoOverlays.addText(VideoOverlays.addText(base, "a", 4_000), "b", 1_000), 1, 4_000, 6_000)
        assertEquals(listOf(1_000L, 4_000L, 6_000L), VideoOverlays.changeTimes(t))
    }

    @Test
    fun textBecomesADesignTextLayer() {
        val o = VideoOverlays.addText(base, "Hi", 0).overlays.single().copy(font = TextFont.Serif, pill = 0xFF000000.toInt())
        val layer = o.toTextLayer()
        assertEquals("Hi", layer.text)
        assertEquals(TextFont.Serif, layer.font)
        assertEquals(o.pill, layer.pill)
        assertEquals(o.rect, layer.rect)
    }

    @Test
    fun overlaysSurviveSavingAndOldTimelinesLoad() {
        val t = VideoOverlays.addImage(VideoOverlays.addText(base, "Hi", 0), "img", 1.0, 1_000)
        assertEquals(t, Json.decodeFromString(VideoTimeline.serializer(), Json.encodeToString(VideoTimeline.serializer(), t)))
        assertTrue(Json.decodeFromString(VideoTimeline.serializer(), "{}").overlays.isEmpty())
    }
}
