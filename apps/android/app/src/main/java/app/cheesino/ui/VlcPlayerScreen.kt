package app.cheesino.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun VlcPlayerScreen(item: PlayItem, vm: LibraryViewModel, onClose: () -> Unit, onEnded: () -> Unit = {},
                    onPrev: (() -> Unit)? = null, onNext: (() -> Unit)? = null,
                    hasNext: Boolean = false, nextTitle: String? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

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
    var showAudio by remember { mutableStateOf(false) }
    var failed by remember(item.id) { mutableStateOf(false) }
    var speed by remember(item.id) { mutableStateOf(1f) }
    var arIdx by remember(item.id) { mutableStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    // Dizi bölümü bitince "sıradaki bölüm" geri sayımı (kuyrukta sonraki bölüm varsa).
    var autoNext by remember(item.id) { mutableStateOf(false) }
    val ratios = remember { listOf<Pair<String, String?>>("Auto" to null, "16:9" to "16:9", "4:3" to "4:3") }

    // Canlı kayıt (DVR) — tek eşzamanlı kayıt; bu yayın kaydediliyor mu?
    val recording by vm.recordingActive.collectAsStateWithLifecycle()
    val recordingThis = item.isLive && recording?.title == item.title

    val audioMgr = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVol = remember { audioMgr.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    // Canlıda düşük gecikme için daha küçük buffer; VOD'da biraz daha büyük.
    val cacheMs = if (item.isLive) 1200 else 1800
    val libVlc = remember(item.id) {
        LibVLC(context, arrayListOf(
            "--network-caching=$cacheMs", "--live-caching=$cacheMs", "--file-caching=$cacheMs",
            "--no-drop-late-frames", "--no-skip-frames", "--http-reconnect", "--avcodec-fast", "--avcodec-skiploopfilter=4"
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

    // Hata ekranından "tekrar dene" — mevcut oynatıcıyı yeniden hazırla (release etmeden).
    fun reload() {
        runCatching {
            val u = app.cheesino.core.StreamResolver.candidates(item.url).firstOrNull()?.url ?: item.url
            val m = Media(libVlc, Uri.parse(u)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":network-caching=$cacheMs")
            }
            mediaPlayer.media = m; m.release()
            failed = false; buffering = true
            mediaPlayer.play()
        }
    }

    DisposableEffect(item.id) {
        val url = app.cheesino.core.StreamResolver.candidates(item.url).firstOrNull()?.url ?: item.url
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=$cacheMs")
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
                // onEnded ekranın kapanmasını (player.release) tetikler → VLC callback thread'inde
                // release deadlock yapar; ana thread'e taşı.
                MediaPlayer.Event.EndReached -> if (!item.isLive) mainHandler.post { if (hasNext) autoNext = true else onEnded() }
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

    if (hud != null) LaunchedEffect(hud) { delay(700); hud = null }

    // Android TV / uzaktan kumanda: D-pad kontrolü + GERİ ile kapat.
    val tvFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { tvFocus.requestFocus() } }
    BackHandler { if (locked) { locked = false; hud = "🔓 Unlocked" } else { saveNow(); onClose() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(tvFocus).focusable()
            .onKeyEvent { ke ->
                if (ke.type != KeyEventType.KeyDown) return@onKeyEvent false
                controlsVisible = true
                // Geri sayım ekranında OK/ileri → hemen sonraki bölüm; diğer tuşlar geri sayımı sürdürür.
                if (autoNext) return@onKeyEvent when (ke.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlay, Key.MediaFastForward, Key.MediaNext -> { onEnded(); true }
                    else -> true
                }
                if (locked) return@onKeyEvent true
                when (ke.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
                        if (isPlaying) mediaPlayer.pause() else mediaPlayer.play(); true
                    }
                    Key.MediaPlay -> { mediaPlayer.play(); true }
                    Key.MediaPause -> { mediaPlayer.pause(); true }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (item.isLive) onPrev?.invoke()
                        else if (lengthMs > 0) { val np = (mediaPlayer.time - 10_000).coerceIn(0, lengthMs); mediaPlayer.time = np; positionMs = np; hud = "«  -10 s" }
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (item.isLive) onNext?.invoke()
                        else if (lengthMs > 0) { val np = (mediaPlayer.time + 10_000).coerceIn(0, lengthMs); mediaPlayer.time = np; positionMs = np; hud = "»  +10 s" }
                        true
                    }
                    Key.MediaNext -> { if (item.isLive) onNext?.invoke(); true }
                    Key.MediaPrevious -> { if (item.isLive) onPrev?.invoke(); true }
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            }
            .pointerInput(item.id, locked) {
                detectTapGestures(
                    onTap = { controlsVisible = if (locked) true else !controlsVisible },
                    onDoubleTap = { off ->
                        if (!locked && !item.isLive && lengthMs > 0) {
                            val fwd = off.x > size.width / 2
                            val np = (mediaPlayer.time + if (fwd) 10_000 else -10_000).coerceIn(0, lengthMs)
                            mediaPlayer.time = np; positionMs = np
                            hud = if (fwd) "»  +10 s" else "«  -10 s"
                        }
                    }
                )
            }
            .pointerInput(item.id, locked) {
                if (locked) return@pointerInput
                var leftSide = false
                var startBright = 0f
                var startVol = 0
                detectVerticalDragGestures(
                    onDragStart = { off ->
                        leftSide = off.x < size.width / 2
                        startBright = activity?.window?.attributes?.screenBrightness ?: 0.5f
                        if (startBright < 0f) startBright = 0.5f
                        startVol = audioMgr.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    },
                    onVerticalDrag = { _, dragAmount ->
                        val delta = -dragAmount / size.height   // yukarı = artış
                        if (leftSide) {
                            val b = (startBright + delta * 1.5f).coerceIn(0.01f, 1f)
                            startBright = b
                            activity?.window?.let { w ->
                                w.attributes = w.attributes.apply { screenBrightness = b }
                            }
                            hud = "☀  ${(b * 100).toInt()}%"
                        } else {
                            val v = (startVol + (delta * maxVol * 1.5f)).toInt().coerceIn(0, maxVol)
                            startVol = v
                            audioMgr.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v, 0)
                            hud = "🔊  ${(v * 100 / maxVol)}%"
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx -> VLCVideoLayout(ctx).also { mediaPlayer.attachViews(it, null, false, false) } },
            modifier = Modifier.fillMaxSize()
        )

        hud?.let {
            Text(it, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp))
        }

        if (buffering && !failed) BrandLoader(modifier = Modifier.align(Alignment.Center))

        if (failed) Column(Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Playback failed.", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("The source or connection may have a problem.", color = TextMute, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
            if (recording != null) {
                Text(
                    "A recording is in progress. Most providers allow only one connection at a time — " +
                        "a second stream may not open without stopping the recording.",
                    color = Accent2, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center
                )
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FailBtn("Stop recording & retry", Live) { vm.stopRecording(); reload() }
                    FailBtn("Retry", Accent) { reload() }
                }
            } else {
                FailBtn("Retry", Accent, Modifier.padding(top = 12.dp)) { reload() }
            }
        }

        // Tam kontroller — kilitli değilken. Üstte yalnız Geri + Kilit; her şey altta (ExoPlayer ile aynı düzen).
        AnimatedVisibility(visible = controlsVisible && !locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.55f),
                    0.25f to Color.Black.copy(alpha = 0.15f),
                    0.7f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.75f)
                )
            )) {
                // Üst bar: Geri (sol) + başlık + VLC rozeti + Kilit (sağ).
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { saveNow(); onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White)
                    }
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("VLC", color = Ground, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(end = 4.dp)
                            .background(Accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    IconButton(onClick = { locked = true; hud = "🔒 Locked"; controlsVisible = true }) {
                        Icon(Icons.Default.Lock, "Lock", tint = Color.White)
                    }
                }

                // Merkez oynat/duraklat + ±10 — büyük dokunma hedefleri.
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    if (!item.isLive && lengthMs > 0) VlcCircleBtn(Icons.Default.Replay10, 52.dp) {
                        val np = (mediaPlayer.time - 10_000).coerceIn(0, lengthMs)
                        mediaPlayer.time = np; positionMs = np; hud = "«  -10 s"; controlsVisible = true
                    } else if (item.isLive && onPrev != null) VlcCircleBtn(Icons.Default.SkipPrevious, 52.dp) {
                        onPrev(); hud = "◀ Previous channel"; controlsVisible = true
                    }
                    Spacer(Modifier.width(40.dp))
                    VlcCircleBtn(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 78.dp) {
                        if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                        controlsVisible = true
                    }
                    Spacer(Modifier.width(40.dp))
                    if (!item.isLive && lengthMs > 0) VlcCircleBtn(Icons.Default.Forward10, 52.dp) {
                        val np = (mediaPlayer.time + 10_000).coerceIn(0, lengthMs)
                        mediaPlayer.time = np; positionMs = np; hud = "»  +10 s"; controlsVisible = true
                    } else if (item.isLive && onNext != null) VlcCircleBtn(Icons.Default.SkipNext, 52.dp) {
                        onNext(); hud = "Next channel ▶"; controlsVisible = true
                    }
                }

                // Alt: kayan kontrol şeridi + seekbar/canlı — hepsi parmak bölgesinde.
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VlcCtrlChip(Icons.Default.AspectRatio, ratios[arIdx].first) {
                            arIdx = (arIdx + 1) % ratios.size
                            runCatching { mediaPlayer.setAspectRatio(ratios[arIdx].second); mediaPlayer.scale = 0f }
                            hud = "Aspect · ${ratios[arIdx].first}"; controlsVisible = true
                        }
                        if (!item.isLive && lengthMs > 0) VlcCtrlChip(Icons.Default.Speed, "${speed}×") {
                            speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.75f; else -> 1f }
                            runCatching { mediaPlayer.rate = speed }; hud = "Speed ${speed}×"; controlsVisible = true
                        }
                        VlcCtrlChip(Icons.Default.Audiotrack, "Audio") { showAudio = true }
                        VlcCtrlChip(Icons.Default.Subtitles, "Subtitles") { showSubs = true }
                        if (item.isLive) VlcCtrlChip(
                            if (recordingThis) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            if (recordingThis) "Stop" else "Record",
                            tint = if (recordingThis) Live else Color.White
                        ) {
                            if (recordingThis) { vm.stopRecording(); hud = "Recording stopped" }
                            else if (recording == null) { vm.startRecording(item.url, item.title); hud = "● Recording" }
                            else { hud = "Already recording" }
                            controlsVisible = true
                        }
                    }
                    if (item.isLive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Live))
                            Text(" LIVE", color = Live, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    } else if (lengthMs > 0) {
                        val frac = if (scrubbing) scrubValue
                            else (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f)
                        Slider(
                            value = frac,
                            onValueChange = { scrubbing = true; scrubValue = it; controlsVisible = true },
                            onValueChangeFinished = {
                                mediaPlayer.time = (scrubValue * lengthMs).toLong()
                                positionMs = (scrubValue * lengthMs).toLong()
                                scrubbing = false
                            },
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f))
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
        }

        // Kilitliyken: yalnız kilit-aç butonu (kazara dokunuş korunur).
        AnimatedVisibility(visible = controlsVisible && locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center)) {
                    VlcCircleBtn(Icons.Default.LockOpen, 56.dp) { locked = false; hud = "🔓 Unlocked"; controlsVisible = true }
                }
            }
        }

        // Dizi bölümü bitti → sıradaki bölüm geri sayımı (kuyrukta sonraki bölüm varsa).
        if (autoNext) NextEpisodeCountdown(
            nextTitle = nextTitle,
            onPlayNext = onEnded,
            onCancel = { autoNext = false; onClose() }
        )

        if (showSubs) SubtitleSheet(mediaPlayer, onClose = { showSubs = false })
        if (showAudio) AudioSheet(mediaPlayer, onClose = { showAudio = false })
    }
}

