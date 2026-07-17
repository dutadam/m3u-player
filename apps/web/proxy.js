#!/usr/bin/env node
/**
 * cheesino web — yerel sunucu + akış proxy'si (sıfır bağımlılık).
 *
 * NEDEN VAR: Tarayıcılar HTTPS sayfadan HTTP yayın oynatmaz (mixed-content) ve çoğu
 * kaynak sunucusu CORS başlığı göndermez. Bu betik iki sorunu birden çözer:
 *   1. Uygulamayı http://localhost:8088 üzerinden sunar → sayfa HTTP olduğundan
 *      mixed-content kuralı hiç devreye girmez.
 *   2. /proxy?url=... ucu hedefi sunucu tarafında çeker, CORS başlıkları ekler,
 *      m3u8 manifestlerinin içindeki segment/anahtar adreslerini de proxy'ye çevirir.
 *
 * Kullanım:  node proxy.js   →  http://localhost:8088
 * UYARI: Bu proxy'yi internete AÇMAYIN — kimlik bilgileri URL'lerden geçer; yalnız
 * kendi makinenizde (localhost) kullanın.
 */
const http = require("http");
const https = require("https");
const fs = require("fs");
const path = require("path");
const dns = require("dns");
const net = require("net");
const { URL } = require("url");
const { spawn, spawnSync } = require("child_process");

const PORT = process.env.PORT || 8088;
const HOST = process.env.HOST || "0.0.0.0";
const ROOT = __dirname;
// İnternete açık dağıtım için opsiyonel erişim anahtarı. Ayarlandıysa /proxy /remux /probe
// uçları ?k=TOKEN ister; yerelde (localhost) boş bırakılır → açık.
const TOKEN = process.env.PROXY_TOKEN || "";
function authed(u) { return !TOKEN || u.searchParams.get("k") === TOKEN; }

// ---- Üretim sertleştirmesi ----
// SSRF: public dağıtımda (token varsa) iç/özel IP'lere istek engellenir; yerelde (LAN kaynağı
// için) açık. PROXY_BLOCK_PRIVATE=1/0 ile elle geçilebilir.
const BLOCK_PRIVATE = process.env.PROXY_BLOCK_PRIVATE != null
  ? process.env.PROXY_BLOCK_PRIVATE === "1" : !!TOKEN;
const MAX_UPSTREAMS = parseInt(process.env.PROXY_MAX_CONN || "300", 10);   // eşzamanlı upstream tavanı
const RATE_MAX = parseInt(process.env.PROXY_RATE || "240", 10);            // IP başına / dakika
let activeUpstreams = 0;
const rate = new Map();  // ip -> {n, reset}

function clientIp(req) {
  return (req.headers["x-forwarded-for"] || "").split(",")[0].trim()
    || req.socket.remoteAddress || "?";
}
function rateOk(ip) {
  const now = Date.now(); let r = rate.get(ip);
  if (!r || now > r.reset) { r = { n: 0, reset: now + 60000 }; rate.set(ip, r); }
  return ++r.n <= RATE_MAX;
}
setInterval(() => { const now = Date.now(); for (const [ip, r] of rate) if (now > r.reset) rate.delete(ip); }, 120000).unref();

