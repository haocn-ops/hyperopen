import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { buildSiteMetadata } from "./site_metadata.mjs";
import { generateReleaseArtifacts } from "./generate_release_artifacts.mjs";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const canonicalOrigin = "https://desk.example.com";

function tenantWithFeatures(features) {
  return {
    "tenant/id": "route-matrix",
    "brand/name": "Route Matrix Desk",
    "brand/logo-url": "",
    "theme/id": "dark",
    features: { affiliate: false, ...features },
    venue: { id: "hyperliquid", label: "Hyperliquid", url: "https://app.hyperliquid.xyz" },
    affiliate: {
      provider: null,
      id: null,
      status: "unavailable",
      "referral-url": "",
      "event-endpoint": "",
      disclosure: "No affiliate program is configured.",
    },
  };
}

test("each disabled owned route is absent everywhere while unrelated public information routes remain", async () => {
  for (const [disabledPath, enabledPath, tenant] of [
    ["/trade", "/portfolio", tenantWithFeatures({ terminal: false, analytics: true })],
    ["/portfolio", "/trade", tenantWithFeatures({ terminal: true, analytics: false })],
  ]) {
    const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-white-label-route-matrix-"));
    const sourceRoot = path.join(tempRoot, "source");
    const outputRoot = path.join(tempRoot, "output");
    try {
      await fs.cp(path.join(projectRoot, "resources", "public"), sourceRoot, { recursive: true });
      await generateReleaseArtifacts({
        sourceRoot,
        outputRoot,
        canonicalOrigin,
        tenant,
        rewriteMainModule: false,
      });
      const siteMetadata = await fs.readFile(path.join(outputRoot, "site-metadata.json"), "utf8");
      const sitemap = await fs.readFile(path.join(outputRoot, "sitemap.xml"), "utf8");
      const routeScript = await fs.readFile(path.join(outputRoot, "js", "release-route-metadata.js"), "utf8");

      assert.doesNotMatch(siteMetadata, new RegExp(`"path":"${disabledPath}"`));
      assert.doesNotMatch(sitemap, new RegExp(`${disabledPath.replace("/", "\\/")}</loc>`));
      assert.doesNotMatch(routeScript, new RegExp(`"path":"${disabledPath}"`));
      await assert.rejects(fs.access(path.join(outputRoot, `${disabledPath.slice(1)}.html`)));
      assert.match(siteMetadata, new RegExp(`"path":"${enabledPath}"`));
      assert.match(siteMetadata, /"path":"\/leaderboard"/);
      assert.match(siteMetadata, /"path":"\/vaults"/);
      assert.equal(routeScript.includes("referral-url"), false);
      assert.equal(routeScript.includes("event-endpoint"), false);
    } finally {
      await fs.rm(tempRoot, { recursive: true, force: true });
    }
  }
});

test("ordinary metadata has no tenant provenance or generated tenant-only release files", async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-default-release-compatibility-"));
  const sourceRoot = path.join(tempRoot, "source");
  const outputRoot = path.join(tempRoot, "output");
  try {
    await fs.cp(path.join(projectRoot, "resources", "public"), sourceRoot, { recursive: true });
    const metadata = buildSiteMetadata({ canonicalOrigin: "https://app.hyperopen.example", indexHtml: "<head></head>" });
    await generateReleaseArtifacts({
      sourceRoot,
      outputRoot,
      canonicalOrigin: "https://app.hyperopen.example",
      rewriteMainModule: false,
    });

    assert.equal(metadata.siteName, "Hyperopen");
    assert.equal(Object.hasOwn(metadata, "tenant"), false);
    await assert.rejects(fs.access(path.join(outputRoot, "tenant-manifest.json")));
    await assert.rejects(fs.access(path.join(outputRoot, "DEPLOYMENT.md")));
    await fs.access(path.join(outputRoot, "trade.html"));
    await fs.access(path.join(outputRoot, "portfolio.html"));
  } finally {
    await fs.rm(tempRoot, { recursive: true, force: true });
  }
});
