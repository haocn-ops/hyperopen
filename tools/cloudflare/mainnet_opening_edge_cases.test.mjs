import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { rewriteReleaseJavaScript } from "./rewrite_hyperunit_release_endpoints.mjs";

const workerModuleUrl = new URL("../../workers/hyperopen-worker.mjs", import.meta.url);

async function loadWorker() {
  return import(workerModuleUrl);
}

function openingEnv(overrides = {}) {
  return {
    HYPERUNIT_TESTNET_URL: "https://api.hyperunit-testnet.xyz",
    HYPERUNIT_MAINNET_URL: "https://api.hyperunit.xyz",
    HYPEROPEN_MAINNET_ENABLED: "true",
    ...overrides,
  };
}

test("Mainnet and Testnet HyperUnit proxies reject authority-relative and backslash authority escapes", async () => {
  const { handleRequest } = await loadWorker();
  const proxyCalls = [];
  const routes = [
    {
      host: "app.dexhelm.com",
      name: "Mainnet",
      prefix: "/api/hyperunit/mainnet",
    },
    {
      host: "testnet.dexhelm.com",
      name: "Testnet",
      prefix: "/api/hyperunit/testnet",
    },
  ];
  const attackSuffixes = [
    { name: "authority-relative double slash", value: "//evil.example/v2/estimate-fees" },
    { name: "backslash authority escape", value: "/\\evil.example/v2/estimate-fees" },
  ];

  for (const route of routes) {
    for (const attack of attackSuffixes) {
      const response = await handleRequest(
        new Request(`https://${route.host}${route.prefix}${attack.value}`),
        openingEnv(),
        {
          fetchImpl: async (request) => {
            proxyCalls.push(request.url);
            return new Response('{"unexpected":true}', { status: 200 });
          },
        }
      );

      assert.equal(response.status, 404, `${route.name}: ${attack.name}`);
    }
  }

  assert.deepEqual(proxyCalls, []);
});

test("Mainnet and Testnet reject syntactically valid but noncanonical HyperUnit upstreams", async () => {
  const { handleRequest, resolveHyperunitTarget } = await loadWorker();
  const wrongOriginEnv = openingEnv({
    HYPERUNIT_MAINNET_URL: "https://mainnet-proxy.example",
    HYPERUNIT_TESTNET_URL: "https://testnet-proxy.example",
  });

  assert.equal(resolveHyperunitTarget(
    new URL("https://app.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees"),
    wrongOriginEnv
  ), null);
  assert.equal(resolveHyperunitTarget(
    new URL("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/estimate-fees"),
    wrongOriginEnv
  ), null);

  const mainnetResponse = await handleRequest(
    new Request("https://app.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees"),
    wrongOriginEnv
  );
  const testnetResponse = await handleRequest(
    new Request("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/estimate-fees"),
    wrongOriginEnv
  );
  assert.equal(mainnetResponse.status, 503);
  assert.equal(testnetResponse.status, 404);
});

test("Mainnet opening canonicalizes the app document and selects the Mainnet asset root", async () => {
  const { handleRequest } = await loadWorker();
  const assetRequests = [];
  const redirect = await handleRequest(
    new Request("https://app.dexhelm.com/trade?coin=BTC&hyperliquidNetwork=testnet&hyperliquidNetwork=wrong", {
      headers: { accept: "text/html,application/xhtml+xml" },
    }),
    openingEnv()
  );

  assert.equal(redirect.status, 307);
  const location = new URL(redirect.headers.get("location"));
  assert.equal(location.pathname, "/trade");
  assert.equal(location.searchParams.get("coin"), "BTC");
  assert.deepEqual(location.searchParams.getAll("hyperliquidNetwork"), ["mainnet"]);

  const response = await handleRequest(
    new Request(location, { headers: { accept: "text/html" } }),
    openingEnv({
      ASSETS: {
        fetch: async (request) => {
          assetRequests.push(request.url);
          return new Response("mainnet shell", { status: 200 });
        },
      },
    })
  );

  assert.equal(response.status, 200);
  assert.equal(await response.text(), "mainnet shell");
  assert.deepEqual(assetRequests, [
    "https://app.dexhelm.com/__hyperopen_mainnet/trade?coin=BTC&hyperliquidNetwork=mainnet",
  ]);
});

test("Mainnet document delivery rewrites the tenant logo origin in the CSP", async () => {
  const { handleRequest } = await loadWorker();
  const response = await handleRequest(
    new Request("https://app.dexhelm.com/trade?hyperliquidNetwork=mainnet", {
      headers: { accept: "text/html" },
    }),
    openingEnv({
      ASSETS: {
        fetch: async () => new Response("mainnet shell", {
          status: 200,
          headers: {
            "content-type": "text/html; charset=utf-8",
            "content-security-policy": "img-src 'self' https://testnet.dexhelm.com; connect-src 'self'",
          },
        }),
      },
    })
  );

  assert.equal(response.status, 200);
  assert.equal(
    response.headers.get("content-security-policy"),
    "img-src 'self' https://app.dexhelm.com; connect-src 'self'"
  );
});

