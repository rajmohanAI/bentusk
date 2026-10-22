package com.bentusk.app.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.bentusk.app.core.data.SettingsStore
import com.bentusk.engine.Plans
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PurchaseEvent {
    data object Purchased : PurchaseEvent
    data object Pending : PurchaseEvent
    data object Cancelled : PurchaseEvent
    data class Failed(val code: Int) : PurchaseEvent
}

/** Play Billing for the one-time lifetime Pro unlock. */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsStore,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _ownsPro = MutableStateFlow<Boolean?>(null)
    /** Null until Play has answered at least once. */
    val ownsPro: StateFlow<Boolean?> = _ownsPro.asStateFlow()

    private val _proDetails = MutableStateFlow<ProductDetails?>(null)
    val proDetails: StateFlow<ProductDetails?> = _proDetails.asStateFlow()

    private val _events = MutableStateFlow<PurchaseEvent?>(null)
    val events: StateFlow<PurchaseEvent?> = _events.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) {
            scope.launch { refresh() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { refresh() }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private suspend fun refresh() {
        val purchases = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build(),
        )
        if (purchases.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            handlePurchases(purchases.purchasesList)
        }
        val details = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(Plans.PRO_PRODUCT_ID)
                            .setProductType(ProductType.INAPP)
                            .build(),
                    ),
                )
                .build(),
        )
        _proDetails.value = details.productDetailsList?.firstOrNull()
    }

    /** Starts the purchase sheet. Returns false when the product is not available yet. */
    fun launchPurchase(activity: Activity): Boolean {
        val details = _proDetails.value ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun restore() = connect()

    fun consumeEvent() {
        _events.value = null
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { handlePurchases(purchases.orEmpty()) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh() }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.value = PurchaseEvent.Cancelled
            else -> _events.value = PurchaseEvent.Failed(result.responseCode)
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val pro = purchases.filter { Plans.PRO_PRODUCT_ID in it.products }
        val purchased = pro.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased != null) {
            if (!purchased.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchased.purchaseToken).build(),
                )
            }
            if (_ownsPro.value != true) _events.value = PurchaseEvent.Purchased
        } else if (pro.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
            _events.value = PurchaseEvent.Pending
        }
        val owns = purchased != null
        _ownsPro.value = owns
        settings.setProCached(owns)
    }
}
