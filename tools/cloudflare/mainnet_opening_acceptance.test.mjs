import assert from "node:assert/strict";
import fs from "node:fs/promises";
import test from "node:test";

const EXPECTED_DOMAINS = [
  "app.dexhelm.com",
  "dexhelm.com",
  "status.dexhelm.com",
  "testnet.dexhelm.com",
];

test("Mainnet candidate has an explicit dry-run-only Wrangler contract", async () => {
  const packageJson = JSON.parse(await fs.readFile("package.json", "utf8"));
  const candidateConfig = JSON.parse(await fs.readFile("wrangler.mainnet-opening.jsonc", "utf8"));
  const productionConfig = JSON.parse(await fs.readFile("wrangler.jsonc", "utf8"));

  assert.equal(
    packageJson.scripts["build:cloudflare:mainnet-candidate"],
    "node tools/cloudflare/build_dexhelm_mainnet_opening_candidate.mjs && node tools/security/release_xss_contract.mjs --release-root out/cloudflare/dexhelm-mainnet-opening"
  );
  assert.equal(
    packageJson.scripts["cloudflare:check:mainnet-candidate"],
    "npm run build:cloudflare:mainnet-candidate && wrangler deploy --dry-run --config wrangler.mainnet-opening.jsonc"
  );
  assert.equal(packageJson.scripts["deploy:cloudflare:mainnet"], undefined);

  assert.equal(candidateConfig.name, "hyperopen");
  assert.equal(candidateConfig.main, "./workers/hyperopen-worker.mjs");
  assert.equal(candidateConfig.workers_dev, false);
  assert.equal(candidateConfig.assets.directory, "./out/cloudflare/dexhelm-mainnet-opening");
  assert.equal(candidateConfig.assets.binding, "ASSETS");
  assert.equal(candidateConfig.assets.run_worker_first, true);
  assert.deepEqual(candidateConfig.vars, {
    HYPEROPEN_MAINNET_ENABLED: "true",
    HYPERUNIT_MAINNET_URL: "https://api.hyperunit.xyz",
    HYPERUNIT_TESTNET_URL: "https://api.hyperunit-testnet.xyz",
  });
  assert.deepEqual(
    candidateConfig.routes.map((route) => route.pattern).sort(),
    EXPECTED_DOMAINS
  );
  assert.ok(candidateConfig.routes.every((route) => route.custom_domain === true));

  assert.equal(productionConfig.vars.HYPEROPEN_MAINNET_ENABLED, undefined);
  assert.equal(productionConfig.vars.HYPERUNIT_MAINNET_URL, undefined);
  assert.equal(productionConfig.assets.directory, "./out/white-label/dexhelm");
  assert.equal(packageJson.scripts["deploy:cloudflare"], "npm run build:cloudflare && wrangler deploy");
});
