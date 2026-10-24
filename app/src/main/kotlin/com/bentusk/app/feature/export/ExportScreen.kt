package com.bentusk.app.feature.export

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.core.image.ExportedTile
import com.bentusk.app.core.storage.ShareHelper
import com.bentusk.app.ui.components.Thumbnail
import com.bentusk.app.ui.theme.NumericStyle
import com.bentusk.engine.InstagramSpec
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun ExportScreen(onDone: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onPermissionResult(it)
    }
    LaunchedEffect(state.needsPermission) {
        if (state.needsPermission) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    var showReview by remember { mutableStateOf(false) }
    // Grid tiles are posted one at a time; remember which ones the user has already sent.
    var posted by remember { mutableStateOf(setOf<Int>()) }
    val instagramInstalled = remember { ShareHelper.isInstagramInstalled(context) }
    LaunchedEffect(state.askForReview) { if (state.askForReview) showReview = true }

    var consentFor by remember { mutableStateOf<List<Uri>?>(null) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var pendingReminder by remember { mutableStateOf<LocalDateTime?>(null) }
    val notificationsNeeded = stringResource(R.string.reminder_needs_notifications)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val time = pendingReminder
        pendingReminder = null
        if (granted && time != null) viewModel.remind(time) else Toast.makeText(context, notificationsNeeded, Toast.LENGTH_LONG).show()
    }
    fun setReminder(time: LocalDateTime) {
        showReminderPicker = false
        if (viewModel.canNotify() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.remind(time)
        } else {
            pendingReminder = time
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val captionCopied = stringResource(R.string.caption_copied)
    // Instagram cannot take a caption through a share intent, so it goes on the clipboard for a long-press paste.
    fun post(uris: List<Uri>) {
        if (state.caption.isNotBlank()) {
            ShareHelper.copyText(context, context.getString(R.string.caption), state.captionToPost)
            Toast.makeText(context, captionCopied, Toast.LENGTH_LONG).show()
        }
        ShareHelper.shareImages(context, uris, state.shareMime, true, context.getString(R.string.share))
    }
    fun requestPost(uris: List<Uri>) {
        when {
            // Without Instagram there is nothing to hand over to, so offer to install it.
            !instagramInstalled -> ShareHelper.openInstagramListing(context)
            state.instagramConsent -> post(uris)
            else -> consentFor = uris
        }
    }
    // A grid goes up one tile at a time, in order; the main button always posts the next one.
    val nextTile = state.tiles.sortedBy { it.tile.postOrder }.firstOrNull { it.tile.postOrder !in posted }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.failed -> Failed(onRetry = viewModel::retry, onDone = onDone)
                !state.finished -> Working(state.done, state.total)
                else -> {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Header(state)
                        when (state.kind) {
                            ExportKind.Grid -> GridResult(state.tiles, posted) { item ->
                                posted = posted + item.tile.postOrder
                                requestPost(listOf(item.uri))
                            }
                            ExportKind.Carousel -> CarouselResult(state.tiles)
                            ExportKind.Cover -> state.coverUri?.let { CoverResult(it) }
                        }
                        if (state.hasCaption) {
                            Spacer(Modifier.height(20.dp))
                            CaptionComposer(
                                caption = state.caption,
                                onCaptionChange = viewModel::setCaption,
                                onToggleHashtag = viewModel::toggleHashtag,
                                onCopy = {
                                    ShareHelper.copyText(context, context.getString(R.string.caption), state.captionToPost)
                                    Toast.makeText(context, captionCopied, Toast.LENGTH_SHORT).show()
                                },
                                onWriteWithAi = viewModel::openAi,
                                keepLineBreaks = state.keepLineBreaks,
                                onKeepLineBreaks = viewModel::setKeepLineBreaks,
                            )
                        }
                    }
                    Actions(
                        instagramLabel = when {
                            state.kind != ExportKind.Grid -> stringResource(R.string.post_to_instagram)
                            nextTile != null -> stringResource(R.string.post_tile_to_instagram, nextTile.tile.postOrder, state.tiles.size)
                            else -> stringResource(R.string.grid_all_posted)
                        },
                        instagramEnabled = state.kind != ExportKind.Grid || nextTile != null,
                        onShareInstagram = {
                            if (state.kind == ExportKind.Grid) {
                                nextTile?.let {
                                    posted = posted + it.tile.postOrder
                                    requestPost(listOf(it.uri))
                                }
                            } else {
                                requestPost(state.uris)
                            }
                        },
                        onShare = {
                            ShareHelper.shareImages(context, state.uris, state.shareMime, false, context.getString(R.string.share))
                        },
                        onShareApp = {
                            ShareHelper.shareText(
                                context,
                                context.getString(R.string.share_app_text, playStoreUrl(context.packageName)),
                                context.getString(R.string.share),
                            )
                        },
                        onDone = {
                            viewModel.clearCaption()
                            onDone()
                        },
                        reminder = if (state.kind == ExportKind.Cover) null else state.reminderAt,
                        canRemind = state.kind != ExportKind.Cover,
                        onRemind = { showReminderPicker = true },
                    )
                }
            }
        }
    }

    if (showReminderPicker) {
        ReminderPicker(onPick = ::setReminder, onDismiss = { showReminderPicker = false })
    }

    if (state.ai.open) {
        AiCaptionSheet(
            state = state.ai,
            onDismiss = viewModel::closeAi,
            onTone = viewModel::setTone,
            onLanguage = viewModel::setLanguage,
            onEmoji = viewModel::setEmoji,
            onAgain = viewModel::writeAgain,
            onUse = viewModel::useIdea,
        )
    }

    consentFor?.let { uris ->
        AlertDialog(
            onDismissRequest = { consentFor = null },
            title = { Text(stringResource(R.string.instagram_consent_title)) },
            text = { Text(stringResource(R.string.instagram_consent_body)) },
            confirmButton = {
                TextButton(onClick = {
                    consentFor = null
                    viewModel.grantInstagramConsent()
                    post(uris)
                }) { Text(stringResource(R.string.instagram_consent_allow)) }
            },
            dismissButton = { TextButton(onClick = { consentFor = null }) { Text(stringResource(R.string.not_now)) } },
        )
    }

    if (showReview) {
        AlertDialog(
            onDismissRequest = { showReview = false },
            title = { Text(stringResource(R.string.review_title)) },
            text = { Text(stringResource(R.string.review_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showReview = false
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(playStoreUrl(context.packageName)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.review_yes)) }
            },
            dismissButton = { TextButton(onClick = { showReview = false }) { Text(stringResource(R.string.not_now)) } },
        )
    }
}

