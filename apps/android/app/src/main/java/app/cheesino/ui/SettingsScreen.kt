package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheesino.BuildConfig
import app.cheesino.data.LibraryViewModel
import app.cheesino.ui.theme.*

@Composable
fun SettingsScreen(vm: LibraryViewModel, onClose: () -> Unit, onSignedOut: () -> Unit,
                   onUpgrade: () -> Unit = {}, onOpenDownloads: () -> Unit = {},
                   onOpenRecordings: () -> Unit = {}, embedded: Boolean = false) {
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val account by vm.authUser.collectAsStateWithLifecycle()
    val authStatus by vm.authStatus.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val activity = ctx as? android.app.Activity
    var ua by remember { mutableStateOf(vm.userAgent) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var parentalOn by remember { mutableStateOf(vm.parentalEnabled) }
    var subScale by remember { mutableStateOf(vm.subtitleScale) }
    var subColor by remember { mutableStateOf(vm.subtitleColor) }
    var subBgOn by remember { mutableStateOf(vm.subtitleBg != 0) }
    var engine by remember { mutableStateOf(vm.playerEngine) }

    Column(Modifier.fillMaxSize().background(Ground).then(if (embedded) Modifier else Modifier.statusBarsPadding()).verticalScroll(rememberScrollState())) {
        // Başlık
        Row(Modifier.fillMaxWidth().padding(if (embedded) 16.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi) }
            Text(if (embedded) "Account" else "Settings", color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }

        Section("Account") {
            val a = account
            if (a != null) {
                Text(a.name ?: a.email ?: "Signed in", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                a.email?.let { Text(it, color = TextMute, fontSize = 12.sp) }
                Text("Favorites and likes sync across devices.", color = TextDim, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
                Button(onClick = { vm.signOutAccount() }, modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ground)
                ) { Text("Sign out", color = Live, fontWeight = FontWeight.Bold) }
            } else {
                Text("Sign in with Google → favorites/likes stay the same across all your devices.",
                    color = TextDim, fontSize = 13.sp)
                Button(onClick = { activity?.let { vm.signIn(it) } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Sign in with Google", color = Ground, fontWeight = FontWeight.Bold) }
                if (!vm.isAuthConfigured(ctx))
                    Text("Google sign-in becomes active once enabled in the console.",
                        color = TextMute, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            authStatus?.let {
                Text(it, color = if (it.contains("failed")) Live else Accent2,
                    fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Section(if (isPro) "cheesino Pro · Active" else "cheesino Pro") {
            if (isPro) {
                Text("Pro active — all features unlocked. Thank you!", color = TextDim, fontSize = 13.sp)
            } else {
                Text("Multi-view, timeline, unlimited sources, sync and more.",
                    color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Go Pro", fontWeight = FontWeight.Black, color = Ground) }
            }
            if (BuildConfig.DEBUG) {
                Text("Developer: toggle Pro", color = TextMute, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp).clickable { vm.setPro(!isPro) })
            }
        }

        Section("Playback") {
            Text("Player engine", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Auto" to 0, "ExoPlayer" to 1, "VLC" to 2).forEach { (lbl, v) ->
                    val on = engine == v
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (on) Accent else Elevated)
                            .clickable { engine = v; vm.setPlayerEngine(v) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(lbl, color = if (on) Ground else TextHi, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
            Text("Automatic (recommended): movies/series via VLC (wide codecs — MKV/AVI/HEVC), live via " +
                "ExoPlayer (Chromecast/PiP/low latency). If ExoPlayer can't open it, VLC takes over.",
                color = TextMute, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))

            OutlinedTextField(
                value = ua, onValueChange = { ua = it }, label = { Text("User-Agent") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = fieldColors()
            )
            Button(onClick = { vm.setUserAgent(ua); msg = "User-Agent saved." },
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }

        Section("Subtitles") {
            Text("Size", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Small" to 0.04f, "Medium" to 0.06f, "Large" to 0.09f).forEach { (lbl, sc) ->
                    val active = kotlin.math.abs(subScale - sc) < 0.001f
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(if (active) Accent else Ground)
                            .clickable { subScale = sc; vm.setSubtitleScale(sc) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) { Text(lbl, color = if (active) Ground else TextHi, fontWeight = FontWeight.Bold) }
                }
            }
            Text("Color", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFFFFFFFF, 0xFFFFD200, 0xFF4FD1C5, 0xFFFF8A00).forEach { argb ->
                    val c = Color(argb)
                    val active = subColor == argb.toInt()
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c)
                            .border(if (active) 3.dp else 1.dp, if (active) Accent else LineSoft, RoundedCornerShape(9.dp))
                            .clickable { subColor = argb.toInt(); vm.setSubtitleColor(argb.toInt()) }
                    )
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Background", color = TextHi, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Switch(checked = subBgOn, onCheckedChange = {
                    subBgOn = it
                    val v = if (it) 0xB0000000.toInt() else 0x00000000
                    vm.setSubtitleBg(v)
                }, colors = SwitchDefaults.colors(checkedThumbColor = Ground, checkedTrackColor = Accent))
            }
        }

        Section("Parental Lock") {
            if (parentalOn) {
                Text("Adult content hidden. Enter PIN to remove.", color = TextDim, fontSize = 13.sp)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it }, label = { Text("PIN") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors()
                )
                Button(onClick = {
                    msg = if (vm.disableParental(pin)) { parentalOn = false; pin = ""; "Parental lock disabled." }
                    else "Wrong PIN."
                }, modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Live)
                ) { Text("Remove Lock", fontWeight = FontWeight.Bold) }
            } else {
                Text("Hide adult categories with a 4+ digit PIN.", color = TextDim, fontSize = 13.sp)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it }, label = { Text("New PIN") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors()
                )
                OutlinedTextField(
                    value = pin2, onValueChange = { pin2 = it }, label = { Text("Repeat PIN") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors()
                )
                Button(onClick = {
                    msg = when {
                        pin.length < 4 -> "PIN must be at least 4 digits."
                        pin != pin2 -> "PINs don't match."
                        else -> { vm.setPin(pin); parentalOn = true; pin = ""; pin2 = ""; "Parental lock enabled." }
                    }
                }, modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Set PIN", fontWeight = FontWeight.Bold) }
            }
        }

        Section("Downloads") {
            Text("Download movies to watch offline; manage them here.",
                color = TextDim, fontSize = 13.sp)
            Button(onClick = onOpenDownloads, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ground)
            ) { Text("Open Downloads", color = Accent, fontWeight = FontWeight.Bold) }
        }

        Section("Recordings") {
            Text("Record live from the player; watch/delete recordings here.",
                color = TextDim, fontSize = 13.sp)
            Button(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ground)
            ) { Text("Open Recordings", color = Accent, fontWeight = FontWeight.Bold) }
        }

        Section("Data") {
            Text("Favorites, likes, watch history and progress are kept on your device.",
                color = TextDim, fontSize = 13.sp)
            Button(onClick = { vm.clearUserData(); msg = "Data cleared." },
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ground)
            ) { Text("Clear Watch Data", color = Live, fontWeight = FontWeight.Bold) }
        }

        Section("Source") {
            Button(onClick = { vm.reload(); msg = "Refreshing content…" }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Refresh Content", color = Ground, fontWeight = FontWeight.Bold) }
            Button(onClick = { vm.signOut(); onSignedOut() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ground)
            ) { Text("Sign out / Change Source", color = Live, fontWeight = FontWeight.Bold) }
        }

        msg?.let { Text(it, color = Accent2, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }

        // Tanılama — son çökme kaydı (varsa) kopyalanıp paylaşılabilir.
        var crash by remember { mutableStateOf(app.cheesino.data.CrashLog.last(ctx)) }
        val clipboard = LocalClipboardManager.current
        crash?.let { c ->
            Section("Diagnostics") {
                Text("A crash report was found — copy and share it with the developer.",
                    color = TextMute, fontSize = 12.sp)
                Text(c.take(1500), color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(c)); msg = "Crash report copied to clipboard" },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Copy", color = Ground, fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { app.cheesino.data.CrashLog.clear(ctx); crash = null },
                        shape = RoundedCornerShape(10.dp)) { Text("Clear", color = TextHi) }
                }
            }
        }

        Section("About") {
            Text("cheesino · version 0.1.0", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Premium media player. Hosts no content — you add your own source. " +
                "Credentials encrypted on device, no telemetry.",
                color = TextMute, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // Veri kaynağı atıfları — Trakt/TMDB lisansları atıf gerektirir.
        Section("Sources") {
            Text("Trending, similar and summary data are provided by Trakt (trakt.tv). " +
                "When TMDB is used: this product uses TMDB and the TMDB APIs but is not " +
                "endorsed or certified by TMDB.",
                color = TextMute, fontSize = 12.sp)
            Text("Ratings by OMDb (omdbapi.com). Powered by Trakt (trakt.tv).",
                color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(title, color = TextDim, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Elevated).padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
    focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
    focusedLabelColor = Accent, unfocusedLabelColor = TextMute
)
