package com.bentusk.app.feature.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.bentusk.app.core.video.VideoOverlayRenderer
import com.bentusk.app.feature.studio.family
import com.bentusk.engine.Contrast
import com.bentusk.engine.TextFont
import com.bentusk.engine.VideoOverlay
import com.bentusk.engine.VideoOverlays
import com.bentusk.engine.CutTransition
import com.bentusk.engine.TransitionFrame
import com.bentusk.engine.VideoTransitions
import android.view.LayoutInflater
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bentusk.app.R
import com.bentusk.app.core.storage.ShareHelper
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.TimelineClip
import com.bentusk.engine.VideoShape
import com.bentusk.engine.VideoTimeline
import kotlinx.coroutines.delay

/** The video maker: a preview on top, the clips in order below it, and tools for the selected clip. */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMakerScreen(onBack: () -> Unit, viewModel: VideoMakerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(VideoTimeline.MAX_CLIPS)) {
        viewModel.add(it)
    }
    val pickAny = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    var elapsedForPick by remember { mutableLongStateOf(0L) }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addImage(it, elapsedForPick) }
    }
    var writing by remember { mutableStateOf<TextEdit?>(null) }
    val failedMessage = stringResource(R.string.video_export_failed)
    val unreadableMessage = pluralStringResource(R.plurals.import_failed, state.unreadable, state.unreadable)
    LaunchedEffect(state.failed, state.unreadable) {
        when {
            state.failed -> snackbar.showSnackbar(failedMessage, withDismissAction = true)
            state.unreadable > 0 -> snackbar.showSnackbar(unreadableMessage, withDismissAction = true)
            else -> return@LaunchedEffect
        }
        viewModel.consumeNotices()
    }

    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ALL } }
    DisposableEffect(player) { onDispose { player.release() } }
    var index by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    val clips = state.timeline.clips

    // Rebuild the preview's playlist a moment after the timeline changes, so a trim drag doesn't rebuild it per frame.
    // Transitions are drawn over the player, so changing one leaves the playlist alone.
    val media = remember(clips) { clips.map { it.copy(transition = CutTransition.None) } }
    LaunchedEffect(media) {
        delay(PLAYLIST_DEBOUNCE_MS)
        val keepIndex = player.currentMediaItemIndex.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
        val keepPosition = player.currentPosition
        player.setMediaItems(clips.map { mediaItem(context, it) }, keepIndex, keepPosition)
        player.prepare()
    }
    // Jump the preview to a clip when it is picked in the strip.
    LaunchedEffect(state.selected) {
        val at = clips.indexOfFirst { it.id == state.selected }
        if (at >= 0 && at < player.mediaItemCount && at != player.currentMediaItemIndex) player.seekTo(at, 0)
    }
    LaunchedEffect(player) {
        while (true) {
            index = player.currentMediaItemIndex
            position = player.currentPosition
            playing = player.isPlaying
            delay(TICK_MS)
        }
    }
    val current = clips.getOrNull(index)
    val elapsed = (current?.let { state.timeline.startOf(it.id) } ?: 0L) + position.coerceAtMost(current?.lengthMs ?: 0L)
    elapsedForPick = elapsed

    // Transitions are short, so while there are any the preview follows the player every frame rather than every tick.
    val timelineNow by rememberUpdatedState(state.timeline)
    val animated = VideoTransitions.any(state.timeline)
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, animated) {
        while (animated) {
            withFrameMillis { }
            val t = timelineNow
            val c = t.clips.getOrNull(player.currentMediaItemIndex)
            frameMs = (c?.let { t.startOf(it.id) } ?: 0L) + player.currentPosition.coerceAtMost(c?.lengthMs ?: 0L)
        }
    }
    val motion: (Long) -> TransitionFrame = remember(state.timeline) {
        val timeline = state.timeline
        val cuts = VideoTransitions.cuts(timeline)
        val f: (Long) -> TransitionFrame = { ms -> if (animated) VideoTransitions.frame(timeline, cuts, ms) else TransitionFrame.Still }
        f
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.video_maker)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo))
                    }
                    TextButton(
                        onClick = {
                            player.pause()
                            viewModel.export()
                        },
                        enabled = !state.timeline.isEmpty,
                    ) { Text(stringResource(R.string.export)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (shape in VideoShape.entries) {
                    FilterChip(
                        selected = state.timeline.shape == shape,
                        onClick = { viewModel.setShape(shape) },
                        label = { Text("${shapeName(shape)} · ${shape.label}") },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .aspectRatio(state.timeline.shape.size.aspect.toFloat())
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .clickable(enabled = clips.isNotEmpty()) { if (player.isPlaying) player.pause() else player.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (clips.isEmpty()) {
                        EmptyPrompt { pick.launch(pickAny) }
                    } else {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val f = motion(frameMs)
                                scaleX = f.scale.toFloat()
                                scaleY = f.scale.toFloat()
                                translationX = (f.shift * size.width).toFloat()
                            },
                        ) {
                            PlayerSurface(player)
                            if (current?.photo == true) PhotoOnTop(current.uri, viewModel)
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            val f = motion(frameMs)
                            if (f.dipAlpha > 0) drawRect(Color(f.dip))
                        }
                        OverlaysOnTop(state, elapsed, editing = state.mode == MakerMode.Layers, onDrag = viewModel::dragOverlay, onDragEnd = viewModel::endLive)
                        if (!playing) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                stringResource(R.string.video_play),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }
                    if (state.adding) CircularProgressIndicator()
                }
            }
            if (clips.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (playing) R.string.video_pause else R.string.video_play),
                        )
                    }
                    Text("${clock(elapsed)} / ${clock(state.timeline.totalMs)}", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    if (state.timeline.tooLong) {
                        Text(
                            stringResource(R.string.video_too_long, VideoTimeline.MAX_TOTAL_MS / 60_000),
                            color = BentuskColors.Coral,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.mode == MakerMode.Clips,
                        onClick = { viewModel.setMode(MakerMode.Clips) },
                        label = { Text(stringResource(R.string.video_mode_clips)) },
                    )
                    FilterChip(
                        selected = state.mode == MakerMode.Layers,
                        onClick = { viewModel.setMode(MakerMode.Layers) },
                        label = { Text(stringResource(R.string.video_mode_layers)) },
                    )
                    FilterChip(
                        selected = state.mode == MakerMode.Transitions,
                        onClick = { viewModel.setMode(MakerMode.Transitions) },
                        label = { Text(stringResource(R.string.video_mode_transitions)) },
                    )
                }
                Column(Modifier.heightIn(max = TOOLS_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                    when (state.mode) {
                        MakerMode.Clips -> {
                            ClipStrip(state, onSelect = viewModel::select, onAdd = { pick.launch(pickAny) })
                            state.selectedClip?.let { clip ->
                                ClipTools(
                                    clip = clip,
                                    canSplit = current?.id == clip.id,
                                    onTrim = viewModel::trim,
                                    onTrimDone = viewModel::trimDone,
                                    onSplit = { viewModel.split(position) },
                                    onMute = viewModel::toggleMute,
                                    onMove = viewModel::move,
                                    onDuplicate = viewModel::duplicate,
                                    onRemove = viewModel::remove,
                                )
                            }
                        }
                        MakerMode.Transitions -> {
                            ClipStrip(state, onSelect = viewModel::select, onAdd = { pick.launch(pickAny) })
                            TransitionTools(
                                state = state,
                                onPick = { t ->
                                    viewModel.setTransition(t)
                                    if (t != CutTransition.None) playCut(player, clips, state.selected)
                                },
                                onAll = viewModel::setAllTransitions,
                                onFadeEnds = viewModel::toggleFadeEnds,
                            )
                        }
                        MakerMode.Layers -> LayerTools(
                            state = state,
                            elapsed = elapsed,
                            viewModel = viewModel,
                            onAddText = { writing = TextEdit.New },
                            onEditText = { writing = TextEdit.Existing },
                            onAddImage = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                    }
                }
            }
        }
    }

    writing?.let { mode ->
        TextDialog(
            initial = if (mode == TextEdit.Existing) state.overlay?.text.orEmpty() else "",
            onDismiss = { writing = null },
            onDone = { text ->
                if (mode == TextEdit.New) viewModel.addText(text, elapsed) else viewModel.editText(text)
                writing = null
            },
        )
    }

    state.progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.video_exporting)) },
            text = {
                Column {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.video_exporting_body),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::cancelExport) { Text(stringResource(R.string.video_cancel)) } },
        )
    }
    state.saved?.let { uri ->
        var dismissed by remember(uri) { mutableStateOf(false) }
        if (!dismissed) {
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text(stringResource(R.string.video_saved)) },
                text = { Text(stringResource(R.string.video_saved_body)) },
                confirmButton = {
                    Button(onClick = {
                        dismissed = true
                        ShareHelper.shareImages(context, listOf(uri), VIDEO_MIME, false, context.getString(R.string.share))
                    }) { Text(stringResource(R.string.share)) }
                },
                dismissButton = { TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.done)) } },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(player: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            // A TextureView rather than the default SurfaceView, so the preview can be zoomed and moved for transitions.
            (LayoutInflater.from(ctx).inflate(R.layout.video_preview_player, null) as PlayerView).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Photos are drawn by the app over the player, cropped to fill the frame as they are in the export. */
@Composable
private fun PhotoOnTop(uri: String, viewModel: VideoMakerViewModel) {
    val bitmap by produceState<Bitmap?>(null, uri) { value = viewModel.preview(uri) }
    bitmap?.let {
        Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun EmptyPrompt(onAdd: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(
            stringResource(R.string.video_empty),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onAdd) { Text(stringResource(R.string.video_add)) }
    }
}

@Composable
private fun ClipStrip(state: VideoMakerState, onSelect: (Long) -> Unit, onAdd: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.timeline.clips, key = { _, c -> c.id }) { i, clip ->
            val selected = clip.id == state.selected
            Box(
                Modifier
                    .size(width = 64.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BentuskColors.Slate)
                    .border(if (selected) 2.dp else 0.dp, if (selected) BentuskColors.Gold else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(clip.id) },
            ) {
                state.thumbs[clip.uri]?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Text(
                    "${i + 1}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp),
                )
                if (i > 0 && clip.transition != CutTransition.None) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        stringResource(transitionName(clip.transition)),
                        tint = BentuskColors.Gold,
                        modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f)).padding(2.dp).size(14.dp),
                    )
                }
                Text(
                    clock(clip.lengthMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp),
                )
            }
        }
        if (state.timeline.canAdd) {
            item(key = "add") {
                Box(
                    Modifier
                        .size(width = 64.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, BentuskColors.Mist, RoundedCornerShape(8.dp))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, stringResource(R.string.video_add)) }
            }
        }
    }
}