/** Ses parçası seçici — VLC audio track'leri (çoklu dil). */
@Composable
private fun AudioSheet(mediaPlayer: MediaPlayer, onClose: () -> Unit) {
    val tracks = remember { mediaPlayer.audioTracks?.toList() ?: emptyList() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Elevated, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .navigationBarsPadding().padding(16.dp)) {
            Text("Audio", color = TextHi, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp))
            tracks.filter { it.id >= 0 }.forEach { t ->
                Text(t.name ?: "Track ${t.id}", color = TextHi, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { mediaPlayer.setAudioTrack(t.id); onClose() }.padding(vertical = 10.dp))
            }
            if (tracks.none { it.id >= 0 }) Text("Only one audio track.", color = TextMute, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp))
        }
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
            Text("Subtitles", color = TextHi, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp))
            Text("Off", color = TextHi, fontSize = 14.sp, modifier = Modifier.fillMaxWidth()
                .clickable { mediaPlayer.setSpuTrack(-1); onClose() }.padding(vertical = 10.dp))
            tracks.filter { it.id >= 0 }.forEach { t ->
                Text(t.name ?: "Track ${t.id}", color = TextHi, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { mediaPlayer.setSpuTrack(t.id); onClose() }.padding(vertical = 10.dp))
            }
            if (tracks.none { it.id >= 0 }) Text("No subtitles in this stream.", color = TextMute, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

/** Hata ekranı aksiyon butonu. */
@Composable
private fun FailBtn(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(label, color = Ground, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(color)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp))
}

/** Yuvarlak transport butonu (oynat/duraklat, ±10, kilit-aç). */
@Composable
private fun VlcCircleBtn(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
}

/** Alt kontrol şeridi pill'i — ikon + etiket, yarı saydam cam yüzey. */
@Composable
private fun VlcCtrlChip(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
