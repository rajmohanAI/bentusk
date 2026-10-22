package com.bentusk.app.feature.home

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.core.ads.HomeBanner
import com.bentusk.app.core.data.Project
import com.bentusk.app.feature.editor.SliceMode
import com.bentusk.app.navigation.StudioRoute
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.Entitlement
import com.bentusk.engine.ProFeature
import com.bentusk.engine.Template
import com.bentusk.engine.TemplateKind
import com.bentusk.engine.Templates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class HomeTab(val title: Int, val icon: ImageVector) {
    Carousel(R.string.tab_carousel, Icons.Rounded.ViewCarousel),
    Grid(R.string.tab_grid, Icons.Rounded.ViewModule),
    Favorites(R.string.tab_favorites, Icons.Rounded.Favorite),
    Projects(R.string.tab_projects, Icons.Rounded.Folder),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedPhoto: Uri?,
    onSharedPhotoHandled: () -> Unit,
    onOpenEditor: (SliceMode, Uri) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenCovers: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenStudio: (StudioRoute) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshProjects() }

    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pickCarousel = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onOpenEditor(SliceMode.Carousel, it) }
    }
    val pickGrid = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onOpenEditor(SliceMode.Grid, it) }
    }
    fun openTemplate(uris: List<Uri>) {
        val id = pending ?: return
        pending = null
        if (uris.isNotEmpty()) onOpenStudio(StudioRoute(templateId = id, photos = uris.joinToString("\n")))
    }
    val pickOneForTemplate = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        openTemplate(listOfNotNull(uri))
    }
    // The photo picker's limit is fixed when it is registered, so there is one picker per slot count.
    val pickers = HashMap<Int, ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>>()
    for (count in Templates.All.map { it.photos.size }.filter { it > 1 }.distinct().sorted()) {
        key(count) {
            pickers[count] = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(count)) { uris ->
                openTemplate(uris)
            }
        }
    }

    fun open(template: Template) {
        if (template.premium && !entitlement.allows(ProFeature.PremiumTemplates)) {
            onOpenPro()
            return
        }
        when (val n = template.photos.size) {
            0 -> onOpenStudio(StudioRoute(templateId = template.id))
            1 -> {
                pending = template.id
                pickOneForTemplate.launch(imagesOnly)
            }
            else -> {
                pending = template.id
                pickers.getValue(n).launch(imagesOnly)
            }
        }
    }

    val current = HomeTab.entries[tab]
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (entitlement.showsAds) HomeBanner()
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    HomeTab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, null) },
                            label = { Text(stringResource(t.title)) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = BentuskColors.Gold.copy(alpha = 0.25f)),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(BentuskColors.Gold)
                    .clickable { showCreate = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, stringResource(R.string.create), tint = Color.Black, modifier = Modifier.size(30.dp))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (current == HomeTab.Carousel) stringResource(R.string.app_name) else stringResource(current.title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                PlanChip(entitlement, onOpenPro)
            }
            if (current == HomeTab.Carousel || current == HomeTab.Grid) {
                SearchField(query) { query = it }
            }
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab",
                modifier = Modifier.weight(1f),
            ) { t ->
                when (t) {
                    HomeTab.Carousel -> CarouselTab(
                        query = query,
                        favorites = favorites,
                        onOpen = ::open,
                        onFavorite = viewModel::toggleFavorite,
                        onPanorama = { pickCarousel.launch(imagesOnly) },
                        onCovers = onOpenCovers,
                        onBlank = { onOpenStudio(StudioRoute(kind = TemplateKind.Carousel.name)) },
                    )
                    HomeTab.Grid -> GridTab(
                        query = query,
                        favorites = favorites,
                        onOpen = ::open,
                        onFavorite = viewModel::toggleFavorite,
                        onPuzzle = { pickGrid.launch(imagesOnly) },
                        onPlanner = onOpenPlanner,
                        onBlank = { onOpenStudio(StudioRoute(kind = TemplateKind.Grid.name)) },
                    )
                    HomeTab.Favorites -> FavoritesTab(favorites, ::open, viewModel::toggleFavorite)
                    HomeTab.Projects -> ProjectsTab(
                        projects = projects,
                        onOpen = { onOpenStudio(StudioRoute(projectId = it.id)) },
                        onDelete = { deleting = it.id },
                    )
                }
            }
        }
    }

    if (showCreate) {
        ModalBottomSheet(onDismissRequest = { showCreate = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    stringResource(R.string.create),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                CreateOption(Icons.Rounded.ViewCarousel, R.string.create_blank_carousel, R.string.create_blank_carousel_body) {
                    showCreate = false
                    onOpenStudio(StudioRoute(kind = TemplateKind.Carousel.name))
                }
                CreateOption(Icons.Rounded.ViewModule, R.string.create_blank_grid, R.string.create_blank_grid_body) {
                    showCreate = false
                    onOpenStudio(StudioRoute(kind = TemplateKind.Grid.name))
                }
                CreateOption(Icons.Rounded.Panorama, R.string.tool_carousel, R.string.tool_carousel_body) {
                    showCreate = false
                    pickCarousel.launch(imagesOnly)
                }
                CreateOption(Icons.Rounded.GridView, R.string.tool_grid, R.string.tool_grid_body) {
                    showCreate = false
                    pickGrid.launch(imagesOnly)
                }
                CreateOption(Icons.Rounded.TripOrigin, R.string.tool_covers, R.string.tool_covers_body) {
                    showCreate = false
                    onOpenCovers()
                }
                CreateOption(Icons.AutoMirrored.Rounded.ViewQuilt, R.string.tool_planner, R.string.tool_planner_body) {
                    showCreate = false
                    onOpenPlanner()
                }
            }
        }
    }

    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_project_title)) },
            text = { Text(stringResource(R.string.delete_project_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(id)
                    deleting = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.not_now)) } },
        )
    }

    if (sharedPhoto != null) {
        AlertDialog(
            onDismissRequest = onSharedPhotoHandled,
            title = { Text(stringResource(R.string.shared_photo_title)) },
            confirmButton = {
                TextButton(onClick = {
                    onSharedPhotoHandled()
                    onOpenEditor(SliceMode.Carousel, sharedPhoto)
                }) { Text(stringResource(R.string.tool_carousel)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSharedPhotoHandled()
                    onOpenEditor(SliceMode.Grid, sharedPhoto)
                }) { Text(stringResource(R.string.tool_grid)) }
            },
        )
    }
}

