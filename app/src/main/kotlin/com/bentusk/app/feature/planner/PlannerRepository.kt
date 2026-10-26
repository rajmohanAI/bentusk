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
import com.bentusk.app.core.video.VideoProbe
import com.bentusk.engine.Affine
import com.bentusk.engine.ArchivePost
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.InstagramArchive
import com.bentusk.engine.PxSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
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
    private val videos: VideoProbe,
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

    /**
     * Adds the newest posts from the user's own Instagram archive ZIP under the plan's photos, since they are
     * already on the profile. The ZIP is read twice as a stream, first for the post lists and then for the covers,
     * so nothing but one cover at a time is written out. Posts already in the plan are skipped.
     */
    suspend fun importArchive(planId: Long, zip: Uri): ArchiveImport = withContext(Dispatchers.IO) {
        val posts = try {
            val found = ArrayList<ArchivePost>()
            readZip(zip) { name, stream ->
                if (InstagramArchive.isIndex(name)) {
                    val text = stream.readCapped(MAX_INDEX_BYTES)?.toString(Charsets.UTF_8)
                    if (text != null) found += InstagramArchive.parse(name, text)
                }
            }
            InstagramArchive.newestFirst(found)
        } catch (e: IOException) {
            return@withContext ArchiveImport.Unreadable
        }
        if (posts.isEmpty()) return@withContext ArchiveImport.NoPosts

        val have = dao.itemsNow(planId).map { it.sourceUri }.toSet()
        val wanted = posts.filter { archiveUri(it.cover) !in have }
        val byCover = wanted.associateBy { it.cover }
        val thumbs = HashMap<String, String>()
        try {
            readZip(zip) { name, stream ->
                val post = byCover[name] ?: return@readZip
                if (name in thumbs) return@readZip
                val extension = name.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "jpg"
                val temp = File(context.cacheDir, "archive_cover.$extension")
                try {
                    temp.outputStream().use { stream.copyTo(it) }
                    val path = if (post.isVideo) videoThumbnail(Uri.fromFile(temp)) else makeThumbnail(Uri.fromFile(temp))
                    thumbs[name] = path
                } catch (e: Exception) {
                    // One unreadable cover should not stop the rest.
                    if (e is CancellationException) throw e
                } finally {
                    temp.delete()
                }
            }
        } catch (e: IOException) {
            // Keep whatever covers were read before the archive broke off.
        }

        val added = wanted.filter { it.cover in thumbs }
        val start = dao.itemsNow(planId).size
        dao.insertItems(
            added.mapIndexed { index, post ->
                PlanItemEntity(planId = planId, sourceUri = archiveUri(post.cover), thumbPath = thumbs.getValue(post.cover), position = start + index)
            },
        )
        ArchiveImport.Done(added = added.size, failed = wanted.size - added.size, alreadyThere = posts.size - wanted.size)
    }

    /** Calls [onEntry] for each file in the ZIP at [zip], with a stream that reads just that file. */
    private suspend fun readZip(zip: Uri, onEntry: suspend (name: String, stream: InputStream) -> Unit) {
        val input = context.contentResolver.openInputStream(zip) ?: throw IOException("Cannot open $zip")
        ZipInputStream(input.buffered()).use { zin ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zin.nextEntry ?: break
                if (!entry.isDirectory) onEntry(entry.name, zin)
                zin.closeEntry()
            }
        }
    }

    /** Reads at most [limit] bytes, or null when the stream holds more, so a hostile ZIP cannot exhaust memory. */
    private fun InputStream.readCapped(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
    }

    private suspend fun videoThumbnail(uri: Uri): String {
        val poster = videos.read(uri, THUMB_LONG_SIDE).poster
        try {
            val file = File(thumbDir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { poster.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return file.absolutePath
        } finally {
            poster.recycle()
        }
    }

    suspend fun removeItem(item: PlanItemEntity) {
        dao.deleteItem(item.id)
        File(item.thumbPath).delete()
    }

    suspend fun saveOrder(ids: List<Long>) = dao.reorder(ids)

    suspend fun setPinned(item: PlanItemEntity, pinned: Boolean, idsInOrder: List<Long>) = dao.pin(item.id, pinned, idsInOrder)

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

        /** Post lists in real archives are well under a megabyte per thousand posts. */
        private const val MAX_INDEX_BYTES = 32 * 1024 * 1024

        /** Marks plan photos that came from the archive, by their path inside it. */
        fun archiveUri(path: String) = "archive:$path"
    }
}

/** How an archive import went. */
sealed interface ArchiveImport {
    data class Done(val added: Int, val failed: Int, val alreadyThere: Int) : ArchiveImport

    /** The file is a ZIP but lists no posts; usually an export requested in HTML format. */
    data object NoPosts : ArchiveImport

    /** The file could not be read as a ZIP at all. */
    data object Unreadable : ArchiveImport
}
