package com.bentusk.app.feature.studio

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bentusk.app.R
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.Fill
import com.bentusk.engine.InstagramSpec
import com.bentusk.engine.Motion
import com.bentusk.engine.Motions
import com.bentusk.engine.NRect
import com.bentusk.engine.Palette
import com.bentusk.engine.PaletteMood
import com.bentusk.engine.Palettes
import com.bentusk.engine.PhotoLayer
import com.bentusk.engine.Plans
import com.bentusk.engine.PostRatio
import com.bentusk.engine.ProFeature
import com.bentusk.engine.RetroStyle
import com.bentusk.engine.StickerPlacement
import com.bentusk.engine.TemplateKind
import com.bentusk.engine.TextAlign
import com.bentusk.engine.TextFont
import com.bentusk.engine.TextLayer

/** The whole canvas: a photo there already spans every slide. */
private val FULL_CANVAS = NRect(0.0, 0.0, 1.0, 1.0)

/** Furthest the zoom slider goes; pinching can go further. */
private const val MAX_SLIDER_ZOOM = 4f

/** Colours offered for text, pills and backgrounds. */
object StudioPalette {
    val Solids = listOf(
        0xFFFFFFFF, 0xFFF4EFE6, 0xFFE9E6E1, 0xFFF6D9D5, 0xFFD5DFCF, 0xFFDCE6F2, 0xFFF3E3B5,
        0xFFE8B49A, 0xFFC96F53, 0xFF8E3B2E, 0xFF5B3E8C, 0xFF2F5A3A, 0xFF1F3B5C, 0xFF1B1C20, 0xFF000000,
    ).map { it.toInt() }

    val Gradients = listOf(
        0xFFFCE4EC to 0xFFF8BBD0,
        0xFFFFE0C2 to 0xFFE9C9E8,
        0xFFE3F1F8 to 0xFF8EC5E8,
        0xFFF6C99A to 0xFFC77E4A,
        0xFFDDEBD7 to 0xFF5E8C61,
        0xFF3A2C6B to 0xFFF39A6B,
        0xFF0F1630 to 0xFF3B3F7A,
        0xFF1B1C20 to 0xFF4A4A52,
    ).map { Fill(it.first.toInt(), it.second.toInt()) }

    val Text = listOf(
        0xFFFFFFFF, 0xFF1B1C20, 0xFFE6C17E, 0xFFC96F53, 0xFFF48FB1, 0xFF8EC5E8, 0xFF5E8C61, 0xFF5B3E8C,
    ).map { it.toInt() }
}

@Composable
fun MainToolbar(
    panel: StudioPanel,
    kind: TemplateKind,
    safeZones: Boolean,
    onSafeZones: () -> Unit,
    onPhotos: () -> Unit,
    onVideo: () -> Unit,
    onText: () -> Unit,
    onPanel: (StudioPanel) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToolButton(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.tool_photos), onClick = onPhotos)
        if (kind == TemplateKind.Carousel) ToolButton(Icons.Rounded.VideoLibrary, stringResource(R.string.tool_video), onClick = onVideo)
        ToolButton(Icons.Rounded.TextFields, stringResource(R.string.tab_text), onClick = onText)
        ToolButton(Icons.Rounded.TouchApp, stringResource(R.string.tool_stickers), panel == StudioPanel.Stickers) {
            onPanel(StudioPanel.Stickers)
        }
        // A story is one fixed 9:16 screen, so it has no slides, rows or post ratio to change.
        val story = kind == TemplateKind.Story
        if (!story) {
            ToolButton(
                if (kind == TemplateKind.Carousel) Icons.Rounded.ViewCarousel else Icons.Rounded.ViewModule,
                stringResource(if (kind == TemplateKind.Carousel) R.string.tool_slides else R.string.tool_rows),
                panel == StudioPanel.Slides,
            ) { onPanel(StudioPanel.Slides) }
        }
        ToolButton(Icons.Rounded.Layers, stringResource(R.string.tool_layers), panel == StudioPanel.Layers) { onPanel(StudioPanel.Layers) }
        if (!story) {
            ToolButton(Icons.Rounded.AspectRatio, stringResource(R.string.tool_ratio), panel == StudioPanel.Ratio) { onPanel(StudioPanel.Ratio) }
        }
        ToolButton(Icons.Rounded.Wallpaper, stringResource(R.string.tool_backgrounds), panel == StudioPanel.Background) {
            onPanel(StudioPanel.Background)
        }
        ToolButton(Icons.Rounded.CropFree, stringResource(R.string.tool_safe_zones), safeZones, onSafeZones)
    }
}

