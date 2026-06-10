import test from "node:test";
import assert from "node:assert/strict";
import {
  NIGHTLY_SPECTATE_ADDRESSES,
  NIGHTLY_TAGS,
  buildNightlyScenarios,
  extractInspectedAddresses,
  summarizeNightlyCoverage
} from "../src/nightly_ui_coverage.mjs";
import { getDefaultScenarioDir } from "../src/scenario_loader.mjs";

function spectateValue(url) {
  return new URL(url).searchParams.get("spectate");
}

test("buildNightlyScenarios expands trade and portfolio smoke routes across the required spectate matrix", async () => {
  const scenarios = await buildNightlyScenarios({
    scenarioDir: getDefaultScenarioDir(),
    tags: NIGHTLY_TAGS
  });

  const tradeSmoke = scenarios.filter((scenario) => scenario.id.startsWith("trade-route-smoke"));
  const portfolioSmoke = scenarios.filter((scenario) => scenario.id.startsWith("portfolio-route-smoke"));
  const vaultsSmoke = scenarios.filter((scenario) => scenario.id.startsWith("vaults-route-smoke"));

  assert.equal(tradeSmoke.some((scenario) => scenario.id === "trade-route-smoke"), false);
  assert.equal(portfolioSmoke.some((scenario) => scenario.id === "portfolio-route-smoke"), false);

  assert.deepEqual(
    [...new Set(tradeSmoke.map((scenario) => spectateValue(scenario.url)).filter(Boolean))],
    NIGHTLY_SPECTATE_ADDRESSES
  );
  assert.deepEqual(
    [...new Set(portfolioSmoke.map((scenario) => spectateValue(scenario.url)).filter(Boolean))],
    NIGHTLY_SPECTATE_ADDRESSES
  );

  for (const scenario of [...tradeSmoke, ...portfolioSmoke]) {
    const compareStep = scenario.steps.find((step) => step.type === "compare");
    assert.ok(compareStep);
    assert.equal(spectateValue(compareStep.rightUrl), spectateValue(scenario.url));
    assert.equal(spectateValue(compareStep.leftUrl), null);
  }

  assert.ok(vaultsSmoke.length >= 2);
  assert.ok(vaultsSmoke.every((scenario) => spectateValue(scenario.url) === null));
});

test("summarizeNightlyCoverage captures the intended route and viewport attempt matrix", async () => {
  const scenarios = await buildNightlyScenarios({
    scenarioDir: getDefaultScenarioDir(),
    tags: NIGHTLY_TAGS
  });

  assert.deepEqual(summarizeNightlyCoverage(scenarios), [
    {
      route: "/portfolio",
      viewport: "desktop",
      expectedAttempts: 3,
      expectedSpectateAddresses: NIGHTLY_SPECTATE_ADDRESSES
    },
    {
      route: "/portfolio",
      viewport: "mobile",
      expectedAttempts: 3,
      expectedSpectateAddresses: NIGHTLY_SPECTATE_ADDRESSES
    },
    {
      route: "/trade",
      viewport: "desktop",
      expectedAttempts: 3,
      expectedSpectateAddresses: NIGHTLY_SPECTATE_ADDRESSES
    },
    {
      route: "/trade",
      viewport: "mobile",
      expectedAttempts: 3,
      expectedSpectateAddresses: NIGHTLY_SPECTATE_ADDRESSES
    },
    {
      route: "/vaults",
      viewport: "desktop",
      expectedAttempts: 1,
      expectedSpectateAddresses: []
    },
    {
      route: "/vaults",
      viewport: "mobile",
      expectedAttempts: 1,
      expectedSpectateAddresses: []
    }
  ]);
});

test("extractInspectedAddresses returns unique nightly spectate addresses in contract order", () => {
  const addresses = extractInspectedAddresses([
    { url: `http://localhost:8080/portfolio?spectate=${NIGHTLY_SPECTATE_ADDRESSES[2]}` },
    { url: "http://localhost:8080/vaults" },
    { url: `http://localhost:8080/trade?spectate=${NIGHTLY_SPECTATE_ADDRESSES[0]}` },
    { url: `http://localhost:8080/trade?spectate=${NIGHTLY_SPECTATE_ADDRESSES[2]}` },
    { url: `http://localhost:8080/trade?spectate=${NIGHTLY_SPECTATE_ADDRESSES[1]}` }
  ]);

  assert.deepEqual(addresses, NIGHTLY_SPECTATE_ADDRESSES);
});
