package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.cheesino.core.Channel
import app.cheesino.core.StreamResolver
import app.cheesino.data.MultiViewConfig
import app.cheesino.ui.theme.*

/** Preset düzenler: (etiket, satır, sütun). Ekranda en fazla 6 slot (2x3). */
private val LAYOUTS = listOf(
    Triple("2", 2, 1),
    Triple("4", 2, 2),
    Triple("6", 3, 2)
)

@Composable
fun MultiViewScreen(
    channels: List<Channel>,
    initial: MultiViewConfig,
    onSave: (MultiViewConfig) -> Unit
) {
    var rows by remember { mutableIntStateOf(initial.rows) }
    var cols by remember { mutableIntStateOf(initial.cols) }
    val slots = remember {
        mutableStateListOf<String?>().apply { repeat(6) { add(initial.slotChannelIds.getOrNull(it)) } }
    }
    var activeSlot by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<Int?>(null) }   // tek slot tam ekran
    var pickerFor by remember { mutableStateOf<Int?>(null) }
    val byId = remember(channels) { channels.associateBy { it.id } }

    fun persist() = onSave(MultiViewConfig(rows, cols, slots.toList()))

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Düzen seçici — 2 / 4 / 6. (Segment çubuğu zaten üstte; ayrı başlık/geri gerekmez.)
        Row(Modifier.fillMaxWidth().background(Ground).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Layout", color = TextMute, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            LAYOUTS.forEach { (label, r, c) ->
                val on = r == rows && c == cols
                Box(
                    Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (on) Accent else Elevated)
                        .clickable {
                            rows = r; cols = c
                            // Küçük düzene geçince aktif slot görünür kalsın (yoksa ses tümden susar).
                            activeSlot = activeSlot.coerceIn(0, r * c - 1)
                            persist()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text(label, color = if (on) Ground else TextHi, fontWeight = FontWeight.Bold) }
            }
        }

        // Grid — bir slot tam ekransa yalnız onu göster.
        val exp = expanded
        if (exp != null) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SlotCell(
                    channel = slots.getOrNull(exp)?.let { byId[it] },
                    active = true,
                    expanded = true,
                    onTap = { activeSlot = exp },
                    onAssign = { pickerFor = exp },
                    onClear = { slots[exp] = null; expanded = null; persist() },
                    onToggleExpand = { expanded = null }
                )
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                for (r in 0 until rows) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until cols) {
                            val idx = r * cols + c
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                val ch = slots[idx]?.let { byId[it] }
                                SlotCell(
                                    channel = ch,
                                    active = idx == activeSlot,
                                    expanded = false,
                                    onTap = { activeSlot = idx },
                                    onAssign = { pickerFor = idx },
                                    onClear = { slots[idx] = null; persist() },
                                    onToggleExpand = { expanded = idx; activeSlot = idx }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Kanal seçici.
    pickerFor?.let { slotIdx ->
        ChannelPicker(
            channels = channels,
            onPick = { slots[slotIdx] = it.id; activeSlot = slotIdx; pickerFor = null; persist() },
            onClose = { pickerFor = null }
        )
    }
}

@Composable
private fun SlotCell(
    channel: Channel?,
    active: Boolean,
    expanded: Boolean,
    onTap: () -> Unit,
    onAssign: () -> Unit,
    onClear: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(8.dp)).background(Elevated)
            .then(if (active) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onTap)
    ) {
        if (channel != null) {
            val url = channel.url
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(StreamResolver.candidates(url).firstOrNull()?.url ?: url))
                    prepare(); playWhenReady = true; volume = 0f
                }
            }
            LaunchedEffect(active) { player.volume = if (active) 1f else 0f }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { PlayerView(it).apply { useController = false } },
                // Kanal değişince yeni player'a yeniden bağla — aksi halde eski yayın kalır.
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            // Kanal adı + tam ekran + kaldır.
            Row(Modifier.align(Alignment.TopStart).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, color = TextHi, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp))
            }
            Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleExpand) {
                    Icon(if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        if (expanded) "Collapse" else "Fullscreen", tint = Color.White)
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, "Remove", tint = Color.White)
                }
            }
            if (active) Text("● AUDIO", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
        } else {
            Column(Modifier.fillMaxSize().clickable(onClick = onAssign), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Add, "Add channel", tint = TextMute)
                Text("Add channel", color = TextMute, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChannelPicker(channels: List<Channel>, onPick: (Channel) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val hits = remember(q, channels) {
        val query = q.trim().lowercase()
        if (query.isEmpty()) channels.take(80)
        else channels.filter { it.name.lowercase().contains(query) }.take(80)
    }
    Column(Modifier.fillMaxSize().background(Ground)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = TextHi) }
            Text("Pick channel", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Search…") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                focusedBorderColor = Accent, unfocusedBorderColor = LineSoft
            )
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(hits) { ch ->
                Text(ch.name, color = TextHi, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(ch) }.padding(16.dp))
            }
        }
    }
}