@Composable
fun PanelContent(state: StudioState, viewModel: StudioViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        when (state.panel) {
            StudioPanel.Slides -> {
                val carousel = state.design.kind == TemplateKind.Carousel
                val pro = state.allows(ProFeature.LongCarousel)
                ChipRow(
                    options = (if (carousel) StudioViewModel.SlideRange else StudioViewModel.RowRange).toList(),
                    selected = state.design.count,
                    label = { if (carousel) it.toString() else "3 × $it" },
                    locked = { carousel && !pro && it > Plans.FREE_MAX_SLIDES },
                    onSelect = viewModel::setCount,
                )
            }
            StudioPanel.Ratio -> {
                ChipRow(
                    options = PostRatio.entries.toList(),
                    selected = state.design.ratio,
                    label = { "${it.label}  ·  ${it.size.width}×${it.size.height}" },
                    onSelect = viewModel::setRatio,
                )
                Spacer(Modifier.size(8.dp))
                val png = state.allows(ProFeature.PngExport)
                ChipRow(
                    options = ExportFormat.entries.toList(),
                    selected = state.format,
                    label = { stringResource(if (it == ExportFormat.Jpeg) R.string.format_jpeg else R.string.format_png) },
                    locked = { !png && it == ExportFormat.Png },
                    onSelect = viewModel::setFormat,
                )
            }
            StudioPanel.Background -> {
                PalettePicker(state.design.palette, viewModel::applyPalette)
                Spacer(Modifier.size(12.dp))
                SwatchRow(StudioPalette.Solids.map { Fill(it) }, state.design.fill, viewModel::setFill)
                Spacer(Modifier.size(8.dp))
                SwatchRow(StudioPalette.Gradients, state.design.fill, viewModel::setFill)
            }
            StudioPanel.Layers -> LayerList(state, viewModel)
            StudioPanel.Stickers -> StickerPanel(state, viewModel)
            else -> Unit
        }
    }
}

/** A ready-made sticker: where it goes and the words it starts with. */
private data class StickerPreset(val placement: StickerPlacement, val text: Int)

private val Nudges = listOf(
    StickerPreset(StickerPlacement.SeamArrow, R.string.sticker_arrow),
    StickerPreset(StickerPlacement.SeamText, R.string.sticker_keep_swiping),
    StickerPreset(StickerPlacement.CornerPill, R.string.sticker_swipe),
    StickerPreset(StickerPlacement.SeamText, R.string.sticker_wait_for_it),
)

private val Calls = listOf(
    StickerPreset(StickerPlacement.Banner, R.string.sticker_save),
    StickerPreset(StickerPlacement.Banner, R.string.sticker_share),
    StickerPreset(StickerPlacement.Banner, R.string.sticker_follow),
    StickerPreset(StickerPlacement.Banner, R.string.sticker_comment),
    StickerPreset(StickerPlacement.Banner, R.string.sticker_link_in_bio),
)

