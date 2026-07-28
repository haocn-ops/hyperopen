import assert from "node:assert/strict";
import test from "node:test";

const workerModuleUrl = new URL("../../workers/hyperopen-worker.mjs", import.meta.url);

async function loadWorker() {
  return import(workerModuleUrl);
}

function workerEnv(overrides = {}) {
  return {
    HYPERUNIT_TESTNET_URL: "https://api.hyperunit-testnet.xyz",
    ...overrides,
  };
}

test("resolveHyperunitTarget maps only the canonical Testnet host and route", async () => {
  const { resolveHyperunitTarget } = await loadWorker();
  const env = workerEnv();

  assert.equal(
    resolveHyperunitTarget(
      new URL("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/estimate-fees?asset=HYPE"),
      env
    ).href,
    "https://api.hyperunit-testnet.xyz/v2/estimate-fees?asset=HYPE"
  );
  assert.equal(
    resolveHyperunitTarget(
      new URL("https://hyperopen.example/api/hyperunit/testnet/v2/estimate-fees?asset=HYPE"),
      env
    ),
    null
  );
  assert.equal(
    resolveHyperunitTarget(
      new URL("https://testnet.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees?asset=HYPE"),
      env
    ),
    null
  );
});

test("buildHyperunitRequest keeps a JSON POST body and an explicit safe request-header allowlist", async () => {
  const { buildHyperunitRequest } = await loadWorker();
  const payload = JSON.stringify({ asset: "HYPE", account: "0xpublic" });
  const request = new Request("https://hyperopen.example/api/hyperunit/mainnet/v2/estimate-fees", {
    method: "POST",
    headers: {
      accept: "application/json",
      "accept-language": "en-US",
      authorization: "Bearer should-not-leave-the-worker",
      connection: "keep-alive",
      "content-length": "999",
      "content-type": "application/json",
      cookie: "session=should-not-leave-the-worker",
      host: "hyperopen.example",
      "if-modified-since": "Tue, 01 Jan 2030 00:00:00 GMT",
      "if-none-match": '"known-etag"',
      "x-arbitrary-sentinel": "must-not-be-forwarded",
    },
    body: payload,
  });

  const proxied = buildHyperunitRequest(
    request,
    new URL("https://api.hyperunit.xyz/v2/estimate-fees")
  );

  assert.equal(proxied.url, "https://api.hyperunit.xyz/v2/estimate-fees");
  assert.equal(proxied.method, "POST");
  assert.equal(proxied.redirect, "manual");
  assert.equal(await proxied.text(), payload);
  assert.deepEqual(Object.fromEntries(proxied.headers), {
    accept: "application/json",
    "accept-language": "en-US",
    "content-type": "application/json",
    "if-modified-since": "Tue, 01 Jan 2030 00:00:00 GMT",
    "if-none-match": '"known-etag"',
  });
});

test("handleRequest forwards a JSON request exactly once and returns only safe upstream response headers", async () => {
  const { handleRequest } = await loadWorker();
  const payload = JSON.stringify({ asset: "HYPE" });
  const fetchCalls = [];
  const response = await handleRequest(
    new Request("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/estimate-fees?asset=HYPE", {
      method: "POST",
      headers: {
        accept: "application/json",
        authorization: "Bearer should-not-leave-the-worker",
        "content-type": "application/json",
        cookie: "session=should-not-leave-the-worker",
        "x-arbitrary-sentinel": "must-not-be-forwarded",
      },
      body: payload,
    }),
    workerEnv(),
    {
      fetchImpl: async (request) => {
        fetchCalls.push({
          body: await request.text(),
          headers: Object.fromEntries(request.headers),
          method: request.method,
          url: request.url,
        });
        return new Response('{"fees":[]}', {
          status: 207,
          headers: {
            "cache-control": "public, max-age=30",
            "content-language": "en",
            "content-type": "application/json; charset=utf-8",
            etag: '"fees-v1"',
            connection: "keep-alive",
            server: "upstream-private-detail",
            "set-cookie": "upstream-session=private",
            "www-authenticate": "Bearer private",
            "x-upstream-secret": "private",
          },
        });
      },
    }
  );

  assert.deepEqual(fetchCalls, [
    {
      body: payload,
      headers: {
        accept: "application/json",
        "content-type": "application/json",
      },
      method: "POST",
      url: "https://api.hyperunit-testnet.xyz/v2/estimate-fees?asset=HYPE",
    },
  ]);
  assert.equal(response.status, 207);
  assert.equal(await response.text(), '{"fees":[]}');
  assert.equal(response.headers.get("cache-control"), "public, max-age=30");
  assert.equal(response.headers.get("content-language"), "en");
  assert.equal(response.headers.get("content-type"), "application/json; charset=utf-8");
  assert.equal(response.headers.get("etag"), '"fees-v1"');
  assert.equal(response.headers.get("connection"), null);
  assert.equal(response.headers.get("server"), null);
  assert.equal(response.headers.get("set-cookie"), null);
  assert.equal(response.headers.get("www-authenticate"), null);
  assert.equal(response.headers.get("x-upstream-secret"), null);
});

