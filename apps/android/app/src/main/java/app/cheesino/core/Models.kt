package app.cheesino.core

import kotlinx.serialization.Serializable

// iOS Core paketinin Kotlin karşılığı. Paylaşılan sözleşme: docs/spec/xtream-m3u-epg.md

enum class MediaKind { LIVE, VOD, SERIES }

enum class Quality(val label: String) { UHD4K("4K"), FHD("FHD"), HD("HD"), SD("SD");
    companion object {
        fun detect(name: String): Quality? {
            val u = name.uppercase()
            return when {
                Regex("\\b(4K|UHD)\\b").containsMatchIn(u) -> UHD4K
                u.contains("FULLHD") || Regex("\\bFHD\\b").containsMatchIn(u) -> FHD
                Regex("\\bHD\\b").containsMatchIn(u) -> HD
                Regex("\\bSD\\b").containsMatchIn(u) -> SD
                else -> null
            }
        }
    }
}

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val logo: String? = null,
    val group: String,
    val url: String,
    val tvgId: String? = null,
    val kind: MediaKind = MediaKind.LIVE,
    val rating: Double? = null,
    val added: Long? = null,          // epoch seconds
    val supportsCatchup: Boolean = false
) {
    val quality: Quality? get() = Quality.detect(name)
}

@Serializable
data class SeriesRef(
    val id: Int,
    val name: String,
    val cover: String? = null,
    val genre: String? = null,
    val group: String
)

@Serializable
data class Episode(
    val id: String,
    val seriesId: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val ext: String,
    val thumb: String? = null,
    val url: String
)

data class Season(val number: Int, val episodes: List<Episode>)
data class Series(val id: String, val name: String, val cover: String?, val plot: String?,
                  val genre: String?, val seasons: List<Season>)

@Serializable
data class XtreamCredentials(val server: String, val username: String, val password: String) {
    companion object {
        /** iOS normalize ile aynı: sondaki / temizlenir, şema yoksa http:// eklenir. */
        fun normalize(raw: String): String? {
            var s = raw.trim()
            while (s.endsWith("/")) s = s.dropLast(1)
            if (!s.lowercase().startsWith("http")) s = "http://$s"
            return s.ifBlank { null }
        }
    }
}

data class EpgEntry(val channelId: String, val start: Long, val stop: Long, val title: String, val desc: String? = null) {
    val isLiveNow: Boolean get() { val n = System.currentTimeMillis() / 1000; return start <= n && n < stop }
}
