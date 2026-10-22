package com.bentusk.app.feature.covers

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.ui.components.SectionLabel
import com.bentusk.app.ui.components.Swatch
import com.bentusk.engine.CoverLayout
import com.bentusk.engine.InstagramSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoversScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: CoversViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lockedMessage = stringResource(R.string.locked_feature)
    val goPro = stringResource(R.string.go_pro)
    LaunchedEffect(locked) {
        if (locked) {
            viewModel.consumeLocked()
            if (snackbar.showSnackbar(lockedMessage, goPro, withDismissAction = true) == SnackbarResult.ActionPerformed) onUpgrade()
        }
    }

    val palette = CoverPalettes[state.palette]
    val painter = rememberVectorPainter(CoverIcons[state.icon])
    val layout = CoverLayout(iconFraction = state.iconFraction.toDouble())

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_covers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = {
                        viewModel.export(renderCover(layout, palette, painter))
                        onExport()
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                ) { Text(stringResource(R.string.export_cover)) }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Full story, as it is saved.
                Canvas(
                    Modifier
                        .height(200.dp)
                        .aspectRatio(InstagramSpec.StorySize.aspect.toFloat())
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    drawCover(layout, palette, painter, size.width / layout.canvas.width)
                    val c = layout.circle
                    val k = size.width / layout.canvas.width
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f),
                        radius = c.width * k / 2,
                        center = Offset((c.left + c.width / 2f) * k, (c.top + c.height / 2f) * k),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                // As it appears in the highlight circle on the profile.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    ) {
                        val c = layout.circle
                        val k = size.width / c.width
                        translate(-c.left * k, -c.top * k) { drawCover(layout, palette, painter, k) }
                    }
                    Text(
                        stringResource(R.string.cover_profile_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            SectionLabel(stringResource(R.string.icon_size))
            Slider(
                value = state.iconFraction,
                onValueChange = viewModel::setIconFraction,
                valueRange = 0.25f..0.8f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionLabel(stringResource(R.string.background))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(CoverPalettes) { index, p ->
                    Swatch(
                        brush = p.brush(120f),
                        selected = index == state.palette,
                        onClick = { viewModel.selectPalette(index) },
                        locked = p.premium && !state.canUsePremium,
                    )
                }
            }

            SectionLabel(stringResource(R.string.icons_count, CoverIcons.size))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(CoverIcons) { index, icon ->
                    val selected = index == state.icon
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable { viewModel.selectIcon(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Draws the cover at scale [k] (1 = export pixels). Shared by the preview and the export, so they match. */
private fun DrawScope.drawCover(layout: CoverLayout, palette: CoverPalette, painter: VectorPainter, k: Float) {
    val canvas = layout.canvas
    drawRect(palette.brush(canvas.height * k), size = Size(canvas.width * k, canvas.height * k))
    val icon = layout.iconRect
    translate(icon.left * k, icon.top * k) {
        with(painter) {
            draw(Size(icon.width * k, icon.height * k), colorFilter = ColorFilter.tint(palette.contentColor))
        }
    }
}

/** Renders the cover offscreen at full story resolution. */
private fun renderCover(layout: CoverLayout, palette: CoverPalette, painter: VectorPainter): Bitmap {
    val size = layout.canvas
    val image = ImageBitmap(size.width, size.height)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        ComposeCanvas(image),
        Size(size.width.toFloat(), size.height.toFloat()),
    ) {
        drawCover(layout, palette, painter, 1f)
    }
    return image.asAndroidBitmap()
}