/** Bir IP özel/dahili/ayrılmış mı? (SSRF koruması) */
function isPrivateIp(ip) {
  if (!ip) return true;
  if (ip.startsWith("::ffff:")) ip = ip.slice(7);      // IPv4-mapped IPv6
  if (net.isIPv4(ip)) {
    const p = ip.split(".").map(Number);
    if (p[0] === 10 || p[0] === 127 || p[0] === 0) return true;
    if (p[0] === 172 && p[1] >= 16 && p[1] <= 31) return true;
    if (p[0] === 192 && p[1] === 168) return true;
    if (p[0] === 169 && p[1] === 254) return true;      // link-local + bulut metadata
    if (p[0] === 100 && p[1] >= 64 && p[1] <= 127) return true; // CGNAT
    if (p[0] >= 224) return true;                        // multicast + reserved
    return false;
  }
  const l = ip.toLowerCase();
  if (l === "::1" || l === "::") return true;
  if (l.startsWith("fe80") || l.startsWith("fc") || l.startsWith("fd")) return true; // link-local + ULA
  return false;
}
/** Host'u çözüp tüm IP'lerini kontrol et; biri özelse reddet (DNS rebinding'e karşı). */
function ssrfCheck(hostname, cb) {
  if (!BLOCK_PRIVATE) return cb(null);
  if (net.isIP(hostname)) return cb(isPrivateIp(hostname) ? new Error("özel IP engellendi") : null);
  dns.lookup(hostname, { all: true }, (err, addrs) => {
    if (err) return cb(err);
    if (addrs.some(a => isPrivateIp(a.address))) return cb(new Error("özel IP engellendi"));
    cb(null);
  });
}
function log(o) { try { process.stdout.write(JSON.stringify({ t: new Date().toISOString(), ...o }) + "\n"); } catch {} }

// ffmpeg varsa MKV/AVI de oynar: konteyner anlık olarak fMP4'e çevrilir
// (Jellyfin/Stremio'nun yerel sunucularıyla aynı yaklaşım).
const HAS_FFMPEG = (() => {
  try { return spawnSync("ffmpeg", ["-version"], { stdio: "ignore" }).status === 0; }
  catch { return false; }
})();
const HAS_FFPROBE = (() => {
  try { return spawnSync("ffprobe", ["-version"], { stdio: "ignore" }).status === 0; }
  catch { return false; }
})();
const MIME = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css",
  ".png": "image/png", ".svg": "image/svg+xml", ".ico": "image/x-icon",
  ".webmanifest": "application/manifest+json", ".json": "application/json"
};

function cors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "*");
  res.setHeader("Access-Control-Expose-Headers", "*");
}

/** Hedefi indir; 30x yönlendirmeleri izle (en çok 5). */
function fetchUrl(target, headers, maxRedirect, cb) {
  let u;
  try { u = new URL(target); } catch (e) { return cb(e); }
  if (u.protocol !== "http:" && u.protocol !== "https:") return cb(new Error("yalnız http/https"));
  // SSRF: hedef host özel/dahili IP'ye çözülüyorsa reddet.
  ssrfCheck(u.hostname, (err) => {
    if (err) return cb(err);
    const mod = u.protocol === "https:" ? https : http;
    const req = mod.request(u, { method: "GET", headers, timeout: 20000 }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location && maxRedirect > 0) {
        res.resume();
        return fetchUrl(new URL(res.headers.location, u).href, headers, maxRedirect - 1, cb);
      }
      cb(null, res, u.href);
    });
    req.on("timeout", () => req.destroy(new Error("zaman aşımı")));
    req.on("error", (e) => cb(e));
    req.end();
  });
}

/** m3u8 içindeki segment/alt-manifest/anahtar adreslerini proxy'ye çevir. */
function rewriteM3u8(text, baseUrl) {
  const kq = TOKEN ? "&k=" + encodeURIComponent(TOKEN) : "";
  const prox = (v) => {
    try { return "/proxy?url=" + encodeURIComponent(new URL(v, baseUrl).href) + kq; }
    catch { return v; }
  };
  return text.split(/\r?\n/).map((line) => {
    if (!line.trim()) return line;
    if (line.startsWith("#")) return line.replace(/URI="([^"]+)"/g, (_, v) => `URI="${prox(v)}"`);
    return prox(line.trim());
  }).join("\n");
}

