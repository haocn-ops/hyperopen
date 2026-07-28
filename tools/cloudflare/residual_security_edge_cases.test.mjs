import assert from "node:assert/strict";
import test from "node:test";

import { handleRequest } from "../../workers/hyperopen-worker.mjs";

const env = { HYPERUNIT_TESTNET_URL: "https://api.hyperunit-testnet.xyz" };
const proxyUrl = "https://testnet.dexhelm.com/api/hyperunit/testnet/v2/fees";

test("proxy denies unsupported methods and oversized bodies before upstream I/O", async () => {
  let fetchCount = 0;
  const options = { fetchImpl: async () => { fetchCount += 1; return new Response("unexpected"); } };
  for (const method of ["PUT", "PATCH", "DELETE", "OPTIONS"]) {
    const response = await handleRequest(new Request(proxyUrl, { method }), env, options);
    assert.equal(response.status, 405, method);
    assert.equal(response.headers.get("allow"), "GET, HEAD, POST", method);
  }
  const oversized = await handleRequest(new Request(proxyUrl, {
    method: "POST",
    headers: { "content-length": String(1024 * 1024 + 1) },
    body: "x",
  }), env, options);
  assert.equal(oversized.status, 413);
  const chunk = new Uint8Array(600 * 1024);
  const streamed = await handleRequest(new Request(proxyUrl, {
    method: "POST",
    duplex: "half",
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(chunk);
        controller.enqueue(chunk);
        controller.close();
      },
    }),
  }), env, options);
  assert.equal(streamed.status, 413);
  assert.equal(fetchCount, 0);
});

test("proxy timeout aborts and returns only generic failure output", async () => {
  let abortCallback;
  let cleared = false;
  const response = await handleRequest(new Request(proxyUrl), env, {
    setTimeoutImpl: (callback) => { abortCallback = callback; return 9; },
    clearTimeoutImpl: (id) => { assert.equal(id, 9); cleared = true; },
    fetchImpl: (request) => new Promise((_resolve, reject) => {
      request.signal.addEventListener("abort", () => reject(new Error("private upstream detail")));
      abortCallback();
    }),
  });
  assert.equal(response.status, 502);
  assert.equal(await response.text(), '{"error":"HyperUnit proxy request failed."}');
  assert.equal(cleared, true);
});

test("proxy deadline aborts a stalled client upload before upstream I/O", async () => {
  let abortCallback;
  let uploadCancelled = false;
  let fetchCount = 0;
  const request = new Request(proxyUrl, {
    method: "POST",
    duplex: "half",
    body: new ReadableStream({
      cancel() {
        uploadCancelled = true;
      },
    }),
  });

  const responsePromise = handleRequest(request, env, {
    setTimeoutImpl: (callback, milliseconds) => {
      assert.equal(milliseconds, 15_000);
      abortCallback = callback;
      return 10;
    },
    clearTimeoutImpl: (id) => assert.equal(id, 10),
    fetchImpl: async () => {
      fetchCount += 1;
      return new Response("unexpected");
    },
  });

  await Promise.resolve();
  assert.equal(typeof abortCallback, "function");
  abortCallback();
  const response = await responsePromise;
  assert.equal(response.status, 502);
  assert.equal(fetchCount, 0);
  assert.equal(uploadCancelled, true);
});

test("proxy deadline remains active until the upstream response body finishes", async () => {
  let abortCallback;
  let responseCancelled = false;
  const cleared = [];
  const response = await handleRequest(new Request(proxyUrl), env, {
    setTimeoutImpl: (callback) => {
      abortCallback = callback;
      return 11;
    },
    clearTimeoutImpl: (id) => cleared.push(id),
    fetchImpl: async () => new Response(new ReadableStream({
      cancel() {
        responseCancelled = true;
      },
    })),
  });

  assert.equal(cleared.length, 0);
  abortCallback();
  await assert.rejects(() => response.text());
  assert.equal(responseCancelled, true);
  assert.deepEqual(cleared, [11]);
});

test("alternate hosts and mainnet or generic paths never reach upstream or assets", async () => {
  let calls = 0;
  const closedEnv = { ...env, ASSETS: { fetch: async () => { calls += 1; return new Response("unexpected"); } } };
  for (const url of [
    "https://dexhelm.com/api/hyperunit/testnet/v2/fees",
    "https://status.dexhelm.com/api/hyperunit/testnet/v2/fees",
    "https://unknown.example/api/hyperunit/testnet/v2/fees",
    "https://testnet.dexhelm.com/api/hyperunit/mainnet/v2/fees",
    "https://testnet.dexhelm.com/api/hyperunit/v2/fees",
  ]) {
    const response = await handleRequest(new Request(url), closedEnv, { fetchImpl: async () => { calls += 1; return new Response("unexpected"); } });
    assert.ok([404, 503].includes(response.status), url);
  }
  assert.equal(calls, 0);
});
