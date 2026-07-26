import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  DEFAULT_CANONICAL_ORIGIN,
  PUBLIC_ROUTE_METADATA,
  buildSiteMetadata,
} from "./site_metadata.mjs";
import {
  DEFAULT_OUTPUT_ROOT,
  generateReleaseArtifacts,
} from "./generate_release_artifacts.mjs";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const tenant = Object.freeze({
  "tenant/id": "enterprise-example",
  "brand/name": "Enterprise Desk",
  "brand/logo-url": "https://cdn.example.com/enterprise-desk.svg",
  "theme/id": "institutional",
  features: { terminal: false, analytics: true, affiliate: true },
  venue: { id: "hyperliquid", label: "Hyperliquid", url: "https://app.hyperliquid.xyz" },
  affiliate: {
    provider: "hyperliquid",
    id: "enterprise-example-affiliate",
    status: "configured",
    "referral-url": "https://app.hyperliquid.xyz/?ref=enterprise-example-affiliate",
    "event-endpoint": "",
    disclosure:
      "Enterprise Desk may use a public Hyperliquid affiliate referral. Trading remains non-custodial.",
  },
});
const canonicalOrigin = "https://desk.example.com";

test("tenant-aware metadata brands public entries and filters only feature-owned routes", async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-white-label-metadata-"));
  const sourceRoot = path.join(tempRoot, "source");
  const outputRoot = path.join(tempRoot, "output");

  try {
    await fs.cp(path.join(projectRoot, "resources", "public"), sourceRoot, { recursive: true });
    const result = await generateReleaseArtifacts({
      sourceRoot,
      outputRoot,
      canonicalOrigin,
      tenant,
      tenantManifest: {
        version: 1,
        tenant,
        canonicalOrigin,
        enabledRoutes: ["/portfolio"],
        buildId: "WHITE_LABEL_TEST_BUILD",
        configDigest: "ABCDEF0123456789",
      },
      rewriteMainModule: false,
    });
    const siteMetadata = JSON.parse(await fs.readFile(path.join(outputRoot, "site-metadata.json"), "utf8"));
    const indexHtml = await fs.readFile(path.join(outputRoot, "index.html"), "utf8");
    const portfolioHtml = await fs.readFile(path.join(outputRoot, "portfolio.html"), "utf8");
    const sitemap = await fs.readFile(path.join(outputRoot, "sitemap.xml"), "utf8");
    const deployment = await fs.readFile(path.join(outputRoot, "DEPLOYMENT.md"), "utf8");
    const headers = await fs.readFile(path.join(outputRoot, "_headers"), "utf8");
    const manifest = JSON.parse(await fs.readFile(path.join(outputRoot, "tenant-manifest.json"), "utf8"));

    assert.equal(result.outputRoot, outputRoot);
    assert.equal(siteMetadata.siteName, "Enterprise Desk");
    assert.equal(siteMetadata.origin, canonicalOrigin);
    assert.doesNotMatch(JSON.stringify(siteMetadata.routes), /Hyperopen/i);
    assert.doesNotMatch(JSON.stringify(siteMetadata), /enterprise-example-affiliate|referral-url|affiliate/i);
    assert.equal(siteMetadata.routes.some((route) => route.path === "/trade"), false);
    assert.equal(siteMetadata.routes.some((route) => route.path === "/portfolio"), true);
    assert.match(indexHtml, /<title>[^<]*Enterprise Desk/i);
    assert.match(indexHtml, /https:\/\/desk\.example\.com/);
    assert.match(portfolioHtml, /Enterprise Desk/);
    assert.match(portfolioHtml, /og:title|twitter:title/);
    assert.match(sitemap, /https:\/\/desk\.example\.com\/portfolio/);
    assert.doesNotMatch(sitemap, /\/trade/);
    await assert.rejects(fs.access(path.join(outputRoot, "trade.html")));
    assert.deepEqual(manifest.tenant, tenant);
    assert.equal(manifest.canonicalOrigin, canonicalOrigin);
    assert.deepEqual(manifest.enabledRoutes, ["/portfolio"]);
    assert.match(deployment, /verify/i);
    assert.doesNotMatch(deployment, /enterprise-example-affiliate.*https?:\/\//i);
    assert.match(headers, /img-src[^\n]*https:\/\/cdn\.example\.com/);
  } finally {
    await fs.rm(tempRoot, { recursive: true, force: true });
  }
});

test("existing release metadata stays Hyperopen-compatible when tenant options are absent", () => {
  const metadata = buildSiteMetadata({ canonicalOrigin: undefined, indexHtml: "<head></head>" });

  assert.equal(metadata.siteName, "Hyperopen");
  assert.equal(metadata.origin, DEFAULT_CANONICAL_ORIGIN);
  assert.deepEqual(metadata.routes, PUBLIC_ROUTE_METADATA);
  assert.equal(DEFAULT_OUTPUT_ROOT, path.resolve("out/release-public"));
  assert.equal(Object.hasOwn(metadata, "tenant"), false);
});

test("same-origin tenant logo is copied into the verified release asset set", async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-white-label-logo-"));
  const sourceRoot = path.join(tempRoot, "source");
  const outputRoot = path.join(tempRoot, "output");
  const logoPath = "brand/tenant-mark.svg";

  try {
    await fs.cp(path.join(projectRoot, "resources", "public"), sourceRoot, { recursive: true });
    await fs.mkdir(path.join(sourceRoot, "brand"), { recursive: true });
    await fs.writeFile(path.join(sourceRoot, logoPath), "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>");
    await generateReleaseArtifacts({
      sourceRoot,
      outputRoot,
      canonicalOrigin,
      tenant: {
        ...tenant,
        "brand/logo-url": `${canonicalOrigin}/${logoPath}`,
      },
      rewriteMainModule: false,
    });

    const metadata = JSON.parse(await fs.readFile(path.join(outputRoot, "site-metadata.json"), "utf8"));
    assert.ok(metadata.rootAssetPaths.includes(`/${logoPath}`));
    assert.equal(
      await fs.readFile(path.join(outputRoot, logoPath), "utf8"),
      "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
    );
  } finally {
    await fs.rm(tempRoot, { recursive: true, force: true });
  }
});

test("tenant affiliate event endpoint contributes only its origin to connect-src", async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-white-label-connect-src-"));
  const sourceRoot = path.join(tempRoot, "source");
  const outputRoot = path.join(tempRoot, "output");
  const eventEndpoint = "https://events.enterprise.example/affiliate/record";

  try {
    await fs.cp(path.join(projectRoot, "resources", "public"), sourceRoot, { recursive: true });
    await generateReleaseArtifacts({
      sourceRoot,
      outputRoot,
      canonicalOrigin,
      tenant: {
        ...tenant,
        affiliate: { ...tenant.affiliate, "event-endpoint": eventEndpoint },
      },
      rewriteMainModule: false,
    });
    const headers = await fs.readFile(path.join(outputRoot, "_headers"), "utf8");

    assert.match(headers, /connect-src[^\n]*https:\/\/events\.enterprise\.example/);
    assert.doesNotMatch(headers, /affiliate\/record/);
  } finally {
    await fs.rm(tempRoot, { recursive: true, force: true });
  }
});
