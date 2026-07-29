# cheesino — App Store Yayın Hazırlığı

Bu doküman TestFlight → App Store yolundaki tüm metin/varlık/uyum gereksinimlerini toplar.
Konumlandırma: **nötr medya oynatıcı** — hiçbir içerik gömülü değil, kullanıcı kendi kaynağını getirir.

---

## 1. Uygulama Künyesi

| Alan | Değer |
|---|---|
| İsim | cheesino |
| Alt başlık (subtitle, ≤30) | Premium streaming oynatıcı |
| Birincil kategori | Entertainment |
| İkincil kategori | Utilities |
| Yaş sınırı | 17+ (kullanıcı-üretimi içerik / sınırsız web erişimi) |
| Fiyat | Ücretsiz (ileride IAP: multi-view/sync premium) |
| Bundle ID | app.cheesino.app |

### Anahtar kelimeler (≤100 karakter, virgülle)
```
streaming,m3u,provider,player,epg,playlist,tv,live,vod,series,chromecast,pip,catchup
```

### Açıklama (EN)
```
cheesino is a premium, privacy-first media player for your own streaming subscription.
Bring your provider Codes login, M3U URL, or file — cheesino does the rest.

• Live TV, Movies & Series with a fast, native SwiftUI interface
• Automatic EPG guide with now/next, catchup & program reminders
• Smart recommendations from your watch history (fully on-device)
• Multi-view: watch up to 6 streams at once
• Picture-in-Picture, background audio, AirPlay & Chromecast
• Subtitle & audio sync, subtitle styling, gesture controls
• Multiple playlists with iCloud sync (passwords never leave your device)
• Parental PIN, category management, favorites & resume

cheesino contains NO channels or content. You use your own source.
No accounts. No tracking. No third-party proxies. Credentials are stored
encrypted in the device Keychain.
```

### Açıklama (TR)
```
cheesino, kendi streaming aboneliğin için premium, gizlilik-öncelikli bir medya oynatıcıdır.
provider girişini, M3U bağlantını veya dosyanı getir — gerisini cheesino halleder.

• Canlı TV, Film & Dizi — hızlı, native SwiftUI arayüz
• Otomatik EPG rehberi (şimdi/sıradaki), catchup & program hatırlatıcı
• İzleme geçmişinden akıllı öneriler (tamamen cihazda)
• Çoklu ekran: 6 yayına kadar aynı anda
• PiP, arka plan sesi, AirPlay & Chromecast
• Altyazı/ses senkron, altyazı stili, jest kontrolleri
• Çoklu playlist + iCloud senkron (şifreler cihazdan çıkmaz)
• Ebeveyn kilidi, kategori yönetimi, favoriler & kaldığın yerden devam

cheesino hiçbir kanal veya içerik İÇERMEZ. Kendi kaynağını kullanırsın.
Hesap yok. Takip yok. Üçüncü-parti proxy yok. Kimlik bilgileri cihazda
Keychain ile şifreli saklanır.
```

---

## 2. App Privacy (Nutrition Label) cevapları

**Veri toplama: HAYIR (Data Not Collected).** Uygulama sunucu barındırmaz, analytics/SDK içermez.

- Contact Info: Hayır
- Health, Financial, Location: Hayır
- User Content: Cihazda kalır (playlist, favoriler, ilerleme). Toplanmaz/gönderilmez.
- Identifiers, Usage Data, Diagnostics: Hayır (MetricKit yalnız cihazda; dışa gönderilmez)
- Third-Party Advertising / Tracking: Yok

> iCloud senkron: kullanıcının **kendi** iCloud'u (CloudKit/KVS) üzerinden; Apple altyapısı,
> geliştiriciye veri akmaz. Şifreler senkronlanmaz.

Privacy Policy URL (zorunlu): `https://cheesino.app/privacy` (aşağıdaki metni yayınla).

---

## 3. Gizlilik Politikası (yayınlanacak tam metin)

