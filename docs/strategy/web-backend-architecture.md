# cheesino web — Global Backend Mimarisi (nihai)

> Amaç: Web player'ı global açmak için **doğru** güçlü backend. Kritik nokta: "güçlü backend" =
> video akıtan dev bir proxy **DEĞİL**. Yanlış güçlüyü inşa edersek hem para hem hukuk bizi vurur.

## 1. Önce sert gerçek: videoyu SEN akıtırsan batarsın

Bir web proxy'si videoyu sunucun üzerinden geçirir. Global ölçekte iki duvar:

- **Bant genişliği maliyeti:** Her yayın senin sunucundan akar. 1000 eşzamanlı kullanıcı × ~5 Mbps ≈
  **5 Gbps sürekli egress**. Bu, ayda on binlerce dolar. Video-relay bir CDN maliyet merkezidir.
- **CPU:** MKV/AVI transcode ≈ çekirdek başına 1 akış. Binlerce eşzamanlı = transcode çiftliği.
- **Hukuk:** Yayını **sen ilettiğin an** artık "nötr oynatıcı" değilsin — iletim zincirindesin.
  IPTV servislerini kapattıran tam olarak budur. Bugüne kadar koruduğumuz "kullanıcı kendi kaynağını
  getirir, biz içeriğe dokunmayız" tezi, backend videoyu relay ettiği an **çöker**.

**Sonuç:** Video, mümkün olan her yerde **cihaz → kaynak** doğrudan akmalı. cheesino sunucusu video
yolunda olmamalı. Yerel `proxy.js` bir geçici geliştirme aracıdır; global ürünün bel kemiği değildir.

## 2. Doğru mimari: ince backend + istemci-doğrudan oynatma

```
                 ┌────────────────────────────────────────────┐
                 │   cheesino BACKEND (ince, ölçeklenir, yasal) │
                 │  • Hesap + kimlik (JWT)                      │
                 │  • Sync (playlist/favori/resume/ayar)        │
                 │  • Metadata cache (OMDb/TMDb — merkezi)      │
                 │  • OAuth broker (YouTube/Twitch/Trakt gizli) │
                 │  • (opsiyonel) Stream-proxy = Pro/opt-in     │
                 └───────────────┬────────────────────────────┘
        Android · iOS · Web ─────┘   (küçük JSON trafiği; video BURADAN GEÇMEZ)
             │
             └── VIDEO: cihaz → kullanıcının kendi kaynağı (doğrudan, relay yok)
```

- **Native app'ler** (Android/iOS) zaten istemci-taraflı, doğrudan kaynağa bağlanır → **ölçeklenen,
  ucuz, yasal global ürün budur.** Backend yalnız hesap/sync/metadata için gerekir.
- **Web**: tarayıcı HTTP/CORS/MKV'yi istemci-tarafında yapamaz. Bu yüzden web'de üç seçenek var:
  1. **HTTPS + CORS'lu kaynaklar** → proxy'siz doğrudan çalışır (backend videoya dokunmaz). ✅
  2. **HTTP / CORS'suz kaynaklar** → proxy zorunlu. Bunu **kullanıcının kendi çalıştırdığı** (yerel/
     kendi hostu) ya da **Pro'ya özel opt-in** bir hizmet yap — herkese açık ücretsiz relay yapma.
  3. **Native app'e yönlendir** (en iyi deneyim + sıfır relay).

## 3. Backend'in gerçek değeri (video değil — bunlar)

Bunlar global, yasal, ucuz ve TÜM istemcileri (web+android+ios) besler:

| Servis | Ne | Neden backend |
|---|---|---|
| **Hesap/kimlik** | e-posta/OAuth ile giriş, JWT | Cihazlar arası kimlik |
| **Sync** | playlist kaynakları (şifreler istemcide şifreli), favori, resume, ayar | "Her yerde aynı" — TiviMate/GSE'nin sevilen yanı |
| **Metadata cache** | OMDb/TMDb sonuçlarını **merkezi** önbellekle | Kullanıcı başına OMDb kotası yakmaz; hızlı |
| **OAuth broker** | YouTube/Twitch/Trakt token değişimi | Client secret'lar backend'de kalmalı (meta katman) |
| **Kullanım/ödeme** | Pro abonelik doğrulama (App Store/Play/Stripe webhook) | Yetki tek yerden |

## 4. Üretim yığını (öneri)

- **API:** Node + **Fastify** (TypeScript) ya da Go — durumsuz (stateless), yatay ölçeklenir.
- **DB:** **Postgres** (yönetilen: Neon/Supabase/RDS). Başlangıçta SQLite → Postgres'e taşınır.
- **Kimlik:** kendi JWT'imiz ya da Clerk/Auth0; şifreler `argon2`.
- **Cache:** Redis (metadata + rate-limit, çok-instance).
- **Dağıtım:** Fly.io / Render / Railway (Docker), sağlık kontrolü + otomatik ölçekleme.
- **Gözlemlenebilirlik:** yapısal JSON log, `/health`, metrikler.
- **Stream-proxy (opt-in katman, ayrı servis):** durumsuz worker havuzu, **SSRF koruması**,
  IP başına rate-limit, eşzamanlılık tavanı, bant-genişliği-ucuz sağlayıcı (Hetzner/OVH), edge'de
  manifest cache. **Ayrı** tut ki API'yi (ucuz) proxy'nin (pahalı) maliyeti kirletmesin.

## 5. Stream-proxy üretimde güvenli olmalı (opt-in olsa bile)

Herkese açık `?url=` alan bir proxy saldırı yüzeyidir. Zorunlu sertleştirmeler:
- **SSRF koruması:** iç/özel IP'lere (localhost, 10/8, 192.168, 169.254 metadata, ::1) istek **engellenir.**
- **Erişim anahtarı** (var) + **IP başına rate-limit** + **eşzamanlı bağlantı tavanı.**
- İstek/okuma zaman aşımı, yönlendirme limiti, gövde boyutu sınırı.
- Yapısal log + `/health` + `SIGTERM` ile zarif kapanış.
- ToS + kötüye kullanım bildirimi; token dönebilir olmalı.

## 6. Yol haritası (nihai, sırayla)

1. **Backend v1 (API):** hesap + sync + metadata cache. (Bant genişliği ~sıfır, tüm ölçeği kaldırır.)
   → İlk gerçek backend işi bu olmalı; web+android+ios hepsi buna bağlanır.
2. **Web'i buna bağla:** giriş + sync; HTTPS/CORS kaynaklar proxy'siz; HTTP kaynaklar için Pro proxy.
3. **OAuth broker + meta katman** (YouTube/Twitch/Trakt).
4. **Stream-proxy'i ayrı, sertleştirilmiş, opt-in servis** olarak üretime al (bu repodaki proxy.js
   üretim seviyesine çekilir — SSRF/limit/health).
5. **Ölçek:** Postgres + Redis + otomatik ölçekleme + edge cache.

## 7. Karar

- **Yapma:** Herkese ücretsiz, varsayılan **video-relay** backend (maliyet + hukuk = intihar).
- **Yap:** İnce API (hesap/sync/metadata/OAuth) = gerçek güçlü backend; video istemci-taraflı/doğrudan;
  proxy = sertleştirilmiş, opt-in/Pro, ayrı servis.
