package com.bentusk.app.core.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.bentusk.app.core.image.UnreadablePhotoException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** A clip's length and one frame of it, upright, for drawing in the editor. */
class VideoInfo(val durationMs: Long, val poster: Bitmap)

/** Reads what the editor needs from a video without playing it. */
@Singleton
class VideoProbe @Inject constructor(@ApplicationContext private val context: Context) {

    /** Reads the clip's length and the frame at [atMs], no larger than [maxSide] on its longest side. */
    suspend fun read(uri: Uri, maxSide: Int, atMs: Long = 0): VideoInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (duration == null || duration <= 0) throw UnreadablePhotoException(uri)
            val timeUs = atMs.coerceIn(0, duration) * 1000
            val frame = scaledFrame(retriever, timeUs, maxSide)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw UnreadablePhotoException(uri)
            VideoInfo(duration, shrink(frame, maxSide))
        } catch (e: RuntimeException) {
            throw UnreadablePhotoException(uri, e)
        } finally {
            retriever.release()
        }
    }

    /** Decodes straight to a small frame where the platform can, instead of a full 4K bitmap. */
    private fun scaledFrame(retriever: MediaMetadataRetriever, timeUs: Long, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
        var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation % 180 != 0) w = h.also { h = w }
        val k = minOf(1.0, maxSide.toDouble() / max(w, h))
        return retriever.getScaledFrameAtTime(
            timeUs,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            max(1, (w * k).roundToInt()),
            max(1, (h * k).roundToInt()),
        )
    }

    private fun shrink(frame: Bitmap, maxSide: Int): Bitmap {
        val longest = max(frame.width, frame.height)
        if (longest <= maxSide) return frame
        val k = maxSide.toDouble() / longest
        val small = Bitmap.createScaledBitmap(frame, max(1, (frame.width * k).roundToInt()), max(1, (frame.height * k).roundToInt()), true)
        if (small !== frame) frame.recycle()
        return small
    }
}
