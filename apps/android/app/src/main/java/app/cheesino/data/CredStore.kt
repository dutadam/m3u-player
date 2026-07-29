package app.cheesino.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cheesino.core.ProviderCredentials

/** provider kimlik bilgisi — EncryptedSharedPreferences (iOS Keychain karşılığı). */
class CredStore(context: Context) {
    private val prefs = run {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "cheesino_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(c: ProviderCredentials) = prefs.edit()
        .putString("server", c.server).putString("user", c.username).putString("pass", c.password).apply()

    fun load(): ProviderCredentials? {
        val s = prefs.getString("server", null) ?: return null
        val u = prefs.getString("user", null) ?: return null
        val p = prefs.getString("pass", null) ?: return null
        return ProviderCredentials(s, u, p)
    }

    fun clear() = prefs.edit().clear().apply()
}
