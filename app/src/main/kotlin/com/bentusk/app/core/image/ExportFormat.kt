package com.bentusk.app.core.image

import android.graphics.Bitmap

enum class ExportFormat(val mimeType: String, val extension: String, val compress: Bitmap.CompressFormat, val quality: Int) {
    /** Maximum-quality JPEG: what Instagram expects, and it re-encodes anything else anyway. */
    Jpeg("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG, 100),
    /** Lossless, for archiving or other platforms. Pro. */
    Png("image/png", "png", Bitmap.CompressFormat.PNG, 100),
}
