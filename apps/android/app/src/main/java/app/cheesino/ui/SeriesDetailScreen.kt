package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Episode
import app.cheesino.core.Season
import app.cheesino.core.Series
import app.cheesino.core.SeriesRef
import app.cheesino.ui.theme.*
import coil.compose.AsyncImage

@Composable
fun SeriesDetailScreen(
    ref: SeriesRef,
    load: suspend (SeriesRef) -> Series?,
    onPlay: (PlayItem) -> Unit,
    onBack: () -> Unit
) {
    var series by remember(ref.id) { mutableStateOf<Series?>(null) }
    var loading by remember(ref.id) { mutableStateOf(true) }
    var selectedSeason by remember(ref.id) { mutableIntStateOf(0) }

    LaunchedEffect(ref.id) {
        loading = true
        series = load(ref)
        selectedSeason = series?.seasons?.firstOrNull()?.number ?: 0
        loading = false
    }

    Box(Modifier.fillMaxSize().background(Ground)) {
        val s = series
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
            s == null || s.seasons.isEmpty() -> Text(
                "Bölüm bilgisi alınamadı.", color = TextDim,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> SeriesContent(s, selectedSeason, { selectedSeason = it }, onPlay)
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi)
        }
    }
}

@Composable
private fun SeriesContent(
    s: Series,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    onPlay: (PlayItem) -> Unit
) {
    val season = s.seasons.firstOrNull { it.number == selectedSeason } ?: s.seasons.first()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(s) }
        if (s.seasons.size > 1) {
            item { SeasonPicker(s.seasons, selectedSeason, onSelectSeason) }
        }
        items(season.episodes) { ep ->
            EpisodeRow(ep) {
                onPlay(PlayItem(
                    id = "ep_${ep.id}",
                    title = "${s.name} — ${ep.title}",
                    url = ep.url,
                    poster = s.cover,
                    group = s.genre,
                    isLive = false,
                    isSeries = true
                ))
            }
        }
    }
}

@Composable
private fun Header(s: Series) {
    Row(Modifier.padding(start = 56.dp, top = 12.dp, end = 16.dp)) {
        Box(
            Modifier.size(96.dp, 144.dp).clip(RoundedCornerShape(10.dp)).background(Elevated),
            contentAlignment = Alignment.Center
        ) {
            if (s.cover != null) AsyncImage(s.cover, s.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(s.name, color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2)
            s.genre?.let { Text(it, color = Accent2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            s.plot?.let {
                Text(it, color = TextDim, fontSize = 13.sp, maxLines = 6,
                    modifier = Modifier.padding(top = 8.dp).verticalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
private fun SeasonPicker(seasons: List<Season>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(seasons) { season ->
            val active = season.number == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) Accent else Elevated)
                    .clickable { onSelect(season.number) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Sezon ${season.number}", color = if (active) Ground else TextHi,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(8.dp)).background(Elevated),
            contentAlignment = Alignment.Center
        ) {
            if (ep.thumb != null) AsyncImage(ep.thumb, ep.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.PlayArrow, null, tint = TextMute)
        }
        Text("${ep.episodeNum}. ${ep.title}", color = TextHi, fontSize = 14.sp, maxLines = 2,
            modifier = Modifier.padding(start = 12.dp).weight(1f))
        Icon(Icons.Default.PlayArrow, "Oynat", tint = Accent)
    }
}
