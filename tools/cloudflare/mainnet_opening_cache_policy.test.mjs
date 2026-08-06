import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  assembleDexhelmMainnetOpeningCandidate,
  verifyDexhelmMainnetOpeningCandidate,
} from "./build_dexhelm_mainnet_opening_candidate.mjs";
import { hashFullContent } from "../release-assets/generate_release_artifacts.mjs";

async function writeReleaseFixture(root, {
  origin,
  network,
  fingerprint,
  proxyBase,
}) {
  const mainScriptPath = `js/main.${fingerprint}.js`;
  const cssPath = `css/main.${fingerprint}.css`;
  await fs.mkdir(path.join(root, "js"), { recursive: true });
  await fs.mkdir(path.join(root, "css"), { recursive: true });
  await fs.writeFile(path.join(root, mainScriptPath), `const proxy = ${JSON.stringify(proxyBase)};\n`);
  await fs.writeFile(path.join(root, cssPath), "body { color: white; }\n");
  await fs.writeFile(path.join(root, "js", "portfolio_worker.js"), "self.onmessage = () => {};\n");
  await fs.writeFile(path.join(root, "sw.js"), "self.addEventListener('fetch', () => {});\n");
  await fs.writeFile(path.join(root, "trade.html"), [
    `<link rel="canonical" href="${origin}/trade">`,
    `<link rel="stylesheet" href="/${cssPath}">`,
    `<script src="/${mainScriptPath}"></script>`,
  ].join("\n"));
  await fs.writeFile(path.join(root, "_headers"), [
    "/*",
    "  Cache-Control: public, max-age=0, must-revalidate",
    "",
    `/${cssPath}`,
    "  ! Cache-Control",
    "  Cache-Control: public, max-age=31556952, immutable",
    "",
    `/${mainScriptPath}`,
    "  ! Cache-Control",
    "  Cache-Control: public, max-age=31556952, immutable",
    "",
  ].join("\n"));
  await fs.writeFile(path.join(root, "tenant-manifest.json"), `${JSON.stringify({
    version: 1,
    tenant: { "tenant/id": `dexhelm-${network}`, "hyperliquid-network": network },
    canonicalOrigin: origin,
    configDigest: `${network.toUpperCase()}-DIGEST`,
    mainScriptHref: `/${mainScriptPath}`,
    mainBundleDigest: "fixture",
    artifactDigests: {},
  })}\n`);
}

test("candidate assembly promotes Mainnet immutable cache rules into the deploy root", async () => {
  const fixtureRoot = await fs.mkdtemp(path.join(os.tmpdir(), "dexhelm-mainnet-cache-policy-"));
  const testnetRoot = path.join(fixtureRoot, "testnet");
  const mainnetRoot = path.join(fixtureRoot, "mainnet");
  const outputRoot = path.join(fixtureRoot, "candidate");
  await writeReleaseFixture(testnetRoot, {
    origin: "https://testnet.dexhelm.com",
    network: "testnet",
    fingerprint: "TESTNET0123456789",
    proxyBase: "/api/hyperunit/testnet",
  });
  await writeReleaseFixture(mainnetRoot, {
    origin: "https://app.dexhelm.com",
    network: "mainnet",
    fingerprint: "MAINNET0123456789",
    proxyBase: "/api/hyperunit/mainnet",
  });

  try {
    const verifyWhiteLabelRelease = async () => {};
    await assembleDexhelmMainnetOpeningCandidate(
      { testnetRoot, mainnetRoot, outputRoot },
      {},
      { verifyWhiteLabelRelease }
    );

    const headers = await fs.readFile(path.join(outputRoot, "_headers"), "utf8");
    assert.match(headers, /\/js\/main\.TESTNET0123456789\.js\n  ! Cache-Control\n  Cache-Control: public, max-age=31556952, immutable/);
    assert.match(headers, /\/__hyperopen_mainnet\/css\/main\.MAINNET0123456789\.css\n  ! Cache-Control\n  Cache-Control: public, max-age=31556952, immutable/);
    assert.match(headers, /\/__hyperopen_mainnet\/js\/main\.MAINNET0123456789\.js\n  ! Cache-Control\n  Cache-Control: public, max-age=31556952, immutable/);
    assert.doesNotMatch(headers, /\/__hyperopen_mainnet\/js\/portfolio_worker\.js[\s\S]*?immutable/);
    assert.doesNotMatch(headers, /\/__hyperopen_mainnet\/sw\.js[\s\S]*?immutable/);

    const rootManifest = JSON.parse(
      await fs.readFile(path.join(outputRoot, "tenant-manifest.json"), "utf8")
    );
    const rootHeadersDigest = hashFullContent(await fs.readFile(path.join(outputRoot, "_headers")));
    assert.equal(rootManifest.artifactDigests._headers, rootHeadersDigest);

    const candidateManifest = JSON.parse(
      await fs.readFile(path.join(outputRoot, "mainnet-opening-manifest.json"), "utf8")
    );
    assert.equal(candidateManifest.artifactDigests._headers, rootHeadersDigest);
    assert.equal(
      candidateManifest.artifactDigests["tenant-manifest.json"],
      hashFullContent(await fs.readFile(path.join(outputRoot, "tenant-manifest.json")))
    );

    await fs.appendFile(path.join(outputRoot, "_headers"), "tampered\n");
    await assert.rejects(
      verifyDexhelmMainnetOpeningCandidate(outputRoot, {}, { verifyWhiteLabelRelease }),
      /digest/i
    );
  } finally {
    await fs.rm(fixtureRoot, { recursive: true, force: true });
  }
});
