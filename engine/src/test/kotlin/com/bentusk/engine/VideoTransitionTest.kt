package com.bentusk.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTransitionTest {

    private val two = VideoTimeline().addVideo("a", 4_000).addVideo("b", 4_000)

    @Test
    fun straightCutsLeaveEveryFrameAlone() {
        assertFalse(VideoTransitions.any(two))
        for (t in 0L..8_000L step 50) assertTrue(VideoTransitions.frame(two, t).isStill)
    }

    @Test
    fun fadeIsBlackRightOnTheCutAndClearOutsideIt() {
        val t = two.setTransition(2, CutTransition.Fade)
        assertTrue(VideoTransitions.any(t))
        val onCut = VideoTransitions.frame(t, 4_000)
        assertEquals(255, onCut.dipAlpha)
        assertEquals(0, onCut.dip and 0xFFFFFF)
        assertTrue(VideoTransitions.frame(t, 4_000 - VideoTransitions.HALF_MS).isStill)
        assertTrue(VideoTransitions.frame(t, 4_000 + VideoTransitions.HALF_MS).isStill)
        val partway = VideoTransitions.frame(t, 3_850).dipAlpha
        assertTrue(partway in 1..254)
        assertEquals(partway, VideoTransitions.frame(t, 4_150).dipAlpha)
    }

    @Test
    fun flashGoesWhite() {
        val t = two.setTransition(2, CutTransition.Flash)
        assertEquals(0xFFFFFFFF.toInt(), VideoTransitions.frame(t, 4_000).dip)
    }

    @Test
    fun zoomPushesInTowardsTheCut() {
        val t = two.setTransition(2, CutTransition.Zoom)
        val near = VideoTransitions.frame(t, 3_950).scale
        val far = VideoTransitions.frame(t, 3_800).scale
        assertTrue(near > far)
        assertTrue(far > 1.0)
        assertEquals(1 + VideoTransitions.ZOOM, VideoTransitions.frame(t, 4_000).scale, 1e-9)
    }

    @Test
    fun whipLeavesLeftAndArrivesFromTheRight() {
        val t = two.setTransition(2, CutTransition.Whip)
        assertTrue(VideoTransitions.frame(t, 3_900).shift < 0)
        assertTrue(VideoTransitions.frame(t, 4_100).shift > 0)
    }

    @Test
    fun shortPiecesGetShorterTransitions() {
        val t = VideoTimeline().addVideo("a", 4_000).addPhoto("b", holdMs = 500).setTransition(2, CutTransition.Fade)
        assertEquals(250L, VideoTransitions.cuts(t).single().halfMs)
        assertTrue(VideoTransitions.frame(t, 3_740).isStill)
    }

    @Test
    fun theFirstPieceHasNoCut() {
        val t = two.setTransition(1, CutTransition.Fade)
        assertTrue(VideoTransitions.cuts(t).isEmpty())
        assertFalse(VideoTransitions.any(t))
        val all = two.setAllTransitions(CutTransition.Whip)
        assertEquals(CutTransition.None, all.clips[0].transition)
        assertEquals(CutTransition.Whip, all.clips[1].transition)
    }

    @Test
    fun fadeEndsDimTheStartAndFinish() {
        val t = two.copy(fadeEnds = true)
        assertEquals(255, VideoTransitions.frame(t, 0).dipAlpha)
        assertTrue(VideoTransitions.frame(t, 4_000).isStill)
        assertTrue(VideoTransitions.frame(t, 7_990).dipAlpha > 240)
        assertTrue(VideoTransitions.frame(t, 300).dipAlpha in 1..254)
    }

    @Test
    fun splittingKeepsTheCutInsideAShotStraight() {
        val t = two.setTransition(2, CutTransition.Fade).split(2, 2_000)
        assertEquals(CutTransition.Fade, t.clips[1].transition)
        assertEquals(CutTransition.None, t.clips[2].transition)
    }

    @Test
    fun transitionsSurviveSavingAndOldSavesStillLoad() {
        val t = two.setTransition(2, CutTransition.Zoom).copy(fadeEnds = true)
        val back = Json.decodeFromString(VideoTimeline.serializer(), Json.encodeToString(VideoTimeline.serializer(), t))
        assertEquals(t, back)
        val old = Json.decodeFromString(VideoTimeline.serializer(), """{"clips":[{"id":1,"uri":"a","photo":false,"durationMs":1000}]}""")
        assertEquals(CutTransition.None, old.clips[0].transition)
        assertFalse(old.fadeEnds)
    }
}
