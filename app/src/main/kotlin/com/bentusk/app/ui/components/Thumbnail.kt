package com.bentusk.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Shows a small decoded copy of an image from a content URI or a private file path. */
@Composable
fun Thumbnail(
    source: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxSide: Int = 512,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, source, maxSide) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                fun open() = when (source) {
                    is Uri -> context.contentResolver.openInputStream(source)
                    is File -> source.inputStream()
                    is String -> File(source).inputStream()
                    else -> null
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                open()?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap, contentDescription, modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
