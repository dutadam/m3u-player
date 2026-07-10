package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.Channel
import app.cheesino.core.SeriesRef
import app.cheesino.ui.theme.*
import coil.compose.AsyncImage

@Composable
fun Rail(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        content()
    }
}

@Composable
fun ChannelRail(title: String, items: List<Channel>, onTap: (Channel) -> Unit) {
    if (items.isEmpty()) return
    Rail(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items.take(30)) { ch -> ChannelCard(ch) { onTap(ch) } }
        }
    }
}

@Composable
fun PosterRail(title: String, items: List<Channel>, onTap: (Channel) -> Unit) {
    if (items.isEmpty()) return
    Rail(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items.take(30)) { ch -> PosterCard(ch.name, ch.logo) { onTap(ch) } }
        }
    }
}

@Composable
fun SeriesRail(title: String, items: List<SeriesRef>, onTap: (SeriesRef) -> Unit) {
    if (items.isEmpty()) return
    Rail(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(items.take(30)) { s -> PosterCard(s.name, s.cover) { onTap(s) } }
        }
    }
}

@Composable
fun ChannelCard(ch: Channel, onTap: () -> Unit) {
    Column(Modifier.padding(end = 11.dp).width(118.dp).clickable(onClick = onTap)) {
        Box(
            Modifier.size(118.dp, 70.dp).clip(RoundedCornerShape(11.dp)).background(Elevated),
            contentAlignment = Alignment.Center
        ) {
            if (ch.logo != null) AsyncImage(ch.logo, ch.name, Modifier.padding(10.dp).fillMaxSize(), contentScale = ContentScale.Fit)
            else Text(ch.name.take(2).uppercase(), color = TextHi, fontWeight = FontWeight.Black)
        }
        Text(ch.name, color = TextHi, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun PosterCard(name: String, poster: String?, onTap: () -> Unit) {
    Column(Modifier.padding(end = 11.dp).width(120.dp).clickable(onClick = onTap)) {
        Box(
            Modifier.size(120.dp, 180.dp).clip(RoundedCornerShape(12.dp)).background(Elevated),
            contentAlignment = Alignment.Center
        ) {
            if (poster != null) AsyncImage(poster, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.take(2).uppercase(), color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        Text(name, color = TextHi, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}
