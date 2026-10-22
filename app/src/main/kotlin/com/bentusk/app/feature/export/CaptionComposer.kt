package com.bentusk.app.feature.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bentusk.app.R
import com.bentusk.app.ui.components.SectionLabel
import com.bentusk.app.ui.theme.NumericStyle
import com.bentusk.engine.Caption
import com.bentusk.engine.HashtagSets
import com.bentusk.engine.InstagramSpec

/** Caption box with live Instagram limits and one-tap hashtag ideas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionComposer(
    caption: String,
    onCaptionChange: (String) -> Unit,
    onToggleHashtag: (String) -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tags = Caption.hashtags(caption)
    val tooLong = Caption.isTooLong(caption)
    Column(modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.caption))
        OutlinedTextField(
            value = caption,
            onValueChange = onCaptionChange,
            placeholder = { Text(stringResource(R.string.caption_hint)) },
            minLines = 3,
            maxLines = 8,
            isError = tooLong || tags.size > InstagramSpec.MAX_HASHTAGS,
            supportingText = {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.caption_preview_hint, InstagramSpec.CAPTION_PREVIEW_CHARS),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(
                            R.string.caption_counts,
                            caption.length,
                            InstagramSpec.CAPTION_MAX_CHARS,
                            tags.size,
                            InstagramSpec.MAX_HASHTAGS,
                        ),
                        style = MaterialTheme.typography.bodySmall.merge(NumericStyle),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        var topic by rememberSaveable { mutableStateOf(HashtagSets.Topics.first().id) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.hashtag_ideas), Modifier.weight(1f))
            TextButton(onClick = onCopy, enabled = caption.isNotBlank(), modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Rounded.ContentCopy, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.copy_caption))
            }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (t in HashtagSets.Topics) {
                FilterChip(selected = t.id == topic, onClick = { topic = t.id }, label = { Text(t.name) })
            }
        }
        val canAdd = Caption.canAddHashtag(caption)
        FlowRow(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (tag in HashtagSets.Topics.first { it.id == topic }.tags) {
                val on = tag in tags
                FilterChip(
                    selected = on,
                    onClick = { onToggleHashtag(tag) },
                    enabled = on || canAdd,
                    label = { Text(tag) },
                )
            }
        }
        Text(
            stringResource(R.string.hashtag_limit_note, InstagramSpec.MAX_HASHTAGS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
