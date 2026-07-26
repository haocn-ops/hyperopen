import test from "node:test";
import assert from "node:assert/strict";
import {
  buildNightlyOutcome,
  classifyNightlyFailures,
  fileNightlyIssues
} from "../src/nightly_ui_qa.mjs";
import { NIGHTLY_SPECTATE_ADDRESSES } from "../src/nightly_ui_coverage.mjs";

const EXPECTED_TRADE_COVERAGE = [
  {
    route: "/trade",
    viewport: "desktop",
    expectedAttempts: 3,
    expectedSpectateAddresses: NIGHTLY_SPECTATE_ADDRESSES
  }
];

function tradeSpectateResult(address, overrides = {}) {
  const suffix = address.slice(2, 6);
  return {
    scenarioId: `trade-route-smoke-spectate-${suffix}`,
    viewport: "desktop",
    route: "/trade",
    severity: "critical",
    state: "pass",
    url: `http://localhost:8080/trade?spectate=${address}`,
    ...overrides
  };
}

test("nightly classification counts only coverage-contract scenarios", () => {
  const contractResults = NIGHTLY_SPECTATE_ADDRESSES.map((address) =>
    tradeSpectateResult(address)
  );
  const additionalTradeResults = [
    {
      scenarioId: "asset-selection-eth",
      viewport: "desktop",
      route: "/trade",
      severity: "critical",
      state: "pass",
      url: "http://localhost:8080/trade"
    },
    {
      scenarioId: "wallet-enable-trading-simulated",
      viewport: "desktop",
      route: "/trade",
      severity: "critical",
      state: "pass",
      url: "http://localhost:8080/trade"
    }
  ];

  const classification = classifyNightlyFailures(
    {
      state: "pass",
      results: [...contractResults, ...additionalTradeResults]
    },
    null,
    EXPECTED_TRADE_COVERAGE
  );

  assert.equal(classification.classification, "pass");
  assert.equal(classification.coverageContractSatisfied, true);
  assert.equal(classification.routeCoverage[0].attempted, 3);
  assert.deepEqual(classification.coverageContractGaps, []);
});

test("nightly classification marks a missing contract attempt as a new automation gap", () => {
  const results = NIGHTLY_SPECTATE_ADDRESSES.slice(0, 2).map((address) =>
    tradeSpectateResult(address)
  );

  const classification = classifyNightlyFailures(
    { state: "pass", results },
    null,
    EXPECTED_TRADE_COVERAGE
  );

  assert.equal(classification.classification, "automation-gap");
  assert.equal(classification.novelty, "NEW");
  assert.deepEqual(classification.newCoverageContractGaps, [
    {
      route: "/trade",
      viewport: "desktop",
      attempted: 2,
      expectedAttempts: 3,
      missingSpectateAddresses: [NIGHTLY_SPECTATE_ADDRESSES[2]],
      novelty: "NEW"
    }
  ]);
  assert.deepEqual(classification.persistentCoverageContractGaps, []);
});

test("nightly classification marks a repeated contract gap as existing", () => {
  const results = NIGHTLY_SPECTATE_ADDRESSES.slice(0, 2).map((address) =>
    tradeSpectateResult(address)
  );
  const summary = { state: "pass", results };

  const classification = classifyNightlyFailures(
    summary,
    summary,
    EXPECTED_TRADE_COVERAGE
  );

  assert.equal(classification.classification, "automation-gap");
  assert.equal(classification.novelty, "EXISTING");
  assert.deepEqual(classification.newCoverageContractGaps, []);
  assert.deepEqual(classification.persistentCoverageContractGaps, [
    {
      route: "/trade",
      viewport: "desktop",
      attempted: 2,
      expectedAttempts: 3,
      missingSpectateAddresses: [NIGHTLY_SPECTATE_ADDRESSES[2]],
      novelty: "EXISTING"
    }
  ]);
});

