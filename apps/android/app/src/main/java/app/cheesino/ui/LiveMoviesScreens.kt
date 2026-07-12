package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
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

/** Filmler — üstte arama + kategori rayları (poster'a dokun → detay). */
@Composable
fun MoviesScreen(state: LibraryState, onPlay: (Channel) -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = remember(state.movies) { state.movies.filter { it.logo != null } }
    val query = q.trim().lowercase()
    Column(Modifier.fillMaxSize()) {
        SearchField("Film ara…", q) { q = it }
        if (query.length >= 2) {
            PosterGrid(all.filter { it.name.lowercase().contains(query) }.take(150), onPlay)
        } else {
            val byCat = remember(all) { all.groupBy { it.group } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                byCat.forEach { (cat, list) -> item { PosterRail(cat, list, onPlay) } }
            }
        }
    }
}

/** Diziler — üstte arama + kategori rayları → detay (get_series_info) ekranına gider. */
@Composable
fun SeriesScreen(state: LibraryState, onSeries: (SeriesRef) -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = remember(state.visibleSeries) { state.visibleSeries.filter { it.cover != null } }
    val query = q.trim().lowercase()
    Column(Modifier.fillMaxSize()) {
        SearchField("Dizi ara…", q) { q = it }
        if (query.length >= 2) {
            val hits = all.filter { it.name.lowercase().contains(query) }.take(150)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) { items(hits) { s -> PosterCard(s.name, s.cover) { onSeries(s) } } }
        } else {
            val byCat = remember(all) { all.groupBy { it.group } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                byCat.forEach { (cat, list) -> item { SeriesRail(cat, list, onSeries) } }
            }
        }
    }
}

@Composable
private fun PosterGrid(movies: List<Channel>, onPlay: (Channel) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) { items(movies) { m -> PosterCard(m.name, m.logo) { onPlay(m) } } }
}

@Composable
private fun SearchField(hint: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextHi, unfocusedTextColor = TextHi,
            focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
            focusedLeadingIconColor = Accent, unfocusedLeadingIconColor = TextMute
        )
    )
}
