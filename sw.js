/* SIByL offline (see index.html): a page opened, and the manifest (the installed app's name and icons), come from the
   network, their last copy (by path) when there's none; the rest of the site (the library, Inter, the icons) from the
   cache, kept as first fetched, so a file changed takes a new name. Dropbox's calls aren't the site's: they pass. */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(clients.claim()));
self.addEventListener('fetch', e => {
  const r = e.request, url = new URL(r.url);
  if (r.method !== 'GET' || url.origin !== location.origin) return;
  e.respondWith(caches.open('sybil').then(async cache => {
    const keep = (key, res) => { if (res.ok) cache.put(key, res.clone()); return res; };
    if (r.mode === 'navigate' || r.destination === 'manifest') return fetch(r).then(res => keep(url.pathname, res), () => cache.match(url.pathname));
    return await cache.match(r) || fetch(r).then(res => keep(r, res));
  }));
});
