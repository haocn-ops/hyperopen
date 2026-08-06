const REQUIRED_HOSTS = Object.freeze([
  "dexhelm.com",
  "testnet.dexhelm.com",
  "app.dexhelm.com",
  "status.dexhelm.com",
]);

const FORBIDDEN_OUTPUT_KEYS = /token|secret|cookie|authorization|wallet|signature|body|payload|private/i;

function requireString(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`Missing ${label}.`);
  }
  return value;
}

function requireTrue(value, label) {
  if (value !== true) throw new Error(`${label} is not approved.`);
}

function assertHostMatrix(matrix) {
  if (!matrix || typeof matrix !== "object") throw new Error("Missing public host matrix.");
  for (const host of REQUIRED_HOSTS) {
    const entry = matrix[host];
    if (!entry || !Number.isInteger(entry.status)) throw new Error(`Missing host status: ${host}.`);
    if (entry.status !== (host === "app.dexhelm.com" ? 200 : 200)) {
      throw new Error(`Unexpected host status: ${host}.`);
    }
  }
}

function assertSecretFree(value, path = "handoff") {
  if (value && typeof value === "object") {
    for (const [key, child] of Object.entries(value)) {
      if (FORBIDDEN_OUTPUT_KEYS.test(key)) throw new Error(`Forbidden sensitive field: ${path}.${key}.`);
      assertSecretFree(child, `${path}.${key}`);
    }
  }
}

export async function collectMainnetOpeningHandoff({ wrangler, probe, release, readiness }) {
  if (!wrangler || typeof wrangler.whoami !== "function" || typeof wrangler.currentVersion !== "function") {
    throw new Error("Missing injected Wrangler identity functions.");
  }
  if (typeof probe !== "function") throw new Error("Missing injected HTTPS probe.");
  requireTrue(readiness?.testnetSettlement, "Testnet settlement");
  requireTrue(readiness?.externalMonitor, "External monitor");
  requireTrue(readiness?.incidentOwner, "Incident owner");
  requireTrue(readiness?.rollbackDrill, "Rollback drill");
  requireTrue(readiness?.phase8Signoff, "Phase 8 signoff");
  requireTrue(readiness?.phase9Authorization, "Phase 9 authorization");
  requireString(readiness.complianceState, "compliance state");
  requireString(readiness.affiliateState, "affiliate state");
  requireString(readiness.bundleDecision, "bundle decision");
  requireString(readiness.monitorUrl, "monitor URL");
  requireString(readiness.incidentChannel, "incident channel");
  requireString(release?.candidateDigest, "candidate digest");
  requireString(release?.testnetDigest, "Testnet digest");
  requireString(release?.mainnetDigest, "Mainnet digest");
  const identity = await wrangler.whoami();
  const version = await wrangler.currentVersion();
  requireString(identity?.accountId, "Cloudflare account id");
  requireString(identity?.workerName, "Worker name");
  requireString(version?.versionId, "current version id");
  requireString(version?.rollbackVersionId, "rollback version id");

  const hosts = {};
  for (const host of REQUIRED_HOSTS) {
    const result = await probe(`https://${host}/`);
    hosts[host] = {
      status: result.status,
      contentType: result.contentType || "",
      cacheControl: result.cacheControl || "",
    };
  }
  assertHostMatrix(hosts);
  const handoff = {
    worker: {
      accountId: identity.accountId,
      workerName: identity.workerName,
      versionId: version.versionId,
      rollbackVersionId: version.rollbackVersionId,
    },
    release: {
      candidateDigest: release.candidateDigest,
      testnetDigest: release.testnetDigest,
      mainnetDigest: release.mainnetDigest,
    },
    hosts,
    readiness: {
      monitorUrl: readiness.monitorUrl,
      incidentChannel: readiness.incidentChannel,
      complianceState: readiness.complianceState,
      affiliateState: readiness.affiliateState,
      bundleDecision: readiness.bundleDecision,
      mainnetState: "open",
    },
  };
  assertSecretFree(handoff);
  return handoff;
}

export { REQUIRED_HOSTS };
