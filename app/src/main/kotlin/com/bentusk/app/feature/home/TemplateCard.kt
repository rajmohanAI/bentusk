package com.bentusk.app.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bentusk.app.core.image.EmptySlots
import com.bentusk.app.feature.studio.StudioCanvas
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.app.ui.theme.NumericStyle
import com.bentusk.engine.Template
import com.bentusk.engine.TemplateKind
import com.bentusk.engine.toDesign
import kotlinx.coroutines.delay

/**
 * A template drawn as a finished post with sample art. Carousel cards page through their slides on their own,
 * and can be swiped, so people see the whole flow before choosing.
 */
@Composable
fun TemplateCard(
    template: Template,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    autoplayDelayMs: Long = 2800,
) {
    val design = remember(template.id) { template.toDesign() }
    val layout = remember(template.id) { design.layout() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")

    Column(
        modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(Modifier.clip(RoundedCornerShape(14.dp))) {
            if (template.kind == TemplateKind.Carousel) {
                val pager = rememberPagerState { layout.count }
                LaunchedEffect(pager) {
                    while (true) {
                        delay(autoplayDelayMs)
                        if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % layout.count)
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().aspectRatio(layout.tileSize.aspect.toFloat())) { page ->
                    StudioCanvas(
                        design = design,
                        layout = layout,
                        photos = emptyMap(),
                        region = layout.tiles[page].rect,
                        empty = EmptySlots.SampleArt,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(layout.count) { i ->
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.45f)),
                        )
                    }
                }
            } else {
                StudioCanvas(
                    design = design,
                    layout = layout,
                    photos = emptyMap(),
                    showGuides = true,
                    empty = EmptySlots.SampleArt,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (template.premium) {
                    Icon(
                        Icons.Rounded.WorkspacePremium,
                        null,
                        tint = BentuskColors.Gold,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(4.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    null,
                    tint = if (favorite) Color(0xFFFF6B81) else Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onFavorite)
                        .padding(4.dp),
                )
            }
        }
        Text(
            template.name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                template.photos.size.toString(),
                style = MaterialTheme.typography.labelMedium.merge(NumericStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Rounded.Image, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                layout.count.toString(),
                style = MaterialTheme.typography.labelMedium.merge(NumericStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                if (template.kind == TemplateKind.Carousel) Icons.Rounded.ViewCarousel else Icons.Rounded.ViewModule,
                null,
                Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
