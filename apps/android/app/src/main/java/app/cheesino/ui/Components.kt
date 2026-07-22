package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
                val rating = ch.rating?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" }
                val hdr = Regex("\\bHDR\\b", RegexOption.IGNORE_CASE).containsMatchIn(ch.name)
                val qual = ch.quality?.label?.let { if (hdr) "$it HDR" else it } ?: if (hdr) "HDR" else null
                PosterCard(ch.name, ch.logo, badge = rating, quality = qual) { onTap(ch) }
            }
        }
    }
}

/** Top 10 — outline'lı büyük sıra numarası + poster (Netflix stili). */
@Composable
fun RankedRail(title: String, items: List<Channel>, onTap: (Channel) -> Unit) {
    if (items.isEmpty()) return
    Rail(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            itemsIndexed(items.take(10)) { i, ch -> RankedCard(i + 1, ch.name, ch.logo) { onTap(ch) } }
        }
    }
}

@Composable
private fun RankedCard(rank: Int, name: String, poster: String?, onTap: () -> Unit) {
    Row(
        Modifier.focusHighlight(12).padding(end = 6.dp).clickable(onClick = onTap),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            "$rank", fontSize = 74.sp, fontWeight = FontWeight.Black, color = Accent,
            style = androidx.compose.ui.text.TextStyle(
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 7f)
            ),
            modifier = Modifier.padding(end = 2.dp).offset(y = 6.dp)
        )
        Box(Modifier.size(104.dp, 156.dp).clip(RoundedCornerShape(12.dp)).background(Elevated)) {
            if (poster != null) AsyncImage(poster, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.take(2).uppercase(), color = TextMute, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center))
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
        // Dizi ise bölüm başlığı yerine dizinin adını göster.
        Text(mark.seriesName?.takeIf { mark.isSeries } ?: mark.title,
            color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun PosterCard(name: String, poster: String?, badge: String? = null, quality: String? = null, onTap: () -> Unit) {
    Column(Modifier.focusHighlight().padding(end = 11.dp).width(124.dp).clickable(onClick = onTap)) {
        Box(
            Modifier.size(124.dp, 186.dp).clip(RoundedCornerShape(14.dp)).background(Elevated),
            contentAlignment = Alignment.Center
        ) {
            if (poster != null) AsyncImage(poster, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.take(2).uppercase(), color = TextMute, fontWeight = FontWeight.Black, fontSize = 22.sp)
            // Puan sol-üstte (altın), kalite/HDR sağ-üstte (accent).
            badge?.let { Badge(it, if (it.startsWith("★")) Gold else qualityColor(it), Modifier.align(Alignment.TopStart).padding(6.dp)) }
            quality?.let { Badge(it, Accent, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
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

/** OMDb rozetleri — IMDb (altın), Rotten Tomatoes (taze yeşil / çürük kırmızı), Metascore. */
@Composable
fun OmdbBadges(o: app.cheesino.core.OmdbInfo?, modifier: Modifier = Modifier) {
    if (o == null || !o.hasAny) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        o.imdb?.let { RatingPill("IMDb ${"%.1f".format(it)}", Gold, Ground) }
        o.rotten?.let { RatingPill("🍅 $it%", if (it >= 60) Color(0xFF21D07A) else Color(0xFFFA320A), Color.White) }
        o.meta?.let { RatingPill("MC $it", Accent2, Ground) }
    }
}

@Composable
private fun RatingPill(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

fun qualityColor(label: String): Color = when (label) {
    "4K" -> Gold
    "FHD" -> QualityFhd
    "HD" -> QualityHd
    else -> QualityHd
}
