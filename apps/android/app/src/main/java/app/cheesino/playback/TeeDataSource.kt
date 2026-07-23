package app.cheesino.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Oynatıcının okuduğu baytları [RecordingSink]'e kopyalayan aracı DataSource. Yalnız kayıt aktifken ve
 * medya segmenti/akışı okunurken yazar; playlist (.m3u8) ve şifre anahtarı (.key) isteklerini atlar
 * (yoksa .ts kayıt bozulur). MPEG-TS segmentleri birleşince tek parça oynatılabilir kayıt olur.
 */
class TeeDataSource(private val upstream: DataSource) : DataSource by upstream {
    private var tee = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri.toString().substringBefore('?').lowercase()
        tee = RecordingSink.recording &&
            !uri.endsWith(".m3u8") && !uri.endsWith(".m3u") &&
            !uri.endsWith(".key") && !uri.contains("/key")
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = upstream.read(buffer, offset, length)
        if (tee && n > 0) RecordingSink.write(buffer, offset, n)
        return n
    }
}

/** [TeeDataSource] üreten fabrika — PlaybackService'in DataSource zincirini sarar. */
class TeeDataSourceFactory(private val upstream: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = TeeDataSource(upstream.createDataSource())
}
