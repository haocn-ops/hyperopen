import assert from "node:assert/strict";
import test from "node:test";

import { parseAndNormalizeTenantConfig } from "./tenant_config.mjs";

const base = {
  "tenant/id": "canonical-edge",
  "brand/name": "Canonical Edge",
  "brand/logo-url": "",
  "theme/id": "institutional",
  features: { terminal: true, analytics: false, affiliate: true },
  venue: { id: "hyperliquid", label: "Venue", url: "https://venue.example" },
  affiliate: {
    provider: "hyperliquid", id: "partner", status: "enabled", "referral-url": "https://venue.example/ref",
    "event-endpoint": "https://events.example/collect", disclosure: "Disclosure",
  },
};

test("affiliate endpoint rejects credentials, fragments, non-HTTPS, and non-default ports", () => {
  for (const endpoint of [
    "https://user:pass@events.example/collect",
    "https://events.example/collect#fragment",
    "http://events.example/collect",
    "https://events.example:8443/collect",
  ]) {
    assert.throws(() => parseAndNormalizeTenantConfig(JSON.stringify({ ...base, affiliate: { ...base.affiliate, "event-endpoint": endpoint } })), /affiliate\.event-endpoint/);
  }
});

test("canonical endpoint normalization is idempotent", () => {
  const first = parseAndNormalizeTenantConfig(JSON.stringify(base));
  const second = parseAndNormalizeTenantConfig(JSON.stringify({ ...base, affiliate: { ...base.affiliate, "event-endpoint": first.affiliate["event-endpoint"] } }));
  assert.equal(second.affiliate["event-endpoint"], first.affiliate["event-endpoint"]);
});

test("affiliate endpoint rejects overlong raw input even when canonicalization shortens it", () => {
  const endpoint = `https://events.example.com/${"a/../".repeat(500)}`;
  assert.ok(endpoint.length > 2048);
  assert.throws(
    () => parseAndNormalizeTenantConfig(JSON.stringify({
      ...base,
      affiliate: { ...base.affiliate, "event-endpoint": endpoint },
    })),
    /affiliate\.event-endpoint/
  );
});
