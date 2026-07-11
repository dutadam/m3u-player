package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.cheesino.core.Channel
import app.cheesino.core.MediaKind
import app.cheesino.core.StreamResolver

/** Oynatılacak öğe — kanal, film ya da dizi bölümü fark etmez. */
data class PlayItem(
    val id: String,
    val title: String,
    val url: String,
    val poster: String? = null,
    val isLive: Boolean = false,
    val isSeries: Boolean = false
)

fun Channel.toPlayItem() = PlayItem(
    id = id, title = name, url = url, poster = logo,
    isLive = kind == MediaKind.LIVE, isSeries = false
)

@Composable
fun PlayerScreen(item: PlayItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val url = StreamResolver.candidates(item.url).firstOrNull()?.url ?: item.url
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

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
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Icon(Icons.Default.Close, "Kapat", tint = Color.White)
        }
    }
}
