package com.github.stephenvinouze.core.models

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.github.stephenvinouze.core.managers.KinAppManager

/**
 * Created by stephenvinouze on 10/05/2017.
 */
enum class KinAppProductType(val value: String) {
    INAPP(KinAppManager.INAPP_TYPE), SUBSCRIPTION(KinAppManager.SUBS_TYPE);

    fun billingType(): String {
        when (this) {
            INAPP -> return BillingClient.SkuType.INAPP
            SUBSCRIPTION -> return BillingClient.SkuType.SUBS
        }
    }
}