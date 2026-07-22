package app.cheesino.data

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
}
