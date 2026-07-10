package app.cheesino.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Marka paleti (iOS "Signal" ile aynı) — cheesino turuncusu + gece yarısı lacivirdi.
val Ground = Color(0xFF0B0F19)
val Surface = Color(0xFF141A28)
val Elevated = Color(0xFF1C2434)
val LineSoft = Color(0xFF243044)
val Accent = Color(0xFFFF8A00)      // Cheesino turuncusu
val Accent2 = Color(0xFF4FD1C5)     // teal aksan
val Gold = Color(0xFFFFD200)        // güneş sarısı (4K/premium)
val Live = Color(0xFFFF4D4F)
val TextHi = Color(0xFFF2F5FA)
val TextDim = Color(0xFF9AA6BE)
val TextMute = Color(0xFF5E6B85)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent2,
    background = Ground,
    onBackground = TextHi,
    surface = Surface,
    onSurface = TextHi,
    surfaceVariant = Elevated,
    error = Live
)

@Composable
fun CheesinoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
