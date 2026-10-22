package com.bentusk.app.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.core.ads.HomeBanner
import com.bentusk.app.feature.editor.SliceMode
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.Entitlement

@Composable
fun HomeScreen(
    sharedPhoto: Uri?,
    onSharedPhotoHandled: () -> Unit,
    onOpenEditor: (SliceMode, Uri) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenCovers: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val pickCarousel = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onOpenEditor(SliceMode.Carousel, it) }
    }
    val pickGrid = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onOpenEditor(SliceMode.Grid, it) }
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.weight(1f),
                )
                PlanChip(entitlement, onOpenPro)
            }
            Text(
                stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            ToolCard(
                title = stringResource(R.string.tool_carousel),
                body = stringResource(R.string.tool_carousel_body),
                colors = listOf(Color(0xFF3B2F5C), Color(0xFF8C5E7A)),
                art = { drawCarouselArt() },
                onClick = { pickCarousel.launch(imagesOnly) },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard(
                    title = stringResource(R.string.tool_grid),
                    body = stringResource(R.string.tool_grid_body),
                    colors = listOf(Color(0xFF203A43), Color(0xFF2C5364)),
                    art = { drawGridArt() },
                    onClick = { pickGrid.launch(imagesOnly) },
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
                ToolCard(
                    title = stringResource(R.string.tool_planner),
                    body = stringResource(R.string.tool_planner_body),
                    colors = listOf(Color(0xFF4A3B28), Color(0xFF8C6A3A)),
                    art = { drawPlannerArt() },
                    onClick = onOpenPlanner,
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
            }
            Spacer(Modifier.height(12.dp))
            ToolCard(
                title = stringResource(R.string.tool_covers),
                body = stringResource(R.string.tool_covers_body),
                colors = listOf(Color(0xFF2D3A2E), Color(0xFF6B8A5E)),
                art = { drawCoversArt() },
                onClick = onOpenCovers,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.home_promise),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (entitlement.showsAds) HomeBanner()
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
        colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ToolCard(
    title: String,
    body: String,
    colors: List<Color>,
    art: DrawScope.() -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (compact) 1.4f else 2.6f)) {
            Canvas(Modifier.fillMaxSize()) { art() }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = BentuskColors.Pearl)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = BentuskColors.Pearl.copy(alpha = 0.75f),
            maxLines = if (compact) 3 else 2,
        )
    }
}

private val ArtLight = Color.White.copy(alpha = 0.9f)
private val ArtSoft = Color.White.copy(alpha = 0.35f)

/** Four slides with one continuous wave running across them. */
private fun DrawScope.drawCarouselArt() {
    val n = 4
    val gap = size.width * 0.02f
    val w = (size.width - gap * (n - 1)) / n
    val h = minOf(size.height, w * 1.25f)
    val top = (size.height - h) / 2
    for (i in 0 until n) {
        drawRoundRect(ArtSoft, Offset(i * (w + gap), top), Size(w, h), CornerRadius(10f))
    }
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, top + h * 0.7f)
        cubicTo(size.width * 0.25f, top + h * 0.2f, size.width * 0.55f, top + h * 1.0f, size.width, top + h * 0.35f)
    }
    drawPath(path, ArtLight, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
    drawCircle(BentuskColors.Gold, radius = h * 0.12f, center = Offset(size.width * 0.78f, top + h * 0.28f))
}

private fun DrawScope.drawGridArt() {
    val cols = 3
    val rows = 3
    val gap = 4f
    val cellW = (size.height * 0.75f - gap * 2) / cols
    val cellH = (size.height - gap * 2) / rows
    val left = (size.width - (cellW * cols + gap * 2)) / 2
    for (r in 0 until rows) for (c in 0 until cols) {
        val color = if (r == rows - 1 && c == cols - 1) BentuskColors.Gold else ArtSoft
        drawRoundRect(color, Offset(left + c * (cellW + gap), r * (cellH + gap)), Size(cellW, cellH), CornerRadius(6f))
    }
}

private fun DrawScope.drawPlannerArt() {
    val cols = 3
    val gap = 4f
    val cellW = (size.width * 0.7f - gap * 2) / cols
    val cellH = cellW * 4f / 3f
    val left = (size.width - (cellW * cols + gap * 2)) / 2
    val rows = (size.height / (cellH + gap)).toInt().coerceAtLeast(1)
    for (r in 0 until rows) for (c in 0 until cols) {
        drawRoundRect(ArtSoft, Offset(left + c * (cellW + gap), r * (cellH + gap)), Size(cellW, cellH), CornerRadius(6f))
    }
    drawRoundRect(
        ArtLight,
        Offset(left + cellW * 0.6f, cellH * 0.5f),
        Size(cellW, cellH),
        CornerRadius(6f),
    )
}

private fun DrawScope.drawCoversArt() {
    // Four circles with half-radius gaps, sized to fit both dimensions and centred.
    val r = minOf(size.height * 0.32f, size.width / 9.5f)
    val gapX = r * 0.5f
    val first = (size.width - (8 * r + 3 * gapX)) / 2 + r
    val colors = listOf(Color(0xFFF4EFE6), BentuskColors.Gold, Color(0xFFD5DFCF), Color(0xFFF6D9D5))
    for (i in 0 until 4) {
        val cx = first + i * (2 * r + gapX)
        drawCircle(colors[i], radius = r, center = Offset(cx, size.height / 2))
        drawCircle(Color.White.copy(alpha = 0.6f), radius = r + 6f, center = Offset(cx, size.height / 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}
