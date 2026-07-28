import assert from "node:assert/strict";
import test from "node:test";

import { handleRequest } from "../../workers/hyperopen-worker.mjs";

const env = { HYPERUNIT_TESTNET_URL: "https://api.hyperunit-testnet.xyz" };

test("testnet proxy allows bounded methods and clears its deadline", async () => {
  const deadlines = [];
  const cleared = [];
  const upstream = [];
  const options = {
    setTimeoutImpl: (callback, milliseconds) => {
      deadlines.push({ callback, milliseconds });
      return 17;
    },
    clearTimeoutImpl: (id) => cleared.push(id),
    fetchImpl: async (request) => {
      upstream.push({ method: request.method, url: request.url, body: request.method === "POST" ? await request.text() : null });
      return new Response(request.method === "HEAD" ? null : "ok", { status: 201, headers: { "content-type": "text/plain", "set-cookie": "secret=1" } });
    },
  };
  const get = await handleRequest(new Request("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/fees"), env, options);
  const head = await handleRequest(new Request("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/fees", { method: "HEAD" }), env, options);
  const post = await handleRequest(new Request("https://testnet.dexhelm.com/api/hyperunit/testnet/v2/fees", { method: "POST", body: "{\"asset\":\"HYPE\"}" }), env, options);
  assert.deepEqual(upstream.map(({ method }) => method), ["GET", "HEAD", "POST"]);
  assert.equal(upstream[2].body, "{\"asset\":\"HYPE\"}");
  assert.equal(deadlines.every(({ milliseconds }) => milliseconds === 15_000), true);
  assert.equal(await head.text(), "");
  assert.equal(await get.text(), "ok");
  assert.equal(await post.text(), "ok");
  assert.deepEqual(cleared, [17, 17, 17]);
  assert.equal(get.headers.get("set-cookie"), null);
  assert.equal(post.status, 201);
});