@Composable
private fun PlanChip(entitlement: Entitlement, onClick: () -> Unit) {
    val label = when (entitlement) {
        is Entitlement.Trial -> stringResource(R.string.trial_chip, entitlement.daysLeft)
        Entitlement.Free -> stringResource(R.string.go_pro)
        Entitlement.Pro -> stringResource(R.string.pro_chip)
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.WorkspacePremium, null, Modifier.width(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = BentuskColors.Gold),
    )
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_templates)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun Template.matches(query: String) =
    query.isBlank() || name.contains(query, ignoreCase = true) || category.contains(query, ignoreCase = true)

@Composable
private fun CarouselTab(
    query: String,
    favorites: Set<String>,
    onOpen: (Template) -> Unit,
    onFavorite: (String) -> Unit,
    onPanorama: () -> Unit,
    onCovers: () -> Unit,
    onBlank: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        if (query.isBlank()) {
            item {
                QuickTools(
                    listOf(
                        QuickTool(Icons.Rounded.Add, R.string.create_blank_carousel, onBlank),
                        QuickTool(Icons.Rounded.Panorama, R.string.tool_carousel, onPanorama),
                        QuickTool(Icons.Rounded.TripOrigin, R.string.tool_covers, onCovers),
                    ),
                )
            }
        }
        for ((category, list) in Templates.CarouselByCategory) {
            val shown = list.filter { it.matches(query) }
            if (shown.isEmpty()) continue
            item(key = category) {
                SectionHeader(category)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(shown, key = { _, t -> t.id }) { i, t ->
                        TemplateCard(
                            template = t,
                            favorite = t.id in favorites,
                            onClick = { onOpen(t) },
                            onFavorite = { onFavorite(t.id) },
                            modifier = Modifier.width(168.dp),
                            autoplayDelayMs = 2600L + i * 450L,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridTab(
    query: String,
    favorites: Set<String>,
    onOpen: (Template) -> Unit,
    onFavorite: (String) -> Unit,
    onPuzzle: () -> Unit,
    onPlanner: () -> Unit,
    onBlank: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        if (query.isBlank()) {
            item {
                QuickTools(
                    listOf(
                        QuickTool(Icons.Rounded.Add, R.string.create_blank_grid, onBlank),
                        QuickTool(Icons.Rounded.GridView, R.string.tool_grid, onPuzzle),
                        QuickTool(Icons.AutoMirrored.Rounded.ViewQuilt, R.string.tool_planner, onPlanner),
                    ),
                )
            }
        }
        item { SectionHeader(Templates.GRID) }
        val shown = Templates.Grid.filter { it.matches(query) }
        items(shown.chunked(2)) { pair -> CardPair(pair, favorites, onOpen, onFavorite) }
    }
}

@Composable
private fun FavoritesTab(favorites: Set<String>, onOpen: (Template) -> Unit, onFavorite: (String) -> Unit) {
    val list = Templates.All.filter { it.id in favorites }
    if (list.isEmpty()) {
        EmptyState(Icons.Rounded.Favorite, R.string.favorites_empty)
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        items(list.chunked(2)) { pair -> CardPair(pair, favorites, onOpen, onFavorite) }
    }
}

@Composable
private fun CardPair(pair: List<Template>, favorites: Set<String>, onOpen: (Template) -> Unit, onFavorite: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (t in pair) {
            TemplateCard(t, t.id in favorites, { onOpen(t) }, { onFavorite(t.id) }, Modifier.weight(1f))
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectsTab(projects: List<Project>, onOpen: (Project) -> Unit, onDelete: (Project) -> Unit) {
    if (projects.isEmpty()) {
        EmptyState(Icons.Rounded.Folder, R.string.projects_empty)
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        items(projects.chunked(2), key = { it.first().id }) { pair ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (p in pair) {
                    Column(
                        Modifier
                            .weight(1f)
                            .combinedClickable(onClick = { onOpen(p) }, onLongClick = { onDelete(p) }),
                    ) {
                        val thumb by produceState<ImageBitmap?>(null, p.thumbnail, p.updatedAt) {
                            value = p.thumbnail?.let { file -> withContext(Dispatchers.IO) { decode(file) } }
                        }
                        val ratio = p.design.layout().tileSize.aspect.toFloat()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        val name = p.design.templateId?.let { Templates.byId(it)?.name }
                            ?: stringResource(if (p.design.kind == TemplateKind.Carousel) R.string.create_blank_carousel else R.string.create_blank_grid)
                        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            DateUtils.getRelativeTimeSpanString(p.updatedAt).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Text(
                stringResource(R.string.projects_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

private fun decode(file: File): ImageBitmap? = runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()

@Composable
private fun EmptyState(icon: ImageVector, text: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
    )
}

private class QuickTool(val icon: ImageVector, val label: Int, val onClick: () -> Unit)

@Composable
private fun QuickTools(tools: List<QuickTool>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(tools) { tool ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = tool.onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tool.icon, null, tint = BentuskColors.Gold)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(tool.label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CreateOption(icon: ImageVector, title: Int, body: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = BentuskColors.Gold) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
