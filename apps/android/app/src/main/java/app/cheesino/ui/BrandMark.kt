package app.cheesino.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cheesino.R
import app.cheesino.ui.theme.Accent
import app.cheesino.ui.theme.Elevated
import app.cheesino.ui.theme.Surface

/** Marka işareti — koyu tile + gerçek cheesino logosu + turuncu kenar (tasarım spec'i). */
@Composable
fun BrandMark(size: Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Elevated, Surface)))
            .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.brand_mark), "cheesino",
            Modifier.size(size * 0.62f))
    }
}

/** Oynatıcı/açılış loader'ı — logo alttan yukarı "doluyor" (SVG-fill). */
@Composable
fun BrandLoader(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "brand")
    val fill by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "fill"
    )
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Elevated, Surface)))
            .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        // Sönük taban
        Image(painterResource(R.drawable.brand_mark), null, Modifier.size(size * 0.62f), alpha = 0.22f)
        // Alttan yukarı dolan parlak kopya
        Image(painterResource(R.drawable.brand_mark), "cheesino",
            Modifier.size(size * 0.62f).drawWithContent {
                clipRect(top = this.size.height * (1f - fill)) { this@drawWithContent.drawContent() }
            })
    }
}
