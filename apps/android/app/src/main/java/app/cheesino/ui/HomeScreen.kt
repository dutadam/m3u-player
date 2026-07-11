package app.cheesino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.SeriesRef
import app.cheesino.data.LibraryState
import app.cheesino.data.Recommender
import app.cheesino.data.ResumeMark
import app.cheesino.data.UserData
import app.cheesino.ui.theme.TextHi
import app.cheesino.ui.theme.TextMute

@Composable
fun HomeScreen(
    state: LibraryState,
    user: UserData,
    onPlay: (Channel) -> Unit,
    onSeries: (SeriesRef) -> Unit,
    onResume: (ResumeMark) -> Unit,
    onSettings: () -> Unit,
    onSports: () -> Unit
) {
    // Görselsiz + isim tekrarını ele (iOS homeList mantığı)
    fun clean(list: List<Channel>): List<Channel> {
        val seen = HashSet<String>()
        return list.filter { it.logo != null && seen.add(it.name.lowercase()) }
    }

    val continueW = remember(user.resume) { Recommender.continueWatching(user) }
    val recommended = remember(state.visibleChannels, user) { Recommender.recommended(state.visibleChannels, user) }
    val favorites = remember(state.visibleChannels, user.favorites) { clean(Recommender.favorites(state.visibleChannels, user)) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("cheesino", color = TextHi, fontWeight = FontWeight.Black, fontSize = 22.sp,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onSports) { Icon(Icons.Default.SportsSoccer, "Spor", tint = TextMute) }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Ayarlar", tint = TextMute) }
            }
        }
        item { ResumeRail("Devam Et", continueW, onResume) }
        item { PosterRail("Sana Özel", recommended, onPlay) }
        item { PosterRail("Favoriler", favorites, onPlay) }
        item { PosterRail("Son Eklenenler", clean(state.recentlyAdded), onPlay) }
        item { PosterRail("Yüksek Puanlı · IMDb", clean(state.topRated), onPlay) }
        item { PosterRail("Filmler", clean(state.movies), onPlay) }
        item {
            if (state.visibleSeries.isNotEmpty())
                SeriesRail("Diziler", state.visibleSeries.filter { it.cover != null }, onSeries)
        }
        item { ChannelRail("Canlı TV", state.live.take(20), onPlay) }
    }
}
