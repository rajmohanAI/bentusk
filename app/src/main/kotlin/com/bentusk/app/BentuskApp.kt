package com.bentusk.app

import android.app.Application
import com.bentusk.app.core.billing.BillingRepository
import com.bentusk.app.core.data.SettingsStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BentuskApp : Application() {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var billing: BillingRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { settings.touch(System.currentTimeMillis()) }
        billing.connect()
    }
}