```
cheesino — Gizlilik Politikası
Son güncelleme: 2026

cheesino ("uygulama") gizliliğini önemser. Bu politika, uygulamanın hangi
verileri işlediğini açıklar.

1. Veri Toplama
cheesino hiçbir kişisel veri toplamaz, sunucularına göndermez veya üçüncü
taraflarla paylaşmaz. Uygulamanın geliştiricinin eriştiği bir sunucusu yoktur.

2. Cihazda Saklanan Veriler
Aşağıdakiler yalnızca cihazınızda saklanır:
- streaming kaynak bilgileri (M3U URL / provider sunucu & kullanıcı adı)
- Şifreler: cihaz Keychain'inde şifreli
- Favoriler, izleme geçmişi/ilerleme, tercihler, beğeniler

3. iCloud Senkron (opsiyonel)
Etkinse, kaynak listeniz ve tercihleriniz sizin iCloud hesabınız üzerinden
cihazlarınız arasında eşitlenir. Bu veri Apple altyapısında kalır; geliştiriciye
iletilmez. Şifreler iCloud ile eşitlenmez.

4. İçerik
cheesino hiçbir medya içeriği barındırmaz veya sağlamaz. Tüm kanallar/akışlar
kullanıcının kendi sağladığı kaynaktan gelir. Uygulama nötr bir oynatıcıdır.

5. Ağ
Uygulama yalnızca sizin girdiğiniz streaming sunucusuna ve (kullanıyorsanız) Apple
iCloud'a bağlanır. Üçüncü-parti proxy veya analitik servis kullanılmaz.

6. Çocuklar
Ebeveyn kilidi (PIN) ile yetişkin kategorileri gizlenebilir.

7. İletişim
Sorular için: privacy@cheesino.app
```

---

## 4. App Review Notes (inceleme ekibine)

```
cheesino is a neutral streaming/media player. It contains NO content, channels, or
playlists. Users must provide their own source (provider Codes account, M3U URL,
or M3U file) — the same model as VLC.

For reviewers to test: use the built-in "Discover" tab which loads the free,
legal, community streaming-org catalog (public domain / free-to-air). No account
needed. [Alternatively we can provide a test provider/M3U on request.]

ATS exception (NSAllowsArbitraryLoads): most streaming portals serve plain HTTP.
The app must connect to user-provided HTTP servers to function. No app-hosted
content is transmitted over HTTP.

No data is collected. No analytics/ad SDKs. Credentials are stored in Keychain.
```

- **Demo için:** Onboarding → **Keşfet** sekmesi (streaming-org) ile hesapsız test edilebilir.

---

## 5. TestFlight kurulumu (adım adım)

1. **App Store Connect** → yeni uygulama oluştur (Bundle ID: `app.cheesino.app`).
2. Xcode → hedef **Cheesino** → Signing & Capabilities → Team seç, otomatik imzalama.
   - Capabilities: **iCloud** (Key-Value Storage), **Background Modes** (Audio, AirPlay, PiP).
3. Sürüm/build: `MARKETING_VERSION` (project.yml) + `CURRENT_PROJECT_VERSION` artır.
4. Xcode → **Product → Archive** → Organizer → **Distribute App → App Store Connect → Upload**.
   - Export compliance: `ITSAppUsesNonExemptEncryption=false` zaten Info.plist'te → soru sorulmaz.
5. App Store Connect → TestFlight → build işlenince (~15dk) **Internal Testing** grubuna ekle.
6. Dış test (External) için: test bilgileri + **Beta App Review** (1-2 gün).

> VLCKit ~200MB (Git LFS) — arşiv boyutu büyük olabilir; ilk upload uzun sürebilir.

---

## 6. Ekran görüntüleri (gerekli boyutlar)

App Store 2 cihaz boyutu ister (6.7" ve 6.5" ya da güncel eşdeğerleri) + iPad 12.9".
Önerilen 6 kare:
1. Ana sayfa — dönen hero + raylar
2. Oynatıcı — film + altyazı/kontroller
3. Çoklu ekran (2×2 spor)
4. Rehber (EPG) + catchup
5. Film detay (poster + TMDB)
6. Spor merkezi / öneriler

> Telifli yayın/logo içeren kareler KULLANMA (Apple 5.2.x). Kendi demo içeriğin
> veya streaming-org free kanallarıyla çek.

---

## 7. Yayın öncesi kontrol listesi

- [ ] App icon 1024 (var: `Assets.xcassets/AppIcon`)
- [ ] Gizlilik politikası URL yayında
- [ ] App Privacy = Data Not Collected
- [ ] Yaş sınırı 17+
- [ ] Review notes + demo yolu (Keşfet)
- [ ] EN + TR lokalizasyon (App Store metinleri)
- [ ] Ekran görüntüleri (telifsiz)
- [ ] Export compliance (muaf)
- [ ] Gerçek cihazda: HTTP oynatma, PiP, arka plan, 60dk soak testi
