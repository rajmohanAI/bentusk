package com.bentusk.app.feature.planner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import com.bentusk.app.core.data.PlanEntity
import com.bentusk.app.core.data.PlanItemEntity
import com.bentusk.app.core.data.PlannerDao
import com.bentusk.app.core.image.PhotoLoader
import com.bentusk.engine.Affine
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.PxSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** Grid plans and their photos. Each photo keeps an upright private thumbnail so plans survive restarts. */
@Singleton
class PlannerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PlannerDao,
    private val loader: PhotoLoader,
) {
    private val thumbDir: File get() = File(context.filesDir, "plan_thumbs").apply { mkdirs() }

    val plans: Flow<List<PlanEntity>> = dao.plans()

    fun items(planId: Long): Flow<List<PlanItemEntity>> = dao.items(planId)

    suspend fun ensureDefaultPlan(defaultName: String): Long {
        val existing = dao.plans().first()
        return existing.firstOrNull()?.id ?: dao.insertPlan(PlanEntity(name = defaultName, createdAt = System.currentTimeMillis()))
    }

    suspend fun createPlan(name: String): Long =
        dao.insertPlan(PlanEntity(name = name, createdAt = System.currentTimeMillis()))

    suspend fun deletePlan(planId: Long) {
        dao.itemsNow(planId).forEach { File(it.thumbPath).delete() }
        dao.deletePlan(planId)
    }

    /** Adds photos at the top of the grid, where Instagram shows the newest posts. Returns how many failed. */
    suspend fun addPhotos(planId: Long, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        val thumbs = uris.mapNotNull { uri ->
            runCatching { uri to makeThumbnail(uri) }.getOrElse {
                failed++
                null
            }
        }
        val current = dao.itemsNow(planId)
        current.forEachIndexed { index, item -> dao.setPosition(item.id, index + thumbs.size) }
        dao.insertItems(
            thumbs.mapIndexed { index, (uri, path) ->
                PlanItemEntity(planId = planId, sourceUri = uri.toString(), thumbPath = path, position = index)
            },
        )
        failed
    }

    suspend fun removeItem(item: PlanItemEntity) {
        dao.deleteItem(item.id)
        File(item.thumbPath).delete()
    }

    suspend fun saveOrder(ids: List<Long>) = dao.reorder(ids)

    private suspend fun makeThumbnail(uri: Uri): String {
        val geometry = loader.probe(uri)
        val plan = DecodePlan.forPreview(geometry.upright, THUMB_LONG_SIDE)
        val raw = loader.decode(uri, plan.sampleSize)
        try {
            val upright = geometry.upright
            val k = THUMB_LONG_SIDE.toDouble() / max(upright.width, upright.height)
            val size = PxSize(max(1, (upright.width * k).roundToInt()), max(1, (upright.height * k).roundToInt()))
            val transform = geometry.decodedToUpright(PxSize(raw.width, raw.height)).then(Affine.scale(k))
            val thumb = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            Canvas(thumb).drawBitmap(
                raw,
                Matrix().apply { setValues(transform.toMatrixValues()) },
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val file = File(thumbDir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            thumb.recycle()
            return file.absolutePath
        } finally {
            raw.recycle()
        }
    }

    companion object {
        const val THUMB_LONG_SIDE = 720
    }
}
