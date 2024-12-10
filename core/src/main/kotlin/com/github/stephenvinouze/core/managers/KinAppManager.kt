package com.github.stephenvinouze.core.managers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import com.android.billingclient.api.*
import com.github.stephenvinouze.core.models.*
import kotlinx.coroutines.*

/**
 * Created by stephenvinouze on 13/03/2017.
 */
class KinAppManager(private val context: Context, private val developerPayload: String): PurchasesUpdatedListener, ConsumeResponseListener, AcknowledgePurchaseResponseListener {

    companion object {
        const val TEST_PURCHASE_PREFIX = "android.test"
        const val TEST_PURCHASE_SUCCESS = "$TEST_PURCHASE_PREFIX.purchased"
        const val TEST_PURCHASE_CANCELED = "$TEST_PURCHASE_PREFIX.canceled"
        const val TEST_PURCHASE_REFUNDED = "$TEST_PURCHASE_PREFIX.refunded"
        const val TEST_PURCHASE_UNAVAILABLE = "$TEST_PURCHASE_PREFIX.item_unavailable"

        const val INAPP_TYPE = "inapp"
        const val SUBS_TYPE = "subs"

        private const val KINAPP_REQUEST_CODE = 1001
        private const val KINAPP_RESPONSE_RESULT_OK = 0
        private const val KINAPP_RESPONSE_RESULT_CANCELLED = 1
        private const val KINAPP_RESPONSE_RESULT_ALREADY_OWNED = 7

        private const val KINAPP_API_VERSION = 3

        private const val KINAPP_INTENT = "com.android.vending.billing.InAppBillingService.BIND"
        private const val KINAPP_PACKAGE = "com.android.vending"

        private const val GET_ITEM_LIST = "ITEM_ID_LIST"

        private const val RESPONSE_CODE = "RESPONSE_CODE"
        private const val RESPONSE_ITEM_LIST = "DETAILS_LIST"
        private const val RESPONSE_BUY_INTENT = "BUY_INTENT"
        private const val RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA"
        private const val RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE"
        private const val RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST"
        private const val RESPONSE_INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN"
    }

    private var billingClient: BillingClient? = null
    private var listener: KinAppListener? = null
    /*private var billingConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            billingService = IInAppBillingService.Stub.asInterface(service)
            listener?.onBillingReady()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            billingService = null
        }
    }*/

    init {
        setupClient()
    }

