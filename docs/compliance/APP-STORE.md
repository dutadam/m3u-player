# cheesino — App Store / TestFlight Uyum Rehberi

streaming oynatıcılar mağazalardan telif baskısıyla sık kaldırılır. Onay şansını yükseltmek ve kaldırılma riskini
azaltmak için **nötr oynatıcı** konumlandırması ve aşağıdaki hazırlık kritiktir.

---

## 1. App Review Notes (App Store Connect → "Notes for Review" alanına yapıştırın)

```
cheesino is a neutral media player. It ships with NO channels, streams, lists, or content of any kind.
To use the app, the user must provide their own source (a stream/list URL, a file, or login credentials
for a streaming service they subscribe to). The app does not host, aggregate, or recommend any content.

HOW TO TEST:
On the onboarding screen choose the "Bağlantı" (Link) tab and paste this public, freely-licensed test
list (a public-domain community list of legal free-to-air channels):
    https://streaming-org.github.io/streaming/index.m3u
Then open any channel to verify playback, guide, and multi-view.

WHY "Allow Arbitrary Loads" (ATS): Many providers serve streams over plain HTTP. The exception is
required to play user-provided HTTP streams; the app itself makes no insecure calls to our own services
(we have none).

Background audio is used so live playback continues when the app is backgrounded.
No accounts, no ads, no analytics, no third-party SDKs. Credentials are stored in the Keychain.
```

> **Not:** İnceleme için gerçek bir abonelik/paralı içerik VERMEYİN. streaming-org gibi **ücretsiz, yasal** bir
> genel M3U kullanın. Böylece incelemeci telifli içerik görmez.

---

## 2. App Privacy ("Nutrition Label" — App Store Connect → App Privacy)

Tüm kategoriler için cevap: **Data Not Collected**.
- Contact Info: No · Identifiers: No · Usage Data: No · Diagnostics: No (MetricKit cihazda kalır, toplanmaz)
- Tracking: **No** (App Tracking Transparency gerektiren izleme yok)

## 3. Export Compliance
- Yalnız standart HTTPS/TLS kullanılır → **muaf**.
- `Info.plist` içine eklendi: `ITSAppUsesNonExemptEncryption = false` (her yüklemede soru sorulmaz).

## 4. Age Rating (Yaş Sınırı)
- Kullanıcı kendi kaynağını getirdiği ve içerik kısıtlanmadığı için **17+** önerilir
  (Unrestricted Web Access / kullanıcı-üretimi içerik). Ebeveyn PIN'i eklenince gözden geçirilebilir.

## 5. Yönerge eşlemesi (App Review Guidelines)
| Yönerge | Durum |
|---|---|
| 2.1 Performance / eksik içerik | İnceleme notundaki streaming-org demo linkiyle çözülür (boş app görünmez) |
| 4.2 Minimum Functionality | Tam özellikli native oynatıcı (EPG, çoklu ekran, catchup) — jenerik/web-wrapper değil |
| 5.2.1 / 5.2.3 Fikri Mülkiyet | Gömülü içerik yok; kullanıcı kendi kaynağını getirir; telifli marka/görsel kullanılmaz |
| 3.1.1 IAP | (İleride premium katman eklenirse) dijital özellikler IAP ile |

## 6. Mağaza metni & görseller (do/don't)
- **Yapma:** Ekran görüntülerinde/metinde telifli kanal logoları, dizi/film afişleri, spor yayını görselleri kullanma.
- **Yap:** Kendi arayüzünü, jenerik/placeholder içerikle göster. "Kendi kaynağınızı getirin" mesajını öne çıkar.
- İsim/anahtar kelimelerde marka adları (beIN, Netflix vb.) kullanma.

## 7. Kaldırılma riskini azaltma (dağıtım dayanıklılığı)
- Yalnız App Store'a bağımlı kalma: **Internal TestFlight** (inceleme yok, 100 kişi) birincil beta kanalı.
- Web landing (mevcut PWA) + gizlilik politikası URL'i hazır tut.
- Reddedilme/kaldırılma olursa hızlı yeniden-gönderim için bu doküman + demo link + notlar el altında.

## 8. Google Play (paralel, Faz 3 Android)
- Aynı nötrlük duruşu. Play "Data safety" formu → veri toplanmıyor.
- streaming kategorisinde benzer telif hassasiyeti; aynı demo-link + içerik-içermeme argümanı geçerli.

---

## Yayın öncesi kontrol listesi
- [ ] Apple Developer Program üyeliği ($99/yıl)
- [ ] Gizlilik politikası bir URL'de yayında (`docs/compliance/PRIVACY.md` → GitHub Pages/alan adı)
- [ ] App Review Notes + streaming-org demo linki girildi
- [ ] App Privacy = Data Not Collected
- [ ] `ITSAppUsesNonExemptEncryption=false` (eklendi)
- [ ] Yaş sınırı 17+
- [ ] Ekran görüntülerinde telifli içerik yok
- [ ] iCloud KVS entitlement (sync için) — Signing & Capabilities
- [ ] Background Modes: Audio (Info.plist'te var)
- [ ] Gerçek cihazda canlı+VOD+dizi+EPG+catchup doğrulandı
