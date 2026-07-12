package app.cheesino.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
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
    val isSeries: Boolean = false,
    val seriesId: Int? = null,
    val seriesName: String? = null
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

    val existing = remember(item.id) { if (item.isLive) null else vm.resumeOf(item.id) }
    var askResume by remember(item.id) {
        mutableStateOf(existing != null && existing.positionMs > 30_000 && !existing.finished)
    }
    var startAtMs by remember(item.id) { mutableStateOf(0L) }

    // Chromecast — cihaz varsa. Play Services yoksa null (buton gizli).
    val castContext = remember { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
    val castPlayer = remember(castContext) { castContext?.let { CastPlayer(it) } }
    var casting by remember { mutableStateOf(castPlayer?.isCastSessionAvailable == true) }

    var buffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
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

    fun saveNow() {
        if (item.isLive) return
        val dur = player.duration; val pos = player.currentPosition
        if (dur > 0 && pos > 0) vm.saveProgress(
            ResumeMark(item.id, item.title, item.url, item.poster, pos, dur,
                System.currentTimeMillis(), item.isSeries, item.seriesId, item.seriesName)
        )
    }

    DisposableEffect(player) {
        var attempts = 0
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) attempts = 0
                if (state == Player.STATE_ENDED && !item.isLive) onEnded()
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: PlaybackException) {
                if (attempts < 5) {
                    attempts++
                    startAtMs = player.currentPosition.coerceAtLeast(0)
                    player.prepare(); player.playWhenReady = true
                }
            }
        }
        player.addListener(listener)
        onDispose { saveNow(); player.removeListener(listener); player.release() }
    }

    // Ekranı açık tut + tam ekran (sistem çubuklarını gizle).
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Cast oturumu geldi/gitti → yereli/cast'i değiştir.
    DisposableEffect(castPlayer) {
        val cp = castPlayer
        if (cp != null) {
            cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    casting = true
                    cp.setMediaItem(MediaItem.fromUri(item.url)); cp.playWhenReady = true
                    player.pause()
                }
                override fun onCastSessionUnavailable() { casting = false; player.play() }
            })
        }
        onDispose { cp?.setSessionAvailabilityListener(null); cp?.release() }
    }

    LaunchedEffect(item.id) { if (!item.isLive) vm.recordPlay(item.id, item.title, item.group) }

    // Konum/süre + periyodik kayıt.
    LaunchedEffect(item.id) {
        while (true) {
            if (!scrubbing) positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.let { if (it > 0) it else 0 }
            delay(500)
        }
    }
    LaunchedEffect(item.id) {
        while (true) { delay(5_000); saveNow() }
    }

    // Kontrolleri 3.5 sn sonra gizle.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) { delay(3_500); controlsVisible = false }
    }
    LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.setFractionalTextSize(vm.subtitleScale)
                }
            },
            update = { it.player = if (casting) castPlayer else player },
            modifier = Modifier.fillMaxSize()
        )

        // Merkez dokunuş — kontrolleri aç/kapat.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { controlsVisible = !controlsVisible })
        })

        // Yan jest bölgeleri.
        GestureZone(
            Modifier.align(Alignment.CenterStart),
            onDoubleTap = { player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)); hud = "⏪ 10 sn" },
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
        GestureZone(
            Modifier.align(Alignment.CenterEnd),
            onDoubleTap = {
                val dur = player.duration
                player.seekTo((player.currentPosition + SEEK_STEP_MS).let { if (dur > 0) it.coerceAtMost(dur) else it })
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

        // Marka loader (canlı dışı buffer).
        if (buffering && !askResume) BrandLoader(Modifier.align(Alignment.Center))

        hud?.let {
            Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 10.dp)
            ) { Text(it, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }

        // Kontrol katmanı.
        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                // Üst bar.
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { saveNow(); onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kapat", tint = Color.White)
                    }
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // Chromecast butonu (cihaz destekliyorsa; MediaRouteButton AppCompat teması ister).
                    if (castContext != null) {
                        AndroidView(factory = { ctx ->
                            val themed = android.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
                            MediaRouteButton(themed).also { CastButtonFactory.setUpMediaRouteButton(themed, it) }
                        })
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = {
                            controlsVisible = false
                            runCatching { activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
                        }) { Icon(Icons.Default.PictureInPictureAlt, "Küçük ekran", tint = Color.White) }
                    }
                    IconButton(onClick = { showTracks = true }) {
                        Icon(Icons.Default.Subtitles, "Altyazı / Ses", tint = Color.White)
                    }
                }

                // Merkez oynat/duraklat + ±10.
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    if (!item.isLive) CircleBtn(Icons.Default.Replay10, 44.dp) {
                        player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
                    }
                    Spacer(Modifier.width(28.dp))
                    CircleBtn(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 64.dp) {
                        if (isPlaying) player.pause() else player.play()
                        controlsVisible = true
                    }
                    Spacer(Modifier.width(28.dp))
                    if (!item.isLive) CircleBtn(Icons.Default.Forward10, 44.dp) {
                        val dur = player.duration
                        player.seekTo((player.currentPosition + SEEK_STEP_MS).let { if (dur > 0) it.coerceAtMost(dur) else it })
                    }
                }

                // Alt bar — seekbar veya canlı rozeti.
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (item.isLive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Live))
                            Text(" CANLI", color = Live, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    } else if (durationMs > 0) {
                        Slider(
                            value = positionMs.coerceIn(0, durationMs).toFloat(),
                            onValueChange = { scrubbing = true; positionMs = it.toLong(); controlsVisible = true },
                            onValueChangeFinished = { player.seekTo(positionMs); scrubbing = false },
                            valueRange = 0f..durationMs.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(alpha = 0.3f))
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(fmt(positionMs), color = Color.White, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(fmt(durationMs), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (showTracks) TrackDialog(player) { showTracks = false }

        if (askResume && existing != null) {
            ResumeDialog(
                positionMs = existing.positionMs,
                onResume = { player.seekTo(existing.positionMs); player.playWhenReady = true; askResume = false },
                onRestart = { player.seekTo(0); player.playWhenReady = true; askResume = false }
            )
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun CircleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
private fun GestureZone(modifier: Modifier, onDoubleTap: () -> Unit, onVerticalDrag: (Float) -> Unit) {
    Box(
        modifier.fillMaxHeight().fillMaxWidth(0.28f)
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
            .setTrackTypeDisabled(group.type, false).build()
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
            Text("%d:%02d".format(mm, ss), color = Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRestart) { Text("Baştan", color = TextHi) }
                TextButton(onClick = onResume) { Text("Devam Et", color = Accent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
