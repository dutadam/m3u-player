package app.cheesino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
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
    val q = query.trim().lowercase()

    val channelHits = remember(q, state.visibleChannels) {
        if (q.length < 2) emptyList() else state.visibleChannels.filter { it.name.lowercase().contains(q) }.take(60)
    }
    val seriesHits = remember(q, state.visibleSeries) {
        if (q.length < 2) emptyList() else state.visibleSeries.filter { it.name.lowercase().contains(q) }.take(30)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Kanal, film, dizi ara…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
                focusedLeadingIconColor = Accent, unfocusedLeadingIconColor = TextMute
            )
        )

        if (q.length < 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("En az 2 harf yaz.", color = TextMute)
            }
            return
        }
        if (channelHits.isEmpty() && seriesHits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sonuç yok.", color = TextMute)
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (seriesHits.isNotEmpty()) {
                header("Diziler")
                items(seriesHits) { s -> PosterCard(s.name, s.cover) { onSeries(s) } }
            }
            if (channelHits.isNotEmpty()) {
                header("Kanallar & Filmler")
                items(channelHits) { c -> PosterCard(c.name, c.logo) { onPlay(c) } }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.header(title: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    }
}
