package com.bentusk.app.feature.export

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bentusk.app.R
import com.bentusk.app.ui.components.SectionLabel
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.app.ui.theme.NumericStyle
import com.bentusk.engine.Caption
import com.bentusk.engine.CaptionLibrary
import com.bentusk.engine.HashtagSets
import com.bentusk.engine.InstagramSpec
import com.bentusk.engine.SavedCaption
import com.bentusk.engine.SavedTags

/** What the user saved for reuse, and what they do with it. */
data class CaptionLibraryActions(
    val saveTags: (name: String) -> Unit,
    val saveCaption: (name: String) -> Unit,
    val useTags: (SavedTags) -> Unit,
    val useCaption: (SavedCaption) -> Unit,
    val removeTags: (SavedTags) -> Unit,
    val removeCaption: (SavedCaption) -> Unit,
)

/** Caption box with live Instagram limits, the user's saved captions and hashtag sets, and one-tap hashtag ideas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionComposer(
    caption: String,
    onCaptionChange: (String) -> Unit,
    onToggleHashtag: (String) -> Unit,
    onCopy: () -> Unit,
    onWriteWithAi: () -> Unit,
    keepLineBreaks: Boolean,
    onKeepLineBreaks: (Boolean) -> Unit,
    library: CaptionLibrary,
    actions: CaptionLibraryActions,
    modifier: Modifier = Modifier,
) {
    val tags = Caption.hashtags(caption)
    var naming by rememberSaveable { mutableStateOf<SaveKind?>(null) }
    var removing by remember { mutableStateOf<Any?>(null) }
    var topic by rememberSaveable { mutableStateOf(HashtagSets.Topics.first().id) }
    val tooLong = Caption.isTooLong(caption)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.caption), Modifier.weight(1f))
            Row(
                Modifier
                    .padding(end = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(BentuskColors.Gold, BentuskColors.GoldDeep)))
                    .clickable(onClick = onWriteWithAi)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.write_with_ai), color = Color.Black, style = MaterialTheme.typography.labelLarge)
            }
        }
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

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onKeepLineBreaks(!keepLineBreaks) }
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.keep_line_breaks), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.keep_line_breaks_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = keepLineBreaks, onCheckedChange = onKeepLineBreaks)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.saved_captions), Modifier.weight(1f))
            TextButton(onClick = { naming = SaveKind.Caption }, enabled = caption.isNotBlank(), modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Rounded.BookmarkAdd, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.save_caption))
            }
        }
        if (library.captions.isEmpty()) {
            Hint(stringResource(R.string.saved_captions_empty))
        } else {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (saved in library.captions) {
                    SavedChip(saved.name, onUse = { actions.useCaption(saved) }, onRemove = { removing = saved })
                }
            }
        }

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
            FilterChip(selected = topic == SAVED_TOPIC, onClick = { topic = SAVED_TOPIC }, label = { Text(stringResource(R.string.saved_tags)) })
            for (t in HashtagSets.Topics) {
                FilterChip(selected = t.id == topic, onClick = { topic = t.id }, label = { Text(t.name) })
            }
        }
        val canAdd = Caption.canAddHashtag(caption)
        if (topic == SAVED_TOPIC) {
            if (library.tags.isEmpty()) Hint(stringResource(R.string.saved_tags_empty))
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (set in library.tags) {
                    SavedChip(
                        "${set.name} · ${set.tags.size}",
                        enabled = canAdd || set.tags.all { it in tags },
                        onUse = { actions.useTags(set) },
                        onRemove = { removing = set },
                    )
                }
            }
        }
        if (topic != SAVED_TOPIC) FlowRow(
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
        TextButton(
            onClick = { naming = SaveKind.Tags },
            enabled = tags.isNotEmpty(),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(Icons.Rounded.BookmarkAdd, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.save_tags))
        }
        Text(
            stringResource(R.string.hashtag_limit_note, InstagramSpec.MAX_HASHTAGS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    naming?.let { kind ->
        NameDialog(
            title = stringResource(if (kind == SaveKind.Tags) R.string.save_tags_title else R.string.save_caption_title),
            suggestion = CaptionLibrary.defaultName(if (kind == SaveKind.Tags) tags.joinToString(" ") else caption),
            onSave = { name ->
                if (kind == SaveKind.Tags) actions.saveTags(name) else actions.saveCaption(name)
                if (kind == SaveKind.Tags) topic = SAVED_TOPIC
                naming = null
            },
            onDismiss = { naming = null },
        )
    }
    removing?.let { item ->
        val name = when (item) {
            is SavedTags -> item.name
            is SavedCaption -> item.name
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_saved_title, name)) },
            confirmButton = {
                TextButton(onClick = {
                    when (item) {
                        is SavedTags -> actions.removeTags(item)
                        is SavedCaption -> actions.removeCaption(item)
                    }
                    removing = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.not_now)) } },
        )
    }
}

private enum class SaveKind { Tags, Caption }

/** Shows the user's saved hashtag sets in place of a topic's ideas. */
private const val SAVED_TOPIC = "saved"

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** A saved caption or hashtag set: tap to use it, or the cross to remove it. */
@Composable
private fun SavedChip(label: String, onUse: () -> Unit, onRemove: () -> Unit, enabled: Boolean = true) {
    InputChip(
        selected = false,
        onClick = onUse,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        trailingIcon = {
            Icon(
                Icons.Rounded.Close,
                stringResource(R.string.remove),
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRemove),
            )
        },
    )
}

/** Asks for a name, offering one made from the content so saving can be a single tap. */
@Composable
private fun NameDialog(title: String, suggestion: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(CaptionLibrary.NAME_CHARS) },
                placeholder = { Text(suggestion, maxLines = 1) },
                label = { Text(stringResource(R.string.save_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}
