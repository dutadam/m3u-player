package app.cheesino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
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
import app.cheesino.ui.theme.Accent
import app.cheesino.ui.theme.TextHi

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

/** Filmler — poster grid. */
@Composable
fun MoviesScreen(state: LibraryState, onPlay: (Channel) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(state.movies.filter { it.logo != null }) { m ->
            PosterCard(m.name, m.logo) { onPlay(m) }
        }
    }
}

/** Diziler — poster grid → detay (get_series_info) ekranına gider. */
@Composable
fun SeriesScreen(state: LibraryState, onSeries: (SeriesRef) -> Unit) {
    val withCover = remember(state.visibleSeries) { state.visibleSeries.filter { it.cover != null } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(withCover) { s ->
            PosterCard(s.name, s.cover) { onSeries(s) }
        }
    }
}
