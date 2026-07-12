package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.GenreTagger
import app.cheesino.core.SeriesRef
import app.cheesino.data.LibraryState
import app.cheesino.ui.theme.*

/** Canlı — kategori bazlı yatay raylar (iOS ile aynı dil). */
@Composable
fun LiveScreen(state: LibraryState, onPlay: (Channel) -> Unit, onGuide: () -> Unit, onMulti: () -> Unit) {
    val byCat = remember(state.live) { state.live.groupBy { it.group } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Canlı", color = TextHi, fontWeight = FontWeight.Black, fontSize = 22.sp,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onMulti) { Icon(Icons.Default.GridView, "Çoklu ekran", tint = Accent) }
                TextButton(onClick = onGuide) {
                    Icon(Icons.Default.CalendarMonth, null, tint = Accent)
                    Text("  Rehber", color = Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        byCat.forEach { (cat, chans) ->
            item { ChannelRail(cat, chans, onPlay) }
        }
    }
}

/** Filmler — tür filtreli poster grid. */
@Composable
fun MoviesScreen(state: LibraryState, onPlay: (Channel) -> Unit) {
    val all = remember(state.movies) { state.movies.filter { it.logo != null } }
    val genres = remember(all) { genresOf(all.map { it.name to it.group }) }
    var selected by remember { mutableStateOf<String?>(null) }
    val shown = remember(all, selected) {
        val g = selected
        if (g == null) all else all.filter { GenreTagger.tags(it.name, it.group).contains(g) }
    }
    Column(Modifier.fillMaxSize()) {
        GenreChips(genres, selected) { selected = it }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(shown) { m -> PosterCard(m.name, m.logo) { onPlay(m) } }
        }
    }
}

/** Diziler — tür filtreli poster grid → detay (get_series_info) ekranına gider. */
@Composable
fun SeriesScreen(state: LibraryState, onSeries: (SeriesRef) -> Unit) {
    val all = remember(state.visibleSeries) { state.visibleSeries.filter { it.cover != null } }
    val genres = remember(all) { genresOf(all.map { it.name to (it.genre ?: it.group) }) }
    var selected by remember { mutableStateOf<String?>(null) }
    val shown = remember(all, selected) {
        val g = selected
        if (g == null) all else all.filter { GenreTagger.tags(it.name, it.genre, it.group).contains(g) }
    }
    Column(Modifier.fillMaxSize()) {
        GenreChips(genres, selected) { selected = it }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(shown) { s -> PosterCard(s.name, s.cover) { onSeries(s) } }
        }
    }
}

/** İçerikte en az 3 kez geçen kanonik türleri döndürür (çok az olan türü gösterme). */
private fun genresOf(nameGroup: List<Pair<String, String>>): List<String> {
    val counts = HashMap<String, Int>()
    nameGroup.forEach { (n, g) -> GenreTagger.tags(n, g).forEach { counts[it] = (counts[it] ?: 0) + 1 } }
    return counts.filter { it.value >= 3 }.keys.sortedBy { GenreTagger.canonical.indexOf(it) }
}

@Composable
private fun GenreChips(genres: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    if (genres.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Chip("Tümü", selected == null) { onSelect(null) } }
        androidx.compose.foundation.lazy.items(genres) { g ->
            Chip(g, selected == g) { onSelect(g) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (active) Accent else Elevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (active) Ground else TextHi, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
