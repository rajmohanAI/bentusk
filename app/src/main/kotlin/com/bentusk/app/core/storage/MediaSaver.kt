package com.bentusk.app.core.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.bentusk.app.core.image.ExportFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Saves exports into the "Carowsle" album of the system gallery. */
@Singleton
class MediaSaver @Inject constructor(@ApplicationContext private val context: Context) {

    /** True when the Android version needs the legacy storage permission to write to the gallery. */
    val needsLegacyPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun save(bitmap: Bitmap, displayName: String, format: ExportFormat): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(bitmap, displayName, format)
        else saveLegacy(bitmap, displayName, format)
    }

    private fun saveScoped(bitmap: Bitmap, displayName: String, format: ExportFormat): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Gallery refused $displayName")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(format.compress, format.quality, out)) throw IOException("Encoding failed")
            } ?: throw IOException("Cannot open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(bitmap: Bitmap, displayName: String, format: ExportFormat): Uri {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create $dir")
        val file = File(dir, displayName)
        file.outputStream().use { out ->
            if (!bitmap.compress(format.compress, format.quality, out)) throw IOException("Encoding failed")
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mimeType), null)
        return uri ?: Uri.fromFile(file)
    }

    companion object {
        const val ALBUM = "Carowsle"
    }
}
