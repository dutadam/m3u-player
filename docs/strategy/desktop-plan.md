# cheesino — Masaüstü (PC) Uygulama Planı (Compose Desktop)

> Dürüst çerçeve: Masaüstü, TV cilası gibi mevcut modül içinde yapılabilecek küçük bir iş **değil**.
> Uygulama şu an derinlemesine Android'e bağlı; masaüstü için **Compose Multiplatform (CMP)** yeniden
> yapılandırması gerekir. Bu doküman o yolu ve neyin taşınabilir olduğunu netleştirir.

## 1. Mevcut bağımlılık haritası (ne taşınır, ne taşınmaz)

| Katman | Dosyalar | Masaüstü uyumu |
|---|---|---|
| **core** | Models, M3UParser, GenreTagger, AdultFilter, SportsFinder, StreamResolver, Recommender, RailEngine | ✅ Saf Kotlin — doğrudan taşınır |
| core | Xtream, OmdbClient (OkHttp) | ✅ OkHttp JVM — Android+Desktop ortak (jvmMain) |
| core | **XmltvParser** (`android.util.Xml`, `org.xmlpull`) | ⚠️ Android XML → JVM `javax.xml`/kotlinx ile değiştir |
| **data** | CredStore/UserDataStore/AppSettings/ContentCache/MultiViewStore (SharedPreferences, filesDir), Reminders (AlarmManager), LibraryViewModel (AndroidViewModel) | ⚠️ Depolama + ViewModel soyutlanmalı |
| **ui** | Compose ekranlar | 🟡 Material3 çoklu-platform; ama Coil + AndroidView oynatıcılar değişmeli |
| ui | AsyncImage (Coil 2) | ⚠️ Coil 3 (multiplatform) veya Kamel'e geç |
| ui | PlayerScreen/VlcPlayerScreen (ExoPlayer, libVLC-android, AndroidView) | ❌ Masaüstü oynatıcıya (vlcj) yeniden yazılmalı |

**Özet:** İş mantığının ~%80'i taşınabilir; oynatıcı, depolama ve görsel-yükleyici platforma özel.

## 2. Hedef mimari (KMP / Compose Multiplatform)

```
shared/ (Kotlin Multiplatform)
  commonMain/  → modeller, UI (Compose), ViewModel mantığı, parser sözleşmeleri
  jvmMain/     → OkHttp istemcileri, XMLTV (javax.xml)  [Android + Desktop ortak]
  androidMain/ → SharedPreferences store, ExoPlayer/libVLC oynatıcı
  desktopMain/ → java.util.prefs store, vlcj oynatıcı

apps/android  → ince kabuk (MainActivity → shared App())
apps/desktop  → main() { application { Window { App() } } }
```

Soyutlanacak arayüzler:
- `KeyValueStore` / `FileStore` — Android (SharedPreferences/filesDir) · Desktop (Preferences/Files)
- `PlayerController` — Android (ExoPlayer+libVLC) · Desktop (**vlcj** + SwingPanel)
- `ImageLoader` — Coil 3 (her iki platform) veya platform-özel
- `Reminders` — Android (AlarmManager) · Desktop (java ScheduledExecutor + tepsi bildirimi)

`LibraryViewModel` → `AndroidViewModel` yerine **çoklu-platform `ViewModel`** (androidx.lifecycle 2.8+ artık KMP) ya da sade bir sınıf + `CoroutineScope`.

## 3. Masaüstü oynatıcı seçenekleri

| Seçenek | Artı | Eksi |
|---|---|---|
| **vlcj** (libVLC Java) + SwingPanel | Geniş codec (mevcut VLC deneyimiyle tutarlı), olgun | Kullanıcıda VLC/libVLC gerekebilir; Swing köprüsü |
| JavaFX MediaView | JVM-yerel | Sınırlı codec (HLS/MKV zayıf) |
| mpv (JNI) | En güçlü | Entegrasyon zor |

**Öneri:** **vlcj** — Android'de zaten VLC motoruna geçtik; masaüstünde tutarlı codec desteği.

## 4. Fazlama (her adım derleme doğrulaması ister)

1. **Shared çekirdek çıkar:** `core`'u commonMain/jvmMain'e taşı, XmltvParser'ı JVM XML ile değiştir. → Android hâlâ derleniyor mu doğrula.
2. **Soyutlamalar:** `KeyValueStore` + `PlayerController` arayüzleri; Android implementasyonlarını mevcut koddan üret.
3. **UI'yı commonMain'e taşı:** Coil 2 → Coil 3; oynatıcı çağrılarını `PlayerController`'a bağla.
4. **Desktop modülü:** `apps/desktop` + Compose Desktop plugin; `java.util.prefs` store + vlcj oynatıcı.
5. **Paketleme:** Compose Desktop `jpackage` ile Windows/macOS/Linux dağıtımı (.msi/.dmg/.deb).

## 5. Neden şimdi kör kod dökmüyorum

- Bu bir **gradle + kaynak-set yeniden yapılandırması**; yanlış yapılırsa **çalışan Android derlemesini bozar**.
- Her adımın **gerçek `./gradlew build` geri bildirimi** gerekir — bu ortamda derleyemiyorum.
- Yarım bir `apps/desktop` iskeleti (gerçek kodu paylaşmayan) ölü koddur; fayda sağlamaz.

## 6. Öneri

İki yol:
- **(A) Kademeli KMP çıkarımı** — ben adım adım sürüklerim (önce `core`'u paylaşılabilir yap → sen `./gradlew :apps:android:assembleDebug` ile doğrula → sonraki adım). Güvenli ama senin build geri bildiriminle ilerler.
- **(B) Hafif bağımsız masaüstü yoldaşı** — yalnız saf parser'ları (kopya) + OkHttp + vlcj kullanan ayrı basit bir Compose Desktop app. Hızlı ayağa kalkar ama mantığı bir süre çoğaltır; sonra (A)'ya birleştirilir.

**Tavsiyem:** Faz sırası gereği önce Android çekirdeğini kapat (EN dil dahil) + gerçek cihaz QA; masaüstünü **(A) kademeli KMP** ile, build alabildiğimiz bir noktada başlat. iOS/macOS zaten SwiftUI tarafında; masaüstü "tek kod her yerde" hedefi için CMP doğru ama en maliyetli parça.
