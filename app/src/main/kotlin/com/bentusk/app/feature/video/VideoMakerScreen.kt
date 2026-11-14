package com.bentusk.app.feature.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
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
    LaunchedEffect(clips) {
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
                        PlayerSurface(player)
                        if (current?.photo == true) PhotoOnTop(current.uri, viewModel)
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
        }
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
            PlayerView(ctx).apply {
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

/** 0:05, 1:02 */
private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private const val VIDEO_MIME = "video/mp4"
private const val PLAYLIST_DEBOUNCE_MS = 250L
private const val TICK_MS = 200L
