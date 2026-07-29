import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  canonicalTenantJson,
  parseAndNormalizeTenantConfig,
  tenantConfigDigest,
} from "./tenant_config.mjs";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const dexhelmConfigPath = path.join(projectRoot, "config", "white-label", "dexhelm.json");
const builderAddress = "0x36a47878219fb346e031f6cf82cbfc8c77e35932";
const disclosure =
  "DEXHelm charges an additional 0.01% builder fee on eligible fills after you approve it.";

const disabledBuilderFee = Object.freeze({
  status: "disabled",
  "builder-address": null,
  "fee-tenths-bp": null,
  disclosure: "No DEXHelm builder fee is active in this release.",
});

const configuredBuilderFee = Object.freeze({
  status: "configured",
  "builder-address": builderAddress,
  "fee-tenths-bp": 10,
  disclosure,
});

const normalizedConfiguredBuilderFee = Object.freeze({
  ...configuredBuilderFee,
  "max-fee-rate": "0.01%",
});

async function dexhelmConfig() {
  return JSON.parse(await fs.readFile(dexhelmConfigPath, "utf8"));
}

function thrownError(run) {
  try {
    run();
  } catch (error) {
    return error;
  }
  assert.fail("Expected the strict builder-fee parser to reject the input.");
}

test("DEXHelm's checked-in Testnet release is configured for the approved canonical builder fee", async () => {
  const normalized = parseAndNormalizeTenantConfig(JSON.stringify(await dexhelmConfig()));

  assert.equal(normalized["hyperliquid-network"], "testnet");
  assert.deepEqual(normalized["builder-fee"], normalizedConfiguredBuilderFee);
  assert.doesNotMatch(canonicalTenantJson(normalized), /private|seed|secret|signature/i);
});

test("a synthetic disabled builder-fee fixture exposes no recipient, rate, or activation control", async () => {
  const normalized = parseAndNormalizeTenantConfig(JSON.stringify({
    ...(await dexhelmConfig()),
    "builder-fee": disabledBuilderFee,
  }));

  assert.deepEqual(normalized["builder-fee"], disabledBuilderFee);
  assert.equal(normalized["builder-fee"]["builder-address"], null);
  assert.equal(normalized["builder-fee"]["fee-tenths-bp"], null);
  assert.equal(normalized["builder-fee"]["max-fee-rate"], undefined);
});

test("a configured DEXHelm builder fee has one canonical public recipient and derived 0.01% approval rate", async () => {
  const configured = {
    ...(await dexhelmConfig()),
    "builder-fee": configuredBuilderFee,
  };
  const reordered = {
    "builder-fee": configuredBuilderFee,
    affiliate: configured.affiliate,
    venue: configured.venue,
    features: configured.features,
    "hyperliquid-network": configured["hyperliquid-network"],
    "theme/id": configured["theme/id"],
    "brand/logo-url": configured["brand/logo-url"],
    "brand/name": configured["brand/name"],
    "tenant/id": configured["tenant/id"],
  };

  const normalized = parseAndNormalizeTenantConfig(JSON.stringify(configured));
  const reorderedNormalized = parseAndNormalizeTenantConfig(JSON.stringify(reordered));

  assert.deepEqual(normalized["builder-fee"], normalizedConfiguredBuilderFee);
  assert.equal(normalized["builder-fee"]["fee-tenths-bp"], 10);
  assert.equal(normalized["builder-fee"]["max-fee-rate"], "0.01%");
  assert.equal(canonicalTenantJson(normalized), canonicalTenantJson(reorderedNormalized));
  assert.equal(tenantConfigDigest(normalized), tenantConfigDigest(reorderedNormalized));
});

test("configured fee tenths render one deterministic approval rate at each allowed boundary", async () => {
  const raw = await dexhelmConfig();

  for (const [feeTenthsBp, expectedRate] of [
    [1, "0.001%"],
    [10, "0.01%"],
    [100, "0.1%"],
  ]) {
    const normalized = parseAndNormalizeTenantConfig(JSON.stringify({
      ...raw,
      "builder-fee": { ...configuredBuilderFee, "fee-tenths-bp": feeTenthsBp },
    }));
    assert.equal(normalized["builder-fee"]["max-fee-rate"], expectedRate);
  }
});

test("builder-fee rejects ambiguity, latent disabled values, and secret-shaped input before build output", async () => {
  const raw = await dexhelmConfig();
  const secretSentinel = "RED_BUILDER_FEE_SECRET_MUST_NOT_ECHO";
  const invalidBuilderFees = [
    { ...configuredBuilderFee, "builder-address": builderAddress.toUpperCase() },
    { ...configuredBuilderFee, "builder-address": ` ${builderAddress}` },
    { ...configuredBuilderFee, "fee-tenths-bp": 0 },
    { ...configuredBuilderFee, "fee-tenths-bp": 10.5 },
    { ...configuredBuilderFee, "fee-tenths-bp": 101 },
    { ...configuredBuilderFee, disclosure: "" },
    { ...disabledBuilderFee, "builder-address": builderAddress },
    { ...configuredBuilderFee, "private-key": secretSentinel },
  ];

  for (const builderFee of invalidBuilderFees) {
    const error = thrownError(() =>
      parseAndNormalizeTenantConfig(JSON.stringify({ ...raw, "builder-fee": builderFee })),
    );
    assert.match(error.message, /builder-fee/i);
    assert.doesNotMatch(error.message, new RegExp(secretSentinel));
  }
});
