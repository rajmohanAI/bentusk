package com.bentusk.app.feature.export

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bentusk.app.R
import com.bentusk.engine.ReminderTimes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Ready-made posting times, plus any date and time the user picks. */
@Composable
fun ReminderPicker(onPick: (LocalDateTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val now = remember { LocalDateTime.now() }
    val picks = remember(now) { ReminderTimes.quickPicks(now) }
    val tooSoon = stringResource(R.string.reminder_too_soon, ReminderTimes.MIN_LEAD_MINUTES)

    fun pickCustom() {
        DatePickerDialog(context, { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            TimePickerDialog(context, { _, hour, minute ->
                val time = date.atTime(hour, minute)
                if (ReminderTimes.isValid(time, LocalDateTime.now())) onPick(time) else Toast.makeText(context, tooSoon, Toast.LENGTH_LONG).show()
            }, 19, 0, DateFormat.is24HourFormat(context)).show()
        }, now.year, now.monthValue - 1, now.dayOfMonth).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.reminder_pick_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                for (time in picks) {
                    OutlinedButton(onClick = { onPick(time) }, modifier = Modifier.fillMaxWidth()) {
                        Text(reminderLabel(time, now.toLocalDate()))
                    }
                }
                TextButton(onClick = ::pickCustom, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reminder_pick_other))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

/** "Today, 7:00 pm", "Tomorrow, 9:00 am" or "Mon 27 Oct, 9:00 am", in the phone's own time format. */
@Composable
fun reminderLabel(time: LocalDateTime, today: LocalDate): String {
    val clock = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return when (ReminderTimes.day(time, today)) {
        ReminderTimes.Day.Today -> stringResource(R.string.reminder_today, clock)
        ReminderTimes.Day.Tomorrow -> stringResource(R.string.reminder_tomorrow, clock)
        ReminderTimes.Day.Later -> stringResource(R.string.reminder_on_day, time.format(DateTimeFormatter.ofPattern("EEE d MMM")), clock)
    }
}
