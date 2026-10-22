package com.bentusk.app.feature.templates

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bentusk.app.core.entitlement.EntitlementRepository
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.core.image.PhotoLoader
import com.bentusk.app.core.image.SlotPhoto
import com.bentusk.app.core.image.SlotSource
import com.bentusk.app.core.image.TemplateJob
import com.bentusk.app.feature.export.ExportSession
import com.bentusk.app.navigation.TemplateEditorRoute
import com.bentusk.engine.DecodePlan
import com.bentusk.engine.Entitlement
import com.bentusk.engine.Framing
import com.bentusk.engine.FramingMath
import com.bentusk.engine.Plans
import com.bentusk.engine.ProFeature
import com.bentusk.engine.SliceLayout
import com.bentusk.engine.SourceGeometry
import com.bentusk.engine.Template
import com.bentusk.engine.Templates
import com.bentusk.engine.Vec2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A photo in one slot: where it came from, its on-screen copy and how it sits in the slot. */
data class FilledSlot(
    val uri: Uri,
    val geometry: SourceGeometry,
    val preview: Bitmap,
    /** In slot pixels at export resolution, like the exporter expects. */
    val framing: Framing = Framing.Default,
) {
    fun toSlotPhoto() = SlotPhoto(preview, geometry, framing)
}

/** Backgrounds offered for every template; the template's own colour comes first. */
val TemplateBackgrounds = listOf(
    0xFFFFFFFF.toInt(),
    0xFFF4EFE6.toInt(),
    0xFFE9E6E1.toInt(),
    0xFFF6D9D5.toInt(),
    0xFFD5DFCF.toInt(),
    0xFF1B1C20.toInt(),
    0xFF000000.toInt(),
)

data class TemplateEditorState(
    val template: Template,
    val slots: List<FilledSlot?>,
    val texts: List<String>,
    val background: Int,
    val selected: Int? = null,
    val loading: Boolean = true,
    val failedPhotos: Int = 0,
    val format: ExportFormat = ExportFormat.Jpeg,
    val showGuides: Boolean = true,
    val entitlement: Entitlement = Entitlement.Trial(Plans.TRIAL_DAYS),
) {
    val layout: SliceLayout get() = template.layout()
    val photos: List<SlotPhoto?> get() = slots.map { it?.toSlotPhoto() }
    val filled: Int get() = slots.count { it != null }

    fun allows(feature: ProFeature) = entitlement.allows(feature)
}

