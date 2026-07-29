# Çekirdek Şartname — M3U · provider Codes · EPG · Stream Fallback

> Faz 0 mühendislik temeli. Bu doküman, **Apple (SwiftUI)** ve **Android (Kotlin/Compose)** stack'lerinin
> ortak sözleşmesidir. Amaç: her iki tarafın da aynı davranışı üretmesi. Mevcut PWA (`index.html`) referans
> implementasyondur; satır referansları oradan verilmiştir. Native istemci **CORS ve mixed-content'ten muaf**
> olduğu için PWA'daki proxy/workaround katmanları burada **kaldırılır**.

---

## 1. Veri Modelleri

```
Playlist { id, name, type: m3u_url | m3u_file | provider, url?, provider?{server,user,pass}, count, updatedAt }
Channel  { id, name, logo?, group, url, tvgId?, epgChannelId?, kind: live|vod|series, quality?: 4K|FHD|HD|SD }
Series   { seriesId, name, cover?, plot?, genre?, seasons:[Season] }
Season   { number, episodes:[Episode] }
Episode  { id, seriesId, season, episodeNum, title, ext, thumb?, duration?, url }
EpgEntry { channelId, start, stop, title, desc? }
Progress { url, positionSec, durationSec, updatedAt }   // resume/continue-watching
```

Kimlik bilgileri **Keychain (Apple) / Keystore (Android)** içinde şifreli saklanır — PWA'daki düz `localStorage`
(`saveXtCreds` `index.html:1508`) yerine.

---

## 2. M3U / M3U8 Ayrıştırma

`#EXTM3U` başlığı + `#EXTINF` satırları. Öznitelikler: `tvg-id`, `tvg-logo`, `tvg-name`, `group-title`.
Referans: `parseM3U` (`index.html:724`). Kurallar:

- `#EXTINF:-1 tvg-id="x" tvg-logo="…" group-title="…",<Kanal Adı>` → sonraki non-yorum satır = stream URL.
- Grup yoksa `group-title` = "Diğer" / "Uncategorized".
- **Kalite tespiti** kanal adından: `4K|UHD → 4K`, `FHD|FULLHD → FHD`, `HD → HD`, `SD → SD` (ref: `dq` `index.html:717`).
- **VOD/Dizi tespiti**: `detectSeries` (`index.html:739`) — `SxxExx`, `Sezon N Bölüm M` desenleri; aynı diziyi grupla (`groupBySeries` `:778`).
- `#EXTGRP`, `#KODIPROP`, `url-tvg`/`x-tvg-url`/`tvg-url` (EPG kaynağı) header'ları okunur.
- **User-Agent / Referer** başlık override desteği (streamingnator'dan): bazı portallar özel UA ister.

---

## 3. provider Codes API (`player_api.php`)

Native'de CORS yok → doğrudan çağrılır. Base = normalize edilmiş server (ref: `_xtNorm` `index.html:1491`;
sondaki `/` temizle, şema yoksa `http://` ekle). Auth query: `?username=U&password=P`.

| Amaç | action | Not |
|---|---|---|
| Hesap doğrulama | `get_account_info` (veya boş auth yanıtı `user_info`) | Abonelik durumu + `exp_date` göster |
| Canlı kategoriler | `get_live_categories` | |
| Canlı kanallar | `get_live_streams` (`&category_id=`) | `stream_id`, `epg_channel_id`, `stream_icon` |
| VOD kategoriler | `get_vod_categories` | |
| VOD | `get_vod_streams` (`&category_id=`) | |
| VOD bilgi | `get_vod_info&vod_id=` | plot, cast, TMDB |
| Dizi kategoriler | `get_series_categories` | |
| Diziler | `get_series` (`&category_id=`) | `series_id`, `cover` |
| **Dizi bölümleri** | `get_series_info&series_id=` | **PWA'daki JSON-indir-seç workaround'unu (`index.html:1522-1580`) tamamen değiştirir** |
| Kısa EPG | `get_short_epg&stream_id=&limit=` | oynatıcı now/next |

### Stream URL şemaları
```
Canlı :  {server}/live/{user}/{pass}/{stream_id}.{ts|m3u8}
VOD   :  {server}/movie/{user}/{pass}/{stream_id}.{ext}
Dizi  :  {server}/series/{user}/{pass}/{episode_id}.{ext}     // ext = container_extension (mkv/mp4)
```
Not: iOS native HLS için provider canlı yayınlarda `.m3u8` son eki tercih; `.ts` fallback (ref: `tryNative` `index.html:1285`).

