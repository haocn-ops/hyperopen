import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const sampleConfigPath = path.join(
  projectRoot,
  "config",
  "white-label",
  "example-enterprise.json"
);

async function loadTenantConfig() {
  return import("./tenant_config.mjs");
}

async function readSampleConfig() {
  return fs.readFile(sampleConfigPath, "utf8");
}

async function sampleObject() {
  return JSON.parse(await readSampleConfig());
}

function thrownError(run) {
  try {
    run();
  } catch (error) {
    return error;
  }

  assert.fail("Expected the strict tenant parser to reject the input.");
}

test("strict public tenant parsing normalizes the checked-in example into stable canonical data", async () => {
  const {
    parseAndNormalizeTenantConfig,
    canonicalTenantJson,
    tenantConfigDigest,
    enabledTenantRoutes,
  } = await loadTenantConfig();
  const raw = await readSampleConfig();
  const reordered = JSON.stringify({
    affiliate: (await sampleObject()).affiliate,
    venue: (await sampleObject()).venue,
    features: (await sampleObject()).features,
    "theme/id": "institutional",
    "brand/logo-url": "",
    "brand/name": "Enterprise Desk",
    "tenant/id": "enterprise-example",
  });

  const normalized = parseAndNormalizeTenantConfig(raw);
  const reorderedNormalized = parseAndNormalizeTenantConfig(reordered);
  const canonical = canonicalTenantJson(normalized);

  assert.deepEqual(normalized, {
    "tenant/id": "enterprise-example",
    "brand/name": "Enterprise Desk",
    "brand/logo-url": "",
    "theme/id": "institutional",
    features: { terminal: false, analytics: true, affiliate: true },
    venue: {
      id: "hyperliquid",
      label: "Hyperliquid",
      url: "https://app.hyperliquid.xyz",
    },
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
  assert.deepEqual(reorderedNormalized, normalized);
  assert.equal(canonical, canonicalTenantJson(reorderedNormalized));
  assert.match(canonical, /^\{.*"tenant\/id":"enterprise-example".*\}$/);
  assert.match(tenantConfigDigest(normalized), /^[A-F0-9]{16,64}$/);
  assert.equal(tenantConfigDigest(normalized), tenantConfigDigest(reorderedNormalized));
  assert.deepEqual(enabledTenantRoutes(normalized), ["/portfolio"]);
});

test("strict public tenant parsing rejects malformed, duplicate, unknown, unsafe, and secret-shaped input without fallback", async () => {
  const { parseAndNormalizeTenantConfig } = await loadTenantConfig();
  const valid = await sampleObject();
  const secretSentinel = "RED_SECRET_VALUE_MUST_NOT_ECHO";
  const invalidCases = [
    {
      name: "malformed JSON",
      raw: '{"tenant/id":',
      expected: /json|syntax/i,
    },
    {
      name: "decoded duplicate root key",
      raw: `{"tenant/id":"first","tenant\\u002fid":"second",${JSON.stringify(valid).slice(1)}`,
      expected: /duplicate.*tenant\/id/i,
    },
    {
      name: "unknown nested feature",
      raw: JSON.stringify({ ...valid, features: { ...valid.features, surprise: true } }),
      expected: /unknown.*features.*surprise|features.*surprise.*unknown/i,
    },
    {
      name: "all primary product routes disabled",
      raw: JSON.stringify({
        ...valid,
        features: { ...valid.features, terminal: false, analytics: false },
      }),
      expected: /features|terminal|analytics/i,
    },
    {
      name: "credential-bearing public URL",
      raw: JSON.stringify({
        ...valid,
        venue: { ...valid.venue, url: "https://red-user:RED_PASSWORD@example.invalid" },
      }),
      expected: /venue.*url|url.*venue/i,
    },
    {
      name: "secret-shaped public value",
      raw: JSON.stringify({ ...valid, "brand/name": secretSentinel }),
      expected: /secret|brand.*name/i,
    },
  ];

  for (const invalid of invalidCases) {
    const error = thrownError(() => parseAndNormalizeTenantConfig(invalid.raw));
    assert.match(error.message, invalid.expected, invalid.name);
    assert.doesNotMatch(error.message, new RegExp(secretSentinel));
    assert.doesNotMatch(error.message, /RED_PASSWORD/);
    assert.doesNotMatch(error.message, /hyperopen-default/i);
  }
});
