# cheesino — İş Planı (Genel)

> Konumlandırma dili: cheesino **nötr bir medya oynatıcı ve izleme merkezidir**. Hiçbir içerik
> barındırmaz; kullanıcı kendi kaynağını ekler. (Belgede teknik terimler yalnızca iç mühendislik
> içindir; üründe/mağazada bu dil kullanılmaz.)

## 1. Vizyon

Tek cümle: **"Tüm izlediklerin ve izleyeceklerin tek yerde."**

İki katmanlı ürün:

- **Çekirdek ürün (gelir kaynağı):** Kullanıcının kendi sunucu-kaynağını ekleyip canlı/film/dizi
  oynattığı **premium native oynatıcı**. Farkımız: stabilite + premium UI + akıllı rehber/EPG +
  çoklu ekran + geniş codec (VLC). Bu, **para kazandıran** taraf.
- **Meta katman (kullanıcı kazandıran, ücretsiz):** YouTube, Twitch, Trakt ve "nerede izlenir"
  entegrasyonlarıyla; kullanıcının **tüm abonelik/izleme dünyasını tek panelde** gördüğü,
  takip ettiği, senkronladığı **izleme merkezi (hub)**. Bu, **büyüme ve elde tutma** tarafı;
  aynı zamanda mağaza onayını kolaylaştıran "gerçek ücretsiz içerik" bacağı.

Meta katman bizi bir "pipe"tan **JustWatch + Trakt + çoklu-servis oynatıcı** karışımı bir platforma taşır.

## 2. İki katman, net ayrım

| | Çekirdek (Pro) | Meta (Ücretsiz) |
|---|---|---|
| Ne | Kullanıcının kendi kaynağından oynatma | Servisler arası keşif + oynatma + senkron |
| Gelir | **Ücretli / Pro abonelik** | Ücretsiz (dönüşüm hunisi) |
| Oynatma | In-app (ExoPlayer/VLC) | YouTube/Twitch resmi oynatıcı; DRM servisleri deep-link |
| Yasal risk | Nötr oynatıcı duruşu | Düşük (resmi API/embed) |
| Öncelik | **ÖNCE bitir (Android)** | Sonra |

## 3. Meta katman — platform listesi (kademeli)

**In-app oynatılabilir (resmi API/embed — yasal):**
- **YouTube** (Data API + resmi IFrame oynatıcı) — abonelik/watch-later
- **Twitch** (Helix API + resmi embed) — takip edilenler/canlı
- **Jellyfin / Emby / Plex** — kullanıcının **kendi medya sunucusu**; açık, %100 yasal, in-app oynatma. Çekirdekle en uyumlu meta parça.
- (İleri) Vimeo, Dailymotion, Kick — resmi embed

**"Nerede izlenir" keşfi + deep-link (DRM — in-app oynatma yok):**
- Netflix, Disney+, Prime Video, HBO/Max, Apple TV+, Paramount+, MUBI
- TR yerel: BluTV, Exxen, Gain, TOD
- Kaynak: **TMDb watch-providers / JustWatch** verisi

**Senkron / takip:**
- **Trakt** (film/dizi geçmişi + watchlist + scrobble) — birincil
- Simkl (alternatif), YouTube/Twitch yerel abonelikler

**Ücretsiz FAST (opsiyonel, ortaklıkla):**
- Pluto TV, Samsung TV Plus, Tubi, Plex FAST

> "Platform sayısını artır" = önce YouTube + Twitch + Trakt + **Jellyfin/Plex** (yasal in-app),
> ardından TMDb "nerede izlenir" ile 10+ DRM servisinin keşfi/deep-link'i.

## 4. Gelir modeli

**Freemium / Pro abonelik** (TiviMate modeli — "kendi verini kullanmak için para" değil, **Pro paket**):

- **Ücretsiz:** meta katman (YouTube/Twitch/Trakt/nerede-izlenir), temel oynatıcı, 1 kaynak.
- **Pro (aylık/yıllık IAP):** çoklu kaynak, **çoklu ekran**, **EPG grid**, kayıt/DVR, cihazlar arası
  senkron, sınırsız favori/liste, gelişmiş altyazı, öncelikli codec (VLC), reklamsız.
