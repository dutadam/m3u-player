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
    val hasSource: Boolean = false,
    val parentalOn: Boolean = false,
    val adultUnlocked: Boolean = false
) {
    private fun <T> gate(list: List<T>, adult: (T) -> Boolean): List<T> =
        if (parentalOn && !adultUnlocked) list.filter { !adult(it) } else list

    val visibleChannels get() = gate(channels) { AdultFilter.isAdult(it.name, it.group) }
    val live get() = visibleChannels.filter { it.kind == MediaKind.LIVE }
    val movies get() = visibleChannels.filter { it.kind == MediaKind.VOD }
    val visibleSeries get() = gate(series) { AdultFilter.isAdult(it.name, it.group) }
    val recentlyAdded get() = movies.filter { it.added != null }.sortedByDescending { it.added }
    val topRated get() = movies.filter { (it.rating ?: 0.0) >= 7.5 }.sortedByDescending { it.rating }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val creds = CredStore(app)
    private val _state = MutableStateFlow(LibraryState(hasSource = creds.load() != null))
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Xtream oturumu — dizi detayı/bölüm çekmek için canlı tutulur (M3U kaynağında null). */
    private var client: XtreamClient? = null

    // ---- EPG (rehber) ----
    private var epgSourceUrl: String? = null   // M3U url-tvg (Xtream'de xmltv.php kullanılır)
    private val _epg = MutableStateFlow<Map<String, List<EpgEntry>>>(emptyMap())
    val epg: StateFlow<Map<String, List<EpgEntry>>> = _epg.asStateFlow()

    /** XMLTV'yi bir kez çeker/ayrıştırır. Rehber ilk açıldığında çağrılır. */
    fun loadEpg() {
        if (_epg.value.isNotEmpty()) return
        val url = client?.xmltvUrl() ?: epgSourceUrl ?: return
        viewModelScope.launch {
            try {
                val xml = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.net.URL(url).readText()
                }
                _epg.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    XmltvParser.parse(xml)
                }
            } catch (e: Exception) { /* rehber opsiyonel */ }
        }
    }

    /** Canlı kanalın geçmiş programını baştan izleme (timeshift) URL'i — yalnız Xtream + arşiv. */
    fun catchupUrl(channel: Channel, entry: EpgEntry): String? {
        val cl = client ?: return null
        if (!channel.supportsCatchup) return null
        val sid = channel.id.removePrefix("live_").toIntOrNull() ?: return null
        val durMin = ((entry.stop - entry.start) / 60).toInt().coerceIn(1, 24 * 60)
        val start = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
            .format(java.util.Date(entry.start * 1000))
        return cl.timeshiftUrl(sid, durMin, start)
    }

    // ---- Uygulama ayarları (User-Agent + parental PIN) ----
    private val settings = AppSettings(app)
    val userAgent: String get() = settings.userAgent
    val parentalEnabled: Boolean get() = settings.parentalEnabled
    val hasPin: Boolean get() = settings.hasPin

    fun setUserAgent(v: String) { settings.userAgent = v }
    fun setPin(pin: String) { settings.setPin(pin); _state.value = _state.value.copy(parentalOn = true, adultUnlocked = false) }
    fun disableParental(pin: String): Boolean {
        if (!settings.verifyPin(pin)) return false
        settings.clearPin()
        _state.value = _state.value.copy(parentalOn = false, adultUnlocked = true)
        return true
    }
    /** Yetişkin içeriği bu oturumda aç. Doğru PIN gerekir. */
    fun unlockAdult(pin: String): Boolean {
        if (!settings.verifyPin(pin)) return false
        _state.value = _state.value.copy(adultUnlocked = true)
        return true
    }
    fun lockAdult() { _state.value = _state.value.copy(adultUnlocked = false) }

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
    fun recordPlay(id: String, name: String, group: String?) = mutateUser { u ->
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

    /** İçeriği yeniden çeker (Xtream kaynağı için). M3U'da kayıtlı url yoksa no-op. */
    fun reload() {
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
                _epg.value = emptyMap()
                _state.value = LibraryState(channels = live + vod, series = series, loading = false,
                    hasSource = true, parentalOn = settings.parentalEnabled)
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
        epgSourceUrl = result.epgUrl
        _epg.value = emptyMap()
        _state.value = LibraryState(channels = result.channels, loading = false,
            hasSource = true, parentalOn = settings.parentalEnabled)
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

    // ---- Çoklu ekran ----
    private val mvStore = MultiViewStore(app)
    fun loadMultiView(): MultiViewConfig = mvStore.load()
    fun saveMultiView(c: MultiViewConfig) = mvStore.save(c)

    fun signOut() { client = null; creds.clear(); _state.value = LibraryState(hasSource = false) }
}
