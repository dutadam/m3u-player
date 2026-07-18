package app.cheesino.playback

import android.app.Notification
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import app.cheesino.R

/**
 * İndirmeleri ön planda yürüten servis (media3 DownloadService). İlerleme bildirimi gösterir,
 * uygulama kapalıyken bile indirmeyi sürdürür.
 */
class CheesinoDownloadService : DownloadService(
    Downloads.FG_NOTIF_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    Downloads.CHANNEL_ID,
    R.string.download_channel_name,
    /* channelDescriptionResourceId = */ 0
) {
    override fun getDownloadManager(): DownloadManager = Downloads.manager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification =
        Downloads.notificationHelper(this).buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            /* contentIntent = */ null,
            /* message = */ if (downloads.isEmpty()) null else downloads.first().request.id,
            downloads,
            notMetRequirements
        )
}
