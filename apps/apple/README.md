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
      HomeView.swift        hero + yatay raylar + ChannelCard
      GuideView.swift       EPG şimdi/sıradaki (tam grid sonraki adım)
      PlayerView.swift      AVPlayer + StreamResolver fallback + watchdog
      OnboardingView.swift  M3U/Xtream/Dosya/Keşfet + güven mesajı
      OtherViews.swift      Live/Search/Library/MultiView
```

## Xcode kurulumu (Faz 1)

1. Xcode → **New Project → Multiplatform App**, hedefler: iOS, tvOS (macOS opsiyonel).
2. `App/` altındaki `.swift` dosyalarını hedefe ekle; şablonun default `App`/`ContentView` dosyalarını sil.
3. **Add Local Package** ile `Packages/Core` ve `Packages/Design` ekle; her ikisini uygulama hedefine bağla.
4. **VOD/exotik codec fallback** için `MobileVLCKit` (SPM/CocoaPods) ekle → `PlayerView`'de `.vlcKit` adayları için kullan.
5. Yetenekler: Background Modes (Audio, AirPlay, PiP), (Faz 2) iCloud/CloudKit.

## Durum

Faz 1 iskelet: tasarım kod'a döküldü, Core entegre. Derleme Xcode'da yapılır (ortamda toolchain yok).
Sıradaki adımlar: Xtream yanıt modelleri (live/vod/series listeleri), VLCKit entegrasyonu, tam EPG grid,
Keychain kimlik saklama, iCloud sync.

## Ürün ismi

**Cheesino.** Marka sabiti tek kaynakta: `App/CheesinoApp.swift` → `enum Brand`. Wordmark + ikon bu isimle güncellenecek.
