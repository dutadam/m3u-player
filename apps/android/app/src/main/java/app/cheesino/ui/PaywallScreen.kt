package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.data.ProFeature
import app.cheesino.ui.theme.*

/**
 * Pro paywall — Pro avantajlarını gösterir ve satın almayı başlatır.
 * [onUpgrade] Play Billing satın alma akışını tetikler; [priceText] ürün yüklenince fiyatı gösterir;
 * [onRestore] önceki satın alımları geri yükler.
 */
@Composable
fun PaywallScreen(
    highlight: ProFeature? = null,
    priceText: String? = null,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit = {},
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ground, Surface, Ground)))
            .statusBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Kapat", tint = TextHi) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(14.dp))
            Text("cheesino Pro", color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                highlight?.let { "“${it.title}” is a Pro feature" } ?: "Unlock everything",
                color = Accent2, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(24.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(18.dp)
            ) {
                ProFeature.entries.forEach { f -> Benefit(f.title, f.desc, f == highlight) }
                Benefit("Cross-device sync", "Liste, favori ve ilerleme her yerde", false)
                Benefit("Ad-free & priority codecs", "Wide codecs (VLC) + seamless experience", false)
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Accent)
                    .clickable(onClick = onUpgrade).padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    priceText?.let { "Go Pro · $it" } ?: "Go Pro",
                    color = Ground, fontWeight = FontWeight.Black, fontSize = 16.sp
                )
            }

            Text("Restore purchases", color = Accent2, fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRestore))
            Text("One-time payment · permanent unlock.", color = TextMute, fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun Benefit(title: String, desc: String, highlighted: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp))
            .background(if (highlighted) Accent else Elevated), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = if (highlighted) Ground else Accent,
                modifier = Modifier.size(16.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = TextMute, fontSize = 12.sp)
        }
    }
}
