package com.bentusk.app.feature.templates

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.feature.home.HomeViewModel
import com.bentusk.app.ui.components.ProBadge
import com.bentusk.engine.ProFeature
import com.bentusk.engine.Template
import com.bentusk.engine.TemplateKind
import com.bentusk.engine.Templates

/** All templates, carousels and grids on separate tabs. Picking one asks for exactly as many photos as it holds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateGalleryScreen(
    onBack: () -> Unit,
    onOpenTemplate: (Template, List<Uri>) -> Unit,
    onUpgrade: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    // The photo picker's limit is fixed when it is registered, so there is one picker per slot count.
    val pickers = HashMap<Int, ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>>()
    for (count in Templates.All.map { it.photos.size }.distinct().sorted()) {
        key(count) {
            pickers[count] = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(count)) { uris ->
                val template = pending?.let(Templates::byId)
                pending = null
                if (template != null && uris.isNotEmpty()) onOpenTemplate(template, uris)
            }
        }
    }

    fun open(template: Template) {
        if (template.premium && !entitlement.allows(ProFeature.PremiumTemplates)) {
            onUpgrade()
            return
        }
        pending = template.id
        pickers.getValue(template.photos.size).launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_templates)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_carousel)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_grid)) })
            }
            val list = if (tab == 0) Templates.Carousel else Templates.Grid
            LazyVerticalGrid(
                columns = if (tab == 0) GridCells.Fixed(1) else GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(if (tab == 0) R.string.templates_carousel_hint else R.string.templates_grid_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(list, key = { it.id }) { template ->
                    TemplateCard(
                        template = template,
                        locked = template.premium && !entitlement.allows(ProFeature.PremiumTemplates),
                        onClick = { open(template) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: Template, locked: Boolean, onClick: () -> Unit) {
    // Locked templates stay fully visible; tapping one opens the Pro screen.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        val layout = template.layout()
        Box {
            TemplateCanvas(
                template = template,
                layout = layout,
                background = template.background,
                photos = emptyList(),
                texts = template.texts.map { it.defaultText },
                showGuides = true,
                interactive = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp)),
            )
            if (template.premium) {
                ProBadge(stringResource(R.string.pro_chip), Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Text(template.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
        Text(
            stringResource(
                if (template.kind == TemplateKind.Carousel) R.string.template_summary_carousel else R.string.template_summary_grid,
                template.photos.size,
                layout.count,
            ) + if (locked) " · " + stringResource(R.string.locked_short) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
