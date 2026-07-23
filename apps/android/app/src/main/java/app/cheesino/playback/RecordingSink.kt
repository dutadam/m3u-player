package app.cheesino.playback

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File

/**
 * İzlenen canlı akışın baytlarını diske yazar — ayrı bir bağlantı AÇMADAN. [TeeDataSource] oynatıcının
 * (PlaybackService/ExoPlayer) okuduğu segment/stream baytlarını buraya kopyalar. Böylece sağlayıcı tek
 * eşzamanlı bağlantıya izin verse bile "izlerken kaydet" çalışır (kayıt = izlenen akış).
 */
object RecordingSink {
    @Volatile private var out: BufferedOutputStream? = null
    @Volatile private var file: File? = null

    /** Tee şu an diske yazıyor mu? */
    val recording: Boolean get() = out != null

    @Synchronized
    fun start(context: Context, title: String) {
        if (out != null) return
        val safe = title.replace(Regex("[^\\w\\-. ]"), "_").take(60).trim()
        val f = File(RecordingService.dir(context), "${safe}_${System.currentTimeMillis()}.ts")
        val ok = runCatching { out = f.outputStream().buffered(); file = f }.isSuccess
        if (!ok) { RecordingState.status.value = "Kayıt dosyası açılamadı."; return }
        RecordingState.active.value = ActiveRecording(title, f.absolutePath, System.currentTimeMillis())
        RecordingState.status.value = "● İzlenen yayından kaydediliyor…"
    }

    /** [TeeDataSource] tarafından çağrılır — okunan medya baytlarını dosyaya ekler. */
    fun write(b: ByteArray, off: Int, len: Int) {
        val o = out ?: return
        synchronized(this) { runCatching { o.write(b, off, len) } }
    }

    @Synchronized
    fun stop() {
        val o = out; val f = file
        out = null; file = null
        runCatching { o?.flush(); o?.close() }
        RecordingState.active.value = null
        // Boş kayıt bırakma.
        if (f != null && f.exists() && f.length() == 0L) {
            f.delete(); RecordingState.status.value = "Kayıt boş kaldı (veri gelmedi)."
        } else RecordingState.status.value = null
    }
}
