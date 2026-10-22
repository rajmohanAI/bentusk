package com.bentusk.app.core.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.bentusk.engine.Design
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A saved design, listed on the Projects tab. */
data class Project(val id: String, val design: Design, val updatedAt: Long, val thumbnail: File?)

/**
 * Drafts saved as small JSON files with a thumbnail beside each, so work survives closing the app.
 * Photos are referenced by URI, not copied, which keeps drafts tiny.
 */
@Singleton
class ProjectStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File get() = File(context.filesDir, "projects").apply { mkdirs() }

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun refresh() {
        _projects.value = withContext(Dispatchers.IO) {
            dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
                runCatching {
                    val id = file.nameWithoutExtension
                    val thumb = File(dir, "$id.jpg").takeIf { it.exists() }
                    Project(id, Design.fromJson(file.readText()), file.lastModified(), thumb)
                }.onFailure { Log.w(TAG, "Skipping unreadable project ${file.name}", it) }.getOrNull()
            }.sortedByDescending { it.updatedAt }
        }
    }

    suspend fun load(id: String): Design? = withContext(Dispatchers.IO) {
        runCatching { Design.fromJson(File(dir, "$id.json").readText()) }.getOrNull()
    }

    suspend fun save(id: String, design: Design, thumbnail: Bitmap?) {
        withContext(Dispatchers.IO) {
            // Write then rename, so a crash mid-save never leaves a half-written draft.
            val tmp = File(dir, "$id.json.tmp")
            tmp.writeText(Design.toJson(design))
            tmp.renameTo(File(dir, "$id.json"))
            thumbnail?.let { bmp ->
                File(dir, "$id.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
        }
        refresh()
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            File(dir, "$id.json").delete()
            File(dir, "$id.jpg").delete()
        }
        refresh()
    }

    companion object {
        private const val TAG = "ProjectStore"
    }
}
