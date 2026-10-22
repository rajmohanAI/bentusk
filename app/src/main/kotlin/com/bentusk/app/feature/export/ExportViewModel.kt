package com.bentusk.app.feature.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentusk.app.core.data.SettingsStore
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.core.image.ExportProgress
import com.bentusk.app.core.image.ExportedTile
import com.bentusk.app.core.image.SliceExporter
import com.bentusk.app.core.storage.MediaSaver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class ExportKind { Carousel, Grid, Cover }

data class ExportUiState(
    val kind: ExportKind = ExportKind.Carousel,
    val done: Int = 0,
    val total: Int = 0,
    val tiles: List<ExportedTile> = emptyList(),
    val coverUri: Uri? = null,
    val format: ExportFormat = ExportFormat.Jpeg,
    val millis: Long = 0,
    val finished: Boolean = false,
    val failed: Boolean = false,
    val needsPermission: Boolean = false,
    /** True on the export where the app should ask for a Play review. */
    val askForReview: Boolean = false,
) {
    val uris: List<Uri> get() = coverUri?.let { listOf(it) } ?: tiles.map { it.uri }
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val session: ExportSession,
    private val exporter: SliceExporter,
    private val saver: MediaSaver,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        if (saver.needsLegacyPermission) _state.update { it.copy(needsPermission = true) } else start()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _state.update { it.copy(needsPermission = false) }
            start()
        } else {
            _state.update { it.copy(failed = true) }
        }
    }

    fun retry() {
        _state.update { it.copy(failed = false, done = 0) }
        start()
    }

    private fun start() {
        if (job?.isActive == true) return
        val slice = session.slice
        val cover = session.cover
        job = viewModelScope.launch {
            try {
                when {
                    slice != null -> {
                        val kind = if (slice.filePrefix.contains("grid")) ExportKind.Grid else ExportKind.Carousel
                        _state.update { it.copy(kind = kind, format = slice.format, total = slice.layout.count) }
                        exporter.export(slice).collect { p ->
                            when (p) {
                                is ExportProgress.Working -> _state.update { it.copy(done = p.done, total = p.total) }
                                is ExportProgress.Finished -> _state.update {
                                    it.copy(tiles = p.tiles, millis = p.millis, finished = true)
                                }
                            }
                        }
                    }
                    cover != null -> {
                        _state.update { it.copy(kind = ExportKind.Cover, format = cover.format, total = 1) }
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val uri = saver.save(cover.bitmap, "bentusk_cover_$stamp.${cover.format.extension}", cover.format)
                        _state.update { it.copy(coverUri = uri, done = 1, finished = true) }
                    }
                    else -> _state.update { it.copy(failed = true) }
                }
                if (_state.value.finished) {
                    val count = settings.recordExport()
                    _state.update { it.copy(askForReview = count == REVIEW_AFTER_EXPORTS) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(failed = true) }
            }
        }
    }

    companion object {
        const val REVIEW_AFTER_EXPORTS = 3
    }
}
