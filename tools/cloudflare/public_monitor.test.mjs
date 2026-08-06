import assert from "node:assert/strict";
import fs from "node:fs/promises";
import test from "node:test";

import { monitorPublicSurfaces, expectedMainnetStatus } from "./public_monitor.mjs";

function response(status, headers = {}) {
  return { status, headers: new Headers(headers) };
}

test("public monitor checks four hosts and Testnet health without reading bodies", async () => {
  const calls = [];
  const result = await monitorPublicSurfaces({
    fetchImpl: async (url, options) => {
      calls.push({ url, options });
      return response(url.endsWith("api/health") ? 200 : url.includes("app.dexhelm") ? 503 : 200, {
        "content-type": url.endsWith("api/health") ? "application/json" : "text/html",
        "cache-control": url.endsWith("api/health") || url.includes("app.dexhelm") ? "no-store" : "public, max-age=300",
      });
    },
  });
  assert.equal(result.ok, true);
  assert.equal(calls.length, 5);
  assert.ok(calls.every(({ options }) => options.method === "GET"));
  assert.ok(calls.every(({ options }) => options.headers.accept.includes("application/json")));
  assert.deepEqual(Object.keys(result.results), ["apex", "testnet", "mainnet", "status", "testnetHealth"]);
});

test("public monitor can switch the expected Mainnet status for an authorized opening", async () => {
  const result = await monitorPublicSurfaces({
    expectedMainnetStatus: 200,
    fetchImpl: async () => response(200),
  });
  assert.equal(result.results.mainnet.expectedStatus, 200);
  assert.equal(result.ok, true);
});

test("invalid Mainnet status configuration is rejected", () => {
  assert.throws(() => expectedMainnetStatus({ DEXHELM_EXPECT_MAINNET_STATUS: "not-a-status" }), /must be an integer/);
});

test("public monitor workflow uses immutable actions and a governed Mainnet state variable", async () => {
  const workflow = await fs.readFile(".github/workflows/dexhelm-public-monitor.yml", "utf8");
  assert.match(workflow, /actions\/checkout@[0-9a-f]{40}/);
  assert.match(workflow, /actions\/setup-node@[0-9a-f]{40}/);
  assert.match(workflow, /DEXHELM_EXPECT_MAINNET_STATUS:\s*\$\{\{\s*vars\.DEXHELM_EXPECT_MAINNET_STATUS\s*\|\|\s*'503'\s*\}\}/);
});
