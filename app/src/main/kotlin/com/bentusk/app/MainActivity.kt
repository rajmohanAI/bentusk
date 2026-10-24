package com.bentusk.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.bentusk.app.core.reminder.Reminders
import com.bentusk.app.feature.reminder.ReminderDialog
import com.bentusk.app.navigation.BentuskNavHost
import com.bentusk.app.ui.theme.BentuskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** A photo shared into the app from another app, waiting to be opened. */
    private val sharedPhoto = mutableStateOf<Uri?>(null)

    /** The posting reminder whose notification the user tapped. */
    private val reminder = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            sharedPhoto.value = intent.sharedImage()
            reminder.value = Reminders.idFrom(intent)
        }
        setContent {
            BentuskTheme {
                BentuskNavHost(
                    sharedPhoto = sharedPhoto.value,
                    onSharedPhotoHandled = { sharedPhoto.value = null },
                )
                reminder.value?.let { id -> ReminderDialog(id, onClosed = { reminder.value = null }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.sharedImage()?.let { sharedPhoto.value = it }
        Reminders.idFrom(intent)?.let { reminder.value = it }
    }

    private fun Intent.sharedImage(): Uri? {
        if (action != Intent.ACTION_SEND || type?.startsWith("image/") != true) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