test("handleRequest delegates non-proxy requests directly to Workers Static Assets", async () => {
  const { handleRequest } = await loadWorker();
  const receivedRequests = [];
  const staticResponse = await handleRequest(
    new Request("https://testnet.dexhelm.com/trade"),
    workerEnv({
      ASSETS: {
        fetch: async (request) => {
          receivedRequests.push(request);
          return new Response("trade asset", {
            status: 200,
            headers: { "content-type": "text/html; charset=utf-8" },
          });
        },
      },
    }),
    {
      fetchImpl: async () => {
        throw new Error("proxy fetch must not handle static requests");
      },
    }
  );

  assert.equal(receivedRequests.length, 1);
  assert.equal(receivedRequests[0].url, "https://testnet.dexhelm.com/trade");
  assert.equal(staticResponse.status, 200);
  assert.equal(await staticResponse.text(), "trade asset");
});

test("DEXHelm apex serves product, documentation, risk, source, and license information", async () => {
  const { handleRequest } = await loadWorker();
  const response = await handleRequest(
    new Request("https://dexhelm.com/"),
    workerEnv()
  );
  const body = await response.text();

  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /^text\/html/i);
  assert.match(response.headers.get("content-security-policy"), /frame-ancestors 'none'/);
  assert.equal(response.headers.get("strict-transport-security"), "max-age=31536000; includeSubDomains");
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
  assert.match(body, /<h1>DEXHelm<\/h1>/);
  assert.match(body, /id="documentation"/);
  assert.match(body, /id="risk"/);
  assert.doesNotMatch(body, /app\.dexhelm\.com\/trade/);
  assert.match(body, /testnet\.dexhelm\.com\/trade/);
  assert.match(body, /Mainnet terminal is temporarily unavailable/);
  assert.match(body, /github\.com\/thegeronimo\/hyperopen/);
  assert.match(body, /GNU AGPL v3/);
});

test("DEXHelm status host serves a no-store status document and the shared health JSON", async () => {
  const { handleRequest } = await loadWorker();
  const statusResponse = await handleRequest(
    new Request("https://status.dexhelm.com/"),
    workerEnv()
  );
  const healthResponse = await handleRequest(
    new Request("https://status.dexhelm.com/api/health", {
      headers: { accept: "text/html,application/json" },
    }),
    workerEnv()
  );

  assert.equal(statusResponse.status, 200);
  assert.equal(statusResponse.headers.get("cache-control"), "no-store");
  assert.equal(statusResponse.headers.get("strict-transport-security"), "max-age=31536000; includeSubDomains");
  const statusBody = await statusResponse.text();
  assert.match(statusBody, /DEXHelm is reachable/);
  assert.match(statusBody, /Mainnet terminal delivery<\/span><span>SUSPENDED/);
  assert.equal(healthResponse.status, 200);
  assert.equal(healthResponse.headers.get("content-type"), "application/json");
  assert.equal(await healthResponse.text(), '{"status":"ok"}');
});

test("DEXHelm Mainnet host is temporarily closed before assets or the HyperUnit proxy", async () => {
  const { handleRequest } = await loadWorker();
  const assetRequests = [];
  const upstreamRequests = [];
  const options = {
    fetchImpl: async (request) => {
      upstreamRequests.push(request.url);
      return new Response("unexpected upstream response");
    },
  };
  const env = workerEnv({
    ASSETS: {
      fetch: async (request) => {
        assetRequests.push(request.url);
        return new Response("unexpected asset response");
      },
    },
  });

  for (const request of [
    new Request("https://app.dexhelm.com/trade", { headers: { accept: "text/html" } }),
    new Request("https://app.dexhelm.com/js/main.HASH.js"),
    new Request("https://app.dexhelm.com/api/hyperunit/mainnet/v2/estimate-fees"),
  ]) {
    const response = await handleRequest(request, env, options);

    assert.equal(response.status, 503, request.url);
    assert.equal(response.headers.get("cache-control"), "no-store", request.url);
    assert.equal(response.headers.get("strict-transport-security"), "max-age=31536000; includeSubDomains", request.url);
    assert.match(await response.text(), /Mainnet terminal is temporarily unavailable/, request.url);
  }

  assert.deepEqual(assetRequests, []);
  assert.deepEqual(upstreamRequests, []);
});

