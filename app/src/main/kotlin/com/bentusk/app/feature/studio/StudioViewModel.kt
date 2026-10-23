package com.bentusk.app.feature.studio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bentusk.app.core.data.ProjectStore
import com.bentusk.app.core.entitlement.EntitlementRepository
import com.bentusk.app.core.image.DesignJob
import com.bentusk.app.core.image.DesignRenderer
import com.bentusk.app.core.image.EmptySlots
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.core.image.PhotoLoader
import com.bentusk.app.core.image.SlotPhoto
import com.bentusk.app.feature.export.ExportSession
import com.bentusk.app.navigation.StudioRoute
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.Design
import com.bentusk.engine.Entitlement
import com.bentusk.engine.Fill
import com.bentusk.engine.Framing
import com.bentusk.engine.FramingMath
import com.bentusk.engine.InstagramSpec
import com.bentusk.engine.Layer
import com.bentusk.engine.NRect
import com.bentusk.engine.PhotoLayer
import com.bentusk.engine.Plans
import com.bentusk.engine.PostRatio
import com.bentusk.engine.ProFeature
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.SourceGeometry
import com.bentusk.engine.StickerPlacement
import com.bentusk.engine.Stickers
import com.bentusk.engine.TemplateKind
import com.bentusk.engine.Templates
import com.bentusk.engine.TextAlign
import com.bentusk.engine.TextLayer
import com.bentusk.engine.Vec2
import com.bentusk.engine.toDesign
import com.bentusk.engine.withRect
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

/** A photo decoded for the screen. */
class LoadedPhoto(val geometry: SourceGeometry, val bitmap: Bitmap)

enum class StudioPanel { None, Add, Slides, Layers, Ratio, Background, Stickers }

data class StudioState(
    val design: Design,
    val loaded: Map<String, LoadedPhoto> = emptyMap(),
    val selected: Long? = null,
    val panel: StudioPanel = StudioPanel.None,
    /** The slide or tile the user is looking at; new layers land here. */
    val page: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val failedPhotos: Int = 0,
    val previewing: Boolean = false,
    val format: ExportFormat = ExportFormat.Jpeg,
    val showGuides: Boolean = true,
    val showSafeZones: Boolean = false,
    val entitlement: Entitlement = Entitlement.Trial(Plans.TRIAL_DAYS),
) {
    val layout: SliceLayout get() = design.layout()

    /** Decoded photos by layer id, framed as the layer says. */
    val photos: Map<Long, SlotPhoto>
        get() = design.photos.mapNotNull { layer ->
            val loaded = layer.uri?.let { loaded[it] } ?: return@mapNotNull null
            layer.id to SlotPhoto(loaded.bitmap, loaded.geometry, layer.framing)
        }.toMap()

    val selectedLayer: Layer? get() = selected?.let(design::layer)

    fun allows(feature: ProFeature) = entitlement.allows(feature)
}

sealed interface StudioEvent {
    data object OpenExport : StudioEvent
    data class Locked(val feature: ProFeature) : StudioEvent
    /** Ask the photo picker for one photo for the selected slot. */
    data object PickOne : StudioEvent
}

