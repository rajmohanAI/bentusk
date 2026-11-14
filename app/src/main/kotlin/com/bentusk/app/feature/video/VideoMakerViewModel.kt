package com.bentusk.app.feature.video

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentusk.app.core.image.PhotoLoader
import com.bentusk.app.core.storage.MediaSaver
import com.bentusk.app.core.video.VideoProbe
import com.bentusk.app.core.video.VideoSlideEncoder
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.ExifOrientation
import com.bentusk.engine.VideoShape
import com.bentusk.engine.VideoTimeline
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class VideoMakerState(
    val timeline: VideoTimeline = VideoTimeline(),
    val selected: Long? = null,
    /** Small pictures of each source, by uri, for the timeline strip. */
    val thumbs: Map<String, Bitmap> = emptyMap(),
    val adding: Boolean = false,
    /** 0..1 while exporting, null otherwise. */
    val progress: Float? = null,
    /** The saved video, once an export has finished. */
    val saved: Uri? = null,
    val failed: Boolean = false,
    val unreadable: Int = 0,
    val canUndo: Boolean = false,
) {
    val selectedClip get() = selected?.let(timeline::clip)
}

/**
 * The video maker: clips and photos joined one after another, trimmed, split and reordered, then encoded on the
 * phone into one MP4. Nothing is uploaded.
 */
@HiltViewModel
class VideoMakerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videos: VideoProbe,
    private val photos: PhotoLoader,
    private val encoder: VideoSlideEncoder,
    private val saver: MediaSaver,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoMakerState())
    val state: StateFlow<VideoMakerState> = _state.asStateFlow()

    private val undo = ArrayDeque<VideoTimeline>()
    private var exportJob: Job? = null

    private fun edit(change: (VideoTimeline) -> VideoTimeline) {
        val s = _state.value
        val next = change(s.timeline)
        if (next == s.timeline) return
        undo.addLast(s.timeline)
        if (undo.size > HISTORY) undo.removeFirst()
        _state.update { it.copy(timeline = next, selected = it.selected?.takeIf { id -> next.clip(id) != null }, saved = null, canUndo = true) }
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        lastTrimmed = null
        _state.update {
            it.copy(timeline = previous, selected = it.selected?.takeIf { id -> previous.clip(id) != null }, saved = null, canUndo = undo.isNotEmpty())
        }
    }

    /** Adds picked clips and photos after the selected piece (or at the end), in the order they were picked. */
    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(adding = true) }
            var timeline = _state.value.timeline
            var after = _state.value.selected
            var failed = 0
            val thumbs = HashMap<String, Bitmap>()
            for (uri in uris) {
                if (!timeline.canAdd) break
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val key = uri.toString()
                val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
                try {
                    if (isVideo) {
                        val info = videos.read(uri, THUMB_SIDE)
                        thumbs[key] = info.poster
                        timeline = timeline.addVideo(key, info.durationMs, after)
                    } else {
                        thumbs[key] = upright(uri, THUMB_SIDE)
                        timeline = timeline.addPhoto(key, afterId = after)
                    }
                    // The next pick goes after this one, which has the newest id.
                    if (after != null) after = timeline.clips.maxOf { it.id }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot add $uri", e)
                    failed++
                }
            }
            val added = timeline
            edit { added }
            _state.update {
                it.copy(
                    thumbs = it.thumbs + thumbs,
                    adding = false,
                    unreadable = it.unreadable + failed,
                    selected = it.selected ?: added.clips.firstOrNull()?.id,
                )
            }
        }
    }

    private suspend fun upright(uri: Uri, side: Int): Bitmap {
        val geometry = photos.probe(uri)
        val raw = photos.decode(uri, DecodePlan.forPreview(geometry.upright, side).sampleSize)
        if (geometry.orientation == ExifOrientation.Normal) return raw
        return withContext(Dispatchers.Default) {
            val swap = geometry.orientation.swapsAxes
            val out = Bitmap.createBitmap(if (swap) raw.height else raw.width, if (swap) raw.width else raw.height, Bitmap.Config.ARGB_8888)
            val m = geometry.orientation.toUpright(raw.width.toDouble(), raw.height.toDouble())
            Canvas(out).drawBitmap(raw, Matrix().apply { setValues(m.toMatrixValues()) }, Paint(Paint.FILTER_BITMAP_FLAG))
            raw.recycle()
            out
        }
    }

    private val previews = LinkedHashMap<String, Bitmap>()

    /** A screen-sized picture of a photo, for the preview while that photo is on. The last few are kept. */
    suspend fun preview(uri: String): Bitmap? {
        previews[uri]?.let { return it }
        val bitmap = runCatching { upright(Uri.parse(uri), PREVIEW_SIDE) }.getOrNull() ?: return null
        previews[uri] = bitmap
        if (previews.size > PREVIEWS_KEPT) previews.remove(previews.keys.first())
        return bitmap
    }

    fun select(id: Long?) = _state.update { it.copy(selected = id) }

    fun setShape(shape: VideoShape) = edit { it.copy(shape = shape) }

    fun trim(start: Long, end: Long) {
        val id = _state.value.selected ?: return
        // A slider drag is many small trims; keep one undo step for it by replacing the last entry.
        val s = _state.value
        val next = s.timeline.trim(id, start, end)
        if (next == s.timeline) return
        if (lastTrimmed != id) {
            undo.addLast(s.timeline)
            if (undo.size > HISTORY) undo.removeFirst()
        }
        lastTrimmed = id
        _state.update { it.copy(timeline = next, saved = null, canUndo = true) }
    }

    /** Ends a slider drag, so the next trim starts a new undo step. */
    fun trimDone() {
        lastTrimmed = null
    }

    private var lastTrimmed: Long? = null

    fun toggleMute() {
        val c = _state.value.selectedClip ?: return
        edit { it.setMuted(c.id, !c.muted) }
    }

    fun move(by: Int) {
        val id = _state.value.selected ?: return
        edit { it.move(id, by) }
    }

    fun duplicate() {
        val id = _state.value.selected ?: return
        edit { it.duplicate(id) }
    }

    /** Splits the selected clip at [atMs] into its played part, where the preview's playhead is. */
    fun split(atMs: Long) {
        val id = _state.value.selected ?: return
        edit { it.split(id, atMs) }
    }

    fun remove() {
        val id = _state.value.selected ?: return
        val index = _state.value.timeline.clips.indexOfFirst { it.id == id }
        edit { it.remove(id) }
        val clips = _state.value.timeline.clips
        _state.update { it.copy(selected = clips.getOrNull(index.coerceAtMost(clips.size - 1))?.id) }
    }

    fun consumeNotices() = _state.update { it.copy(failed = false, unreadable = 0) }

    fun export() {
        val timeline = _state.value.timeline
        if (timeline.isEmpty || exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            _state.update { it.copy(progress = 0f, saved = null, failed = false) }
            val name = "Carowsle_video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
            val temp = File(context.cacheDir, name)
            try {
                encoder.encodeTimeline(timeline, temp) { p -> _state.update { it.copy(progress = p) } }
                val uri = saver.saveVideo(temp, name)
                _state.update { it.copy(progress = null, saved = uri) }
            } catch (e: CancellationException) {
                _state.update { it.copy(progress = null) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Video export failed", e)
                _state.update { it.copy(progress = null, failed = true) }
            } finally {
                temp.delete()
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
    }

    private companion object {
        const val TAG = "VideoMaker"
        const val THUMB_SIDE = 240
        const val PREVIEW_SIDE = 1080
        const val PREVIEWS_KEPT = 3
        const val HISTORY = 50
    }
}
