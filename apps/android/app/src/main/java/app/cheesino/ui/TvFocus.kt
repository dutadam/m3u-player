package app.cheesino.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.cheesino.ui.theme.Accent

/** Cihaz televizyon mu? (10-foot davranışlarını buna göre ayarlarız.) */
@Composable
fun isTvDevice(): Boolean {
    val ctx = LocalContext.current
    return remember {
        val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}

/**
 * Android TV / uzaktan kumanda için 10-foot odak vurgusu.
 * clickable zaten odak hedefi üretir; bu modifier D-pad odağında kartı büyütüp, öne getirip
 * belirgin bir accent çerçeve + gölge ile işaretler. Telefonda (dokunmatik) odak gelmez.
 */
fun Modifier.focusHighlight(cornerDp: Int = 12, scaleFocused: Float = 1.12f): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleFocused else 1f, label = "focusScale")
    this
        .onFocusChanged { focused = it.isFocused }
        .zIndex(if (focused) 1f else 0f)   // odaklı kart komşularının ÜSTÜNde (kırpılmasın)
        .scale(scale)
        .then(if (focused)
            Modifier.shadow(12.dp, RoundedCornerShape(cornerDp.dp))
                .border(3.dp, Accent, RoundedCornerShape(cornerDp.dp))
        else Modifier)
}
