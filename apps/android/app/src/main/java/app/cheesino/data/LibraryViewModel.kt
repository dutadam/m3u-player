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

    /** Xtream oturumu — dizi detayı/bölüm çekmek için canlı tutulur (M3U kaynağında null). */
    private var client: XtreamClient? = null

    // ---- Kullanıcı verisi (favori/beğeni/ilerleme/geçmiş) ----
    private val userStore = UserDataStore(app)
    private val _user = MutableStateFlow(userStore.load())
    val user: StateFlow<UserData> = _user.asStateFlow()

    private fun mutateUser(block: (UserData) -> UserData) {
        val next = block(_user.value)
        _user.value = next
        userStore.save(next)
    }

    fun isFavorite(id: String) = id in _user.value.favorites
    fun toggleFavorite(id: String) = mutateUser { u ->
        u.copy(favorites = if (id in u.favorites) u.favorites - id else u.favorites + id)
    }

    /** Beğeni durumu: +1 beğen, -1 beğenme, 0 nötr. */
    fun setRating(id: String, value: Int) = mutateUser { u ->
        when (value) {
            1 -> u.copy(likes = u.likes + id, dislikes = u.dislikes - id)
            -1 -> u.copy(dislikes = u.dislikes + id, likes = u.likes - id)
            else -> u.copy(likes = u.likes - id, dislikes = u.dislikes - id)
        }
    }
    fun ratingOf(id: String): Int = when {
        id in _user.value.likes -> 1
        id in _user.value.dislikes -> -1
        else -> 0
    }

    /** İzleme başladığında tür afinitesi için olay kaydı (canlı hariç). */
    fun recordPlay(id: String, name: String, group: String) = mutateUser { u ->
        val genres = GenreTagger.tags(name, group).toList()
        val ev = PlayEvent(id, genres, System.currentTimeMillis())
        u.copy(history = (u.history + ev).takeLast(400))
    }

    fun saveProgress(mark: ResumeMark) = mutateUser { u ->
        if (mark.finished) u.copy(resume = u.resume - mark.id)
        else u.copy(resume = u.resume + (mark.id to mark))
    }
    fun clearResume(id: String) = mutateUser { u -> u.copy(resume = u.resume - id) }
    fun resumeOf(id: String): ResumeMark? = _user.value.resume[id]

    fun restore() {
        creds.load()?.let { loadXtream(it) }
    }

    fun loadXtream(c: XtreamCredentials) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val cl = XtreamClient(c)
                if (!cl.authenticateActive()) {
                    _state.value = _state.value.copy(loading = false, error = "Abonelik aktif değil.")
                    return@launch
                }
                val live = cl.allLive()
                val vod = runCatching { cl.allVod() }.getOrDefault(emptyList())
                val series = runCatching { cl.allSeries() }.getOrDefault(emptyList())
                if (live.isEmpty() && vod.isEmpty()) {
                    _state.value = _state.value.copy(loading = false, error = "Sunucuda kanal bulunamadı.")
                    return@launch
                }
                client = cl
                creds.save(c)
                _state.value = LibraryState(channels = live + vod, series = series, loading = false, hasSource = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Giriş başarısız — sunucu/kullanıcı/şifreyi kontrol edin.")
            }
        }
    }

    /** Dizi detayını (sezon/bölüm) tembel çeker. M3U kaynağında Xtream yoksa null döner. */
    suspend fun seriesDetail(ref: SeriesRef): Series? = client?.seriesInfo(ref)

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

    fun signOut() { client = null; creds.clear(); _state.value = LibraryState(hasSource = false) }
}
