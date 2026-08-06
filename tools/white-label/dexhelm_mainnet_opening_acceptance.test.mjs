import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  buildDexhelmMainnetRelease,
  DEXHELM_MAINNET_CANONICAL_ORIGIN,
  DEXHELM_MAINNET_CONFIG_PATH,
  DEXHELM_MAINNET_OUTPUT_PATH,
} from "../cloudflare/build_dexhelm_mainnet_release.mjs";
import {
  assembleDexhelmMainnetOpeningCandidate,
  MAINNET_CANDIDATE_PREFIX,
  verifyDexhelmMainnetOpeningCandidate,
} from "../cloudflare/build_dexhelm_mainnet_opening_candidate.mjs";
import { parseAndNormalizeTenantConfig } from "./tenant_config.mjs";

test("checked-in DEXHelm Mainnet tenant is explicit and fee-disabled before production approval", async () => {
  const tenant = parseAndNormalizeTenantConfig(
    await fs.readFile(DEXHELM_MAINNET_CONFIG_PATH, "utf8")
  );

  assert.equal(tenant["tenant/id"], "dexhelm-mainnet");
  assert.equal(tenant["brand/name"], "DEXHelm");
  assert.equal(tenant["brand/logo-url"], "https://app.dexhelm.com/brand/dexhelm-mark.svg");
  assert.equal(tenant["hyperliquid-network"], "mainnet");
  assert.equal(tenant["builder-fee"].status, "disabled");
  assert.equal(tenant["builder-fee"]["builder-address"], null);
  assert.equal(tenant["builder-fee"]["fee-tenths-bp"], null);
});

test("DEXHelm Mainnet release build uses its isolated config, origin, output, and rewrite mode", async () => {
  const calls = [];
  const result = await buildDexhelmMainnetRelease(
    { repositoryRoot: process.cwd() },
    {
      buildWhiteLabelRelease: async (options) => {
        calls.push(["build", options]);
        return { configDigest: "MAINNET-DIGEST", outputPath: options.outputPath };
      },
      rewriteReleaseJavaScript: async (outputPath, options) => {
        calls.push(["rewrite", outputPath, options]);
      },
      refreshTenantManifestDigests: async (outputPath) => {
        calls.push(["refresh", outputPath]);
      },
      verifyWhiteLabelRelease: async (options) => {
        calls.push(["verify", options]);
      },
    }
  );

  assert.equal(result.configDigest, "MAINNET-DIGEST");
  assert.deepEqual(calls, [
    ["build", {
      repositoryRoot: process.cwd(),
      configPath: DEXHELM_MAINNET_CONFIG_PATH,
      canonicalOrigin: DEXHELM_MAINNET_CANONICAL_ORIGIN,
      outputPath: DEXHELM_MAINNET_OUTPUT_PATH,
    }],
    ["rewrite", `${process.cwd()}/${DEXHELM_MAINNET_OUTPUT_PATH}`, { network: "mainnet" }],
    ["refresh", `${process.cwd()}/${DEXHELM_MAINNET_OUTPUT_PATH}`],
    ["verify", {
      repositoryRoot: process.cwd(),
      configPath: DEXHELM_MAINNET_CONFIG_PATH,
      canonicalOrigin: DEXHELM_MAINNET_CANONICAL_ORIGIN,
      outputPath: `${process.cwd()}/${DEXHELM_MAINNET_OUTPUT_PATH}`,
    }],
  ]);
});

async function writeReleaseFixture(root, { origin, network, proxyBase }) {
  const mainScriptName = "main.FIXTURE0123456789.js";
  await fs.mkdir(path.join(root, "js"), { recursive: true });
  await fs.writeFile(path.join(root, "js", mainScriptName), `const proxy = ${JSON.stringify(proxyBase)};\n`);
  await fs.writeFile(path.join(root, "trade.html"), `<link rel="canonical" href="${origin}/trade">`);
  await fs.writeFile(path.join(root, "_headers"), [
    "/*",
    "  Cache-Control: public, max-age=0, must-revalidate",
    "",
    `/js/${mainScriptName}`,
    "  ! Cache-Control",
    "  Cache-Control: public, max-age=31556952, immutable",
    "",
  ].join("\n"));
  await fs.writeFile(path.join(root, "tenant-manifest.json"), `${JSON.stringify({
    version: 1,
    tenant: { "tenant/id": `dexhelm-${network}`, "hyperliquid-network": network },
    canonicalOrigin: origin,
    configDigest: `${network.toUpperCase()}-DIGEST`,
    mainScriptHref: `/js/${mainScriptName}`,
  })}\n`);
}

