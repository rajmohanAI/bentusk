package com.bentusk.app.core.ai

import android.net.Uri
import android.util.Log
import com.bentusk.app.core.image.PhotoLoader
import com.bentusk.engine.DecodePlan
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Names what is in a photo ("Beach", "Coffee", "Dog") with ML Kit's bundled model.
 * It runs entirely on the phone, so photos never leave it.
 */
@Singleton
class PhotoLabeler @Inject constructor(private val loader: PhotoLoader) {

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE).build())
    }

    /** Labels across [uris], most confident first, without repeats. Failures give an empty list. */
    suspend fun labels(uris: List<Uri>): List<String> {
        val all = ArrayList<ImageLabel>()
        for (uri in uris.take(MAX_PHOTOS)) {
            try {
                val geometry = loader.probe(uri)
                val bitmap = loader.decode(uri, DecodePlan.forPreview(geometry.upright, SIDE).sampleSize)
                val rotation = when (geometry.orientation.exifValue) {
                    6 -> 90
                    3 -> 180
                    8 -> 270
                    else -> 0
                }
                all += process(InputImage.fromBitmap(bitmap, rotation))
                bitmap.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "Labelling failed for $uri", e)
            }
        }
        return all.sortedByDescending { it.confidence }.map { it.text }.distinct()
    }

    private suspend fun process(image: InputImage): List<ImageLabel> = suspendCancellableCoroutine { cont ->
        labeler.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener {
                Log.w(TAG, "Labeller error", it)
                cont.resume(emptyList())
            }
    }

    companion object {
        private const val TAG = "PhotoLabeler"
        private const val MIN_CONFIDENCE = 0.6f
        private const val MAX_PHOTOS = 4
        private const val SIDE = 640
    }
}