test("nightly classification detects a missing address even when attempt counts match", () => {
  const results = [
    tradeSpectateResult(NIGHTLY_SPECTATE_ADDRESSES[0]),
    tradeSpectateResult(NIGHTLY_SPECTATE_ADDRESSES[1]),
    tradeSpectateResult(NIGHTLY_SPECTATE_ADDRESSES[1], {
      scenarioId: "trade-route-smoke-spectate-4096"
    })
  ];

  const classification = classifyNightlyFailures(
    { state: "pass", results },
    null,
    EXPECTED_TRADE_COVERAGE
  );

  assert.equal(classification.classification, "automation-gap");
  assert.deepEqual(classification.newCoverageContractGaps, [
    {
      route: "/trade",
      viewport: "desktop",
      attempted: 3,
      expectedAttempts: 3,
      missingSpectateAddresses: [NIGHTLY_SPECTATE_ADDRESSES[2]],
      novelty: "NEW"
    }
  ]);
});

test("nightly outcome uses the final classification as the overall state", () => {
  const results = NIGHTLY_SPECTATE_ADDRESSES.slice(0, 2).map((address) =>
    tradeSpectateResult(address)
  );

  const { classification, overallState } = buildNightlyOutcome({
    summary: { state: "pass", results },
    previousSummary: null,
    expectedRouteCoverage: EXPECTED_TRADE_COVERAGE,
    designReviewState: "PASS"
  });

  assert.equal(classification.classification, "automation-gap");
  assert.equal(classification.overallState, "automation-gap");
  assert.equal(overallState, "automation-gap");
});

test("nightly issue filing creates tasks immediately for new automation gaps", async () => {
  const filedIssues = await fileNightlyIssues({
    classification: {
      newProductRegressions: [],
      newAutomationGaps: [
        {
          scenarioId: "trade-route-smoke-spectate-162c",
          viewport: "desktop",
          route: "/trade",
          state: "automation-gap",
          message: "Browser session failed to start."
        }
      ],
      persistentAutomationGaps: [],
      newCoverageContractGaps: [
        {
          route: "/trade",
          viewport: "mobile",
          attempted: 2,
          expectedAttempts: 3,
          missingSpectateAddresses: [NIGHTLY_SPECTATE_ADDRESSES[2]],
          novelty: "NEW"
        }
      ],
      persistentCoverageContractGaps: []
    },
    repoRoot: "/repo",
    runDir: "/artifacts/nightly-run",
    runId: "nightly-run",
    reportPath: "/repo/docs/qa/nightly.md",
    previousRunDir: null,
    dryRun: true
  });

  assert.equal(filedIssues.length, 2);
  assert.deepEqual(
    filedIssues.map(({ type, priority }) => ({ type, priority })),
    [
      { type: "task", priority: 2 },
      { type: "task", priority: 2 }
    ]
  );
});

test("nightly issue filing maps critical and high product regressions to priorities 1 and 2", async () => {
  const filedIssues = await fileNightlyIssues({
    classification: {
      newProductRegressions: [
        {
          scenarioId: "trade-critical",
          viewport: "desktop",
          route: "/trade",
          severity: "critical",
          state: "product-regression",
          message: "Critical regression."
        },
        {
          scenarioId: "portfolio-high",
          viewport: "mobile",
          route: "/portfolio",
          severity: "high",
          state: "product-regression",
          message: "High regression."
        }
      ],
      newAutomationGaps: [],
      persistentAutomationGaps: [],
      newCoverageContractGaps: [],
      persistentCoverageContractGaps: []
    },
    repoRoot: "/repo",
    runDir: "/artifacts/nightly-run",
    runId: "nightly-run",
    reportPath: "/repo/docs/qa/nightly.md",
    previousRunDir: null,
    dryRun: true
  });

  assert.deepEqual(
    filedIssues.map(({ type, priority }) => ({ type, priority })),
    [
      { type: "bug", priority: 1 },
      { type: "bug", priority: 2 }
    ]
  );
});
