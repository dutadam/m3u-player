package app.cheesino.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cheesino.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryState(
    val channels: List<Channel> = emptyList(),
    val series: List<SeriesRef> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasSource: Boolean = false
) {
    val live get() = channels.filter { it.kind == MediaKind.LIVE }
    val movies get() = channels.filter { it.kind == MediaKind.VOD }
    val recentlyAdded get() = movies.filter { it.added != null }.sortedByDescending { it.added }
    val topRated get() = movies.filter { (it.rating ?: 0.0) >= 7.5 }.sortedByDescending { it.rating }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val creds = CredStore(app)
    private val _state = MutableStateFlow(LibraryState(hasSource = creds.load() != null))
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun restore() {
        creds.load()?.let { loadXtream(it) }
    }

    fun loadXtream(c: XtreamCredentials) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val client = XtreamClient(c)
                if (!client.authenticateActive()) {
                    _state.value = _state.value.copy(loading = false, error = "Abonelik aktif değil.")
                    return@launch
                }
                val live = client.allLive()
                val vod = runCatching { client.allVod() }.getOrDefault(emptyList())
                val series = runCatching { client.allSeries() }.getOrDefault(emptyList())
                if (live.isEmpty() && vod.isEmpty()) {
                    _state.value = _state.value.copy(loading = false, error = "Sunucuda kanal bulunamadı.")
                    return@launch
                }
                creds.save(c)
                _state.value = LibraryState(channels = live + vod, series = series, loading = false, hasSource = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Giriş başarısız — sunucu/kullanıcı/şifreyi kontrol edin.")
            }
        }
    }

    fun loadM3U(text: String) {
        val result = M3UParser.parse(text)
        if (result.channels.isEmpty()) { _state.value = _state.value.copy(error = "M3U içinde kanal bulunamadı."); return }
        _state.value = LibraryState(channels = result.channels, loading = false, hasSource = true)
    }

    fun loadM3UUrl(url: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.net.URL(url).readText()
                }
                loadM3U(text)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Playlist alınamadı.")
            }
        }
    }

    fun signOut() { creds.clear(); _state.value = LibraryState(hasSource = false) }
}
