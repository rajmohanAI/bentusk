package com.bentusk.app.feature.editor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.core.image.Backdrop
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.ui.components.ChoiceRow
import com.bentusk.app.ui.components.SectionLabel
import com.bentusk.app.ui.components.Swatch
import com.bentusk.app.ui.theme.NumericStyle
import com.bentusk.engine.FramingMath
import com.bentusk.engine.InstagramSpec
import com.bentusk.engine.Plans
import com.bentusk.engine.PostRatio
import com.bentusk.engine.ProFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lockedMessage = stringResource(R.string.locked_feature)
    val goPro = stringResource(R.string.go_pro)

    LaunchedEffect(event) {
        when (event) {
            EditorEvent.OpenExport -> {
                viewModel.consumeEvent()
                onExport()
            }
            is EditorEvent.Locked -> {
                viewModel.consumeEvent()
                val result = snackbar.showSnackbar(lockedMessage, actionLabel = goPro, withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) onUpgrade()
            }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (state.mode == SliceMode.Carousel) R.string.tool_carousel else R.string.tool_grid),
                    )
                },
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
                    ControlPanel(state, viewModel)
                    Button(
                        onClick = viewModel::export,
                        enabled = state.preview != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp),
                    ) {
                        Text(
                            stringResource(
                                if (state.mode == SliceMode.Carousel) R.string.export_slides else R.string.export_tiles,
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
            val preview = state.preview
            when {
                state.error -> Text(
                    stringResource(R.string.photo_unreadable),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                preview == null -> CircularProgressIndicator()
                state.mode == SliceMode.Carousel -> CarouselPreview(state, preview, viewModel)
                else -> SliceCanvas(
                    preview = preview,
                    layout = state.layout,
                    framing = state.framing,
                    frame = state.frame.frame,
                    showGuides = state.showGuides,
                    onGesture = viewModel::onGesture,
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

/** The whole strip for composing, and a swipeable page per slide to check each one as followers will see it. */
@Composable
private fun CarouselPreview(state: EditorState, preview: PreviewPhoto, viewModel: EditorViewModel) {
    val layout = state.layout
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        SliceCanvas(
            preview = preview,
            layout = layout,
            framing = state.framing,
            frame = state.frame.frame,
            showGuides = state.showGuides,
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
                val tile = layout.tiles[page.coerceAtMost(layout.count - 1)]
                SliceCanvas(
                    preview = preview,
                    layout = layout,
                    framing = state.framing,
                    frame = state.frame.frame,
                    showGuides = false,
                    region = tile.rect,
                    gestureMode = GestureMode.MultiTouch,
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
    Layout(R.string.tab_layout),
    Adjust(R.string.tab_adjust),
    Fill(R.string.tab_fill),
    Output(R.string.tab_output),
}

@Composable
private fun ControlPanel(state: EditorState, viewModel: EditorViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            PanelTab.entries.forEachIndexed { index, t ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(t.title)) })
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (PanelTab.entries[tab]) {
                PanelTab.Layout -> LayoutControls(state, viewModel)
                PanelTab.Adjust -> AdjustControls(state, viewModel)
                PanelTab.Fill -> FillControls(state, viewModel)
                PanelTab.Output -> OutputControls(state, viewModel)
            }
        }
    }
}

@Composable
private fun LayoutControls(state: EditorState, viewModel: EditorViewModel) {
    if (state.mode == SliceMode.Carousel) {
        SectionLabel(stringResource(R.string.slides_count, state.slides))
        val pro = state.allows(ProFeature.LongCarousel)
        ChoiceRow(
            options = (InstagramSpec.MIN_CAROUSEL_SLIDES..InstagramSpec.MAX_CAROUSEL_SLIDES).toList(),
            selected = state.slides,
            label = { it.toString() },
            onSelect = viewModel::setSlides,
            locked = { !pro && it > Plans.FREE_MAX_SLIDES },
        )
    } else {
        SectionLabel(stringResource(R.string.grid_rows))
        ChoiceRow(
            options = (1..InstagramSpec.MAX_GRID_ROWS).toList(),
            selected = state.rows,
            label = { "3 × $it" },
            onSelect = viewModel::setRows,
        )
    }
    ChoiceRow(
        options = PostRatio.entries.toList(),
        selected = state.ratio,
        label = { "${it.label}  ·  ${it.size.width}×${it.size.height}" },
        onSelect = viewModel::setRatio,
    )
}

@Composable
private fun AdjustControls(state: EditorState, viewModel: EditorViewModel) {
    SectionLabel(stringResource(R.string.straighten_value, state.straighten))
    Slider(
        value = state.straighten,
        onValueChange = viewModel::setStraighten,
        valueRange = -45f..45f,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AdjustButton(Icons.Rounded.Rotate90DegreesCw, R.string.rotate, viewModel::rotateQuarter)
        AdjustButton(Icons.Rounded.FitScreen, R.string.fit_whole_photo, viewModel::fit)
        AdjustButton(Icons.Rounded.CenterFocusStrong, R.string.fill_frame) { viewModel.setZoom(1.0) }
        AdjustButton(Icons.Rounded.RestartAlt, R.string.reset, viewModel::reset)
    }
}

@Composable
private fun AdjustButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun FillControls(state: EditorState, viewModel: EditorViewModel) {
    SectionLabel(
        stringResource(if (state.needsBackdrop) R.string.background else R.string.background_hidden),
    )
    Row(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (choice in BackdropChoice.entries) {
            val brush = when (val b = choice.backdrop) {
                Backdrop.Blur -> Brush.linearGradient(listOf(Color(0xFF6B7A99), Color(0xFFB8A48A), Color(0xFF3E4A5E)))
                is Backdrop.Solid -> Brush.linearGradient(listOf(Color(b.argb), Color(b.argb)))
            }
            Swatch(brush = brush, selected = state.backdrop == choice, onClick = { viewModel.setBackdrop(choice) })
        }
    }
    SectionLabel(stringResource(R.string.borders))
    val pro = state.allows(ProFeature.TileFrames)
    ChoiceRow(
        options = FrameChoice.entries.toList(),
        selected = state.frame,
        label = { stringResource(it.label()) },
        onSelect = viewModel::setFrame,
        locked = { !pro && it != FrameChoice.None },
    )
}

private fun FrameChoice.label() = when (this) {
    FrameChoice.None -> R.string.frame_none
    FrameChoice.ThinWhite -> R.string.frame_thin_white
    FrameChoice.WideWhite -> R.string.frame_wide_white
    FrameChoice.ThinBlack -> R.string.frame_thin_black
}

@Composable
private fun OutputControls(state: EditorState, viewModel: EditorViewModel) {
    SectionLabel(stringResource(R.string.format))
    val pro = state.allows(ProFeature.PngExport)
    ChoiceRow(
        options = ExportFormat.entries.toList(),
        selected = state.format,
        label = { stringResource(if (it == ExportFormat.Jpeg) R.string.format_jpeg else R.string.format_png) },
        onSelect = viewModel::setFormat,
        locked = { !pro && it == ExportFormat.Png },
    )
    val layout = state.layout
    val preview = state.preview
    val sharpness = preview?.let {
        val scale = FramingMath.scale(it.geometry.upright, layout.canvas, state.framing)
        // Above 1.0 the photo is being enlarged; Instagram-ready output stays crisp up to about 1.25x.
        if (scale > 1.25) R.string.quality_upscaled else R.string.quality_sharp
    }
    Text(
        stringResource(R.string.output_summary, layout.count, layout.tileSize.width, layout.tileSize.height),
        style = MaterialTheme.typography.bodyMedium.merge(NumericStyle),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (sharpness != null) {
        Text(
            stringResource(sharpness),
            style = MaterialTheme.typography.bodySmall,
            color = if (sharpness == R.string.quality_sharp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 480.dp)
                .background(Color.Transparent),
        )
    }
}
