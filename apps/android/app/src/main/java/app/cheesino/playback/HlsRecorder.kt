package app.cheesino.playback

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HLS (.m3u8) canlı yayın kaydedici — ffmpeg olmadan.
 *
 * Yöntem: medya playlist'i periyodik çekilir; yeni segmentler (URI ile tekilleştirilir) indirilir,
 * AES-128 ile şifreliyse çözülür ve tek bir `.ts` dosyasına **ardışık eklenir**. MPEG-TS segmentleri
 * birleştirilebilir olduğu için sonuç tek parça oynatılabilir kayıt olur.
 *
 * Kapsam: MPEG-TS segmentli HLS (yaygın streaming). fMP4/CMAF segmentli HLS düzgün mux gerektirir →
 * bu basit ekleme onlarda çalışmaz.
 */
object HlsRecorder {

    private data class Key(val method: String, val bytes: ByteArray?, val iv: ByteArray?)

    fun record(client: OkHttpClient, playlistUrl: String, out: File, isStopped: () -> Boolean) {
        val mediaUrl = resolveMediaPlaylist(client, playlistUrl) ?: playlistUrl
        val seen = HashSet<String>()
        out.outputStream().buffered().use { output ->
            var ended = false
            var fails = 0
            while (!isStopped() && !ended) {
                // Playlist çekilemezse (geçici ağ hatası) kaydı hemen bırakma — bir süre yeniden dene.
                val text = httpText(client, mediaUrl)
                if (text == null) {
                    if (++fails >= 20) break
                    if (!isStopped()) runCatching { Thread.sleep(1000L) }
                    continue
                }
                fails = 0
                val lines = text.lines()
                val targetDur = lines.firstOrNull { it.startsWith("#EXT-X-TARGETDURATION:") }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 6
                var seq = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
                    ?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0L
                var key: Key? = null

                for (raw in lines) {
                    if (isStopped()) break
                    val line = raw.trim()
                    when {
                        line.startsWith("#EXT-X-KEY:") -> key = parseKey(client, line, mediaUrl)
                        line.startsWith("#EXT-X-ENDLIST") -> ended = true
                        line.isNotEmpty() && !line.startsWith("#") -> {
                            val segUrl = resolve(mediaUrl, line)
                            // Yalnız başarılı indirmede "görüldü" işaretle → geçici hatada gelecek
                            // pollingde segment yeniden denenir (kayıtta boşluk kalmaz).
                            if (segUrl !in seen && writeSegment(client, segUrl, key, seq, output)) seen.add(segUrl)
                            seq++
                        }
                    }
                }
                output.flush()
                if (!ended && !isStopped())
                    runCatching { Thread.sleep((targetDur.coerceIn(1, 10) * 500L)) }  // ~yarı hedef süre
            }
        }
    }

    /** Segment indirilip yazıldıysa true; indirme başarısızsa false (yeniden denenebilsin). */
    private fun writeSegment(client: OkHttpClient, url: String, key: Key?, seq: Long, out: OutputStream): Boolean {
        val bytes = httpBytes(client, url) ?: return false
        val plain = if (key != null && key.method == "AES-128" && key.bytes != null)
            decryptAes(bytes, key.bytes, key.iv ?: ivFromSeq(seq)) else bytes
        out.write(plain)
        return true
    }

    /** Master playlist ise en yüksek bant genişlikli varyantı seçer; medya playlist ise aynen döner. */
    private fun resolveMediaPlaylist(client: OkHttpClient, url: String): String? {
        val text = httpText(client, url) ?: return null
        if (!text.contains("#EXT-X-STREAM-INF")) return url
        val lines = text.lines()
        var bestUri: String? = null
        var bestBw = -1
        var i = 0
        while (i < lines.size) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val uri = lines.getOrNull(i + 1)?.trim()
                if (!uri.isNullOrEmpty() && !uri.startsWith("#") && bw >= bestBw) { bestBw = bw; bestUri = uri }
                i += 2
            } else i++
        }
        return bestUri?.let { resolve(url, it) } ?: url
    }

    private fun parseKey(client: OkHttpClient, line: String, baseUrl: String): Key {
        val method = Regex("METHOD=([^,]+)").find(line)?.groupValues?.get(1)?.trim() ?: "NONE"
        if (method == "NONE") return Key("NONE", null, null)
        val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
        val ivHex = Regex("IV=0x([0-9A-Fa-f]+)").find(line)?.groupValues?.get(1)
        val keyBytes = uri?.let { httpBytes(client, resolve(baseUrl, it)) }
        val iv = ivHex?.let { hexToBytes(it) }
        return Key(method, keyBytes, iv)
    }

    private fun decryptAes(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return try {
            val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            c.doFinal(data)
        } catch (_: Exception) {
            // Dolgusuz (NoPadding) akışlar için blok hizalı kısmı çöz.
            runCatching {
                val aligned = data.copyOf(data.size - data.size % 16)
                val c = Cipher.getInstance("AES/CBC/NoPadding")
                c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                c.doFinal(aligned)
            }.getOrDefault(data)
        }
    }

    private fun ivFromSeq(seq: Long): ByteArray {
        val iv = ByteArray(16)
        var v = seq
        for (i in 15 downTo 0) { iv[i] = (v and 0xFF).toByte(); v = v shr 8 }
        return iv
    }

    private fun hexToBytes(hex: String): ByteArray {
        val s = if (hex.length % 2 == 1) "0$hex" else hex
        return ByteArray(s.length / 2) { ((s[it * 2].digitToInt(16) shl 4) + s[it * 2 + 1].digitToInt(16)).toByte() }
    }

    private fun resolve(base: String, ref: String): String =
        base.toHttpUrlOrNull()?.resolve(ref)?.toString() ?: ref

    private fun httpText(client: OkHttpClient, url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
    }.getOrNull()

    private fun httpBytes(client: OkHttpClient, url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
    }.getOrNull()
}
