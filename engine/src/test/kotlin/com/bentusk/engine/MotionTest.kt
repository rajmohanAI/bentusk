package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    /** Three slides: each is a third of the canvas wide. */
    private val carousel = Design.blank(TemplateKind.Carousel, 3, PostRatio.Portrait)

    private fun text(rect: NRect, motion: Motion?, id: Long = 1) =
        TextLayer(id, rect, "→", 0.1, 0xFF000000.toInt(), motion = motion)

    @Test
    fun `the wave rests at the loop's ends and peaks in the middle`() {
        assertEquals(0.0, Motions.wave(0), 1e-12)
        assertEquals(1.0, Motions.wave(Motions.LOOP_MS / 2), 1e-12)
        assertEquals(0.0, Motions.wave(Motions.LOOP_MS), 1e-12)
        assertEquals(0.5, Motions.wave(Motions.LOOP_MS / 4), 1e-12)
        assertEquals(Motions.wave(300), Motions.wave(300 + 5 * Motions.LOOP_MS), 1e-12)
    }

    @Test
    fun `an exported slide holds whole loops and passes Instagram's minimum`() {
        assertEquals(0L, Motions.SLIDE_MS % Motions.LOOP_MS)
        assertTrue(Motions.SLIDE_MS >= QualityCheck.MIN_CLIP_MS)
    }

    @Test
    fun `a nudge travels right by a share of one slide`() {
        val layer = text(NRect(0.3, 0.4, 0.36, 0.6), Motion.Nudge)
        val peak = Motions.at(layer, carousel, Motions.LOOP_MS / 2)
        assertEquals(0.3 + 0.04 / 3, peak.rect.left, 1e-12)
        assertEquals(0.36 + 0.04 / 3, peak.rect.right, 1e-12)
        assertEquals(layer.rect.top, peak.rect.top, 1e-12)
        assertEquals(layer.rect, Motions.at(layer, carousel, 0).rect)
    }

    @Test
    fun `a nudge stops at the canvas edge`() {
        val layer = text(NRect(0.9, 0.4, 0.995, 0.6), Motion.Nudge)
        assertEquals(1.0, Motions.at(layer, carousel, Motions.LOOP_MS / 2).rect.right, 1e-12)
    }

    @Test
    fun `a pulse grows text around its centre`() {
        val layer = text(NRect(0.4, 0.4, 0.6, 0.6), Motion.Pulse)
        val peak = Motions.at(layer, carousel, Motions.LOOP_MS / 2)
        assertEquals(0.108, peak.size, 1e-12)
        assertEquals(0.5, (peak.rect.left + peak.rect.right) / 2, 1e-12)
        assertEquals(0.216, peak.rect.bottom - peak.rect.top, 1e-12)
    }

    @Test
    fun `still layers and still designs are left alone`() {
        val still = text(NRect(0.4, 0.4, 0.6, 0.6), null)
        assertSame(still, Motions.at(still, carousel, 600))
        val design = carousel.add(still)
        assertSame(design, Motions.frame(design, 600))
        assertTrue(Motions.movingTiles(design).isEmpty())
        assertEquals(-1, Motions.firstMoving(design))
    }

    @Test
    fun `a nudge across a seam moves both slides`() {
        val arrow = Stickers.layer(carousel, 0, StickerPlacement.SeamArrow, "→", 0).copy(motion = Motion.Nudge)
        val design = carousel.add(PhotoLayer(1, NRect(0.0, 0.0, 1.0 / 3, 1.0))).add(arrow.copy(id = 2))
        assertEquals(setOf(0, 1), Motions.movingTiles(design))
        assertEquals(1, Motions.firstMoving(design))
    }

    @Test
    fun `blank moving text does not turn a slide into video`() {
        val blank = text(NRect(0.4, 0.4, 0.6, 0.6), Motion.Pulse).copy(text = " ")
        assertTrue(Motions.movingTiles(carousel.add(blank)).isEmpty())
    }

    @Test
    fun `motion survives saving`() {
        val design = carousel.add(text(NRect(0.4, 0.4, 0.6, 0.6), Motion.Pulse))
        assertEquals(design, Design.fromJson(Design.toJson(design)))
        val plain = carousel.add(text(NRect(0.4, 0.4, 0.6, 0.6), null))
        assertNull((Design.fromJson(Design.toJson(plain)).layers.single() as TextLayer).motion)
    }
}
