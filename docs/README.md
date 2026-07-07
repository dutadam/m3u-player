# cheesino — Cross-Platform IPTV Uygulaması (Docs)

iOS öncelikli, native-per-ecosystem bir IPTV oynatıcı (**cheesino**). Bu klasör Faz 0 (temel & tasarım) çıktılarını içerir.

## İçindekiler

- **[Tasarım Şartnamesi (görsel)](./design/ui-preview.html)** — iOS UI/UX prototipi ("Signal" yönü).
  Tarayıcıda aç; native SwiftUI implementasyonunun görsel referansı. Palet, tipografi, anahtar ekranlar, tasarım sistemi.
- **[Çekirdek Spec](./spec/xtream-m3u-epg.md)** — M3U/Xtream/EPG sözleşmesi + stream fallback algoritması + PWA→native geçiş tablosu.
- **Tam ürün/teknik plan** — oturum planı (`effervescent-weaving` plan dosyası): rakip analizi, Reddit içgörüleri, mimari, yol haritası.

## Mimari (özet)

```
Paylaşılan Çekirdek (spec → ileride KMP/Rust)
  M3U parser · Xtream API client · XMLTV/EPG · modeller · stream fallback
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

Gelişmiş özellikler · Premium UI/UX & stabilite · Tam Xtream API entegrasyonu · Akıllı içerik & EPG.

## Farklılaşma (kısa)

Rakiplerin (IPTV Smarters, GSE) en zayıf noktaları — **stabilite, arayüz, EPG** — ana saldırı vektörü.
Ek: çoklu ekran + spor merkezi, iCloud sync, gerçek PiP/AirPlay, nötr & gizlilik-öncelikli konumlandırma
(mağaza telif-uyumu için de kritik).

## Sonraki adım

Faz 1 — Apple MVP: SwiftUI iskelet + tam Xtream API + native oynatıcı + otomatik EPG. Tasarım şartnamesinden türetilir.