- Ödeme: App Store / Play Billing (dijital özellik → IAP zorunlu).
- Fiyat aralığı (öneri): ~₺X/ay, ~₺Y/yıl (yıllıkta indirim). Lansmanda 7 gün deneme.

## 5. Pazar & farklılaşma

- **Boşluk:** iOS/Apple ekosisteminde stabil, güzel, çökmeyen premium oynatıcı kıt (TiviMate iOS'ta yok).
- **Saldırı vektörü:** rakiplerin en zayıf 3 noktası — **stabilite, UI/UX, EPG** — bizim güçlü yanımız.
- **Meta katman kimsede tam yok:** JustWatch keşfeder ama oynatmaz; Trakt takip eder ama oynatmaz;
  oynatıcılar tek kaynağa bakar. **Keşif + oynatma + senkronu tek yerde** birleştiren az.

## 6. Yol haritası (fazlar)

**Faz A — Android çekirdeğini BİTİR (şu an burada):**
- Kalan parite/cila: program hatırlatıcı & bildirim, kayıt/DVR (değerlendir), gerçek puan (OMDb),
  EN dil (i18n), Android TV odak cilası, gerçek-cihaz QA & çökme sertleştirme.
- Pro paywall iskeleti (Play Billing) + Pro özellik kapıları.
- Store gönderimi (Play) + nötr listeleme.

**Faz B — Meta MVP:**
- `Provider` mimarisi (mevcut kaynak = ilk provider).
- YouTube + Twitch (OAuth + resmi oynatıcı), Trakt senkron.
- Jellyfin/Plex (kendi sunucu) provider'ı.

**Faz C — Meta genişleme:**
- TMDb "nerede izlenir" + 10+ DRM servisi keşif/deep-link.
- Birleşik "İzleme Listem" (tüm servisler tek watchlist, Trakt ile senkron).
- FAST kanalları (ortaklıkla).

**Faz D — Ekosistem:**
- Apple tarafı (iOS/tvOS/macOS) parite, cihazlar arası senkron, TV uygulamaları, i18n genişleme.

## 7. Mağaza & yasal duruş

- **Nötr oynatıcı** dili her yerde; içerik barındırmaz; kullanıcı kendi kaynağını ekler.
- Meta katman **resmi API/embed** kullanır (akış sıyırmak yok) → düşük risk + "gerçek ücretsiz içerik".
- Ekran görüntüsü/metinde telifli marka/afiş yok. Yaş 17+, ebeveyn PIN.
- Dağıtım dayanıklılığı: yalnız tek mağazaya bağımlı kalma (TestFlight/web landing yedeği).

## 8. Riskler & azaltma

| Risk | Azaltma |
|---|---|
| Mağaza kaldırması | Nötrlük + içeriksizlik + meta ücretsiz içerik + yedek dağıtım |
| YouTube/Twitch ToS | Yalnız resmi oynatıcı/embed + resmi API; akış sıyırma yok |
| DRM servisleri oynatılamaz | Baştan "keşif + deep-link" olarak konumla (yanlış beklenti yok) |
| Codec/oynatma sorunları | ExoPlayer + VLC çift motor (yapıldı) |
| Tek pazar (TR) bağımlılığı | i18n + Trakt/YouTube ile global meta katman |

## 9. Başarı metrikleri (KPI)

- Aktivasyon: ilk kaynağı ekleyip ilk oynatmaya ulaşan % (ücretsiz meta ile artırılır).
- Elde tutma: D1/D7/D30; meta katman (YouTube/Twitch/Trakt) tutmayı besler.
- Dönüşüm: ücretsiz → Pro %.
- Stabilite: çökmesiz oturum oranı, ortalama oturum süresi, rebuffer oranı.

---

## Özet karar

**Şimdi:** Android çekirdeğini (premium oynatıcı) bitir + Pro paywall + Play gönderimi.
**Sonra:** `Provider` mimarisi üstüne meta katman (YouTube/Twitch/Trakt/Jellyfin → keşif+senkron).
Meta katman ücretsiz ve büyüme motoru; çekirdek Pro ve gelir motoru.
