package app.cheesino.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * TMDB (themoviedb.org) trend istemcisi — "Haftanın Trendleri · Top 10" için ücretsiz veri kaynağı.
 * `trending/all/week` haftanın küresel trend film/dizilerini sıralı döner; başlıkları kullanıcının
 * kendi kaynağıyla eşleştirip oynatılabilir bir Top 10 rayı kurarız. Telemetri yok; yalnız TMDB'ye
 * sorgu gider. v3 API anahtarı ücretsiz (strings.xml → tmdb_api_key).
 */
object TmdbClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Haftanın trend başlıkları — sıralı (en üstte en çok trend). Anahtar boşsa/başarısızsa boş liste. */
    suspend fun trendingTitles(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val url = "https://api.themoviedb.org/3/trending/all/week?api_key=$apiKey&language=tr-TR"
        try {
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                r.body?.string() ?: return@withContext emptyList()
            }
            val o = json.parseToJsonElement(body) as? JsonObject ?: return@withContext emptyList()
            val results = o["results"] as? JsonArray ?: return@withContext emptyList()
            results.mapNotNull { it as? JsonObject }.mapNotNull { r ->
                (r["title"] as? JsonPrimitive)?.content
                    ?: (r["name"] as? JsonPrimitive)?.content
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
