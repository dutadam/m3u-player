package app.cheesino.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    MOVIES("Filmler", Icons.Default.Movie),
    SERIES("Diziler", Icons.Default.Tv)
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
    var showMyList by remember { mutableStateOf(false) }
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

    // İlk yükleme — içerik henüz gelmediyse tam ekran loader.
    if (state.channels.isEmpty() && state.loading) {
        CenterLoader("İçerik yükleniyor…")
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
        bottomBar = { BottomBar(tab) { tab = it } }
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
                showMyList -> MyListScreen(state, user, onContent, openSeries, onBack = { showMyList = false })
                showSearch -> SearchScreen(state, onContent, openSeries, onBack = { showSearch = false })
                else -> Crossfade(targetState = tab, label = "tab") { t ->
                    when (t) {
                        Tab.HOME -> HomeScreen(state, user, onContent, openSeries, resumePlay,
                            onSettings = { showSettings = true },
                            onSearch = { showSearch = true },
                            onMyList = { showMyList = true })
                        Tab.LIVE -> LiveScreen(state, epg, playChannel,
                            onGuide = { showGuide = true },
                            onMulti = { showMulti = true },
                            onSports = { vm.loadEpg(); showSports = true })
                        Tab.MOVIES -> MoviesScreen(state, onContent)
                        Tab.SERIES -> SeriesScreen(state, openSeries)
                    }
                }
            }
        }
    }

    // Rehber — overlay (fade).
    AnimatedVisibility(visible = showGuide, enter = fadeIn(), exit = fadeOut()) {
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

    // Spor merkezi — overlay (fade).
    AnimatedVisibility(visible = showSports, enter = fadeIn(), exit = fadeOut()) {
        SportsScreen(
            channels = state.live,
            epg = epg,
            onPlay = { showSports = false; playChannel(it) },
            onClose = { showSports = false }
        )
    }

    // Ayarlar — overlay (fade).
    AnimatedVisibility(visible = showSettings, enter = fadeIn(), exit = fadeOut()) {
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
                    Modifier.clip(RoundedCornerShape(20.dp))
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
