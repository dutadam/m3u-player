# cheesino — Gizlilik Politikası / Privacy Policy

_Son güncelleme / Last updated: 2026-07-30_

> Bu metin, uygulama mağazasında **Privacy Policy URL** olarak yayınlanmak üzere hazırlanmıştır
> (ör. GitHub Pages veya kendi alan adınız). Türkçe ve İngilizce birlikte. Uygulamanın _gerçek_
> davranışını yansıtır — mağaza "Data Safety / Veri Güvenliği" formu bununla birebir uyumlu olmalıdır.

---

## Türkçe

**Özet:** cheesino'yu **hesapsız** kullanabilirsiniz — cihazınızdaki videoları oynatmak için hiçbir
veri toplanmaz. **İsteğe bağlı** olarak Google ile giriş yaparsanız, tercihlerinizi cihazlarınız
arasında eşitlemek için sınırlı veri işlenir (aşağıda). Reklam yok, üçüncü-parti takip yok, analitik yok.

### 1. Hesapsız kullanım (varsayılan)
Giriş yapmadan cheesino'yu yerel bir medya oynatıcı olarak kullanabilirsiniz. Bu modda:
- Cihazınızdaki videolara erişim (izin verirseniz) yalnızca **oynatma** içindir; hiçbir dosya
  yüklenmez, taranıp bir yere gönderilmez.
- İzleme ilerlemesi, favoriler, altyazı ayarları ve tercihler **yalnızca cihazınızda** saklanır.

### 2. İsteğe bağlı Google ile giriş
Giriş yaparsanız, Google hesabınızdan şu bilgiler alınır: **ad, e-posta adresi, profil fotoğrafı
ve hesap kimliği (ID)**. Bu, hesabınızı tanımak ve eşitlemeyi mümkün kılmak içindir. Kimlik doğrulama
Google Firebase Authentication ile yapılır.

### 3. İsteğe bağlı bulut eşitleme
Giriş yaptığınızda, aşağıdaki veriler sizin hesabınıza ait özel bir kayıtta (Google Firebase
Firestore) saklanır ve cihazlarınız arasında eşitlenir:
- Favorileriniz ve beğeni/beğenmeme işaretleriniz.
- Eklediyseniz, **ağ medya kaynağınızın giriş bilgileri** (sunucu adresi, kullanıcı adı ve parola) —
  yalnızca kaynağınızın yeni cihazlarınızda otomatik görünmesi için. Bu veri yalnızca **sizin**
  hesabınıza bağlıdır; güvenlik kuralları başkalarının erişimini engeller. Eşitlemeyi kullanmak
  istemiyorsanız giriş yapmayın; giriş yapıp bu bilgiyi eklememeyi de seçebilirsiniz.

Videolarınızın kendisi hiçbir zaman sunucularımızdan geçmez; yalnızca yukarıdaki küçük metin verisi eşitlenir.

### 4. Cihazda saklanan veriler
- İzleme geçmişi/ilerleme, favoriler, tercihler, altyazı ayarları — cihaz yerel depolamasında.
- Ağ medya kaynağınızın giriş bilgileri — cihazda **şifreli** olarak (Android EncryptedSharedPreferences).
- Ebeveyn kilidi PIN'i — geri döndürülemez biçimde (SHA-256) saklanır; düz metin tutulmaz.

### 5. İzinler
- **Video/depolama erişimi:** yalnızca yerel videoları listelemek ve oynatmak için.
- **İnternet / ağ durumu:** eklediğiniz ağ medya kaynağına ve (giriş yaptıysanız) Google Firebase'e
  bağlanmak için.
- **Bildirimler / ön plan servisi / uyanık kalma:** arka planda oynatma ve varsa kayıt/hatırlatıcılar için.
- **Tam zamanlı alarm / açılışta başlatma:** program hatırlatıcıları/zamanlanmış kayıt için (kullanırsanız).

### 6. Üçüncü taraflar
Kimlik doğrulama ve eşitleme için **Google Firebase** (Authentication + Firestore) kullanılır.
Reklam ağı, analitik veya izleyici SDK'sı **kullanılmaz**. Verileriniz reklam amacıyla satılmaz/paylaşılmaz.

### 7. Veri silme
Hesap verinizi silmek için uygulamada çıkış yapıp silme talep edebilir ya da
**privacy@cheesino.app** adresine yazabilirsiniz; hesabınıza bağlı Firestore kaydı silinir.

### 8. Çocuklar
Uygulama genel kitleye yöneliktir; çocuklardan bilerek veri toplamaz. Ebeveyn kilidi (PIN) ile
yetişkin kategorileri gizlenebilir.

### 9. İçerik sorumluluğu
cheesino **nötr bir medya oynatıcıdır**; hiçbir kanal, yayın veya içerik içermez. İzlediğiniz
içeriğin kaynağını ve yasallığını sağlamak kullanıcıya aittir.

### 10. İletişim
Sorular ve veri talepleri için: **privacy@cheesino.app**

---

## English

**Summary:** You can use cheesino **without an account** — no data is collected to play videos on
your device. **Optionally**, if you sign in with Google, limited data is processed to sync your
preferences across devices (below). No ads, no third-party tracking, no analytics.

### 1. Use without an account (default)
Without signing in, cheesino works as a local media player. In this mode:
- Access to videos on your device (if you grant it) is used **only for playback**; no file is
  uploaded, scanned, or sent anywhere.
- Watch progress, favorites, subtitle settings, and preferences are stored **on your device only**.

### 2. Optional Google sign-in
If you sign in, the following is obtained from your Google account: **name, email address, profile
photo, and account ID (UID)**. This identifies your account and enables sync. Authentication uses
Google Firebase Authentication.

### 3. Optional cloud sync
When signed in, the following is stored in a private record tied to your account (Google Firebase
Firestore) and synced across your devices:
- Your favorites and like/dislike marks.
- If you add one, the **sign-in details of your network media source** (server address, username,
  and password) — only so your source appears automatically on your other devices. This data is
  tied to **your** account only; security rules prevent others from accessing it. If you don't want
  sync, don't sign in; you may also sign in without adding this information.

Your videos themselves never pass through our servers; only the small text data above is synced.

### 4. Data stored on device
- Watch history/progress, favorites, preferences, subtitle settings — in local device storage.
- Your network media source credentials — stored **encrypted** on device (Android
  EncryptedSharedPreferences).
- Parental PIN — stored irreversibly (SHA-256); never kept in plain text.

### 5. Permissions
- **Video/storage access:** only to list and play local videos.
- **Internet / network state:** to connect to a network media source you add and (if signed in) to
  Google Firebase.
- **Notifications / foreground service / wake lock:** for background playback and any
  recording/reminders.
- **Exact alarm / boot completed:** for program reminders / scheduled recording (if you use them).

### 6. Third parties
**Google Firebase** (Authentication + Firestore) is used for sign-in and sync. No advertising,
analytics, or tracking SDKs are used. Your data is not sold or shared for advertising.

### 7. Data deletion
To delete your account data, sign out and request deletion in the app, or email
**privacy@cheesino.app**; the Firestore record tied to your account will be deleted.

### 8. Children
The app is intended for a general audience and does not knowingly collect data from children. A
parental PIN can hide adult categories.

### 9. Content responsibility
cheesino is a **neutral media player**; it contains no channels, streams, or content. Ensuring the
source and legality of what you watch is the user's responsibility.

### 10. Contact
Questions and data requests: **privacy@cheesino.app**
