# apps/apple — SwiftUI Uygulaması (iskele)

Tek SwiftUI kod tabanı → **iOS · iPadOS · macOS · tvOS**. iPhone öncelik.

## Hedef mimari (Faz 1)
- **UI**: SwiftUI, koyu tema (görsel şartname: `../../docs/design/ui-preview.html`).
- **Oynatıcı**: `AVPlayer` (HLS/MP4, AirPlay, PiP, arka plan ses) + `VLCKit` fallback (MKV/AVI/exotik codec).
- **Ağ**: `URLSession` ile Xtream `player_api.php` (CORS/proxy yok). Spec: `../../docs/spec/xtream-m3u-epg.md`.
- **Kalıcılık**: SwiftData/CoreData (kanal cache) + Keychain (kimlik bilgisi) + CloudKit (sync).
- **Modüller (SPM)**: `Core` (parser/API/EPG modelleri), `Player`, `UI`, `Design` (token seti).

## Katmanlar
```
App            → giriş, sekmeler (İzle/Canlı/Rehber/Ara/Kitaplık)
Feature/*      → Home, Guide(EPG), Player, MultiView, Onboarding
Core           → M3UParser, XtreamClient, XMLTVParser, StreamResolver (fallback)
Design         → Color/Type token'ları (ui-preview.html'den)
```

> İskele. Xcode projesi Faz 1 onayıyla eklenir.
