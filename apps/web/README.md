# cheesino web (PC)

PC için kurulumsuz oynatıcı — tek HTML + küçük bir yerel sunucu/proxy.

## Çalıştırma

```bash
node apps/web/proxy.js
# → http://localhost:8088
```

Tarayıcıda `http://localhost:8088` açın. Kaynağınızı ekleyin (Sunucu veya Bağlantı sekmesi), izlemeye başlayın.

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

## Sınırlar (dürüst liste)

- Codec: tarayıcı ne oynatıyorsa o — HLS (hls.js), MPEG-TS (mpegts.js), MP4 native. **MKV/AVI çoğu tarayıcıda oynamaz** (bu, native uygulamalardaki VLC motorunun web'de karşılığı olmamasından; webin doğal sınırı).
- Dizi bölüm ağacı henüz yok (yakında).
- Veriler yalnız tarayıcının localStorage'ında tutulur.
