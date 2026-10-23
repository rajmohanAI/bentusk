package com.bentusk.app.core.image

import android.graphics.Bitmap
import android.graphics.ColorSpace
import com.bentusk.engine.Lanczos
import com.bentusk.engine.PxSize
import com.bentusk.engine.SourceGeometry

/** Shrinks decoded photos with Lanczos-3 before export, so they are drawn at about 1:1 instead of skipping pixels. */
object Resampler {

    /**
     * Returns [photo] shrunk to the size it will be drawn at, [rawToCanvas] canvas pixels per full-resolution pixel,
     * or [photo] itself when it is already close to that size or the shrunk copy would not fit in [budgetPixels]
     * alongside it. When a copy is made, [photo] is recycled.
     */
    fun prepare(photo: Bitmap, geometry: SourceGeometry, rawToCanvas: Double, budgetPixels: Long): Bitmap {
        val decoded = PxSize(photo.width, photo.height)
        val target = Lanczos.targetSize(geometry.raw, decoded, rawToCanvas) ?: return photo
        // Both bitmaps are held while filtering; past the budget, the plain filtered draw is the safer choice.
        if (decoded.pixels + target.pixels > budgetPixels * 3 / 2) return photo
        val out = try {
            Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888, photo.hasAlpha(), photo.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
        } catch (e: OutOfMemoryError) {
            return photo
        }
        val width = photo.width
        Lanczos.resize(
            width, photo.height, target.width, target.height,
            readRow = { y, row -> photo.getPixels(row, 0, width, 0, y, width, 1) },
            writeRow = { y, row -> out.setPixels(row, 0, target.width, 0, y, target.width, 1) },
        )
        photo.recycle()
        return out
    }
}
