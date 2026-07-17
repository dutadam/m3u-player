package app.cheesino.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cheesino.ui.theme.*

/**
 * Kayan parıltı (shimmer) fırçası — yükleme sırasında iskelet kutularını doldurur.
 * Spinner beklemek yerine içerik "geliyormuş" hissi verir → daha az bekliyormuş algısı.
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f, targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "x"
    )
    val base = Elevated
    val hi = LineSoft.copy(alpha = 0.55f)
    return Brush.linearGradient(
        colors = listOf(base, hi, base),
        start = Offset(x, 0f), end = Offset(x + 320f, 220f)
    )
}

@Composable
private fun SkeletonBox(w: Dp, h: Dp, radius: Dp = 12.dp, brush: Brush) {
    Box(Modifier.size(w, h).clip(RoundedCornerShape(radius)).background(brush))
}

/** Ana sayfa iskeleti — header + hero + birkaç ray placeholder'ı (parıltılı). */
@Composable
fun HomeSkeleton() {
    val brush = shimmerBrush()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(36.dp, 36.dp, 10.dp, brush)
                Spacer(Modifier.weight(1f))
                SkeletonBox(24.dp, 24.dp, 12.dp, brush)
                Spacer(Modifier.width(12.dp))
                SkeletonBox(24.dp, 24.dp, 12.dp, brush)
                Spacer(Modifier.width(12.dp))
                SkeletonBox(24.dp, 24.dp, 12.dp, brush)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                SkeletonBox(1000.dp, 200.dp, 16.dp, brush)
            }
        }
        repeat(4) {
            item {
                Column(Modifier.padding(top = 14.dp)) {
                    Box(Modifier.padding(start = 16.dp)) { SkeletonBox(160.dp, 18.dp, 6.dp, brush) }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        item {
                            Row {
                                repeat(4) {
                                    SkeletonBox(124.dp, 186.dp, 14.dp, brush)
                                    Spacer(Modifier.width(11.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
