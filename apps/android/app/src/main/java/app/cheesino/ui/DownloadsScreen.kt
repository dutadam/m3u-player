package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import app.cheesino.ui.theme.*
import kotlinx.coroutines.delay

/**
 * İndirilenler ekranı — indiriliyor/tamamlandı/başarısız öğeleri ilerlemeleriyle listeler,
 * tamamlananları oynatır, hepsini silebilir. Görünürken ilerleme için periyodik tazeler.
 */
@Composable
fun DownloadsScreen(
    downloads: List<Download>,
    onPlay: (PlayItem) -> Unit,
    onRemove: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    embedded: Boolean = false
) {
    // Ekran açıkken canlı yüzde için periyodik tazele.
    LaunchedEffect(Unit) {
        while (true) { onRefresh(); delay(1200) }
    }

    Column(Modifier.fillMaxSize().background(Ground).then(if (embedded) Modifier else Modifier.statusBarsPadding())) {
        if (!embedded) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text("İndirilenler", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.padding(start = 4.dp))
        }

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz indirme yok.\nFilm detayında “Çevrimdışı indir”e dokun.",
                    color = TextMute, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(downloads, key = { it.request.id }) { dl ->
                    DownloadRow(dl, onPlay, onRemove)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(dl: Download, onPlay: (PlayItem) -> Unit, onRemove: (String) -> Unit) {
    val title = runCatching { String(dl.request.data, Charsets.UTF_8) }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: dl.request.id
    val completed = dl.state == Download.STATE_COMPLETED
    val percent = dl.percentDownloaded.let { if (it < 0f) 0f else it } / 100f
    val status = when (dl.state) {
        Download.STATE_COMPLETED -> "İndirildi"
        Download.STATE_DOWNLOADING -> "İniyor · %${(dl.percentDownloaded.coerceAtLeast(0f)).toInt()}"
        Download.STATE_QUEUED, Download.STATE_RESTARTING -> "Sırada"
        Download.STATE_STOPPED -> "Duraklatıldı"
        Download.STATE_FAILED -> "Başarısız"
        Download.STATE_REMOVING -> "Kaldırılıyor"
        else -> ""
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
            .background(Surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, color = if (completed) Accent else Accent2, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp))
            if (!completed && dl.state != Download.STATE_FAILED) {
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = Accent, trackColor = Elevated
                )
            }
        }
        if (completed) {
            IconButton(onClick = {
                onPlay(PlayItem(id = dl.request.id, title = title, url = dl.request.uri.toString()))
            }) { Icon(Icons.Default.PlayArrow, "Oynat", tint = Accent) }
        }
        IconButton(onClick = { onRemove(dl.request.id) }) {
            Icon(Icons.Default.Delete, "Sil", tint = TextDim)
        }
    }
}
