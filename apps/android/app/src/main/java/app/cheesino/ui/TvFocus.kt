package app.cheesino.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import app.cheesino.ui.theme.Accent

/**
 * Android TV / uzaktan kumanda için 10-foot odak vurgusu.
 * clickable zaten odak hedefi üretir; bu modifier D-pad odağında kartı büyütüp çerçeveler.
 * Telefonda (dokunmatik) görünmez — odak yalnız kumandayla gelir.
 */
fun Modifier.focusHighlight(cornerDp: Int = 12): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focusScale")
    this
        .onFocusChanged { focused = it.isFocused }
        .scale(scale)
        .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(cornerDp.dp)) else Modifier)
}
