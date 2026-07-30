package app.cheesino.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Çevrimdışı altyazı çevirisi — ML Kit on-device. Dil paketi ASLA sessizce inmez: model yoksa
 * [Result.NeedsDownload] döner ve indirme yalnız kullanıcı onayıyla (allowDownload=true) yapılır.
 * İndikten sonra çeviri tamamen offline. Sonuç yeni bir .srt dosyası.
 */
object SubtitleTranslate {

    sealed class Result {
        data class Done(val uri: Uri) : Result()
        /** Dil paketi cihazda yok — indirme kullanıcı onayı ister. */
        object NeedsDownload : Result()
        /** Kaynak==hedef ya da dil desteklenmiyor. */
        object Unsupported : Result()
        object Failed : Result()
    }

    private data class Cue(val index: Int, val timing: String, val text: String)

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

    private suspend fun modelReady(lang: String): Boolean = runCatching {
        RemoteModelManager.getInstance()
            .isModelDownloaded(TranslateRemoteModel.Builder(lang).build()).await()
    }.getOrDefault(false)

    /**
     * [srtUri] altyazısını [targetTag] diline çevirir.
     * @param allowDownload false ise ve dil paketi yoksa indirmez → [Result.NeedsDownload].
     */
    suspend fun translate(
        context: Context, srtUri: Uri, targetTag: String, allowDownload: Boolean
    ): Result = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(srtUri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext Result.Failed
        val cues = parse(raw)
        if (cues.isEmpty()) return@withContext Result.Failed

        val target = TranslateLanguage.fromLanguageTag(targetTag) ?: return@withContext Result.Unsupported
        val sample = cues.take(30).joinToString(" ") { it.text }.take(600)
        val srcCode = runCatching { LanguageIdentification.getClient().identifyLanguage(sample).await() }.getOrNull()
        val source = srcCode?.takeIf { it != "und" }?.let { TranslateLanguage.fromLanguageTag(it) }
            ?: TranslateLanguage.ENGLISH
        if (source == target) return@withContext Result.Unsupported

        // Dil paketleri cihazda mı? Yoksa yalnız onaylıysa indir.
        val ready = modelReady(source) && modelReady(target)
        if (!ready && !allowDownload) return@withContext Result.NeedsDownload

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        try {
            if (!ready) translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val translated = cues.map { c ->
                if (c.text.isBlank()) c
                else c.copy(text = runCatching { translator.translate(c.text.replace("\n", " ")).await() }.getOrDefault(c.text))
            }
            val outFile = File(context.cacheDir, "cheesino_sub_${System.currentTimeMillis()}.srt")
            outFile.writeText(build(translated))
            Result.Done(Uri.fromFile(outFile))
        } catch (e: Exception) {
            Result.Failed
        } finally {
            translator.close()
        }
    }
}
