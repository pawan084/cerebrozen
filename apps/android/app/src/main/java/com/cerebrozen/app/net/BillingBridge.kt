package com.cerebrozen.app.net

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The Play Billing SDK adapter (WC-10).
 *
 * Everything that requires Play Services lives here and nothing else does:
 * [Billing] holds the rules and is unit-tested, this file holds the calls and
 * is excluded from the coverage scope for the same reason `PushKt` and
 * `CereBroMessagingService` are — it cannot run off-device.
 *
 * Its one job beyond translation is to set **`obfuscatedAccountId`** on every
 * purchase. That is what lets `services/playstore.py` refuse a purchase bought
 * for a different account; without it the server falls back to token
 * uniqueness alone, which is a weaker guarantee.
 */
object BillingBridge : Billing.Store {

    private var client: BillingClient? = null

    /** Filled by the purchases-updated listener, drained by [reconcileNow]. */
    @Volatile
    private var lastFlowPurchases: List<Billing.Purchase> = emptyList()

    /** Product details, once Play has them. Empty until configured in Console.
     *  Compose state, because whether anything is purchasable decides whether a
     *  paywall door is drawn at all — and that answer arrives asynchronously. */
    var offers: List<ProductDetails> by androidx.compose.runtime.mutableStateOf(emptyList())
        private set

    /**
     * True once Play has actually offered something purchasable.
     *
     * The paywall keys its button off this rather than off "the SDK loaded":
     * with no Play Console products configured the query succeeds and returns
     * nothing, and a button that opens an empty sheet is the dead CTA audit H1
     * removed in the first place.
     */
    val purchasable: Boolean get() = offers.isNotEmpty()

    private fun ensureClient(context: Context): BillingClient {
        client?.let { return it }
        val created = BillingClient.newBuilder(context.applicationContext)
            .enablePendingPurchases(
                // Pending purchases are real in India — cash-at-counter and
                // net-banking flows complete minutes later. Billing.reconcile
                // leaves them alone until they do.
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .setListener { _, purchases ->
                lastFlowPurchases = purchases.orEmpty().map(::toPurchase)
            }
            .build()
        client = created
        return created
    }

    private suspend fun connected(context: Context): BillingClient? {
        val billing = ensureClient(context)
        if (billing.isReady) return billing
        return suspendCancellableCoroutine { cont ->
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) {
                        cont.resume(
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) billing else null,
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }

    private fun toPurchase(p: com.android.billingclient.api.Purchase) = Billing.Purchase(
        // originalJson and signature are handed on untouched: the signature is
        // over these exact bytes (Api.verifyPlayPurchase says the same).
        originalJson = p.originalJson,
        signature = p.signature,
        purchaseToken = p.purchaseToken,
        productId = p.products.firstOrNull().orEmpty(),
        isAcknowledged = p.isAcknowledged,
        isPending = p.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PENDING,
    )

    /** Load what Play will actually sell. Silent when nothing is configured. */
    suspend fun loadOffers(context: Context) {
        val billing = connected(context) ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Billing.PRODUCTS.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val result = billing.queryProductDetails(params)
        offers = result.productDetailsList.orEmpty()
    }

    /**
     * Open Play's purchase sheet for one product.
     *
     * [accountId] is the signed-in user's id; it travels with the purchase as
     * `obfuscatedAccountId` and is what the server checks.
     */
    fun launch(activity: Activity, product: ProductDetails, accountId: String): Boolean {
        val billing = client ?: return false
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .setObfuscatedAccountId(accountId)
            .build()
        return billing.launchBillingFlow(activity, params).responseCode ==
            BillingClient.BillingResponseCode.OK
    }

    override suspend fun purchases(): List<Billing.Purchase> {
        val billing = client?.takeIf { it.isReady } ?: return lastFlowPurchases
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billing.queryPurchasesAsync(params)
        return result.purchasesList.map(::toPurchase)
    }

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        val billing = client?.takeIf { it.isReady } ?: return false
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        return billing.acknowledgePurchase(params).responseCode ==
            BillingClient.BillingResponseCode.OK
    }

    /**
     * One reconcile pass: ask Play what is owned, ask the server to honour it,
     * acknowledge what it honoured. Safe to call on every launch.
     */
    suspend fun reconcileNow(context: Context): Billing.Report {
        connected(context) ?: return Billing.Report()
        return Billing.reconcile(
            purchases = purchases(),
            verify = { purchase ->
                val error = runCatching {
                    Api.verifyPlayPurchase(purchase.originalJson, purchase.signature)
                }.exceptionOrNull()
                Billing.verdictFor(error)
            },
            acknowledge = ::acknowledge,
        )
    }
}
