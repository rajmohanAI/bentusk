package com.bentusk.app.feature.editor

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bentusk.app.core.entitlement.EntitlementRepository
import com.bentusk.app.core.image.Backdrop
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.core.image.PhotoLoader
import com.bentusk.app.core.image.PreparedBackdrop
import com.bentusk.app.core.image.SliceJob
import com.bentusk.app.core.image.TileFrame
import com.bentusk.app.feature.export.ExportSession
import com.bentusk.app.navigation.EditorRoute
import com.bentusk.engine.CarouselLayout
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.Entitlement
import com.bentusk.engine.Framing
import com.bentusk.engine.FramingMath
import com.bentusk.engine.GridLayout
import com.bentusk.engine.Plans
import com.bentusk.engine.PostRatio
import com.bentusk.engine.ProFeature
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.SourceGeometry
import com.bentusk.engine.Vec2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

enum class SliceMode { Carousel, Grid }

enum class BackdropChoice(val backdrop: Backdrop) {
    Blur(Backdrop.Blur),
    White(Backdrop.White),
    Black(Backdrop.Black),
    Cream(Backdrop.Solid(0xFFF4EFE6.toInt())),
    Blush(Backdrop.Solid(0xFFF6D9D5.toInt())),
    Sage(Backdrop.Solid(0xFFD5DFCF.toInt())),
}

enum class FrameChoice(val frame: TileFrame?) {
    None(null),
    ThinWhite(TileFrame(12, 0xFFFFFFFF.toInt())),
    WideWhite(TileFrame(36, 0xFFFFFFFF.toInt())),
    ThinBlack(TileFrame(12, 0xFF000000.toInt())),
}

/** A photo decoded for the screen, plus a backdrop prepared for the current layout. */
data class PreviewPhoto(
    val bitmap: Bitmap,
    val geometry: SourceGeometry,
    val backdrop: PreparedBackdrop,
)

data class EditorState(
    val mode: SliceMode,
    val slides: Int = 3,
    val rows: Int = 3,
    val ratio: PostRatio = if (mode == SliceMode.Carousel) PostRatio.CarouselDefault else PostRatio.GridDefault,
    val framing: Framing = Framing.Default,
    val backdrop: BackdropChoice = BackdropChoice.Blur,
    val frame: FrameChoice = FrameChoice.None,
    val format: ExportFormat = ExportFormat.Jpeg,
    val showGuides: Boolean = true,
    val preview: PreviewPhoto? = null,
    val error: Boolean = false,
    val entitlement: Entitlement = Entitlement.Trial(Plans.TRIAL_DAYS),
) {
    val layout: SliceLayout
        get() = when (mode) {
            SliceMode.Carousel -> CarouselLayout(slides, ratio)
            SliceMode.Grid -> GridLayout(rows, ratio)
        }

    val needsBackdrop: Boolean
        get() = preview?.let { FramingMath.needsBackground(it.geometry.upright, layout.canvas, framing) } ?: false

    /** Fine rotation shown on the straighten dial, within ±45°. */
    val straighten: Float
        get() = (framing.rotationDegrees - quarterTurnBase(framing.rotationDegrees)).toFloat()

    fun allows(feature: ProFeature) = entitlement.allows(feature)
}

private fun quarterTurnBase(degrees: Double) = (degrees / 90.0).roundToInt() * 90.0