test("candidate assembly keeps Testnet at root, Mainnet under a private prefix, and verifies every digest", async () => {
  const fixtureRoot = await fs.mkdtemp(path.join(os.tmpdir(), "dexhelm-mainnet-candidate-"));
  const testnetRoot = path.join(fixtureRoot, "testnet");
  const mainnetRoot = path.join(fixtureRoot, "mainnet");
  const outputRoot = path.join(fixtureRoot, "candidate");
  await writeReleaseFixture(testnetRoot, {
    origin: "https://testnet.dexhelm.com",
    network: "testnet",
    proxyBase: "/api/hyperunit/testnet",
  });
  await writeReleaseFixture(mainnetRoot, {
    origin: "https://app.dexhelm.com",
    network: "mainnet",
    proxyBase: "/api/hyperunit/mainnet",
  });

  try {
    const strictVerificationCalls = [];
    const verifyWhiteLabelRelease = async (options) => {
      strictVerificationCalls.push({
        canonicalOrigin: options.canonicalOrigin,
        configPath: options.configPath,
        outputPath: options.outputPath,
      });
    };
    const result = await assembleDexhelmMainnetOpeningCandidate({
      testnetRoot,
      mainnetRoot,
      outputRoot,
    }, {}, { verifyWhiteLabelRelease });
    assert.equal(result.outputRoot, outputRoot);
    assert.equal(strictVerificationCalls.length, 4);
    assert.deepEqual(
      strictVerificationCalls.map(({ canonicalOrigin }) => canonicalOrigin),
      [
        "https://testnet.dexhelm.com",
        "https://app.dexhelm.com",
        "https://testnet.dexhelm.com",
        "https://app.dexhelm.com",
      ]
    );
    assert.equal(await fs.readFile(path.join(outputRoot, "js", "main.FIXTURE0123456789.js"), "utf8"),
      'const proxy = "/api/hyperunit/testnet";\n');
    assert.equal(await fs.readFile(path.join(outputRoot, MAINNET_CANDIDATE_PREFIX, "js", "main.FIXTURE0123456789.js"), "utf8"),
      'const proxy = "/api/hyperunit/mainnet";\n');
    const verified = await verifyDexhelmMainnetOpeningCandidate(
      outputRoot,
      {},
      { verifyWhiteLabelRelease }
    );
    assert.equal(verified.testnetOrigin, "https://testnet.dexhelm.com");
    assert.equal(verified.mainnetOrigin, "https://app.dexhelm.com");

    await fs.appendFile(
      path.join(outputRoot, MAINNET_CANDIDATE_PREFIX, "js", "main.FIXTURE0123456789.js"),
      "tampered\n"
    );
    await assert.rejects(
      verifyDexhelmMainnetOpeningCandidate(outputRoot, {}, { verifyWhiteLabelRelease }),
      /digest/i
    );
  } finally {
    await fs.rm(fixtureRoot, { recursive: true, force: true });
  }
});

test("candidate assembly rejects source releases without strict inner digest manifests", async () => {
  const fixtureRoot = await fs.mkdtemp(path.join(os.tmpdir(), "dexhelm-mainnet-candidate-invalid-"));
  const testnetRoot = path.join(fixtureRoot, "testnet");
  const mainnetRoot = path.join(fixtureRoot, "mainnet");
  const outputRoot = path.join(fixtureRoot, "candidate");
  await writeReleaseFixture(testnetRoot, {
    origin: "https://testnet.dexhelm.com",
    network: "testnet",
    proxyBase: "/api/hyperunit/testnet",
  });
  await writeReleaseFixture(mainnetRoot, {
    origin: "https://app.dexhelm.com",
    network: "mainnet",
    proxyBase: "/api/hyperunit/mainnet",
  });

  try {
    await assert.rejects(
      assembleDexhelmMainnetOpeningCandidate({ testnetRoot, mainnetRoot, outputRoot }),
      /artifactDigests|mainBundleDigest|manifest/i
    );
  } finally {
    await fs.rm(fixtureRoot, { recursive: true, force: true });
  }
});
