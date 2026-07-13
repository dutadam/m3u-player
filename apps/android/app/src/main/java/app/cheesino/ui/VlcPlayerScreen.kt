package app.cheesino.ui

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.cheesino.data.LibraryViewModel
import app.cheesino.data.ResumeMark
import app.cheesino.ui.theme.*
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * libVLC tabanlı oynatıcı — ExoPlayer'ın oynatamadığı/kastığı yayınlar (MKV/AVI/HEVC, geniş codec)
 * için ikinci motor. Temel kontroller: oynat/duraklat, ara çubuğu (VOD), altyazı seçimi, resume.
 */
@Composable
fun VlcPlayerScreen(item: PlayItem, vm: LibraryViewModel, onClose: () -> Unit, onEnded: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity

    val existing = remember(item.id) { if (item.isLive) null else vm.resumeOf(item.id) }
    val startAtMs = remember(item.id) {
        existing?.takeIf { it.positionMs > 30_000 && !it.finished }?.positionMs ?: 0L
    }

    var buffering by remember(item.id) { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember(item.id) { mutableStateOf(0L) }
    var lengthMs by remember(item.id) { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSubs by remember { mutableStateOf(false) }
    var failed by remember(item.id) { mutableStateOf(false) }

    val libVlc = remember(item.id) {
        LibVLC(context, arrayListOf(
            "--network-caching=2000", "--no-drop-late-frames", "--no-skip-frames", "--http-reconnect"
        ))
    }
    val mediaPlayer = remember(item.id) { MediaPlayer(libVlc) }

    fun saveNow() {
        if (item.isLive) return
        if (lengthMs > 0 && positionMs > 0) vm.saveProgress(
            ResumeMark(item.id, item.title, item.url, item.poster, positionMs, lengthMs,
                System.currentTimeMillis(), item.isSeries, item.seriesId, item.seriesName)
        )
    }

    DisposableEffect(item.id) {
        val url = app.cheesino.core.StreamResolver.candidates(item.url).firstOrNull()?.url ?: item.url
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=2000")
            if (startAtMs > 0) addOption(":start-time=${startAtMs / 1000}")
        }
        mediaPlayer.media = media
        media.release()
        mediaPlayer.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Buffering -> buffering = ev.buffering < 100f
                MediaPlayer.Event.Playing -> { isPlaying = true; buffering = false }
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.TimeChanged -> if (!scrubbing) positionMs = ev.timeChanged
                MediaPlayer.Event.LengthChanged -> lengthMs = ev.lengthChanged
                MediaPlayer.Event.EndReached -> if (!item.isLive) onEnded()
                MediaPlayer.Event.EncounteredError -> failed = true
            }
        }
        mediaPlayer.play()
        onDispose {
            saveNow()
            runCatching { mediaPlayer.setEventListener(null) }
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.detachViews() }
            runCatching { mediaPlayer.release() }
            runCatching { libVlc.release() }
        }
    }

    // İzleme geçmişi (tür afinitesi) — canlı hariç.
    LaunchedEffect(item.id) { if (!item.isLive) vm.recordPlay(item.id, item.title, item.group) }

    // Ekranı açık tut + tam ekran (sistem çubuklarını gizle).
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Kontrolleri birkaç saniye sonra gizle.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) { delay(4000); controlsVisible = false }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controlsVisible = !controlsVisible
            }
    ) {
        AndroidView(
            factory = { ctx -> VLCVideoLayout(ctx).also { mediaPlayer.attachViews(it, null, false, false) } },
            modifier = Modifier.fillMaxSize()
        )

        if (buffering && !failed) BrandLoader(modifier = Modifier.align(Alignment.Center))

        if (failed) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Yayın açılamadı.", color = TextHi, fontWeight = FontWeight.Bold)
            Text("Kaynak veya bağlantı sorunlu olabilir.", color = TextMute, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
        }

        if (controlsVisible) {
            // Üst bar — geri + başlık + VLC rozeti.
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { saveNow(); onClose() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kapat", tint = Color.White)
                }
                Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("VLC", color = Ground, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(end = 4.dp)
                        .background(Accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                IconButton(onClick = { showSubs = true }) {
                    Icon(Icons.Default.Subtitles, "Altyazı", tint = Color.White)
                }
            }

            // Orta — oynat/duraklat.
            IconButton(
                onClick = {
                    if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                    controlsVisible = true
                },
                modifier = Modifier.align(Alignment.Center).size(64.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Oynat/Duraklat",
                    tint = Color.White, modifier = Modifier.size(38.dp))
            }

            // Alt — ara çubuğu (yalnız VOD).
            if (!item.isLive && lengthMs > 0) {
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f)).navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    val frac = if (scrubbing) scrubValue
                        else (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f)
                    Slider(
                        value = frac,
                        onValueChange = { scrubbing = true; scrubValue = it },
                        onValueChangeFinished = {
                            mediaPlayer.time = (scrubValue * lengthMs).toLong()
                            positionMs = (scrubValue * lengthMs).toLong()
                            scrubbing = false
                        },
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtTime(if (scrubbing) (scrubValue * lengthMs).toLong() else positionMs),
                            color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(fmtTime(lengthMs), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        if (showSubs) SubtitleSheet(mediaPlayer, onClose = { showSubs = false })
    }
}

/** Altyazı parça seçici — VLC spu track'leri. */
@Composable
private fun SubtitleSheet(mediaPlayer: MediaPlayer, onClose: () -> Unit) {
    val tracks = remember { mediaPlayer.spuTracks?.toList() ?: emptyList() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Elevated, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .navigationBarsPadding().padding(16.dp)) {
            Text("Altyazı", color = TextHi, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp))
            Text("Kapalı", color = TextHi, fontSize = 14.sp, modifier = Modifier.fillMaxWidth()
                .clickable { mediaPlayer.setSpuTrack(-1); onClose() }.padding(vertical = 10.dp))
            tracks.filter { it.id >= 0 }.forEach { t ->
                Text(t.name ?: "Parça ${t.id}", color = TextHi, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { mediaPlayer.setSpuTrack(t.id); onClose() }.padding(vertical = 10.dp))
            }
            if (tracks.none { it.id >= 0 }) Text("Bu yayında altyazı yok.", color = TextMute, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
