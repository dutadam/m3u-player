package app.cheesino.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.core.XtreamCredentials
import app.cheesino.data.LibraryState
import app.cheesino.ui.theme.*

@Composable
fun OnboardingScreen(state: LibraryState, onXtream: (XtreamCredentials) -> Unit, onM3U: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }   // 0 Xtream, 1 M3U
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }

    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }
                .getOrNull()?.let { text -> onM3U(text) }
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Ground, Surface, Ground))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            BrandMark(size = 76.dp)
            Spacer(Modifier.height(16.dp))
            Text("cheesino", color = TextHi, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("Premium IPTV oynatıcı", color = Accent2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(28.dp))

            // Form kartı.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                    .padding(18.dp)
            ) {
                SegTabs(tab) { tab = it }
                Spacer(Modifier.height(14.dp))

                if (tab == 0) {
                    Field("Sunucu (http://host:port)", server) { server = it }
                    Field("Kullanıcı adı", user) { user = it }
                    Field("Şifre", pass, password = true) { pass = it }
                    PrimaryButton("Bağlan", state.loading) {
                        XtreamCredentials.normalize(server)?.let { onXtream(XtreamCredentials(it, user, pass)) }
                    }
                } else {
                    Field("M3U URL", m3u) { m3u = it }
                    PrimaryButton("Yükle", state.loading) { onM3U(m3u) }
                    Box(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                            .background(Ground).clickable { filePicker.launch("*/*") }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("veya cihazdan .m3u dosyası seç", color = Accent2, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
                }

                state.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Live.copy(alpha = 0.12f)).padding(10.dp)
                    ) { Text(it, color = Live, fontSize = 13.sp) }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "cheesino içerik barındırmaz — kendi kaynağını getirirsin. Kimlik bilgileri cihazda şifreli saklanır, telemetri yok.",
                color = TextMute, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SegTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ground).padding(4.dp)
    ) {
        listOf("Xtream", "M3U URL").forEachIndexed { i, label ->
            val active = selected == i
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (active) Accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (active) Ground else TextDim,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !loading,
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, disabledContainerColor = Accent.copy(alpha = 0.6f))
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Ground, strokeWidth = 2.dp)
        else Text(label, color = Ground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextHi, unfocusedTextColor = TextHi,
            focusedContainerColor = Ground, unfocusedContainerColor = Ground,
            focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
            focusedLabelColor = Accent, unfocusedLabelColor = TextMute
        )
    )
}
