package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.GenreTagger
import app.cheesino.core.MediaKind
import app.cheesino.core.SeriesRef
import app.cheesino.data.LibraryState
import app.cheesino.ui.theme.*

@Composable
fun SearchScreen(
    state: LibraryState,
    onPlay: (Channel) -> Unit,
    onSeries: (SeriesRef) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf<String?>(null) }
    val q = query.trim().lowercase()

    val movies = remember(state.visibleChannels) { state.visibleChannels.filter { it.kind == MediaKind.VOD && it.logo != null } }
    val series = remember(state.visibleSeries) { state.visibleSeries.filter { it.cover != null } }
    val genres = remember(movies, series) {
        val c = HashMap<String, Int>()
        movies.forEach { m -> GenreTagger.tags(m.name, m.group).forEach { c[it] = (c[it] ?: 0) + 1 } }
        series.forEach { s -> GenreTagger.tags(s.name, s.genre, s.group).forEach { c[it] = (c[it] ?: 0) + 1 } }
        c.filter { it.value >= 3 }.keys.sortedBy { GenreTagger.canonical.indexOf(it) }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Kanal, film, dizi ara…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
                focusedLeadingIconColor = Accent, unfocusedLeadingIconColor = TextMute
            )
        )

        // Tür filtresi çipleri (arama boşken keşif için).
        if (q.length < 2 && genres.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Tümü", genre == null) { genre = null } }
                lazyItems(genres) { g -> Chip(g, genre == g) { genre = if (genre == g) null else g } }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                q.length >= 2 -> {
                    val chHits = state.visibleChannels.filter { it.name.lowercase().contains(q) }.take(80)
                    val seHits = series.filter { it.name.lowercase().contains(q) }.take(40)
                    if (chHits.isEmpty() && seHits.isEmpty()) EmptyState("Sonuç yok")
                    else LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (seHits.isNotEmpty()) {
                            header("Diziler")
                            items(seHits) { s -> PosterCard(s.name, s.cover) { onSeries(s) } }
                        }
                        if (chHits.isNotEmpty()) {
                            header("Kanallar & Filmler")
                            items(chHits) { c -> PosterCard(c.name, c.logo) { onPlay(c) } }
                        }
                    }
                }
                genre != null -> {
                    val g = genre!!
                    val gm = movies.filter { GenreTagger.tags(it.name, it.group).contains(g) }
                    val gs = series.filter { GenreTagger.tags(it.name, it.genre, it.group).contains(g) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                        item { PosterRail("$g · Filmler", gm, onPlay) }
                        item { SeriesRail("$g · Diziler", gs, onSeries) }
                    }
                }
                else -> {
                    // Keşfet — öneriler.
                    val topRated = movies.filter { (it.rating ?: 0.0) >= 7.0 }.sortedByDescending { it.rating }
                    val recent = movies.filter { it.added != null }.sortedByDescending { it.added }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                        item { PosterRail("Yüksek Puanlı", topRated, onPlay) }
                        item { PosterRail("Son Eklenenler", recent, onPlay) }
                        item { SeriesRail("Diziler", series, onSeries) }
                        item { PosterRail("Filmler", movies, onPlay) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (active) Accent else Elevated)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = if (active) Ground else TextHi, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.header(title: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    }
}
