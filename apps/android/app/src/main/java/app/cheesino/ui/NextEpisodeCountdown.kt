package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Dizi bölümü bitince "Sıradaki bölüm" geri sayımı — N sn sonra otomatik sonraki bölüme geçer.
 * "Play now" hemen geçer, "Cancel" oynatıcıyı kapatır. Son bölümde gösterilmez (hasNext=false).
 * Her iki oynatıcı motoru (ExoPlayer + VLC) tarafından paylaşılır.
 */
@Composable
fun NextEpisodeCountdown(
    nextTitle: String?,
    seconds: Int = 8,
    onPlayNext: () -> Unit,
    onCancel: () -> Unit
) {
    var remaining by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) { delay(1000); remaining-- }
        onPlayNext()
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("UP NEXT", color = Accent2, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
            if (!nextTitle.isNullOrBlank()) Text(
                nextTitle, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).widthIn(max = 340.dp)
            )
            Text("Next episode in ${remaining}s", color = TextDim, fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Accent)
                        .clickable(onClick = onPlayNext).padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Ground)
                    Text("Play now", color = Ground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                }
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onCancel).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Close, null, tint = TextHi, modifier = Modifier.size(18.dp))
                    Text("Cancel", color = TextHi, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
