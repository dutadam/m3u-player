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

    private fun img(path: String?, size: String): String? =
        path?.takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/$size$it" }

    /**
     * Başlığı TMDB'de arar ve zengin metadata döner (yüksek kaliteli backdrop/poster, özet, gerçek
     * puan, oyuncular, tür). [isTv] diziler için true. Bulunamazsa/anahtar boşsa null.
     */
    suspend fun info(apiKey: String, rawTitle: String, yearHint: String?, isTv: Boolean): TmdbInfo? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            val query = railKey(rawTitle)
            if (query.isBlank()) return@withContext null
            val kind = if (isTv) "tv" else "movie"
            try {
                val searchUrl = buildString {
                    append("https://api.themoviedb.org/3/search/").append(kind)
                    append("?api_key=").append(apiKey)
                    append("&language=tr-TR&query=").append(URLEncoder.encode(query, "UTF-8"))
                    yearHint?.take(4)?.takeIf { it.length == 4 }?.let {
                        append(if (isTv) "&first_air_date_year=" else "&year=").append(it)
                    }
                }
                val sBody = http.newCall(Request.Builder().url(searchUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null; r.body?.string() ?: return@withContext null
                }
                val sObj = json.parseToJsonElement(sBody) as? JsonObject ?: return@withContext null
                val first = (sObj["results"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull()
                    ?: return@withContext null
                val id = (first["id"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return@withContext null

                val detUrl = "https://api.themoviedb.org/3/$kind/$id?api_key=$apiKey&language=tr-TR&append_to_response=credits"
                val dBody = http.newCall(Request.Builder().url(detUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null; r.body?.string() ?: return@withContext null
                }
                val d = json.parseToJsonElement(dBody) as? JsonObject ?: return@withContext null
                fun str(k: String) = (d[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                val cast = ((d["credits"] as? JsonObject)?.get("cast") as? JsonArray)
                    ?.mapNotNull { (it as? JsonObject)?.get("name") as? JsonPrimitive }
                    ?.take(6)?.joinToString(", ") { it.content }?.takeIf { it.isNotBlank() }
                val genres = (d["genres"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonObject)?.get("name") as? JsonPrimitive }
                    ?.joinToString(", ") { it.content }?.takeIf { it.isNotBlank() }
                TmdbInfo(
                    backdrop = img(str("backdrop_path"), "w780"),
                    poster = img(str("poster_path"), "w500"),
                    overview = str("overview"),
                    rating = (d["vote_average"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it > 0 },
                    year = (str("release_date") ?: str("first_air_date"))?.take(4),
                    cast = cast,
                    genres = genres
                ).takeIf { it.backdrop != null || it.overview != null || it.rating != null }
            } catch (e: Exception) {
                null
            }
        }
}

/** TMDB zengin metadata. */
data class TmdbInfo(
    val backdrop: String? = null,
    val poster: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val year: String? = null,
    val cast: String? = null,
    val genres: String? = null
)
