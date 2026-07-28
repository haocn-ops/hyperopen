import assert from "node:assert/strict";
import test from "node:test";

import { parseAndNormalizeTenantConfig } from "./tenant_config.mjs";

test("enabled affiliate endpoint is emitted as one canonical href", () => {
  const normalized = parseAndNormalizeTenantConfig(JSON.stringify({
    "tenant/id": "canonical-example",
    "brand/name": "Canonical Example",
    "brand/logo-url": "",
    "theme/id": "institutional",
    features: { terminal: true, analytics: false, affiliate: true },
    venue: { id: "hyperliquid", label: "Venue", url: "https://venue.example" },
    affiliate: {
      provider: "hyperliquid",
      id: "partner",
      status: "enabled",
      "referral-url": "https://venue.example/ref",
      "event-endpoint": "HTTPS://EVENTS.Example.COM:443/a/../collect?campaign=one",
      disclosure: "Disclosure",
    },
  }));
  assert.equal(normalized.affiliate["event-endpoint"], "https://events.example.com/collect?campaign=one");
});
