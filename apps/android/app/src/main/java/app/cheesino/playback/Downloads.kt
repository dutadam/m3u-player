package app.cheesino.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import app.cheesino.data.AppSettings
import java.io.File
import java.util.concurrent.Executors

/**
 * Çevrimdışı indirme altyapısı — süreç genelinde tekil (singleton).
 *
 * Aynı [SimpleCache] hem indirmenin yazdığı hem de oynatıcının okuduğu depodur:
 *  - İndirme: [DownloadManager] içeriği `filesDir/downloads` altına (uygulamaya özel, izin
 *    gerektirmez, dışarıdan erişilemez) yazar.
 *  - Oynatma: [cacheDataSourceFactory] SALT-OKUNUR bir CacheDataSource verir; indirilmiş öğe
 *    internet olmadan da bu depodan oynatılır.
 *
 * Not: Bir dizin için süreçte yalnız TEK SimpleCache örneği olabilir; bu yüzden tekil.
 */
object Downloads {
    const val CHANNEL_ID = "cheesino_downloads"
    const val FG_NOTIF_ID = 2000

    private var db: StandaloneDatabaseProvider? = null
    private var cacheRef: SimpleCache? = null
    private var managerRef: DownloadManager? = null
    private var notifHelper: DownloadNotificationHelper? = null

    @Synchronized
    fun notificationHelper(context: Context): DownloadNotificationHelper =
        notifHelper ?: DownloadNotificationHelper(context.applicationContext, CHANNEL_ID)
            .also { notifHelper = it }

    @Synchronized
    private fun db(context: Context): StandaloneDatabaseProvider =
        db ?: StandaloneDatabaseProvider(context.applicationContext).also { db = it }

    /** Kullanıcının ayarladığı User-Agent'ı taşıyan HTTP kaynağı — indirme + oynatma upstream'i.
     *  Çoğu sağlayıcı belirli bir UA ister; ayardaki değer artık gerçekten uygulanıyor. */
    private fun httpFactory(context: Context): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(AppSettings(context.applicationContext).userAgent)
            .setAllowCrossProtocolRedirects(true)

    @Synchronized
    fun cache(context: Context): SimpleCache =
        cacheRef ?: SimpleCache(
            File(context.applicationContext.filesDir, "downloads"),
            NoOpCacheEvictor(),           // indirilenleri asla otomatik silme (kullanıcı siler)
            db(context)
        ).also { cacheRef = it }

    @Synchronized
    fun manager(context: Context): DownloadManager {
        managerRef?.let { return it }
        val ctx = context.applicationContext
        val m = DownloadManager(
            ctx,
            db(ctx),
            cache(ctx),
            httpFactory(ctx),
            Executors.newFixedThreadPool(3)
        ).apply {
            maxParallelDownloads = 2
        }
        managerRef = m
        return m
    }

    /** Oynatıcının indirilmiş içeriği depodan okuması için SALT-OKUNUR kaynak fabrikası. */
    fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(httpFactory(context))
            .setCacheWriteDataSinkFactory(null)   // oynatırken yazma yok → salt-okunur
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
