package app.cheesino.core

// #EXTINF ayrıştırma — iOS PlaylistParser ile aynı mantık.
object PlaylistParser {
    data class Result(val channels: List<Channel>, val epgUrl: String?)

    private val attr = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")

    fun parse(text: String): Result {
        val lines = text.split("\n").map { it.trim() }
        var epgUrl: String? = null
        val out = ArrayList<Channel>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXTM3U")) {
                attr.findAll(line).forEach { if (it.groupValues[1].equals("url-tvg", true) || it.groupValues[1].equals("x-tvg-url", true)) epgUrl = it.groupValues[2] }
            } else if (line.startsWith("#EXTINF")) {
                val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                // İsim = tırnak dışındaki İLK virgülden sonrası → virgüllü başlıklar ("Film, 2. Bölüm")
                // ve virgüllü öznitelik değerleri bozulmaz (substringAfterLast bunları kesiyordu).
                val nameComma = nameCommaIndex(line)
                val name = (if (nameComma >= 0) line.substring(nameComma + 1) else "").trim()
                // URL'i bul — araya giren #EXTGRP/#EXTVLCOPT gibi etiketleri atla, ama sıradaki
                // #EXTINF'in URL'ini çalma (aksi halde araya etiket giren kanallar tümden düşüyordu).
                var j = i + 1
                while (j < lines.size && lines[j].startsWith("#") && !lines[j].startsWith("#EXTINF")) j++
                val url = lines.getOrNull(j)?.takeIf { it.isNotEmpty() && !it.startsWith("#") }
                if (url != null) {
                    val group = attrs["group-title"] ?: "Genel"
                    out.add(Channel(
                        id = attrs["tvg-id"]?.ifBlank { null } ?: url,
                        name = name.ifBlank { attrs["tvg-name"] ?: "Channel" },
                        logo = attrs["tvg-logo"]?.ifBlank { null },
                        group = group,
                        url = url,
                        tvgId = attrs["tvg-id"]?.ifBlank { null },
                        kind = detectKind(name, group, url)
                    ))
                    i = j
                }
            }
            i++
        }
        return Result(out, epgUrl)
    }

    /** Öznitelik/isim ayıracı: tırnak dışındaki ilk virgülün konumu (yoksa -1). */
    private fun nameCommaIndex(s: String): Int {
        var inQuotes = false
        for (idx in s.indices) {
            val c = s[idx]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return idx
        }
        return -1
    }

    private fun detectKind(name: String, group: String, url: String): MediaKind {
        val g = group.lowercase(); val u = url.lowercase()
        return when {
            u.contains("/series/") || g.contains("dizi") || g.contains("series") -> MediaKind.SERIES
            u.contains("/movie/") || g.contains("film") || g.contains("vod") || u.endsWith(".mkv") || u.endsWith(".mp4") -> MediaKind.VOD
            else -> MediaKind.LIVE
        }
    }
}
