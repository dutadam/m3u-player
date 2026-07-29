package app.cheesino.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.cheesino.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Aktif kayıt bilgisi (yoksa null). */
data class ActiveRecording(val title: String, val path: String, val startedAt: Long)

/**
 * Canlı yayın kaydı — iki kol:
 *  - Doğrudan MPEG-TS / progressive: yayının bayt akışını dosyaya (`.ts`) döker.
 *  - HLS (`.m3u8`): [HlsRecorder] ile playlist izlenir, segmentler (AES-128 çözülerek) tek
 *    `.ts` dosyasına eklenir. (fMP4/CMAF segmentli HLS kapsam dışı.)
 *
 * Ön plan servisidir: uygulama arka planda/kapalıyken de kaydı sürdürür; bildirimde "Durdur".
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "app.cheesino.record.START"
        const val ACTION_STOP = "app.cheesino.record.STOP"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val CHANNEL_ID = "cheesino_recording"
        const val NOTIF_ID = 3000

        /** UI için: şu an kaydedilen yayın (yoksa null). Tee ve servis kaydı ortak akışa yazar. */
        val active: StateFlow<ActiveRecording?> = RecordingState.active.asStateFlow()

        /** Son kayıt durum/hata mesajı (UI'da göster — 0 MB nedenini görmek için). */
        val status: StateFlow<String?> = RecordingState.status.asStateFlow()

        /** Kayıt dizini (uygulamaya özel, izin gerektirmez). */
        fun dir(context: Context): File =
            File(context.applicationContext.filesDir, "recordings").apply { mkdirs() }
    }

    @Volatile private var stop = false
    private var worker: Thread? = null
    private var currentFile: File? = null
    // Süren HTTP çağrısı — bekçi/durdurma bunu iptal ederek bloke okumayı (readTimeout=0) keser.
    @Volatile private var currentCall: okhttp3.Call? = null

    // Çoğu streaming sunucusu User-Agent olmayan isteği 403 ile reddeder → kayıt boş kalır.
    // Oynatıcılarla uyumlu bir UA gönder.
    private val userAgent = "VLC/3.0.20 LibVLC/3.0.20"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRecording(); return START_NOT_STICKY }
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Recording"
                if (url.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
                startRecording(url, title)
            }
            else -> stopSelf()
        }
        return START_STICKY
    }

    private fun startRecording(url: String, title: String) {
        if (worker != null) return  // zaten kayıtta (tek eşzamanlı kayıt)
        val safe = title.replace(Regex("[^\\w\\-. ]"), "_").take(60).trim()
        val file = File(dir(this), "${safe}_${System.currentTimeMillis()}.ts")
        currentFile = file
        RecordingState.active.value = ActiveRecording(title, file.absolutePath, System.currentTimeMillis())
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(title), fgType)

        stop = false
        // Oynatıcıyla aynı aday-URL çözümünü kullan (http→https, .m3u8 ek) — ham URL çalışmıyorsa.
        val streamUrl = app.cheesino.core.StreamResolver.candidates(url).firstOrNull()?.url ?: url
        val isHls = streamUrl.substringBefore('?').lowercase().endsWith(".m3u8") || streamUrl.contains(".m3u8")
        RecordingState.status.value = "Connecting…"

        // Bekçi: veri gelmezse kaydı sonsuza dek çalışır durumda bırakma. Aksi halde başarısız bir
        // kayıt sağlayıcının (çoğu tek eşzamanlı) bağlantısını tutar → başka yayın açılamaz.
        Thread {
            val start = System.currentTimeMillis()
            while (!stop) {
                runCatching { Thread.sleep(1000) }
                if (file.length() > 0L) break            // veri akıyor → bekçiyi bırak
                if (System.currentTimeMillis() - start > 15_000) {
                    RecordingState.status.value = "Recording couldn't start — source sent no data, connection released."
                    stop = true                          // worker döngüsünü kır
                    runCatching { currentCall?.cancel() } // bloke okumayı da kes → bağlantıyı serbest bırak
                    break
                }
            }
        }.also { it.start() }
        worker = Thread {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)   // canlı akış → okuma zaman aşımı yok
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
                }
                .build()
            try {
                if (isHls) {
                    // HLS: playlist'i izleyip segmentleri tek .ts dosyasına ekle (AES-128 çözerek).
                    RecordingState.status.value = "HLS recording…"
                    // HLS istekleri kısa → sonsuz beklemesin diye okuma zaman aşımı ver.
                    val hlsClient = client.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()
                    HlsRecorder.record(hlsClient, streamUrl, file) { stop }
                    if (file.length() == 0L && !stop)
                        RecordingState.status.value = "HLS segments unavailable (source blocked or fMP4)."
                } else {
                    // Doğrudan TS/progressive: bayt akışını dosyaya dök.
                    val call = client.newCall(Request.Builder().url(streamUrl).build())
                    currentCall = call
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) {
                            RecordingState.status.value = "Server refused (HTTP ${resp.code})."
                            return@use
                        }
                        val body = resp.body ?: run { RecordingState.status.value = "Empty response."; return@use }
                        var total = 0L
                        body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                while (!stop) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    output.write(buf, 0, n); total += n
                                    if (total > 0) RecordingState.status.value = null   // akış başladı, mesaj temiz
                                }
                                output.flush()
                            }
                        }
                        if (total == 0L) RecordingState.status.value = "No data (source empty or blocked)."
                    }
                }
            } catch (e: Exception) {
                if (!stop) RecordingState.status.value = "Recording error: ${e.message ?: "bilinmeyen"}"
            } finally {
                currentCall = null
                // Havuzdaki keep-alive soketi hemen kapat — yoksa kayıt bittikten sonra bile
                // sağlayıcının (tek eşzamanlı) bağlantısı dakikalarca tutulu kalır → yayın açılmaz.
                runCatching { client.connectionPool.evictAll() }
                runCatching { client.dispatcher.executorService.shutdown() }
                finishAndStop()
            }
        }.also { it.start() }
    }

    private fun stopRecording() { stop = true; runCatching { currentCall?.cancel() } }

    private fun finishAndStop() {
        stop = true
        RecordingState.active.value = null
        worker = null
        // Boş kayıt (ör. sunucu reddi) → 0 MB dosya bırakma.
        currentFile?.let { if (it.exists() && it.length() == 0L) it.delete() }
        currentFile = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stop = true
        super.onDestroy()
    }

    private fun buildNotification(title: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.record_channel_name),
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                null as android.graphics.drawable.Icon?, "Stop", stopIntent).build())
            .build()
    }
}