/** A warm sunset gradient that reads as "post" without copying Instagram's mark. */
private val InstagramGradient = Brush.linearGradient(listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF)))

private fun playStoreUrl(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"

@Composable
private fun Working(done: Int, total: Int) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (total == 0) 0f else done / total.toFloat() },
                modifier = Modifier.size(96.dp),
                strokeWidth = 6.dp,
            )
            Text("$done/$total", style = MaterialTheme.typography.titleMedium.merge(NumericStyle))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.exporting), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.exporting_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Failed(onRetry: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.export_failed), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.close)) }
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun Header(state: ExportUiState) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.hasCaption) stringResource(R.string.ready_to_post) else stringResource(R.string.saved_to_gallery, state.uris.size),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (state.hasCaption) {
            Text(
                stringResource(R.string.saved_to_gallery, state.uris.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val hint = when (state.kind) {
            ExportKind.Grid -> R.string.grid_post_hint
            ExportKind.Carousel -> R.string.carousel_post_hint
            ExportKind.Cover -> R.string.cover_post_hint
        }
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Tiles laid out as they will appear on the profile, each labelled with its posting step. */
@Composable
private fun GridResult(tiles: List<ExportedTile>, posted: Set<Int>, onPost: (ExportedTile) -> Unit) {
    val ratio = tiles.first().tile.rect.let { it.width.toFloat() / it.height }
    val next = (1..tiles.size).firstOrNull { it !in posted }
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in tiles.sortedBy { it.tile.index }.chunked(InstagramSpec.GRID_COLUMNS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (item in row) {
                    Box(Modifier.weight(1f).aspectRatio(ratio).clickable { onPost(item) }) {
                        Thumbnail(item.uri, item.fileName, Modifier.fillMaxSize())
                        if (item.tile.postOrder in posted) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = Color.White)
                            }
                        }
                        StepBadge(item.tile.postOrder, highlight = item.tile.postOrder == next)
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselResult(tiles: List<ExportedTile>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(280.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tiles.sortedBy { it.tile.postOrder }, key = { it.tile.index }) { item ->
            val ratio = item.tile.rect.let { it.width.toFloat() / it.height }
            Box(Modifier.height(260.dp).aspectRatio(ratio).clip(RoundedCornerShape(6.dp))) {
                Thumbnail(item.uri, item.fileName, Modifier.fillMaxSize())
                StepBadge(item.tile.postOrder, highlight = false)
            }
        }
    }
}

@Composable
private fun CoverResult(uri: Uri) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Thumbnail(uri, null, Modifier.size(180.dp).clip(CircleShape))
    }
}

@Composable
private fun StepBadge(step: Int, highlight: Boolean) {
    Box(
        Modifier
            .padding(6.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(if (highlight) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            step.toString(),
            style = MaterialTheme.typography.labelMedium.merge(NumericStyle),
            color = if (highlight) MaterialTheme.colorScheme.onPrimary else Color.White,
        )
    }
}

@Composable
private fun Actions(
    instagramLabel: String,
    instagramEnabled: Boolean,
    onShareInstagram: () -> Unit,
    onShare: () -> Unit,
    onShareApp: () -> Unit,
    onDone: () -> Unit,
    reminder: LocalDateTime?,
    canRemind: Boolean,
    onRemind: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onShareInstagram,
            enabled = instagramEnabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (instagramEnabled) InstagramGradient else Brush.linearGradient(listOf(Color.Gray, Color.Gray)),
                        RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(instagramLabel, color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.IosShare, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.share))
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.done)) }
        }
        if (canRemind) {
            TextButton(onClick = onRemind, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(if (reminder == null) Icons.Rounded.Alarm else Icons.Rounded.AlarmOn, null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (reminder == null) {
                        stringResource(R.string.reminder_set_button)
                    } else {
                        stringResource(R.string.reminder_set_for, reminderLabel(reminder, LocalDate.now()))
                    },
                )
            }
        }
        TextButton(onClick = onShareApp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.share_app))
        }
    }
}
