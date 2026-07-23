package app.cheesino.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cheesino.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryState(
    val channels: List<Channel> = emptyList(),
    val series: List<SeriesRef> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,   // arka planda tazeleme (içerik gösterilirken)
    val error: String? = null,
    val hasSource: Boolean = false,
    val parentalOn: Boolean = false,
    val adultUnlocked: Boolean = false
) {
    private fun <T> gate(list: List<T>, adult: (T) -> Boolean): List<T> =
        if (parentalOn && !adultUnlocked) list.filter { !adult(it) } else list

    val visibleChannels get() = gate(channels) { AdultFilter.isAdult(it.name, it.group) }
    val live get() = visibleChannels.filter { it.kind == MediaKind.LIVE }
    // Filmler: görselsizleri gizle + aynı filmin farklı kategori/varyantlarını tek kart olarak
    // topla (global tekilleştirme) — "2 farklı film gibi" tekrarları önler.
    val movies get() = visibleChannels.asSequence()
        .filter { it.kind == MediaKind.VOD && it.logo != null }
        .distinctBy { app.cheesino.core.baseTitle(it.name) }
        .toList()
    // Diziler: görselsizleri gizle + isim bazlı tekilleştir.
    val visibleSeries get() = gate(series) { AdultFilter.isAdult(it.name, it.group) }
        .asSequence().filter { it.cover != null }
        .distinctBy { app.cheesino.core.baseTitle(it.name) }
        .toList()
    val recentlyAdded get() = movies.filter { it.added != null }.sortedByDescending { it.added }
    val topRated get() = movies.filter { (it.rating ?: 0.0) >= 7.5 }.sortedByDescending { it.rating }
}

