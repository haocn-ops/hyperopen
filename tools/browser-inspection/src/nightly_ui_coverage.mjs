import { loadScenarios } from "./scenario_loader.mjs";

export const NIGHTLY_TAGS = [
  "critical",
  "funding",
  "wallet",
  "overlay",
  "account-surface",
  "parity",
  "mobile"
];

export const NIGHTLY_SPECTATE_ADDRESSES = [
  "0x162cc7c861ebd0c06b3d72319201150482518185",
  "0x2ba553d9f990a3b66b03b2dc0d030dfc1c061036",
  "0x4096d3377ae5ade578daae8188804740c8b1da3e"
];

const SPECTATE_SMOKE_IDS = new Set([
  "trade-route-smoke",
  "trade-route-smoke-mobile",
  "portfolio-route-smoke",
  "portfolio-route-smoke-mobile"
]);

const CONTRACT_SMOKE_IDS = new Set([
  ...SPECTATE_SMOKE_IDS,
  "vaults-route-smoke",
  "vaults-route-smoke-mobile"
]);

function shortAddress(address) {
  return String(address || "").slice(2, 6);
}

function setSpectate(url, address) {
  const parsed = new URL(url);
  parsed.searchParams.set("spectate", address);
  return parsed.toString();
}

function routePath(url) {
  try {
    return new URL(url).pathname;
  } catch (_error) {
    return String(url || "");
  }
}

function sortSpectateAddresses(addresses = []) {
  const seen = new Set();
  const known = [];
  const extras = [];

  for (const address of addresses) {
    if (!address || seen.has(address)) {
      continue;
    }
    seen.add(address);
    if (NIGHTLY_SPECTATE_ADDRESSES.includes(address)) {
      known.push(address);
      continue;
    }
    extras.push(address);
  }

  return [
    ...NIGHTLY_SPECTATE_ADDRESSES.filter((address) => known.includes(address)),
    ...extras.sort()
  ];
}

function cloneScenarioForSpectate(scenario, address) {
  const suffix = `spectate-${shortAddress(address)}`;
  const cloned = structuredClone(scenario);
  cloned.id = `${scenario.id}-${suffix}`;
  cloned.title = `${scenario.title} (${suffix})`;
  cloned.url = setSpectate(scenario.url, address);
  cloned.steps = (cloned.steps || []).map((step) => {
    if (step.type !== "compare") {
      return step;
    }
    return {
      ...step,
      rightUrl: setSpectate(step.rightUrl || scenario.url, address),
      rightLabel: `${step.rightLabel || scenario.id}-${suffix}`
    };
  });
  return cloned;
}

function isContractScenarioId(id) {
  return [...CONTRACT_SMOKE_IDS].some(
    (baseId) => id === baseId || String(id || "").startsWith(`${baseId}-spectate-`)
  );
}

export async function buildNightlyScenarios({ scenarioDir, tags = NIGHTLY_TAGS } = {}) {
  const scenarios = await loadScenarios({
    scenarioDir,
    tags
  });
  const expanded = [];

  for (const scenario of scenarios) {
    if (!SPECTATE_SMOKE_IDS.has(scenario.id)) {
      expanded.push(structuredClone(scenario));
      continue;
    }
    for (const address of NIGHTLY_SPECTATE_ADDRESSES) {
      expanded.push(cloneScenarioForSpectate(scenario, address));
    }
  }

  expanded.sort((left, right) => left.id.localeCompare(right.id));
  return expanded;
}

export function summarizeNightlyCoverage(scenarios = []) {
  const buckets = new Map();

  for (const scenario of scenarios) {
    if (!isContractScenarioId(scenario.id)) {
      continue;
    }
    const route = routePath(scenario.route || scenario.url);
    const spectateAddress = (() => {
      try {
        return new URL(scenario.url).searchParams.get("spectate");
      } catch (_error) {
        return null;
      }
    })();

    for (const viewport of scenario.viewports || []) {
      const key = `${route}::${viewport}`;
      if (!buckets.has(key)) {
        buckets.set(key, {
          route,
          viewport,
          expectedAttempts: 0,
          expectedSpectateAddresses: []
        });
      }
      const bucket = buckets.get(key);
      bucket.expectedAttempts += 1;
      if (spectateAddress) {
        bucket.expectedSpectateAddresses.push(spectateAddress);
      }
    }
  }

  return [...buckets.values()]
    .map((bucket) => ({
      ...bucket,
      expectedSpectateAddresses: sortSpectateAddresses(bucket.expectedSpectateAddresses)
    }))
    .sort((left, right) =>
      `${left.route}/${left.viewport}`.localeCompare(`${right.route}/${right.viewport}`)
    );
}

export function extractInspectedAddresses(results = []) {
  const seen = new Set();
  const extras = [];

  for (const result of results || []) {
    const url = result?.url;
    if (!url) {
      continue;
    }
    let address = null;
    try {
      address = new URL(url).searchParams.get("spectate");
    } catch (_error) {
      address = null;
    }
    if (!address || seen.has(address)) {
      continue;
    }
    seen.add(address);
    if (!NIGHTLY_SPECTATE_ADDRESSES.includes(address)) {
      extras.push(address);
    }
  }

  return [
    ...NIGHTLY_SPECTATE_ADDRESSES.filter((address) => seen.has(address)),
    ...extras
  ];
}
