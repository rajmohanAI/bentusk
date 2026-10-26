package com.bentusk.app.feature.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.core.image.EmptySlots
import com.bentusk.app.ui.components.SafeZoneLegend
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.app.ui.theme.NumericStyle
import com.bentusk.engine.InstagramSpec
import com.bentusk.engine.Palettes
import com.bentusk.engine.PhotoLayer
import com.bentusk.engine.TemplateKind
import com.bentusk.engine.TextLayer

@Composable
fun StudioScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: StudioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val lockedMessage = stringResource(R.string.locked_feature)
    val goPro = stringResource(R.string.go_pro)
    val failedMessage = pluralStringResource(R.plurals.import_failed, state.failedPhotos, state.failedPhotos)
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(InstagramSpec.MAX_CAROUSEL_SLIDES)) {
        if (it.isNotEmpty()) viewModel.addPhotos(it)
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::replacePhoto)
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addVideo)
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val videoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)

    LaunchedEffect(event) {
        when (event) {
            StudioEvent.OpenExport -> {
                viewModel.consumeEvent()
                onExport()
            }
            StudioEvent.PickOne -> {
                viewModel.consumeEvent()
                pickOne.launch(imagesOnly)
            }
            is StudioEvent.Locked -> {
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
    LaunchedEffect(state.selected) {
        if (state.selected != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    BackHandler(enabled = state.selected != null || state.panel != StudioPanel.None) {
        viewModel.select(null)
        viewModel.openPanel(StudioPanel.None)
    }

    state.issues?.let { issues ->
        QualityCheckDialog(
            design = state.design,
            issues = issues,
            onShow = viewModel::showIssue,
            onExportAnyway = viewModel::exportAnyway,
            onDismiss = viewModel::dismissIssues,
        )
    }

    val photos = state.photos
    val layout = state.layout

    Box(
        Modifier
            .fillMaxSize()
            .background(StudioColors.Backdrop),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                showGuides = state.showGuides,
                onClose = onBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onGuides = viewModel::toggleGuides,
                onPreview = { viewModel.setPreviewing(true) },
                onExport = viewModel::export,
            )
            val gestures = CanvasGestures(
                onPress = viewModel::onPress,
                onTap = viewModel::onTap,
                onStart = viewModel::onGestureStart,
                onTransform = viewModel::onTransform,
                onEnd = viewModel::onGestureEnd,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    !state.ready -> CircularProgressIndicator()
                    state.design.kind == TemplateKind.Carousel -> SlidePager(state, photos, gestures, viewModel)
                    else -> StudioCanvas(
                        design = state.design,
                        layout = layout,
                        photos = photos,
                        selected = state.selected,
                        showGuides = state.showGuides,
                        showSafeZones = state.showSafeZones,
                        gestures = gestures,
                        modifier = Modifier
                            .padding(16.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
                if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
                if (state.showSafeZones && state.ready) {
                    SafeZoneLegend(
                        Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
                        textColor = Color.White.copy(alpha = 0.8f),
                        square = state.design.kind != TemplateKind.Story,
                    )
                }
            }
            Surface(color = StudioColors.Panel, contentColor = Color.White) {
                Column(Modifier.navigationBarsPadding()) {
                    val mode = when (val layer = state.selectedLayer) {
                        is TextLayer -> 1
                        is PhotoLayer -> if (layer.video != null) 3 else 2
                        else -> 0
                    }
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { it / 3 })
                        },
                        label = "tools",
                    ) { m ->
                        when (m) {
                            1 -> (state.selectedLayer as? TextLayer)?.let { TextTools(it, viewModel, state.design.kind == TemplateKind.Carousel, state.design.palette?.let(Palettes::byId)) }
                            2 -> (state.selectedLayer as? PhotoLayer)?.let { PhotoTools(it, viewModel, state.design.kind == TemplateKind.Carousel) { pickOne.launch(imagesOnly) } }
                            3 -> (state.selectedLayer as? PhotoLayer)?.let { VideoTools(it, viewModel) { pickVideo.launch(videoOnly) } }
                            else -> Column {
                                AnimatedVisibility(visible = state.panel != StudioPanel.None) {
                                    PanelContent(state, viewModel)
                                }
                                MainToolbar(
                                    panel = state.panel,
                                    kind = state.design.kind,
                                    safeZones = state.showSafeZones,
                                    onSafeZones = viewModel::toggleSafeZones,
                                    onPhotos = { pickMany.launch(imagesOnly) },
                                    onVideo = { pickVideo.launch(videoOnly) },
                                    onText = viewModel::addText,
                                    onPanel = viewModel::openPanel,
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
    }

    if (state.previewing) {
        PreviewDialog(state, onClose = { viewModel.setPreviewing(false) })
    }
}

object StudioColors {
    val Backdrop = Color(0xFF0E0E10)
    val Panel = Color(0xFF17171A)
    val Chip = Color(0xFF26262B)
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    showGuides: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onGuides: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = Color.White
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = tint) }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), tint = tint.copy(alpha = if (canUndo) 1f else 0.35f))
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.redo), tint = tint.copy(alpha = if (canRedo) 1f else 0.35f))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onGuides) {
            Icon(if (showGuides) Icons.Rounded.GridOn else Icons.Rounded.GridOff, stringResource(R.string.toggle_guides), tint = tint)
        }
        IconButton(onClick = onPreview) { Icon(Icons.Rounded.Visibility, stringResource(R.string.preview), tint = tint) }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(BentuskColors.Gold)
                .clickable(onClick = onExport)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Upload, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.export), color = Color.Black, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Slides side by side with the next one peeking in, plus the whole strip above to see the flow. */
@Composable
private fun SlidePager(
    state: StudioState,
    photos: Map<Long, com.bentusk.app.core.image.SlotPhoto>,
    gestures: CanvasGestures,
    viewModel: StudioViewModel,
) {
    val layout = state.layout
    val pager = rememberPagerState(initialPage = state.page) { layout.count }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect(viewModel::setPage) }
    LaunchedEffect(state.page) { if (pager.currentPage != state.page) pager.animateScrollToPage(state.page) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // The whole strip: tap a slide to jump to it.
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            StudioCanvas(
                design = state.design,
                layout = layout,
                photos = photos,
                showGuides = true,
                empty = EmptySlots.Placeholder,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.matchParentSize()) {
                for (i in 0 until layout.count) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .then(if (i == pager.currentPage) Modifier.background(Color.White.copy(alpha = 0.18f)) else Modifier)
                            .clickable { viewModel.setPage(i) },
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageWidth = minOf(maxWidth * 0.84f, maxHeight * layout.tileSize.aspect.toFloat() * 0.94f)
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = (maxWidth - pageWidth) / 2),
                pageSpacing = 10.dp,
                userScrollEnabled = state.selected == null,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                StudioCanvas(
                    design = state.design,
                    layout = layout,
                    photos = photos,
                    region = layout.tiles[page.coerceAtMost(layout.count - 1)].rect,
                    selected = state.selected,
                    showSafeZones = state.showSafeZones,
                    gestures = gestures,
                    modifier = Modifier
                        .width(pageWidth)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until layout.count) {
                Box(
                    Modifier
                        .size(if (i == pager.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.35f)),
                )
            }
        }
    }
}

/** Full-screen look at the post as followers will swipe through it, or as the grid will sit on the profile. */
@Composable
private fun PreviewDialog(state: StudioState, onClose: () -> Unit) {
    val layout = state.layout
    val photos = state.photos
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            if (state.design.kind == TemplateKind.Carousel) {
                val pager = rememberPagerState { layout.count }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                        StudioCanvas(
                            design = state.design,
                            layout = layout,
                            photos = photos,
                            region = layout.tiles[page].rect,
                            empty = EmptySlots.Nothing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "${pager.currentPage + 1} / ${layout.count}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.merge(NumericStyle),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                StudioCanvas(
                    design = state.design,
                    layout = layout,
                    photos = photos,
                    showGuides = true,
                    empty = EmptySlots.Nothing,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
            Text(
                stringResource(R.string.preview_hint),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            )
        }
    }
}

@Composable
fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (selected) StudioColors.Chip else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) BentuskColors.Gold else Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
    }
}
