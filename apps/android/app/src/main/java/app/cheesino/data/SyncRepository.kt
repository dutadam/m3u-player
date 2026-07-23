package app.cheesino.data

import app.cheesino.core.XtreamCredentials
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Buluttan çekilen senkron kümeleri. */
data class RemoteSets(val favorites: Set<String>, val likes: Set<String>, val dislikes: Set<String>)

/**
 * Cihazlar arası senkron — Firestore `users/{uid}` belgesinde favori/beğeni/beğenmeme.
 * Video BURADAN GEÇMEZ; yalnız küçük JSON (mimari: ince backend). Şifre/kaynak saklanmaz.
 */
class SyncRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private fun doc(uid: String) = db.collection("users").document(uid)

    suspend fun push(uid: String, data: UserData): Result<Unit> = try {
        doc(uid).set(
            mapOf(
                "favorites" to data.favorites.toList(),
                "likes" to data.likes.toList(),
                "dislikes" to data.dislikes.toList(),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun pull(uid: String): RemoteSets? = try {
        val snap = doc(uid).get().await()
        if (!snap.exists()) null
        else RemoteSets(
            strSet(snap.get("favorites")),
            strSet(snap.get("likes")),
            strSet(snap.get("dislikes"))
        )
    } catch (e: Exception) {
        null
    }

    private fun strSet(v: Any?): Set<String> =
        (v as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()

    /**
     * Kullanıcının KENDİ Xtream kaynağını hesabına yazar (cihazlar arası otomatik gelsin diye).
     * Yalnız kendi uid belgesine yazılır; Firestore güvenlik kuralları başkasının okumasını engeller.
     */
    suspend fun pushSource(uid: String, c: XtreamCredentials): Result<Unit> = try {
        doc(uid).set(
            mapOf("source" to mapOf("server" to c.server, "user" to c.username, "pass" to c.password)),
            SetOptions.merge()
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Hesaba kayıtlı Xtream kaynağını getirir (yeni cihazda otomatik yükleme için). */
    suspend fun pullSource(uid: String): XtreamCredentials? = try {
        val m = doc(uid).get().await().get("source") as? Map<*, *> ?: return null
        val s = m["server"] as? String ?: return null
        val u = m["user"] as? String ?: return null
        val p = m["pass"] as? String ?: return null
        XtreamCredentials(s, u, p)
    } catch (e: Exception) {
        null
    }
}
