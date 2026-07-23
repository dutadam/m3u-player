package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.EpgEntry
import app.cheesino.core.SportsFinder
import app.cheesino.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SportsScreen(
    channels: List<Channel>,
    epg: Map<String, List<EpgEntry>>,
    onPlay: (Channel) -> Unit,
    onClose: () -> Unit,
    embedded: Boolean = false
) {
    val matches = remember(channels, epg) { SportsFinder.today(channels, epg) }
    val byId = remember(channels) { channels.associateBy { it.id } }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(Ground).then(if (embedded) Modifier else Modifier.statusBarsPadding())) {
        if (!embedded) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi) }
            Text("Sports Center", color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        if (matches.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matches today (EPG required).", color = TextMute)
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(matches) { m ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { byId[m.channelId]?.let(onPlay) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Saat + canlı rozeti
                    Column(Modifier.width(56.dp)) {
                        Text(timeFmt.format(Date(m.start * 1000)), color = TextHi,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (m.isLiveNow) Box(
                            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp))
                                .background(Live).padding(horizontal = 6.dp, vertical = 1.dp)
                        ) { Text("CANLI", color = TextHi, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(m.title, color = TextHi, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(m.channelName, color = Accent2, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}
