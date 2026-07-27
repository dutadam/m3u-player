package app.cheesino.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.core.content.ContextCompat
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
import app.cheesino.playback.PlaybackService
import app.cheesino.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/**
 * Oynatıcı girişi — arka plan servisine (PlaybackService) bir MediaController ile bağlanır.
 * Bağlantı kurulana kadar marka loader gösterir; kurulunca gerçek oynatıcıyı çizer.
 * Oynatıcının kendisi servise ait olduğu için ekran kapanınca/arka plana alınınca ses sürebilir.
 */
@Composable
fun PlayerScreen(item: PlayItem, vm: LibraryViewModel, onClose: () -> Unit, onEnded: () -> Unit = {},
                 onFallback: (() -> Unit)? = null, onPrev: (() -> Unit)? = null, onNext: (() -> Unit)? = null,
                 hasNext: Boolean = false, nextTitle: String? = null) {
    val controller = rememberMediaController()
    if (controller == null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { BrandLoader(modifier = Modifier.align(Alignment.Center)) }
    } else {
        PlayerScreenContent(controller, item, vm, onClose, onEnded, onFallback, onPrev, onNext, hasNext, nextTitle)
    }
}

/** Arka plan oynatma servisine bağlanan MediaController'ı hazırlar; hazır olunca döndürür. */
@Composable
private fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ runCatching { controller = future.get() } }, ContextCompat.getMainExecutor(context))
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

