package app.cheesino.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheesino.core.Channel
import app.cheesino.core.MediaKind
import app.cheesino.core.SeriesRef
import app.cheesino.data.LibraryViewModel
import app.cheesino.data.ResumeMark
import app.cheesino.ui.theme.Accent
import app.cheesino.ui.theme.Ground
import app.cheesino.ui.theme.Surface
import app.cheesino.ui.theme.TextMute

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Default.Home),
    LIVE("Canlı", Icons.Default.LiveTv),
    MOVIES("Filmler", Icons.Default.Movie),
    SERIES("Diziler", Icons.Default.Tv),
    SEARCH("Ara", Icons.Default.Search)
}

@Composable
fun RootScreen(vm: LibraryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.HOME) }
    // Oynatma kuyruğu — tek öğe (kanal/film) ya da dizi bölümleri (otomatik sonraki).
    var playQueue by remember { mutableStateOf<List<PlayItem>>(emptyList()) }
    var playIndex by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<SeriesRef?>(null) }
    var movieDetail by remember { mutableStateOf<Channel?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showMulti by remember { mutableStateOf(false) }
    var showSports by remember { mutableStateOf(false) }
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
            NavigationBar(containerColor = Surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = Ground,
                            unselectedIconColor = TextMute,
                            unselectedTextColor = TextMute
                        )
                    )
                }
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
                    isFavorite = md.id in user.favorites,
                    rating = ratingOf(md.id),
                    onFavorite = { vm.toggleFavorite(md.id) },
                    onRate = { vm.setRating(md.id, it) },
                    onPlay = { movieDetail = null; playChannel(md) },
                    onBack = { movieDetail = null }
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
                    onBack = { detail = null }
                )
                else -> when (tab) {
                    Tab.HOME -> HomeScreen(state, user, onContent, openSeries, resumePlay,
                        onSettings = { showSettings = true },
                        onSports = { vm.loadEpg(); showSports = true },
                        onRefresh = { vm.reload() })
                    Tab.LIVE -> LiveScreen(state, playChannel,
                        onGuide = { vm.loadEpg(); showGuide = true },
                        onMulti = { showMulti = true })
                    Tab.MOVIES -> MoviesScreen(state, onContent)
                    Tab.SERIES -> SeriesScreen(state, openSeries)
                    Tab.SEARCH -> SearchScreen(state, onContent, openSeries)
                }
            }
        }
    }

    // Rehber — overlay.
    if (showGuide) {
        GuideScreen(
            channels = state.live,
            epg = epg,
            onPlay = { showGuide = false; playChannel(it) },
            onCatchup = { ch, entry ->
                vm.catchupUrl(ch, entry)?.let { url ->
                    showGuide = false
                    playOne(PlayItem("${ch.id}_ts", "${ch.name} · baştan", url, ch.logo, isLive = false))
                }
            },
            onClose = { showGuide = false }
        )
    }

    // Çoklu ekran — overlay.
    if (showMulti) {
        MultiViewScreen(
            channels = state.live,
            initial = remember { vm.loadMultiView() },
            onSave = { vm.saveMultiView(it) },
            onClose = { showMulti = false }
        )
    }

    // Spor merkezi — overlay.
    if (showSports) {
        SportsScreen(
            channels = state.live,
            epg = epg,
            onPlay = { showSports = false; playChannel(it) },
            onClose = { showSports = false }
        )
    }

    // Ayarlar — overlay.
    if (showSettings) {
        SettingsScreen(vm, onClose = { showSettings = false }, onSignedOut = { showSettings = false })
    }

    // Oynatıcı — en üstte. Dizi kuyruğunda bittiğinde otomatik sonraki bölüm.
    playQueue.getOrNull(playIndex)?.let { item ->
        PlayerScreen(
            item = item, vm = vm,
            onClose = { playQueue = emptyList() },
            onEnded = { if (playIndex < playQueue.lastIndex) playIndex++ else playQueue = emptyList() }
        )
    }
}