sealed interface TemplateEditorEvent {
    data object OpenExport : TemplateEditorEvent
    data class Locked(val feature: ProFeature) : TemplateEditorEvent
}

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loader: PhotoLoader,
    private val session: ExportSession,
    entitlements: EntitlementRepository,
) : ViewModel() {

    private val route = savedState.toRoute<TemplateEditorRoute>()
    private val template = Templates.byId(route.templateId) ?: Templates.All.first()

    private val _state = MutableStateFlow(
        TemplateEditorState(
            template = template,
            slots = List(template.photos.size) { null },
            texts = template.texts.map { it.defaultText },
            background = template.background,
        ),
    )
    val state: StateFlow<TemplateEditorState> = _state.asStateFlow()

    private val _events = MutableStateFlow<TemplateEditorEvent?>(null)
    val events: StateFlow<TemplateEditorEvent?> = _events.asStateFlow()

    init {
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(entitlement = e) } } }
        viewModelScope.launch {
            val uris = route.photos.split('\n').filter { it.isNotBlank() }.map(Uri::parse).take(template.photos.size)
            var failed = 0
            uris.forEachIndexed { index, uri ->
                val slot = load(uri)
                if (slot == null) failed++
                _state.update { s -> s.copy(slots = s.slots.replace(index, slot)) }
            }
            _state.update { it.copy(loading = false, failedPhotos = failed) }
        }
    }

    private suspend fun load(uri: Uri): FilledSlot? = try {
        val geometry = loader.probe(uri)
        val bitmap = loader.decode(uri, DecodePlan.forPreview(geometry.upright, PREVIEW_LONG_SIDE).sampleSize)
        FilledSlot(uri, geometry, bitmap)
    } catch (e: Exception) {
        Log.w(TAG, "Cannot load $uri", e)
        null
    }

    fun consumeEvent() {
        _events.value = null
    }

    fun select(index: Int?) = _state.update { it.copy(selected = index) }

    /** Selects the photo slot under [point] (layout-canvas pixels), or clears the selection. */
    fun selectAt(point: Vec2) {
        val s = _state.value
        val hit = s.template.photos.indices.lastOrNull { s.template.slotRect(it, s.layout).contains(point.x.toInt(), point.y.toInt()) }
        _state.update { it.copy(selected = hit) }
    }

    /** Puts [uri] in the selected slot, or the first empty one when nothing is selected. */
    fun placePhoto(uri: Uri) {
        val s = _state.value
        val target = s.selected ?: s.slots.indexOfFirst { it == null }.takeIf { it >= 0 } ?: return
        viewModelScope.launch {
            val slot = load(uri)
            if (slot == null) {
                _state.update { it.copy(failedPhotos = it.failedPhotos + 1) }
                return@launch
            }
            _state.update { it.copy(slots = it.slots.replace(target, slot), selected = target) }
        }
    }

    fun removeSelected() {
        val index = _state.value.selected ?: return
        _state.update { it.copy(slots = it.slots.replace(index, null)) }
    }

    /**
     * Applies a gesture to the selected photo. [centroid] and [pan] are in layout-canvas pixels;
     * zoom and rotation pivot around the fingers, as in the single-photo editor.
     */
    fun onGesture(centroid: Vec2, pan: Vec2, zoom: Double, rotation: Double) {
        updateSelected { s, index, filled ->
            val slot = s.template.slotRect(index, s.layout)
            val c = centroid - Vec2(slot.left + slot.width / 2.0, slot.top + slot.height / 2.0)
            val f = filled.framing
            val pivoted = (f.offset - c).rotated(rotation) * zoom + c + pan
            Framing(f.rotationDegrees + rotation, f.zoom * zoom, pivoted)
        }
    }

    fun rotateQuarter() = updateSelected { _, _, f ->
        f.framing.copy(rotationDegrees = Math.round(f.framing.rotationDegrees / 90.0) * 90.0 + 90.0)
    }

    fun fit() = updateSelected { _, _, f -> Framing(f.framing.rotationDegrees, 0.0, Vec2.Zero) }

    fun resetFraming() = updateSelected { _, _, _ -> Framing.Default }

    private fun updateSelected(change: (TemplateEditorState, Int, FilledSlot) -> Framing) {
        _state.update { s ->
            val index = s.selected ?: return@update s
            val filled = s.slots.getOrNull(index) ?: return@update s
            val slotSize = s.template.slotRect(index, s.layout).size
            val clamped = FramingMath.clamp(filled.geometry.upright, slotSize, change(s, index, filled))
            s.copy(slots = s.slots.replace(index, filled.copy(framing = clamped)))
        }
    }

    fun setText(index: Int, text: String) = _state.update { it.copy(texts = it.texts.replace(index, text)) }

    fun setBackground(argb: Int) = _state.update { it.copy(background = argb) }

    fun setFormat(format: ExportFormat) {
        if (format == ExportFormat.Png && !_state.value.allows(ProFeature.PngExport)) {
            _events.value = TemplateEditorEvent.Locked(ProFeature.PngExport)
            return
        }
        _state.update { it.copy(format = format) }
    }

    fun toggleGuides() = _state.update { it.copy(showGuides = !it.showGuides) }

    fun export() {
        val s = _state.value
        if (s.template.premium && !s.allows(ProFeature.PremiumTemplates)) {
            _events.value = TemplateEditorEvent.Locked(ProFeature.PremiumTemplates)
            return
        }
        session.start(
            TemplateJob(
                template = s.template,
                photos = s.slots.map { it?.let { f -> SlotSource(f.uri, f.geometry, f.framing) } },
                texts = s.texts,
                background = s.background,
                format = s.format,
            ),
        )
        _events.value = TemplateEditorEvent.OpenExport
    }

    companion object {
        private const val TAG = "TemplateEditor"

        /** Smaller than the single-photo editor's, since up to ten photos share the screen. */
        const val PREVIEW_LONG_SIDE = 1600
    }
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
