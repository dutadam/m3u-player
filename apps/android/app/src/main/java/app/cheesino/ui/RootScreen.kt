package app.cheesino.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheesino.core.Channel
import app.cheesino.core.MediaKind
import app.cheesino.core.SeriesRef
import app.cheesino.data.LibraryViewModel
import app.cheesino.data.ResumeMark
import app.cheesino.ui.theme.Accent
import app.cheesino.ui.theme.Elevated
import app.cheesino.ui.theme.Ground
import app.cheesino.ui.theme.LineSoft
import app.cheesino.ui.theme.TextMute

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Default.Home),
    LIVE("Canlı", Icons.Default.LiveTv),
    CATALOG("Katalog", Icons.Default.Movie),
    LIBRARY("Kitaplığım", Icons.Default.VideoLibrary),
    ACCOUNT("Account", Icons.Default.AccountCircle)
}

@Composable
fun RootScreen(vm: LibraryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val discover by vm.discover.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val proPrice by vm.proPrice.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val recordingActive by vm.recordingActive.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? android.app.Activity
    var paywallFor by remember { mutableStateOf<app.cheesino.data.ProFeature?>(null) }
    var showPaywall by remember { mutableStateOf(false) }
    // Sekme uygulamaya geri dönüşte/ekran dönmede korunur (ana sayfaya atmasın).
    var tabOrdinal by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabOrdinal]
    // Sekme-içi segmentler (Canlı: TV/Rehber/Spor/Çoklu · Katalog: Filmler/Diziler · Kitaplığım: İndirilenler/Kayıtlar/Listem).
    var liveSeg by rememberSaveable { mutableIntStateOf(0) }
    var catSeg by rememberSaveable { mutableIntStateOf(0) }
    var catGenre by rememberSaveable { mutableIntStateOf(0) }   // Katalog kategori/mood filtresi (0 = Tümü)
    var libSeg by rememberSaveable { mutableIntStateOf(0) }
    // Oynatma kuyruğu — tek öğe (kanal/film) ya da dizi bölümleri (otomatik sonraki).
    var playQueue by remember { mutableStateOf<List<PlayItem>>(emptyList()) }
    var playIndex by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<SeriesRef?>(null) }
    var movieDetail by remember { mutableStateOf<Channel?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    val epg by vm.epg.collectAsStateWithLifecycle()

    // Kaynak yoksa onboarding tam ekran.
    if (!state.hasSource) {
        OnboardingScreen(
            state = state,
            onXtream = { vm.loadXtream(it) },
            onM3U = { url ->
                if (url.startsWith("http", true)) vm.loadM3UUrl(url) else vm.loadM3U(url)
            }
        )
        return
    }

    // İlk yükleme — içerik henüz gelmediyse iskelet (shimmer) göster; spinner beklemek yerine
    // içerik geliyormuş hissi → daha az bekliyormuş algısı.
    if (state.channels.isEmpty() && state.loading) {
        HomeSkeleton()
        return
    }

    // Canlı sekmesine girince EPG'yi (rehber + şimdi oynuyor) tembel yükle.
    LaunchedEffect(tab) { if (tab == Tab.LIVE) vm.loadEpg() }

    fun playOne(item: PlayItem) { playQueue = listOf(item); playIndex = 0 }
    fun ratingOf(id: String) = when { id in user.likes -> 1; id in user.dislikes -> -1; else -> 0 }
    val watchedIds = remember(user.history) { user.history.mapTo(HashSet()) { it.id } }
    val playChannel: (Channel) -> Unit = { playOne(it.toPlayItem()) }
    // İçerik dokunuşu: film → detay, kanal → doğrudan oynat.
    val onContent: (Channel) -> Unit = { ch -> if (ch.kind == MediaKind.VOD) movieDetail = ch else playChannel(ch) }
    val openSeries: (SeriesRef) -> Unit = { detail = it }
    // Devam Et: dizi ise detay ekranına git, film ise doğrudan oynat.
    val resumePlay: (ResumeMark) -> Unit = { m ->
        if (m.isSeries && m.seriesId != null)
            detail = SeriesRef(m.seriesId, m.seriesName ?: m.title, m.poster, null, "")
        else playOne(PlayItem(m.id, m.title, m.url, m.poster, isLive = false, isSeries = m.isSeries))
    }

    Scaffold(
        containerColor = Ground,
        bottomBar = {
            // Sekmeye dokununca açık detay/arama/liste katmanını kapat → gezinme takılmasın.
            BottomBar(tab) { t ->
                tabOrdinal = t.ordinal; detail = null; movieDetail = null; showSearch = false
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val md = movieDetail
            val sd = detail
            when {
                // Detay ekranları scaffold içinde → alt navigasyon görünür kalır.
                md != null -> MovieDetailScreen(
                    channel = md,
                    load = { vm.movieInfo(it) },
                    omdb = { t, y -> vm.omdbRatings(t, y) },
                    isFavorite = md.id in user.favorites,
                    rating = ratingOf(md.id),
                    onFavorite = { vm.toggleFavorite(md.id) },
                    onRate = { vm.setRating(md.id, it) },
                    onPlay = { movieDetail = null; playChannel(md) },
                    onBack = { movieDetail = null },
                    downloadState = downloads.firstOrNull { it.request.id == md.id }?.state,
                    onDownload = { vm.download(md.id, md.url, md.name) },
                    onRemoveDownload = { vm.removeDownload(md.id) }
                )
                sd != null -> SeriesDetailScreen(
                    ref = sd,
                    load = { vm.seriesDetail(it) },
                    resumeFor = { user.resume[it] },
                    watchedIds = watchedIds,
                    favorite = "series_${sd.id}" in user.favorites,
                    rating = ratingOf("series_${sd.id}"),
                    onFavorite = { vm.toggleFavorite("series_${sd.id}") },
                    onRate = { vm.setRating("series_${sd.id}", it) },
                    onPlayQueue = { queue, i -> playQueue = queue; playIndex = i },
                    onBack = { detail = null },
                    omdb = { t, y -> vm.omdbRatings(t, y) },
                    downloadStateFor = { key -> downloads.firstOrNull { it.request.id == key }?.state },
                    onDownloadEpisode = { item -> vm.download(item.id, item.url, item.title) },
                    onRemoveDownload = { vm.removeDownload(it) }
                )
                showSearch -> SearchScreen(state, discover.genres, onContent, openSeries, onBack = { showSearch = false })
                else -> Crossfade(targetState = tab, label = "tab") { t ->
                    when (t) {
                        Tab.HOME -> HomeScreen(state, user, discover.recommended, discover.movieRails, discover.seriesRails,
                            onContent, openSeries, resumePlay,
                            onSearch = { showSearch = true },
                            genres = discover.genres,
                            onGenre = { g ->
                                tabOrdinal = Tab.CATALOG.ordinal; catSeg = 0
                                val gi = discover.genres.take(14).indexOf(g)
                                catGenre = if (gi >= 0) gi + 1 else 0
                            })

                        // CANLI — üstte TV · Rehber · Spor · Çoklu segmenti.
                        Tab.LIVE -> Column(Modifier.fillMaxSize()) {
                            SegmentBar(listOf("Kanallar", "Rehber", "Spor", "Çoklu"), liveSeg) { liveSeg = it }
                            when (liveSeg) {
                                0 -> LiveScreen(state, epg, playChannel,
                                    onGuide = { liveSeg = 1 },
                                    onMulti = {
                                        if (isPro) liveSeg = 3
                                        else { paywallFor = app.cheesino.data.ProFeature.MULTI_VIEW; showPaywall = true }
                                    },
                                    onSports = { vm.loadEpg(); liveSeg = 2 })
                                1 -> GuideScreen(channels = state.live, epg = epg,
                                    onPlay = playChannel,
                                    onCatchup = { ch, entry ->
                                        vm.catchupUrl(ch, entry)?.let { url ->
                                            playOne(PlayItem("${ch.id}_ts", "${ch.name} · baştan", url, ch.logo, isLive = false))
                                        }
                                    },
                                    onClose = { liveSeg = 0 },
                                    onToggleReminder = { ch, e -> vm.toggleReminder(ch, e) },
                                    isReminded = { ch, e -> vm.isReminded(ch, e) }, embedded = true)
                                2 -> SportsScreen(channels = state.live, epg = epg, onPlay = playChannel, onClose = { liveSeg = 0 }, embedded = true)
                                else -> MultiViewScreen(channels = state.live,
                                    initial = remember { vm.loadMultiView() },
                                    onSave = { vm.saveMultiView(it) }, onClose = { liveSeg = 0 })
                            }
                        }

                        // KATALOG — üstte Filmler/Diziler + arama, altında Kategori/Mood filtresi.
                        Tab.CATALOG -> Column(Modifier.fillMaxSize()) {
                            SegmentBar(listOf("Filmler", "Diziler"), catSeg, onSearch = { showSearch = true }) { catSeg = it; catGenre = 0 }
                            val genreItems = remember(discover.genres) { listOf("Tümü") + discover.genres.take(14) }
                            if (genreItems.size > 1)
                                SegmentBar(genreItems, catGenre.coerceIn(0, genreItems.lastIndex), topInset = false) { catGenre = it }
                            val g = genreItems.getOrNull(catGenre)?.takeIf { catGenre > 0 }
                            when {
                                g == null && catSeg == 0 -> MoviesScreen(state, discover.movieRails, onContent)
                                g == null -> SeriesScreen(state, discover.seriesRails, openSeries)
                                catSeg == 0 -> FilteredPosterGrid(
                                    remember(g, state.movies) { state.movies.filter { g in app.cheesino.core.GenreTagger.tags(it.name, it.group) } },
                                    onContent)
                                else -> FilteredSeriesGrid(
                                    remember(g, state.visibleSeries) { state.visibleSeries.filter { g in app.cheesino.core.GenreTagger.tags(it.name, it.genre, it.group) } },
                                    openSeries)
                            }
                        }

                        // KİTAPLIĞIM — İndirilenler · Kayıtlar · Listem.
                        Tab.LIBRARY -> Column(Modifier.fillMaxSize()) {
                            SegmentBar(listOf("İndirilenler", "Kayıtlar", "Listem"), libSeg) { libSeg = it }
                            when (libSeg) {
                                0 -> DownloadsScreen(downloads = downloads, onPlay = { playOne(it) },
                                    onRemove = { vm.removeDownload(it) }, onRefresh = { vm.refreshDownloads() }, onClose = {}, embedded = true)
                                1 -> RecordingsScreen(recordings = recordings, active = recordingActive,
                                    onPlay = { playOne(it) }, onDelete = { vm.deleteRecording(it) },
                                    onStopActive = { vm.stopRecording() }, onRefresh = { vm.refreshRecordings() }, onClose = {}, embedded = true)
                                else -> MyListScreen(state, user, onContent, openSeries, onBack = {}, embedded = true)
                            }
                        }

                        // ACCOUNT — profil + ayarlar (giriş ileride).
                        Tab.ACCOUNT -> SettingsScreen(vm, onClose = {}, onSignedOut = {},
                            onUpgrade = { paywallFor = null; showPaywall = true },
                            onOpenDownloads = { tabOrdinal = Tab.LIBRARY.ordinal; libSeg = 0 },
                            onOpenRecordings = { tabOrdinal = Tab.LIBRARY.ordinal; libSeg = 1 }, embedded = true)
                    }
                }
            }
        }
    }

    // Pro paywall — overlay (fade).
    AnimatedVisibility(visible = showPaywall, enter = fadeIn(), exit = fadeOut()) {
        PaywallScreen(
            highlight = paywallFor,
            priceText = proPrice,
            onUpgrade = {
                // Play satın alma ekranını aç; sonuç Entitlements üzerinden isPro'yu günceller.
                activity?.let { vm.purchasePro(it) }
                showPaywall = false
            },
            onRestore = { vm.restorePurchases() },
            onClose = { showPaywall = false }
        )
    }

    // Oynatıcı — en üstte. Dizi kuyruğunda bittiğinde otomatik sonraki bölüm.
    playQueue.getOrNull(playIndex)?.let { item ->
        PlayerHost(
            item = item, vm = vm,
            onClose = { playQueue = emptyList() },
            onEnded = { if (playIndex < playQueue.lastIndex) playIndex++ else playQueue = emptyList() }
        )
    }
}

/**
 * Oynatıcı motorunu seçer:
 *  0 Otomatik → film/dizi (VOD) VLC (geniş codec), canlı ExoPlayer (Cast/PiP/düşük gecikme);
 *  1 ExoPlayer her zaman; 2 VLC her zaman.
 * Otomatik/ExoPlayer'da ExoPlayer oynatamazsa VLC'ye düşer.
 */
@Composable
private fun PlayerHost(item: PlayItem, vm: LibraryViewModel, onClose: () -> Unit, onEnded: () -> Unit) {
    val engine = vm.playerEngine
    // İndirilmiş öğe disk cache'inden ExoPlayer ile oynar (çevrimdışı) — VLC cache'i okumaz.
    val downloaded = remember(item.id) { vm.isDownloaded(item.id) }
    var useVlc by remember(item.id) { mutableStateOf(!downloaded && (engine == 2 || (engine == 0 && !item.isLive))) }
    if (useVlc) {
        VlcPlayerScreen(item, vm, onClose, onEnded)
    } else {
        PlayerScreen(item, vm, onClose, onEnded,
            // Çevrimdışı öğede VLC'ye düşme (kaynak URL'i offline erişilemez).
            onFallback = if (!downloaded && engine == 0) ({ useVlc = true }) else null)
    }
}

/** Kategori filtresi seçilince gösterilen film grid'i. */
@Composable
private fun FilteredPosterGrid(items: List<Channel>, onTap: (Channel) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bu kategoride içerik yok.", color = TextMute, fontSize = 14.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 90.dp)
    ) {
        items(items) { ch ->
            val rating = ch.rating?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" }
            PosterCard(ch.name, ch.logo, badge = rating, quality = ch.quality?.label) { onTap(ch) }
        }
    }
}

