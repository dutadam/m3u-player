package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.EpgEntry
import app.cheesino.ui.theme.*

@Composable
fun GuideScreen(
    channels: List<Channel>,
    epg: Map<String, List<EpgEntry>>,
    onPlay: (Channel) -> Unit,
    onCatchup: (Channel, EpgEntry) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Ground)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text("Rehber", color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        if (epg.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("EPG yükleniyor veya bu kaynakta yok.", color = TextMute)
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(channels) { ch ->
                val list = ch.tvgId?.let { epg[it] } ?: emptyList()
                val now = list.firstOrNull { it.isLiveNow }
                val next = list.firstOrNull { it.start > (now?.stop ?: 0L) }
                GuideRow(ch, now, next, onPlay, onCatchup)
            }
        }
    }
}

@Composable
private fun GuideRow(
    ch: Channel,
    now: EpgEntry?,
    next: EpgEntry?,
    onPlay: (Channel) -> Unit,
    onCatchup: (Channel, EpgEntry) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onPlay(ch) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(ch.name, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text("Şimdi · ${now.title}", color = Accent, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                // ilerleme çubuğu
                val frac = progress(now)
                Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.9f).height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(LineSoft)) {
                    Box(Modifier.fillMaxWidth(frac).height(3.dp).background(Accent))
                }
            } else {
                Text("Program bilgisi yok", color = TextMute, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            next?.let {
                Text("Sırada · ${it.title}", color = TextDim, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        // Catchup — arşiv destekliyorsa şu anki programı baştan izle.
        if (ch.supportsCatchup && now != null) {
            IconButton(onClick = { onCatchup(ch, now) }) {
                Icon(Icons.Default.Replay, "Baştan izle", tint = Accent2)
            }
        }
    }
}

private fun progress(e: EpgEntry): Float {
    val n = System.currentTimeMillis() / 1000
    val span = (e.stop - e.start).coerceAtLeast(1)
    return ((n - e.start).toFloat() / span).coerceIn(0f, 1f)
}
