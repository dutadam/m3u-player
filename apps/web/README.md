# cheesino web (PC)

PC için kurulumsuz oynatıcı — tek HTML + küçük bir yerel sunucu/proxy.

## En kolay test (3 yol)

**1) Hemen dene (kurulum yok):** GitHub Pages'e yayınlanan URL'yi telefondan/PC'den aç,
Onboarding'de **"Demo içerikle dene"**ye bas → ücretsiz kanallarla anında çalışır.
*(HTTPS kaynaklar ve demo çalışır; düz HTTP kaynaklar için 2. yol gerekir.)*

**2) Tam güç (kendi kaynağın · HTTP dahil):** Tek komut —
```bash
./apps/web/start.sh          # macOS/Linux (tarayıcıyı da açar)
apps\web\start.bat           # Windows (çift tıkla)
# ya da elle:  node apps/web/proxy.js  →  http://localhost:8088
```
Yalnız **Node** gerekir. **ffmpeg** de kuruluysa MKV/AVI otomatik açılır.

**3) Sadece bir kaynak dene:** proxy'yi çalıştır, `http://localhost:8088` aç, Sunucu/Bağlantı ile ekle.

## HTTP / HTTPS sorunu nasıl çözülüyor?

Tarayıcıların iki sert kuralı web oynatıcıları bozar:

1. **Mixed-content:** HTTPS bir sayfa, HTTP bir yayını **asla** oynatamaz. Kaynak sunucuların çoğu düz HTTP'dir.
2. **CORS:** Kaynak sunucular tarayıcıya izin başlığı göndermez; `fetch`/HLS istekleri engellenir.

`proxy.js` ikisini birden çözer:

- Uygulamayı **http://localhost:8088** üzerinden sunar → sayfa HTTP olduğu için mixed-content kuralı hiç devreye girmez.
- Tüm içerik/akış istekleri `/proxy?url=...` üzerinden **sunucu tarafında** çekilir → CORS sorunu kalmaz.
- m3u8 manifestlerinin içindeki segment ve anahtar adresleri de otomatik proxy'ye yeniden yazılır.

Uygulama, proxy ile açıldığını **otomatik algılar** (`/proxy-ping`) — ek ayar gerekmez. Sayfayı başka bir yerden (ör. GitHub Pages) açarsanız Ayarlar'dan ayrı çalışan bir proxy adresi girebilirsiniz.

> **Güvenlik:** Proxy'yi internete açmayın; kimlik bilgileri URL'lerden geçer. Yalnız kendi makinenizde kullanın.

## MKV / AVI desteği (ffmpeg ile — otomatik)

Tarayyıcılar MKV/AVI **konteynerini** okuyamaz (içindeki H.264/AAC'yi çoğu zaman çözebildiği halde).
Jellyfin/Plex/Stremio'nun kullandığı çözümün aynısı entegre edildi: makinede **ffmpeg** kuruluysa
`proxy.js` bunu algılar ve MKV/AVI istekleri anlık olarak tarayıcının oynatabildiği **parçalı MP4'e**
çevrilir (`/remux` ucu):

- **MKV** → video akışı aynen kopyalanır (`-c:v copy`, CPU ~sıfır), ses AAC'ye çevrilir (AC3/DTS tarayıcıda yok).
- **AVI/WMV/FLV** → eski video codec'leri (XviD vb.) H.264'e çevrilir (CPU kullanır).
- Kopyalama başarısız olursa otomatik transcode'a düşülür; süre/seek `ffprobe` ile sağlanır
  (çubukta ileri-geri sarma, o saniyeden yeni akış açarak çalışır).

ffmpeg kurulumu: Windows `winget install ffmpeg` · macOS `brew install ffmpeg` · Linux `apt install ffmpeg`.
Ayarlar ekranı ffmpeg'in algılanıp algılanmadığını gösterir.

## Sınırlar (dürüst liste)

- ffmpeg **kurulu değilse** MKV/AVI oynamaz (HLS/TS/MP4 her durumda çalışır).
- Remux akışında sarma, o konumdan yeni akış açarak yapılır (anlık atlama yerine ~1 sn yeniden başlatma).
- Dizi bölüm ağacı henüz yok (yakında).
- Veriler yalnız tarayıcının localStorage'ında tutulur.
