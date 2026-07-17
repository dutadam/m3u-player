// cheesino web — minimal service worker (kurulabilirlik + uygulama kabuğu önbelleği).
// Yalnız uygulama kabuğunu (statik dosyalar) önbelleğe alır; API/akış istekleri asla önbelleklenmez.
const CACHE = "cheesino-shell-v1";
const SHELL = ["./", "./index.html", "./manifest.webmanifest", "./icon-192.png", "./icon-512.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).catch(() => {}));
  self.skipWaiting();
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))));
  self.clients.claim();
});
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  // Yalnız kendi origin'imizdeki statik kabuğu ele al; proxy/akış/API dokunma.
  if (e.request.method !== "GET" || url.origin !== self.location.origin) return;
  if (["/proxy", "/remux", "/probe", "/api"].some((p) => url.pathname.startsWith(p))) return;
  e.respondWith(
    caches.match(e.request).then((hit) => hit || fetch(e.request).then((res) => {
      if (res.ok && (url.pathname === "/" || /\.(html|js|css|png|svg|webmanifest)$/.test(url.pathname))) {
        const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy));
      }
      return res;
    }).catch(() => caches.match("./index.html")))
  );
});
