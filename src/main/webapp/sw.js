/*
 * Multiplayer Snake Game — Service Worker (offline app shell)
 *
 * NOTE: Gameplay itself (WebSocket movement, game state broadcasts, REST
 * actions) requires a live connection. This service worker only provides
 * the offline app shell: it precaches the HTML/JS/CSS entry points so an
 * installed user can open the lobby while offline. API calls (/api/*) and
 * WebSocket connections are never intercepted and always hit the network.
 */

'use strict';

const PRECACHE = 'snake-v1';
const RUNTIME_CACHE = 'snake-v1-runtime';

// App shell assets, cached at install time (cache-first once installed).
const PRECACHE_URLS = [
  '/',
  '/game.jsp',
  '/profile.jsp',
  '/css/style.css',
  '/js/ajax.js',
  '/js/game.js',
  '/js/profile.js',
  '/js/i18n.js',
  '/manifest.webmanifest',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/icon.svg',
  '/sounds/countdown.ogg',
  '/sounds/gameover.wav'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(PRECACHE)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  const currentCaches = [PRECACHE, RUNTIME_CACHE];
  event.waitUntil(
    caches.keys()
      .then((cacheNames) =>
        Promise.all(
          cacheNames
            .filter((name) => name.startsWith('snake-v') && !currentCaches.includes(name))
            .map((name) => caches.delete(name))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;

  // Only handle same-origin GET requests.
  if (request.method !== 'GET') {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }

  // Never intercept API calls (REST + WebSocket handshakes live under
  // /api/). They must always reach the live server — e.g. /api/health
  // and all game actions. Just let the network handle them.
  if (url.pathname.startsWith('/api/')) {
    return;
  }

  // HTML page navigations: network-first, fall back to the cached
  // app shell so the lobby still opens offline.
  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request));
    return;
  }

  // Precache-list assets: cache-first (fast, works offline).
  if (PRECACHE_URLS.includes(url.pathname)) {
    event.respondWith(cacheFirst(request));
    return;
  }

  // Everything else: network with cache fallback.
  event.respondWith(networkFirst(request));
});

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) {
    return cached;
  }
  const cache = await caches.open(PRECACHE);
  try {
    const response = await fetch(request);
    if (response && response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    return new Response('Offline', { status: 503, statusText: 'Offline' });
  }
}

async function networkFirst(request) {
  const cache = await caches.open(RUNTIME_CACHE);
  try {
    const response = await fetch(request);
    if (response && response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    const cached = await caches.match(request);
    if (cached) {
      return cached;
    }
    // For offline navigations, serve the precached app shell (lobby).
    if (request.mode === 'navigate') {
      const shell = await caches.match('/');
      if (shell) {
        return shell;
      }
    }
    return new Response('Offline', { status: 503, statusText: 'Offline' });
  }
}
