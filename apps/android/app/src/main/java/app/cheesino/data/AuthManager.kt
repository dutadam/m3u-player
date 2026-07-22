package app.cheesino.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Giriş yapmış kullanıcı (UI için). */
data class AuthUser(val uid: String, val name: String?, val email: String?, val photo: String?)

/**
 * Google ile giriş — Credential Manager (modern, TV dahil) + Firebase Auth.
 *
 * Not: sunucu istemci kimliği (`default_web_client_id`) konsolda Google sağlayıcısı açılıp
 * google-services.json yeniden eklenince google-services eklentisi tarafından üretilir.
 * O yüzden çalışma zamanında aranır — henüz yoksa giriş kibarca devre dışı kalır (build kırılmaz).
 */
class AuthManager(context: Context) {

    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableStateFlow(auth.currentUser?.toUi())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    init { auth.addAuthStateListener { fa -> _user.value = fa.currentUser?.toUi() } }

    /** Google giriş yapılandırılmış mı (web client id üretildi mi)? */
    fun isConfigured(context: Context): Boolean = webClientId(context) != null

    private fun webClientId(context: Context): String? {
        val ctx = context.applicationContext
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        return if (id != 0) ctx.getString(id) else null
    }

    /** Google hesap seçici → Firebase oturumu. Activity bağlamı gerekir. */
    suspend fun signInWithGoogle(context: Context): Result<Unit> {
        val webId = webClientId(context)
            ?: return Result.failure(IllegalStateException("Google giriş henüz yapılandırılmadı (konsolda etkinleştir)."))
        return try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(webId)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(context).getCredential(context, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            auth.signInWithCredential(firebaseCred).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() { auth.signOut() }

    fun currentUid(): String? = auth.currentUser?.uid

    private fun com.google.firebase.auth.FirebaseUser.toUi() =
        AuthUser(uid, displayName, email, photoUrl?.toString())
}
