package app.cheesino.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Kaldığın-yerden-devam işareti. iOS SeriesResume/progress karşılığı. */
@Serializable
data class ResumeMark(
    val id: String,
    val title: String,
    val url: String,
    val poster: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val isSeries: Boolean = false
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    /** Neredeyse bitmiş (>%92) içerik "devam et" listesinden düşer. */
    val finished: Boolean get() = fraction >= 0.92f
}

/** İzleme olayı — tür afinitesi için (id + tür etiketleri + zaman). */
@Serializable
data class PlayEvent(val id: String, val genres: List<String>, val at: Long)

@Serializable
private data class Blob(
    val favorites: Set<String> = emptySet(),
    val likes: Set<String> = emptySet(),
    val dislikes: Set<String> = emptySet(),
    val resume: Map<String, ResumeMark> = emptyMap(),
    val history: List<PlayEvent> = emptyList()
)

/**
 * Cihaz-yerel kullanıcı verisi (favori/beğeni/ilerleme/geçmiş).
 * iOS'ta UserDefaults; burada tek JSON blob'lu SharedPreferences — senkron ve basit.
 */
class UserDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("cheesino_userdata", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun read(): Blob = try {
        prefs.getString("blob", null)?.let { json.decodeFromString<Blob>(it) } ?: Blob()
    } catch (e: Exception) { Blob() }

    private fun write(b: Blob) { prefs.edit().putString("blob", json.encodeToString(b)).apply() }

    fun load(): UserData = read().let { UserData(it.favorites, it.likes, it.dislikes, it.resume, it.history) }

    fun save(d: UserData) = write(Blob(d.favorites, d.likes, d.dislikes, d.resume, d.history))
}

/** Bellekteki değişmez kopya — ViewModel StateFlow'unda tutulur. */
data class UserData(
    val favorites: Set<String> = emptySet(),
    val likes: Set<String> = emptySet(),
    val dislikes: Set<String> = emptySet(),
    val resume: Map<String, ResumeMark> = emptyMap(),
    val history: List<PlayEvent> = emptyList()
)
