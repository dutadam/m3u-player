package app.cheesino.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Arka plan oynatma servisi. Oynatıcıyı (ExoPlayer) SAHİPLENİR ve bir MediaSession üzerinden
 * sunar; böylece:
 *  - Ekran kapansa / uygulama arka plana alınsa bile ses akmaya devam eder (ön plan servisi).
 *  - Bildirim + kilit ekranı kontrolleri (oynat/duraklat/ileri) media3 tarafından otomatik gelir.
 *  - Kulaklık çıkınca duraklatma + ses odağı yönetimi motor tarafında yapılır.
 *
 * UI (PlayerScreen) buna bir MediaController ile bağlanır; oynatıcıyı doğrudan yaratmaz.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // 4K/yüksek bit hızı için geniş buffer (PlayerScreen'deki eski ayarla aynı).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(50_000, 240_000, 2_500, 5_000)
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        // Donanım kod çözücü açılamazsa ikincil kod çözücüye düş.
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        // İndirilmiş içerik disk cache'inden (çevrimdışı) oynasın; canlı/akış içerik yazılmadan
        // upstream'e geçer (salt-okunur cache — canlı yayını cache'lemeyiz).
        // Tee: kayıt aktifken oynatıcının okuduğu baytlar diske de yazılır (ayrı bağlantı açmadan).
        val mediaSourceFactory = DefaultMediaSourceFactory(TeeDataSourceFactory(Downloads.cacheDataSourceFactory(this)))
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            // Ses odağını motor yönetsin (arama/başka uygulama gelince duraklat/kıs).
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            // Kulaklık/BT çıkınca (ses "gürültülü" olunca) duraklat.
            .setHandleAudioBecomingNoisy(true)
            // Arka planda ağ akışı sürerken CPU/Wi-Fi uyanık kalsın.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Kullanıcı uygulamayı son işlemlerden kaydırınca: oynatmıyorsa servisi kapat. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
