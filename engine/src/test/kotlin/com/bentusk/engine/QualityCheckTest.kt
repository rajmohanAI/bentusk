package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityCheckTest {
    /** Three 1080×1350 slides side by side: a 3240×1350 canvas. */
    private val carousel = Design.blank(TemplateKind.Carousel, 3, PostRatio.Portrait)
    private val middleSlide = NRect(1.0 / 3, 0.0, 2.0 / 3, 1.0)

    private fun text(id: Long, rect: NRect, size: Double = 0.05, text: String = "Hello") =
        TextLayer(id, rect, text, size, 0xFFFFFFFF.toInt())

    private fun kinds(design: Design, sources: Map<Long, PxSize> = emptyMap()) =
        QualityCheck.run(design, sources).map { it.kind }

    @Test
    fun `a clean design has nothing to report`() {
        val design = carousel
            .add(PhotoLayer(1, middleSlide, uri = "content://photo"))
            .add(text(2, NRect(0.4, 0.4, 0.6, 0.6)))
        assertEquals(emptyList<IssueKind>(), kinds(design, mapOf(1L to PxSize(3000, 4000))))
    }

    @Test
    fun `an empty template frame is reported but an empty free photo is not`() {
        val design = carousel
            .add(PhotoLayer(1, middleSlide))
            .add(PhotoLayer(2, NRect(0.0, 0.0, 0.2, 0.5), free = true))
        val issues = QualityCheck.run(design, emptyMap())
        assertEquals(listOf(QualityIssue(IssueKind.EmptyFrame, 1, 1)), issues)
    }

    @Test
    fun `a photo enlarged past one and a half times is soft`() {
        val design = carousel.add(PhotoLayer(1, middleSlide, uri = "content://photo"))
        // 600×750 must grow 1.8× to cover a 1080×1350 slide.
        val issue = QualityCheck.run(design, mapOf(1L to PxSize(600, 750))).single()
        assertEquals(IssueKind.SoftPhoto, issue.kind)
        assertEquals(1, issue.tile)
        assertEquals(1.8, issue.amount, 1e-9)
        // Zooming in a sharp photo far enough also softens it.
        val zoomed = design.replace(PhotoLayer(1, middleSlide, uri = "content://photo", framing = Framing(zoom = 4.0)))
        assertEquals(listOf(IssueKind.SoftPhoto), kinds(zoomed, mapOf(1L to PxSize(2160, 2700))))
        // A photo whose size is not known yet is skipped.
        assertEquals(emptyList<IssueKind>(), kinds(design))
    }

    @Test
    fun `tiny text is reported with its height on a phone`() {
        // 0.02 of 1350 px is 27 px, about 9.75 points once a 1080 px slide fills a 390 point screen.
        val design = carousel.add(text(1, NRect(0.4, 0.4, 0.6, 0.6), size = 0.02))
        val issue = QualityCheck.run(design, emptyMap()).single()
        assertEquals(IssueKind.SmallText, issue.kind)
        assertEquals(9.75, issue.amount, 1e-9)
        assertEquals(emptyList<IssueKind>(), kinds(carousel.add(text(1, NRect(0.4, 0.4, 0.6, 0.6), size = 0.03))))
    }

    @Test
    fun `text under the slide counter is reported on the slide it is under`() {
        // Top-right corner of the first slide, where the "1/3" pill sits.
        val corner = NRect(850.0 / 3240, 20.0 / 1350, 1060.0 / 3240, 120.0 / 1350)
        val design = carousel.add(text(1, corner, size = 0.04))
        assertEquals(listOf(QualityIssue(IssueKind.UnderIcons, 1, 0)), QualityCheck.run(design, emptyMap()))
    }

    @Test
    fun `only the band the words fill counts, not the whole box`() {
        // A tall box on the right of the first slide, with one line of text centred well below the counter.
        val tall = NRect(850.0 / 3240, 0.0, 1060.0 / 3240, 0.6)
        assertEquals(emptyList<IssueKind>(), kinds(carousel.add(text(1, tall, size = 0.04))))
    }

    @Test
    fun `grid posts have no slide counter`() {
        val grid = Design.blank(TemplateKind.Grid, 1, PostRatio.Portrait)
        // 3240×1350 again: top-right corner of the first tile.
        val corner = NRect(850.0 / 3240, 20.0 / 1350, 1060.0 / 3240, 120.0 / 1350)
        assertEquals(emptyList<IssueKind>(), kinds(grid.add(text(1, corner, size = 0.04))))
    }

    @Test
    fun `clips shorter than three seconds are reported`() {
        val short = VideoSlides.place(carousel, 2, "content://clip", 2_000)
        val issue = QualityCheck.run(short, emptyMap()).single()
        assertEquals(QualityIssue(IssueKind.ShortClip, issue.layerId, 2, 2_000.0), issue)
        assertTrue(QualityCheck.run(VideoSlides.place(carousel, 2, "content://clip", 10_000), emptyMap()).isEmpty())
    }

    @Test
    fun `issues come in posting order`() {
        val design = carousel
            .add(PhotoLayer(1, NRect(2.0 / 3, 0.0, 1.0, 1.0)))
            .add(PhotoLayer(2, NRect(0.0, 0.0, 1.0 / 3, 1.0)))
        assertEquals(listOf(0, 2), QualityCheck.run(design, emptyMap()).map { it.tile })

        // A grid posts its bottom-right tile first.
        val grid = Design.blank(TemplateKind.Grid, 2, PostRatio.Portrait)
            .add(PhotoLayer(1, NRect(0.0, 0.0, 1.0 / 3, 0.5)))
            .add(PhotoLayer(2, NRect(2.0 / 3, 0.5, 1.0, 1.0)))
        assertEquals(listOf(2L, 1L), QualityCheck.run(grid, emptyMap()).map { it.layerId })
    }
}