const server = http.createServer((req, res) => {
  const u = new URL(req.url, "http://localhost");

  if (req.method === "OPTIONS") { cors(res); res.writeHead(204); return res.end(); }

  // Sağlık kontrolü (load balancer / autoscaler).
  if (u.pathname === "/health") {
    cors(res); res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ ok: true, ffmpeg: HAS_FFMPEG, active: activeUpstreams }));
  }

  // Uygulamanın "proxy var mı?" otomatik algısı için.
  if (u.pathname === "/proxy-ping") { cors(res); res.writeHead(204); return res.end(); }

  // Pahalı uçlarda IP başına oran sınırı.
  if ((u.pathname === "/proxy" || u.pathname === "/remux" || u.pathname === "/probe") && !rateOk(clientIp(req))) {
    cors(res); res.writeHead(429); return res.end("çok fazla istek");
  }

  // ffmpeg var mı? (MKV/AVI desteğinin algısı)
  if (u.pathname === "/ffmpeg-ping") {
    cors(res); res.writeHead(HAS_FFMPEG ? 204 : 404); return res.end();
  }

  // Süre sorgusu — remux akışında seek çubuğu için (ffprobe).
  if (u.pathname === "/probe") {
    const target = u.searchParams.get("url");
    cors(res);
    if (!authed(u)) { res.writeHead(403); return res.end("{}"); }
    if (!target || !HAS_FFPROBE) { res.writeHead(404); return res.end("{}"); }
    const p = spawn("ffprobe", ["-v", "error", "-show_entries", "format=duration",
      "-of", "json", target], { stdio: ["ignore", "pipe", "ignore"] });
    let out = "";
    p.stdout.on("data", (d) => (out += d));
    p.on("close", () => {
      let dur = 0;
      try { dur = parseFloat(JSON.parse(out).format.duration) || 0; } catch {}
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ duration: dur }));
    });
    setTimeout(() => { try { p.kill("SIGKILL"); } catch {} }, 15000);
    return;
  }

  /**
   * MKV/AVI çözümü: /remux?url=...&mode=copy|transcode&start=saniye
   * - copy: video akışı aynen kopyalanır (H.264/HEVC), ses AAC'ye çevrilir (AC3/DTS
   *   tarayıcıda yok) → CPU ~sıfır. MKV için varsayılan.
   * - transcode: video da H.264'e çevrilir (AVI/XviD gibi tarayıcının çözemediği
   *   codec'ler için) → CPU kullanır.
   * Çıktı: parçalı (fragmented) MP4, pipe ile anlık akıtılır — dosyanın inmesi beklenmez.
   */
  if (u.pathname === "/remux") {
    const target = u.searchParams.get("url");
    cors(res);
    if (!authed(u)) { res.writeHead(403); return res.end("yetki yok"); }
    if (!target) { res.writeHead(400); return res.end("url gerekli"); }
    if (!HAS_FFMPEG) { res.writeHead(501); return res.end("ffmpeg kurulu değil"); }
    const mode = u.searchParams.get("mode") === "transcode" ? "transcode" : "copy";
    const start = parseFloat(u.searchParams.get("start") || "0") || 0;
    const args = ["-hide_banner", "-loglevel", "error"];
    if (start > 0) args.push("-ss", String(start));
    args.push("-i", target);
    if (mode === "copy") args.push("-c:v", "copy");
    else args.push("-c:v", "libx264", "-preset", "veryfast", "-crf", "23");
    args.push("-c:a", "aac", "-ac", "2", "-b:a", "192k",
      "-f", "mp4", "-movflags", "frag_keyframe+empty_moov+default_base_moof", "pipe:1");
    const ff = spawn("ffmpeg", args, { stdio: ["ignore", "pipe", "pipe"] });
    cors(res);
    res.writeHead(200, { "content-type": "video/mp4" });
    ff.stdout.pipe(res);
    let err = "";
    ff.stderr.on("data", (d) => { err += d; if (err.length > 4000) err = err.slice(-4000); });
    ff.on("close", (code) => {
      if (code !== 0 && err) console.log("[remux]", mode, "hata:", err.split("\n").slice(-3).join(" | "));
      try { res.end(); } catch {}
    });
    req.on("close", () => { try { ff.kill("SIGKILL"); } catch {} });
    return;
  }

  if (u.pathname === "/proxy") {
    const target = u.searchParams.get("url");
    if (!authed(u)) { cors(res); res.writeHead(403); return res.end("yetki yok"); }
    if (!target) { res.writeHead(400); return res.end("url parametresi gerekli"); }
    if (activeUpstreams >= MAX_UPSTREAMS) { cors(res); res.writeHead(503); return res.end("meşgul"); }
    activeUpstreams++;
    let released = false;
    const release = () => { if (!released) { released = true; activeUpstreams--; } };
    res.on("close", release); res.on("finish", release);
    const fwd = { "user-agent": req.headers["user-agent"] || "cheesino-web/1.0" };
    if (req.headers.range) fwd.range = req.headers.range;
    fetchUrl(target, fwd, 5, (err, up, finalUrl) => {
      if (err) { cors(res); res.writeHead(502); res.end("Upstream hata: " + err.message); return release(); }
      cors(res);
      const ct = up.headers["content-type"] || "";
      const isM3u8 = /mpegurl/i.test(ct) || /\.m3u8(\?|$)/i.test(finalUrl);
      if (isM3u8) {
        let body = "";
        up.setEncoding("utf8");
        up.on("data", (d) => (body += d));
        up.on("end", () => {
          res.writeHead(up.statusCode || 200, { "content-type": "application/vnd.apple.mpegurl" });
          res.end(rewriteM3u8(body, finalUrl));
        });
        up.on("error", () => { try { res.end(); } catch {} });
      } else {
        const h = {};
        for (const k of ["content-type", "content-length", "accept-ranges", "content-range"])
          if (up.headers[k]) h[k] = up.headers[k];
        res.writeHead(up.statusCode || 200, h);
        up.pipe(res);
        req.on("close", () => up.destroy());
      }
    });
    return;
  }

  // Statik dosyalar (index.html vb.)
  let p = u.pathname === "/" ? "/index.html" : decodeURIComponent(u.pathname);
  const file = path.join(ROOT, path.normalize(p));
  if (!file.startsWith(ROOT)) { res.writeHead(403); return res.end(); }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); return res.end("bulunamadı"); }
    res.writeHead(200, { "content-type": MIME[path.extname(file)] || "application/octet-stream" });
    res.end(data);
  });
});

