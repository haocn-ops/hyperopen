import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { hashContent, hashFullContent } from "../release-assets/generate_release_artifacts.mjs";
import { parseAndNormalizeTenantConfig } from "../white-label/tenant_config.mjs";
import {
  DEXHELM_CANONICAL_ORIGIN,
  DEXHELM_CONFIG_PATH,
  DEXHELM_OUTPUT_PATH,
  refreshTenantManifestDigests,
} from "./build_dexhelm_release.mjs";

const repositoryRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");

test("DEXHelm Cloudflare release uses the checked-in public tenant identity", async () => {
  const tenant = parseAndNormalizeTenantConfig(
    await fs.readFile(path.join(repositoryRoot, DEXHELM_CONFIG_PATH), "utf8")
  );
  const logoPath = path.join(repositoryRoot, "resources/public/brand/dexhelm-mark.svg");

  assert.equal(tenant["tenant/id"], "dexhelm");
  assert.equal(tenant["brand/name"], "DEXHelm");
  assert.equal(
    tenant["brand/logo-url"],
    "https://testnet.dexhelm.com/brand/dexhelm-mark.svg"
  );
  assert.equal(DEXHELM_CANONICAL_ORIGIN, "https://testnet.dexhelm.com");
  assert.equal(DEXHELM_CONFIG_PATH, "config/white-label/dexhelm.json");
  assert.equal(DEXHELM_OUTPUT_PATH, "out/white-label/dexhelm");
  assert.match(await fs.readFile(logoPath, "utf8"), /viewBox="0 0 64 64"/);

  const packageJson = JSON.parse(await fs.readFile(path.join(repositoryRoot, "package.json"), "utf8"));
  const wrangler = JSON.parse(await fs.readFile(path.join(repositoryRoot, "wrangler.jsonc"), "utf8"));
  assert.equal(
    packageJson.scripts["build:cloudflare"],
    "node tools/cloudflare/build_dexhelm_release.mjs && node tools/security/release_xss_contract.mjs --release-root out/white-label/dexhelm"
  );
  assert.equal(wrangler.assets.directory, "./out/white-label/dexhelm");
});

test("refreshTenantManifestDigests records the rewritten main bundle and every release artifact", async () => {
  const releaseRoot = await fs.mkdtemp(path.join(os.tmpdir(), "dexhelm-cloudflare-release-"));
  try {
    await fs.mkdir(path.join(releaseRoot, "js"), { recursive: true });
    const mainBundle = Buffer.from('const endpoint = "/api/hyperunit/testnet";');
    const tradeDocument = Buffer.from("<!doctype html><title>DEXHelm</title>");
    await fs.writeFile(path.join(releaseRoot, "js/main.BRANDED.js"), mainBundle);
    await fs.writeFile(path.join(releaseRoot, "trade.html"), tradeDocument);
    await fs.writeFile(
      path.join(releaseRoot, "tenant-manifest.json"),
      `${JSON.stringify({
        version: 1,
        tenant: { "tenant/id": "dexhelm", "brand/name": "DEXHelm" },
        mainScriptHref: "/js/main.BRANDED.js",
        mainBundleDigest: "stale",
        artifactDigests: {},
      })}\n`
    );

    const manifest = await refreshTenantManifestDigests(releaseRoot);

    assert.equal(manifest.mainBundleDigest, hashContent(mainBundle));
    assert.deepEqual(manifest.artifactDigests, {
      "js/main.BRANDED.js": hashFullContent(mainBundle),
      "trade.html": hashFullContent(tradeDocument),
    });
    assert.deepEqual(
      JSON.parse(await fs.readFile(path.join(releaseRoot, "tenant-manifest.json"), "utf8")),
      manifest
    );
  } finally {
    await fs.rm(releaseRoot, { recursive: true, force: true });
  }
});