sealed interface EditorEvent {
    data object OpenExport : EditorEvent
    data class Locked(val feature: ProFeature) : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loader: PhotoLoader,
    private val session: ExportSession,
    entitlements: EntitlementRepository,
) : ViewModel() {

    private val route = savedState.toRoute<EditorRoute>()
    private val photoUri = Uri.parse(route.photo)

    private val _state = MutableStateFlow(EditorState(mode = SliceMode.valueOf(route.mode)))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _events = MutableStateFlow<EditorEvent?>(null)
    val events: StateFlow<EditorEvent?> = _events.asStateFlow()

    init {
        viewModelScope.launch { loadPreview() }
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(entitlement = e) } } }
    }

    private suspend fun loadPreview() {
        try {
            val geometry = loader.probe(photoUri)
            val plan = DecodePlan.forPreview(geometry.upright, PREVIEW_LONG_SIDE)
            val bitmap = loader.decode(photoUri, plan.sampleSize)
            val backdrop = prepareBackdrop(bitmap, geometry, _state.value)
            _state.update { it.copy(preview = PreviewPhoto(bitmap, geometry, backdrop)) }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load $photoUri", e)
            _state.update { it.copy(error = true) }
        }
    }

    private suspend fun prepareBackdrop(bitmap: Bitmap, geometry: SourceGeometry, s: EditorState) =
        withContext(Dispatchers.Default) { PreparedBackdrop.prepare(s.backdrop.backdrop, bitmap, geometry, s.layout.canvas) }

    fun consumeEvent() {
        _events.value = null
    }

    fun setSlides(slides: Int) {
        if (slides > Plans.FREE_MAX_SLIDES && !_state.value.allows(ProFeature.LongCarousel)) {
            _events.value = EditorEvent.Locked(ProFeature.LongCarousel)
            return
        }
        changeLayout { it.copy(slides = slides) }
    }

    fun setRows(rows: Int) = changeLayout { it.copy(rows = rows) }

    fun setRatio(ratio: PostRatio) = changeLayout { it.copy(ratio = ratio) }

    /** Applies a layout change, carrying the composition over to the new canvas, then rebuilds the backdrop. */
    private fun changeLayout(change: (EditorState) -> EditorState) {
        val before = _state.value
        val after = change(before)
        if (after.layout == before.layout) return
        val from = before.layout.canvas
        val to = after.layout.canvas
        val carried = FramingMath.rescale(before.framing, from, to)
        _state.value = after.copy(framing = clampFor(after, carried))
        refreshBackdrop()
    }

    private fun refreshBackdrop() {
        val preview = _state.value.preview ?: return
        viewModelScope.launch {
            val fresh = prepareBackdrop(preview.bitmap, preview.geometry, _state.value)
            _state.update { s -> s.copy(preview = s.preview?.copy(backdrop = fresh)) }
        }
    }

    /**
     * Applies a gesture. All values are in export-canvas pixels; [centroid] is measured from the canvas centre.
     * Zoom and rotation pivot around the fingers, so the point under them stays put.
     */
    fun onGesture(centroid: Vec2, pan: Vec2, zoom: Double, rotation: Double) {
        _state.update { s ->
            val f = s.framing
            val pivoted = (f.offset - centroid).rotated(rotation) * zoom + centroid + pan
            s.copy(framing = clampFor(s, Framing(f.rotationDegrees + rotation, f.zoom * zoom, pivoted)))
        }
    }

    fun rotateQuarter() = updateFraming { it.copy(rotationDegrees = quarterTurnBase(it.rotationDegrees) + 90.0) }

    fun setStraighten(degrees: Float) =
        updateFraming { it.copy(rotationDegrees = quarterTurnBase(it.rotationDegrees) + degrees.toDouble()) }

    fun setZoom(zoom: Double) = updateFraming { it.copy(zoom = zoom) }

    fun fit() = updateFraming { Framing(it.rotationDegrees, 0.0, Vec2.Zero) }

    fun reset() = updateFraming { Framing.Default }

    private fun updateFraming(change: (Framing) -> Framing) {
        _state.update { s -> s.copy(framing = clampFor(s, change(s.framing))) }
    }

    private fun clampFor(s: EditorState, framing: Framing): Framing {
        val upright = s.preview?.geometry?.upright ?: return framing
        return FramingMath.clamp(upright, s.layout.canvas, framing)
    }

    fun setBackdrop(choice: BackdropChoice) {
        _state.update { it.copy(backdrop = choice) }
        refreshBackdrop()
    }

    fun setFrame(choice: FrameChoice) {
        if (choice != FrameChoice.None && !_state.value.allows(ProFeature.TileFrames)) {
            _events.value = EditorEvent.Locked(ProFeature.TileFrames)
            return
        }
        _state.update { it.copy(frame = choice) }
    }

    fun setFormat(format: ExportFormat) {
        if (format == ExportFormat.Png && !_state.value.allows(ProFeature.PngExport)) {
            _events.value = EditorEvent.Locked(ProFeature.PngExport)
            return
        }
        _state.update { it.copy(format = format) }
    }

    fun toggleGuides() = _state.update { it.copy(showGuides = !it.showGuides) }

    fun export() {
        val s = _state.value
        val preview = s.preview ?: return
        session.start(
            SliceJob(
                photo = photoUri,
                geometry = preview.geometry,
                layout = s.layout,
                framing = s.framing,
                backdrop = s.backdrop.backdrop,
                frame = s.frame.frame,
                format = s.format,
                filePrefix = if (s.mode == SliceMode.Carousel) "carowsle_carousel" else "carowsle_grid",
            ),
        )
        _events.value = EditorEvent.OpenExport
    }

    companion object {
        private const val TAG = "EditorViewModel"

        /** Long side of the on-screen copy: sharp on any phone, a fraction of the full photo's memory. */
        const val PREVIEW_LONG_SIDE = 2400
    }
}
