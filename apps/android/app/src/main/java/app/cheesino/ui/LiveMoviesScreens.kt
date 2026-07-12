package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.EpgEntry
import app.cheesino.core.GenreTagger
import app.cheesino.core.SeriesRef
import app.cheesino.data.LibraryState
import app.cheesino.ui.theme.*
import coil.compose.AsyncImage

/** Canlı — ray/liste geçişli; her kanalda EPG'den "şimdi oynuyor". */
@Composable
fun LiveScreen(
    state: LibraryState,
    epg: Map<String, List<EpgEntry>>,
    onPlay: (Channel) -> Unit,
    onGuide: () -> Unit,
    onMulti: () -> Unit
) {
    var listMode by remember { mutableStateOf(false) }
    val byCat = remember(state.live) { state.live.groupBy { it.group } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Canlı", color = TextHi, fontWeight = FontWeight.Black, fontSize = 22.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { listMode = !listMode }) {
                Icon(if (listMode) Icons.Default.ViewModule else Icons.Default.ViewList,
                    "Görünüm", tint = Accent)
            }
            IconButton(onClick = onMulti) { Icon(Icons.Default.GridView, "Çoklu ekran", tint = Accent) }
            TextButton(onClick = onGuide) {
                Icon(Icons.Default.CalendarMonth, null, tint = Accent)
                Text("  Rehber", color = Accent, fontWeight = FontWeight.Bold)
            }
        }
        when {
            byCat.isEmpty() -> EmptyState("Canlı kanal yok", "Bu kaynakta canlı yayın görünmüyor.",
                modifier = Modifier.weight(1f).fillMaxWidth())
            listMode -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)) {
                byCat.forEach { (cat, chans) ->
                    item { CategoryHeader(cat) }
                    lazyItems(chans) { ch ->
                        val list = ch.tvgId?.let { epg[it] }
                        val now = list?.firstOrNull { it.isLiveNow }
                        val next = list?.firstOrNull { it.start > (now?.stop ?: 0L) }
                        LiveListRow(ch, now?.title, next?.title, onPlay)
                    }
                }
            }
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                byCat.forEach { (cat, chans) -> item { LiveRail(cat, chans, epg, onPlay) } }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
        Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LiveListRow(ch: Channel, now: String?, next: String?, onPlay: (Channel) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onPlay(ch) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(Elevated),
            contentAlignment = Alignment.Center
        ) {
            if (ch.logo != null) AsyncImage(ch.logo, ch.name, Modifier.padding(6.dp).fillMaxSize(), contentScale = ContentScale.Fit)
            else Text(ch.name.take(2).uppercase(), color = TextHi, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(ch.name, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now ?: "Program bilgisi yok", color = if (now != null) Accent2 else TextMute,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            next?.let {
                Text("Sırada · $it", color = TextDim, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        ch.quality?.label?.let {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Accent).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text(it, color = Ground, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun LiveRail(title: String, channels: List<Channel>, epg: Map<String, List<EpgEntry>>, onPlay: (Channel) -> Unit) {
    if (channels.isEmpty()) return
    Rail(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            lazyItems(channels.take(30)) { ch ->
                val now = ch.tvgId?.let { epg[it] }?.firstOrNull { it.isLiveNow }?.title
                LiveChannelCard(ch, now) { onPlay(ch) }
            }
        }
    }
}

@Composable
private fun LiveChannelCard(ch: Channel, now: String?, onTap: () -> Unit) {
    Column(Modifier.focusHighlight(14).padding(end = 11.dp).width(150.dp).clickable(onClick = onTap)) {
        Box(
            Modifier.size(150.dp, 86.dp).clip(RoundedCornerShape(14.dp)).background(Elevated)
                .border(1.dp, LineSoft.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (ch.logo != null) AsyncImage(ch.logo, ch.name, Modifier.padding(14.dp).fillMaxSize(), contentScale = ContentScale.Fit)
            else Text(ch.name.take(2).uppercase(), color = TextHi, fontWeight = FontWeight.Black)
            ch.quality?.label?.let {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(6.dp))
                        .background(Accent).padding(horizontal = 5.dp, vertical = 1.dp)
                ) { Text(it, color = Ground, fontSize = 9.sp, fontWeight = FontWeight.Black) }
            }
        }
        Text(ch.name, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(now ?: "Program bilgisi yok", color = if (now != null) Accent2 else TextMute, fontSize = 10.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Filmler — üstte arama + kategori rayları (poster'a dokun → detay). */
@Composable
fun MoviesScreen(state: LibraryState, onPlay: (Channel) -> Unit) {
    var q by remember { mutableStateOf("") }
    var seeAll by remember { mutableStateOf<Pair<String, List<Channel>>?>(null) }
    val all = remember(state.movies) { state.movies.filter { it.logo != null } }
    val query = q.trim().lowercase()

    val sa = seeAll
    if (sa != null) {
        CategoryGrid(sa.first, sa.second.map { CardItem(it.name, it.logo) { onPlay(it) } }) { seeAll = null }
        return
    }

    Column(Modifier.fillMaxSize()) {
        SearchField("Film ara…", q) { q = it }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                all.isEmpty() -> EmptyState("Film bulunamadı", "Bu kaynakta film kategorisi görünmüyor.")
                query.length >= 2 -> {
                    val hits = all.filter { it.name.lowercase().contains(query) }.take(150)
                    if (hits.isEmpty()) EmptyState("Sonuç yok") else PosterGrid(hits, onPlay)
                }
                else -> {
                    val byCat = remember(all) { all.groupBy { it.group } }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                        byCat.forEach { (cat, list) ->
                            item { PosterRail(cat, list, onPlay, onSeeAll = { seeAll = cat to list }) }
                        }
                    }
                }
            }
        }
    }
}

/** Diziler — üstte arama + kategori rayları → detay (get_series_info) ekranına gider. */
@Composable
fun SeriesScreen(state: LibraryState, onSeries: (SeriesRef) -> Unit) {
    var q by remember { mutableStateOf("") }
    var seeAll by remember { mutableStateOf<Pair<String, List<SeriesRef>>?>(null) }
    val all = remember(state.visibleSeries) { state.visibleSeries.filter { it.cover != null } }
    val query = q.trim().lowercase()

    val sa = seeAll
    if (sa != null) {
        CategoryGrid(sa.first, sa.second.map { CardItem(it.name, it.cover) { onSeries(it) } }) { seeAll = null }
        return
    }

    Column(Modifier.fillMaxSize()) {
        SearchField("Dizi ara…", q) { q = it }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                all.isEmpty() -> EmptyState("Dizi bulunamadı", "Bu kaynakta dizi kategorisi görünmüyor.")
                query.length >= 2 -> {
                    val hits = all.filter { it.name.lowercase().contains(query) }.take(150)
                    if (hits.isEmpty()) EmptyState("Sonuç yok")
                    else LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) { items(hits) { s -> PosterCard(s.name, s.cover) { onSeries(s) } } }
                }
                else -> {
                    val byCat = remember(all) { all.groupBy { it.group } }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                        byCat.forEach { (cat, list) ->
                            item { SeriesRail(cat, list, onSeries, onSeeAll = { seeAll = cat to list }) }
                        }
                    }
                }
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

private class CardItem(val name: String, val poster: String?, val onClick: () -> Unit)

/** Bir kategorinin tüm içeriği — "Tümü" ile açılan poster grid + geri. */
@Composable
private fun CategoryGrid(title: String, cards: List<CardItem>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text(title, color = TextHi, fontWeight = FontWeight.Black, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) { items(cards) { c -> PosterCard(c.name, c.poster, null, c.onClick) } }
    }
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
