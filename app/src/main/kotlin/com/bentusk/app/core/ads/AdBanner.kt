package com.bentusk.app.core.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bentusk.app.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean

object Ads {
    private val started = AtomicBoolean(false)

    /** Starts the ads SDK on a background thread the first time an ad is needed. */
    fun ensureStarted(context: Context) {
        if (started.compareAndSet(false, true)) {
            Thread { MobileAds.initialize(context.applicationContext) }.start()
        }
    }
}

/** The app's only ad placement: a banner on the home screen. Never shown in editors or for Pro users. */
@Composable
fun HomeBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember {
        Ads.ensureStarted(context)
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth())
}
