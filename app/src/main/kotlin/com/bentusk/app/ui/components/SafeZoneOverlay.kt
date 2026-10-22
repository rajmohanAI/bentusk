package com.bentusk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bentusk.app.R
import com.bentusk.engine.NRect
import com.bentusk.engine.SafeZones
import com.bentusk.engine.ZoneKind

object SafeZoneColors {
    /** Covered by Instagram's own icons. */
    val Covered = Color(0xFFFF5A5F)

    /** Edge of the profile-grid thumbnail. */
    val Grid = Color(0xFF4FC3F7)

    /** Edge of the square view. */
    val Square = Color(0xFFFFD54F)
}

/**
 * Draws Instagram's overlays and crops onto one slide or tile shown at [topLeft] with [size] on screen.
 * [cover] adds the grid and square crops, which matter for the post's first slide and for every grid tile.
 */
fun DrawScope.drawSafeZones(topLeft: Offset, size: Size, carousel: Boolean, cover: Boolean) {
    val aspect = (size.width / size.height).toDouble()
    fun place(r: NRect) = Offset(topLeft.x + (r.left * size.width).toFloat(), topLeft.y + (r.top * size.height).toFloat()) to
        Size(((r.right - r.left) * size.width).toFloat(), ((r.bottom - r.top) * size.height).toFloat())

    val line = 1.5f * density
    if (cover) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density))
        for (zone in SafeZones.coverCrops(aspect)) {
            val (at, s) = place(zone.rect)
            // A crop that keeps the whole post has nothing to show.
            if (s.width >= size.width - 0.5f && s.height >= size.height - 0.5f) continue
            val colour = if (zone.kind == ZoneKind.GridCrop) SafeZoneColors.Grid else SafeZoneColors.Square
            drawRect(colour, at, s, style = Stroke(width = line, pathEffect = dash))
        }
    }
    for (zone in SafeZones.overlays(aspect, carousel)) {
        val (at, s) = place(zone.rect)
        val radius = CornerRadius(minOf(s.width, s.height) / 2)
        drawRoundRect(SafeZoneColors.Covered.copy(alpha = 0.35f), at, s, radius)
        drawRoundRect(SafeZoneColors.Covered, at, s, radius, style = Stroke(width = line))
    }
}

/** A one-line key for the overlay colours. */
@Composable
fun SafeZoneLegend(modifier: Modifier = Modifier, textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(SafeZoneColors.Covered, stringResource(R.string.safe_zone_icons), textColor, filled = true)
        LegendItem(SafeZoneColors.Grid, stringResource(R.string.safe_zone_grid), textColor, filled = false)
        LegendItem(SafeZoneColors.Square, stringResource(R.string.safe_zone_square), textColor, filled = false)
    }
}

@Composable
private fun LegendItem(colour: Color, label: String, textColor: Color, filled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .then(if (filled) Modifier.background(colour.copy(alpha = 0.6f)) else Modifier)
                .border(1.5.dp, colour),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor, modifier = Modifier.padding(end = 2.dp))
    }
}
