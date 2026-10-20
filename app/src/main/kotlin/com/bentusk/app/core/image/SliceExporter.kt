package com.bentusk.app.core.image

import android.graphics.Bitmap
import android.net.Uri
import com.bentusk.app.core.storage.MediaSaver
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.ExportNaming
import com.bentusk.engine.Framing
import com.bentusk.engine.FramingMath
import com.bentusk.engine.PxSize
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.SourceGeometry
import com.bentusk.engine.Tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext

/** Everything needed to export one sliced photo. [framing] is in export-canvas pixels. */
data class SliceJob(
    val photo: Uri,
    val geometry: SourceGeometry,
    val layout: SliceLayout,
    val framing: Framing,
    val backdrop: Backdrop,
    val frame: TileFrame?,
    val format: ExportFormat,
    val filePrefix: String,
)

data class ExportedTile(val tile: Tile, val uri: Uri, val fileName: String)

sealed interface ExportProgress {
    data class Working(val done: Int, val total: Int) : ExportProgress
    data class Finished(val tiles: List<ExportedTile>, val millis: Long) : ExportProgress
}

/**
 * The shared pipeline: decode at the needed resolution, render each tile straight from the source,
 * encode at full quality and save to the gallery, one tile at a time.
 */
@Singleton
class SliceExporter @Inject constructor(
    private val loader: PhotoLoader,
    private val saver: MediaSaver,
) {
    fun export(job: SliceJob): Flow<ExportProgress> = flow {
        val started = System.currentTimeMillis()
        val canvas = job.layout.canvas
        val upright = job.geometry.upright
        val framing = FramingMath.clamp(upright, canvas, job.framing)
        val scale = FramingMath.scale(upright, canvas, framing)

        val plan = DecodePlan.forRender(upright, scale, loader.renderBudgetPixels())
        val photo = loader.decode(job.photo, plan.sampleSize)
        val decoded = PxSize(photo.width, photo.height)
        val transform = job.geometry.decodedToCanvas(decoded, canvas, framing)
        val backdrop = PreparedBackdrop.prepare(job.backdrop, photo, job.geometry, canvas)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(started))
        val sequence = job.layout.postingSequence
        val results = ArrayList<ExportedTile>(sequence.size)
        emit(ExportProgress.Working(0, sequence.size))
        try {
            for (tile in sequence) {
                currentCoroutineContext().ensureActive()
                val bitmap: Bitmap = TileRenderer.render(photo, transform, tile.rect, backdrop, job.frame)
                try {
                    val name = ExportNaming.tileFileName(job.filePrefix, stamp, tile, sequence.size, job.format.extension)
                    results += ExportedTile(tile, saver.save(bitmap, name, job.format), name)
                } finally {
                    bitmap.recycle()
                }
                emit(ExportProgress.Working(results.size, sequence.size))
            }
        } finally {
            backdrop.recycle()
            photo.recycle()
        }
        emit(ExportProgress.Finished(results, System.currentTimeMillis() - started))
    }.flowOn(Dispatchers.Default)
}
