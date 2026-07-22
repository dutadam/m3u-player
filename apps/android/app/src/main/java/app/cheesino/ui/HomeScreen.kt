package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.cheesino.core.Channel
import app.cheesino.core.SeriesRef
import app.cheesino.core.baseTitle
import app.cheesino.data.LibraryState
import app.cheesino.data.Recommender
import app.cheesino.data.ResumeMark
import app.cheesino.data.UserData
import app.cheesino.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: LibraryState,
    user: UserData,
    recommendedIn: List<Channel>,
    movieRails: List<Pair<String, List<Channel>>>,
    seriesRails: List<Pair<String, List<SeriesRef>>>,
    onPlay: (Channel) -> Unit,
    onSeries: (SeriesRef) -> Unit,
    onResume: (ResumeMark) -> Unit,
    onSearch: () -> Unit
) {
    fun clean(list: List<Channel>): List<Channel> {
        val seen = HashSet<String>()
        // Aynı filmin 4K/FHD/HD/HEVC gibi varyantları tek posterde toplanır.
        return list.filter { it.logo != null && seen.add(baseTitle(it.name)) }
    }

    // Bunların hepsi ucuz (filter) — ağır iş (öneri/raylar) arka planda önceden hesaplandı.
    val continueW = remember(user.resume) { Recommender.continueWatching(user) }
    val recommended = remember(recommendedIn) { clean(recommendedIn) }
    val favorites = remember(state.visibleChannels, user.favorites) { clean(Recommender.favorites(state.visibleChannels, user)) }
    val featured = remember(recommended, state.topRated) {
        (recommended + clean(state.topRated)).distinctBy { it.name.lowercase() }.filter { it.logo != null }.take(6)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 36.dp)
                // Arka planda içerik tazeleniyorsa küçük gösterge.
                if (state.refreshing) CircularProgressIndicator(
                    modifier = Modifier.padding(start = 10.dp).size(16.dp),
                    color = Accent, strokeWidth = 2.dp
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Ara", tint = TextMute) }
            }
        }
        if (featured.isNotEmpty()) item { Hero(featured, onPlay) }
        // Öncelikli raylar üstte.
        item { ResumeRail("Devam Et", continueW, onResume) }
        item { PosterRail("Sana Özel", recommended, onPlay) }
        item { PosterRail("Daha Sonra İzle", favorites, onPlay) }
        item { RankedRail("Bu Hafta Top 10", clean(state.topRated), onPlay) }
        item { PosterRail("Son Eklenenler", clean(state.recentlyAdded), onPlay) }
        // Dinamik tür/senaryo rayları — film ve dizi karışık.
        movieRails.take(6).forEach { (title, list) -> item { PosterRail(title, clean(list), onPlay) } }
        seriesRails.take(4).forEach { (title, list) -> item { SeriesRail(title, list, onSeries) } }
        // Genel katalog en altta.
        item { PosterRail("Tüm Filmler", clean(state.movies), onPlay) }
        item {
            if (state.visibleSeries.isNotEmpty())
                SeriesRail("Tüm Diziler", state.visibleSeries.filter { it.cover != null }, onSeries)
        }
    }
}

/** Öne çıkan içerik — 6 sn'de bir dönen büyük hero. */
@Composable
private fun Hero(items: List<Channel>, onPlay: (Channel) -> Unit) {
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(items) {
        if (items.size > 1) while (true) { delay(6000); idx = (idx + 1) % items.size }
    }
    val item = items[idx % items.size]
    Box(
        Modifier.fillMaxWidth().height(210.dp).padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Elevated)
            .clickable { onPlay(item) }
    ) {
        item.logo?.let {
            // Posteri üst-merkezden kırparak tüm hero'yu doldur (yan bulanık şeritler yok).
            AsyncImage(it, item.name, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        // Alt + yan karartma → başlık okunur, kenarlar yumuşar.
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent, Color.Black.copy(alpha = 0.9f)))
        ))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(item.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(
                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Accent)
                    .clickable { onPlay(item) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Ground)
                Text("Oynat", color = Ground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