server.listen(PORT, HOST, () => {
  console.log(`cheesino web hazır → http://localhost:${PORT}`);
  console.log(TOKEN
    ? "Erişim anahtarı AKTİF → uygulamayı ?k=ANAHTAR ile açın (public dağıtım için)."
    : "HTTP yayınlar ve CORS bu sunucu üzerinden otomatik çözülür. Bu adresi internete açmayın.");
  console.log(HAS_FFMPEG
    ? "ffmpeg bulundu → MKV/AVI oynatma AKTİF (anlık remux/transcode)."
    : "ffmpeg bulunamadı → MKV/AVI oynatılamaz. Kurulum: https://ffmpeg.org (winget install ffmpeg / brew install ffmpeg / apt install ffmpeg)");
  log({ ev: "listen", port: PORT, host: HOST, ffmpeg: HAS_FFMPEG, blockPrivate: BLOCK_PRIVATE, tokenGate: !!TOKEN });
});
server.requestTimeout = 0;          // uzun canlı akışlar kesilmesin
server.headersTimeout = 30000;

// Zarif kapanış (autoscaler/deploy SIGTERM).
let shuttingDown = false;
function shutdown(sig) {
  if (shuttingDown) return; shuttingDown = true;
  log({ ev: "shutdown", sig });
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 8000).unref();
}
process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
process.on("uncaughtException", (e) => log({ ev: "uncaught", err: String(e && e.message || e) }));
