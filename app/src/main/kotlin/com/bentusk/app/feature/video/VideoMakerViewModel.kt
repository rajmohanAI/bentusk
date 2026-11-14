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
import com.bentusk.app.core.video.TimelineOverlay
import com.bentusk.app.core.video.VideoProbe
import com.bentusk.app.core.video.VideoSlideEncoder
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.ExifOrientation
import com.bentusk.engine.TextFont
import com.bentusk.engine.VideoOverlay
import com.bentusk.engine.VideoOverlays
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
    val mode: MakerMode = MakerMode.Clips,
    val selectedOverlay: Long? = null,
    /** Pictures used by image overlays, by uri, for the preview and the export. */
    val overlayImages: Map<String, Bitmap> = emptyMap(),
) {
    val selectedClip get() = selected?.let(timeline::clip)
    val overlay get() = selectedOverlay?.let { id -> timeline.overlays.firstOrNull { it.id == id } }
}

/** Whether the tools below the preview edit the clips or the text and pictures on top. */
enum class MakerMode { Clips, Layers }

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
        live = null
        _state.update { it.copy(timeline = next, saved = null, canUndo = true).valid() }
    }

    /** Drops selections of clips or overlays that are gone. */
    private fun VideoMakerState.valid() = copy(
        selected = selected?.takeIf { timeline.clip(it) != null },
        selectedOverlay = selectedOverlay?.takeIf { id -> timeline.overlays.any { it.id == id } },
    )

    /**
     * An edit made many times during one drag (a slider, a finger moving text). The first change of a drag is one
     * undo step; the rest replace it until [endLive].
     */
    private fun editLive(key: String, change: (VideoTimeline) -> VideoTimeline) {
        val s = _state.value
        val next = change(s.timeline)
        if (next == s.timeline) return
        if (live != key) {
            undo.addLast(s.timeline)
            if (undo.size > HISTORY) undo.removeFirst()
        }
        live = key
        _state.update { it.copy(timeline = next, saved = null, canUndo = true) }
    }

    /** Ends a drag, so the next change starts a new undo step. */
    fun endLive() {
        live = null
    }

    private var live: String? = null

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        live = null
        _state.update { it.copy(timeline = previous, saved = null, canUndo = undo.isNotEmpty()).valid() }
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
        editLive("trim:$id") { it.trim(id, start, end) }
    }

    /** Ends a slider drag, so the next trim starts a new undo step. */
    fun trimDone() = endLive()

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

    // Text and pictures

    fun setMode(mode: MakerMode) = _state.update { it.copy(mode = mode) }

    fun selectOverlay(id: Long?) = _state.update { it.copy(selectedOverlay = id) }

    fun addText(text: String, atMs: Long) {
        if (text.isBlank()) return
        edit { VideoOverlays.addText(it, text.trim(), atMs) }
        _state.update { it.copy(selectedOverlay = it.timeline.overlays.lastOrNull()?.id) }
    }

    fun editText(text: String) = updateOverlay { if (it.isText && text.isNotBlank()) it.copy(text = text.trim()) else it }

    /** Adds the picked picture over the video from [atMs]. */
    fun addImage(uri: Uri, atMs: Long) {
        viewModelScope.launch {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val key = uri.toString()
            val bitmap = runCatching { upright(uri, OVERLAY_SIDE) }.getOrNull()
            if (bitmap == null) {
                _state.update { it.copy(unreadable = it.unreadable + 1) }
                return@launch
            }
            _state.update { it.copy(overlayImages = it.overlayImages + (key to bitmap)) }
            edit { VideoOverlays.addImage(it, key, bitmap.width.toDouble() / bitmap.height, atMs) }
            _state.update { it.copy(selectedOverlay = it.timeline.overlays.lastOrNull()?.id) }
        }
    }

    private fun updateOverlay(change: (VideoOverlay) -> VideoOverlay) {
        val id = _state.value.selectedOverlay ?: return
        edit { VideoOverlays.update(it, id, change) }
    }

    fun setOverlayColor(argb: Int) = updateOverlay { it.copy(argb = argb) }

    fun setOverlayFont(font: TextFont) = updateOverlay { it.copy(font = font) }

    fun toggleBold() = updateOverlay { it.copy(bold = !it.bold) }

    /** A label behind the text in [argb], or none when it already has that one. */
    fun togglePill(argb: Int) = updateOverlay { it.copy(pill = if (it.pill == argb) null else argb) }

    fun setTextSize(size: Double) {
        val id = _state.value.selectedOverlay ?: return
        editLive("size:$id") {
            VideoOverlays.update(it, id) { o -> o.copy(size = size.coerceIn(VideoOverlay.MIN_TEXT_SIZE, VideoOverlay.MAX_TEXT_SIZE)) }
        }
    }

    /** Sets a picture's width as a fraction of the frame, keeping its shape. */
    fun setImageWidth(width: Double) {
        val o = _state.value.overlay ?: return
        val current = o.rect.right - o.rect.left
        editLive("scale:${o.id}") { VideoOverlays.scale(it, o.id, width / current) }
    }

    fun setOverlayTimes(start: Long, end: Long) {
        val id = _state.value.selectedOverlay ?: return
        editLive("times:$id") { VideoOverlays.setTimes(it, id, start, end) }
    }

    /** Moves the selected overlay by a fraction of the frame, as a finger drags it on the preview. */
    fun dragOverlay(dx: Double, dy: Double) {
        val id = _state.value.selectedOverlay ?: return
        editLive("move:$id") { VideoOverlays.move(it, id, dx, dy) }
    }

    fun removeOverlay() {
        val id = _state.value.selectedOverlay ?: return
        edit { VideoOverlays.remove(it, id) }
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
                val overlay = if (timeline.overlays.isEmpty()) null else TimelineOverlay(timeline, _state.value.overlayImages)
                try {
                    encoder.encodeTimeline(timeline, temp, overlay) { p -> _state.update { it.copy(progress = p) } }
                } finally {
                    overlay?.recycle()
                }
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
        const val OVERLAY_SIDE = 1080
        const val PREVIEWS_KEPT = 3
        const val HISTORY = 50
    }
}
