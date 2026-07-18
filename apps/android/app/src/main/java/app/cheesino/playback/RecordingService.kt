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
 * Canlı yayın kaydı — yayının bayt akışını doğrudan bir dosyaya (`.ts`) yazar. Doğrudan
 * MPEG-TS / progressive yayınlar için gerçek DVR (çoğu Xtream `.ts` canlı akışı böyle). HLS
 * (`.m3u8`) canlı yayın segment muxing gerektirir → kapsam dışı (kullanıcıya bildirilir).
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

        private val _active = MutableStateFlow<ActiveRecording?>(null)
        /** UI için: şu an kaydedilen yayın (yoksa null). */
        val active: StateFlow<ActiveRecording?> = _active.asStateFlow()

        /** Kayıt dizini (uygulamaya özel, izin gerektirmez). */
        fun dir(context: Context): File =
            File(context.applicationContext.filesDir, "recordings").apply { mkdirs() }
    }

    @Volatile private var stop = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRecording(); return START_NOT_STICKY }
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Kayıt"
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
        _active.value = ActiveRecording(title, file.absolutePath, System.currentTimeMillis())
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(title), fgType)

        stop = false
        worker = Thread {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)   // canlı akış → okuma zaman aşımı yok
                .build()
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val body = resp.body ?: return@use
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (!stop) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                            }
                            output.flush()
                        }
                    }
                }
            } catch (_: Exception) {
                // Ağ/kesinti — dosya o ana kadar yazılanı korur.
            } finally {
                finishAndStop()
            }
        }.also { it.start() }
    }

    private fun stopRecording() { stop = true }

    private fun finishAndStop() {
        stop = true
        _active.value = null
        worker = null
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
            .setContentTitle("Kaydediliyor")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                null as android.graphics.drawable.Icon?, "Durdur", stopIntent).build())
            .build()
    }
}