test("Mainnet opening proxies only the fixed Mainnet route and filters caller headers", async () => {
  const { handleRequest, resolveHyperunitTarget } = await loadWorker();
  const target = resolveHyperunitTarget(
    new URL("https://app.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees?asset=BTC"),
    openingEnv()
  );
  assert.equal(target?.href, "https://api.hyperunit.xyz/v2/estimate-fees?asset=BTC");

  const fetchCalls = [];
  const response = await handleRequest(
    new Request("https://app.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees?asset=BTC", {
      headers: {
        accept: "application/json",
        authorization: "must-not-forward",
        cookie: "must-not-forward",
      },
    }),
    openingEnv(),
    {
      fetchImpl: async (request) => {
        fetchCalls.push({ url: request.url, headers: Object.fromEntries(request.headers) });
        return new Response('{"fees":[]}', {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      },
    }
  );

  assert.equal(response.status, 200);
  await response.text();
  assert.deepEqual(fetchCalls, [{
    url: "https://api.hyperunit.xyz/v2/estimate-fees?asset=BTC",
    headers: { accept: "application/json" },
  }]);
});

test("Mainnet stays closed unless both the explicit opening flag and upstream are present", async () => {
  const { handleRequest, resolveHyperunitTarget } = await loadWorker();
  const upstreamCalls = [];
  const assetsCalls = [];
  const env = openingEnv({ HYPEROPEN_MAINNET_ENABLED: "false" });
  const response = await handleRequest(
    new Request("https://app.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees"),
    {
      ...env,
      ASSETS: { fetch: async (request) => { assetsCalls.push(request.url); return new Response("unexpected"); } },
    },
    { fetchImpl: async (request) => { upstreamCalls.push(request.url); return new Response("unexpected"); } }
  );

  assert.equal(response.status, 503);
  assert.equal(resolveHyperunitTarget(
    new URL("https://app.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees"),
    { ...env, HYPERUNIT_MAINNET_URL: "" }
  ), null);
  assert.deepEqual(assetsCalls, []);
  assert.deepEqual(upstreamCalls, []);

  const missingUpstreamAssetCalls = [];
  const missingUpstreamResponse = await handleRequest(
    new Request("https://app.dexhelm.com/trade?hyperliquidNetwork=mainnet", {
      headers: { accept: "text/html" },
    }),
    openingEnv({
      HYPERUNIT_MAINNET_URL: "",
      ASSETS: {
        fetch: async (request) => {
          missingUpstreamAssetCalls.push(request.url);
          return new Response("unexpected");
        },
      },
    })
  );
  assert.equal(missingUpstreamResponse.status, 503);
  assert.deepEqual(missingUpstreamAssetCalls, []);
});

test("Testnet remains isolated while Mainnet opening is enabled", async () => {
  const { handleRequest, resolveHyperunitTarget } = await loadWorker();
  assert.equal(resolveHyperunitTarget(
    new URL("https://testnet.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees"),
    openingEnv()
  ), null);
  const calls = [];
  const response = await handleRequest(
    new Request("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/estimate-fees"),
    openingEnv(),
    { fetchImpl: async (request) => { calls.push(request.url); return new Response('{"fees":[]}'); } }
  );
  assert.equal(response.status, 200);
  await response.text();
  assert.deepEqual(calls, ["https://api.hyperunit-testnet.xyz/v2/estimate-fees"]);
});

test("Mainnet release rewriting enables only the Mainnet same-origin proxy", async () => {
  const releaseDirectory = await fs.mkdtemp(path.join(os.tmpdir(), "dexhelm-mainnet-rewrite-"));
  const javascriptPath = path.join(releaseDirectory, "js", "main.js");
  await fs.mkdir(path.dirname(javascriptPath), { recursive: true });
  await fs.writeFile(javascriptPath, [
    'const mainnet = "https://api.hyperunit.xyz/v2/estimate-fees";',
    'const testnet = "https://api.hyperunit-testnet.xyz/v2/estimate-fees";',
  ].join("\n"));

  try {
    await rewriteReleaseJavaScript(releaseDirectory, { network: "mainnet" });
    assert.equal(await fs.readFile(javascriptPath, "utf8"), [
      'const mainnet = "/api/hyperunit/mainnet/v2/estimate-fees";',
      'const testnet = "/__hyperopen_disabled__/hyperunit-testnet/v2/estimate-fees";',
    ].join("\n"));
  } finally {
    await fs.rm(releaseDirectory, { recursive: true, force: true });
  }
});
