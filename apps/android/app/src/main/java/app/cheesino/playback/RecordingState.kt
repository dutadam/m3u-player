package app.cheesino.playback

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Kayıt için ortak durum — hem ayrı bağlantılı [RecordingService] hem de izlenen akıştan yakalayan
 * [RecordingSink] buraya yazar; UI tek yerden okur.
 */
object RecordingState {
    /** Şu an kaydedilen yayın (yoksa null). */
    val active = MutableStateFlow<ActiveRecording?>(null)
    /** Son kayıt durum/hata mesajı. */
    val status = MutableStateFlow<String?>(null)
}
