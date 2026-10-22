package com.bentusk.app.core.image

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.bentusk.engine.ExifOrientation
import com.bentusk.engine.PxSize
import com.bentusk.engine.SourceGeometry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class UnreadablePhotoException(uri: Uri, cause: Throwable? = null) : IOException("Cannot read photo $uri", cause)

/** Reads photos from content URIs at exactly the resolution a render needs. */
@Singleton
class PhotoLoader @Inject constructor(@ApplicationContext private val context: Context) {

    /** Reads the photo's size and EXIF orientation without decoding pixels. */
    suspend fun probe(uri: Uri): SourceGeometry = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; success shows up in outWidth/outHeight instead.
        val stream = context.contentResolver.openInputStream(uri) ?: throw UnreadablePhotoException(uri)
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnreadablePhotoException(uri)

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        SourceGeometry(PxSize(bounds.outWidth, bounds.outHeight), ExifOrientation.fromExif(orientation))
    }

    /**
     * Decodes the raw (not yet oriented) pixels, subsampled by [sampleSize]. Colours are converted to sRGB,
     * the colour space Instagram displays, so wide-gamut photos keep their intended look.
     */
    suspend fun decode(uri: Uri, sampleSize: Int): Bitmap = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inMutable = false
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw UnreadablePhotoException(uri)
        } catch (e: OutOfMemoryError) {
            throw UnreadablePhotoException(uri, e)
        }
    }

    /** Largest bitmap, in pixels, that is safe to hold for a render on this device. */
    fun renderBudgetPixels(): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        val heapMb = am?.largeMemoryClass ?: 256
        val bytes = heapMb.toLong() * 1024 * 1024
        // The source bitmap gets about a third of the heap; tiles, the blur and the UI share the rest.
        return bytes / 3 / 4
    }
}
