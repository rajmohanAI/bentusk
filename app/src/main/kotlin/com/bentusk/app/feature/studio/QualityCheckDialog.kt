package com.bentusk.app.feature.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bentusk.app.R
import com.bentusk.engine.Design
import com.bentusk.engine.IssueKind
import com.bentusk.engine.QualityCheck
import com.bentusk.engine.QualityIssue
import com.bentusk.engine.TemplateKind
import java.util.Locale
import kotlin.math.roundToInt

/** The pre-export check: what may look wrong on Instagram, each finding a tap away from its layer. */
@Composable
fun QualityCheckDialog(
    design: Design,
    issues: List<QualityIssue>,
    onShow: (QualityIssue) -> Unit,
    onExportAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_title)) },
        text = {
            Column {
                Text(
                    pluralStringResource(R.plurals.check_summary, issues.size, issues.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(issues) { issue -> IssueRow(design, issue) { onShow(issue) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onExportAnyway) { Text(stringResource(R.string.check_export_anyway)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_fix)) } },
    )
}

@Composable
private fun IssueRow(design: Design, issue: QualityIssue, onClick: () -> Unit) {
    val (icon, title, detail) = describe(issue)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(place(design, issue.tile), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.padding(start = 8.dp).width(14.dp))
    }
}

@Composable
private fun describe(issue: QualityIssue): Triple<ImageVector, String, String> = when (issue.kind) {
    IssueKind.EmptyFrame -> Triple(
        Icons.Rounded.HideImage,
        stringResource(R.string.check_empty_frame),
        stringResource(R.string.check_empty_frame_detail),
    )
    IssueKind.SoftPhoto -> Triple(
        Icons.Rounded.BlurOn,
        stringResource(R.string.check_soft_photo),
        stringResource(R.string.check_soft_photo_detail, String.format(Locale.getDefault(), "%.1f", issue.amount)),
    )
    IssueKind.UnderIcons -> Triple(
        Icons.Rounded.VisibilityOff,
        stringResource(R.string.check_under_icons),
        stringResource(R.string.check_under_icons_detail),
    )
    IssueKind.SmallText -> Triple(
        Icons.Rounded.FormatSize,
        stringResource(R.string.check_small_text),
        stringResource(R.string.check_small_text_detail, issue.amount.roundToInt(), QualityCheck.MIN_TEXT_POINTS.roundToInt()),
    )
    IssueKind.ShortClip -> Triple(
        Icons.Rounded.Timer,
        stringResource(R.string.check_short_clip),
        stringResource(
            R.string.check_short_clip_detail,
            String.format(Locale.getDefault(), "%.1f", issue.amount / 1000),
            QualityCheck.MIN_CLIP_MS / 1000,
        ),
    )
}

/** "Slide 2" in a carousel; "Row 1, column 3" in a grid, matching what the user sees on the canvas. */
@Composable
private fun place(design: Design, tile: Int): String {
    val t = design.layout().tiles[tile]
    return if (design.kind == TemplateKind.Carousel) {
        stringResource(R.string.check_on_slide, t.index + 1)
    } else {
        stringResource(R.string.check_on_tile, t.row + 1, t.column + 1)
    }
}
