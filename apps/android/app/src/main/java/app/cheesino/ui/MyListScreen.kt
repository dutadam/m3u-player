package app.cheesino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
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
import app.cheesino.data.UserData
import app.cheesino.ui.theme.*

@Composable
fun MyListScreen(
    state: LibraryState,
    user: UserData,
    onPlay: (Channel) -> Unit,
    onSeries: (SeriesRef) -> Unit,
    onBack: () -> Unit
) {
    val fav = user.favorites
    val movies = remember(state.movies, fav) { state.movies.filter { it.id in fav && it.logo != null } }
    val channels = remember(state.live, fav) { state.live.filter { it.id in fav } }
    val series = remember(state.series, fav) { state.series.filter { "series_${it.id}" in fav && it.cover != null } }
    val empty = movies.isEmpty() && channels.isEmpty() && series.isEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
                Text("Listem", color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
        if (empty) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BookmarkBorder, null, tint = TextMute, modifier = Modifier.size(48.dp))
                    Text("Listen boş", color = TextDim, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.padding(top = 12.dp))
                    Text("Film veya dizi detayında yer imi ekleyerek buraya kaydet.",
                        color = TextMute, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp))
                }
            }
        } else {
            item { PosterRail("Filmler", movies, onPlay) }
            item { SeriesRail("Diziler", series, onSeries) }
            item { ChannelRail("Kanallar", channels, onPlay) }
        }
    }
}
