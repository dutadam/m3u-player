package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
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
    onClose: () -> Unit,
    onToggleReminder: (Channel, EpgEntry) -> Unit = { _, _ -> },
    isReminded: (Channel, EpgEntry) -> Boolean = { _, _ -> false }
) {
    var q by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    val query = q.trim().lowercase()

    // Kanal seçildiyse: o kanalın tüm programı (öncesi/sonrası) — programa dokununca hatırlatıcı/izle.
    selected?.let { sel ->
        ChannelSchedule(sel, epg, onPlay, onCatchup, onToggleReminder, isReminded, onBack = { selected = null })
        return
    }

    Column(Modifier.fillMaxSize().background(Ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text("Rehber", color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { grid = !grid }) {
                Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView,
                    if (grid) "Liste görünümü" else "Zaman çizelgesi", tint = Accent)
            }
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

        if (grid) EpgGrid(channels, epg, query, onPlay, onCatchup)
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (query.length >= 2) {
                // Kanal adına VE program içeriğine (EPG başlıkları) göre ara.
                val hits = channels.filter { ch ->
                    ch.name.lowercase().contains(query) ||
                        (ch.tvgId?.let { epg[it] }?.any { e -> e.title.lowercase().contains(query) } == true)
                }
                if (hits.isEmpty()) item { Text("Sonuç yok.", color = TextMute, modifier = Modifier.padding(16.dp)) }
                items(hits) { ch -> GuideRow(ch, epg, query) { selected = it } }
            } else {
                fun hasNow(ch: Channel) = ch.tvgId?.let { epg[it] }?.any { it.isLiveNow } == true
                // Kategoriler EPG yoğunluğuna göre (alfabetik değil); içinde EPG olanlar üstte.
                val byCat = channels.groupBy { it.group }
                    .mapValues { (_, chans) -> chans.sortedByDescending { hasNow(it) } }
                    .entries.sortedByDescending { (_, chans) -> chans.count { hasNow(it) } }
                byCat.forEach { (cat, chans) ->
                    item { GuideCategoryHeader(cat, chans.count { hasNow(it) }) }
                    items(chans) { ch -> GuideRow(ch, epg, "") { selected = it } }
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

/** Rehber liste satırı — dokununca kanalın program çizelgesi açılır. */
@Composable
private fun GuideRow(
    ch: Channel,
    epg: Map<String, List<EpgEntry>>,
    matchQuery: String,
    onSelect: (Channel) -> Unit
) {
    val list = ch.tvgId?.let { epg[it] } ?: emptyList()
    val now = list.firstOrNull { it.isLiveNow }
    val next = list.firstOrNull { it.start > (now?.stop ?: 0L) }
    val matched = matchQuery.takeIf { it.length >= 2 && !ch.name.lowercase().contains(it) }
        ?.let { q -> list.firstOrNull { it.title.lowercase().contains(q) && it != now } }
    Row(
        Modifier.fillMaxWidth().focusHighlight(10, scaleFocused = 1f).clickable { onSelect(ch) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(ch.name, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text("${hhmm(now.start)} · ${now.title}", color = Accent, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
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
                Text("Sırada · ${hhmm(it.start)} · ${it.title}", color = TextDim, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            matched?.let {
                Text("🔎 ${dayHhmm(it.start)} · ${it.title}", color = Accent2, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp))
            }
        }
        Icon(Icons.Default.ChevronRight, "Programı gör", tint = TextMute)
    }
}

/** Bir kanalın tam program çizelgesi — şimdi vurgulu, geçmiş soluk. Programa dokun → izle/catchup/hatırlat. */
@Composable
private fun ChannelSchedule(
    ch: Channel,
    epg: Map<String, List<EpgEntry>>,
    onPlay: (Channel) -> Unit,
    onCatchup: (Channel, EpgEntry) -> Unit,
    onToggleReminder: (Channel, EpgEntry) -> Unit,
    isReminded: (Channel, EpgEntry) -> Boolean,
    onBack: () -> Unit
) {
    val now = System.currentTimeMillis() / 1000
    val progs = remember(ch.id, epg) {
        (ch.tvgId?.let { epg[it] } ?: emptyList()).sortedBy { it.start }.filter { it.stop > now - 3 * 3600 }
    }
    Column(Modifier.fillMaxSize().background(Ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text(ch.name, color = TextHi, fontWeight = FontWeight.Black, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Accent)
                .clickable { onPlay(ch) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Ground)
                Text("İzle", color = Ground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (progs.isEmpty()) {
            EmptyState("Program bilgisi yok", "Bu kanal için EPG verisi görünmüyor.")
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(progs) { p ->
                val live = p.isLiveNow
                val past = p.stop <= now
                var reminded by remember(ch.id, p.start) { mutableStateOf(isReminded(ch, p)) }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        when {
                            live -> onPlay(ch)
                            past && ch.supportsCatchup -> onCatchup(ch, p)
                            !past -> { onToggleReminder(ch, p); reminded = !reminded }
                        }
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(hhmm(p.start), color = if (live) Accent else TextMute, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(p.title, color = if (past) TextMute else TextHi,
                            fontWeight = if (live) FontWeight.Black else FontWeight.Medium, fontSize = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (live) Text("● CANLI", color = Live, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    when {
                        live -> Icon(Icons.Default.PlayArrow, "İzle", tint = Accent)
                        past && ch.supportsCatchup -> Icon(Icons.Default.Replay, "Baştan izle", tint = Accent2)
                        !past -> Icon(if (reminded) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                            if (reminded) "Hatırlatmayı kaldır" else "Bildirim aç",
                            tint = if (reminded) Accent else TextMute)
                        else -> {}
                    }
                }
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
