package app.cheesino.data

import android.content.Context
import java.security.MessageDigest

/** Uygulama ayarları — User-Agent + parental PIN (SHA-256). iOS AppSettings karşılığı. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("cheesino_settings", Context.MODE_PRIVATE)

    var userAgent: String
        get() = prefs.getString("ua", DEFAULT_UA) ?: DEFAULT_UA
        set(v) { prefs.edit().putString("ua", v.ifBlank { DEFAULT_UA }).apply() }

    var parentalEnabled: Boolean
        get() = prefs.getBoolean("parental", false)
        set(v) { prefs.edit().putBoolean("parental", v).apply() }

    /** Oynatıcı motoru: 0 = Otomatik (ExoPlayer, hata olursa VLC'ye düş), 1 = ExoPlayer, 2 = VLC. */
    var playerEngine: Int
        get() = prefs.getInt("player_engine", 0)
        set(v) { prefs.edit().putInt("player_engine", v.coerceIn(0, 2)).apply() }

    /** Altyazı boyutu — PlayerView fractional text size (0.04 küçük · 0.06 orta · 0.09 büyük). */
    var subtitleScale: Float
        get() = prefs.getFloat("sub_scale", 0.06f)
        set(v) { prefs.edit().putFloat("sub_scale", v).apply() }

    /** Altyazı yazı rengi (ARGB). Varsayılan beyaz. */
    var subtitleColor: Int
        get() = prefs.getInt("sub_color", 0xFFFFFFFF.toInt())
        set(v) { prefs.edit().putInt("sub_color", v).apply() }

    /** Altyazı arka plan rengi (ARGB). Varsayılan saydam. */
    var subtitleBg: Int
        get() = prefs.getInt("sub_bg", 0x00000000)
        set(v) { prefs.edit().putInt("sub_bg", v).apply() }

    /** Altyazının alttan yükseltilme miktarı (dp) — kullanıcı sürükleyerek ayarlar. */
    var subtitleRaiseDp: Int
        get() = prefs.getInt("sub_raise", 0)
        set(v) { prefs.edit().putInt("sub_raise", v.coerceIn(0, 480)).apply() }

    private var pinHash: String?
        get() = prefs.getString("pin", null)
        set(v) { prefs.edit().putString("pin", v).apply() }

    val hasPin: Boolean get() = pinHash != null

    /** PIN belirle → parental kilidi otomatik açılır. */
    fun setPin(pin: String) { pinHash = sha256(pin); parentalEnabled = true }
    fun verifyPin(pin: String): Boolean = pinHash?.let { it == sha256(pin) } ?: false
    fun clearPin() { pinHash = null; parentalEnabled = false }

    companion object {
        const val DEFAULT_UA = "cheesino/1.0 (Android)"
        fun sha256(s: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
