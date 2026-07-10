package app.cheesino.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

// Xtream sunucuları int alanları bazen String döndürür → esnek okuyucular
private fun JsonElement?.asInt(): Int = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }?.toInt() ?: 0
private fun JsonElement?.asDoubleOrNull(): Double? = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
private fun JsonElement?.asLongOrNull(): Long? = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
private fun JsonElement?.asStr(): String? = (this as? JsonPrimitive)?.contentOrNull

@Serializable private class Cat(@SerialName("category_id") val id: String? = null, @SerialName("category_name") val name: String? = null)
@Serializable private class LiveRaw(
    val name: String? = null,
    @SerialName("stream_id") val streamId: JsonElement? = null,
    @SerialName("stream_icon") val icon: String? = null,
    @SerialName("epg_channel_id") val epg: String? = null,
    @SerialName("category_id") val cat: String? = null,
    @SerialName("tv_archive") val tvArchive: JsonElement? = null
)
@Serializable private class VodRaw(
    val name: String? = null,
    @SerialName("stream_id") val streamId: JsonElement? = null,
    @SerialName("stream_icon") val icon: String? = null,
    @SerialName("category_id") val cat: String? = null,
    @SerialName("container_extension") val ext: String? = null,
    val rating: JsonElement? = null,
    val added: JsonElement? = null
)
@Serializable private class SeriesRaw(
    val name: String? = null,
    @SerialName("series_id") val seriesId: JsonElement? = null,
    val cover: String? = null,
    val genre: String? = null,
    @SerialName("category_id") val cat: String? = null
)

/** player_api.php istemcisi — CORS/proxy YOK (native). */
class XtreamClient(val creds: XtreamCredentials) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private fun api(action: String, params: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder("${creds.server}/player_api.php?username=${creds.username}&password=${creds.password}")
        if (action.isNotEmpty()) sb.append("&action=$action")
        params.forEach { (k, v) -> sb.append("&$k=$v") }
        return sb.toString()
    }
    fun liveUrl(id: Int, ext: String = "m3u8") = "${creds.server}/live/${creds.username}/${creds.password}/$id.$ext"
    fun vodUrl(id: Int, ext: String) = "${creds.server}/movie/${creds.username}/${creds.password}/$id.$ext"
    fun seriesUrl(episodeId: String, ext: String) = "${creds.server}/series/${creds.username}/${creds.password}/$episodeId.$ext"
    fun timeshiftUrl(id: Int, durationMin: Int, startYmdHm: String) =
        "${creds.server}/timeshift/${creds.username}/${creds.password}/$durationMin/$startYmdHm/$id.ts"
    fun xmltvUrl() = "${creds.server}/xmltv.php?username=${creds.username}&password=${creds.password}"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }
    }

    suspend fun authenticateActive(): Boolean {
        val body = get(api(""))
        val el = json.parseToJsonElement(body)
        val status = (el as? kotlinx.serialization.json.JsonObject)?.get("user_info")
            ?.let { it as? kotlinx.serialization.json.JsonObject }?.get("status").asStr()
        return status?.lowercase() == "active"
    }

    private suspend fun <T> decodeList(url: String, deser: kotlinx.serialization.KSerializer<List<T>>): List<T> =
        try { json.decodeFromString(deser, get(url)) } catch (e: Exception) { emptyList() }

    suspend fun allLive(): List<Channel> {
        val cats = decodeList(api("get_live_categories"), kotlinx.serialization.builtins.ListSerializer(Cat.serializer()))
            .associate { (it.id ?: "") to (it.name ?: "Canlı") }
        return decodeList(api("get_live_streams"), kotlinx.serialization.builtins.ListSerializer(LiveRaw.serializer())).map { s ->
            val id = s.streamId.asInt()
            Channel(
                id = "live_$id", name = s.name ?: "Kanal", logo = s.icon?.ifBlank { null },
                group = cats[s.cat ?: ""] ?: "Canlı", url = liveUrl(id),
                tvgId = s.epg?.ifBlank { null }, kind = MediaKind.LIVE,
                supportsCatchup = (s.tvArchive.asInt()) > 0
            )
        }
    }

    suspend fun allVod(): List<Channel> {
        val cats = decodeList(api("get_vod_categories"), kotlinx.serialization.builtins.ListSerializer(Cat.serializer()))
            .associate { (it.id ?: "") to (it.name ?: "Filmler") }
        return decodeList(api("get_vod_streams"), kotlinx.serialization.builtins.ListSerializer(VodRaw.serializer())).map { s ->
            val id = s.streamId.asInt()
            Channel(
                id = "vod_$id", name = s.name ?: "Film", logo = s.icon?.ifBlank { null },
                group = cats[s.cat ?: ""] ?: "Filmler", url = vodUrl(id, s.ext ?: "mp4"),
                kind = MediaKind.VOD,
                rating = s.rating.asDoubleOrNull()?.takeIf { it > 0 },
                added = s.added.asLongOrNull()
            )
        }
    }

    suspend fun allSeries(): List<SeriesRef> {
        val cats = decodeList(api("get_series_categories"), kotlinx.serialization.builtins.ListSerializer(Cat.serializer()))
            .associate { (it.id ?: "") to (it.name ?: "Diziler") }
        return decodeList(api("get_series"), kotlinx.serialization.builtins.ListSerializer(SeriesRaw.serializer())).map { s ->
            SeriesRef(id = s.seriesId.asInt(), name = s.name ?: "Dizi", cover = s.cover?.ifBlank { null },
                genre = s.genre, group = cats[s.cat ?: ""] ?: "Diziler")
        }
    }
}
