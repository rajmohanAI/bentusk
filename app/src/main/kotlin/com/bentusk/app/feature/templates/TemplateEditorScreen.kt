package com.bentusk.app.feature.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.feature.editor.GestureMode
import com.bentusk.app.ui.components.ChoiceRow
import com.bentusk.app.ui.components.SectionLabel
import com.bentusk.app.ui.components.Swatch
import com.bentusk.app.ui.theme.NumericStyle
import com.bentusk.engine.ProFeature
import com.bentusk.engine.TemplateKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: TemplateEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lockedMessage = stringResource(R.string.locked_feature)
    val goPro = stringResource(R.string.go_pro)
    val failedMessage = pluralStringResource(R.plurals.import_failed, state.failedPhotos, state.failedPhotos)
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::placePhoto)
    }

    LaunchedEffect(event) {
        when (event) {
            TemplateEditorEvent.OpenExport -> {
                viewModel.consumeEvent()
                onExport()
            }
            is TemplateEditorEvent.Locked -> {
                viewModel.consumeEvent()
                val result = snackbar.showSnackbar(lockedMessage, actionLabel = goPro, withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) onUpgrade()
            }
            null -> Unit
        }
    }
    LaunchedEffect(state.failedPhotos) {
        if (state.failedPhotos > 0) snackbar.showSnackbar(failedMessage, withDismissAction = true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.template.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleGuides) {
                        Icon(
                            if (state.showGuides) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
                            contentDescription = stringResource(R.string.toggle_guides),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.navigationBarsPadding()) {
                    ControlPanel(state, viewModel, onPick = {
                        pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    Button(
                        onClick = viewModel::export,
                        enabled = !state.loading && state.filled > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp),
                    ) {
                        Text(
                            stringResource(
                                if (state.template.kind == TemplateKind.Carousel) R.string.export_slides else R.string.export_tiles,
                                state.layout.count,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading && state.filled == 0 -> CircularProgressIndicator()
                state.template.kind == TemplateKind.Carousel -> CarouselPreview(state, viewModel)
                else -> TemplateCanvas(
                    template = state.template,
                    layout = state.layout,
                    background = state.background,
                    photos = state.photos,
                    texts = state.texts,
                    selected = state.selected,
                    showGuides = state.showGuides,
                    onTap = viewModel::selectAt,
                    onGesture = viewModel::onGesture,
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

/** The whole strip to see the flow, and a page per slide to work on each one at a comfortable size. */
@Composable
private fun CarouselPreview(state: TemplateEditorState, viewModel: TemplateEditorViewModel) {
    val layout = state.layout
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TemplateCanvas(
            template = state.template,
            layout = layout,
            background = state.background,
            photos = state.photos,
            texts = state.texts,
            selected = state.selected,
            showGuides = state.showGuides,
            onTap = viewModel::selectAt,
            onGesture = viewModel::onGesture,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        val pager = rememberPagerState { layout.count }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageWidth = minOf(maxWidth * 0.82f, maxHeight * layout.tileSize.aspect.toFloat() * 0.92f)
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = (maxWidth - pageWidth) / 2),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                TemplateCanvas(
                    template = state.template,
                    layout = layout,
                    background = state.background,
                    photos = state.photos,
                    texts = state.texts,
                    region = layout.tiles[page.coerceAtMost(layout.count - 1)].rect,
                    selected = state.selected,
                    gestureMode = GestureMode.MultiTouch,
                    onTap = viewModel::selectAt,
                    onGesture = viewModel::onGesture,
                    modifier = Modifier
                        .width(pageWidth)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        }
        Text(
            "${pager.currentPage + 1} / ${layout.count}",
            style = MaterialTheme.typography.labelMedium.merge(NumericStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

private enum class PanelTab(val title: Int) {
    PhotoTab(R.string.tab_photo),
    TextTab(R.string.tab_text),
    StyleTab(R.string.tab_style),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlPanel(state: TemplateEditorState, viewModel: TemplateEditorViewModel, onPick: () -> Unit) {
    val tabs = PanelTab.entries.filter { it != PanelTab.TextTab || state.template.texts.isNotEmpty() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = tabs[tab.coerceAtMost(tabs.size - 1)]
    Column {
        PrimaryTabRow(selectedTabIndex = tabs.indexOf(current), containerColor = Color.Transparent) {
            tabs.forEachIndexed { index, t ->
                Tab(selected = current == t, onClick = { tab = index }, text = { Text(stringResource(t.title)) })
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (current) {
                PanelTab.PhotoTab -> PhotoControls(state, viewModel, onPick)
                PanelTab.TextTab -> TextControls(state, viewModel)
                PanelTab.StyleTab -> StyleControls(state, viewModel)
            }
        }
    }
}

@Composable
private fun PhotoControls(state: TemplateEditorState, viewModel: TemplateEditorViewModel, onPick: () -> Unit) {
    val selected = state.selected
    val filled = selected?.let { state.slots.getOrNull(it) }
    SectionLabel(
        when {
            selected == null -> stringResource(R.string.template_tap_hint)
            filled == null -> stringResource(R.string.template_empty_slot, selected + 1, state.slots.size)
            else -> stringResource(R.string.template_slot, selected + 1, state.slots.size)
        },
    )
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val hasEmpty = state.slots.any { it == null }
        if (selected != null || hasEmpty) {
            PanelButton(
                Icons.Rounded.AddPhotoAlternate,
                if (filled != null) R.string.replace_photo else R.string.add_photo,
                onPick,
            )
        }
        if (filled != null) {
            PanelButton(Icons.Rounded.Rotate90DegreesCw, R.string.rotate, viewModel::rotateQuarter)
            PanelButton(Icons.Rounded.FitScreen, R.string.fit_whole_photo, viewModel::fit)
            PanelButton(Icons.Rounded.CenterFocusStrong, R.string.fill_frame, viewModel::resetFraming)
            PanelButton(Icons.Rounded.Delete, R.string.remove, viewModel::removeSelected)
        }
    }
    if (selected != null && filled != null) {
        Text(
            stringResource(R.string.template_gesture_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PanelButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun TextControls(state: TemplateEditorState, viewModel: TemplateEditorViewModel) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.texts.forEachIndexed { index, text ->
            OutlinedTextField(
                value = text,
                onValueChange = { viewModel.setText(index, it) },
                label = { Text(stringResource(R.string.template_text_label, index + 1)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StyleControls(state: TemplateEditorState, viewModel: TemplateEditorViewModel) {
    SectionLabel(stringResource(R.string.background))
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val colors = (listOf(state.template.background) + TemplateBackgrounds).distinct()
        for (argb in colors) {
            Swatch(
                brush = Brush.linearGradient(listOf(Color(argb), Color(argb))),
                selected = state.background == argb,
                onClick = { viewModel.setBackground(argb) },
            )
        }
    }
    val pro = state.allows(ProFeature.PngExport)
    ChoiceRow(
        options = ExportFormat.entries.toList(),
        selected = state.format,
        label = { stringResource(if (it == ExportFormat.Jpeg) R.string.format_jpeg else R.string.format_png) },
        onSelect = viewModel::setFormat,
        locked = { !pro && it == ExportFormat.Png },
    )
}
