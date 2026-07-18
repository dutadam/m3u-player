package app.cheesino.data

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import app.cheesino.playback.CheesinoDownloadService
import app.cheesino.playback.Downloads
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Çevrimdışı indirmelerin UI köprüsü — media3 [DownloadManager]'ı sarar; tüm indirmelerin
 * güncel listesini bir akış olarak verir, indirme başlatma/silme/durum sorgulama sunar.
 */
class DownloadsRepository(context: Context) {
    private val appCtx = context.applicationContext
    private val manager: DownloadManager = Downloads.manager(appCtx)

    private val _downloads = MutableStateFlow<List<Download>>(emptyList())
    val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(dm: DownloadManager, d: Download, e: Exception?) = refresh()
        override fun onDownloadRemoved(dm: DownloadManager, d: Download) = refresh()
    }

    init {
        manager.addListener(listener)
        refresh()
    }

    /** İndirme dizinini okuyup akışı tazeler (ilerleme yüzdesi için UI periyodik çağırabilir). */
    fun refresh() {
        val list = ArrayList<Download>()
        val cursor = manager.downloadIndex.getDownloads()
        try { while (cursor.moveToNext()) list.add(cursor.download) } finally { cursor.close() }
        _downloads.value = list
    }

    /** [id] için indirmeyi kuyruğa alır; [title] geri gösterim için istekle saklanır. */
    fun enqueue(id: String, url: String, title: String) {
        val request = DownloadRequest.Builder(id, Uri.parse(url))
            .setData(title.toByteArray(Charsets.UTF_8))
            .build()
        DownloadService.sendAddDownload(appCtx, CheesinoDownloadService::class.java, request, false)
    }

    /** İndirilmiş/indirilen öğeyi siler (diskteki dosyalar da temizlenir). */
    fun remove(id: String) {
        DownloadService.sendRemoveDownload(appCtx, CheesinoDownloadService::class.java, id, false)
    }

    fun stateOf(id: String): Int? = _downloads.value.firstOrNull { it.request.id == id }?.state
    fun isCompleted(id: String): Boolean = stateOf(id) == Download.STATE_COMPLETED
}
