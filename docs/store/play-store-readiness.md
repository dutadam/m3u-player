# cheesino — Google Play Yayın Hazırlığı (Android)

Bu doküman, cheesino'nun Google Play'e çıkışı için gereken tüm metin / veri beyanı / imzalama /
kontrol listesi maddelerini toplar. **Konumlandırma:** cheesino bir **medya oynatıcıdır**.
- **Ücretsiz:** cihazdaki/klasördeki videoları oynatan yerel oynatıcı.
- **Pro:** ağ medya kaynağı deneyiminin tamamı (kaynak ekleme, canlı/VOD, rehber, kayıt, çoklu ekran,
  cihazlar arası eşitleme).

> Mağaza metinlerinde **asla** "iptv / xtream / m3u" gibi ifadeler kullanılmaz — bunlar hem marka
> riski hem de mağaza reddi sebebidir. Nötr karşılıklar: "ağ medya kaynağı", "yayın kaynağı",
> "çalma listesi bağlantısı".

---

## 1. Uygulama künyesi

| Alan | Değer |
|---|---|
| Uygulama adı | cheesino |
| Kısa açıklama (≤80) | Videolarını oynat; premium akış deneyimini Pro ile aç. |
| Kategori | Video Players & Editors |
| İçerik derecelendirmesi | IARC anketiyle belirlenir (bkz. §5) |
| Fiyat | Ücretsiz + uygulama içi satın alma (Pro) |
| Package / applicationId | `app.cheesino` |
| versionName / versionCode | `1.0.0` / `1` (çıkışta) |
| Hedef / min SDK | 34 / 24 |

### Kısa açıklama (EN, ≤80)
```
Play your own videos; unlock the premium streaming experience with Pro.
```

### Tam açıklama (TR)
```
cheesino, cihazındaki videolar için hızlı, şık ve reklamsız bir medya oynatıcıdır.

ÜCRETSİZ
• Telefonundaki ve klasörlerindeki videoları anında oynat
• Geniş codec desteği (MP4, MKV, AVI, HEVC ve daha fazlası)
• Kaldığın yerden devam, altyazı yükleme ve senkron, jest kontrolleri
• Arka planda oynatma ve Resim-içinde-Resim (PiP)

PRO (uygulama içi satın alma)
• Kendi ağ medya kaynağını ekle: canlı yayın, film ve diziler
• Otomatik program rehberi (şimdi/sıradaki) ve hatırlatıcılar
• Çoklu ekran, Chromecast, kayıt
• Cihazlar arası eşitleme (favoriler ve tercihler)

cheesino hiçbir kanal veya içerik İÇERMEZ; kaynağını sen getirirsin. Nötr bir oynatıcıdır.
Reklam yok. Üçüncü-parti takip yok.
```

### Tam açıklama (EN)
```
cheesino is a fast, elegant, ad-free media player for the videos on your device.

FREE
• Instantly play videos from your phone and folders
• Broad codec support (MP4, MKV, AVI, HEVC and more)
• Resume, load & sync subtitles, gesture controls
• Background playback and Picture-in-Picture (PiP)

PRO (in-app purchase)
• Add your own network media source: live, movies and series
• Automatic program guide (now/next) and reminders
• Multi-view, Chromecast, recording
• Cross-device sync (favorites and preferences)

cheesino contains NO channels or content; you bring your own source. It is a neutral player.
No ads. No third-party tracking.
```

---

## 2. Data Safety (Veri Güvenliği) formu — Play Console cevap seti

> **KRİTİK:** Bu form gerçek davranışla birebir uyumlu olmalı. cheesino Firebase Auth + Firestore
> kullandığı için "veri toplanmıyor" **denemez**. Aşağıdaki cevaplar `AuthManager.kt` ve
> `SyncRepository.kt` davranışına dayanır.

**Does your app collect or share any of the required user data types?** → **Yes**

| Veri türü | Toplanır? | Paylaşılır? | Zorunlu mu? | Amaç | Not |
|---|---|---|---|---|---|
| Name (ad) | Evet | Hayır | İsteğe bağlı | App functionality (hesap) | Google girişinde |
| Email address | Evet | Hayır | İsteğe bağlı | App functionality (hesap) | Google girişinde |
| User IDs (UID) | Evet | Hayır | İsteğe bağlı | App functionality (hesap/senkron) | Firebase UID |
| Photos/profile image | Evet | Hayır | İsteğe bağlı | App functionality (avatar) | Google profil foto URL'i |
| App activity — favorites/likes | Evet | Hayır | İsteğe bağlı | App functionality (senkron) | Firestore |
| **Credentials — ağ kaynağı parolası** | Evet | Hayır | İsteğe bağlı | App functionality (kaynak senkronu) | Yalnız kullanıcı eklerse; bkz. güvenlik notu |
| Video/Media files | Hayır (toplanmaz) | Hayır | — | Yalnız cihazda oynatma | Yüklenmez |

**Güvenlik uygulamaları (Security practices):**
- "Is your data encrypted in transit?" → **Yes** (Firebase HTTPS/TLS).
- "Do you provide a way to request data deletion?" → **Yes** (uygulama içi çıkış + privacy@cheesino.app).
- "Data collection is optional (users can use the app without it)" → **Evet** — hesapsız yerel oynatma.