/** Arka planda hesaplanan keşif verisi — ana thread'i kilitlemez, kompozisyonda ağır iş yok. */
data class Discover(
    val recommended: List<Channel> = emptyList(),
    val movieRails: List<Pair<String, List<Channel>>> = emptyList(),
    val seriesRails: List<Pair<String, List<SeriesRef>>> = emptyList(),
    val genres: List<String> = emptyList()
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val creds = CredStore(app)
    // Kayıtlı kaynak varsa açılışta doğrudan yükleniyor durumu → boş ekran görünmez.
    private val _state = MutableStateFlow(
        creds.load().let { LibraryState(hasSource = it != null, loading = it != null) }
    )
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Xtream oturumu — dizi detayı/bölüm çekmek için canlı tutulur (M3U kaynağında null). */
    private var client: XtreamClient? = null

    /** İçerik disk önbelleği — açılışta anında gösterim, ağ arka planda tazeler. */
    private val contentCache = ContentCache(app)
    private fun xtKey(c: XtreamCredentials) = "xt:${c.server}:${c.username}"

    // ---- Keşif (arka planda hesaplanır; kullanıcı değişiminde DEĞİL, içerik yüklenince yenilenir) ----
    private val _discover = MutableStateFlow(Discover())
    val discover: StateFlow<Discover> = _discover.asStateFlow()

    private fun rebuildDiscover() {
        val st = _state.value
        val u = _user.value
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val allCh = st.visibleChannels
            val movies = st.movies          // tekilleştirilmiş + görselli film havuzu
            val series = st.visibleSeries
            val rec = runCatching { Recommender.recommended(movies, u) }.getOrDefault(emptyList())
            val mRails = runCatching { RailEngine.movieRails(movies, allCh, u) }.getOrDefault(emptyList())
            val sRails = runCatching { RailEngine.seriesRails(series, u) }.getOrDefault(emptyList())
            val genres = runCatching {
                val c = HashMap<String, Int>()
                movies.forEach { m -> GenreTagger.tags(m.name, m.group).forEach { c[it] = (c[it] ?: 0) + 1 } }
                series.forEach { s -> GenreTagger.tags(s.name, s.genre, s.group).forEach { c[it] = (c[it] ?: 0) + 1 } }
                c.filter { it.value >= 3 }.keys.sortedBy { GenreTagger.canonical.indexOf(it) }
            }.getOrDefault(emptyList())
            _discover.value = Discover(rec, mRails, sRails, genres)
        }
    }

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
    // ---- Pro yetkilendirme + Play Billing ----
    private val entitlements = Entitlements(app)
    val isPro: StateFlow<Boolean> = entitlements.isPro
    fun setPro(v: Boolean) = entitlements.setPro(v)

    private val billing = BillingManager(app, entitlements)
    /** Pro fiyat metni (ör. "₺149,00"); ürün yüklenene kadar null. */
    val proPrice: StateFlow<String?> = billing.proProduct
        .map { it?.oneTimePurchaseOfferDetails?.formattedPrice }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** Satın alma durum/hata mesajı (UI snackbar/uyarı için). */
    val billingStatus: StateFlow<String?> = billing.status
    init { billing.start() }
    /** Play satın alma ekranını açar (Activity gerekir). */
    fun purchasePro(activity: android.app.Activity) = billing.purchase(activity)
    /** Önceki satın alımları geri yükler. */
    fun restorePurchases() = billing.restore()

    // ---- Çevrimdışı indirmeler ----
    private val downloadsRepo = DownloadsRepository(app)
    val downloads: StateFlow<List<androidx.media3.exoplayer.offline.Download>> = downloadsRepo.downloads
    fun download(id: String, url: String, title: String) = downloadsRepo.enqueue(id, url, title)
    fun removeDownload(id: String) = downloadsRepo.remove(id)
    fun refreshDownloads() = downloadsRepo.refresh()
    fun downloadState(id: String): Int? = downloadsRepo.stateOf(id)
    fun isDownloaded(id: String): Boolean = downloadsRepo.isCompleted(id)

    // ---- Canlı kayıt (DVR) ----
    private val recordingsRepo = RecordingsRepository(app)
    val recordingActive: StateFlow<app.cheesino.playback.ActiveRecording?> = recordingsRepo.active
    val recordingStatus: StateFlow<String?> = recordingsRepo.status
    val recordings: StateFlow<List<java.io.File>> = recordingsRepo.recordings
    fun startRecording(url: String, title: String) = recordingsRepo.start(url, title)
    /** İzlenen akıştan kaydet (ExoPlayer tee) — sağlayıcı tek bağlantıya izin verse de çalışır. */
    fun startRecordingHere(title: String) = recordingsRepo.startHere(title)
    fun stopRecording() = recordingsRepo.stop()
    fun refreshRecordings() = recordingsRepo.refresh()
    fun deleteRecording(file: java.io.File) = recordingsRepo.delete(file)

    // ---- Program hatırlatıcıları ----
    fun isReminded(ch: Channel, e: EpgEntry) = Reminders.isSet(getApplication(), ch.id, e.start)
    fun toggleReminder(ch: Channel, e: EpgEntry) {
        val app = getApplication<Application>()
        val r = Reminder(ch.id, ch.name, ch.url, ch.logo, e.title, e.start)
        if (Reminders.isSet(app, ch.id, e.start)) Reminders.remove(app, r) else Reminders.add(app, r)
    }

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
    /** Oynatıcı motoru: 0 Otomatik · 1 ExoPlayer · 2 VLC. */
    val playerEngine: Int get() = settings.playerEngine
    fun setPlayerEngine(v: Int) { settings.playerEngine = v }
    val subtitleScale: Float get() = settings.subtitleScale
    fun setSubtitleScale(v: Float) { settings.subtitleScale = v }
    val subtitleColor: Int get() = settings.subtitleColor
    val subtitleBg: Int get() = settings.subtitleBg
    fun setSubtitleColor(v: Int) { settings.subtitleColor = v }
    fun setSubtitleBg(v: Int) { settings.subtitleBg = v }
    /** Favori/beğeni/geçmiş/ilerleme temizle. */
    fun clearUserData() { _user.value = UserData(); userStore.save(UserData()) }
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
        pushSync()   // giriş varsa buluta yansıt
    }

    // ---- Hesap (Google giriş) + cihazlar arası senkron ----
    private val authManager = AuthManager(app)
    private val syncRepo = SyncRepository()
    val authUser: StateFlow<AuthUser?> = authManager.user
    private val _authStatus = MutableStateFlow<String?>(null)
    val authStatus: StateFlow<String?> = _authStatus
    fun isAuthConfigured(context: android.content.Context) = authManager.isConfigured(context)

    fun signIn(activity: android.app.Activity) {
        viewModelScope.launch {
            _authStatus.value = "Giriş yapılıyor…"
            try {
                val r = authManager.signInWithGoogle(activity)
                _authStatus.value = if (r.isSuccess) null
                    else "Giriş başarısız: ${r.exceptionOrNull()?.message ?: "iptal edildi / hata"}"
                if (r.isSuccess) { pullAndMerge(); syncSourceOnSignIn() }
            } catch (t: Throwable) {
                _authStatus.value = "Giriş başarısız: ${t.message ?: "hata"}"
            }
        }
    }

    /**
     * Girişte kaynağı senkronla: yerelde kaynak varsa hesaba yaz; yoksa hesaptakini indir ve yükle
     * → yeni cihazda "Google ile gir, kaynağın otomatik gelsin". (Kullanıcının KENDİ kaynağı.)
     */
    private fun syncSourceOnSignIn() {
        val uid = authManager.currentUid() ?: return
        viewModelScope.launch {
            runCatching {
                val local = creds.load()
                if (local != null) { syncRepo.pushSource(uid, local); return@runCatching }
                if (!_state.value.hasSource) {
                    _authStatus.value = "Kaynak hesabından getiriliyor…"
                    val remote = syncRepo.pullSource(uid)
                    if (remote != null) { loadXtream(remote); _authStatus.value = null }
                    else _authStatus.value = "Hesapta kayıtlı kaynak yok — kaynağını bir kez ekle, sonraki cihazda otomatik gelir."
                }
            }
        }
    }
    fun signOutAccount() = authManager.signOut()

    /** Yerel veriyi buluta yaz (giriş varsa). Firestore hazır değilse sessizce geç (çökme yok). */
    private fun pushSync() {
        val uid = authManager.currentUid() ?: return
        viewModelScope.launch { runCatching { syncRepo.push(uid, _user.value) } }
    }

    /** Girişte buluttaki favori/beğeni/beğenmeme'yi yerelle birleştir (union) ve geri yaz. */
    private fun pullAndMerge() {
        val uid = authManager.currentUid() ?: return
        viewModelScope.launch {
            // Firestore SDK'sından gelebilecek her türlü hatayı (Error dahil) yut — giriş sonrası çökmesin.
            runCatching {
                syncRepo.pull(uid)?.let { r ->
                    val merged = _user.value.copy(
                        favorites = _user.value.favorites + r.favorites,
                        likes = _user.value.likes + r.likes,
                        dislikes = _user.value.dislikes + r.dislikes
                    )
                    _user.value = merged
                    userStore.save(merged)
                    syncRepo.push(uid, merged)
                }
            }
        }
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

    /**
     * Açılış: içerik zaten belleğe yüklendiyse (uygulamaya geri dönüş / ekran döndürme) hiçbir şey yapma.
     * Önbellek varsa anında göster; yalnızca önbellek 24 saatten eskiyse ağdan arka planda tazele.
     */
    fun restore() {
        if (_state.value.channels.isNotEmpty()) return   // geri dönüşte yeniden yükleme yok
        val c = creds.load() ?: return
        val cached = contentCache.load(xtKey(c))
        if (cached != null && cached.channels.isNotEmpty()) {
            client = XtreamClient(c)   // detay (dizi/film) çekimleri için canlı istemci
            _epg.value = emptyMap()
            val stale = System.currentTimeMillis() - cached.savedAt > DAY_MS
            _state.value = LibraryState(channels = cached.channels, series = cached.series,
                loading = false, refreshing = stale, hasSource = true, parentalOn = settings.parentalEnabled)
            rebuildDiscover()
            if (stale) loadXtream(c, background = true)   // günde bir kez otomatik tazele
        } else {
            loadXtream(c)
        }
    }

    private companion object { const val DAY_MS = 24L * 3600 * 1000 }

    /** Manuel yenileme (ayarlardan). İçerik varsa ekranı boşaltmadan arka planda tazeler. */
    fun reload() {
        val c = creds.load() ?: return
        loadXtream(c, background = _state.value.channels.isNotEmpty())
    }

    /**
     * Xtream içeriğini çeker. [background] true ise (önbellek zaten gösteriliyorken tazeleme)
     * tam ekran yükleyici yerine "refreshing" göstergesi kullanılır ve hata sessizce yutulur.
     */
    fun loadXtream(c: XtreamCredentials, background: Boolean = false) {
        _state.value =
            if (background) _state.value.copy(refreshing = true, error = null)
            else _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val cl = XtreamClient(c)
                if (!cl.authenticateActive()) {
                    _state.value = _state.value.copy(loading = false, refreshing = false,
                        error = if (background) null else "Abonelik aktif değil.")
                    return@launch
                }
                val live = cl.allLive()
                val vod = runCatching { cl.allVod() }.getOrDefault(emptyList())
                val series = runCatching { cl.allSeries() }.getOrDefault(emptyList())
                if (live.isEmpty() && vod.isEmpty()) {
                    _state.value = _state.value.copy(loading = false, refreshing = false,
                        error = if (background) null else "Sunucuda kanal bulunamadı.")
                    return@launch
                }
                client = cl
                creds.save(c)
                // Giriş yapıldıysa kendi kaynağını hesaba yaz → başka cihazda otomatik gelsin.
                authManager.currentUid()?.let { uid -> launch { syncRepo.pushSource(uid, c) } }
                val channels = live + vod
                contentCache.save(xtKey(c), CachedContent(channels, series, null, System.currentTimeMillis()))
                // Oturum içi yetişkin kilidini koru (arka plan tazelemede yeniden kilitleme).
                val adult = _state.value.adultUnlocked
                if (!background) _epg.value = emptyMap()
                _state.value = LibraryState(channels = channels, series = series, loading = false,
                    refreshing = false, hasSource = true, parentalOn = settings.parentalEnabled,
                    adultUnlocked = adult)
                rebuildDiscover()
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, refreshing = false,
                    error = if (background) null else "Giriş başarısız — sunucu/kullanıcı/şifreyi kontrol edin.")
            }
        }
    }

    // Detay önbelleği — tekrar açılışta anında gelsin (ağ beklemesi yok).
    private val seriesCache = HashMap<Int, Series>()
    private val movieCache = HashMap<String, MovieInfo>()

    // OMDb puan önbelleği — aynı başlığı tekrar sorma.
    private val omdbCache = HashMap<String, OmdbInfo?>()
    suspend fun omdbRatings(title: String, year: String? = null): OmdbInfo? {
        val key = title.trim().lowercase()
        if (omdbCache.containsKey(key)) return omdbCache[key]
        val r = runCatching { OmdbClient.ratings(title, year) }.getOrNull()
        omdbCache[key] = r
        return r
    }

    /** Dizi detayını (sezon/bölüm) tembel çeker + önbellekler. M3U'da null. */
    suspend fun seriesDetail(ref: SeriesRef): Series? {
        seriesCache[ref.id]?.let { return it }
        return client?.seriesInfo(ref)?.also { seriesCache[ref.id] = it }
    }

    /** Film detayını (özet/afiş) tembel çeker + önbellekler. */
    suspend fun movieInfo(ch: Channel): MovieInfo? {
        movieCache[ch.id]?.let { return it }
        return client?.vodInfo(ch)?.also { movieCache[ch.id] = it }
    }

    fun loadM3U(text: String) {
        val result = M3UParser.parse(text)
        if (result.channels.isEmpty()) { _state.value = _state.value.copy(error = "M3U içinde kanal bulunamadı."); return }
        epgSourceUrl = result.epgUrl
        _epg.value = emptyMap()
        _state.value = LibraryState(channels = result.channels, loading = false,
            hasSource = true, parentalOn = settings.parentalEnabled)
        rebuildDiscover()
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

    fun signOut() {
        client = null; creds.clear(); seriesCache.clear(); movieCache.clear(); contentCache.clearAll()
        _state.value = LibraryState(hasSource = false)
    }
}
