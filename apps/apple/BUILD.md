# cheesino — Xcode Derleme & Çalıştırma Rehberi

Bu repo **dağınık Swift dosyaları + iki yerel paket** içerir (Xcode projesi `.xcodeproj` commit'lenmez).
`project.yml` ile saniyeler içinde bir Xcode projesi üretilir.

---

## 0. Ön koşullar
- **Mac + Xcode 15+** (iOS 16 SDK). App Store'dan güncel Xcode.
- **XcodeGen**: `brew install xcodegen` (veya `brew upgrade xcodegen` — `supportedDestinations` için 2.35+ gerekir).
- (Cihaz/TestFlight için) **Apple Developer Program** üyeliği — simülatör için gerekmez.

## 1. Projeyi üret
```bash
cd apps/apple
xcodegen generate          # project.yml → Cheesino.xcodeproj
open Cheesino.xcodeproj
```
XcodeGen `App/` altındaki tüm Swift'i tek bir çok-platformlu (iOS+tvOS) hedefe koyar, `Packages/Core` ve
`Packages/Design` yerel paketlerini bağlar, `App/Info.plist` ve `AppIcon`'u ayarlar.

## 2. İlk derleme — Simülatör
1. Xcode üst çubukta şema **Cheesino**, hedef **iPhone 15 (simulator)**.
2. **Cmd+B** (build) veya **Cmd+R** (run).

> **İpucu — önce sadece iOS:** tvOS 10-foot ekranları henüz özel değil. İlk yeşil derlemeyi hızlandırmak için
> `project.yml`'de `supportedDestinations: [iOS]` yapıp yeniden `xcodegen generate` diyebilirsin; tvOS'u sonra ekle.

## 3. İlk derlemede beklenen düzeltmeler (dürüst not)
Core + Design paketleri CI'da **yeşil** (swift test geçiyor). Ancak **SwiftUI app target'ı** ilk kez burada
derlenecek — küçük düzeltmeler çıkabilir. Tipik kategoriler ve çözümleri:
- **Platform-availability**: bir API iOS'ta olup tvOS'ta yoksa → `#if os(iOS)` ile sarmalanır (kodda çoğu
  yapıldı: DocumentPicker, segmented picker, navigationBarTitleDisplayMode).
- **Eksik import**: hata satırında Xcode önerir (ör. `import AVFoundation`).
- **Tip çıkarımı**: karmaşık SwiftUI `body`'lerinde "unable to type-check" → ilgili görünümü küçük alt-görünümlere böl.
Xcode hataları tek tek gösterir; yukarıdan aşağı düzelt, tekrar derle. Mimari sağlam; bunlar mekanik düzeltmelerdir.

## 4. Yetenekler (Signing & Capabilities sekmesi)
- **Background Modes → Audio** (arka planda ses). `Info.plist`'te `UIBackgroundModes=audio` zaten var; yine de
  capability olarak eklemek imzayı netleştirir.
- **iCloud → Key-value storage** (cihazlar arası favori/ilerleme senkronu). Yoksa sync sessizce kapalı kalır.
- (PiP AVKit ile otomatik gelir.)

## 5. Gerçek cihazda çalıştırma (asıl test)
1. Xcode → hedefi kendi iPhone'un yap (USB/kablosuz).
2. **Signing & Capabilities → Team**: Apple ID'ni seç (ücretsiz hesap 7 günlük imza verir; ücretli program kalıcı).
3. `PRODUCT_BUNDLE_IDENTIFIER` benzersiz olmalı (ör. `app.cheesino.app` → kendi ters-alan adın).
4. Cmd+R. İlk çalıştırmada cihazda "Ayarlar → VPN & Cihaz Yönetimi → Geliştiriciye Güven".
> **Neden cihaz şart:** HTTP yayınlar (ATS), PiP, arka plan ses, codec'ler ve uzun-oturum stabilitesi
> **yalnız gerçek cihazda** doğrulanır. Simülatör bunların çoğunu yansıtmaz.

## 6. VLCKit (MKV/AVI/TS — Xtream film/dizi/canlı oynatma için GEREKLİ)
Xtream içeriği çoğunlukla MKV/AVI (film/dizi) ve MPEG-TS (canlı) — AVPlayer bunları oynatamaz, VLC oynatır.
Uygulama VLCKit olmadan da derlenir/çalışır (AVPlayer ile HLS/MP4), ama Xtream'in çoğu için VLCKit şart.

**Xcode arayüzünden ekle (önerilen — doğru ürünü görerek seçersin):**
1. Xcode → **File → Add Package Dependencies…**
2. URL yapıştır (biri çalışır):
   - `https://github.com/tylerjonesio/vlckit-spm`
   - (alternatif) `https://github.com/rsaleass/VLCKit-SPM`
3. **Add Package** → paket çözülür (~200MB Git LFS, ilk sefer birkaç dk).
4. Ürün seçim ekranında listelenen ürünü (**MobileVLCKit** veya **VLCKit**) **Cheesino** hedefine ekle.
5. Kod `#if canImport(MobileVLCKit)` ile hazır. Ürün adı `MobileVLCKit` modülü sağlıyorsa otomatik aktifleşir.
   (Modül adı farklıysa — ör. `VLCKit` — bana söyle, `#if canImport` koşulunu ona göre güncelleyeyim.)

> Not: `project.yml`'ye elle koymadım çünkü SPM ürün adı pakete göre değişip "Missing package product"
> hatası veriyor. Xcode UI'dan eklemek en güvenli yol.
> tvOS için TVVLCKit ayrıdır; şu an hedef **iOS-only**.

## 6b. Chromecast (Google Cast — opsiyonel)
Uygulama Cast SDK olmadan da derlenir (`#if canImport(GoogleCast)` seam; buton gizli).
Eklemek istersen:
1. Xcode → **File → Add Package Dependencies…** → `https://github.com/mozilla/GoogleCast-SPM`
   (veya CocoaPods: `pod 'google-cast-sdk'`). Ürünü **Cheesino** hedefine ekle.
2. Kod hazır: `CastSupport.start()` (CheesinoApp init'te) ve oynatıcıda **Cast butonu** otomatik görünür.
3. `Info.plist` zaten hazır: `NSLocalNetworkUsageDescription` + `NSBonjourServices`
   (`_googlecast._tcp`, `_CC1AD845._googlecast._tcp` — varsayılan medya alıcısı).
4. Özel bir receiver app ID kullanacaksan `CastSupport.start()` içindeki `kGCKDefaultMediaReceiverApplicationID`
   yerine kendi ID'ni yaz + Bonjour servisini güncelle.

> Not: Cast, HLS/MP4 (AVPlayer içeriği) için sorunsuz; MKV/TS (VLC içeriği) Chromecast'te native oynamaz
> (alıcı transcoding gerekir) — bu yüzden Cast en iyi HLS canlı/VOD ile çalışır.

## 7. Core birim testleri
- Xcode: şema **Core** → **Cmd+U**. Veya terminal:
```bash
cd apps/apple/Packages/Core && swift test
```
(CI bunu her push'ta otomatik koşar — `.github/workflows/ci.yml`.)

## 8. TestFlight'a yükleme
1. Şema hedefi **Any iOS Device (arm64)**.
2. **Product → Archive**.
3. Organizer → **Distribute App → App Store Connect → Upload**.
4. App Store Connect'te **Internal Testing** grubuna ekle (inceleme yok, 100 kişiye kadar).
5. Export compliance: `Info.plist`'te `ITSAppUsesNonExemptEncryption=false` var → soru sorulmaz.
> External TestFlight / App Store için: [`../../docs/compliance/APP-STORE.md`](../../docs/compliance/APP-STORE.md)
> (App Review notları, iptv-org demo linki, nutrition label, kontrol listesi).

---

## Özet akış
```
brew install xcodegen
cd apps/apple && xcodegen generate && open Cheesino.xcodeproj
→ Cmd+R (simülatör)  →  hata varsa düzelt  →  cihazda test  →  Archive → Internal TestFlight
```
