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

    /** Altyazı boyutu — PlayerView fractional text size (0.04 küçük · 0.06 orta · 0.09 büyük). */
    var subtitleScale: Float
        get() = prefs.getFloat("sub_scale", 0.06f)
        set(v) { prefs.edit().putFloat("sub_scale", v).apply() }

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
