# apps/apple — SwiftUI Uygulaması

Tek SwiftUI kod tabanı → **iOS · iPadOS · macOS · tvOS**. iPhone öncelik. Görsel şartname:
[`../../docs/design/ui-preview.html`](../../docs/design/ui-preview.html) (telefon) ·
[`../../docs/design/tvos-preview.html`](../../docs/design/tvos-preview.html) (TV).

## Yapı

```
apps/apple/
  Packages/
    Core/        SwiftPM — M3U/Xtream/EPG ayrıştırma + StreamResolver (bağımlılıksız, test kapsamlı)
    Design/      SwiftPM — "Signal" token'ları (renk/tipografi) + LivePill/QualityBadge/ProgressBar/FocusGlow
  App/
    M3UPlayerApp.swift     @main, dark-committed
    RootView.swift         kaynak yoksa Onboarding, varsa TabView (İzle/Canlı/Rehber/Ara/Kitaplık)
    Stores/LibraryStore.swift   Core'u tüketen @MainActor ObservableObject
    Features/
      HomeView.swift        hero + raylar (Devam Et/Favoriler/Diziler) + ChannelCard
      GuideView.swift       tam zaman-çizelgeli EPG grid + catchup dokunuşu
      PlayerView.swift      AVPlayer + VLCKit fallback + watchdog + resume/favori
      VLCPlayerView.swift   MKV/AVI oynatıcı (#if canImport MobileVLCKit)
      SeriesViews.swift     dizi katalog + detay (sezon/bölüm, get_series_info)
      OnboardingView.swift  M3U/Xtream/Dosya/Keşfet + güven mesajı
      OtherViews.swift      Live/Search/Library/MultiView
    Stores/
      LibraryStore.swift    Core tüketen ana store (kanal/dizi/EPG/favori/recent/progress + iCloud merge)
      KeychainStore.swift   Xtream kimlik bilgisi (şifreli)
      LocalStore.swift      favori/recent/progress kalıcılık + RecentItem/WatchProgress
      CloudStore.swift      iCloud KVS senkron (cihazlar arası favori/ilerleme)
      DiagnosticsMonitor.swift  MetricKit crash/hang (cihazda, telemetri yok)
```

> **iCloud entitlement gerekir:** Xcode → Signing & Capabilities → **iCloud → Key-value storage**
> (`com.apple.developer.ubiquity-kvstore-identifier`). Yoksa sync sessizce devre dışı kalır (çökme yok).

## Xcode kurulumu (Faz 1)

1. Xcode → **New Project → Multiplatform App**, hedefler: iOS, tvOS (macOS opsiyonel).
2. `App/` altındaki `.swift` dosyalarını hedefe ekle; şablonun default `App`/`ContentView` dosyalarını sil.
3. **Add Local Package** ile `Packages/Core` ve `Packages/Design` ekle; her ikisini uygulama hedefine bağla.
4. **VOD/exotik codec fallback** için `MobileVLCKit` (SPM/CocoaPods) ekle → `PlayerView`'de `.vlcKit` adayları için kullan.
5. Yetenekler: Background Modes (Audio, AirPlay, PiP), (Faz 2) iCloud/CloudKit.

## Durum

Faz 1–2: Core entegre; tam Xtream API (live/vod/series + get_series_info), tam EPG grid + catchup,
Keychain kimlik saklama, favori/recent/resume kalıcılık + **iCloud sync**, AVPlayer+VLCKit oynatıcı,
**çoklu ekran** (4 yayın), dosya seçici. Core/Design paketleri CI'da yeşil (swift test). SwiftUI app
target'ı ilk kez Xcode'da derlenecek. Sıradaki: App Store uyum paketi, topluluk kataloğu keşfet (Faz 4), Android (Faz 3).

## Ürün ismi

**cheesino** (her zaman küçük harf wordmark). Marka sabiti tek kaynakta: `App/CheesinoApp.swift` → `enum Brand`.
