package app.cheesino.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cihazdaki yerel bir video dosyası (MediaStore'dan). */
data class LocalVideo(
    val uri: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val folder: String?
)

/**
 * Cihazın video kütüphanesini MediaStore'dan okur — yerel medya oynatıcı için.
 * READ_MEDIA_VIDEO (Android 13+) / READ_EXTERNAL_STORAGE (≤32) izni gerekir; izin yoksa boş döner.
 */
object LocalMediaStore {

    suspend fun videos(context: Context): List<LocalVideo> = withContext(Dispatchers.IO) {
        val out = ArrayList<LocalVideo>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        runCatching {
            context.contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val bucketCol = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    out.add(
                        LocalVideo(
                            uri = uri.toString(),
                            title = c.getString(nameCol) ?: "Video",
                            durationMs = c.getLong(durCol),
                            sizeBytes = c.getLong(sizeCol),
                            dateAddedSec = c.getLong(dateCol),
                            folder = if (bucketCol >= 0) c.getString(bucketCol) else null
                        )
                    )
                }
            }
        }
        out
    }
}