    private fun setupClient() {
        val billingClient = BillingClient.newBuilder(this.context).enablePendingPurchases().setListener(this).build()
        this.billingClient = billingClient
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(p0: BillingResult) {
                listener?.onBillingReady()
            }

            override fun onBillingServiceDisconnected() {
                this@KinAppManager.billingClient = null
            }
        })
    }

    fun bind(listener: KinAppListener? = null) {
        this.listener = listener
        val billingIntent = Intent(KINAPP_INTENT)
        billingIntent.`package` = KINAPP_PACKAGE
        billingClient?.endConnection()
        billingClient = null
        setupClient()
    }

    fun unbind() {
        billingClient?.endConnection()
        billingClient = null
    }
    suspend fun fetchProductsAsync(productIds: ArrayList<String>, isSubscription: Boolean, onReady: (List<KinAppProduct>) -> Unit) {
            val params = SkuDetailsParams.newBuilder()
            params.setSkusList(productIds).setType(if (isSubscription) BillingClient.SkuType.SUBS else BillingClient.SkuType.INAPP)
            val output = billingClient?.querySkuDetailsAsync(params.build(), SkuDetailsResponseListener { billingResult, productDetails ->
                if (billingResult.responseCode == KINAPP_RESPONSE_RESULT_OK) {
                    val products = arrayListOf<KinAppProduct>()
                    productDetails?.forEach {
                        products.add(getProduct(it))
                    }
                    onReady(products)
                } else {
                    onReady(listOf())
                }
            })
    }

    fun restorePurchases(productType: KinAppProductType): List<KinAppPurchase>? {
        try {
            return retrievePurchases(mutableListOf(), productType, null)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        return null
    }

    fun purchase(activity: Activity, productId: String, productType: KinAppProductType) {
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(listOf(productId)).setType(if (productType == KinAppProductType.SUBSCRIPTION) BillingClient.SkuType.SUBS else BillingClient.SkuType.INAPP)
        billingClient?.querySkuDetailsAsync(params.build(),
                object : SkuDetailsResponseListener {
                    override fun onSkuDetailsResponse(p0: BillingResult, p1: MutableList<SkuDetails>?) {
                        if (p0.responseCode == KINAPP_RESPONSE_RESULT_OK) {
                            p1?.forEach { skuDetails ->
                                val purchaseParams = BillingFlowParams.newBuilder()
                                        .setSkuDetails(skuDetails)
                                        .build()
                                billingClient?.launchBillingFlow(activity, purchaseParams)
                            }
                        } else if (p0.responseCode == KINAPP_RESPONSE_RESULT_ALREADY_OWNED) {
                            listener?.onPurchaseFinished(KinAppPurchaseResult.ALREADY_OWNED, null)
                        }

                    }
                }
        )
    }

    fun verifyPurchase(billingResult: BillingResult, purchase: Purchase?): Boolean {
            if (billingResult.responseCode == KINAPP_RESPONSE_RESULT_OK || billingResult.responseCode == KINAPP_RESPONSE_RESULT_ALREADY_OWNED) {
                if (purchase != null) {
                    val dataSignature = purchase.signature
                    val json = purchase.originalJson
                    val purchase = getPurchase(purchase)
                    if (purchase.productId.startsWith(TEST_PURCHASE_PREFIX) ||
                            (dataSignature != null && SecurityManager.verifyPurchase(developerPayload, json, dataSignature))) {
                        listener?.onPurchaseFinished(KinAppPurchaseResult.SUCCESS, purchase)
                    } else {
                        listener?.onPurchaseFinished(KinAppPurchaseResult.INVALID_SIGNATURE, null)
                    }
                } else {
                    listener?.onPurchaseFinished(KinAppPurchaseResult.INVALID_PURCHASE, null)
                }
            } else if (billingResult.responseCode == KINAPP_RESPONSE_RESULT_CANCELLED) {
                listener?.onPurchaseFinished(KinAppPurchaseResult.CANCEL, null)
            } else {
                listener?.onPurchaseFinished(KinAppPurchaseResult.INVALID_PURCHASE, null)
            }
            return true
    }

    private fun getResult(responseBundle: Bundle?, responseExtra: String): Int? =
            responseBundle?.getInt(responseExtra)

    private fun getProduct(productData: SkuDetails): KinAppProduct {
        return KinAppProduct(
                product_id = productData.sku,
                title = productData.title,
                description = productData.description,
                price = productData.price,
                priceAmountMicros = productData.priceAmountMicros,
                priceCurrencyCode = productData.priceCurrencyCode,
                type = if(productData.type.equals(SUBS_TYPE, ignoreCase = true)) KinAppProductType.SUBSCRIPTION else KinAppProductType.INAPP)
    }

    private fun getPurchase(purchaseData: Purchase): KinAppPurchase {

        fun purchaseState(purchaseState: Int): KinAppPurchaseState {
            if (purchaseState == Purchase.PurchaseState.PURCHASED) {
                return KinAppPurchaseState.PURCHASED
            } else {
                return KinAppPurchaseState.CANCELED
            }
        }

        return KinAppPurchase(
                orderId = purchaseData.orderId,
                productId = purchaseData.sku,
                purchaseTime = purchaseData.purchaseTime,
                purchaseToken = purchaseData.purchaseToken,
                purchaseState = purchaseState(purchaseData.purchaseState),
                packageName = purchaseData.packageName,
                developerPayload = purchaseData.developerPayload,
                autoRenewing = purchaseData.isAutoRenewing
        )
    }

    private fun retrievePurchases(purchases: MutableList<KinAppPurchase>, productType: KinAppProductType, continuationToken: String?): MutableList<KinAppPurchase> {
        val purchaseResult = billingClient?.queryPurchases(productType.value)
        return purchaseResult?.purchasesList?.map {
            return@map getPurchase(it)
        }?.toMutableList() ?: mutableListOf()
    }

    interface KinAppListener {
        fun onBillingReady()
        fun onPurchaseFinished(purchaseResult: KinAppPurchaseResult, purchase: KinAppPurchase?)
    }

    override fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?) {
        val purchase = p1?.firstOrNull()
        if (purchase != null && purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
            billingClient?.acknowledgePurchase((acknowledgePurchaseParams.build()), this)
        }
        verifyPurchase(p0, purchase)
    }

    override fun onConsumeResponse(p0: BillingResult, p1: String) {

    }

    override fun onAcknowledgePurchaseResponse(p0: BillingResult) {

    }

    fun isBillingSupported(productType: KinAppProductType): Boolean {
        val billingReady = billingClient?.isReady ?: false
        if (productType == KinAppProductType.SUBSCRIPTION) {
            return billingReady && (billingClient?.isFeatureSupported("SUBSCRIPTIONS")?.responseCode == KINAPP_RESPONSE_RESULT_OK)
        }
        return billingReady
    }

}