"use strict";

const ICON_HOST = "app.hyperliquid.xyz";
const ICON_PATH_PREFIX = "/coins/";
const ICON_FILE_SUFFIX = ".svg";
const ICON_CACHE_NAME = "hyperopen-icon-cache-v2";
const ICON_META_CACHE_NAME = "hyperopen-icon-meta-v2";
const ICON_CACHE_PREFIX = "hyperopen-icon-";
const ICON_TTL_MS = 10 * 24 * 60 * 60 * 1000;

function isIconRequest(request) {
  if (!request || request.method !== "GET") {
    return false;
  }

  const url = new URL(request.url);
  return (
    url.hostname === ICON_HOST &&
    url.pathname.startsWith(ICON_PATH_PREFIX) &&
    url.pathname.endsWith(ICON_FILE_SUFFIX)
  );
}

function iconMetaRequest(url) {
  return new Request(`/__hyperopen_icon_meta__?u=${encodeURIComponent(url)}`);
}

function responseContentType(response) {
  if (!response || !response.headers || typeof response.headers.get !== "function") {
    return "";
  }

  return String(response.headers.get("content-type") || "").toLowerCase();
}

function isCacheableIconResponse(response) {
  return Boolean(
    response &&
      response.ok &&
      response.type !== "opaque" &&
      responseContentType(response).includes("image/svg+xml")
  );
}

async function readCachedAt(url) {
  const metaCache = await caches.open(ICON_META_CACHE_NAME);
  const response = await metaCache.match(iconMetaRequest(url));
  if (!response) {
    return null;
  }

  const cachedAt = Number(await response.text());
  return Number.isFinite(cachedAt) ? cachedAt : null;
}

async function writeCachedAt(url, timestampMs) {
  const metaCache = await caches.open(ICON_META_CACHE_NAME);
  await metaCache.put(
    iconMetaRequest(url),
    new Response(String(timestampMs), {
      headers: { "content-type": "text/plain" },
    })
  );
}

async function cacheIconResponse(request, response) {
  if (!isCacheableIconResponse(response)) {
    return;
  }

  const iconCache = await caches.open(ICON_CACHE_NAME);
  await iconCache.put(request, response.clone());
  await writeCachedAt(request.url, Date.now());
}

async function refreshIcon(request) {
  try {
    const networkResponse = await fetch(request, { cache: "no-store" });
    await cacheIconResponse(request, networkResponse);
  } catch (_err) {
    // Keep serving the prior cached icon when refresh fails.
  }
}

async function handleIconRequest(event) {
  const request = event.request;
  const iconCache = await caches.open(ICON_CACHE_NAME);
  const cachedResponse = await iconCache.match(request);

  if (cachedResponse) {
    event.waitUntil(
      (async () => {
        const cachedAt = await readCachedAt(request.url);
        const stale =
          !Number.isFinite(cachedAt) || Date.now() - cachedAt >= ICON_TTL_MS;
        if (stale) {
          await refreshIcon(request);
        }
      })()
    );
    return cachedResponse;
  }

  const networkResponse = await fetch(request);
  await cacheIconResponse(request, networkResponse);
  return networkResponse;
}

self.addEventListener("install", (event) => {
  event.waitUntil(
    Promise.all([
      caches.open(ICON_CACHE_NAME),
      caches.open(ICON_META_CACHE_NAME),
    ]).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keepCaches = new Set([ICON_CACHE_NAME, ICON_META_CACHE_NAME]);
      const keys = await caches.keys();
      await Promise.all(
        keys
          .filter(
            (name) => name.startsWith(ICON_CACHE_PREFIX) && !keepCaches.has(name)
          )
          .map((name) => caches.delete(name))
      );
      await self.clients.claim();
    })()
  );
});

self.addEventListener("fetch", (event) => {
  if (!isIconRequest(event.request)) {
    return;
  }

  event.respondWith(handleIconRequest(event));
});