### Catchup / Timeshift (yeni — PWA'da yok)
```
{server}/timeshift/{user}/{pass}/{duration_min}/{YYYY-MM-DD:HH-MM}/{stream_id}.ts
```
Kanal `tv_archive=1` ise EPG bloğundan geçmişe kaydırınca timeshift URL üret.

---

## 4. EPG (XMLTV)

Kaynaklar (öncelik sırası): provider `xmltv.php?username=&password=` → playlist header `url-tvg`/`x-tvg-url` →
kullanıcı-tanımlı URL. Referans: `parseXMLTV` (`index.html:851`), `getEPGNow` (`:868`).

- `<programme start="..." stop="..." channel="...">` → `parseXMLTVDate` (`index.html:843`): `YYYYMMDDHHMMSS ±ZZZZ`.
- **Eşleme**: `channel` id ↔ kanal `tvgId`. Birebir eşleşme yoksa **normalize edilmiş isim fuzzy fallback**
  (büyük/küçük, boşluk, "HD/FHD" ekleri atılarak) — GSE'nin sevilen "otomatik EPG eşleme" davranışı.
- Büyük XMLTV: streaming/parça-parça ayrıştırma + arka planda indeksleme; UI'yi bloklamadan.
- Rehber grid: now-line, catchup için geçmiş bloklar seçilebilir, hatırlatıcı (local notification).

---

## 5. Stream Fallback Algoritması (native port)

PWA'daki `streamSources` (`index.html:1246`) + `playChannel` (`:1260`) mantığının **proxysiz** native karşılığı:

1. **Kaynak listesi üret**: HTTPS ise doğrudan. HTTP ise: `https://host{:port}/path` dene, sonra **orijinal HTTP**
   (native'de mixed-content engeli YOK — bu satır PWA'da sadece http-page'de çalışıyordu; native'de her zaman geçerli).
2. **Oynatıcı seçimi**: HLS/MP4 → **AVPlayer** (Apple) / **Media3/ExoPlayer** (Android). Başarısız veya exotic codec
   (MKV/AVI/…) → **VLCKit** (Apple) / **libVLC veya mpv** (Android).
3. **provider canlı & uzantısız URL** + iOS → `.m3u8` son eki dene (ref: `tryNative` iOS dalı `index.html:1289`).
4. **Watchdog**: MANIFEST/ilk-frame için timeout (~12–15 sn, ref: hls.js config `index.html:1270`). Takılırsa
   sıradaki kaynağa geç. Oynatma sırasında stall → otomatik yeniden bağlan (GSE'nin "60 sn'de donma" regresyonunu hedefler).
5. **Net hata teşhisi**: kaynak tükendiğinde kullanıcıya anlaşılır mesaj (URL geçersiz / sunucu yanıt vermiyor /
   codec desteklenmiyor) — genel "playback failed" değil (streaming-org #14534 dersi).

---

## 6. Kalıcılık & Sync

- Yerel: playlist listesi + kanal cache (büyük → dosya/DB; PWA'da IndexedDB `index.html:689`).
- **İzleme ilerlemesi**: `saveProgress`/`getResume` (`index.html:710`), ≥%95 izlendiyse resume sıfırla.
- **Cross-device**: Apple → **CloudKit** (playlist meta + favori + ilerleme; kimlik bilgisi hariç veya iCloud Keychain).
  Android/diğer → Faz 3'te değerlendir.

---

## 7. PWA'dan Native'e: Neyin Değiştiği

| Konu | PWA (bugün) | Native (hedef) |
|---|---|---|
| HTTP yayın | Mixed-content engeli | **Sorunsuz oynatılır** |
| CORS | 5 public proxy zinciri (`loadURL` `:1411`) | **Gereksiz — kaldırıldı** |
| provider | Sadece m3u_plus linki | **Tam `player_api.php`** |
| Dizi bölümleri | JSON indir → manuel seç | **Otomatik `get_series_info`** |
| EPG | Manuel XMLTV dosya | **Otomatik xmltv.php + fuzzy eşleme** |
| Codec | Sadece HLS (hls.js) | **AVPlayer + VLCKit / ExoPlayer + libVLC** |
| Kimlik bilgisi | Düz localStorage | **Keychain/Keystore şifreli** |
| Arka plan/PiP/AirPlay | Kısıtlı/yok | **Native tam destek** |
