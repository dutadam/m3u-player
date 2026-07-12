package app.cheesino.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.ui.theme.Accent
import app.cheesino.ui.theme.TextDim
import app.cheesino.ui.theme.TextMute

@Composable
fun CenterLoader(label: String = "Yükleniyor…", modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent)
            Text(label, color = TextMute, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = TextDim, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            subtitle?.let {
                Text(it, color = TextMute, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp))
            }
        }
    }
}
