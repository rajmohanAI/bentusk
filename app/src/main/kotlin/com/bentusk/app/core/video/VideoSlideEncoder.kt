package com.bentusk.app.core.video

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.bentusk.engine.Framing
import com.bentusk.engine.InstagramSpec
import com.bentusk.engine.PxSize
import com.bentusk.engine.VideoClip
import com.bentusk.engine.VideoSlides
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Encodes video slides as H.264 video and AAC sound in an MP4, in standard dynamic range (HDR clips are
 * tone-mapped), at a steady 30 fps. A slide is either a trimmed clip, scaled to cover the slide and zoomed and
 * panned as framed in the editor, or a still picture held for a few seconds; either way the slide's text and
 * stickers are laid over it by an overlay, which may change from frame to frame.
 */
@OptIn(UnstableApi::class)
@Singleton
class VideoSlideEncoder @Inject constructor(@ApplicationContext private val context: Context) {

    /** Encodes [clip] of the video at [source] under [overlay]. */
    suspend fun encodeClip(
        source: Uri,
        clip: VideoClip,
        framing: Framing,
        slide: PxSize,
        overlay: TextureOverlay,
        output: File,
        onProgress: (Float) -> Unit = {},
    ) {
        val item = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs)
                    .build(),
            )
            .build()
        val matrix = Matrix().apply { setValues(VideoSlides.ndcMatrix(framing, slide)) }
        val effects = listOf<Effect>(
            FrameDropEffect.createDefaultFrameDropEffect(InstagramSpec.VIDEO_FPS.toFloat()),
            Presentation.createForWidthAndHeight(slide.width, slide.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
            MatrixTransformation { matrix },
            OverlayEffect(ImmutableList.of(overlay)),
        )
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(clip.muted)
            .setEffects(Effects(emptyList(), effects))
            .build()
        transform(edited, output, onProgress)
    }

    /** Encodes the picture in [image] (exactly [slide] in size) held for [durationMs], under [overlay]. */
    suspend fun encodeStill(
        image: File,
        durationMs: Long,
        slide: PxSize,
        overlay: TextureOverlay,
        output: File,
        onProgress: (Float) -> Unit = {},
    ) {
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(image))
            .setMimeType(MimeTypes.IMAGE_PNG)
            .setImageDurationMs(durationMs)
            .build()
        val effects = listOf<Effect>(
            Presentation.createForWidthAndHeight(slide.width, slide.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
            OverlayEffect(ImmutableList.of(overlay)),
        )
        val edited = EditedMediaItem.Builder(item)
            .setFrameRate(InstagramSpec.VIDEO_FPS)
            .setEffects(Effects(emptyList(), effects))
            .build()
        transform(edited, output, onProgress)
    }

    /** Transformer must be driven from a thread with a Looper, so the work runs on the main thread. */
    private suspend fun transform(edited: EditedMediaItem, output: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.Main) {
        val composition = Composition.Builder(EditedMediaItemSequence(edited)).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            }
        }.build()

        coroutineScope {
            var transformer: Transformer? = null
            val progress = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    if (transformer?.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    delay(PROGRESS_POLL_MS)
                }
            }
            try {
                suspendCancellableCoroutine { cont ->
                    val t = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(
                            DefaultEncoderFactory.Builder(context)
                                .setRequestedVideoEncoderSettings(
                                    VideoEncoderSettings.Builder().setBitrate(InstagramSpec.VIDEO_BITRATE).build(),
                                )
                                .setEnableFallback(true)
                                .build(),
                        )
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (cont.isActive) cont.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer = t
                    // Cancellation can arrive on any thread; Transformer only accepts calls on its own.
                    cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { t.cancel() } }
                    t.start(composition, output.absolutePath)
                }
            } finally {
                progress.cancel()
            }
        }
    }

    private companion object {
        const val PROGRESS_POLL_MS = 250L
    }
}
