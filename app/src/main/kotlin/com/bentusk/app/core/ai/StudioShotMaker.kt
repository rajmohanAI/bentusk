package com.bentusk.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import com.bentusk.app.core.image.PhotoLoader
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.ExifOrientation
import com.bentusk.engine.PxSize
import com.bentusk.engine.StudioBackdrop
import com.bentusk.engine.StudioPlacement
import com.bentusk.engine.StudioShot
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** No product or person could be found in the photo. */
class NoSubjectException : Exception("No subject found")

/**
 * Makes a studio shot on the phone: ML Kit cuts the subject out of the photo, then it is placed on a seamless
 * backdrop with a soft glow behind it and a contact shadow under it. Photos never leave the device. The result is
 * saved as a new picture in the app's own storage, so the original photo is untouched and undo brings it back.
 */
@Singleton
class StudioShotMaker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: PhotoLoader,
) {

    private val segmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    }

    /** Makes a studio shot of [uri] sized for a [slot]-pixel frame and returns the new picture's uri. */
    suspend fun make(uri: Uri, slot: PxSize, backdrop: StudioBackdrop): Uri {
        val geometry = loader.probe(uri)
        val decoded = loader.decode(uri, DecodePlan.forPreview(geometry.upright, SEGMENT_SIDE).sampleSize)
        val photo = withContext(Dispatchers.Default) { upright(decoded, geometry.orientation) }
        val cutout = segment(photo)
        if (photo !== cutout) photo.recycle()
        return withContext(Dispatchers.Default) {
            try {
                val alpha = IntArray(cutout.width * cutout.height).also { cutout.getPixels(it, 0, cutout.width, 0, 0, cutout.width, cutout.height) }
                for (i in alpha.indices) alpha[i] = alpha[i] ushr 24
                val bounds = StudioShot.subjectBounds(alpha, cutout.width, cutout.height) ?: throw NoSubjectException()
                val out = StudioShot.outputSize(slot)
                val shot = compose(cutout, StudioShot.place(bounds, cutout.height, out), out, backdrop)
                save(shot).also { shot.recycle() }
            } finally {
                cutout.recycle()
            }
        }
    }

    private fun upright(bitmap: Bitmap, orientation: ExifOrientation): Bitmap {
        if (orientation == ExifOrientation.Normal) return bitmap
        val transform = orientation.toUpright(bitmap.width.toDouble(), bitmap.height.toDouble())
        val w = if (orientation.swapsAxes) bitmap.height else bitmap.width
        val h = if (orientation.swapsAxes) bitmap.width else bitmap.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, Matrix().apply { setValues(transform.toMatrixValues()) }, Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
        return out
    }

    private suspend fun segment(photo: Bitmap): Bitmap = suspendCancellableCoroutine { cont ->
        segmenter.process(InputImage.fromBitmap(photo, 0))
            .addOnSuccessListener { result ->
                val fg = result.foregroundBitmap
                if (fg == null) cont.resumeWithException(NoSubjectException()) else cont.resume(fg)
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun compose(cutout: Bitmap, placement: StudioPlacement, out: PxSize, backdrop: StudioBackdrop): Bitmap {
        val shot = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(shot)
        val full = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
        canvas.drawRect(full, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, out.height.toFloat(), backdrop.top, backdrop.bottom, Shader.TileMode.CLAMP)
        })
        // A soft key light on the sweep behind the subject.
        val glow = placement.glow
        val glowColor = if (backdrop.dark) 0x38FFFFFF else 0x66FFFFFF
        canvas.drawRect(full, Paint(Paint.DITHER_FLAG).apply {
            shader = RadialGradient(
                glow.centerX.toFloat(), glow.centerY.toFloat(), (glow.width / 2).toFloat(),
                glowColor, glowColor and 0x00FFFFFF, Shader.TileMode.CLAMP,
            )
        })
        placement.shadow?.let { s ->
            val dark = if (backdrop.dark) 0x99000000.toInt() else 0x66000000
            canvas.save()
            // A circle's radial gradient squashed into the shadow's oval keeps its soft edge all the way round.
            canvas.translate(s.centerX.toFloat(), s.centerY.toFloat())
            canvas.scale(1f, (s.height / s.width).toFloat())
            val r = (s.width / 2).toFloat()
            canvas.drawCircle(0f, 0f, r, Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                val half = ((dark ushr 24) / 2) shl 24
                shader = RadialGradient(0f, 0f, r, intArrayOf(dark, half, 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            })
            canvas.restore()
        }
        val m = Matrix().apply {
            setScale(placement.scale.toFloat(), placement.scale.toFloat())
            postTranslate(placement.left.toFloat(), placement.top.toFloat())
        }
        canvas.drawBitmap(cutout, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return shot
    }

    private fun save(shot: Bitmap): Uri {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "studio-${UUID.randomUUID()}.jpg")
        file.outputStream().use { shot.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return Uri.fromFile(file)
    }

    companion object {
        private const val SEGMENT_SIDE = 2048
        private const val JPEG_QUALITY = 95
        private const val DIR = "studio-shots"
    }
}
