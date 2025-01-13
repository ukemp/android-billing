package de.kempmobil.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState.PENDING
import com.android.billingclient.api.Purchase.PurchaseState.PURCHASED
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
class BillingFacade(
    context: Context,
    private val billingState: BillingStateAdapter,
    private val productId: String,
    private val base64PublicKey: String,
    private val scope: CoroutineScope,
    private val isOfferPersonalized: Boolean = false
) : BillingClientStateListener, PurchasesUpdatedListener, PurchasesResponseListener {

    @Suppress("MemberVisibilityCanBePrivate")
    var fullVersionPurchase: Purchase? = null
        private set

    /**
     * Determines whether a purchase can be launched.
     */
    val isReady: Boolean
        get() {
            return if (billingClient.isReady && fullVersionDetails != null) {
                true
            } else {
                start()
                Timber.e(
                    RuntimeException(),
                    "Billing client not available, is ready: %b, product details: <%s>",
                    billingClient.isReady, fullVersionDetails
                )
                false
            }
        }

    val price: String?
        get() = fullVersionDetails?.oneTimePurchaseOfferDetails?.formattedPrice

    @Suppress("MemberVisibilityCanBePrivate")
    var purchaseListener: PurchaseListener? = null

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var fullVersionDetails: ProductDetails? = null

    private val isStarting = AtomicBoolean(false)

    private val maxRetry = TimeUnit.MINUTES.toMillis(5)

    private var retryDelay = 1000L

    @Suppress("MemberVisibilityCanBePrivate")
    fun start() {
        if (!billingClient.isReady && isStarting.compareAndSet(false, true)) {
            Timber.d("Starting billing client connection...")
            billingClient.startConnection(this)
        }
    }

    /**
     * Must be called in `Activity#onResume()`, see also
     * https://developer.android.com/google/play/billing/integrate#pending
     */
    fun onResume() {
        queryPurchases()
    }

    /**
     * Must be called in `Activity#onCreate()`, see also
     * https://developer.android.com/google/play/billing/integrate#pending
     */
    fun onCreate() {
        queryPurchases()
    }

    fun purchaseFullVersion(activity: Activity) {
        fullVersionDetails?.let { details ->
            if (billingClient.isReady) {
                val productDetails = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
                val params = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetails))
                    .setIsOfferPersonalized(isOfferPersonalized)
                    .build()
                val result = billingClient.launchBillingFlow(activity, params)

                if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    queryPurchases()
                } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Timber.e(
                        "Error launching billing flow: %s (%s)",
                        result.responseCodeString(), result.debugMessage
                    )
                }
            } else {
                Timber.e("Cannot launch purchaseFullVersion(), because client is not ready")
            }
        } ?: run {
            Timber.e("Cannot launch purchaseFullVersion(), because ProductDetails is null")
        }
    }

    /**
     * Consumes/revokes a purchased product for testing purposes.
     */
    fun consumePurchase() {
        if (billingClient.isReady) {
            fullVersionPurchase?.let { purchase ->
                scope.launch {
                    val params = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    val (billingResult, token) = withContext(Dispatchers.IO) {
                        billingClient.consumePurchase(params)
                    }
                    Timber.i(
                        "Product with token <%s> has been consumed/revoked: %s",
                        token, billingResult.responseCodeString()
                    )
                    fullVersionPurchase = null
                    billingState.state = BillingState.AD_VERSION
                    // Just to avoid missing product details:
                    queryProductDetails()
                }
            }
        } else {
            Timber.w(
                "Unable to consume purchase, client=%s, purchase=%s",
                billingClient, fullVersionPurchase
            )
        }
    }

    // -------------------------------------------------------------------------------
    // BillingClientStateListener, PurchasesUpdatedListener, PurchasesResponseListener
    // -------------------------------------------------------------------------------

    override fun onBillingSetupFinished(result: BillingResult) {
        Timber.d(
            "Billing setup finished with result: '%s' (%s)",
            result.responseCodeString(), result.debugMessage
        )
        isStarting.set(false)

        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            retryDelay = 1000L
            queryPurchases()
            if (billingState.state != BillingState.FULL_VERSION) {
                scope.launch {
                    queryProductDetails()
                }
            } else {
                Timber.i("Skipping query for product details, as this is already the FULL_VERSION")
            }
        } else {
            retryDelayed()
        }
    }

    override fun onBillingServiceDisconnected() {
        Timber.w("Billing service got disconnected!")
        retryDelayed()
    }

    override fun onQueryPurchasesResponse(result: BillingResult, purchases: List<Purchase>) {
        this.fullVersionPurchase = null

        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            for (purchase in purchases) {
                if (purchase.hasFullVersionOf(productId)) {
                    Timber.d("Found purchase '%s'", purchase)
                    checkUnacknowledgedPurchase(purchase)

                    this.fullVersionPurchase = purchase
                    break
                }
            }
        }
        billingState.state =
            if (fullVersionPurchase != null) BillingState.FULL_VERSION else BillingState.AD_VERSION
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        Timber.d(
            "Billing purchases updated with result: '%s', purchases=%s",
            result.responseCodeString(), purchases
        )

        val responseCode = result.responseCode
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases != null) {
                handlePurchases(purchases)
            } else {
                Timber.e("Received purchase updated callback with empty purchase list")
            }
        } else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryPurchases()
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Timber.i("Purchase canceled by user")
        } else if (responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
            Timber.w(
                "Error purchasing full version: %s (%s), service not available",
                result.responseCodeString(), result.debugMessage
            )
        } else if (responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR) {
            Timber.w(
                "Error purchasing full version: %s (%s), network error",
                result.responseCodeString(), result.debugMessage
            )
        } else {
            Timber.e(
                "Error purchasing full version: %s (%s), purchases=%s",
                result.responseCodeString(), result.debugMessage, purchases
            )
        }
    }

    private suspend fun queryProductDetails() {
        Timber.d("Starting query of product details now...")
        if (billingClient.isReady) {
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()

            val (billingResult, productDetailsList) = withContext(Dispatchers.IO) {
                billingClient.queryProductDetails(params)
            }
            Timber.d(
                "Product details response: %s: details=%s",
                billingResult.debugMessage, productDetailsList
            )

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                for (productDetails in productDetailsList) {
                    if (productId == productDetails.productId) {
                        fullVersionDetails = productDetails
                        Timber.d(
                            "Full version details: id=%s, title=%s, description=%s, price=%s, amount=%d",
                            productDetails.productId,
                            productDetails.title,
                            productDetails.description,
                            productDetails.oneTimePurchaseOfferDetails?.formattedPrice,
                            productDetails.oneTimePurchaseOfferDetails?.priceAmountMicros
                        )
                        break
                    }
                }
                if (fullVersionDetails == null) {
                    Timber.e(
                        "Failed to retrieve right product details from <%s>",
                        productDetailsList
                    )
                }
            } else {
                Timber.e(
                    "Failed to retrieve product details, reason=%s, list=%s, message=%s",
                    billingResult.debugMessage, productDetailsList, billingResult.debugMessage
                )
            }
        }
    }

    private fun queryPurchases() {
        if (billingClient.isReady) {
            Timber.d("Starting query purchases now...")
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            billingClient.queryPurchasesAsync(params, this)
        } else {
            Timber.d("Cannot start queryPurchasesAsync() because billing client is not ready yet")
            start()
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            Timber.d(
                "Handling purchase %s with state: %s",
                purchase.products, purchase.purchaseState
            )

            if (purchase.purchaseState == PENDING) {
                Timber.i("Purchase in state PENDING, waiting before granting entitlement")
            } else if (purchase.purchaseState == PURCHASED
                && purchase.hasFullVersionOf(productId)
                && verifyPurchase(purchase)
            ) {
                fullVersionPurchase = purchase
                billingState.state = BillingState.FULL_VERSION

                // New in billing client version 2.x must acknowledge purchase!!!
                checkUnacknowledgedPurchase(purchase)

                purchaseListener?.purchaseCompleted()
                break
            }
        }
    }

    private fun checkUnacknowledgedPurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            scope.launch {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                val ackPurchaseResult = withContext(Dispatchers.IO) {
                    billingClient.acknowledgePurchase(params)
                }

                if (ackPurchaseResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.i("Successfully acknowledged purchase")
                } else {
                    Timber.e(
                        "Unable to acknowledge purchase: %s (%s)",
                        ackPurchaseResult.responseCodeString(), ackPurchaseResult.debugMessage
                    )
                }
            }
        } else {
            Timber.d("Purchase %s already acknowledged, skipping acknowledgePurchase()", purchase)
        }
    }

    private fun verifyPurchase(purchase: Purchase): Boolean {
        val isValid = verifyPurchase(base64PublicKey, purchase.originalJson, purchase.signature)
        Timber.d("Purchase is valid=%b", isValid)
        if (!isValid) {
            Timber.e("Failed to verify purchase <%s>", purchase)
        }
        return isValid
    }

    private fun retryDelayed() {
        scope.launch {
            Timber.d("Restarting billing client after %dms...", retryDelay)
            delay(retryDelay)
            retryDelay = (2 * retryDelay).coerceAtMost(maxRetry)
            start()
        }
    }
}

private fun Purchase.hasFullVersionOf(productId: String): Boolean {
    return products.find { it == productId } != null
}

private fun BillingResult.responseCodeString(): String {
    return when (responseCode) {
        BillingClient.BillingResponseCode.OK -> "OK"
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
        BillingClient.BillingResponseCode.ERROR -> "ERROR"
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "FEATURE_NOT_SUPPORTED"
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "SERVICE_DISCONNECTED"
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "NETWORK_ERROR"
        BillingClient.BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
        else -> "UNKNOWN (${responseCode})"
    }
}