test("DEXHelm Testnet host canonicalizes document navigations and preserves other query values", async () => {
  const { handleRequest } = await loadWorker();
  const response = await handleRequest(
    new Request(
      "https://testnet.dexhelm.com/portfolio?tab=positions&hyperliquidNetwork=wrong&hyperliquidNetwork=duplicate",
      { headers: { accept: "text/html,application/xhtml+xml" } }
    ),
    workerEnv()
  );
  const location = new URL(response.headers.get("location"));

  assert.equal(response.status, 307);
  assert.equal(location.hostname, "testnet.dexhelm.com");
  assert.equal(location.pathname, "/portfolio");
  assert.equal(location.searchParams.get("tab"), "positions");
  assert.deepEqual(location.searchParams.getAll("hyperliquidNetwork"), ["testnet"]);
});

test("DEXHelm terminal roots open trade and canonical document URLs do not redirect again", async () => {
  const { handleRequest } = await loadWorker();
  const rootResponse = await handleRequest(
    new Request("https://testnet.dexhelm.com/?from=home", {
      headers: { accept: "text/html" },
    }),
    workerEnv()
  );
  const assetRequests = [];
  const canonicalResponse = await handleRequest(
    new Request(
      "https://testnet.dexhelm.com/trade?coin=HYPE&hyperliquidNetwork=testnet",
      { headers: { accept: "text/html" } }
    ),
    workerEnv({
      ASSETS: {
        fetch: async (request) => {
          assetRequests.push(request.url);
          return new Response("testnet trade shell", { status: 200 });
        },
      },
    })
  );
  const rootLocation = new URL(rootResponse.headers.get("location"));

  assert.equal(rootResponse.status, 307);
  assert.equal(rootLocation.pathname, "/trade");
  assert.equal(rootLocation.searchParams.get("from"), "home");
  assert.equal(rootLocation.searchParams.get("hyperliquidNetwork"), "testnet");
  assert.equal(canonicalResponse.status, 200);
  assert.equal(await canonicalResponse.text(), "testnet trade shell");
  assert.deepEqual(assetRequests, [
    "https://testnet.dexhelm.com/trade?coin=HYPE&hyperliquidNetwork=testnet",
  ]);
});

test("DEXHelm host policy serves Testnet assets and rejects the public Workers hostname", async () => {
  const { handleRequest } = await loadWorker();
  const assetRequests = [];
  const assets = {
    fetch: async (request) => {
      assetRequests.push(`${request.method} ${request.url}`);
      return new Response("asset response", { status: 200 });
    },
  };

  const assetRequest = new Request("https://testnet.dexhelm.com/js/main.HASH.js", {
    headers: { accept: "*/*" },
  });
  const assetResponse = await handleRequest(assetRequest, workerEnv({ ASSETS: assets }));
  assert.equal(assetResponse.status, 200);
  assert.equal(await assetResponse.text(), "asset response");

  const workersResponse = await handleRequest(
    new Request("https://hyperopen.izhenghaocn.workers.dev/trade", {
      headers: { accept: "text/html" },
    }),
    workerEnv({ ASSETS: assets })
  );
  assert.equal(workersResponse.status, 404);

  assert.deepEqual(assetRequests, [
    "GET https://testnet.dexhelm.com/js/main.HASH.js",
  ]);
});

test("handleRequest serves the portfolio shell for canonical optimizer deep links", async () => {
  const { handleRequest } = await loadWorker();
  const receivedUrls = [];
  const assets = {
    fetch: async (request) => {
      receivedUrls.push(request.url);
      const pathname = new URL(request.url).pathname;
      if (pathname === "/portfolio") {
        return new Response("portfolio shell", {
          status: 200,
          headers: { "content-type": "text/html; charset=utf-8" },
        });
      }
      return new Response("not found", { status: 404 });
    },
  };

  for (const pathname of [
    "/portfolio/optimize",
    "/portfolio/optimize/new",
    "/portfolio/optimize/scn_01",
    "/portfolio/optimize/scn_01/details",
  ]) {
    receivedUrls.length = 0;
    const response = await handleRequest(
      new Request(`https://testnet.dexhelm.com${pathname}?hyperliquidNetwork=testnet`),
      workerEnv({ ASSETS: assets })
    );

    assert.equal(response.status, 200, pathname);
    assert.equal(await response.text(), "portfolio shell", pathname);
    assert.deepEqual(receivedUrls, [
      `https://testnet.dexhelm.com${pathname}?hyperliquidNetwork=testnet`,
      "https://testnet.dexhelm.com/portfolio",
    ]);
  }
});
