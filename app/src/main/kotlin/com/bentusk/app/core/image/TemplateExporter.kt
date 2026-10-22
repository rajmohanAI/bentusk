package com.bentusk.app.core.image

import android.net.Uri
import com.bentusk.app.core.storage.MediaSaver
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.ExportNaming
import com.bentusk.engine.Framing
import com.bentusk.engine.FramingMath
import com.bentusk.engine.SlotMath
import com.bentusk.engine.SourceGeometry
import com.bentusk.engine.Template
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

/** A photo chosen for one template slot. [framing] is in slot pixels at export resolution. */
data class SlotSource(val uri: Uri, val geometry: SourceGeometry, val framing: Framing)

/** Everything needed to export a filled template. [photos] has one entry per photo slot; null leaves it empty. */
data class TemplateJob(
    val template: Template,
    val photos: List<SlotSource?>,
    val texts: List<String>,
    val background: Int,
    val format: ExportFormat,
) {
    val layout get() = template.layout()
}

/** Decodes every photo once, sharing the memory budget, then renders and saves the tiles one by one. */
@Singleton
class TemplateExporter @Inject constructor(
    private val loader: PhotoLoader,
    private val saver: MediaSaver,
) {
    fun export(job: TemplateJob): Flow<ExportProgress> = flow {
        val started = System.currentTimeMillis()
        val layout = job.layout
        val sequence = layout.postingSequence
        emit(ExportProgress.Working(0, sequence.size))

        val budget = SlotMath.budgetPerPhoto(loader.renderBudgetPixels(), job.photos.count { it != null })
        val decoded = ArrayList<SlotPhoto?>(job.photos.size)
        try {
            job.photos.forEachIndexed { index, source ->
                if (source == null) {
                    decoded += null
                    return@forEachIndexed
                }
                val slot = job.template.slotRect(index, layout)
                val framing = FramingMath.clamp(source.geometry.upright, slot.size, source.framing)
                val plan = DecodePlan.forRender(source.geometry.upright, SlotMath.scale(source.geometry, slot, framing), budget)
                decoded += SlotPhoto(loader.decode(source.uri, plan.sampleSize), source.geometry, framing)
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(started))
            val prefix = "bentusk_${job.template.id.replace('-', '_')}"
            val results = ArrayList<ExportedTile>(sequence.size)
            for (tile in sequence) {
                currentCoroutineContext().ensureActive()
                val bitmap = TemplateRenderer.renderTile(tile.rect, job.template, layout, job.background, decoded, job.texts)
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
            decoded.forEach { it?.bitmap?.recycle() }
        }
    }.flowOn(Dispatchers.Default)
}
