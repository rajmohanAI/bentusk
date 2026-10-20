package com.bentusk.app.core.entitlement

import com.bentusk.app.core.billing.BillingRepository
import com.bentusk.app.core.data.SettingsStore
import com.bentusk.engine.Entitlement
import com.bentusk.engine.Plans
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for what the user can do: trial, free or Pro. */
@Singleton
class EntitlementRepository @Inject constructor(
    settings: SettingsStore,
    billing: BillingRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val entitlement: StateFlow<Entitlement> = combine(
        settings.settings.filter { it.trialStartMillis > 0 },
        billing.ownsPro,
    ) { s, owns ->
        Plans.resolve(
            ownsPro = owns ?: s.proCached,
            trialStartMillis = s.trialStartMillis,
            nowMillis = System.currentTimeMillis(),
            lastSeenMillis = s.lastSeenMillis,
        )
    }.stateIn(scope, SharingStarted.Eagerly, Entitlement.Trial(Plans.TRIAL_DAYS))
}
