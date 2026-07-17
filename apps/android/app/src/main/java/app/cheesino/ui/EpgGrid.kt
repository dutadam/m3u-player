package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.cheesino.core.Channel
import app.cheesino.core.EpgEntry
import app.cheesino.ui.theme.*
import kotlin.math.max
import kotlin.math.min

/**
 * Çok kanallı EPG zaman çizelgesi (TiviMate tarzı): satır = kanal, sütun = zaman.
 * Program blokları süreye göre genişler; şu an dikey çizgi ile işaretlenir.
 * Yatay kaydırma tüm satırlarda ve saat başlığında ortaktır.
 */
@Composable
fun EpgGrid(
    channels: List<Channel>,
    epg: Map<String, List<EpgEntry>>,
    query: String,
    onPlay: (Channel) -> Unit,
    onCatchup: (Channel, EpgEntry) -> Unit
) {
    val hState = rememberScrollState()
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val startT = nowSec - 30 * 60          // 30 dk geriden
    val endT = nowSec + 12 * 3600          // 12 saat ileri
    val totalMin = ((endT - startT) / 60).toInt()
    val pxPerMin = 5.dp
    val laneWidth = pxPerMin * totalMin.toFloat()
    val colWidth = 116.dp
    val rowHeight = 58.dp

    val rows = remember(channels, epg, query) {
        val q = query.trim().lowercase()
        channels.filter { ch ->
            ch.tvgId != null && (epg[ch.tvgId]?.isNotEmpty() == true) &&
                (q.length < 2 || ch.name.lowercase().contains(q))
        }
    }

    if (rows.isEmpty()) {
        EmptyState("EPG yok", "Bu kaynakta program verisi görünmüyor.")
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Saat başlığı — sol köşe boş, sağı yatay kaydırılır (lane'lerle senkron).
        Row {
            Box(Modifier.width(colWidth).height(28.dp).background(Surface))
            Row(Modifier.horizontalScroll(hState).background(Surface)) {
                val firstTick = startT - (startT % 3600) + 3600   // ilk tam saat
                var t = firstTick
                // Baştaki yarım saatlik boşluk.
                Spacer(Modifier.width(pxPerMin * ((firstTick - startT) / 60).toFloat()))
                while (t < endT) {
                    Box(Modifier.width(pxPerMin * 60f).height(28.dp), contentAlignment = Alignment.CenterStart) {
                        Text(hhmmGrid(t), color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    t += 3600
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(rows) { ch ->
                Row(Modifier.height(rowHeight)) {
                    // Kanal adı sütunu (sabit).
                    Box(Modifier.width(colWidth).fillMaxHeight().background(Ground)
                        .clickable { onPlay(ch) }.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Text(ch.name, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    // Program şeridi.
                    Box(Modifier.horizontalScroll(hState).width(laneWidth).fillMaxHeight()) {
                        val progs = epg[ch.tvgId]?.filter { it.stop > startT && it.start < endT } ?: emptyList()
                        progs.forEach { p ->
                            val leftMin = ((max(p.start, startT) - startT) / 60).toInt().coerceAtLeast(0)
                            val wMin = ((min(p.stop, endT) - max(p.start, startT)) / 60).toInt().coerceAtLeast(1)
                            val live = p.isLiveNow
                            Box(
                                Modifier.offset(x = pxPerMin * leftMin.toFloat())
                                    .width(pxPerMin * wMin.toFloat()).fillMaxHeight().padding(1.5.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (live) Accent.copy(alpha = 0.22f) else Elevated)
                                    .clickable {
                                        if (p.stop < nowSec && ch.supportsCatchup) onCatchup(ch, p) else onPlay(ch)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column {
                                    Text(p.title, color = if (live) Accent else TextHi, fontSize = 11.sp,
                                        fontWeight = if (live) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(hhmmGrid(p.start), color = TextMute, fontSize = 9.sp, maxLines = 1)
                                }
                            }
                        }
                        // Şu an çizgisi.
                        val nowMin = ((nowSec - startT) / 60).toInt().coerceAtLeast(0)
                        Box(Modifier.offset(x = pxPerMin * nowMin.toFloat()).width(2.dp).fillMaxHeight()
                            .background(Live))
                    }
                }
            }
        }
    }
}

private val gridTimeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private fun hhmmGrid(epochSec: Long): String = gridTimeFmt.format(java.util.Date(epochSec * 1000))
