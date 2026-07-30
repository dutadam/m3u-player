package app.cheesino.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Çevrimdışı altyazı çevirisi — ML Kit on-device. Yüklenen bir .srt'yi kullanıcının diline çevirir,
 * internet gerektirmez (dil paketi ilk kullanımda iner, sonra tamamen offline). Sonuç yeni bir .srt.
 */
object SubtitleTranslate {

    private data class Cue(val index: Int, val timing: String, val text: String)

    /** SRT metnini bloklara ayırır (index satırı opsiyonel; zamanlama "-->" içeren satırdan bulunur). */
    private fun parse(raw: String): List<Cue> {
        val out = ArrayList<Cue>()
        raw.replace("\r\n", "\n").trim().split(Regex("\n[ \t]*\n")).forEach { block ->
            val lines = block.trim().split("\n")
            val tIdx = lines.indexOfFirst { it.contains("-->") }
            if (tIdx < 0) return@forEach
            val idx = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: (out.size + 1)
            out.add(Cue(idx, lines[tIdx].trim(), lines.drop(tIdx + 1).joinToString("\n").trim()))
        }
        return out
    }

    private fun build(cues: List<Cue>): String =
        cues.joinToString("\n\n") { "${it.index}\n${it.timing}\n${it.text}" } + "\n"

    /**
     * [srtUri] altyazısını [targetTag] diline (örn. "tr", "en") çevirir; başarılıysa yeni SRT dosyasının
     * URI'sini döner. Kaynak dil otomatik algılanır; kaynak==hedef ya da desteklenmiyorsa null.
     */
    suspend fun translate(context: Context, srtUri: Uri, targetTag: String): Uri? = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(srtUri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext null
        val cues = parse(raw)
        if (cues.isEmpty()) return@withContext null

        val target = TranslateLanguage.fromLanguageTag(targetTag) ?: return@withContext null
        val sample = cues.take(30).joinToString(" ") { it.text }.take(600)
        val srcCode = runCatching { LanguageIdentification.getClient().identifyLanguage(sample).await() }.getOrNull()
        val source = srcCode?.takeIf { it != "und" }?.let { TranslateLanguage.fromLanguageTag(it) }
            ?: TranslateLanguage.ENGLISH
        if (source == target) return@withContext null

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        try {
            translator.downloadModelIfNeeded().await()   // ilk kullanımda dil paketi iner
            val translated = cues.map { c ->
                if (c.text.isBlank()) c
                else {
                    val t = runCatching { translator.translate(c.text.replace("\n", " ")).await() }.getOrDefault(c.text)
                    c.copy(text = t)
                }
            }
            val outFile = File(context.cacheDir, "cheesino_sub_${System.currentTimeMillis()}.srt")
            outFile.writeText(build(translated))
            Uri.fromFile(outFile)
        } catch (e: Exception) {
            null
        } finally {
            translator.close()
        }
    }
}
