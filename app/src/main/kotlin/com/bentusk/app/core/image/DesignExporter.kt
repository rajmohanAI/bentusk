package com.bentusk.app.core.image

import android.content.Context
import android.net.Uri
import com.bentusk.app.core.storage.MediaSaver
import com.bentusk.app.core.video.VideoSlideEncoder
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.Design
import com.bentusk.engine.ExportNaming
import com.bentusk.engine.Fill
import com.bentusk.engine.FramingMath
import com.bentusk.engine.PhotoLayer
import com.bentusk.engine.SlotMath
import com.bentusk.engine.Tile
import com.bentusk.engine.VideoSlides
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** A finished design to save as Instagram-ready tiles. */
data class DesignJob(val design: Design, val format: ExportFormat)

/**
 * Decodes every photo once, sharing the memory budget, then renders and saves the tiles one by one.
 * A slide holding a video clip is encoded as an MP4 with the slide's other layers drawn over the clip.
 */
@Singleton
class DesignExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: PhotoLoader,
    private val saver: MediaSaver,
    private val encoder: VideoSlideEncoder,
) {
    fun export(job: DesignJob): Flow<ExportProgress> = flow {
        val started = System.currentTimeMillis()
        val design = job.design
        val layout = design.layout()
        val sequence = layout.postingSequence
        emit(ExportProgress.Working(0, sequence.size))

        val withPhotos = design.stills.filter { it.uri != null }
        val budget = SlotMath.budgetPerPhoto(loader.renderBudgetPixels(), withPhotos.size)
        val decoded = HashMap<Long, SlotPhoto>()
        try {
            for (layer in withPhotos) {
                val uri = Uri.parse(layer.uri)
                val geometry = loader.probe(uri)
                val slot = design.rectPx(layer, layout)
                val framing = FramingMath.clamp(geometry.upright, slot.size, layer.framing)
                val scale = SlotMath.scale(geometry, slot, framing)
                val plan = DecodePlan.forRender(geometry.upright, scale, budget)
                val bitmap = Resampler.prepare(loader.decode(uri, plan.sampleSize), geometry, scale, budget)
                decoded[layer.id] = SlotPhoto(bitmap, geometry, framing)
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(started))
            val prefix = "carowsle_" + (design.templateId?.replace('-', '_') ?: "design")
            val results = ArrayList<ExportedTile>(sequence.size)
            for (tile in sequence) {
                currentCoroutineContext().ensureActive()
                val video = VideoSlides.videoOn(design, tile.index)
                if (video != null) {
                    results += exportVideo(design, video, tile, prefix, stamp, sequence.size, decoded)
                } else {
                    val bitmap = DesignRenderer.renderTile(tile.rect, design, layout, decoded)
                    try {
                        val name = ExportNaming.tileFileName(prefix, stamp, tile, sequence.size, job.format.extension)
                        results += ExportedTile(tile, saver.save(bitmap, name, job.format), name)
                    } finally {
                        bitmap.recycle()
                    }
                }
                emit(ExportProgress.Working(results.size, sequence.size))
            }
            emit(ExportProgress.Finished(results, System.currentTimeMillis() - started))
        } finally {
            decoded.values.forEach { it.bitmap.recycle() }
        }
    }.flowOn(Dispatchers.Default)

    /** Encodes a video slide: the clip under everything stacked above it on that slide. */
    private suspend fun exportVideo(
        design: Design,
        video: PhotoLayer,
        tile: Tile,
        prefix: String,
        stamp: String,
        total: Int,
        photos: Map<Long, SlotPhoto>,
    ): ExportedTile {
        val clip = requireNotNull(video.video)
        val layout = design.layout()
        // Only the layers above the clip are drawn, on a clear background, so the clip shows through everywhere else.
        val above = design.layers.dropWhile { it.id != video.id }.drop(1)
        val overlay = DesignRenderer.renderTile(tile.rect, design.copy(fill = Fill(0), layers = above), layout, photos)
        val name = ExportNaming.tileFileName(prefix, stamp, tile, total, "mp4")
        val temp = File(context.cacheDir, name)
        try {
            encoder.encode(
                source = Uri.parse(video.uri),
                clip = clip,
                framing = video.framing.copy(rotationDegrees = 0.0, zoom = maxOf(1.0, video.framing.zoom)),
                slide = tile.rect.size,
                overlay = overlay,
                output = temp,
            )
            return ExportedTile(tile, saver.saveVideo(temp, name), name, isVideo = true)
        } finally {
            overlay.recycle()
            temp.delete()
        }
    }
}
