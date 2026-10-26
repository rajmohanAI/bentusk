package com.bentusk.app.feature.reminder

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bentusk.app.R
import com.bentusk.app.core.data.SettingsStore
import com.bentusk.app.core.reminder.Reminders
import com.bentusk.app.core.storage.ShareHelper
import com.bentusk.engine.PostReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReminderState(
    val reminder: PostReminder? = null,
    /** How many files of a one-at-a-time reminder have been handed over. */
    val sent: Int = 0,
    val instagramConsent: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val reminders: Reminders,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ReminderState())
    val state: StateFlow<ReminderState> = _state.asStateFlow()

    fun open(id: String) {
        if (_state.value.reminder?.id == id) return
        viewModelScope.launch {
            val reminder = reminders.load(id)
            val consent = settings.settings.first().instagramConsent
            _state.value = ReminderState(reminder, 0, consent, loaded = true)
        }
    }

    /** Marks the next file (or all of them) as handed over, and forgets the reminder once nothing is left. */
    fun sent() {
        val s = _state.value
        val reminder = s.reminder ?: return
        val sent = if (reminder.oneAtATime) s.sent + 1 else reminder.uris.size
        _state.update { it.copy(sent = sent) }
        if (sent >= reminder.uris.size) viewModelScope.launch { reminders.done(reminder.id) }
    }

    /** Puts the reminder off by an hour. */
    fun snooze() {
        val reminder = _state.value.reminder ?: return
        viewModelScope.launch { reminders.schedule(reminder.copy(dueAt = System.currentTimeMillis() + SNOOZE_MS)) }
        close()
    }

    fun remove() {
        val reminder = _state.value.reminder ?: return
        viewModelScope.launch { reminders.done(reminder.id) }
        close()
    }

    fun close() = _state.update { ReminderState() }

    private companion object {
        const val SNOOZE_MS = 60 * 60 * 1000L
    }
}

/**
 * Shown when the user taps a posting reminder: hands the saved files to Instagram with the caption on the
 * clipboard, one tile at a time for a grid.
 */
@Composable
fun ReminderDialog(id: String, onClosed: () -> Unit, viewModel: ReminderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(id) { viewModel.open(id) }
    if (!state.loaded) return
    val reminder = state.reminder
    if (reminder == null) {
        // Already posted or removed from another notification.
        LaunchedEffect(Unit) {
            Toast.makeText(context, R.string.reminder_gone, Toast.LENGTH_SHORT).show()
            viewModel.close()
            onClosed()
        }
        return
    }
    val total = reminder.uris.size
    val finished = state.sent >= total
    fun close() {
        viewModel.close()
        onClosed()
    }
    fun post() {
        val uris = if (reminder.oneAtATime) listOf(reminder.uris[state.sent]) else reminder.uris
        if (reminder.caption.isNotBlank()) {
            ShareHelper.copyText(context, context.getString(R.string.caption), reminder.caption)
            Toast.makeText(context, R.string.caption_copied, Toast.LENGTH_LONG).show()
        }
        ShareHelper.shareImages(
            context,
            uris.map(Uri::parse),
            reminder.mime,
            toInstagram = state.instagramConsent && ShareHelper.isInstagramInstalled(context),
            chooserTitle = context.getString(R.string.share),
        )
        val last = !reminder.oneAtATime || state.sent + 1 >= total
        viewModel.sent()
        if (last) close()
    }

    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(if (finished) R.string.reminder_all_posted else R.string.reminder_title)) },
        text = {
            Column {
                Text(pluralStringResource(R.plurals.reminder_text, total, total))
                if (reminder.caption.isNotBlank()) {
                    Text(
                        reminder.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!finished) {
                    TextButton(onClick = viewModel::remove, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.reminder_remove))
                    }
                }
            }
        },
        confirmButton = {
            if (!finished) {
                TextButton(onClick = ::post) {
                    Text(
                        if (reminder.oneAtATime) {
                            stringResource(R.string.post_tile_to_instagram, state.sent + 1, total)
                        } else {
                            stringResource(R.string.post_to_instagram)
                        },
                    )
                }
            }
        },
        dismissButton = {
            if (finished) {
                TextButton(onClick = ::close) { Text(stringResource(R.string.done)) }
            } else if (state.sent == 0) {
                TextButton(onClick = { viewModel.snooze(); onClosed() }) { Text(stringResource(R.string.reminder_snooze)) }
            }
        },
    )
}
