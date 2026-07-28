import assert from "node:assert/strict";
import test from "node:test";

import {
  MAX_REQUEST_BODY_BYTES,
  createProxyServer,
  filterRequestHeaders,
  normalizeProxyConfig,
  routeForRequest,
} from "./server.mjs";

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => resolve(server.address()));
  });
}

test("proxy configuration accepts only loopback app origins and HTTPS upstream origins", () => {
  assert.throws(
    () => normalizeProxyConfig({ appOrigin: "http://0.0.0.0:8080" }),
    /loopback/i,
  );
  assert.throws(
    () => normalizeProxyConfig({ mainnetBase: "https://user:pass@api.hyperunit.xyz/path" }),
    /origin/i,
  );
});

test("proxy route matching requires a complete prefix segment", () => {
  assert.equal(routeForRequest("/api/hyperunit-mainnet-evil/v1").kind, "app");
  assert.equal(routeForRequest("/api/hyperunit/mainnet/v1")?.upstreamPath, "/v1");
});

test("proxy strips browser credentials and publishes a finite body limit", () => {
  const headers = filterRequestHeaders({
    authorization: "Bearer secret",
    cookie: "session=secret",
    "content-type": "application/json",
    accept: "application/json",
    "x-arbitrary": "not-forwarded",
  });
  assert.deepEqual(headers, {
    accept: "application/json",
    "content-type": "application/json",
  });
  assert.ok(Number.isSafeInteger(MAX_REQUEST_BODY_BYTES));
  assert.ok(MAX_REQUEST_BODY_BYTES > 0 && MAX_REQUEST_BODY_BYTES <= 1024 * 1024);
});

test("proxy binds on loopback, strips credentials in both directions, and rejects oversized bodies", async () => {
  const upstreamCalls = [];
  const server = createProxyServer({
    fetchFn: async (url, init) => {
      upstreamCalls.push({ url: String(url), headers: init.headers });
      return new Response('{"ok":true}', {
        status: 200,
        headers: {
          "content-type": "application/json",
          "set-cookie": "upstream=secret",
        },
      });
    },
  });
  try {
    const address = await listen(server);
    assert.equal(address.address, "127.0.0.1");
    const origin = `http://127.0.0.1:${address.port}`;
    const response = await fetch(`${origin}/api/hyperunit/mainnet/v1/quote`, {
      headers: {
        authorization: "Bearer secret",
        cookie: "session=secret",
        accept: "application/json",
      },
    });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("set-cookie"), null);
    assert.deepEqual(upstreamCalls[0].headers, { accept: "application/json" });

    const unsupported = await fetch(`${origin}/api/hyperunit/mainnet/v1/order`, {
      method: "DELETE",
    });
    assert.equal(unsupported.status, 405);
    assert.equal(upstreamCalls.length, 1);

    const oversized = await fetch(`${origin}/api/hyperunit/mainnet/v1/order`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "x".repeat(MAX_REQUEST_BODY_BYTES + 1),
    });
    assert.equal(oversized.status, 413);
    assert.equal(upstreamCalls.length, 1);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
