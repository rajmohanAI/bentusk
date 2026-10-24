package com.bentusk.app.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bentusk.app.MainActivity
import com.bentusk.app.R
import com.bentusk.engine.PostReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posting reminders: each is a small JSON file, and WorkManager wakes the app at its time to show a notification.
 * The timing is inexact (usually within minutes), which suits a reminder and needs no exact-alarm permission.
 */
@Singleton
class Reminders @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File get() = File(context.filesDir, "reminders").apply { mkdirs() }

    suspend fun schedule(reminder: PostReminder) = withContext(Dispatchers.IO) {
        File(dir, "${reminder.id}.json").writeText(PostReminder.toJson(reminder))
        val delay = (reminder.dueAt - System.currentTimeMillis()).coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<PostReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_ID to reminder.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(reminder.id), ExistingWorkPolicy.REPLACE, work)
    }

    suspend fun load(id: String): PostReminder? = withContext(Dispatchers.IO) {
        File(dir, "$id.json").takeIf { it.exists() }?.readText()?.let(PostReminder::fromJson)
    }

    /** Forgets a reminder once it has been dealt with, and drops its notification and pending work. */
    suspend fun done(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    /** Whether a notification can be shown; Android 13 and later ask the user first. */
    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val KEY_ID = "reminder_id"
        const val CHANNEL = "post_reminders"

        private fun workName(id: String) = "reminder-$id"

        /** The reminder an intent from a reminder notification points at, if any. */
        fun idFrom(intent: Intent?): String? = intent?.getStringExtra(KEY_ID)
    }
}

/** Shows the notification for one reminder when its time comes. */
class PostReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(Reminders.KEY_ID) ?: return Result.success()
        val reminder = Reminders(applicationContext).load(id) ?: return Result.success()
        notify(reminder)
        return Result.success()
    }

    private fun notify(reminder: PostReminder) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(Reminders.CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra(Reminders.KEY_ID, reminder.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val count = reminder.uris.size
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.resources.getQuantityString(R.plurals.reminder_text, count, count))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    reminder.caption.lineSequence().firstOrNull { it.isNotBlank() }
                        ?: context.resources.getQuantityString(R.plurals.reminder_text, count, count),
                ),
            )
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
    }
}
