package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.data.SubCue
import app.cheesino.data.Subtitles

/**
 * Motor-bağımsız altyazı katmanı — SRT cue'larını oynatma konumuna göre kendimiz çizeriz.
 * Böylece anlık sync offset (rebuffer yok), çift altyazı (orijinal + çeviri) ve tam stil kontrolü.
 * [primary] alta büyük, [secondary] (çift altyazıda) üstte küçük gösterilir.
 */
@Composable
fun SubtitleOverlay(
    primary: List<SubCue>,
    secondary: List<SubCue>?,
    positionMs: Long,
    offsetMs: Long,
    scale: Float,
    textColor: Int,
    bgColor: Int,
    raiseDp: Int = 0,
    onRaise: ((Float) -> Unit)? = null
) {
    val primText = Subtitles.activeAt(primary, positionMs, offsetMs)
    val secText = secondary?.let { Subtitles.activeAt(it, positionMs, offsetMs) }
    if (primText == null && secText == null) return
    val sizeSp = when { scale <= 0.045f -> 15; scale <= 0.07f -> 19; else -> 24 }
    val density = LocalDensity.current
    // Sürükleme yalnız altyazı metninde (Column boş alanı dokunuşları alta geçirir).
    val dragMod = if (onRaise != null) Modifier.pointerInput(Unit) {
        detectVerticalDragGestures { _, dy -> onRaise(with(density) { -dy.toDp().value }) }
    } else Modifier
    Column(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = (42 + raiseDp).dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        secText?.let { SubLine(it, Color(textColor).copy(alpha = 0.82f), bgColor, sizeSp - 4, Modifier) }
        primText?.let { SubLine(it, Color(textColor), bgColor, sizeSp, dragMod) }
    }
}

@Composable
private fun SubLine(text: String, color: Color, bgArgb: Int, sizeSp: Int, modifier: Modifier) {
    val hasBg = (bgArgb ushr 24) != 0
    Text(
        text, color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        style = if (hasBg) TextStyle.Default
        else TextStyle(shadow = Shadow(Color.Black, Offset(0f, 0f), blurRadius = 8f)),   // arka plan yoksa okunurluk için gölge
        modifier = modifier.padding(vertical = 2.dp)
            .then(if (hasBg) Modifier.background(Color(bgArgb), RoundedCornerShape(4.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
