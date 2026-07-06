# apps/android — Kotlin/Compose Uygulaması (iskele)

Tek Compose kod tabanı → **Android · Android TV / Google TV · Desktop (Compose)**. Faz 3.

## Hedef mimari
- **UI**: Jetpack Compose (+ Compose for TV, 10-foot düzen). Görsel şartname: `../../docs/design/ui-preview.html`.
- **Oynatıcı**: `Media3/ExoPlayer` (HLS/DASH) + `libVLC` veya `mpv` (geniş codec) fallback.
- **Ağ**: Ktor/OkHttp ile Xtream `player_api.php`. Spec: `../../docs/spec/xtream-m3u-epg.md`.
- **Kalıcılık**: Room (cache) + EncryptedSharedPreferences/Keystore (kimlik bilgisi).

> İskele. Ortak spec Apple ile paylaşılır; ileride KMP/Rust çekirdeğe refactor değerlendirilir.
