# cheesino — Android / Android TV

Premium medya oynatıcının Android tarafı. iOS (SwiftUI) kod tabanıyla aynı ürün
dilini ve paylaşılan Xtream/M3U/EPG sözleşmesini (`../../docs/spec/provider-playlist-epg.md`)
takip eder. Tek APK hem telefon/tablet hem Android TV / Google TV'de çalışır.

## Stack

| Katman | Seçim |
|---|---|
| Dil | Kotlin 2.0.20 (JVM 17) |
| UI | Jetpack Compose (BOM 2024.09.02) + Material 3 · Compose for TV |
| Oynatıcı | Media3 / ExoPlayer 1.4.1 (HLS/MP4/TS) |
| Ağ / JSON | OkHttp 4.12 · kotlinx.serialization 1.7.1 |
| Görsel | Coil 2.7 |
| Güvenli depolama | androidx.security EncryptedSharedPreferences (iOS Keychain karşılığı) |
| Build | AGP 8.5.2 · Gradle 8.9 · minSdk 24 · compileSdk 34 |

## Android Studio ile açma

1. Android Studio (Ladybug / 2024.2+) ile **`apps/android`** klasörünü aç
   (repo kökünü değil — proje kökü burası).
2. İlk açılışta Gradle sync gerekli bağımlılıkları indirir.
3. `app` konfigürasyonunu bir cihaz/emülatörde çalıştır (**Run ▶**).
   - Telefon/tablet: normal launcher ikonu.
   - Android TV: `LEANBACK_LAUNCHER` ile TV ana ekranında görünür.

Komut satırından:

```bash
cd apps/android
./gradlew :app:assembleDebug      # APK üret
./gradlew :app:installDebug       # bağlı cihaza kur
```

> Not: Gradle wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`) ikili olduğu için
> repoya eklenmedi. Android Studio ilk sync'te otomatik oluşturur; komut satırında
> gerekiyorsa bir kez `gradle wrapper --gradle-version 8.9` çalıştır.

## Mimari (paketler)

```
app.cheesino
├── core/         # platform-bağımsız iş mantığı (iOS Core paketinin eşi)
│   ├── Models        Channel · SeriesRef · Episode · XtreamCredentials · EpgEntry
│   ├── M3UParser     #EXTINF ayrıştırma + kind tespiti
│   ├── Xtream        player_api.php istemcisi (live/vod/series/auth + URL üretimi)
│   └── StreamResolver aday URL zinciri (.m3u8 → .ts fallback)
├── data/         # CredStore (şifreli) · LibraryViewModel (StateFlow)
└── ui/           # Compose ekranları
    ├── theme/        marka paleti + CheesinoTheme
    ├── RootScreen    alt navigasyon (Ana Sayfa/Canlı/Filmler/Diziler) + player overlay
    ├── OnboardingScreen  Xtream / M3U giriş
    ├── HomeScreen    Son Eklenenler · IMDb · Filmler · Diziler · Canlı rayları
    ├── LiveMoviesScreens  kategori rayları · poster grid
    ├── Components    Rail / ChannelCard / PosterCard
    └── PlayerScreen  ExoPlayer + PlayerView
```

## Olanlar (iOS çekirdek paritesi)

- **Giriş**: Xtream Codes + M3U (URL/metin); kimlik bilgisi cihazda şifreli.
- **Kütüphane**: Canlı / Film / Dizi ayrımı, kategori rayları, poster & kanal kartları, arama.
- **Dizi**: detay ekranı — sezon seçici + thumbnail'li bölüm listesi (`get_series_info`).
- **Kişiselleştirme**: favoriler, beğeni/beğenme, kaldığın yerden devam, izleme geçmişi;
  tür çıkarımı + ağırlıklı skor + çeşitlilik ile "Sana Özel" ve "Devam Et" rayları.
- **Oynatıcı**: Media3 (HLS/MP4/TS); resume diyalogu, ilerleme kaydı, yan jest bölgeleri
  (çift dokunuş ±10 sn, dikey kaydır = parlaklık/ses HUD), buffer göstergesi, otomatik
  yeniden bağlanma, favori/beğeni üst barı.
- **Rehber (EPG)**: xmltv.php / url-tvg → şimdi/sıradaki; canlı catchup/timeshift.
- **Ayarlar**: User-Agent, ebeveyn PIN (yetişkin kategori kilidi), kaynak çıkışı.

## Sırada (gelişmiş farklılaştırıcılar)

Çoklu ekran (2x1/2x2/2x3), spor/maç merkezi, Android TV 10-foot odak arayüzü
(tv-material), Chromecast, çoklu playlist yönetimi.
