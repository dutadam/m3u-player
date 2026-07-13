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

/** OMDb'den gerçek puanlar (IMDb + Rotten Tomatoes + Metascore). */
data class OmdbInfo(
    val imdb: Double? = null,
    val rotten: Int? = null,     // Rotten Tomatoes %
    val meta: Int? = null,       // Metascore /100
    val year: String? = null,
    val plot: String? = null
) {
    val hasAny get() = imdb != null || rotten != null || meta != null
}

/**
 * OMDb istemcisi — başlık (+ yıl) ile film/dizi puanı sorgular.
 * Ücretsiz OMDb API anahtarı (omdbapi.com); telemetri yok, yalnız başlık gönderilir.
 */
object OmdbClient {
    private const val KEY = "3558eb32"
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val qualityRe = Regex(
        "\\b(4K|UHD|FHD|FULL ?HD|HD|SD|HEVC|H\\.?26[45]|X26[45]|2160P|1080P|720P|480P|HDR|DUAL|MULTI|" +
            "TR|EN|TR-EN|T[ÜU]RK[ÇC]E|DUBLAJ|ALTYAZI|ALT YAZI)\\b",
        RegexOption.IGNORE_CASE
    )

    private fun cleanTitle(name: String): String =
        name.replace(Regex("\\(\\d{4}\\)"), " ")
            .replace(qualityRe, " ")
            .replace(Regex("[\\[\\](){}|:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun yearIn(name: String): String? = Regex("\\((\\d{4})\\)").find(name)?.groupValues?.get(1)

    /** Başlık normalleştirilir, yıl ipucu varsa kullanılır. Bulunamazsa null. */
    suspend fun ratings(rawTitle: String, yearHint: String? = null): OmdbInfo? = withContext(Dispatchers.IO) {
        val title = cleanTitle(rawTitle)
        if (title.isBlank()) return@withContext null
        val year = yearHint?.take(4)?.takeIf { it.length == 4 } ?: yearIn(rawTitle)
        val url = buildString {
            append("https://www.omdbapi.com/?apikey=").append(KEY)
            append("&t=").append(URLEncoder.encode(title, "UTF-8"))
            if (!year.isNullOrBlank()) append("&y=").append(year)
        }
        try {
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.string() ?: return@withContext null
            }
            val o = json.parseToJsonElement(body) as? JsonObject ?: return@withContext null
            if ((o["Response"] as? JsonPrimitive)?.content.equals("False", true)) return@withContext null
            fun str(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "N/A" }
            val imdb = str("imdbRating")?.toDoubleOrNull()?.takeIf { it > 0 }
            val rotten = (o["Ratings"] as? JsonArray)?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { (it["Source"] as? JsonPrimitive)?.content == "Rotten Tomatoes" }
                ?.let { (it["Value"] as? JsonPrimitive)?.content?.removeSuffix("%")?.trim()?.toIntOrNull() }
            val meta = str("Metascore")?.toIntOrNull()
            OmdbInfo(imdb, rotten, meta, str("Year"), str("Plot"))
                .takeIf { it.hasAny || it.plot != null }
        } catch (e: Exception) { null }
    }
}
