# cheesino — Cross-Platform streaming Uygulaması (Docs)

iOS öncelikli, native-per-ecosystem bir streaming oynatıcı (**cheesino**). Bu klasör Faz 0 (temel & tasarım) çıktılarını içerir.

## İçindekiler

- **[Tasarım Şartnamesi (görsel)](./design/ui-preview.html)** — iOS UI/UX prototipi ("Signal" yönü).
  Tarayıcıda aç; native SwiftUI implementasyonunun görsel referansı. Palet, tipografi, anahtar ekranlar, tasarım sistemi.
- **[Çekirdek Spec](./spec/provider-playlist-epg.md)** — M3U/provider/EPG sözleşmesi + stream fallback algoritması + PWA→native geçiş tablosu.
- **Uyum** — [gizlilik politikası (TR/EN)](./compliance/PRIVACY.md) · [App Store rehberi](./compliance/APP-STORE.md) (App Review notları, nutrition label, export compliance, kontrol listesi).
- **Tam ürün/teknik plan** — oturum planı (`effervescent-weaving` plan dosyası): rakip analizi, Reddit içgörüleri, mimari, yol haritası.

## Mimari (özet)

```
Paylaşılan Çekirdek (spec → ileride KMP/Rust)
  M3U parser · provider API client · XMLTV/EPG · modeller · stream fallback
        │
   ┌────┴─────────────────────────┐
Apple (SwiftUI)              Android/Desktop (Kotlin/Compose)
iOS·iPadOS·macOS·tvOS        Android·Android TV·Desktop
AVPlayer + VLCKit            Media3/ExoPlayer + libVLC/mpv
CloudKit sync
```

## Repo yapısı (Faz 0)

```
apps/apple/      SwiftUI uygulaması (iOS öncelik) — iskele
apps/android/    Kotlin/Compose uygulaması — iskele
docs/design/     Görsel tasarım şartnamesi (ui-preview.html)
docs/spec/       Mühendislik sözleşmeleri
index.html       Mevcut PWA (korunur — hızlı web girişi / landing)
```

## Öncelikler

Gelişmiş özellikler · Premium UI/UX & stabilite · Tam provider API entegrasyonu · Akıllı içerik & EPG.

## Farklılaşma (kısa)

Rakiplerin (streaming Smarters, GSE) en zayıf noktaları — **stabilite, arayüz, EPG** — ana saldırı vektörü.
Ek: çoklu ekran + spor merkezi, iCloud sync, gerçek PiP/AirPlay, nötr & gizlilik-öncelikli konumlandırma
(mağaza telif-uyumu için de kritik).

## Sonraki adım

Faz 1 — Apple MVP: SwiftUI iskelet + tam provider API + native oynatıcı + otomatik EPG. Tasarım şartnamesinden türetilir.
