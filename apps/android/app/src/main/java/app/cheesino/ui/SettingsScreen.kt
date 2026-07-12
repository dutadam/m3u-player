package app.cheesino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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

    Column(Modifier.fillMaxSize().background(Ground).statusBarsPadding().verticalScroll(rememberScrollState())) {
        // Başlık
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi) }
            Text("Ayarlar", color = TextHi, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }

        Section("Oynatma") {
            OutlinedTextField(
                value = ua, onValueChange = { ua = it }, label = { Text("User-Agent") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Button(onClick = { vm.setUserAgent(ua); msg = "User-Agent kaydedildi." },
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
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

        Section("Kaynak") {
            Button(onClick = { vm.signOut(); onSignedOut() }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Elevated)
            ) { Text("Çıkış Yap / Kaynağı Değiştir", color = Live, fontWeight = FontWeight.Bold) }
        }

        msg?.let { Text(it, color = Accent2, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }

        Text("cheesino içerik barındırmaz — kendi kaynağını getirirsin. Veriler cihazda tutulur, telemetri yok.",
            color = TextMute, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
    focusedBorderColor = Accent, unfocusedBorderColor = LineSoft,
    focusedLabelColor = Accent, unfocusedLabelColor = TextMute
)
