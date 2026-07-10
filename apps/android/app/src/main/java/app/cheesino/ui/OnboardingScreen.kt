package app.cheesino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("cheesino", color = TextHi, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Premium IPTV oynatıcı", color = Accent2, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))

        TabRow(selectedTabIndex = tab, containerColor = Surface, contentColor = Accent) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Xtream") })
            Tab(tab == 1, { tab = 1 }, text = { Text("M3U URL") })
        }
        Spacer(Modifier.height(16.dp))

        if (tab == 0) {
            Field("Sunucu", server) { server = it }
            Field("Kullanıcı adı", user) { user = it }
            Field("Şifre", pass, password = true) { pass = it }
            Button(
                onClick = { XtreamCredentials.normalize(server)?.let { onXtream(XtreamCredentials(it, user, pass)) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color_White) else Text("Bağlan", fontWeight = FontWeight.Bold) }
        } else {
            Field("M3U URL", m3u) { m3u = it }
            Button(
                onClick = { onM3U(m3u) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color_White) else Text("Yükle", fontWeight = FontWeight.Bold) }
        }

        state.error?.let { Text(it, color = Live, modifier = Modifier.padding(top = 12.dp)) }

        Spacer(Modifier.height(20.dp))
        Text("cheesino içerik barındırmaz — kendi kaynağını getirirsin. Kimlik bilgileri cihazda şifreli saklanır.",
            color = TextDim, fontSize = 12.sp)
    }
}

private val Color_White = androidx.compose.ui.graphics.Color.White

@Composable
private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextHi, unfocusedTextColor = TextHi,
            focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
            focusedLabelColor = Accent, unfocusedLabelColor = TextMute
        )
    )
}
