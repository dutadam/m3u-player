package app.cheesino.data

import android.content.Context
import app.cheesino.core.Channel
import app.cheesino.core.SeriesRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Diske yazılan içerik anlık görüntüsü — açılışta anında gösterilir, ağ arka planda tazeler. */
@Serializable
data class CachedContent(
    val channels: List<Channel> = emptyList(),
    val series: List<SeriesRef> = emptyList(),
    val epgSourceUrl: String? = null,
    val savedAt: Long = 0L
)

/**
 * İçerik disk önbelleği. Kanal/dizi listesi büyük olabildiği için SharedPreferences yerine
 * dosyaya JSON yazılır. Kaynağa (provider sunucu+kullanıcı / playlist) göre anahtarlanır ki
 * hesap değişince eski önbellek gösterilmesin.
 */
class ContentCache(context: Context) {
    private val dir = context.filesDir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun fileFor(key: String) = File(dir, "content_${key.hashCode()}.json")

    fun load(key: String): CachedContent? = try {
        val f = fileFor(key)
        if (f.exists()) json.decodeFromString<CachedContent>(f.readText()) else null
    } catch (e: Exception) { null }

    fun save(key: String, content: CachedContent) {
        try { fileFor(key).writeText(json.encodeToString(content)) } catch (e: Exception) { /* önbellek opsiyonel */ }
    }

    /** Tüm önbellek dosyalarını sil (çıkış/temizle). */
    fun clearAll() {
        try { dir.listFiles { f -> f.name.startsWith("content_") && f.name.endsWith(".json") }?.forEach { it.delete() } }
        catch (e: Exception) { }
    }
}
