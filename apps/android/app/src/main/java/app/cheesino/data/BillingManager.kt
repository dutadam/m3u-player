package app.cheesino.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import app.cheesino.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play Billing köprüsü — Pro'yu **tek seferlik (kalıcı) kilit açma** ürünü olarak satar.
 *
 * Akış: Play'e bağlan → ürün ayrıntısını çek (fiyat) → satın alma başlat → sonucu dinle →
 * satın almayı **onayla** (acknowledge, yoksa Play 3 gün sonra iade eder) → Entitlements.setPro(true).
 * Açılışta sahiplik sorgulanır (queryPurchases) → başka cihazda alınmış Pro geri yüklenir.
 *
 * Ürün Play Console'da `PRO_PRODUCT_ID` kimliğiyle "yönetilen ürün" olarak tanımlanmalıdır.
 * `isPro` durumunu tek kaynak olan [Entitlements] besler; UI oradan okur.
 */
class BillingManager(
    context: Context,
    private val entitlements: Entitlements
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        /** Play Console'daki yönetilen ürün kimliği (tek seferlik Pro kilit açma). */
        const val PRO_PRODUCT_ID = "cheesino_pro"
    }

    /** Ürün ayrıntısı (fiyat metni buradan). Hazır olunca dolar. */
    private val _proProduct = MutableStateFlow<ProductDetails?>(null)
    val proProduct: StateFlow<ProductDetails?> = _proProduct.asStateFlow()

    /** Kullanıcıya gösterilecek son durum/hata mesajı (null = mesaj yok). */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Play bağlantısını başlatır (hazırsa yeniden sorgular). Tekrar çağrılması güvenlidir. */
    fun start() {
        if (client.isReady) { queryProduct(); queryOwned(); return }
        runCatching { client.startConnection(this) }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            queryProduct(); queryOwned()
        }
    }

    override fun onBillingServiceDisconnected() {
        // Sonraki start()/işlem yeniden bağlanmayı dener.
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRO_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _proProduct.value = list.firstOrNull()
            }
        }
    }

    /** Satın alma ekranını açar. Ürün henüz yüklenmediyse bilgi mesajı verir. */
    fun purchase(activity: Activity) {
        val product = _proProduct.value ?: run {
            _status.value = "Ürün henüz yüklenmedi, birazdan tekrar dene."
            start()
            return
        }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product)
                    .build()
            )
        ).build()
        client.launchBillingFlow(activity, params)
    }

    /** "Satın alımları geri yükle" — sahiplik durumunu Play'den yeniden çeker. */
    fun restore() { start() }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> _status.value = null
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { _status.value = null; queryOwned() }
            else -> _status.value = "Satın alma tamamlanamadı (kod ${result.responseCode})."
        }
    }

    private fun queryOwned() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any {
                it.products.contains(PRO_PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (owned) {
                purchases.forEach { handlePurchase(it) }
            } else if (!BuildConfig.DEBUG) {
                // Sahiplik yoksa Pro'yu kapat — ama debug'da geliştirici bayrağını ezme.
                entitlements.setPro(false)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(PRO_PRODUCT_ID)) return
        entitlements.setPro(true)
        _status.value = null
        // Onaylanmamışsa onayla (yoksa Play otomatik iade eder).
        if (!purchase.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(ack) { }
        }
    }
}