/** Kategori filtresi seçilince gösterilen dizi grid'i. */
@Composable
private fun FilteredSeriesGrid(items: List<SeriesRef>, onTap: (SeriesRef) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bu kategoride içerik yok.", color = TextMute, fontSize = 14.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 90.dp)
    ) {
        items(items) { s ->
            val rating = s.rating?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" }
            PosterCard(s.name, s.cover, badge = rating) { onTap(s) }
        }
    }
}

/** Sekme-içi segment çubuğu — aktif segment accent pill; opsiyonel sağda arama ikonu (Katalog). */
@Composable
private fun SegmentBar(items: List<String>, selected: Int, onSearch: (() -> Unit)? = null,
                      topInset: Boolean = true, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (topInset) Modifier.statusBarsPadding() else Modifier)
            .padding(start = 14.dp, end = 4.dp, top = if (topInset) 8.dp else 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { i, label ->
                val on = i == selected
                Text(
                    label,
                    color = if (on) Ground else TextMute,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (on) Accent else Elevated)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Ara", tint = TextMute) }
    }
}

/** Modern yüzen sekme çubuğu — aktif sekme accent pill'e genişler (icon+etiket), diğerleri sadece icon. */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Elevated)
                .border(1.dp, LineSoft.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Row(
                    Modifier.focusHighlight(20).clip(RoundedCornerShape(20.dp))
                        .background(if (on) Accent else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { onSelect(t) }
                        .padding(horizontal = if (on) 16.dp else 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(t.icon, t.label, tint = if (on) Ground else TextMute, modifier = Modifier.size(22.dp))
                    if (on) Text(t.label, color = Ground, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
    }
}
