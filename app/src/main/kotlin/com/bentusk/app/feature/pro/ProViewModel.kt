package com.bentusk.app.feature.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentusk.app.core.billing.BillingRepository
import com.bentusk.app.core.billing.PurchaseEvent
import com.bentusk.app.core.entitlement.EntitlementRepository
import com.bentusk.engine.Entitlement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billing: BillingRepository,
    entitlements: EntitlementRepository,
) : ViewModel() {

    val entitlement: StateFlow<Entitlement> = entitlements.entitlement

    /** Localised price from Play, e.g. "₹299.00". Null until Play answers. */
    val price: StateFlow<String?> = billing.proDetails
        .map { it?.oneTimePurchaseOfferDetails?.formattedPrice }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val events: StateFlow<PurchaseEvent?> = billing.events

    init {
        billing.connect()
    }

    fun buy(activity: Activity): Boolean = billing.launchPurchase(activity)

    fun restore() = billing.restore()

    fun consumeEvent() = billing.consumeEvent()
}
