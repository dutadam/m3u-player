package app.cheesino.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Zamanlı altyazı cue'su (ms). Kendi altyazı katmanımız (motor-bağımsız) bunları çizer. */
data class SubCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * SRT/VTT ayrıştırma → zamanlı cue listesi. Kendi overlay'imiz oynatma konumuna göre aktif cue'yu
 * gösterir → anlık sync offset (rebuffer yok), çift altyazı, sürüklenebilir konum mümkün olur.
 */
object Subtitles {

    /** "HH:MM:SS,mmm" veya "MM:SS.mmm" → ms. Ayraç ',' ya da '.'. */
    private fun parseTs(s: String): Long? {
        val t = s.trim().replace(',', '.')
        val dot = t.lastIndexOf('.')
        val ms = if (dot >= 0) t.substring(dot + 1).padEnd(3, '0').take(3).toLongOrNull() ?: 0L else 0L
        val hms = (if (dot >= 0) t.substring(0, dot) else t).split(":").map { it.toLongOrNull() ?: return null }
        val secs = when (hms.size) {
            3 -> hms[0] * 3600 + hms[1] * 60 + hms[2]
            2 -> hms[0] * 60 + hms[1]
            1 -> hms[0]
            else -> return null
        }
        return secs * 1000 + ms
    }

    fun parse(raw: String): List<SubCue> {
        val out = ArrayList<SubCue>()
        raw.replace("\r\n", "\n").replace("﻿", "").trim().split(Regex("\n[ \t]*\n")).forEach { block ->
            val lines = block.trim().split("\n")
            val tLine = lines.firstOrNull { it.contains("-->") } ?: return@forEach
            val parts = tLine.split("-->")
            if (parts.size < 2) return@forEach
            val start = parseTs(parts[0]) ?: return@forEach
            // WebVTT'de zaman satırında konum bilgisi olabilir → ilk token'ı al.
            val end = parseTs(parts[1].trim().split(" ").first()) ?: return@forEach
            val text = lines.drop(lines.indexOf(tLine) + 1).joinToString("\n")
                .replace(Regex("<[^>]+>"), "").trim()   // basit HTML/etiket temizliği
            if (text.isNotEmpty()) out.add(SubCue(start, end, text))
        }
        return out.sortedBy { it.startMs }
    }

    suspend fun read(context: Context, uri: Uri): List<SubCue> = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext emptyList()
        parse(raw)
    }

    /** [posMs] anındaki aktif cue metni (offset uygulanmış). Yoksa null. */
    fun activeAt(cues: List<SubCue>, posMs: Long, offsetMs: Long): String? {
        if (cues.isEmpty()) return null
        val t = posMs - offsetMs
        return cues.firstOrNull { t in it.startMs..it.endMs }?.text
    }
}
