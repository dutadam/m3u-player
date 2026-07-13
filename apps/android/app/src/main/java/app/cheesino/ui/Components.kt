package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.SeriesRef
import app.cheesino.data.ResumeMark
import app.cheesino.ui.theme.*
import coil.compose.AsyncImage

/**
 * Yatay çerçevede dikey poster göstermek için: arka planda bulanık "fill" + önde tam "fit".
 * Böylece portre görsel yatay kutuda saçma bir dilime kırpılmaz (Netflix hero stili).
 */
@Composable
fun BlurCover(url: String, desc: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        AsyncImage(url, null, Modifier.matchParentSize().blur(22.dp), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f)))
        AsyncImage(url, desc, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
fun Rail(title: String, onSeeAll: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp, 18.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
            Text(title, color = TextHi, fontWeight = FontWeight.Black, fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp).weight(1f))
            if (onSeeAll != null) Text("Tümü ›", color = Accent2, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onSeeAll).padding(4.dp))
        }
        content()
    }
}

@Composable
fun ChannelRail(title: String, items: List<Channel>, onTap: (Channel) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    Rail(title, onSeeAll) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items.take(30)) { ch -> ChannelCard(ch) { onTap(ch) } }
        }
    }
}

@Composable
fun PosterRail(title: String, items: List<Channel>, onTap: (Channel) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    Rail(title, onSeeAll) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items.take(30)) { ch ->
                val badge = ch.rating?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" } ?: ch.quality?.label
                PosterCard(ch.name, ch.logo, badge) { onTap(ch) }
            }
        }
    }
}

@Composable
fun SeriesRail(title: String, items: List<SeriesRef>, onTap: (SeriesRef) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    Rail(title, onSeeAll) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items.take(30)) { s ->
                val badge = s.rating?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" }
                PosterCard(s.name, s.cover, badge) { onTap(s) }
            }
        }
    }
}

@Composable
fun ChannelCard(ch: Channel, onTap: () -> Unit) {
    Column(Modifier.focusHighlight(14).padding(end = 11.dp).width(120.dp).clickable(onClick = onTap)) {
        Box(
            Modifier.size(120.dp, 72.dp).clip(RoundedCornerShape(14.dp)).background(Elevated)
                .border(1.dp, LineSoft.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (ch.logo != null) AsyncImage(ch.logo, ch.name, Modifier.padding(12.dp).fillMaxSize(), contentScale = ContentScale.Fit)
            else Text(ch.name.take(2).uppercase(), color = TextHi, fontWeight = FontWeight.Black)
            ch.quality?.label?.let { Badge(it, qualityColor(it), Modifier.align(Alignment.TopEnd).padding(5.dp)) }
        }
        Text(ch.name, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun ResumeRail(title: String, items: List<ResumeMark>, onTap: (ResumeMark) -> Unit) {
    if (items.isEmpty()) return
    Rail(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items.take(30)) { m -> ResumeCard(m) { onTap(m) } }
        }
    }
}

@Composable
fun ResumeCard(mark: ResumeMark, onTap: () -> Unit) {
    Column(Modifier.focusHighlight(14).padding(end = 11.dp).width(158.dp).clickable(onClick = onTap)) {
        Box(
            Modifier.size(158.dp, 94.dp).clip(RoundedCornerShape(14.dp)).background(Elevated),
            contentAlignment = Alignment.BottomStart
        ) {
            if (mark.poster != null)
                BlurCover(mark.poster, mark.title, Modifier.fillMaxSize())
            // ilerleme çubuğu
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
                Box(Modifier.fillMaxWidth(mark.fraction).height(4.dp).background(Accent))
            }
        }
        Text(mark.title, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun PosterCard(name: String, poster: String?, badge: String? = null, onTap: () -> Unit) {
    Column(Modifier.focusHighlight().padding(end = 11.dp).width(124.dp).clickable(onClick = onTap)) {
        Box(
            Modifier.size(124.dp, 186.dp).clip(RoundedCornerShape(14.dp)).background(Elevated),
            contentAlignment = Alignment.Center
        ) {
            if (poster != null) AsyncImage(poster, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.take(2).uppercase(), color = TextMute, fontWeight = FontWeight.Black, fontSize = 22.sp)
            badge?.let { Badge(it, if (it.startsWith("★")) Gold else qualityColor(it), Modifier.align(Alignment.TopStart).padding(6.dp)) }
        }
        Text(name, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

/** Küçük etiket — kalite (4K/FHD/HD) veya puan (★). Renk spec'e göre kodlu. */
@Composable
private fun Badge(text: String, bg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)
    ) { Text(text, color = Ground, fontSize = 10.sp, fontWeight = FontWeight.Black) }
}

fun qualityColor(label: String): Color = when (label) {
    "4K" -> Gold
    "FHD" -> QualityFhd
    "HD" -> QualityHd
    else -> QualityHd
}