@Composable
private fun PlayerScreenContent(player: MediaController, item: PlayItem, vm: LibraryViewModel,
                 onClose: () -> Unit, onEnded: () -> Unit = {},
                 onFallback: (() -> Unit)? = null, onPrev: (() -> Unit)? = null, onNext: (() -> Unit)? = null,
                 hasNext: Boolean = false, nextTitle: String? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    // Canlı kayıt (DVR) — tek eşzamanlı kayıt; bu kanal kaydediliyor mu?
    val recording by vm.recordingActive.collectAsStateWithLifecycle()
    val recordingThis = item.isLive && recording?.title == item.title
    val recStatus by vm.recordingStatus.collectAsStateWithLifecycle()
    // Kayıt durumunu oynatıcıda göster (bağlanıyor / kaydediliyor MB / hata) — "ne oldu" belli olsun.
    var recNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recStatus, recording) {
        recNote = recStatus
        // Hata mesajıysa bir süre sonra kendiliğinden kaybolsun (aktif kayıt sürüyorsa kalsın).
        if (recStatus != null && recording == null) { delay(6000); if (recNote == recStatus) recNote = null }
    }
    // Aktif kaydın diske yazdığı MB — oynatıcıda ilerlemeyi göster.
    var recMb by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(recording?.path) {
        val path = recording?.path ?: return@LaunchedEffect
        while (true) {
            recMb = runCatching { java.io.File(path).length() }.getOrDefault(0L) / (1024.0 * 1024.0)
            delay(1000)
        }
    }
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
    var startedOnce by remember(item.id) { mutableStateOf(false) }
    // Dizi bölümü bitince "sıradaki bölüm" geri sayımı (kuyrukta sonraki bölüm varsa).
    var autoNext by remember(item.id) { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var hud by remember { mutableStateOf<String?>(null) }
    var showTracks by remember { mutableStateOf(false) }
    // Aspect ratio: Fit → Zoom → Fill (remove black bars).
    var resizeIdx by remember { mutableIntStateOf(0) }
    val resizeModes = remember { listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    ) }
    val resizeLabels = remember { listOf("Fit", "Zoom", "Fill") }
    // Ekran kilidi (kazara dokunuşları önler) + oynatma hızı.
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    // Uyku zamanlayıcısı (dk; 0 = kapalı) — süre dolunca duraklat.
    var sleepMin by remember { mutableIntStateOf(0) }
    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) { delay(sleepMin * 60_000L); player.pause(); hud = "😴 Sleep — paused"; sleepMin = 0 }
    }

    // Oynatıcı servise ait; burada yalnız bu öğe için medyayı kur (buffer/kod çözücü ayarları
    // serviste). Öğe değişince yeniden kur.
    LaunchedEffect(item.id) {
        val url = StreamResolver.candidates(item.url).firstOrNull()?.url ?: item.url
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = !askResume
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
                if (state == Player.STATE_READY) { attempts = 0; startedOnce = true }
                if (state == Player.STATE_ENDED && !item.isLive) { if (hasNext) autoNext = true else onEnded() }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: PlaybackException) {
                if (attempts < 3) {
                    attempts++
                    startAtMs = player.currentPosition.coerceAtLeast(0)
                    player.prepare(); player.playWhenReady = true
                } else {
                    // ExoPlayer oynatamadı — Otomatik modda VLC motoruna devret.
                    onFallback?.invoke()
                }
            }
        }
        player.addListener(listener)
        // Oynatıcı servise ait — burada RELEASE ETME. Ekrandan çıkınca oynatmayı durdur
        // (kullanıcı kapattı); controller bağlantısı rememberMediaController'da bırakılır.
        onDispose { saveNow(); player.removeListener(listener); player.stop(); player.clearMediaItems() }
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

    // Android TV / uzaktan kumanda: oynatıcı dokunmatik değil → D-pad ile kontrol.
    val tvFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { tvFocus.requestFocus() } }
    // Donanım GERİ tuşu oynatıcıyı kapatsın (kilitliyse önce kilidi aç).
    BackHandler { if (locked) { locked = false; hud = "🔓 Unlocked" } else { saveNow(); onClose() } }

    Box(Modifier.fillMaxSize().background(Color.Black)
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
                Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                    if (isPlaying) player.pause() else player.play(); true
                }
                Key.MediaPlayPause -> { if (isPlaying) player.pause() else player.play(); true }
                Key.MediaPlay -> { player.play(); true }
                Key.MediaPause -> { player.pause(); true }
                Key.DirectionLeft, Key.MediaRewind -> {
                    if (item.isLive) onPrev?.invoke()
                    else { player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)); hud = "⏪ 10 s" }
                    true
                }
                Key.DirectionRight, Key.MediaFastForward -> {
                    if (item.isLive) onNext?.invoke()
                    else { val d = player.duration; player.seekTo((player.currentPosition + SEEK_STEP_MS).let { if (d > 0) it.coerceAtMost(d) else it }); hud = "⏩ 10 s" }
                    true
                }
                Key.MediaNext -> { if (item.isLive) onNext?.invoke(); true }
                Key.MediaPrevious -> { if (item.isLive) onPrev?.invoke(); true }
                Key.DirectionUp, Key.DirectionDown -> true
                else -> false
            }
        }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.apply {
                        setFractionalTextSize(vm.subtitleScale)
                        setStyle(androidx.media3.ui.CaptionStyleCompat(
                            vm.subtitleColor, vm.subtitleBg, android.graphics.Color.TRANSPARENT,
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK, null
                        ))
                    }
                }
            },
            update = {
                it.player = if (casting) castPlayer else player
                it.resizeMode = resizeModes[resizeIdx]
            },
            modifier = Modifier.fillMaxSize()
        )

        // Merkez dokunuş — tek dokunuş kontrolleri açar, çift dokunuş konuma göre ±10 sn.
        Box(Modifier.fillMaxSize().pointerInput(locked) {
            detectTapGestures(
                onTap = { controlsVisible = if (locked) true else !controlsVisible },
                onDoubleTap = { offset ->
                    if (locked || item.isLive) return@detectTapGestures
                    if (offset.x < size.width / 2f) {
                        player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)); hud = "⏪ 10 sn"
                    } else {
                        val dur = player.duration
                        player.seekTo((player.currentPosition + SEEK_STEP_MS).let { if (dur > 0) it.coerceAtMost(dur) else it })
                        hud = "⏩ 10 sn"
                    }
                }
            )
        })

        // Yan jest bölgeleri — yalnız dikey kaydırma (çakışmasın): sol parlaklık, sağ ses. Kilitliyken kapalı.
        if (!locked) GestureZone(Modifier.align(Alignment.CenterStart)) { dy ->
            activity?.window?.let { w ->
                val lp = w.attributes
                val cur = if (lp.screenBrightness in 0f..1f) lp.screenBrightness else 0.5f
                val next = (cur - dy / 800f).coerceIn(0.01f, 1f)
                lp.screenBrightness = next; w.attributes = lp
                hud = "☀ ${(next * 100).roundToInt()}%"
            }
        }
        GestureZone(Modifier.align(Alignment.CenterEnd)) { dy ->
            val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val delta = (-dy / 40f).roundToInt()
            if (delta != 0) {
                val next = (cur + delta).coerceIn(0, maxVol)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                hud = "🔊 ${(next * 100 / maxVol)}%"
            }
        }

        // Marka loader — yalnız ilk yüklemede (sardırırken/rebuffer'da gösterme).
        if (buffering && !askResume && !startedOnce) BrandLoader(modifier = Modifier.align(Alignment.Center))

        hud?.let {
            Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 10.dp)
            ) { Text(it, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }

        // Kayıt durum şeridi — üstte, kontroller gizliyken de görünür (donma/boş Kitaplık yerine net geri bildirim).
        val recBanner = when {
            recordingThis -> "● Recording · %.1f MB".format(recMb)
            recNote != null -> recNote
            else -> null
        }
        recBanner?.let { note ->
            val nl = note.lowercase()
            val err = listOf("error", "refused", "no data", "unavailable", "couldn't", "empty", "blocked")
                .any { nl.contains(it) }
            Box(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(note, color = if (err) Accent2 else Live, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Tam kontroller — kilitli değilken. Üstte yalnız Geri + Kilit; her şey altta.
        AnimatedVisibility(visible = controlsVisible && !locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.55f),
                    0.25f to Color.Black.copy(alpha = 0.15f),
                    0.7f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.75f)
                )
            )) {
                // Üst bar: Geri (sol) + başlık + Kilit (sağ).
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { saveNow(); onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White)
                    }
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { locked = true; hud = "🔒 Locked" }) {
                        Icon(Icons.Default.Lock, "Lock", tint = Color.White)
                    }
                }

                // Merkez oynat/duraklat + ±10 — büyük dokunma hedefleri, ferah aralık.
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    // VOD: ±10 sn · Canlı: önceki kanal (zap).
                    if (!item.isLive) CircleBtn(Icons.Default.Replay10, 52.dp) {
                        player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)); hud = "⏪ 10 sn"
                    } else if (onPrev != null) CircleBtn(Icons.Default.SkipPrevious, 52.dp) {
                        onPrev(); hud = "◀ Previous channel"; controlsVisible = true
                    }
                    Spacer(Modifier.width(40.dp))
                    CircleBtn(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 78.dp) {
                        if (isPlaying) player.pause() else player.play()
                        controlsVisible = true
                    }
                    Spacer(Modifier.width(40.dp))
                    if (!item.isLive) CircleBtn(Icons.Default.Forward10, 52.dp) {
                        val dur = player.duration
                        player.seekTo((player.currentPosition + SEEK_STEP_MS).let { if (dur > 0) it.coerceAtMost(dur) else it }); hud = "⏩ 10 sn"
                    } else if (onNext != null) CircleBtn(Icons.Default.SkipNext, 52.dp) {
                        onNext(); hud = "Next channel ▶"; controlsVisible = true
                    }
                }

                // Alt: kayan kontrol şeridi + seekbar/canlı — hepsi parmak bölgesinde.
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chromecast (MediaRouteButton — kendi diyalogunu açar; AppCompat teması ister).
                        if (castContext != null) {
                            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                                AndroidView(factory = { ctx ->
                                    val themed = android.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
                                    MediaRouteButton(themed).also { CastButtonFactory.setUpMediaRouteButton(themed, it) }
                                })
                            }
                        }
                        CtrlChip(Icons.Default.AspectRatio, resizeLabels[resizeIdx]) {
                            resizeIdx = (resizeIdx + 1) % resizeModes.size; hud = resizeLabels[resizeIdx]; controlsVisible = true
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) CtrlChip(Icons.Default.PictureInPictureAlt, "PiP") {
                            controlsVisible = false
                            runCatching { activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
                        }
                        if (!item.isLive) CtrlChip(Icons.Default.Speed, "${speed}×") {
                            speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.75f; else -> 1f }
                            runCatching { player.setPlaybackSpeed(speed) }; hud = "Speed ${speed}×"; controlsVisible = true
                        }
                        CtrlChip(Icons.Default.Subtitles, "Subtitles") { showTracks = true }
                        CtrlChip(Icons.Default.Bedtime, if (sleepMin == 0) "Sleep" else "$sleepMin min",
                            tint = if (sleepMin == 0) Color.White else Accent) {
                            sleepMin = when (sleepMin) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
                            hud = if (sleepMin == 0) "Sleep off" else "Sleep: $sleepMin min"; controlsVisible = true
                        }
                        if (item.isLive) CtrlChip(
                            if (recordingThis) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            if (recordingThis) "Stop" else "Record",
                            tint = if (recordingThis) Live else Color.White
                        ) {
                            if (recordingThis) { vm.stopRecording(); hud = "Recording stopped" }
                            // İzlenen akıştan kaydet — ikinci bağlantı açmaz (tek-bağlantılı sağlayıcıda da çalışır).
                            else if (recording == null) { vm.startRecordingHere(item.title); hud = "Recording started" }
                            else { hud = "Already recording" }
                            controlsVisible = true
                        }
                    }
                    if (item.isLive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Live))
                            Text(" LIVE", color = Live, fontWeight = FontWeight.Black, fontSize = 13.sp)
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

        // Kilitliyken: yalnız kilit-aç butonu (kazara dokunuş korunur).
        AnimatedVisibility(visible = controlsVisible && locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center)) {
                    CircleBtn(Icons.Default.LockOpen, 56.dp) { locked = false; hud = "🔓 Unlocked"; controlsVisible = true }
                }
            }
        }

        if (showTracks) TrackDialog(player) { showTracks = false }

        // Dizi bölümü bitti → sıradaki bölüm geri sayımı (kuyrukta sonraki bölüm varsa).
        if (autoNext) NextEpisodeCountdown(
            nextTitle = nextTitle,
            onPlayNext = onEnded,
            onCancel = { autoNext = false; onClose() }
        )

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

