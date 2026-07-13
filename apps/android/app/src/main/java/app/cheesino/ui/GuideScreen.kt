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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var q by remember { mutableStateOf("") }
    val query = q.trim().lowercase()

    Column(Modifier.fillMaxSize().background(Ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text("Rehber", color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Kanal veya program ara…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
                focusedLeadingIconColor = Accent, unfocusedLeadingIconColor = TextMute
            )
        )
        if (epg.isEmpty()) {
            Text("EPG yükleniyor… kanallar aşağıda listeleniyor.", color = TextMute, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (query.length >= 2) {
                // Kanal adına VE program içeriğine (EPG başlıkları) göre ara.
                val hits = channels.filter { ch ->
                    ch.name.lowercase().contains(query) ||
                        (ch.tvgId?.let { epg[it] }?.any { e -> e.title.lowercase().contains(query) } == true)
                }
                if (hits.isEmpty()) item { Text("Sonuç yok.", color = TextMute, modifier = Modifier.padding(16.dp)) }
                items(hits) { ch -> GuideRow(ch, epg, onPlay, onCatchup, query) }
            } else {
                fun hasNow(ch: Channel) = ch.tvgId?.let { epg[it] }?.any { it.isLiveNow } == true
                // Kategoriler EPG yoğunluğuna göre (alfabetik değil); içinde EPG olanlar üstte.
                val byCat = channels.groupBy { it.group }
                    .mapValues { (_, chans) -> chans.sortedByDescending { hasNow(it) } }
                    .entries.sortedByDescending { (_, chans) -> chans.count { hasNow(it) } }
                byCat.forEach { (cat, chans) ->
                    item { GuideCategoryHeader(cat, chans.count { hasNow(it) }) }
                    items(chans) { ch -> GuideRow(ch, epg, onPlay, onCatchup, "") }
                }
            }
        }
    }
}

@Composable
private fun GuideCategoryHeader(title: String, liveCount: Int) {
    Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
        Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (liveCount > 0) Text("$liveCount canlı", color = Accent2, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun GuideRow(
    ch: Channel,
    epg: Map<String, List<EpgEntry>>,
    onPlay: (Channel) -> Unit,
    onCatchup: (Channel, EpgEntry) -> Unit,
    matchQuery: String
) {
    val list = ch.tvgId?.let { epg[it] } ?: emptyList()
    val now = list.firstOrNull { it.isLiveNow }
    val next = list.firstOrNull { it.start > (now?.stop ?: 0L) }
    // Arama program başlığıyla eşleştiyse (ve şu an oynayan değilse) o programı da göster.
    val matched = matchQuery.takeIf { it.length >= 2 && !ch.name.lowercase().contains(it) }
        ?.let { q -> list.firstOrNull { it.title.lowercase().contains(q) && it != now } }
    Row(
        Modifier.fillMaxWidth().clickable { onPlay(ch) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(ch.name, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text("${hhmm(now.start)} · ${now.title}", color = Accent, fontSize = 12.sp,
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
                Text("${hhmm(it.start)} · ${it.title}", color = TextDim, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            matched?.let {
                Text("🔎 ${dayHhmm(it.start)} · ${it.title}", color = Accent2, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp))
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

private val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private fun hhmm(epochSec: Long): String = timeFmt.format(java.util.Date(epochSec * 1000))
private val dayTimeFmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault())
private fun dayHhmm(epochSec: Long): String = dayTimeFmt.format(java.util.Date(epochSec * 1000))

private fun progress(e: EpgEntry): Float {
    val n = System.currentTimeMillis() / 1000
    val span = (e.stop - e.start).coerceAtLeast(1)
    return ((n - e.start).toFloat() / span).coerceIn(0f, 1f)
}
