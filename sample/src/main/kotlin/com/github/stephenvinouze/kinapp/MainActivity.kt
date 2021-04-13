package com.github.stephenvinouze.kinapp

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.github.stephenvinouze.core.managers.KinAppManager
import com.github.stephenvinouze.core.models.KinAppProduct
import com.github.stephenvinouze.core.models.KinAppProductType
import com.github.stephenvinouze.core.models.KinAppPurchase
import com.github.stephenvinouze.core.models.KinAppPurchaseResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), KinAppManager.KinAppListener, View.OnClickListener {

    private val fetchProductsButton: Button by lazy { findViewById<Button>(R.id.fetch_products_button) }
    private val buyProductButton: Button by lazy { findViewById<Button>(R.id.buy_available_product_button) }
    private val consumePurchasesButton: Button by lazy { findViewById<Button>(R.id.consume_purchases_button) }
    private val restorePurchasesButton: Button by lazy { findViewById<Button>(R.id.restore_purchases_button) }

    /**
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     *
     *  You must insert your developer payload here in order to test products from your app with their product_id
     *
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
    private val billingManager = KinAppManager(this, "YOUR_DEVELOPER_PAYLOAD_HERE")
    private var products: MutableList<KinAppProduct>? = null
    private var purchases: MutableList<KinAppPurchase>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fetchProductsButton.isEnabled = false
        buyProductButton.isEnabled = false
        consumePurchasesButton.isEnabled = false
        restorePurchasesButton.isEnabled = false

        fetchProductsButton.setOnClickListener(this)
        buyProductButton.setOnClickListener(this)
        consumePurchasesButton.setOnClickListener(this)
        restorePurchasesButton.setOnClickListener(this)
    }
    
    private fun displayPurchaseDialog(title: String, content: String) {
        MaterialDialog.Builder(this)
                .title(title)
                .content(content)
                .positiveText(android.R.string.ok)
                .show()
    }



    override fun onBillingReady() {
        fetchProductsButton.isEnabled = true
        restorePurchasesButton.isEnabled = true
    }

    override fun onPurchaseFinished(purchaseResult: KinAppPurchaseResult, purchase: KinAppPurchase?) {
        when (purchaseResult) {
            KinAppPurchaseResult.SUCCESS -> {
                purchase?.let {
                    purchases?.add(purchase)
                    consumePurchasesButton.isEnabled = true
                    Toast.makeText(this, "Product successfully bought", Toast.LENGTH_LONG).show()
                }
            }
            KinAppPurchaseResult.ALREADY_OWNED -> {
                displayPurchaseDialog(
                        title = "Purchase already owned",
                        content = "You already own this item. If you need to buy it again, consider consuming it first (you may need to restore your purchases before that)"
                )
            }
            KinAppPurchaseResult.INVALID_PURCHASE -> {
                displayPurchaseDialog(
                        title = "Error while buying item",
                        content = "Purchase invalid"
                )
            }
            KinAppPurchaseResult.INVALID_SIGNATURE -> {
                displayPurchaseDialog(
                        title = "Error while buying item",
                        content = "Signature invalid"
                )
            }
            KinAppPurchaseResult.CANCEL -> {
                displayPurchaseDialog(
                        title = "Purchase canceled",
                        content = "You have canceled your purchase"
                )
            }
        }
    }

    override fun onClick(p0: View?) {

    }


}
