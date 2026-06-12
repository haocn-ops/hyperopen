import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "../..");
const SERVICE_WORKER_PATH = path.join(REPO_ROOT, "resources/public/sw.js");
const EXPECTED_ICON_CACHE_NAME = "hyperopen-icon-cache-v2";
const EXPECTED_ICON_META_CACHE_NAME = "hyperopen-icon-meta-v2";
const SP500_ICON_URL = "https://app.hyperliquid.xyz/coins/xyz:SP500.svg";

async function loadServiceWorkerHarness({ cacheKeys = [] } = {}) {
  const source = await fs.readFile(SERVICE_WORKER_PATH, "utf8");
  const listeners = new Map();
  const puts = [];
  const deleted = [];
  const cacheNames = new Set(cacheKeys);
  let clientsClaimed = false;

  class TestRequest {
    constructor(url, init = {}) {
      this.url = String(url);
      this.method = init.method ?? "GET";
    }
  }

  const caches = {
    async open(name) {
      cacheNames.add(name);
      return {
        async match() {
          return null;
        },
        async put(request, response) {
          puts.push({ cacheName: name, request, response });
        },
      };
    },
    async keys() {
      return [...cacheNames];
    },
    async delete(name) {
      deleted.push(name);
      return cacheNames.delete(name);
    },
  };

  const self = {
    addEventListener(type, handler) {
      listeners.set(type, handler);
    },
    clients: {
      async claim() {
        clientsClaimed = true;
      },
    },
    async skipWaiting() {},
  };

  const context = {
    Boolean,
    Date,
    Number,
    Promise,
    Request: TestRequest,
    Response,
    Set,
    String,
    URL,
    caches,
    encodeURIComponent,
    fetch: async () => {
      throw new Error("fetch was not stubbed for this service worker test");
    },
    self,
  };

  vm.createContext(context);
  vm.runInContext(
    `${source}\nself.__testExports = { cacheIconResponse, isIconRequest };`,
    context,
    { filename: SERVICE_WORKER_PATH }
  );

  return {
    cacheIconResponse: self.__testExports.cacheIconResponse,
    deleted,
    get clientsClaimed() {
      return clientsClaimed;
    },
    listeners,
    puts,
    Request: TestRequest,
  };
}

function opaqueResponse() {
  return {
    ok: false,
    type: "opaque",
    headers: {
      get() {
        return null;
      },
    },
    clone() {
      return opaqueResponse();
    },
  };
}

test("icon service worker ignores opaque icon responses", async () => {
  const harness = await loadServiceWorkerHarness();
  const request = new harness.Request(SP500_ICON_URL);

  await harness.cacheIconResponse(request, opaqueResponse());

  assert.deepEqual(harness.puts, []);
});

test("icon service worker ignores readable non-SVG responses", async () => {
  const harness = await loadServiceWorkerHarness();
  const request = new harness.Request(SP500_ICON_URL);
  const htmlResponse = new Response("<!doctype html><title>Missing</title>", {
    headers: { "content-type": "text/html; charset=utf-8" },
    status: 200,
  });

  await harness.cacheIconResponse(request, htmlResponse);

  assert.deepEqual(harness.puts, []);
});

test("icon service worker caches readable SVG responses with metadata", async () => {
  const harness = await loadServiceWorkerHarness();
  const request = new harness.Request(SP500_ICON_URL);
  const svgResponse = new Response("<svg xmlns=\"http://www.w3.org/2000/svg\" />", {
    headers: { "content-type": "image/svg+xml; charset=utf-8" },
    status: 200,
  });

  await harness.cacheIconResponse(request, svgResponse);

  assert.deepEqual(
    harness.puts.map((entry) => entry.cacheName),
    [EXPECTED_ICON_CACHE_NAME, EXPECTED_ICON_META_CACHE_NAME]
  );
});

test("icon service worker activation evicts the stale v1 icon caches", async () => {
  const harness = await loadServiceWorkerHarness({
    cacheKeys: [
      "hyperopen-icon-cache-v1",
      "hyperopen-icon-meta-v1",
      EXPECTED_ICON_CACHE_NAME,
      EXPECTED_ICON_META_CACHE_NAME,
      "hyperopen-app-cache-v1",
    ],
  });
  const activate = harness.listeners.get("activate");
  const waitUntilPromises = [];

  activate({
    waitUntil(promise) {
      waitUntilPromises.push(promise);
    },
  });
  await Promise.all(waitUntilPromises);

  assert.deepEqual(harness.deleted.sort(), [
    "hyperopen-icon-cache-v1",
    "hyperopen-icon-meta-v1",
  ]);
  assert.equal(harness.clientsClaimed, true);
});
