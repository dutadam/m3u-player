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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheesino.core.Channel
import app.cheesino.core.SeriesRef
import app.cheesino.data.LibraryViewModel
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
    var tab by remember { mutableStateOf(Tab.HOME) }
    var playing by remember { mutableStateOf<PlayItem?>(null) }
    var detail by remember { mutableStateOf<SeriesRef?>(null) }

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

    val playChannel: (Channel) -> Unit = { playing = it.toPlayItem() }
    val openSeries: (SeriesRef) -> Unit = { detail = it }

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
            when (tab) {
                Tab.HOME -> HomeScreen(state, playChannel, openSeries)
                Tab.LIVE -> LiveScreen(state, playChannel)
                Tab.MOVIES -> MoviesScreen(state, playChannel)
                Tab.SERIES -> SeriesScreen(state, openSeries)
                Tab.SEARCH -> SearchScreen(state, playChannel, openSeries)
            }
        }
    }

    // Dizi detayı — tam ekran overlay.
    detail?.let { ref ->
        SeriesDetailScreen(
            ref = ref,
            load = { vm.seriesDetail(it) },
            onPlay = { playing = it },
            onBack = { detail = null }
        )
    }

    // Oynatıcı — en üstte.
    playing?.let { item ->
        PlayerScreen(item) { playing = null }
    }
}
