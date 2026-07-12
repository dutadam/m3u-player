package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Episode
import app.cheesino.core.Season
import app.cheesino.core.Series
import app.cheesino.core.SeriesRef
import app.cheesino.data.ResumeMark
import app.cheesino.ui.theme.*
import coil.compose.AsyncImage

@Composable
fun SeriesDetailScreen(
    ref: SeriesRef,
    load: suspend (SeriesRef) -> Series?,
    resumeFor: (String) -> ResumeMark?,
    watchedIds: Set<String>,
    favorite: Boolean,
    rating: Int,
    onFavorite: () -> Unit,
    onRate: (Int) -> Unit,
    onPlayQueue: (List<PlayItem>, Int) -> Unit,
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
            else -> SeriesContent(s, selectedSeason, { selectedSeason = it }, resumeFor, watchedIds,
                favorite, rating, onFavorite, onRate, onPlayQueue)
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp).align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi)
        }
    }
}

@Composable
private fun SeriesContent(
    s: Series,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    resumeFor: (String) -> ResumeMark?,
    watchedIds: Set<String>,
    favorite: Boolean,
    rating: Int,
    onFavorite: () -> Unit,
    onRate: (Int) -> Unit,
    onPlayQueue: (List<PlayItem>, Int) -> Unit
) {
    val season = s.seasons.firstOrNull { it.number == selectedSeason } ?: s.seasons.first()
    val queue = remember(season, s.name) {
        season.episodes.map { ep ->
            PlayItem("ep_${ep.id}", "${s.name} — ${ep.title}", ep.url, s.cover, s.genre,
                false, true, s.id.toIntOrNull(), s.name)
        }
    }
    // Devam edilecek bölüm: yarım kalan ya da son izlenenin bir sonrası.
    val resumeIndex = run {
        val ip = season.episodes.indexOfFirst { resumeFor("ep_${it.id}") != null }
        if (ip >= 0) ip else {
            val lw = season.episodes.indexOfLast { "ep_${it.id}" in watchedIds }
            if (lw in 0 until season.episodes.lastIndex) lw + 1 else -1
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Header(s, favorite, rating, onFavorite, onRate,
                resumeEp = season.episodes.getOrNull(resumeIndex.coerceAtLeast(0)),
                isResume = resumeIndex >= 0,
                onPlay = { onPlayQueue(queue, resumeIndex.coerceAtLeast(0)) })
        }
        if (s.seasons.size > 1) item { SeasonPicker(s.seasons, selectedSeason, onSelectSeason) }
        itemsIndexed(season.episodes) { i, ep ->
            val key = "ep_${ep.id}"
            EpisodeRow(ep, resumeFor(key), key in watchedIds) { onPlayQueue(queue, i) }
        }
    }
}

@Composable
private fun Header(
    s: Series,
    favorite: Boolean,
    rating: Int,
    onFavorite: () -> Unit,
    onRate: (Int) -> Unit,
    resumeEp: Episode?,
    isResume: Boolean,
    onPlay: () -> Unit
) {
    Column(Modifier.padding(top = 44.dp)) {
        Row(Modifier.padding(horizontal = 16.dp)) {
            Box(
                Modifier.size(104.dp, 156.dp).clip(RoundedCornerShape(12.dp)).background(Elevated),
                contentAlignment = Alignment.Center
            ) {
                if (s.cover != null) AsyncImage(s.cover, s.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(s.name, color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                s.genre?.let { Text(it, color = Accent2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                s.plot?.let {
                    Text(it, color = TextDim, fontSize = 13.sp, maxLines = 5,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        // Aksiyon satırı.
        Row(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Accent)
                    .clickable(onClick = onPlay).padding(horizontal = 22.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Ground)
                Text(
                    if (isResume && resumeEp != null) "Devam Et · B${resumeEp.episodeNum}" else "Oynat",
                    color = Ground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp)
                )
            }
            IconButton(onClick = onFavorite, modifier = Modifier.padding(start = 6.dp)) {
                Icon(if (favorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    "Daha sonra izle", tint = if (favorite) Accent else TextDim)
            }
            IconButton(onClick = { onRate(if (rating == 1) 0 else 1) }) {
                Icon(Icons.Default.ThumbUp, "Beğen", tint = if (rating == 1) Accent else TextDim)
            }
            IconButton(onClick = { onRate(if (rating == -1) 0 else -1) }) {
                Icon(Icons.Default.ThumbDown, "Beğenme", tint = if (rating == -1) Live else TextDim)
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
                Modifier.clip(RoundedCornerShape(20.dp)).background(if (active) Accent else Elevated)
                    .clickable { onSelect(season.number) }.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Sezon ${season.number}", color = if (active) Ground else TextHi,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, resume: ResumeMark?, watched: Boolean, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(8.dp)).background(Elevated),
            contentAlignment = Alignment.BottomStart
        ) {
            if (ep.thumb != null) AsyncImage(ep.thumb, ep.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.PlayArrow, null, tint = TextMute, modifier = Modifier.align(Alignment.Center))
            if (resume != null) Box(Modifier.fillMaxWidth().height(4.dp).background(LineSoft)) {
                Box(Modifier.fillMaxWidth(resume.fraction).height(4.dp).background(Accent))
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${ep.episodeNum}. ${ep.title}", color = TextHi, fontSize = 14.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            if (watched && resume == null)
                Text("İzlendi", color = Accent2, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.PlayArrow, "Oynat", tint = Accent)
    }
}
