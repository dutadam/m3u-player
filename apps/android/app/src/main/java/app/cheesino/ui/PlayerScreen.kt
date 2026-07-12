package app.cheesino.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.cheesino.core.Channel
import app.cheesino.core.MediaKind
import app.cheesino.core.StreamResolver
import app.cheesino.data.LibraryViewModel
import app.cheesino.data.ResumeMark
import app.cheesino.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Oynatılacak öğe — kanal, film ya da dizi bölümü fark etmez. */
data class PlayItem(
    val id: String,
    val title: String,
    val url: String,
    val poster: String? = null,
    val group: String? = null,
    val isLive: Boolean = false,
    val isSeries: Boolean = false
)

fun Channel.toPlayItem() = PlayItem(
    id = id, title = name, url = url, poster = logo, group = group,
    isLive = kind == MediaKind.LIVE, isSeries = false
)

private const val SEEK_STEP_MS = 10_000L

@Composable
fun PlayerScreen(item: PlayItem, vm: LibraryViewModel, onClose: () -> Unit, onEnded: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val user by vm.user.collectAsStateWithLifecycle()

    // Kaldığın yerden devam — canlı dışı ve kayda değer konum varsa sor.
    val existing = remember(item.id) { if (item.isLive) null else vm.resumeOf(item.id) }
    var askResume by remember(item.id) {
        mutableStateOf(existing != null && existing.positionMs > 30_000 && !existing.finished)
    }
    var startAtMs by remember(item.id) { mutableStateOf(0L) }

    var buffering by remember { mutableStateOf(true) }
    var hud by remember { mutableStateOf<String?>(null) }
    var showTracks by remember { mutableStateOf(false) }

    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            val url = StreamResolver.candidates(item.url).firstOrNull()?.url ?: item.url
            setMediaItem(MediaItem.fromUri(url))
            if (startAtMs > 0) seekTo(startAtMs)
            prepare()
            playWhenReady = !askResume
        }
    }

    // Oynatma durumu + hata → otomatik yeniden bağlanma.
    DisposableEffect(player) {
        var attempts = 0
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) attempts = 0
                // Bölüm/film bitti → sıradaki (canlıda son yoktur).
                if (state == Player.STATE_ENDED && !item.isLive) onEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                if (attempts < 5) {
                    attempts++
                    startAtMs = player.currentPosition.coerceAtLeast(0)
                    player.prepare()
                    player.playWhenReady = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            // Son ilerlemeyi kaydet, sonra bırak.
            if (!item.isLive) {
                val dur = player.duration; val pos = player.currentPosition
                if (dur > 0 && pos > 0)
                    vm.saveProgress(ResumeMark(item.id, item.title, item.url, item.poster, pos, dur, System.currentTimeMillis(), item.isSeries))
            }
            player.removeListener(listener)
            player.release()
        }
    }

    // İzleme olayını bir kez kaydet (tür afinitesi).
    LaunchedEffect(item.id) {
        if (!item.isLive) vm.recordPlay(item.id, item.title, item.group)
    }

    // Periyodik ilerleme kaydı.
    LaunchedEffect(item.id) {
        if (item.isLive) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val dur = player.duration; val pos = player.currentPosition
            if (dur > 0 && pos > 0)
                vm.saveProgress(ResumeMark(item.id, item.title, item.url, item.poster, pos, dur, System.currentTimeMillis(), item.isSeries))
        }
    }

    // HUD'u kısa süre sonra gizle.
    LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Sol jest bölgesi — parlaklık + çift dokunuş geri sar.
        GestureZone(
            modifier = Modifier.align(Alignment.CenterStart),
            onDoubleTap = {
                player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
                hud = "⏪ 10 sn"
            },
            onVerticalDrag = { dy ->
                activity?.window?.let { w ->
                    val lp = w.attributes
                    val cur = if (lp.screenBrightness in 0f..1f) lp.screenBrightness else 0.5f
                    val next = (cur - dy / 800f).coerceIn(0.01f, 1f)
                    lp.screenBrightness = next; w.attributes = lp
                    hud = "☀ ${(next * 100).roundToInt()}%"
                }
            }
        )
        // Sağ jest bölgesi — ses + çift dokunuş ileri sar.
        GestureZone(
            modifier = Modifier.align(Alignment.CenterEnd),
            onDoubleTap = {
                val dur = player.duration
                val target = player.currentPosition + SEEK_STEP_MS
                player.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                hud = "⏩ 10 sn"
            },
            onVerticalDrag = { dy ->
                val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val delta = (-dy / 40f).roundToInt()
                if (delta != 0) {
                    val next = (cur + delta).coerceIn(0, maxVol)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                    hud = "🔊 ${(next * 100 / maxVol)}%"
                }
            }
        )

        // Buffer göstergesi (canlı dışı).
        if (buffering && !askResume) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
        }

        // HUD.
        hud?.let {
            Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 10.dp)
            ) { Text(it, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }

        // Üst bar — favori, beğen/beğenme, kapat.
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val fav = item.id in user.favorites
            val rating = when { item.id in user.likes -> 1; item.id in user.dislikes -> -1; else -> 0 }
            IconButton(onClick = { showTracks = true }) {
                Icon(Icons.Default.Subtitles, "Altyazı / Ses", tint = Color.White)
            }
            IconButton(onClick = { vm.setRating(item.id, if (rating == 1) 0 else 1) }) {
                Icon(Icons.Default.ThumbUp, "Beğen", tint = if (rating == 1) Accent else Color.White)
            }
            IconButton(onClick = { vm.setRating(item.id, if (rating == -1) 0 else -1) }) {
                Icon(Icons.Default.ThumbDown, "Beğenme", tint = if (rating == -1) Live else Color.White)
            }
            IconButton(onClick = { vm.toggleFavorite(item.id) }) {
                Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favori", tint = if (fav) Accent else Color.White)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Kapat", tint = Color.White) }
        }

        // Altyazı / ses parça seçici.
        if (showTracks) TrackDialog(player) { showTracks = false }

        // Kaldığın yerden devam diyalogu.
        if (askResume && existing != null) {
            ResumeDialog(
                positionMs = existing.positionMs,
                onResume = { startAtMs = existing.positionMs; player.seekTo(existing.positionMs); player.playWhenReady = true; askResume = false },
                onRestart = { startAtMs = 0; player.seekTo(0); player.playWhenReady = true; askResume = false }
            )
        }
    }
}

