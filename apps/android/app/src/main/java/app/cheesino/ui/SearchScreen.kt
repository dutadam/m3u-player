package app.cheesino.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.GenreTagger
import app.cheesino.core.MediaKind
import app.cheesino.core.SeriesRef
import app.cheesino.data.LibraryState
import app.cheesino.ui.theme.*
import java.text.Normalizer

/** Aksan/işaret-duyarsız normalize — "Çukur"↔"cukur", tire/nokta yok. Bulanık eşleşme için. */
private fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace('ı', 'i').replace('ş', 's').replace('ç', 'c').replace('ğ', 'g').replace('ö', 'o').replace('ü', 'u')
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

@Composable
fun SearchScreen(
    state: LibraryState,
    genres: List<String>,
    onPlay: (Channel) -> Unit,
    onSeries: (SeriesRef) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf<String?>(null) }
    val q = query.trim().lowercase()
    val nq = remember(query) { norm(query) }

    val movies = remember(state.visibleChannels) { state.visibleChannels.filter { it.kind == MediaKind.VOD && it.logo != null } }
    val series = remember(state.visibleSeries) { state.visibleSeries.filter { it.cover != null } }

    // Tahmin/otomatik-tamamlama dizini: tüm başlıklar + normalize edilmiş biçim.
    val index = remember(state.visibleChannels, series) {
        (state.visibleChannels.map { it.name } + series.map { it.name })
            .distinct().map { it to norm(it) }
    }
    // Yazdıkça öneri başlıkları — önce baştan eşleşenler, sonra kelime-başı, sonra içeren; kısa isim öne.
    val suggestions = remember(nq, index) {
        if (nq.isBlank()) emptyList()
        else index.asSequence()
            .filter { it.second.contains(nq) }
            .sortedWith(compareBy(
                { !it.second.startsWith(nq) },
                { !it.second.split(" ").any { w -> w.startsWith(nq) } },
                { it.first.length }
            ))
            .map { it.first }.distinct().take(10).toList()
    }

    val context = LocalContext.current
    // Arama açılınca kutuya odaklan (klavye/yazım hemen; TV'de de alan seçili).
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    // Sesli arama (özellikle TV) — sistem konuşma tanıyıcısı; sonucu arama kutusuna yazar.
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }?.let { query = it }
    }
    fun startVoice() {
        runCatching {
            voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
            })
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi) }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search channels, movies, series…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.weight(1f).focusRequester(fieldFocus),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                    focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
                    focusedLeadingIconColor = Accent, unfocusedLeadingIconColor = TextMute
                )
            )
            // Sesli arama — mikrofon.
            IconButton(onClick = { startVoice() }) { Icon(Icons.Default.Mic, "Voice search", tint = Accent) }
        }

        // Tahmin çipleri — dokununca aramayı tamamlar (mobil + TV D-pad ile hızlı).
        if (suggestions.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lazyItems(suggestions) { s -> SuggestChip(s) { query = s } }
            }
        }

        // Tür filtresi çipleri (arama boşken keşif için).
        if (q.length < 2 && genres.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("All", genre == null) { genre = null } }
                lazyItems(genres) { g -> Chip(g, genre == g) { genre = if (genre == g) null else g } }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                nq.length >= 2 -> {
                    val chHits = state.visibleChannels.filter { norm(it.name).contains(nq) }.take(80)
                    val seHits = series.filter { norm(it.name).contains(nq) }.take(40)
                    if (chHits.isEmpty() && seHits.isEmpty()) EmptyState("No results")
                    else LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (seHits.isNotEmpty()) {
                            header("Series")
                            items(seHits) { s -> PosterCard(s.name, s.cover) { onSeries(s) } }
                        }
                        if (chHits.isNotEmpty()) {
                            header("Channels & Movies")
                            items(chHits) { c -> PosterCard(c.name, c.logo) { onPlay(c) } }
                        }
                    }
                }
                genre != null -> {
                    val g = genre!!
                    val gm = movies.filter { GenreTagger.tags(it.name, it.group).contains(g) }
                    val gs = series.filter { GenreTagger.tags(it.name, it.genre, it.group).contains(g) }
                    if (gm.isEmpty() && gs.isEmpty()) EmptyState("No content for $g")
                    else LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (gs.isNotEmpty()) {
                            header("$g · Series")
                            items(gs) { s -> PosterCard(s.name, s.cover) { onSeries(s) } }
                        }
                        if (gm.isNotEmpty()) {
                            header("$g · Movies")
                            items(gm) { m -> PosterCard(m.name, m.logo) { onPlay(m) } }
                        }
                    }
                }
                else -> {
                    // Keşfet — öneriler.
                    val topRated = movies.filter { (it.rating ?: 0.0) >= 7.0 }.sortedByDescending { it.rating }
                    val recent = movies.filter { it.added != null }.sortedByDescending { it.added }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                        item { PosterRail("Top Rated", topRated, onPlay) }
                        item { PosterRail("Recently Added", recent, onPlay) }
                        item { SeriesRail("Series", series, onSeries) }
                        item { PosterRail("Movies", movies, onPlay) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.focusHighlight(18, scaleFocused = 1.06f).clip(RoundedCornerShape(18.dp)).background(Elevated)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = TextMute, modifier = Modifier.size(15.dp))
        Text(label, color = TextHi, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp).widthIn(max = 220.dp))
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.focusHighlight(20, scaleFocused = 1.06f).clip(RoundedCornerShape(20.dp)).background(if (active) Accent else Elevated)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = if (active) Ground else TextHi, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.header(title: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    }
}
