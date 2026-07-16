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
const { URL } = require("url");

const PORT = process.env.PORT || 8088;
const ROOT = __dirname;
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
}

/** m3u8 içindeki segment/alt-manifest/anahtar adreslerini proxy'ye çevir. */
function rewriteM3u8(text, baseUrl) {
  const prox = (v) => {
    try { return "/proxy?url=" + encodeURIComponent(new URL(v, baseUrl).href); }
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

  // Uygulamanın "proxy var mı?" otomatik algısı için.
  if (u.pathname === "/proxy-ping") { cors(res); res.writeHead(204); return res.end(); }

  if (u.pathname === "/proxy") {
    const target = u.searchParams.get("url");
    if (!target) { res.writeHead(400); return res.end("url parametresi gerekli"); }
    const fwd = { "user-agent": req.headers["user-agent"] || "cheesino-web/1.0" };
    if (req.headers.range) fwd.range = req.headers.range;
    fetchUrl(target, fwd, 5, (err, up, finalUrl) => {
      if (err) { cors(res); res.writeHead(502); return res.end("Upstream hata: " + err.message); }
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

server.listen(PORT, () => {
  console.log(`cheesino web hazır → http://localhost:${PORT}`);
  console.log("HTTP yayınlar ve CORS bu sunucu üzerinden otomatik çözülür. Bu adresi internete açmayın.");
});
