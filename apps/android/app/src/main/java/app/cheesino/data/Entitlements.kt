package app.cheesino.data

import android.content.Context
import app.cheesino.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pro yetkilendirme durumu.
 *
 * Şimdilik yerel bir bayrak. Gerçek arka uç Google Play Billing olacak (ürün Play Console'da
 * tanımlanıp cihazda test edilebildiğinde `isPro` aynı seam'den beslenir). Hata ayıklama
 * derlemelerinde varsayılan Pro = true → geliştirirken tüm özellikler açık; sürümde false başlar.
 */
class Entitlements(context: Context) {
    private val prefs = context.getSharedPreferences("cheesino_pro", Context.MODE_PRIVATE)
    private val _isPro = MutableStateFlow(prefs.getBoolean("pro", BuildConfig.DEBUG))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun setPro(v: Boolean) {
        _isPro.value = v
        prefs.edit().putBoolean("pro", v).apply()
    }
}

/** Pro'ya kilitli özellikler — tek yerden yönetilir. */
enum class ProFeature(val title: String, val desc: String) {
    MULTI_VIEW("Çoklu Ekran", "2–6 yayını aynı anda izle"),
    EPG_GRID("Zaman Çizelgesi", "Çok kanallı rehber ızgarası"),
    MULTI_SOURCE("Sınırsız Kaynak", "Birden çok kaynağı kaydet ve geçiş yap"),
    RECORDING("Kayıt", "Canlı yayını kaydet ve sonra izle")
}
