package app.cheesino.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tasarım tokenları — tek kaynak. Renk rolleri Theme.kt'te; burada boşluk/köşe skalası ve
 * cam (glass) yüzey renkleri. Amaç: ekranlar arası tutarlı ritim.
 */

// Cam yüzeyler — üstünden içerik hafif görünür (kayan içerik üzerinde "glass" hissi).
val Glass = Color(0xCC141A28)          // yarı saydam yüzey (floating nav/segment)
val GlassStrong = Color(0xE6141A28)    // daha opak cam
val GlassBorder = Color(0x22FFFFFF)    // ince açık kenar

// Boşluk skalası (4·8·12·16·24).
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

// Köşe yarıçapı skalası.
object Radii {
    val sm = 10.dp
    val md = 14.dp
    val lg = 22.dp
    val pill = 26.dp
}