@Composable
private fun GestureZone(
    modifier: Modifier,
    onDoubleTap: () -> Unit,
    onVerticalDrag: (Float) -> Unit
) {
    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth(0.28f)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> onVerticalDrag(dy) } }
    )
}

@Composable
private fun TrackDialog(player: ExoPlayer, onDismiss: () -> Unit) {
    val tracks = player.currentTracks
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
    val textOff = text.none { g -> (0 until g.length).any { g.isTrackSelected(it) } }

    fun select(group: Tracks.Group, index: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .setTrackTypeDisabled(group.type, false)
            .build()
        onDismiss()
    }
    fun disableText() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        onDismiss()
    }
    fun label(group: Tracks.Group, i: Int): String {
        val f = group.getTrackFormat(i)
        return f.label ?: f.language?.uppercase() ?: "Parça ${i + 1}"
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center) {
        Column(Modifier.width(300.dp).clip(RoundedCornerShape(16.dp)).background(Ground).padding(16.dp)) {
            if (audio.isNotEmpty()) {
                Text("Ses", color = Accent2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                audio.forEach { g ->
                    for (i in 0 until g.length) if (g.isTrackSupported(i))
                        TrackRow(label(g, i), g.isTrackSelected(i)) { select(g, i) }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text("Altyazı", color = Accent2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TrackRow("Kapalı", textOff) { disableText() }
            text.forEach { g ->
                for (i in 0 until g.length) if (g.isTrackSupported(i))
                    TrackRow(label(g, i), g.isTrackSelected(i)) { select(g, i) }
            }
            if (audio.isEmpty() && text.isEmpty())
                Text("Ek parça yok.", color = TextMute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        (if (selected) "● " else "○ ") + label,
        color = if (selected) Accent else TextHi, fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)
    )
}

@Composable
private fun ResumeDialog(positionMs: Long, onResume: () -> Unit, onRestart: () -> Unit) {
    val mm = positionMs / 60000; val ss = (positionMs / 1000) % 60
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Ground).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Kaldığın yerden devam?", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(String.format("%d:%02d", mm, ss), color = Accent, fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRestart) { Text("Baştan", color = TextHi) }
                TextButton(onClick = onResume) { Text("Devam Et", color = Accent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
