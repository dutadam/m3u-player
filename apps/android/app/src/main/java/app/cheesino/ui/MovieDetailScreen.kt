package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.MovieInfo
import app.cheesino.ui.theme.*
import coil.compose.AsyncImage

@Composable
fun MovieDetailScreen(
    channel: Channel,
    load: suspend (Channel) -> MovieInfo?,
    omdb: suspend (String, String?) -> app.cheesino.core.OmdbInfo? = { _, _ -> null },
    tmdb: suspend (String, String?) -> app.cheesino.core.TmdbInfo? = { _, _ -> null },
    isFavorite: Boolean,
    rating: Int,
    onFavorite: () -> Unit,
    onRate: (Int) -> Unit,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    downloadState: Int? = null,
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    similar: List<Channel> = emptyList(),
    onSimilar: (Channel) -> Unit = {},
    onTmdbSimilar: (suspend () -> List<Channel>)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // TMDB gerçek benzerleri (varsa tür-tahmininin yerine geçer).
    var tmdbSimilar by remember(channel.id) { mutableStateOf<List<Channel>>(emptyList()) }
    LaunchedEffect(channel.id) {
        onTmdbSimilar?.let { tmdbSimilar = runCatching { it() }.getOrDefault(emptyList()) }
    }
    var info by remember(channel.id) { mutableStateOf<MovieInfo?>(null) }
    var omdbInfo by remember(channel.id) { mutableStateOf<app.cheesino.core.OmdbInfo?>(null) }
    var tmdbInfo by remember(channel.id) { mutableStateOf<app.cheesino.core.TmdbInfo?>(null) }
    LaunchedEffect(channel.id) {
        val i = load(channel); info = i
        val year = i?.releaseDate?.take(4)
        omdbInfo = omdb(channel.name, i?.releaseDate)
        tmdbInfo = tmdb(channel.name, year)
    }

    // Hero: TMDB backdrop (16:9, az kırpılır) > poster. Metin: sağlayıcı > TMDB > OMDb.
    val cover = tmdbInfo?.backdrop ?: info?.cover ?: channel.logo
    Box(Modifier.fillMaxSize().background(Ground)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Backdrop.
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                if (cover != null)
                    AsyncImage(cover, channel.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Ground))
                ))
                Text(channel.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
            }

            Column(Modifier.padding(16.dp)) {
                // Meta satırı.
                val meta = buildList {
                    (info?.rating ?: channel.rating ?: tmdbInfo?.rating)?.takeIf { it > 0 }?.let { add("★ ${"%.1f".format(it)}") }
                    (info?.genre ?: tmdbInfo?.genres)?.let { add(it) }
                    (info?.releaseDate?.take(4)?.takeIf { it.isNotBlank() } ?: tmdbInfo?.year)?.let { add(it) }
                    info?.durationSecs?.let { add("${it / 60} dk") }
                    channel.quality?.label?.let { add(it) }
                }
                if (meta.isNotEmpty())
                    Text(meta.joinToString("  ·  "), color = Accent2, fontSize = 13.sp, fontWeight = FontWeight.Medium)

                // Gerçek puanlar (OMDb: IMDb + Rotten Tomatoes).
                OmdbBadges(omdbInfo, modifier = Modifier.padding(top = 10.dp))

                // Fragman (TMDB → YouTube).
                tmdbInfo?.trailerKey?.let { key ->
                    Row(
                        Modifier.padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).background(Elevated)
                            .clickable {
                                runCatching {
                                    context.startActivity(android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.youtube.com/watch?v=$key")))
                                }
                            }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.size(18.dp))
                        Text("Fragmanı İzle", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(start = 6.dp))
                    }
                }

                // Aksiyonlar.
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Accent)
                            .clickable(onClick = onPlay).padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Ground)
                        Text("Oynat", color = Ground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                    }
                    IconButton(onClick = onFavorite, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            "Daha sonra izle", tint = if (isFavorite) Accent else TextDim)
                    }
                    IconButton(onClick = { onRate(if (rating == 1) 0 else 1) }) {
                        Icon(Icons.Default.ThumbUp, "Beğen", tint = if (rating == 1) Accent else TextDim)
                    }
                    IconButton(onClick = { onRate(if (rating == -1) 0 else -1) }) {
                        Icon(Icons.Default.ThumbDown, "Beğenme", tint = if (rating == -1) Live else TextDim)
                    }
                    // Çevrimdışı indirme — duruma göre indir / iniyor / indirildi.
                    when (downloadState) {
                        androidx.media3.exoplayer.offline.Download.STATE_COMPLETED ->
                            IconButton(onClick = onRemoveDownload) {
                                Icon(Icons.Default.DownloadDone, "İndirildi — kaldır", tint = Accent)
                            }
                        androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING,
                        androidx.media3.exoplayer.offline.Download.STATE_QUEUED,
                        androidx.media3.exoplayer.offline.Download.STATE_RESTARTING ->
                            IconButton(onClick = onRemoveDownload) {
                                Icon(Icons.Default.Downloading, "İniyor — iptal", tint = Accent2)
                            }
                        else ->
                            IconButton(onClick = onDownload) {
                                Icon(Icons.Default.Download, "Çevrimdışı indir", tint = TextDim)
                            }
                    }
                }

                (info?.plot ?: tmdbInfo?.overview ?: omdbInfo?.plot)?.let {
                    Text(it, color = TextDim, fontSize = 14.sp, lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 16.dp))
                }
                (info?.cast ?: tmdbInfo?.cast)?.let {
                    Text("Oyuncular: $it", color = TextMute, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp))
                }
                info?.director?.let {
                    Text("Yönetmen: $it", color = TextMute, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
            // Benzerler — TMDB önerisi varsa onu, yoksa tür bazlı listeyi göster.
            val shownSimilar = tmdbSimilar.ifEmpty { similar }
            if (shownSimilar.isNotEmpty()) PosterRail("Benzerler", shownSimilar, onSimilar)
            Spacer(Modifier.height(24.dp))
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
        }
    }
}
