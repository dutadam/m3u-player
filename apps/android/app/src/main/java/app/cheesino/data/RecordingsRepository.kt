package app.cheesino.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.cheesino.playback.ActiveRecording
import app.cheesino.playback.RecordingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Canlı kayıt UI köprüsü — [RecordingService]'i başlatır/durdurur, aktif kaydı ve diskteki
 * kayıt dosyalarını akış olarak verir.
 */
class RecordingsRepository(context: Context) {
    private val appCtx = context.applicationContext

    /** Şu an kaydedilen yayın (yoksa null). */
    val active: StateFlow<ActiveRecording?> = RecordingService.active

    /** Son kayıt durum/hata mesajı. */
    val status: StateFlow<String?> = RecordingService.status

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val recordings: StateFlow<List<File>> = _files.asStateFlow()

    init { refresh() }

    fun refresh() {
        _files.value = RecordingService.dir(appCtx).listFiles()
            ?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun start(url: String, title: String) {
        val i = Intent(appCtx, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_URL, url)
            .putExtra(RecordingService.EXTRA_TITLE, title)
        ContextCompat.startForegroundService(appCtx, i)
    }

    fun stop() {
        appCtx.startService(
            Intent(appCtx, RecordingService::class.java).setAction(RecordingService.ACTION_STOP)
        )
    }

    fun delete(file: File) { runCatching { file.delete() }; refresh() }
}
