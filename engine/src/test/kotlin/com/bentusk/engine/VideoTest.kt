package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTest {
    private val carousel = Design.blank(TemplateKind.Carousel, 3, PostRatio.Portrait)

    @Test
    fun `a new clip plays from the start for at most a minute`() {
        assertEquals(VideoClip(90_000, 0, 60_000), VideoClip(90_000))
        assertEquals(12_000, VideoClip(12_000).lengthMs)
    }

    @Test
    fun `trimming stays inside the video and within the allowed lengths`() {
        val clip = VideoClip(90_000)
        assertEquals(VideoClip(90_000, 5_000, 20_000), clip.trimmed(5_000, 20_000))
        // Too short: the end is pushed out to the one-second minimum.
        assertEquals(1_000, clip.trimmed(10_000, 10_200).lengthMs)
        // Past the end of the video.
        assertEquals(90_000, clip.trimmed(80_000, 120_000).endMs)
        // Moving the start of a long range drags the end along so it stays a minute.
        val moved = clip.trimmed(20_000, 90_000)
        assertEquals(VideoClip(90_000, 20_000, 80_000), moved)
        // Moving the end of a long range drags the start.
        val dragged = VideoClip(90_000, 0, 60_000).trimmed(0, 85_000)
        assertEquals(VideoClip(90_000, 25_000, 85_000), dragged)
    }

    @Test
    fun `clips shorter than a second keep their whole length`() {
        assertEquals(VideoClip(600, 0, 600), VideoClip(600).trimmed(300, 400))
    }

    @Test
    fun `a clip fills its slide, sits under everything, and replaces the old clip`() {
        val withText = carousel.add(TextLayer(1, carousel.tileRect(1), "Hi", 0.05, 0))
        val one = VideoSlides.place(withText, 1, "content://a", 10_000)
        val clip = VideoSlides.videoOn(one, 1)!!
        assertEquals(one.tileRect(1), clip.rect)
        assertEquals(clip.id, one.layers.first().id)
        assertNull(VideoSlides.videoOn(one, 0))

        val two = VideoSlides.place(one, 1, "content://b", 5_000)
        assertEquals(1, two.photos.count { it.video != null })
        assertEquals("content://b", VideoSlides.videoOn(two, 1)!!.uri)
        assertTrue(two.stills.isEmpty())
    }

    @Test
    fun `video slides survive saving and loading`() {
        val design = VideoSlides.place(carousel, 2, "content://v", 30_000)
        assertEquals(design, Design.fromJson(Design.toJson(design)))
    }

    @Test
    fun `framing maps to the same picture in device coordinates`() {
        val slide = PostRatio.Portrait.size
        val framing = Framing(0.0, 2.0, Vec2(108.0, -135.0))
        val m = VideoSlides.ndcMatrix(framing, slide)
        // Pixel space: p' = c + zoom * (p - c) + offset. The slide centre lands at centre + offset.
        val centre = Affine(m[0].toDouble(), m[1].toDouble(), m[2].toDouble(), m[3].toDouble(), m[4].toDouble(), m[5].toDouble())
            .map(Vec2.Zero)
        assertEquals(2 * 108.0 / 1080, centre.x, 1e-6)
        assertEquals(2 * 135.0 / 1350, centre.y, 1e-6)
        assertEquals(2f, m[0])
        assertEquals(2f, m[4])
    }

    @Test
    fun `video framing never rotates or shows the background`() {
        val f = VideoSlides.clamp(PxSize(1920, 1080), PostRatio.Portrait.size, Framing(30.0, 0.5, Vec2.Zero))
        assertEquals(0.0, f.rotationDegrees, 0.0)
        assertEquals(1.0, f.zoom, 0.0)
    }
}
