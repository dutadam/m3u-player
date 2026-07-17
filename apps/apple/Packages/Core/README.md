# Core — Paylaşılan İş Mantığı (SwiftPM)

Platform-bağımsız çekirdek: M3U/Xtream/EPG ayrıştırma + stream çözümleme. Harici bağımlılık yok (yalnız Foundation).
iOS · iPadOS · macOS · tvOS ortak modülü. Spec: [`docs/spec/xtream-m3u-epg.md`](../../../../docs/spec/xtream-m3u-epg.md).

## İçerik

| Dosya | Sorumluluk | PWA referansı |
|---|---|---|
| `Models.swift` | Channel/Playlist/EpgEntry/Series + Quality/Xtream normalize | `dq`, `_xtNorm` |
| `M3UParser.swift` | M3U/M3U8 ayrıştırma, öznitelik + dizi/VOD tespiti | `parseM3U`, `detectSeries` |
| `XtreamClient.swift` | `player_api.php` endpoint + stream/timeshift URL + auth | `loadXtream` (proxysiz) |
| `XtreamModels.swift` + `XtreamClient+Fetch.swift` | Yanıt modelleri (tip-dayanıklı) + Channel/Series mapping | dizi JSON workaround'unun yerine |
| `XMLTVParser.swift` | XMLTV EPG (SAX) + tvg-id/fuzzy eşleme indeksi | `parseXMLTV`, `getEPGNow` |
| `StreamResolver.swift` | Kaynak fallback (HTTPS yükseltme + HTTP) + oynatıcı ipucu | `streamSources` |

## Test

```bash
cd apps/apple/Packages/Core
swift test          # veya Xcode: Cmd+U
```

Test kapsamı: M3U ayrıştırma (öznitelik, grup fallback, virgüllü başlık), kalite/dizi tespiti, medya türü
sınıflandırma, Xtream URL üretimi + sunucu normalizasyonu, stream fallback sıralaması, iOS `.m3u8` varyantı,
XMLTV tarih/programme ayrıştırma, EPG fuzzy eşleme.

## Not

Bu ortamda Swift toolchain bulunmadığından kod burada derlenmedi; Xcode/`swift test` altında çalışacak şekilde
yazıldı. Faz 1'de UI katmanı (`apps/apple`) bu modülü tüketir; oynatıcı (AVPlayer + VLCKit) `StreamResolver.Engine`
ipucuna göre seçilir.
