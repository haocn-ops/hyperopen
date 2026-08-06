import assert from "node:assert/strict";
import test from "node:test";

import { collectMainnetOpeningHandoff } from "./mainnet_opening_handoff.mjs";

const readiness = {
  testnetSettlement: true,
  externalMonitor: true,
  incidentOwner: true,
  rollbackDrill: true,
  phase8Signoff: true,
  phase9Authorization: true,
  complianceState: "approved",
  affiliateState: "disabled-unavailable",
  bundleDecision: "accepted-advisory-overage",
  monitorUrl: "https://monitor.example/dexhelm",
  incidentChannel: "ops://dexhelm-incidents",
};

const release = {
  candidateDigest: "CANDIDATE-DIGEST",
  testnetDigest: "TESTNET-DIGEST",
  mainnetDigest: "MAINNET-DIGEST",
};

function fixtureProbe() {
  return async () => ({ status: 200, contentType: "text/html; charset=utf-8", cacheControl: "no-store" });
}

test("handoff records complete redacted identity, release, host, and readiness evidence", async () => {
  const handoff = await collectMainnetOpeningHandoff({
    wrangler: {
      whoami: async () => ({ accountId: "account-redacted", workerName: "hyperopen" }),
      currentVersion: async () => ({ versionId: "version-new", rollbackVersionId: "version-old" }),
    },
    probe: fixtureProbe(),
    release,
    readiness,
  });

  assert.equal(handoff.worker.workerName, "hyperopen");
  assert.equal(handoff.worker.rollbackVersionId, "version-old");
  assert.equal(handoff.release.mainnetDigest, "MAINNET-DIGEST");
  assert.equal(handoff.hosts["app.dexhelm.com"].status, 200);
  assert.equal(handoff.readiness.complianceState, "approved");
  assert.equal(handoff.readiness.mainnetState, "open");
  assert.doesNotMatch(JSON.stringify(handoff), /token|cookie|authorization|wallet|signature|body|payload|secret/i);
});

test("handoff rejects missing human gates before any probe or deployment action", async () => {
  let probeCalls = 0;
  let deployCalls = 0;
  for (const missing of ["testnetSettlement", "externalMonitor", "incidentOwner", "rollbackDrill", "phase8Signoff", "phase9Authorization"]) {
    const incomplete = { ...readiness, [missing]: false };
    await assert.rejects(
      collectMainnetOpeningHandoff({
        wrangler: {
          whoami: async () => ({ accountId: "account", workerName: "hyperopen" }),
          currentVersion: async () => ({ versionId: "new", rollbackVersionId: "old" }),
          deploy: async () => { deployCalls += 1; },
        },
        probe: async () => { probeCalls += 1; return { status: 200 }; },
        release,
        readiness: incomplete,
      }),
      /not approved/i
    );
  }
  assert.equal(probeCalls, 0);
  assert.equal(deployCalls, 0);
});
