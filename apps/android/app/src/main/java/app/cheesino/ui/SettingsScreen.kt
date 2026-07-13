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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheesino.data.LibraryViewModel
import app.cheesino.ui.theme.*

@Composable
fun SettingsScreen(vm: LibraryViewModel, onClose: () -> Unit, onSignedOut: () -> Unit) {
    var ua by remember { mutableStateOf(vm.userAgent) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var parentalOn by remember { mutableStateOf(vm.parentalEnabled) }
    var subScale by remember { mutableStateOf(vm.subtitleScale) }
    var subColor by remember { mutableStateOf(vm.subtitleColor) }
    var subBgOn by remember { mutableStateOf(vm.subtitleBg != 0) }
    var engine by remember { mutableStateOf(vm.playerEngine) }

    Column(Modifier.fillMaxSize().background(Ground).statusBarsPadding().verticalScroll(rememberScrollState())) {
        // Başlık
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text("Ayarlar", color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }

        Section("Oynatma") {
            Text("Oynatıcı motoru", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Otomatik" to 0, "ExoPlayer" to 1, "VLC" to 2).forEach { (lbl, v) ->
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
            Text("Otomatik (önerilen): film/dizi VLC ile (geniş codec — MKV/AVI/HEVC), canlı yayın " +
                "ExoPlayer ile (Chromecast/PiP/düşük gecikme). ExoPlayer açamazsa VLC devreye girer.",
                color = TextMute, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))

            OutlinedTextField(
                value = ua, onValueChange = { ua = it }, label = { Text("User-Agent") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = fieldColors()
            )
            Button(onClick = { vm.setUserAgent(ua); msg = "User-Agent kaydedildi." },
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
        }

        Section("Altyazı") {
            Text("Boyut", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Küçük" to 0.04f, "Orta" to 0.06f, "Büyük" to 0.09f).forEach { (lbl, sc) ->
                    val active = kotlin.math.abs(subScale - sc) < 0.001f
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(if (active) Accent else Ground)
                            .clickable { subScale = sc; vm.setSubtitleScale(sc) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) { Text(lbl, color = if (active) Ground else TextHi, fontWeight = FontWeight.Bold) }
                }
            }
            Text("Renk", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
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
                Text("Arka plan", color = TextHi, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Switch(checked = subBgOn, onCheckedChange = {
                    subBgOn = it
                    val v = if (it) 0xB0000000.toInt() else 0x00000000
                    vm.setSubtitleBg(v)
                }, colors = SwitchDefaults.colors(checkedThumbColor = Ground, checkedTrackColor = Accent))
            }
        }

        Section("Ebeveyn Kilidi") {
            if (parentalOn) {
                Text("Yetişkin içerik gizli. Kaldırmak için PIN gir.", color = TextDim, fontSize = 13.sp)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it }, label = { Text("PIN") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors()
                )
                Button(onClick = {
                    msg = if (vm.disableParental(pin)) { parentalOn = false; pin = ""; "Ebeveyn kilidi kapatıldı." }
                    else "PIN yanlış."
                }, modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Live)
                ) { Text("Kilidi Kaldır", fontWeight = FontWeight.Bold) }
            } else {
                Text("4+ haneli PIN ile yetişkin kategorileri gizle.", color = TextDim, fontSize = 13.sp)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it }, label = { Text("Yeni PIN") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors()
                )
                OutlinedTextField(
                    value = pin2, onValueChange = { pin2 = it }, label = { Text("PIN tekrar") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors()
                )
                Button(onClick = {
                    msg = when {
                        pin.length < 4 -> "PIN en az 4 hane olmalı."
                        pin != pin2 -> "PIN'ler eşleşmiyor."
                        else -> { vm.setPin(pin); parentalOn = true; pin = ""; pin2 = ""; "Ebeveyn kilidi açıldı." }
                    }
                }, modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("PIN Belirle", fontWeight = FontWeight.Bold) }
            }
        }

        Section("Veriler") {
            Text("Favori, beğeni, izleme geçmişi ve ilerleme cihazında tutulur.",
                color = TextDim, fontSize = 13.sp)
            Button(onClick = { vm.clearUserData(); msg = "Veriler temizlendi." },
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ground)
            ) { Text("İzleme Verilerini Temizle", color = Live, fontWeight = FontWeight.Bold) }
        }

        Section("Kaynak") {
            Button(onClick = { vm.reload(); msg = "İçerik yenileniyor…" }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("İçeriği Yenile", color = Ground, fontWeight = FontWeight.Bold) }
            Button(onClick = { vm.signOut(); onSignedOut() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ground)
            ) { Text("Çıkış Yap / Kaynağı Değiştir", color = Live, fontWeight = FontWeight.Bold) }
        }

        msg?.let { Text(it, color = Accent2, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }

        Section("Hakkında") {
            Text("cheesino · sürüm 0.1.0", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Premium medya oynatıcı. İçerik barındırmaz — kendi kaynağını sen eklersin. " +
                "Kimlik bilgileri cihazda şifreli, telemetri yok.",
                color = TextMute, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