> ⚠️ **Güvenlik notu (geliştirici — yayından önce karar):** `SyncRepository.pushSource()` ağ kaynağı
> parolasını Firestore'a **düz metin** yazıyor. Cihazda şifreli tutuluyor ama bulutta değil. Seçenekler:
> (a) buluta yazmadan önce istemci-tarafı şifrele, (b) kaynak-senkronunu tamamen kapat, (c) olduğu gibi
> beyan et. Data Safety'de "Credentials → collected" işaretlendiği sürece mağaza açısından uyumludur,
> ancak (a) güçlü şekilde önerilir.

---

## 3. İzin gerekçeleri (Play Console → App content)

| İzin | Neden | Play beyanı |
|---|---|---|
| `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` (maxSdk 32) | Yerel videoları listeleyip oynatmak | Photo/Video permission — çekirdek işlev |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Ağ kaynağı + Firebase | — |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Arka planda oynatma | FGS declaration — media playback |
| `FOREGROUND_SERVICE_DATA_SYNC` | Kayıt/senkron | FGS declaration — data sync |
| `POST_NOTIFICATIONS` | Oynatma bildirimi/hatırlatıcı | — |
| `WAKE_LOCK` | Oynatma sırasında uyumama | — |
| `SCHEDULE_EXACT_ALARM` | **Zorunlu Console beyanı** — program hatırlatıcısı/zamanlanmış kayıt | Tam alarm gerekçesi yazılmalı; kullanılmıyorsa kaldır |
| `RECEIVE_BOOT_COMPLETED` | Yeniden başlatma sonrası zamanlanmış hatırlatıcıları geri kur | — |

> `SCHEDULE_EXACT_ALARM` Play'de ek inceleme çeker. Kayıt/hatırlatıcı çıkışta yoksa bu izni ve
> `RECEIVE_BOOT_COMPLETED`'ı kaldırıp beyandan da çıkarmak en temizi.

---

## 4. İmzalama ve derleme (AAB)

1. **Upload anahtarı üret** (bir kez):
   ```
   keytool -genkey -v -keystore upload.jks -alias cheesino-upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. `apps/android/keystore.properties` oluştur (örnek: `keystore.properties.example`). Bu dosya
   **gitignore'lu** — sır repoya girmez. Gradle release `signingConfig`'i otomatik okur.
3. **Play App Signing**'i etkinleştir (önerilir): Google dağıtım anahtarını kendi tutar; sen yalnız
   upload anahtarını korursun (kaybolursa sıfırlanabilir).
4. AAB üret:
   ```
   ANDROID_HOME=<sdk> gradle :app:bundleRelease --no-daemon
   ```
   Çıktı: `app/build/outputs/bundle/release/app-release.aab`
5. (İsteğe bağlı, cihazda doğrulandıktan sonra) R8/küçültme: `build.gradle.kts`'te release için
   `isMinifyEnabled = true; isShrinkResources = true`. Koruma kuralları `proguard-rules.pro`'da hazır
   (libVLC keep'leri dahil). Açtıktan sonra gerçek cihazda oynatma + giriş + Pro akışını test et.

---

## 5. İçerik derecelendirmesi (IARC anketi)
- Uygulama içeriği **kullanıcı-üretimi** ve **sınırlanmamış web erişimi** içerdiğinden (Pro'da ağ
  kaynağı), ankette "kullanıcı kendi içeriğini getirir / filtrelenmemiş internet" dürüstçe işaretlenir.
- Bu genelde **Teen/Mature** bandına düşürür — beyanı gerçeğe uygun tut; düşük göstermek yayından
  kaldırma sebebidir.

---

## 6. Mağaza görselleri (senin hazırlaman gerek)
| Varlık | Boyut | Not |
|---|---|---|
| Uygulama ikonu | 512×512 PNG | `docs/design/brand/icon-source.svg`'den üret |
| Öne çıkan görsel (feature graphic) | 1024×500 PNG | Zorunlu |
| Telefon ekran görüntüleri | ≥2, 16:9 veya 9:16 | Telifsiz içerikle çek |
| (varsa) TV afişi | 1280×720 | Android TV listeleniyorsa |

> Ekran görüntülerinde telifli yayın/logo **kullanma**. Kendi demo videon veya telifsiz içerikle çek.

---

## 7. Yayın öncesi kontrol listesi
- [ ] versionName `1.0.0`, versionCode `1`
- [ ] Gizlilik politikası bir URL'de yayında (`docs/compliance/PRIVACY.md`)
- [ ] **Data Safety formu §2 ile birebir** (Firebase veri toplama beyan edildi)
- [ ] İçerik derecelendirmesi anketi dürüstçe dolduruldu (§5)
- [ ] `SCHEDULE_EXACT_ALARM` beyanı yazıldı **veya** izin kaldırıldı
- [ ] Upload keystore üretildi + Play App Signing açık
- [ ] AAB imzalı üretildi (`bundleRelease`)
- [ ] Görseller yüklendi (telifsiz)
- [ ] Kapalı test (Closed testing) grubu — en az 14 gün / 12 test kullanıcısı (yeni hesap kuralı)
- [ ] Gerçek cihazda soak: yerel oynatma, altyazı, PiP, arka plan, Pro satın alma, giriş/senkron
- [ ] (varsa) `pushSource` düz-metin parola kararı verildi (§2 güvenlik notu)