@Composable
private fun ClipTools(
    clip: TimelineClip,
    canSplit: Boolean,
    onTrim: (Long, Long) -> Unit,
    onTrimDone: () -> Unit,
    onSplit: () -> Unit,
    onMute: () -> Unit,
    onMove: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (clip.photo) {
            Text(stringResource(R.string.video_photo_time, clock(clip.lengthMs)), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = clip.lengthMs.toFloat(),
                onValueChange = { onTrim(0, it.toLong()) },
                onValueChangeFinished = onTrimDone,
                valueRange = VideoTimeline.MIN_PIECE_MS.toFloat()..VideoTimeline.PHOTO_MAX_MS.toFloat(),
            )
        } else {
            Text(
                stringResource(R.string.video_trim_range, clock(clip.startMs), clock(clip.endMs), clock(clip.durationMs)),
                style = MaterialTheme.typography.labelLarge,
            )
            RangeSlider(
                value = clip.startMs.toFloat()..clip.endMs.toFloat(),
                onValueChange = { onTrim(it.start.toLong(), it.endInclusive.toLong()) },
                onValueChangeFinished = onTrimDone,
                valueRange = 0f..clip.durationMs.toFloat(),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (!clip.photo) {
                Tool(Icons.Rounded.ContentCut, R.string.video_split, enabled = canSplit, onClick = onSplit)
                Tool(if (clip.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (clip.muted) R.string.video_unmute else R.string.video_mute, onClick = onMute)
            }
            Tool(Icons.Rounded.ChevronLeft, R.string.video_move_earlier) { onMove(-1) }
            Tool(Icons.Rounded.ChevronRight, R.string.video_move_later) { onMove(1) }
            Tool(Icons.Rounded.ContentCopy, R.string.video_duplicate, onClick = onDuplicate)
            Tool(Icons.Rounded.Delete, R.string.remove, onClick = onRemove)
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        Icon(icon, null, tint = tint)
        Text(stringResource(label), color = tint, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun shapeName(shape: VideoShape): String = stringResource(
    when (shape) {
        VideoShape.Reel -> R.string.video_shape_reel
        VideoShape.Portrait -> R.string.video_shape_portrait
        VideoShape.Square -> R.string.video_shape_square
    },
)

/** The preview plays each clip trimmed, and each photo for its time, in the same order as the export. */
@OptIn(UnstableApi::class)
private fun mediaItem(context: Context, clip: TimelineClip): MediaItem {
    val uri = Uri.parse(clip.uri)
    return if (clip.photo) {
        MediaItem.Builder()
            .setUri(uri)
            .setMimeType(context.contentResolver.getType(uri))
            .setImageDurationMs(clip.lengthMs)
            .build()
    } else {
        MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build(),
            )
            .build()
    }
}

/** Plays the second before the selected piece's cut, so a newly picked transition shows straight away. */
private fun playCut(player: ExoPlayer, clips: List<TimelineClip>, selected: Long?) {
    val i = clips.indexOfFirst { it.id == selected }
    if (i <= 0 || i - 1 >= player.mediaItemCount) return
    player.seekTo(i - 1, (clips[i - 1].lengthMs - CUT_LEAD_MS).coerceAtLeast(0))
    player.play()
}

private fun transitionName(t: CutTransition): Int = when (t) {
    CutTransition.None -> R.string.video_transition_none
    CutTransition.Fade -> R.string.video_transition_fade
    CutTransition.Flash -> R.string.video_transition_flash
    CutTransition.Zoom -> R.string.video_transition_zoom
    CutTransition.Whip -> R.string.video_transition_whip
}

/** How the selected piece comes in from the one before, and the fade at the very start and end. */
@Composable
private fun TransitionTools(
    state: VideoMakerState,
    onPick: (CutTransition) -> Unit,
    onAll: (CutTransition) -> Unit,
    onFadeEnds: () -> Unit,
) {
    val clips = state.timeline.clips
    val clip = state.selectedClip
    val i = clip?.let { c -> clips.indexOfFirst { it.id == c.id } } ?: -1
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (clip == null || i <= 0) {
            Text(
                stringResource(R.string.video_transition_pick),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.video_transition_into, i, i + 1), style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (t in CutTransition.entries) {
                    FilterChip(
                        selected = clip.transition == t,
                        onClick = { onPick(t) },
                        label = { Text(stringResource(transitionName(t))) },
                    )
                }
            }
            if (clips.size > 2) {
                TextButton(onClick = { onAll(clip.transition) }) { Text(stringResource(R.string.video_transition_all)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.video_fade_ends), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.timeline.fadeEnds, onCheckedChange = { onFadeEnds() })
        }
    }
}

/** 0:05, 1:02 */
private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private enum class TextEdit { New, Existing }

/** Colours for text and labels: white and black first, then the app's accents and a few strong hues. */
private val TextColors = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFE6C17E, 0xFFFF7A6B, 0xFF8FB8FF, 0xFFFFD54F, 0xFF66BB6A, 0xFFEC407A, 0xFF7E57C2,
).map { it.toInt() }

/** The text and pictures on screen at [elapsed], plus the selected one while editing, drawn as the export draws them. */
@Composable
private fun OverlaysOnTop(state: VideoMakerState, elapsed: Long, editing: Boolean, onDrag: (Double, Double) -> Unit, onDragEnd: () -> Unit) {
    val timeline = state.timeline
    val selected = if (editing) state.overlay else null
    val shown = VideoOverlays.visibleAt(timeline, elapsed).let { v -> if (selected != null && selected !in v) v + selected else v }
    if (shown.isEmpty()) return
    val frame = timeline.shape.size
    val gold = BentuskColors.Gold
    Canvas(
        Modifier
            .fillMaxSize()
            .then(
                if (selected == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(selected.id) {
                        detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, drag ->
                            change.consume()
                            onDrag(drag.x.toDouble() / size.width, drag.y.toDouble() / size.height)
                        }
                    }
                },
            ),
    ) {
        val k = size.width / frame.width
        drawIntoCanvas { c ->
            c.nativeCanvas.save()
            c.nativeCanvas.scale(k, k)
            VideoOverlayRenderer.draw(c.nativeCanvas, frame, shown, state.overlayImages)
            c.nativeCanvas.restore()
        }
        selected?.let { o ->
            drawRect(
                gold,
                topLeft = Offset((o.rect.left * size.width).toFloat(), (o.rect.top * size.height).toFloat()),
                size = Size(((o.rect.right - o.rect.left) * size.width).toFloat(), ((o.rect.bottom - o.rect.top) * size.height).toFloat()),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
            )
        }
    }
}

@Composable
private fun LayerTools(
    state: VideoMakerState,
    elapsed: Long,
    viewModel: VideoMakerViewModel,
    onAddText: () -> Unit,
    onEditText: () -> Unit,
    onAddImage: () -> Unit,
) {
    val timeline = state.timeline
    val canAdd = timeline.overlays.size < VideoOverlays.MAX_OVERLAYS
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(onClick = onAddText, enabled = canAdd, label = { Text(stringResource(R.string.video_add_text)) }, leadingIcon = {
                Icon(Icons.Rounded.TextFields, null)
            })
            AssistChip(onClick = onAddImage, enabled = canAdd, label = { Text(stringResource(R.string.video_add_picture)) }, leadingIcon = {
                Icon(Icons.Rounded.AddPhotoAlternate, null)
            })
            timeline.overlays.forEachIndexed { i, o ->
                FilterChip(
                    selected = o.id == state.selectedOverlay,
                    onClick = { viewModel.selectOverlay(if (o.id == state.selectedOverlay) null else o.id) },
                    label = {
                        Text(
                            o.text?.take(LABEL_CHARS) ?: stringResource(R.string.video_picture_n, i + 1),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        if (timeline.overlays.isEmpty()) {
            Text(
                stringResource(R.string.video_layers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val o = state.overlay ?: return@Column
        val total = maxOf(timeline.totalMs, VideoTimeline.MIN_PIECE_MS)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.video_shows_range, clock(o.startMs), clock(o.endMs)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                val length = o.endMs - o.startMs
                viewModel.setOverlayTimes(elapsed, elapsed + length)
                viewModel.endLive()
            }) { Text(stringResource(R.string.video_start_here)) }
        }
        RangeSlider(
            value = o.startMs.toFloat().coerceAtMost(total.toFloat())..o.endMs.toFloat().coerceAtMost(total.toFloat()),
            onValueChange = { viewModel.setOverlayTimes(it.start.toLong(), it.endInclusive.toLong()) },
            onValueChangeFinished = viewModel::endLive,
            valueRange = 0f..total.toFloat(),
        )
        if (o.isText) {
            Text(stringResource(R.string.video_text_size), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = o.size.toFloat(),
                onValueChange = { viewModel.setTextSize(it.toDouble()) },
                onValueChangeFinished = viewModel::endLive,
                valueRange = VideoOverlay.MIN_TEXT_SIZE.toFloat()..VideoOverlay.MAX_TEXT_SIZE.toFloat(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in TextColors) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (o.argb == c) 3.dp else 1.dp, if (o.argb == c) BentuskColors.Gold else BentuskColors.Mist, CircleShape)
                            .clickable { viewModel.setOverlayColor(c) },
                    )
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (font in TextFont.entries) {
                    FilterChip(
                        selected = o.font == font,
                        onClick = { viewModel.setOverlayFont(font) },
                        label = { Text(font.name, fontFamily = font.family()) },
                    )
                }
            }
        } else {
            Text(stringResource(R.string.video_picture_size), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = (o.rect.right - o.rect.left).toFloat(),
                onValueChange = { viewModel.setImageWidth(it.toDouble()) },
                onValueChangeFinished = viewModel::endLive,
                valueRange = 0.1f..1f,
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (o.isText) {
                Tool(Icons.Rounded.Edit, R.string.video_edit_text, onClick = onEditText)
                Tool(Icons.Rounded.FormatBold, R.string.video_bold) { viewModel.toggleBold() }
                // A dark label behind light text, or a light one behind dark text.
                Tool(Icons.AutoMirrored.Rounded.Label, R.string.video_label) {
                    viewModel.togglePill(if (Contrast.luminance(o.argb) > 0.5) LABEL_DARK else LABEL_LIGHT)
                }
            }
            Tool(Icons.Rounded.Delete, R.string.remove, onClick = viewModel::removeOverlay)
        }
        Text(
            stringResource(R.string.video_drag_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextDialog(initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_add_text)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(MAX_TEXT_CHARS) }, minLines = 2) },
        confirmButton = { Button(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.video_cancel)) } },
    )
}

private const val LABEL_CHARS = 12
private const val MAX_TEXT_CHARS = 200
private val LABEL_DARK = 0xCC000000.toInt()
private val LABEL_LIGHT = 0xE6FFFFFF.toInt()
private val TOOLS_MAX_HEIGHT = 300.dp

private const val VIDEO_MIME = "video/mp4"
private const val PLAYLIST_DEBOUNCE_MS = 250L
private const val TICK_MS = 200L
private const val CUT_LEAD_MS = 1_000L
