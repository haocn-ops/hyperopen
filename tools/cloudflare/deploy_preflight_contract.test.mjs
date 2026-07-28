import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import test from "node:test";

import { checkWranglerConfiguration, inspectArtifactJavaScript } from "../../.agents/skills/deploy-hyperopen-cloudflare/scripts/preflight.mjs";

const safeConfig = {
  name: "hyperopen",
  main: "./workers/hyperopen-worker.mjs",
  compatibility_date: "2026-07-21",
  workers_dev: false,
  routes: ["dexhelm.com", "app.dexhelm.com", "testnet.dexhelm.com", "status.dexhelm.com"]
    .map((pattern) => ({ pattern, custom_domain: true })),
  vars: { HYPERUNIT_TESTNET_URL: "https://api.hyperunit-testnet.xyz" },
  assets: { directory: "./out/white-label/dexhelm", binding: "ASSETS", run_worker_first: true },
};

test("Testnet-only Wrangler policy passes and unsafe inversions fail", () => {
  assert.deepEqual(checkWranglerConfiguration(safeConfig, { workerSource: "dexhelm.com app.dexhelm.com testnet.dexhelm.com status.dexhelm.com" }), []);

  for (const [mutate, pattern] of [
    [(config) => { config.workers_dev = true; }, /workers\.dev/i],
    [(config) => { config.vars.HYPERUNIT_MAINNET_URL = "https://api.hyperunit.xyz"; }, /mainnet/i],
    [(config) => { config.vars.HYPERUNIT_TESTNET_URL = "https://evil.example"; }, /testnet upstream/i],
    [(config) => { config.routes.push({ pattern: "*.example.com", custom_domain: true }); }, /custom-domain/i],
  ]) {
    const config = structuredClone(safeConfig);
    mutate(config);
    assert.match(checkWranglerConfiguration(config, { workerSource: "dexhelm.com app.dexhelm.com testnet.dexhelm.com status.dexhelm.com" }).join("\n"), pattern);
  }
});

test("artifact policy accepts Testnet proxy only and rejects direct or Mainnet authority", async () => {
  const safe = await inspectArtifactJavaScript([{ relativePath: "js/main.js", contents: "const endpoint='/api/hyperunit/testnet';" }]);
  assert.deepEqual(safe.failures, []);

  for (const contents of [
    "https://api.hyperunit-testnet.xyz/info",
    "const endpoint='/api/hyperunit/mainnet';",
    "const endpoint='/api/hyperunit';",
  ]) {
    const result = await inspectArtifactJavaScript([{ relativePath: "js/main.js", contents }]);
    assert.ok(result.failures.length > 0);
  }
});

test("repository preflight declares the current safe configuration", async () => {
  const config = JSON.parse(await fs.readFile(path.resolve("wrangler.jsonc"), "utf8"));
  const workerSource = await fs.readFile(path.resolve("workers/hyperopen-worker.mjs"), "utf8");
  assert.deepEqual(checkWranglerConfiguration(config, { workerSource }), []);
});
