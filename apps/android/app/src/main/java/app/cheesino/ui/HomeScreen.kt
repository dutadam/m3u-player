package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import app.cheesino.core.railKey
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
    onSearch: () -> Unit,
    genres: List<String> = emptyList(),
    onGenre: (String) -> Unit = {},
    weeklyTop: List<Channel> = emptyList(),
    tmdbRails: List<Pair<String, List<Channel>>> = emptyList()
) {
    fun clean(list: List<Channel>): List<Channel> {
        val seenKey = HashSet<String>()
        val seenPoster = HashSet<String>()
        // Aynı filmin kalite/yıl/dil varyantları tek posterde toplanır. Ayrıca aynı poster görseli
        // rayda iki kez görünmez (ad farklı olsa da aynı içerik → kullanıcının "aynı poster" şikâyeti).
        return list.filter { c ->
            val logo = c.logo ?: return@filter false
            val keyOk = seenKey.add(railKey(c.name))
            val posterOk = seenPoster.add(logo)
            keyOk && posterOk
        }
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
        // Hero altı hızlı tür filtresi → Katalog'a yönlendirir.
        if (genres.isNotEmpty()) item { GenreChipRow(genres, onGenre) }
        // Öncelikli raylar üstte.
        item { ResumeRail("Devam Et", continueW, onResume) }
        item { PosterRail("Sana Özel", recommended, onPlay) }
        item { PosterRail("Daha Sonra İzle", favorites, onPlay) }
        if (weeklyTop.isNotEmpty()) item { RankedRail("Haftanın Trendleri · Top 10", weeklyTop, onPlay) }
        item { RankedRail("Yüksek Puanlı · Top 10", clean(state.topRated), onPlay) }
        // TMDB keşif rayları (Dünyada Popüler, Vizyondakiler) — kütüphaneyle eşleşen.
        tmdbRails.forEach { (title, list) -> item { PosterRail(title, clean(list), onPlay) } }
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

/** Hero altı hızlı tür çipleri — dokununca Katalog'da o kategoriyi açar. */
@Composable
private fun GenreChipRow(genres: List<String>, onGenre: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(genres.take(14)) { g ->
            Text(
                g, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Elevated)
                    .clickable { onGenre(g) }.padding(horizontal = 14.dp, vertical = 8.dp)
            )
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
    // Dikey (2:3) büyük hero — ekranı neredeyse kaplar, poster kırpılmaz.
    Box(
        Modifier.fillMaxWidth().aspectRatio(2f / 3f).padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp)).background(Elevated)
            .clickable { onPlay(item) }
    ) {
        item.logo?.let {
            AsyncImage(it, item.name, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        // Güçlü alt degrade → başlık/buton okunur.
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.94f))
        ))
        // Sayfa noktaları (üstte).
        if (items.size > 1) Row(
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items.forEachIndexed { i, _ ->
                Box(Modifier.height(4.dp).width(if (i == idx) 16.dp else 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i == idx) Accent else Color.White.copy(alpha = 0.4f)))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(item.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 28.sp)
            item.rating?.takeIf { it > 0 }?.let {
                Text("★ ${"%.1f".format(it)}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Accent)
                    .clickable { onPlay(item) }.padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Ground)
                Text("Oynat", color = Ground, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
