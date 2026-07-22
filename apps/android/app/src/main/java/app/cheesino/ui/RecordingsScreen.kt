package app.cheesino.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.cheesino.playback.ActiveRecording
import app.cheesino.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

/**
 * Kayıtlar ekranı — aktif kaydı (durdur) üstte, tamamlanan kayıt dosyalarını altta listeler;
 * oynatır/siler. Dosyalar uygulamaya özel dizinde tutulur.
 */
@Composable
fun RecordingsScreen(
    recordings: List<File>,
    active: ActiveRecording?,
    onPlay: (PlayItem) -> Unit,
    onDelete: (File) -> Unit,
    onStopActive: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    embedded: Boolean = false,
    status: String? = null
) {
    LaunchedEffect(Unit) { while (true) { onRefresh(); delay(1500) } }

    Column(Modifier.fillMaxSize().background(Ground).then(if (embedded) Modifier else Modifier.statusBarsPadding())) {
        if (!embedded) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text("Kayıtlar", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.padding(start = 4.dp))
        }

        if (active != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Live))
                val mb = (runCatching { File(active.path).length() }.getOrDefault(0L)) / (1024.0 * 1024.0)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Kaydediliyor · %.1f MB".format(mb), color = Live, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(active.title, color = TextHi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onStopActive) { Icon(Icons.Default.Stop, "Durdur", tint = Live) }
            }
        }

        status?.let {
            val err = listOf("hata", "reddetti", "gelmedi", "alınamadı", "Boş").any { k -> it.contains(k) }
            Text(it, color = if (err) Live else Accent2, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
        if (recordings.isEmpty() && active == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz kayıt yok.\nCanlı yayında oynatıcıdan ● Kaydet'e dokun.",
                    color = TextMute, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(recordings, key = { it.absolutePath }) { file -> RecRow(file, onPlay, onDelete) }
            }
        }
    }
}

@Composable
private fun RecRow(file: File, onPlay: (PlayItem) -> Unit, onDelete: (File) -> Unit) {
    val title = file.name.substringBeforeLast('_').ifBlank { file.name }
    val mb = file.length() / (1024.0 * 1024.0)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
            .background(Surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("%.1f MB".format(mb), color = Accent2, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp))
        }
        IconButton(onClick = {
            onPlay(PlayItem(id = "rec_${file.name}", title = title, url = Uri.fromFile(file).toString()))
        }) { Icon(Icons.Default.PlayArrow, "Oynat", tint = Accent) }
        IconButton(onClick = { onDelete(file) }) { Icon(Icons.Default.Delete, "Sil", tint = TextDim) }
    }
}
