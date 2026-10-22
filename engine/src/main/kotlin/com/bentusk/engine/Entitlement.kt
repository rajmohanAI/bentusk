package com.bentusk.engine

import java.util.concurrent.TimeUnit

/** Features that only Pro unlocks once the trial ends. Core tools stay free forever. */
enum class ProFeature {
    /** Carousels longer than [Plans.FREE_MAX_SLIDES] slides. */
    LongCarousel,
    /** Lossless PNG export. */
    PngExport,
    /** Borders and gaps on tiles. */
    TileFrames,
    /** Gradient backgrounds and the premium palettes on highlight covers. */
    PremiumCovers,
    /** More than one saved grid plan. */
    MultiplePlans,
    /** Templates marked premium in the catalogue. */
    PremiumTemplates,
    /** No ads anywhere. */
    AdFree,
}

sealed interface Entitlement {
    /** All features unlocked while the free trial runs. */
    data class Trial(val daysLeft: Int) : Entitlement
    /** Trial over and Pro not bought: core tools only, with ads. */
    data object Free : Entitlement
    /** Lifetime Pro bought. */
    data object Pro : Entitlement

    fun allows(feature: ProFeature): Boolean = when (this) {
        is Trial -> true
        Pro -> true
        Free -> false
    }

    val showsAds: Boolean get() = !allows(ProFeature.AdFree)
}

object Plans {
    const val TRIAL_DAYS = 90
    const val FREE_MAX_SLIDES = 10
    const val PRO_PRODUCT_ID = "bentusk_pro_lifetime"

    /**
     * @param trialStartMillis when the trial began (first launch).
     * @param nowMillis current wall clock.
     * @param lastSeenMillis latest time the app has observed; guards against winding the clock back.
     */
    fun resolve(
        ownsPro: Boolean,
        trialStartMillis: Long,
        nowMillis: Long,
        lastSeenMillis: Long = nowMillis,
    ): Entitlement {
        if (ownsPro) return Entitlement.Pro
        val effectiveNow = maxOf(nowMillis, lastSeenMillis)
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(maxOf(0L, effectiveNow - trialStartMillis))
        val left = TRIAL_DAYS - elapsedDays
        return if (left > 0) Entitlement.Trial(left.toInt()) else Entitlement.Free
    }
}
