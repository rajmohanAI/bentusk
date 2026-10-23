package com.bentusk.app.core.image

import android.net.Uri
import com.bentusk.app.core.storage.MediaSaver
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.Design
import com.bentusk.engine.ExportNaming
import com.bentusk.engine.FramingMath
import com.bentusk.engine.SlotMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A finished design to save as Instagram-ready tiles. */
data class DesignJob(val design: Design, val format: ExportFormat)

/** Decodes every photo once, sharing the memory budget, then renders and saves the tiles one by one. */
@Singleton
class DesignExporter @Inject constructor(
    private val loader: PhotoLoader,
    private val saver: MediaSaver,
) {
    fun export(job: DesignJob): Flow<ExportProgress> = flow {
        val started = System.currentTimeMillis()
        val design = job.design
        val layout = design.layout()
        val sequence = layout.postingSequence
        emit(ExportProgress.Working(0, sequence.size))

        val withPhotos = design.photos.filter { it.uri != null }
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
                val bitmap = DesignRenderer.renderTile(tile.rect, design, layout, decoded)
                try {
                    val name = ExportNaming.tileFileName(prefix, stamp, tile, sequence.size, job.format.extension)
                    results += ExportedTile(tile, saver.save(bitmap, name, job.format), name)
                } finally {
                    bitmap.recycle()
                }
                emit(ExportProgress.Working(results.size, sequence.size))
            }
            emit(ExportProgress.Finished(results, System.currentTimeMillis() - started))
        } finally {
            decoded.values.forEach { it.bitmap.recycle() }
        }
    }.flowOn(Dispatchers.Default)
}