/** Swipe nudges for the seams and calls to action for the closing slide, each added as editable text. */
@Composable
private fun StickerPanel(state: StudioState, viewModel: StudioViewModel) {
    val carousel = state.design.kind == TemplateKind.Carousel
    StickerSection(stringResource(R.string.stickers_nudges), Nudges, viewModel)
    Text(
        stringResource(if (carousel) R.string.stickers_nudges_hint else R.string.stickers_nudges_hint_grid),
        color = Color.White.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Spacer(Modifier.size(8.dp))
    StickerSection(stringResource(R.string.stickers_calls), Calls, viewModel)
}

@Composable
private fun StickerSection(title: String, presets: List<StickerPreset>, viewModel: StudioViewModel) {
    Text(
        title,
        color = Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (preset in presets) {
            val text = stringResource(preset.text)
            Text(
                text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(StudioColors.Chip)
                    .clickable { viewModel.addSticker(preset.placement, text) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LayerList(state: StudioState, viewModel: StudioViewModel) {
    val layers = state.design.layers.reversed()
    if (layers.isEmpty()) {
        Text(
            stringResource(R.string.layers_empty),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    LazyColumn(Modifier.heightIn(max = 200.dp)) {
        items(layers, key = { it.id }) { layer ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.select(layer.id) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (icon, label) = when (layer) {
                    is TextLayer -> Icons.Rounded.TextFields to layer.text.ifBlank { "…" }
                    is PhotoLayer -> when {
                        layer.video != null -> Icons.Rounded.VideoLibrary to stringResource(R.string.layer_video)
                        layer.uri == null -> Icons.Rounded.Image to stringResource(R.string.layer_empty_photo)
                        else -> Icons.Rounded.Image to stringResource(R.string.layer_photo)
                    }
                }
                Icon(icon, null, tint = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(12.dp))
                Text(label, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                SmallIcon(Icons.Rounded.FlipToFront, R.string.bring_forward) { viewModel.restack(layer.id, true) }
                SmallIcon(Icons.Rounded.FlipToBack, R.string.send_backward) { viewModel.restack(layer.id, false) }
                SmallIcon(Icons.Rounded.Delete, R.string.remove) { viewModel.deleteLayer(layer.id) }
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, label: Int, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) StudioColors.Chip else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, stringResource(label), tint = if (active) BentuskColors.Gold else Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    locked: (T) -> Boolean = { false },
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (option in options) {
            val on = option == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) BentuskColors.Gold else StudioColors.Chip)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label(option), color = if (on) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
                if (locked(option)) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Lock, null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Ready-made colour schemes, filtered by mood. Picking one recolours the background and every text layer. */
@Composable
private fun PalettePicker(applied: String?, onApply: (Palette) -> Unit) {
    var mood by rememberSaveable { mutableStateOf<PaletteMood?>(null) }
    ChipRow(
        options = listOf<PaletteMood?>(null) + PaletteMood.entries,
        selected = mood,
        label = { moodLabel(it) },
        onSelect = { mood = it },
    )
    LazyRow(
        Modifier.padding(top = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(Palettes.of(mood), key = { it.id }) { palette ->
            PaletteCard(palette, palette.id == applied) { onApply(palette) }
        }
    }
}

/** A palette as stacked colour bands, the background band largest, like a designer's swatch card. */
@Composable
private fun PaletteCard(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).clickable(onClick = onClick)) {
        Column(
            Modifier
                .height(92.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) BentuskColors.Gold else Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
        ) {
            palette.swatches.forEachIndexed { i, swatch ->
                Box(Modifier.fillMaxWidth().weight(if (i == 0) 2f else 1f).background(Color(swatch.argb)))
            }
        }
        Text(
            palette.name,
            color = if (selected) BentuskColors.Gold else Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun moodLabel(mood: PaletteMood?): String = stringResource(
    when (mood) {
        null -> R.string.palette_all
        PaletteMood.Bold -> R.string.palette_bold
        PaletteMood.Pastel -> R.string.palette_pastel
        PaletteMood.Earthy -> R.string.palette_earthy
        PaletteMood.Luxe -> R.string.palette_luxe
        PaletteMood.Fresh -> R.string.palette_fresh
        PaletteMood.Moody -> R.string.palette_moody
        PaletteMood.Retro -> R.string.palette_retro
        PaletteMood.Neon -> R.string.palette_neon
        PaletteMood.Classic -> R.string.palette_classic
    },
)

@Composable
private fun SwatchRow(fills: List<Fill>, selected: Fill, onSelect: (Fill) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (fill in fills) {
            ColorDot(Brush.verticalGradient(listOf(Color(fill.top), Color(fill.bottom))), fill == selected) { onSelect(fill) }
        }
    }
}

@Composable
private fun ColorDot(brush: Brush, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) BentuskColors.Gold else Color.White.copy(alpha = 0.3f), CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(brush)
            .clickable(onClick = onClick),
    )
}

/** Tools for the selected text: what it says, its typeface, colour, alignment and label. */
@Composable
fun TextTools(layer: TextLayer, viewModel: StudioViewModel, carousel: Boolean, palette: Palette?) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = layer.text,
                onValueChange = viewModel::setText,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BentuskColors.Gold,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = BentuskColors.Gold,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            SmallIcon(Icons.Rounded.Check, R.string.done) { viewModel.select(null) }
        }
        Spacer(Modifier.size(10.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (font in TextFont.entries) {
                val on = layer.font == font
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) BentuskColors.Gold else StudioColors.Chip)
                        .clickable { viewModel.updateText { it.copy(font = font) } }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Aa",
                        color = if (on) Color.Black else Color.White,
                        style = TextStyle(fontFamily = font.family(), fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    )
                }
            }
        }
        Spacer(Modifier.size(10.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The applied palette's colours come first, then the standard ones.
            palette?.swatches?.forEach { swatch ->
                val c = swatch.argb
                ColorDot(Brush.linearGradient(listOf(Color(c), Color(c))), layer.argb == c) { viewModel.updateText { it.copy(argb = c) } }
            }
            if (palette != null) Box(Modifier.size(width = 1.dp, height = 28.dp).background(Color.White.copy(alpha = 0.3f)))
            for (c in StudioPalette.Text) {
                ColorDot(Brush.linearGradient(listOf(Color(c), Color(c))), layer.argb == c) { viewModel.updateText { it.copy(argb = c) } }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val alignIcon = when (layer.align) {
                TextAlign.Start -> Icons.AutoMirrored.Rounded.FormatAlignLeft
                TextAlign.Center -> Icons.Rounded.FormatAlignCenter
                TextAlign.End -> Icons.AutoMirrored.Rounded.FormatAlignRight
            }
            SmallIcon(alignIcon, R.string.text_align, onClick = viewModel::cycleAlign)
            SmallIcon(Icons.Rounded.FormatBold, R.string.text_bold, layer.bold) { viewModel.updateText { it.copy(bold = !it.bold) } }
            // Moving text exports as video, which only carousels keep as a normal slide.
            if (carousel) SmallIcon(Icons.Rounded.Animation, R.string.text_motion, layer.motion != null, viewModel::cycleMotion)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (layer.pill != null) StudioColors.Chip else Color.Transparent)
                    .clickable {
                        viewModel.updateText { t ->
                            // The label takes the text colour's opposite, so the words stay readable.
                            val pill = if (StudioViewModel.luminance(t.argb) > 0.5) 0xFF1B1C20.toInt() else 0xFFFFFFFF.toInt()
                            t.copy(pill = if (t.pill == null) pill else null)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Ab",
                    color = if (layer.pill != null) BentuskColors.Gold else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .border(1.5.dp, if (layer.pill != null) BentuskColors.Gold else Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            SmallIcon(Icons.Rounded.FlipToFront, R.string.bring_forward) { viewModel.restack(layer.id, true) }
            SmallIcon(Icons.Rounded.FlipToBack, R.string.send_backward) { viewModel.restack(layer.id, false) }
            SmallIcon(Icons.Rounded.Delete, R.string.remove, onClick = viewModel::deleteSelected)
        }
        Text(
            when (layer.motion) {
                null -> stringResource(R.string.text_gesture_hint)
                Motion.Nudge -> stringResource(R.string.motion_nudge_hint, Motions.SLIDE_MS / 1000.0)
                Motion.Pulse -> stringResource(R.string.motion_pulse_hint, Motions.SLIDE_MS / 1000.0)
            },
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
}

fun TextFont.family(): FontFamily = when (this) {
    TextFont.Sans -> FontFamily.SansSerif
    TextFont.Serif -> FontFamily.Serif
    TextFont.Mono -> FontFamily.Monospace
    TextFont.Script -> FontFamily.Cursive
    TextFont.Condensed -> FontFamily.SansSerif
}

/** Tools for the selected photo. */
@Composable
fun PhotoTools(layer: PhotoLayer, viewModel: StudioViewModel, carousel: Boolean, onReplace: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolButton(
                Icons.Rounded.AddPhotoAlternate,
                stringResource(if (layer.uri == null) R.string.add_photo else R.string.replace_photo),
                onClick = onReplace,
            )
            if (layer.uri != null && !layer.free) {
                ToolButton(Icons.Rounded.Rotate90DegreesCw, stringResource(R.string.rotate), onClick = viewModel::rotateSelected)
                ToolButton(Icons.Rounded.FitScreen, stringResource(R.string.fit_whole_photo), onClick = viewModel::fitSelected)
                ToolButton(Icons.Rounded.CenterFocusStrong, stringResource(R.string.fill_frame), onClick = viewModel::fillSelected)
                if (carousel && layer.rect != FULL_CANVAS) {
                    ToolButton(Icons.Rounded.Panorama, stringResource(R.string.span_slides), onClick = viewModel::spanSelected)
                }
            }
            ToolButton(Icons.Rounded.FlipToFront, stringResource(R.string.bring_forward)) { viewModel.restack(layer.id, true) }
            ToolButton(Icons.Rounded.FlipToBack, stringResource(R.string.send_backward)) { viewModel.restack(layer.id, false) }
            if (layer.uri != null || layer.free) {
                ToolButton(Icons.Rounded.Delete, stringResource(R.string.remove), onClick = viewModel::clearSelectedPhoto)
            }
            ToolButton(Icons.Rounded.Check, stringResource(R.string.done)) { viewModel.select(null) }
        }
        if (layer.uri != null && !layer.free) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.zoom), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = layer.framing.zoom.toFloat().coerceIn(1f, MAX_SLIDER_ZOOM),
                    onValueChange = { viewModel.setZoom(it.toDouble()) },
                    valueRange = 1f..MAX_SLIDER_ZOOM,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
        }
        if (layer.uri != null && layer.video == null) RetroTools(layer, viewModel)
        Text(
            stringResource(if (layer.free) R.string.free_photo_hint else R.string.template_gesture_hint),
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** Film looks for the selected photo: a style, how strong it is, and an optional date stamp. */
@Composable
private fun RetroTools(layer: PhotoLayer, viewModel: StudioViewModel) {
    val look = layer.look
    Text(
        stringResource(R.string.retro),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
    )
    ChipRow(
        options = listOf<RetroStyle?>(null) + RetroStyle.entries,
        selected = look?.style,
        label = { retroLabel(it) },
        onSelect = viewModel::setLook,
    )
    if (look != null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.retro_strength), color = Color.White, style = MaterialTheme.typography.labelLarge)
            Slider(
                value = look.strength.toFloat(),
                onValueChange = { viewModel.setLookStrength(it.toDouble()) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            SmallIcon(Icons.Rounded.Today, R.string.date_stamp, active = look.stamp != null, onClick = viewModel::toggleStamp)
        }
    }
}

@Composable
private fun retroLabel(style: RetroStyle?): String = stringResource(
    when (style) {
        null -> R.string.retro_none
        RetroStyle.Seventies -> R.string.retro_seventies
        RetroStyle.SlideFilm -> R.string.retro_slide_film
        RetroStyle.Instant -> R.string.retro_instant
        RetroStyle.Disposable -> R.string.retro_disposable
        RetroStyle.Sepia -> R.string.retro_sepia
        RetroStyle.Noir -> R.string.retro_noir
        RetroStyle.Faded -> R.string.retro_faded
        RetroStyle.Cross -> R.string.retro_cross
        RetroStyle.HomeMovie -> R.string.retro_home_movie
    },
)

/** Tools for the selected video clip: trim, sound and framing. */
@Composable
fun VideoTools(layer: PhotoLayer, viewModel: StudioViewModel, onReplace: () -> Unit) {
    val clip = layer.video ?: return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.video_trim), color = Color.White, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "${clock(clip.startMs)} – ${clock(clip.endMs)} · ${clock(clip.lengthMs)}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (clip.durationMs > InstagramSpec.VIDEO_MIN_MS) {
            RangeSlider(
                value = clip.startMs.toFloat()..clip.endMs.toFloat(),
                onValueChange = { viewModel.setTrim(it.start.toLong(), it.endInclusive.toLong()) },
                valueRange = 0f..clip.durationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolButton(
                if (clip.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                stringResource(if (clip.muted) R.string.video_unmute else R.string.video_mute),
                clip.muted,
                viewModel::toggleMute,
            )
            ToolButton(Icons.Rounded.CenterFocusStrong, stringResource(R.string.fill_frame), onClick = viewModel::fillSelected)
            ToolButton(Icons.Rounded.VideoLibrary, stringResource(R.string.replace_video), onClick = onReplace)
            ToolButton(Icons.Rounded.Delete, stringResource(R.string.remove), onClick = viewModel::clearSelectedPhoto)
            ToolButton(Icons.Rounded.Check, stringResource(R.string.done)) { viewModel.select(null) }
        }
        Text(
            stringResource(R.string.video_hint, InstagramSpec.CAROUSEL_VIDEO_MAX_MS / 1000),
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** A time like 0:05 or 1:02.5 for clip lengths; tenths show only when they matter. */
private fun clock(ms: Long): String {
    val total = ms / 1000
    val tenths = (ms % 1000) / 100
    val base = "%d:%02d".format(total / 60, total % 60)
    return if (tenths == 0L) base else "$base.$tenths"
}
