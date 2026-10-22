package com.bentusk.app.feature.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentusk.app.core.ai.PhotoLabeler
import com.bentusk.app.core.data.SettingsStore
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.core.image.ExportProgress
import com.bentusk.app.core.image.ExportedTile
import com.bentusk.app.core.image.SliceExporter
import com.bentusk.app.core.image.DesignExporter
import com.bentusk.app.core.storage.MediaSaver
import com.bentusk.engine.Caption
import com.bentusk.engine.CaptionIdeas
import com.bentusk.engine.HashtagSets
import com.bentusk.engine.TemplateKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
    val caption: String = "",
    /** True once the user has agreed to hand posts over to the Instagram app. */
    val instagramConsent: Boolean = false,
    val keepLineBreaks: Boolean = true,
    val ai: AiCaptionState = AiCaptionState(),
) {
    val uris: List<Uri> get() = coverUri?.let { listOf(it) } ?: tiles.map { it.uri }

    /** Posts that go on the feed get a caption; covers are only used inside highlights. */
    val hasCaption: Boolean get() = kind != ExportKind.Cover

    val hashtags: List<String> get() = Caption.hashtags(caption)

    /** The caption as it goes on the clipboard for Instagram. */
    val captionToPost: String get() = if (keepLineBreaks) Caption.keepLineBreaks(caption) else caption.trim()
}

/** The "Write with AI" sheet: the user's choices and the captions written for them. */
data class AiCaptionState(
    val open: Boolean = false,
    val busy: Boolean = false,
    val tone: CaptionIdeas.Tone = CaptionIdeas.Tone.Fun,
    val language: CaptionIdeas.Language = CaptionIdeas.Language.English,
    val emoji: Boolean = true,
    val ideas: List<String> = emptyList(),
    val variant: Int = 0,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val session: ExportSession,
    private val exporter: SliceExporter,
    private val designExporter: DesignExporter,
    private val saver: MediaSaver,
    private val settings: SettingsStore,
    private val labeler: PhotoLabeler,
) : ViewModel() {

    /** What the exported images show, worked out once on the phone. */
    private var labels: List<String>? = null

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        _state.update { it.copy(caption = session.caption) }
        viewModelScope.launch {
            settings.settings.collect { s ->
                _state.update { it.copy(instagramConsent = s.instagramConsent, keepLineBreaks = s.keepLineBreaks) }
            }
        }
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

    fun setCaption(text: String) {
        session.caption = text
        _state.update { it.copy(caption = text) }
    }

    fun openAi() {
        _state.update { it.copy(ai = it.ai.copy(open = true)) }
        if (_state.value.ai.ideas.isEmpty()) writeIdeas()
    }

    fun closeAi() = _state.update { it.copy(ai = it.ai.copy(open = false)) }

    fun setTone(tone: CaptionIdeas.Tone) = changeAi { it.copy(tone = tone) }

    fun setLanguage(language: CaptionIdeas.Language) = changeAi { it.copy(language = language) }

    fun setEmoji(on: Boolean) = changeAi { it.copy(emoji = on) }

    fun writeAgain() = changeAi { it.copy(variant = it.variant + 1) }

    private fun changeAi(change: (AiCaptionState) -> AiCaptionState) {
        _state.update { it.copy(ai = change(it.ai)) }
        writeIdeas()
    }

    private var aiJob: Job? = null

    private fun writeIdeas() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _state.update { it.copy(ai = it.ai.copy(busy = true)) }
            val found = labels ?: labeler.labels(_state.value.uris).also { labels = it }
            val s = _state.value
            val ideas = CaptionIdeas.write(
                CaptionIdeas.Request(found, s.ai.tone, s.ai.language, s.ai.emoji, slides = s.tiles.size, variant = s.ai.variant),
            )
            _state.update { it.copy(ai = it.ai.copy(busy = false, ideas = ideas)) }
        }
    }

    /** Puts a written caption in the box, with a few fitting hashtags if it has none yet. It stays editable. */
    fun useIdea(text: String) {
        val theme = CaptionIdeas.theme(labels.orEmpty())
        val topic = HashtagSets.Topics.firstOrNull { it.id == CaptionIdeas.hashtagTopic(theme) }
        val existing = Caption.hashtags(_state.value.caption)
        var caption = text
        if (existing.isEmpty()) topic?.tags?.take(SUGGESTED_TAGS)?.forEach { caption = Caption.toggleHashtag(caption, it) }
        else existing.forEach { caption = Caption.toggleHashtag(caption, it) }
        setCaption(caption)
        closeAi()
    }

    /** The post is finished; the next one starts with an empty caption. */
    fun clearCaption() {
        session.caption = ""
    }

    fun setKeepLineBreaks(on: Boolean) {
        viewModelScope.launch { settings.setKeepLineBreaks(on) }
    }

    fun toggleHashtag(tag: String) = setCaption(Caption.toggleHashtag(_state.value.caption, tag))

    fun grantInstagramConsent() {
        viewModelScope.launch { settings.setInstagramConsent(true) }
    }

    private suspend fun collect(progress: Flow<ExportProgress>) {
        progress.collect { p ->
            when (p) {
                is ExportProgress.Working -> _state.update { it.copy(done = p.done, total = p.total) }
                is ExportProgress.Finished -> _state.update {
                    it.copy(tiles = p.tiles, millis = p.millis, finished = true)
                }
            }
        }
    }

    private fun start() {
        if (job?.isActive == true) return
        val slice = session.slice
        val cover = session.cover
        val design = session.design
        job = viewModelScope.launch {
            try {
                when {
                    slice != null -> {
                        val kind = if (slice.filePrefix.contains("grid")) ExportKind.Grid else ExportKind.Carousel
                        _state.update { it.copy(kind = kind, format = slice.format, total = slice.layout.count) }
                        collect(exporter.export(slice))
                    }
                    design != null -> {
                        val kind = if (design.design.kind == TemplateKind.Grid) ExportKind.Grid else ExportKind.Carousel
                        _state.update { it.copy(kind = kind, format = design.format, total = design.design.layout().count) }
                        collect(designExporter.export(design))
                    }
                    cover != null -> {
                        _state.update { it.copy(kind = ExportKind.Cover, format = cover.format, total = 1) }
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val uri = saver.save(cover.bitmap, "carowsle_cover_$stamp.${cover.format.extension}", cover.format)
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
        private const val SUGGESTED_TAGS = 3
    }
}