@HiltViewModel
class StudioViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val loader: PhotoLoader,
    private val session: ExportSession,
    private val projects: ProjectStore,
    entitlements: EntitlementRepository,
) : ViewModel() {

    private val route = savedState.toRoute<StudioRoute>()
    private val projectId: String = route.projectId ?: savedState.get<String>(KEY_PROJECT) ?: projects.newId().also {
        savedState[KEY_PROJECT] = it
    }

    private val _state = MutableStateFlow(StudioState(design = Design.blank(TemplateKind.Carousel, 3, PostRatio.Portrait)))
    val state: StateFlow<StudioState> = _state.asStateFlow()

    private val _events = MutableStateFlow<StudioEvent?>(null)
    val events: StateFlow<StudioEvent?> = _events.asStateFlow()

    private val undo = ArrayDeque<Design>()
    private val redo = ArrayDeque<Design>()
    private var gestureBase: Design? = null
    private var coalesceKey: String? = null
    private var coalesceAt = 0L

    init {
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(entitlement = e) } } }
        viewModelScope.launch {
            val design = route.projectId?.let { projects.load(it) }
                ?: route.templateId?.let { Templates.byId(it)?.toDesign() }
                ?: blank(route.kind)
            _state.update { it.copy(design = design, ready = true) }
            design.photos.mapNotNull { it.uri }.distinct().forEach { ensureLoaded(it) }
            val uris = route.photos.split('\n').filter { it.isNotBlank() }.map(Uri::parse)
            if (uris.isNotEmpty() && route.projectId == null) placePhotos(uris, record = false)
            autosave()
        }
    }

    private fun blank(kind: String?): Design = when (kind?.let(TemplateKind::valueOf)) {
        TemplateKind.Grid -> Design.blank(TemplateKind.Grid, 3, PostRatio.GridDefault)
        else -> Design.blank(TemplateKind.Carousel, 3, PostRatio.CarouselDefault)
    }

    /** Saves the draft shortly after each change, with a thumbnail of its first slide. */
    private suspend fun autosave() {
        _state.map { it.design }.distinctUntilChanged().drop(1).collectLatest { design ->
            delay(AUTOSAVE_DELAY_MS)
            val thumb = withContext(Dispatchers.Default) { thumbnail(design) }
            projects.save(projectId, design, thumb)
            thumb?.recycle()
        }
    }

    private fun thumbnail(design: Design): Bitmap? = runCatching {
        val layout = design.layout()
        val tile = layout.tiles.first().rect
        val k = THUMB_WIDTH / tile.width.toFloat()
        val out = Bitmap.createBitmap(THUMB_WIDTH, (tile.height * k).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.scale(k, k)
        canvas.translate(-tile.left.toFloat(), -tile.top.toFloat())
        DesignRenderer.draw(canvas, tile, design, layout, _state.value.photos, EmptySlots.Placeholder)
        out
    }.getOrNull()

    fun consumeEvent() {
        _events.value = null
    }

    // History

    /** Applies a finished change and records it for undo. Changes with the same [key] in quick succession merge. */
    private fun commit(design: Design, key: String? = null) {
        val current = _state.value.design
        if (design == current) return
        val now = System.currentTimeMillis()
        val merge = key != null && key == coalesceKey && now - coalesceAt < COALESCE_MS
        if (!merge) {
            undo.addLast(current)
            if (undo.size > HISTORY) undo.removeFirst()
        }
        redo.clear()
        coalesceKey = key
        coalesceAt = now
        setDesign(design)
    }

    private fun setDesign(design: Design) {
        _state.update { s ->
            val selected = s.selected?.takeIf { design.layer(it) != null }
            s.copy(
                design = design,
                selected = selected,
                page = s.page.coerceAtMost(design.layout().count - 1),
                canUndo = undo.isNotEmpty(),
                canRedo = redo.isNotEmpty(),
            )
        }
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(_state.value.design)
        coalesceKey = null
        setDesign(previous)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(_state.value.design)
        coalesceKey = null
        setDesign(next)
    }

    // Selection and gestures (points in layout-canvas pixels)

    fun select(id: Long?) = _state.update { it.copy(selected = id, panel = if (id != null) StudioPanel.None else it.panel) }

    /**
     * A finger went down: returns true when the drag that follows should move the layer under it.
     * Text and free photos are grabbed at once. A photo slot must be tapped first, because slots often fill
     * whole slides and grabbing them would stop the slides from swiping.
     */
    fun onPress(point: Vec2): Boolean {
        val s = _state.value
        val hit = s.design.hit(point, s.layout) ?: return false
        val grab = hit.id == s.selected || hit is TextLayer || (hit is PhotoLayer && hit.free)
        if (grab && hit.id != s.selected) select(hit.id)
        return grab
    }

    fun onTap(point: Vec2) {
        val s = _state.value
        val hit = s.design.hit(point, s.layout)
        when {
            hit == null -> _state.update { it.copy(selected = null, panel = StudioPanel.None) }
            hit is PhotoLayer && hit.uri == null -> {
                select(hit.id)
                _events.value = StudioEvent.PickOne
            }
            else -> select(hit.id)
        }
    }

    fun onGestureStart() {
        gestureBase = _state.value.design
    }

    fun onGestureEnd() {
        val base = gestureBase ?: return
        gestureBase = null
        if (base != _state.value.design) {
            undo.addLast(base)
            if (undo.size > HISTORY) undo.removeFirst()
            redo.clear()
            coalesceKey = null
            _state.update { it.copy(canUndo = true, canRedo = false) }
        }
    }

    /** Moves, zooms or rotates the selected layer. [centroid] and [pan] are in layout-canvas pixels. */
    fun onTransform(centroid: Vec2, pan: Vec2, zoom: Double, rotation: Double) {
        val s = _state.value
        val layer = s.selectedLayer ?: return
        val layout = s.layout
        val canvas = layout.canvas
        val design = when {
            layer is PhotoLayer && !layer.free -> {
                val loaded = layer.uri?.let { s.loaded[it] } ?: return
                val slot = s.design.rectPx(layer, layout)
                val c = centroid - Vec2(slot.left + slot.width / 2.0, slot.top + slot.height / 2.0)
                val f = layer.framing
                val pivoted = (f.offset - c).rotated(rotation) * zoom + c + pan
                val framing = FramingMath.clamp(loaded.geometry.upright, slot.size, Framing(f.rotationDegrees + rotation, f.zoom * zoom, pivoted))
                s.design.replace(layer.copy(framing = framing))
            }
            else -> {
                val moved = s.design.move(layer.id, pan.x / canvas.width, pan.y / canvas.height)
                if (zoom == 1.0) moved else moved.scale(layer.id, zoom, Vec2(centroid.x / canvas.width, centroid.y / canvas.height), canvas.width / canvas.height.toDouble())
            }
        }
        _state.update { it.copy(design = design) }
    }

    fun setPage(page: Int) = _state.update { it.copy(page = page) }

    fun openPanel(panel: StudioPanel) = _state.update {
        it.copy(panel = if (it.panel == panel) StudioPanel.None else panel, selected = null)
    }

    fun setPreviewing(on: Boolean) = _state.update { it.copy(previewing = on, selected = null) }

    fun toggleGuides() = _state.update { it.copy(showGuides = !it.showGuides) }

    fun toggleSafeZones() = _state.update { it.copy(showSafeZones = !it.showSafeZones) }

    // Photos

    private suspend fun ensureLoaded(uri: String): LoadedPhoto? {
        _state.value.loaded[uri]?.let { return it }
        return try {
            val parsed = Uri.parse(uri)
            val geometry = loader.probe(parsed)
            val bitmap = loader.decode(parsed, DecodePlan.forPreview(geometry.upright, PREVIEW_LONG_SIDE).sampleSize)
            LoadedPhoto(geometry, bitmap).also { photo -> _state.update { it.copy(loaded = it.loaded + (uri to photo)) } }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load $uri", e)
            _state.update { it.copy(failedPhotos = it.failedPhotos + 1) }
            null
        }
    }

    /** Keeps access to picked photos after a restart, so drafts reopen with their pictures. */
    private fun persist(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch { placePhotos(uris, record = true) }
    }

    /** Fills empty slots first (the selected one before others), then adds the rest as free photos on this slide. */
    private suspend fun placePhotos(uris: List<Uri>, record: Boolean) {
        _state.update { it.copy(busy = true, panel = StudioPanel.None) }
        var design = _state.value.design
        var offset = 0
        for (uri in uris) {
            persist(uri)
            val key = uri.toString()
            val photo = ensureLoaded(key) ?: continue
            val selected = (design.layer(_state.value.selected ?: -1) as? PhotoLayer)?.takeIf { it.uri == null && !it.free }
            val empty = selected ?: design.photos.firstOrNull { it.uri == null && !it.free }
            design = if (empty != null) {
                design.replace(empty.copy(uri = key, framing = Framing.Default))
            } else {
                // Each extra photo lands a little lower and to the right, so none hides another completely.
                val rect = Design.freePhotoRect(design, _state.value.page, photo.geometry.upright.aspect)
                val id = design.nextId()
                val step = 0.03 * offset++
                design.add(PhotoLayer(id, rect, uri = key, free = true)).move(id, step / design.count, step)
            }
        }
        if (record) commit(design) else setDesign(design)
        _state.update { it.copy(busy = false) }
    }

    /** Puts [uri] in the selected photo layer, or the first empty slot. */
    fun replacePhoto(uri: Uri) {
        viewModelScope.launch {
            persist(uri)
            val key = uri.toString()
            val photo = ensureLoaded(key) ?: return@launch
            val s = _state.value
            val target = (s.selectedLayer as? PhotoLayer) ?: s.design.photos.firstOrNull { it.uri == null }
            if (target == null) {
                placePhotos(listOf(uri), record = true)
                return@launch
            }
            val updated = if (target.free) {
                target.copy(uri = key, framing = Framing.Default, rect = reshape(target.rect, photo.geometry.upright.aspect, s.design))
            } else {
                target.copy(uri = key, framing = Framing.Default)
            }
            commit(s.design.replace(updated))
            select(target.id)
        }
    }

    /** A free photo's frame with the same width and centre but the shape of a photo with [aspect]. */
    private fun reshape(r: NRect, aspect: Double, design: Design): NRect {
        val canvas = design.layout().canvas
        val w = r.right - r.left
        var h = w * canvas.width / (aspect * canvas.height)
        var width = w
        if (h > 1.0) {
            width *= 1.0 / h
            h = 1.0
        }
        val cx = (r.left + r.right) / 2
        val cy = (r.top + r.bottom) / 2
        val left = (cx - width / 2).coerceIn(0.0, 1.0 - width)
        val top = (cy - h / 2).coerceIn(0.0, 1.0 - h)
        return NRect(left, top, left + width, top + h)
    }

    fun adjustSelected(change: (Framing) -> Framing) {
        val s = _state.value
        val layer = s.selectedLayer as? PhotoLayer ?: return
        val loaded = layer.uri?.let { s.loaded[it] } ?: return
        val slot = s.design.rectPx(layer, s.layout)
        commit(s.design.replace(layer.copy(framing = FramingMath.clamp(loaded.geometry.upright, slot.size, change(layer.framing)))))
    }

    fun rotateSelected() = adjustSelected { it.copy(rotationDegrees = Math.round(it.rotationDegrees / 90.0) * 90.0 + 90.0) }

    fun fitSelected() = adjustSelected { Framing(it.rotationDegrees, 0.0, Vec2.Zero) }

    fun fillSelected() = adjustSelected { Framing.Default }

    fun clearSelectedPhoto() {
        val s = _state.value
        val layer = s.selectedLayer as? PhotoLayer ?: return
        commit(if (layer.free) s.design.remove(layer.id) else s.design.replace(layer.copy(uri = null, framing = Framing.Default)))
    }

    // Text

    fun addText() {
        val s = _state.value
        val tile = s.design.tileRect(s.page)
        val w = tile.right - tile.left
        val h = tile.bottom - tile.top
        val rect = NRect(tile.left + w * 0.1, tile.top + h * 0.42, tile.right - w * 0.1, tile.top + h * 0.58)
        val dark = luminance(s.design.fill.top) > 0.5
        val layer = TextLayer(
            id = s.design.nextId(),
            rect = rect,
            text = DEFAULT_TEXT,
            size = 0.07,
            argb = if (dark) 0xFF1B1C20.toInt() else 0xFFFFFFFF.toInt(),
        )
        commit(s.design.add(layer))
        _state.update { it.copy(selected = layer.id, panel = StudioPanel.None) }
    }

    /** Adds a ready-made swipe nudge or call to action to the slide in view, selected so it can be edited at once. */
    fun addSticker(placement: StickerPlacement, text: String) {
        val s = _state.value
        val ink = if (luminance(s.design.fill.top) > 0.5) 0xFF1B1C20.toInt() else 0xFFFFFFFF.toInt()
        val layer = Stickers.layer(s.design, s.page, placement, text, ink)
        commit(s.design.add(layer))
        _state.update { it.copy(selected = layer.id, panel = StudioPanel.None) }
    }

    fun updateText(key: String = "style", change: (TextLayer) -> TextLayer) {
        val s = _state.value
        val layer = s.selectedLayer as? TextLayer ?: return
        commit(s.design.replace(change(layer)), key = "$key-${layer.id}")
    }

    fun setText(text: String) = updateText("type") { it.copy(text = text) }

    fun cycleAlign() = updateText {
        it.copy(align = TextAlign.entries[(it.align.ordinal + 1) % TextAlign.entries.size])
    }

    // Layers

    fun deleteLayer(id: Long) = commit(_state.value.design.remove(id))

    fun restack(id: Long, up: Boolean) = commit(_state.value.design.restack(id, up))

    fun deleteSelected() {
        val id = _state.value.selected ?: return
        deleteLayer(id)
    }

    // Post shape

    fun setCount(count: Int) {
        val s = _state.value
        if (s.design.kind == TemplateKind.Carousel && count > Plans.FREE_MAX_SLIDES && !s.allows(ProFeature.LongCarousel)) {
            _events.value = StudioEvent.Locked(ProFeature.LongCarousel)
            return
        }
        commit(reframe(s.design.withCount(count)))
    }

    fun setRatio(ratio: PostRatio) = commit(reframe(_state.value.design.copy(ratio = ratio)))

    /** After the canvas changes size, keeps free photos their own shape and framed photos covering their slots. */
    private fun reframe(design: Design): Design {
        val s = _state.value
        val layout = design.layout()
        return design.copy(
            layers = design.layers.map { layer ->
                val photo = (layer as? PhotoLayer)?.uri?.let { s.loaded[it] } ?: return@map layer
                layer as PhotoLayer
                if (layer.free) {
                    layer.withRect(reshape(layer.rect, photo.geometry.upright.aspect, design))
                } else {
                    layer.copy(framing = FramingMath.clamp(photo.geometry.upright, design.rectPx(layer, layout).size, layer.framing))
                }
            },
        )
    }

    fun setFill(fill: Fill) = commit(_state.value.design.copy(fill = fill), key = "fill")

    fun setFormat(format: ExportFormat) {
        if (format == ExportFormat.Png && !_state.value.allows(ProFeature.PngExport)) {
            _events.value = StudioEvent.Locked(ProFeature.PngExport)
            return
        }
        _state.update { it.copy(format = format) }
    }

    fun export() {
        val s = _state.value
        val premium = s.design.templateId?.let(Templates::byId)?.premium == true
        if (premium && !s.allows(ProFeature.PremiumTemplates)) {
            _events.value = StudioEvent.Locked(ProFeature.PremiumTemplates)
            return
        }
        if (s.design.kind == TemplateKind.Carousel && s.design.count > Plans.FREE_MAX_SLIDES && !s.allows(ProFeature.LongCarousel)) {
            _events.value = StudioEvent.Locked(ProFeature.LongCarousel)
            return
        }
        _state.update { it.copy(selected = null) }
        session.start(DesignJob(s.design, s.format))
        _events.value = StudioEvent.OpenExport
    }

    companion object {
        private const val TAG = "Studio"
        private const val KEY_PROJECT = "project_id"
        private const val HISTORY = 60
        private const val COALESCE_MS = 1500L
        private const val AUTOSAVE_DELAY_MS = 800L
        private const val THUMB_WIDTH = 360
        const val DEFAULT_TEXT = "Your text"

        /** Smaller than the single-photo editor's, since up to ten photos share the screen. */
        const val PREVIEW_LONG_SIDE = 1600

        val SlideRange = InstagramSpec.MIN_CAROUSEL_SLIDES..InstagramSpec.MAX_CAROUSEL_SLIDES
        val RowRange = 1..InstagramSpec.MAX_GRID_ROWS

        fun luminance(argb: Int): Double {
            val r = (argb shr 16 and 0xFF) / 255.0
            val g = (argb shr 8 and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
    }
}
