package com.bentusk.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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

/** The first frame of a video, for showing exported video slides next to photos. */
private fun videoFrame(context: Context, uri: Uri, maxSide: Int): ImageBitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
        val k = minOf(1.0, maxSide.toDouble() / maxOf(frame.width, frame.height))
        val small = if (k < 1.0) {
            Bitmap.createScaledBitmap(frame, maxOf(1, (frame.width * k).toInt()), maxOf(1, (frame.height * k).toInt()), true)
        } else {
            frame
        }
        small.asImageBitmap()
    } finally {
        retriever.release()
    }
}.getOrNull()

/** Shows a small decoded copy of an image (or a video's first frame) from a content URI or a private file path. */
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
            }.getOrNull() ?: (source as? Uri)?.let { videoFrame(context, it, maxSide) }
        }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap, contentDescription, modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
