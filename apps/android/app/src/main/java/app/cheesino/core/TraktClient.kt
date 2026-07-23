package app.cheesino.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Trakt.tv istemcisi — trend/popüler/benzer/özet/puan için. Trakt GÖRSEL barındırmaz; yalnız
 * metadata + dış ID verir. Bizde görseller kaynağın kendi afişlerinden gelir (başlık eşleşmesi),
 * bu yüzden görsel lisansı sorunu olmaz. Yalnız Client ID (public) gerekir (strings.xml).
 */
object TraktClient {
    private const val BASE = "https://api.trakt.tv"
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun get(clientId: String, url: String): String? = runCatching {
        val req = Request.Builder().url(url)
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", clientId)
            .build()
        http.newCall(req).execute().use { if (!it.isSuccessful) null else it.body?.string() }
    }.getOrNull()

    // Trend/popüler öğe sarmalı düz de olabilir ({title}) sarmalı da ({movie:{...}}/{show:{...}}).
    private fun titleOf(o: JsonObject): String? {
        val inner = (o["movie"] as? JsonObject) ?: (o["show"] as? JsonObject) ?: o
        return (inner["title"] as? JsonPrimitive)?.content
    }

    private fun idsOf(o: JsonObject): JsonObject? {
        val inner = (o["movie"] as? JsonObject) ?: (o["show"] as? JsonObject) ?: o
        return inner["ids"] as? JsonObject
    }

    private fun parseTitles(body: String?): List<String> {
        val arr = runCatching { json.parseToJsonElement(body ?: return emptyList()) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.let(::titleOf) }
    }

    /** Haftanın trendleri — izleyici sayısına göre sıralı başlıklar. */
    suspend fun trendingTitles(clientId: String, isTv: Boolean = false): List<String> =
        withContext(Dispatchers.IO) {
            if (clientId.isBlank()) return@withContext emptyList()
            parseTitles(get(clientId, "$BASE/${if (isTv) "shows" else "movies"}/trending?limit=30"))
        }

    /** Genel liste (movies/popular, movies/boxoffice, shows/popular ...) → sıralı başlıklar. */
    suspend fun listTitles(clientId: String, path: String): List<String> =
        withContext(Dispatchers.IO) {
            if (clientId.isBlank()) return@withContext emptyList()
            parseTitles(get(clientId, "$BASE/$path?limit=30"))
        }

    private fun ytKey(url: String?): String? {
        url ?: return null
        return Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
    }

    /** Başlığı arar, özet/puan/tür/yıl/fragman (+ trakt id) döner. Görsel YOK (kaynak afişi kullanılır). */
    suspend fun info(clientId: String, rawTitle: String, yearHint: String?, isTv: Boolean): TmdbInfo? =
        withContext(Dispatchers.IO) {
            if (clientId.isBlank()) return@withContext null
            val type = if (isTv) "show" else "movie"
            val q = URLEncoder.encode(railKey(rawTitle), "UTF-8")
            val sBody = get(clientId, "$BASE/search/$type?query=$q&limit=1${yearHint?.take(4)?.let { "&years=$it" } ?: ""}")
                ?: return@withContext null
            val first = (runCatching { json.parseToJsonElement(sBody) as? JsonArray }.getOrNull())
                ?.firstOrNull() as? JsonObject ?: return@withContext null
            val id = idsOf(first)?.get("trakt")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                ?: return@withContext null
            val dBody = get(clientId, "$BASE/${if (isTv) "shows" else "movies"}/$id?extended=full")
                ?: return@withContext null
            val d = runCatching { json.parseToJsonElement(dBody) as? JsonObject }.getOrNull() ?: return@withContext null
            fun str(k: String) = (d[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
            val genres = (d["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }?.takeIf { it.isNotBlank() }
            TmdbInfo(
                id = id,
                overview = str("overview"),
                rating = (d["rating"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it > 0 },
                year = str("year"),
                genres = genres,
                trailerKey = ytKey(str("trailer"))
            ).takeIf { it.overview != null || it.rating != null || it.trailerKey != null }
        }

    /** Bir başlığın Trakt "related" (benzer) önerileri → başlık listesi. */
    suspend fun relatedTitles(clientId: String, id: Int, isTv: Boolean): List<String> =
        withContext(Dispatchers.IO) {
            if (clientId.isBlank()) return@withContext emptyList()
            parseTitles(get(clientId, "$BASE/${if (isTv) "shows" else "movies"}/$id/related?limit=30"))
        }
}
