package com.bentusk.app.feature.planner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.ui.components.ChoiceRow
import com.bentusk.app.ui.components.Thumbnail
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.InstagramArchive
import com.bentusk.engine.InstagramSpec
import com.bentusk.engine.PostRatio
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

private const val MAX_PICK = 30

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlannerScreen(onBack: () -> Unit, onUpgrade: () -> Unit, viewModel: PlannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) {
        viewModel.add(it)
    }
    val lockedMessage = stringResource(R.string.locked_feature)
    val goPro = stringResource(R.string.go_pro)
    val failedMessage = pluralStringResource(R.plurals.import_failed, state.failedImports, state.failedImports)
    val newPlanName = stringResource(R.string.planner_new_name, state.plans.size + 1)
    var showImportHelp by remember { mutableStateOf(false) }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importArchive)
    }
    val context = LocalContext.current
    LaunchedEffect(state.archiveResult) {
        val result = state.archiveResult ?: return@LaunchedEffect
        viewModel.consumeArchiveResult()
        val res = context.resources
        val message = when (result) {
            is ArchiveImport.Done -> buildString {
                append(res.getQuantityString(R.plurals.planner_import_done, result.added, result.added))
                if (result.alreadyThere > 0) append(' ').append(res.getQuantityString(R.plurals.planner_import_already, result.alreadyThere, result.alreadyThere))
                if (result.failed > 0) append(' ').append(res.getQuantityString(R.plurals.import_failed, result.failed, result.failed))
            }
            ArchiveImport.NoPosts -> res.getString(R.string.planner_import_none)
            ArchiveImport.Unreadable -> res.getString(R.string.planner_import_unreadable)
        }
        snackbar.showSnackbar(message, withDismissAction = true)
    }

    LaunchedEffect(locked) {
        if (locked) {
            viewModel.consumeLocked()
            if (snackbar.showSnackbar(lockedMessage, goPro, withDismissAction = true) == SnackbarResult.ActionPerformed) onUpgrade()
        }
    }
    val pinLimitMessage = stringResource(R.string.planner_pin_limit, InstagramSpec.MAX_PINNED)
    LaunchedEffect(state.pinLimitHit) {
        if (state.pinLimitHit) {
            viewModel.consumePinLimit()
            snackbar.showSnackbar(pinLimitMessage)
        }
    }
    LaunchedEffect(state.failedImports) {
        if (state.failedImports > 0) {
            viewModel.consumeFailures()
            snackbar.showSnackbar(failedMessage)
        }
    }

    if (showImportHelp) {
        AlertDialog(
            onDismissRequest = { showImportHelp = false },
            title = { Text(stringResource(R.string.planner_import_title)) },
            text = { Text(stringResource(R.string.planner_import_body, InstagramArchive.MAX_POSTS)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportHelp = false
                    zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }) { Text(stringResource(R.string.planner_import_choose)) }
            },
            dismissButton = { TextButton(onClick = { showImportHelp = false }) { Text(stringResource(R.string.not_now)) } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_planner)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showImportHelp = true }) {
                        Icon(Icons.Rounded.Unarchive, stringResource(R.string.planner_import))
                    }
                    if (state.plans.size > 1) {
                        IconButton(onClick = viewModel::deleteSelectedPlan) {
                            Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.planner_delete_plan))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                icon = { Icon(Icons.Rounded.AddPhotoAlternate, null) },
                text = { Text(stringResource(R.string.planner_add)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val planOptions = state.plans.map { it.id } + NEW_PLAN
            ChoiceRow(
                options = planOptions,
                selected = state.selectedPlanId ?: NEW_PLAN,
                label = { id -> if (id == NEW_PLAN) "+ " + stringResource(R.string.planner_new) else state.plans.first { it.id == id }.name },
                onSelect = { id -> if (id == NEW_PLAN) viewModel.newPlan(newPlanName) else viewModel.select(id) },
                modifier = Modifier.padding(vertical = 8.dp),
            )
            ChoiceRow(
                options = listOf(false, true),
                selected = state.squarePreview,
                label = { square -> stringResource(if (square) R.string.planner_view_square else R.string.planner_view_grid) },
                onSelect = viewModel::setSquarePreview,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (state.adding) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (state.items.isEmpty() && !state.adding) {
                EmptyPlanner()
            } else {
                val gridState = rememberLazyGridState()
                val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
                    viewModel.move(from.index, to.index)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                val ratio = if (state.squarePreview) 1f else PostRatio.GridDefault.size.aspect.toFloat()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(InstagramSpec.GRID_COLUMNS),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ReorderableItem(reorderState, key = item.id) { isDragging ->
                            Box(
                                Modifier
                                    .aspectRatio(ratio)
                                    .scale(if (isDragging) 1.05f else 1f)
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            viewModel.onDragStart()
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = { viewModel.onDragEnd() },
                                    ),
                            ) {
                                Thumbnail(item.thumbPath, null, Modifier.fillMaxSize())
                                IconButton(
                                    onClick = { viewModel.togglePin(item) },
                                    modifier = Modifier.align(Alignment.TopStart).size(32.dp),
                                ) {
                                    Icon(
                                        if (item.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                        stringResource(if (item.pinned) R.string.planner_unpin else R.string.planner_pin),
                                        tint = if (item.pinned) BentuskColors.Gold else Color.White,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .padding(3.dp)
                                            .size(14.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.remove(item) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        stringResource(R.string.remove),
                                        tint = Color.White,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .padding(2.dp)
                                            .size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val NEW_PLAN = -1L

@Composable
private fun EmptyPlanner() {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.GridView, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.planner_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.planner_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