/** Alt kontrol şeridi pill'i — ikon + etiket, yarı saydam cam yüzey. */
@Composable
private fun CtrlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun GestureZone(modifier: Modifier, onVerticalDrag: (Float) -> Unit) {
    Box(
        modifier.fillMaxHeight().fillMaxWidth(0.30f)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> onVerticalDrag(dy) } }
    )
}

@Composable
private fun TrackDialog(player: Player, onDismiss: () -> Unit) {
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
        return f.label ?: f.language?.uppercase() ?: "Track ${i + 1}"
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center) {
        Column(Modifier.width(300.dp).heightIn(max = 460.dp).clip(RoundedCornerShape(16.dp))
            .background(Ground).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (audio.isNotEmpty()) {
                Text("Audio", color = Accent2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                audio.forEach { g ->
                    for (i in 0 until g.length) if (g.isTrackSupported(i))
                        TrackRow(label(g, i), g.isTrackSelected(i)) { select(g, i) }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text("Subtitles", color = Accent2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TrackRow("Off", textOff) { disableText() }
            text.forEach { g ->
                for (i in 0 until g.length) if (g.isTrackSupported(i))
                    TrackRow(label(g, i), g.isTrackSelected(i)) { select(g, i) }
            }
            if (audio.isEmpty() && text.isEmpty())
                Text("No extra tracks.", color = TextMute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
fun ResumeDialog(positionMs: Long, onResume: () -> Unit, onRestart: () -> Unit) {
    val mm = positionMs / 60000; val ss = (positionMs / 1000) % 60
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Ground).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Resume where you left off?", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("%d:%02d".format(mm, ss), color = Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRestart) { Text("Restart", color = TextHi) }
                TextButton(onClick = onResume) { Text("Resume", color = Accent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
