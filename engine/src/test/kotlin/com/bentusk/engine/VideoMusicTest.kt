package com.bentusk.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMusicTest {

    /** 20 seconds of video. */
    private val video = VideoTimeline().addVideo("a", 12_000).addVideo("b", 8_000)

    @Test
    fun aLongSongIsCutToTheVideo() {
        val t = VideoSoundtrack.setStart(VideoSoundtrack.add(video, "song", "Song", 180_000), 30_000)
        assertEquals(listOf(MusicPiece(30_000, 50_000, 0)), VideoSoundtrack.pieces(t))
        assertEquals(35_000L, VideoSoundtrack.songPosition(t, 5_000))
    }

    @Test
    fun aShortSongLoopsFromItsBeginning() {
        val t = VideoSoundtrack.setStart(VideoSoundtrack.add(video, "loop", "Loop", 8_000), 6_000)
        val pieces = VideoSoundtrack.pieces(t)
        assertEquals(
            listOf(MusicPiece(6_000, 8_000, 0), MusicPiece(0, 8_000, 2_000), MusicPiece(0, 8_000, 10_000), MusicPiece(0, 2_000, 18_000)),
            pieces,
        )
        assertEquals(video.totalMs, pieces.sumOf { it.lengthMs })
        assertEquals(1_000L, VideoSoundtrack.songPosition(t, 3_000))
        assertNull(VideoSoundtrack.songPosition(t, 20_000))
    }

    @Test
    fun theStartPointLeavesSomethingToPlay() {
        val t = VideoSoundtrack.setStart(VideoSoundtrack.add(video, "s", "S", 10_000), 99_000)
        assertEquals(10_000 - VideoSoundtrack.MIN_MUSIC_MS, t.music!!.startMs)
        assertEquals(video, VideoSoundtrack.add(video, "tiny", "Tiny", 400))
    }

    @Test
    fun musicFadesInAndOut() {
        val t = VideoSoundtrack.add(video, "s", "S", 60_000)
        assertEquals(0.0, VideoSoundtrack.gainAt(t, 0), 1e-9)
        assertEquals(VideoSoundtrack.DEFAULT_VOLUME, VideoSoundtrack.gainAt(t, 10_000), 1e-9)
        assertEquals(VideoSoundtrack.DEFAULT_VOLUME / 2, VideoSoundtrack.gainAt(t, 750), 1e-9)
        assertTrue(VideoSoundtrack.gainAt(t, 19_900) < 0.1)
        val flat = VideoSoundtrack.setFade(VideoSoundtrack.setVolume(t, 0.5), false)
        assertEquals(0.5, VideoSoundtrack.gainAt(flat, 0), 1e-9)
    }

    @Test
    fun volumesStayInRange() {
        val t = VideoSoundtrack.setVolume(VideoSoundtrack.add(video, "s", "S", 60_000), 3.0)
        assertEquals(1.0, t.music!!.volume, 1e-9)
        assertEquals(0.0, VideoSoundtrack.setClipVolume(t, -1.0).clipVolume, 1e-9)
    }

    @Test
    fun musicSurvivesSaving() {
        val t = VideoSoundtrack.setClipVolume(VideoSoundtrack.add(video, "content://song", "Song", 60_000), 0.3)
        assertEquals(t, Json.decodeFromString(VideoTimeline.serializer(), Json.encodeToString(VideoTimeline.serializer(), t)))
        assertEquals(VideoTimeline(), VideoSoundtrack.remove(VideoSoundtrack.add(VideoTimeline(), "s", "S", 60_000)))
    }
}
