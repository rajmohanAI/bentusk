package com.bentusk.app.feature.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bentusk.app.R
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.CaptionIdeas

/** Picks a tone and language, then shows three captions written from what the photos show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCaptionSheet(
    state: AiCaptionState,
    onDismiss: () -> Unit,
    onTone: (CaptionIdeas.Tone) -> Unit,
    onLanguage: (CaptionIdeas.Language) -> Unit,
    onEmoji: (Boolean) -> Unit,
    onAgain: () -> Unit,
    onUse: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = BentuskColors.Gold)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.write_with_ai), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onAgain, enabled = !state.busy) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.write_again))
                }
            }
            Text(
                stringResource(R.string.ai_on_phone_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            ChipLine(CaptionIdeas.Tone.entries, state.tone, { stringResource(it.label()) }, onTone)
            ChipLine(CaptionIdeas.Language.entries, state.language, { stringResource(it.label()) }, onLanguage)
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ai_emoji), modifier = Modifier.weight(1f))
                Switch(checked = state.emoji, onCheckedChange = onEmoji)
            }
            AnimatedContent(
                targetState = state.busy to state.ideas,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ideas",
            ) { (busy, ideas) ->
                if (busy) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (idea in ideas) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { onUse(idea) }
                                    .padding(16.dp),
                            ) {
                                Text(idea, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.ai_use_caption),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BentuskColors.Gold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChipLine(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (o in options) FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
    }
}

private fun CaptionIdeas.Tone.label() = when (this) {
    CaptionIdeas.Tone.Fun -> R.string.tone_fun
    CaptionIdeas.Tone.Minimal -> R.string.tone_minimal
    CaptionIdeas.Tone.Poetic -> R.string.tone_poetic
    CaptionIdeas.Tone.Business -> R.string.tone_business
}

private fun CaptionIdeas.Language.label() = when (this) {
    CaptionIdeas.Language.English -> R.string.lang_english
    CaptionIdeas.Language.Hindi -> R.string.lang_hindi
    CaptionIdeas.Language.Hinglish -> R.string.lang_hinglish
}
