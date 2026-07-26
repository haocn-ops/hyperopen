#!/usr/bin/env node
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { BrowserInspectionService } from "./service.mjs";
import { runDesignReview } from "./design_review_runner.mjs";
import {
  buildNightlyScenarios,
  extractInspectedAddresses,
  isContractScenarioId,
  NIGHTLY_SPECTATE_ADDRESSES,
  NIGHTLY_TAGS,
  summarizeNightlyCoverage
} from "./nightly_ui_coverage.mjs";
import { runScenarioBundle } from "./scenario_runner.mjs";
import { safeNowIso, writeJsonFile } from "./util.mjs";

const execFileAsync = promisify(execFile);

function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) {
      out._.push(token);
      continue;
    }
    const stripped = token.slice(2);
    if (stripped.includes("=")) {
      const [key, ...rest] = stripped.split("=");
      out[key] = rest.join("=");
      continue;
    }
    const next = argv[i + 1];
    if (!next || next.startsWith("--")) {
      out[stripped] = true;
      continue;
    }
    out[stripped] = next;
    i += 1;
  }
  return out;
}

function reportDateString(timeZone = "America/New_York") {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(new Date());
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  return `${year}-${month}-${day}`;
}

function scenarioKey(result) {
  return `${result.scenarioId}::${result.viewport}`;
}

function summarizeRouteCoverage(results) {
  const buckets = new Map();
  for (const result of results || []) {
    if (!isContractScenarioId(result.scenarioId)) {
      continue;
    }
    const key = `${result.route}::${result.viewport}`;
    if (!buckets.has(key)) {
      buckets.set(key, {
        route: result.route,
        viewport: result.viewport,
        attempted: 0,
        pass: 0,
        failed: 0,
        inspectedSpectateAddresses: []
      });
    }
    const bucket = buckets.get(key);
    bucket.attempted += 1;
    try {
      const spectateAddress = new URL(result.url).searchParams.get("spectate");
      if (
        spectateAddress &&
        !bucket.inspectedSpectateAddresses.includes(spectateAddress)
      ) {
        bucket.inspectedSpectateAddresses.push(spectateAddress);
      }
    } catch (_error) {
      // Missing or invalid result URLs are accounted for as absent contract coverage.
    }
    if (result.state === "pass") {
      bucket.pass += 1;
    } else {
      bucket.failed += 1;
    }
  }
  return [...buckets.values()].sort((left, right) =>
    `${left.route}/${left.viewport}`.localeCompare(`${right.route}/${right.viewport}`)
  );
}

function mergeRouteCoverage(actualCoverage, expectedCoverage = []) {
  const actualByKey = new Map(
    (actualCoverage || []).map((entry) => [`${entry.route}::${entry.viewport}`, entry])
  );
  const expectedByKey = new Map(
    (expectedCoverage || []).map((entry) => [`${entry.route}::${entry.viewport}`, entry])
  );
  const keys = new Set([...expectedByKey.keys(), ...actualByKey.keys()]);

  return [...keys]
    .map((key) => {
      const actual = actualByKey.get(key);
      const expected = expectedByKey.get(key);
      return {
        route: expected?.route || actual?.route,
        viewport: expected?.viewport || actual?.viewport,
        attempted: actual?.attempted || 0,
        expectedAttempts: expected?.expectedAttempts ?? actual?.attempted ?? 0,
        pass: actual?.pass || 0,
        failed: actual?.failed || 0,
        expectedSpectateAddresses: expected?.expectedSpectateAddresses || [],
        inspectedSpectateAddresses: actual?.inspectedSpectateAddresses || []
      };
    })
    .sort((left, right) =>
      `${left.route}/${left.viewport}`.localeCompare(`${right.route}/${right.viewport}`)
    );
}

function coverageGapKey(entry) {
  return `${entry.route}::${entry.viewport}`;
}

function coverageContractGapsFor(summary, expectedRouteCoverage) {
  return mergeRouteCoverage(
    summarizeRouteCoverage(summary?.results),
    expectedRouteCoverage
  )
    .map((entry) => ({
      route: entry.route,
      viewport: entry.viewport,
      attempted: entry.attempted,
      expectedAttempts: entry.expectedAttempts,
      missingSpectateAddresses: entry.expectedSpectateAddresses.filter(
        (address) => !entry.inspectedSpectateAddresses.includes(address)
      )
    }))
    .filter(
      (entry) =>
        entry.attempted !== entry.expectedAttempts ||
        entry.missingSpectateAddresses.length > 0
    );
}

function summarizeStateCounts(results) {
  return (results || []).reduce(
    (acc, result) => {
      acc[result.state] = (acc[result.state] || 0) + 1;
      return acc;
    },
    { pass: 0, "product-regression": 0, "automation-gap": 0, "manual-exception": 0 }
  );
}

function severityRank(severity) {
  switch (severity) {
    case "critical":
      return 0;
    case "high":
      return 1;
    case "medium":
      return 2;
    case "low":
    default:
      return 3;
  }
}

function toTsv(rows) {
  return rows.map((row) => row.join("\t")).join("\n");
}

async function readJson(filePath, fallback = null) {
  try {
    const raw = await fs.readFile(filePath, "utf8");
    return JSON.parse(raw);
  } catch (_error) {
    return fallback;
  }
}

async function listRunIdsByPrefix(rootDir, prefix) {
  const entries = await fs.readdir(rootDir, { withFileTypes: true }).catch(() => []);
  return entries
    .filter((entry) => entry.isDirectory() && entry.name.startsWith(prefix))
    .map((entry) => entry.name)
    .sort();
}

async function findPreviousNightlyRun(artifactRoot, currentRunDir) {
  const runIds = await listRunIdsByPrefix(artifactRoot, "nightly-ui-qa-");
  const current = path.basename(currentRunDir);
  const previous = runIds.filter((runId) => runId !== current).at(-1);
  return previous ? path.join(artifactRoot, previous) : null;
}

async function gitBranch(repoRoot) {
  const { stdout } = await execFileAsync("git", ["rev-parse", "--abbrev-ref", "HEAD"], {
    cwd: repoRoot
  });
  return stdout.trim();
}

export function classifyNightlyFailures(summary, previousSummary, expectedRouteCoverage = []) {
  const previousResults = new Map(
    (previousSummary?.results || []).map((result) => [scenarioKey(result), result])
  );

  const results = [...(summary?.results || [])].sort((left, right) => {
    const severityOrder = severityRank(left.severity) - severityRank(right.severity);
    if (severityOrder !== 0) {
      return severityOrder;
    }
    return scenarioKey(left).localeCompare(scenarioKey(right));
  });

  const newProductRegressions = results.filter((result) => {
    if (result.state !== "product-regression") {
      return false;
    }
    if (!["critical", "high"].includes(result.severity || "high")) {
      return false;
    }
    return previousResults.get(scenarioKey(result))?.state !== "product-regression";
  });

  const newAutomationGaps = results.filter((result) => {
    if (result.state !== "automation-gap") {
      return false;
    }
    return previousResults.get(scenarioKey(result))?.state !== "automation-gap";
  });

  const persistentAutomationGaps = results.filter((result) => {
    if (result.state !== "automation-gap") {
      return false;
    }
    return previousResults.get(scenarioKey(result))?.state === "automation-gap";
  });

  const manualExceptions = results.filter((result) => result.state === "manual-exception");
  const stateCounts = summarizeStateCounts(results);
  const routeCoverage = mergeRouteCoverage(
    summarizeRouteCoverage(results),
    expectedRouteCoverage
  );
  const currentCoverageContractGaps = coverageContractGapsFor(
    summary,
    expectedRouteCoverage
  );
  const previousCoverageGapKeys = new Set(
    previousSummary
      ? coverageContractGapsFor(previousSummary, expectedRouteCoverage).map(coverageGapKey)
      : []
  );
  const coverageContractGaps = currentCoverageContractGaps.map((entry) => ({
    ...entry,
    novelty: previousCoverageGapKeys.has(coverageGapKey(entry)) ? "EXISTING" : "NEW"
  }));
  const newCoverageContractGaps = coverageContractGaps.filter(
    (entry) => entry.novelty === "NEW"
  );
  const persistentCoverageContractGaps = coverageContractGaps.filter(
    (entry) => entry.novelty === "EXISTING"
  );
  const classification =
    coverageContractGaps.length > 0 && summary.state === "pass" ? "automation-gap" : summary.state;
  const novelty =
    classification === "pass"
      ? null
      : newProductRegressions.length > 0 ||
          newAutomationGaps.length > 0 ||
          newCoverageContractGaps.length > 0 ||
          !previousSummary
        ? "NEW"
        : "EXISTING";

  return {
    classification,
    novelty,
    summary:
      coverageContractGaps.length > 0
        ? "Nightly scenario bundle did not match the expected coverage contract."
        : summary.state === "pass"
        ? "Nightly scenario bundle completed without failing scenarios."
        : `Nightly scenario bundle finished with state ${summary.state}.`,
    stateCounts,
    newProductRegressions,
    newAutomationGaps,
    persistentAutomationGaps,
    manualExceptions,
    inspectedAddresses: extractInspectedAddresses(results),
    routeCoverage,
    coverageContractSatisfied: coverageContractGaps.length === 0,
    coverageContractGaps,
    newCoverageContractGaps,
    persistentCoverageContractGaps,
    generatedAt: safeNowIso()
  };
}

export function combinedNightlyState(scenarioState, designReviewState) {
  if (scenarioState !== "pass") {
    return scenarioState;
  }
  if (designReviewState === "FAIL") {
    return "design-review-fail";
  }
  if (designReviewState === "BLOCKED") {
    return "design-review-blocked";
  }
  return "pass";
}

export function buildNightlyOutcome({
  summary,
  previousSummary,
  expectedRouteCoverage,
  designReviewState
}) {
  const classification = classifyNightlyFailures(
    summary,
    previousSummary,
    expectedRouteCoverage
  );
  const overallState = combinedNightlyState(
    classification.classification,
    designReviewState
  );
  classification.overallState = overallState;
  classification.designReviewState = designReviewState;
  return {
    classification,
    overallState
  };
}

async function createBdIssue({ title, description, type, priority, cwd, dryRun }) {
  if (dryRun) {
    return {
      dryRun: true,
      title,
      type,
      priority
    };
  }

  try {
    const { stdout } = await execFileAsync(
      "bd",
      [
        "create",
        title,
        `--description=${description}`,
        "-t",
        type,
        "-p",
        String(priority),
        "--json"
      ],
      { cwd }
    );
    return JSON.parse(stdout);
  } catch (error) {
    return {
      error: error?.message || String(error),
      title,
      type,
      priority
    };
  }
}

export async function fileNightlyIssues({
  classification,
  repoRoot,
  runDir,
  runId,
  reportPath,
  previousRunDir,
  dryRun
}) {
  const issues = [];

  for (const result of classification.newProductRegressions || []) {
    const description =
      `Nightly UI QA detected a new ${result.severity} product regression.\n\n` +
      `Scenario: ${result.scenarioId}\n` +
      `Viewport: ${result.viewport}\n` +
      `Route: ${result.route}\n` +
      `State: ${result.state}\n` +
      `Message: ${result.message || "n/a"}\n` +
      `Run: ${runId}\n` +
      `Artifacts: ${runDir}\n` +
      `Report: ${reportPath}\n` +
      `Previous nightly: ${previousRunDir || "none"}`;

    issues.push(
      await createBdIssue({
        title: `Nightly UI regression: ${result.scenarioId} (${result.viewport})`,
        description,
        type: "bug",
        priority: result.severity === "critical" ? 1 : 2,
        cwd: repoRoot,
        dryRun
      })
    );
  }

  for (const result of classification.newAutomationGaps || []) {
    const description =
      `Nightly UI QA detected a new automation gap.\n\n` +
      `Scenario: ${result.scenarioId}\n` +
      `Viewport: ${result.viewport}\n` +
      `Route: ${result.route}\n` +
      `State: ${result.state}\n` +
      `Message: ${result.message || "n/a"}\n` +
      `Run: ${runId}\n` +
      `Artifacts: ${runDir}\n` +
      `Report: ${reportPath}\n` +
      `Previous nightly: ${previousRunDir || "none"}`;

    issues.push(
      await createBdIssue({
        title: `Nightly UI automation gap: ${result.scenarioId} (${result.viewport})`,
        description,
        type: "task",
        priority: 2,
        cwd: repoRoot,
        dryRun
      })
    );
  }

  if ((classification.newCoverageContractGaps || []).length > 0) {
    const gapLines = classification.newCoverageContractGaps
      .map((gap) => {
        const missingAddresses =
          gap.missingSpectateAddresses.length > 0
            ? `; missing spectate addresses ${gap.missingSpectateAddresses.join(", ")}`
            : "";
        return `- ${gap.route} / ${gap.viewport}: attempted ${gap.attempted}/${gap.expectedAttempts}${missingAddresses}`;
      })
      .join("\n");
    const description =
      `Nightly UI QA detected a new coverage-contract automation gap.\n\n` +
      `${gapLines}\n\n` +
      `Run: ${runId}\n` +
      `Artifacts: ${runDir}\n` +
      `Report: ${reportPath}\n` +
      `Previous nightly: ${previousRunDir || "none"}`;

    issues.push(
      await createBdIssue({
        title: "Nightly UI coverage contract gap",
        description,
        type: "task",
        priority: 2,
        cwd: repoRoot,
        dryRun
      })
    );
  }

  return issues;
}

function renderReport({
  overallState,
  summary,
  designReview,
  classification,
  branch,
  reportDate,
  reportPath,
  previousRunDir,
  filedIssues
}) {
  const counts = classification.stateCounts;
  const routeCoverageLines = classification.routeCoverage
    .map(
      (entry) =>
        `- \`${entry.route}\` / \`${entry.viewport}\`: attempted ${entry.attempted}/${entry.expectedAttempts}, pass ${entry.pass}, failed ${entry.failed}`
    )
    .join("\n");
  const inspectedAddressLines =
    classification.inspectedAddresses.length === 0
      ? "- None."
      : classification.inspectedAddresses.map((address) => `- \`${address}\``).join("\n");
  const coverageGapLines =
    classification.coverageContractGaps.length === 0
      ? "- None."
      : classification.coverageContractGaps
          .map((entry) => {
            const missingAddresses =
              entry.missingSpectateAddresses.length > 0
                ? `; missing spectate addresses ${entry.missingSpectateAddresses
                    .map((address) => `\`${address}\``)
                    .join(", ")}`
                : "";
            return `- \`${entry.route}\` / \`${entry.viewport}\` / \`${entry.novelty}\`: attempted ${entry.attempted}/${entry.expectedAttempts}${missingAddresses}`;
          })
          .join("\n");

  const productRegressionLines =
    classification.newProductRegressions.length === 0
      ? "- None."
      : classification.newProductRegressions
          .map(
            (result) =>
              `- \`${result.scenarioId}\` / \`${result.viewport}\` / \`${result.severity}\`: ${result.message || "no message"}`
          )
          .join("\n");

  const automationGapLines =
    classification.persistentAutomationGaps.length === 0
      ? "- None."
      : classification.persistentAutomationGaps
          .map(
            (result) =>
              `- \`${result.scenarioId}\` / \`${result.viewport}\`: ${result.message || "no message"}`
          )
          .join("\n");

  const newAutomationGapLines =
    classification.newAutomationGaps.length === 0
      ? "- None."
      : classification.newAutomationGaps
          .map(
            (result) =>
              `- \`${result.scenarioId}\` / \`${result.viewport}\`: ${result.message || "no message"}`
          )
          .join("\n");

  const manualExceptionLines =
    classification.manualExceptions.length === 0
      ? "- None."
      : classification.manualExceptions
          .map(
            (result) =>
              `- \`${result.scenarioId}\` / \`${result.viewport}\`: ${result.message || "no message"}`
          )
          .join("\n");

  const issueLines =
    (filedIssues || []).length === 0
      ? "- None."
      : filedIssues
          .map((issue) => {
            if (issue?.dryRun) {
              return `- DRY RUN: ${issue.title}`;
            }
            if (issue?.id) {
              return `- ${issue.id}: ${issue.title || issue.name || "created"}`;
            }
            if (issue?.error) {
              return `- Failed to create issue for ${issue.title}: ${issue.error}`;
            }
            return `- ${JSON.stringify(issue)}`;
          })
          .join("\n");

  const designReviewLines = !designReview
    ? "- Not run."
    : [
        `- State: \`${designReview.state}\``,
        `- Run id: \`${designReview.runId}\``,
        `- Artifacts: \`${designReview.runDir}\``,
        ...(designReview.passes || []).map(
          (entry) =>
            `- ${entry.pass}: ${entry.status} (${entry.issueCount} issue(s))${entry.blockedReason ? ` - ${entry.blockedReason}` : ""}`
        )
      ].join("\n");

  return `# Nightly UI QA Report - ${reportDate}

## Summary

- Overall state: \`${overallState}\`
- Failure classification: \`${classification.classification}\`
- Novelty: \`${classification.novelty || "n/a"}\`
- Run id: \`${summary.runId}\`
- Scenario bundle state: \`${summary.state}\`
- Design review state: \`${designReview?.state || "not-run"}\`
- Branch: \`${branch}\`
- Artifacts: \`${summary.runDir}\`
- Previous nightly: \`${previousRunDir || "none"}\`
- Report path: \`${reportPath}\`

## Scenario counts

- pass: ${counts.pass}
- product-regression: ${counts["product-regression"]}
- automation-gap: ${counts["automation-gap"]}
- manual-exception: ${counts["manual-exception"]}

## Route coverage

${routeCoverageLines || "- None."}

## Coverage contract gaps

${coverageGapLines}

## Inspected spectate addresses

${inspectedAddressLines}

## New critical/high product regressions

${productRegressionLines}

## New automation gaps

${newAutomationGapLines}

## Persistent automation gaps

${automationGapLines}

## Manual exceptions

${manualExceptionLines}

## Design Review

${designReviewLines}

## Filed bd issues

${issueLines}
`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const repoRoot = process.cwd();
  const branch = await gitBranch(repoRoot);
  const allowNonMain = Boolean(args["allow-non-main"]);
  const dryRun = Boolean(args["dry-run"]);

  if (branch !== "main" && !allowNonMain && !dryRun) {
    throw new Error(
      `Nightly UI QA must run from main. Current branch is ${branch}. Pass --allow-non-main to override.`
    );
  }

  const service = await BrowserInspectionService.create();
  const nightlyScenarios = await buildNightlyScenarios({
    scenarioDir: service.config.scenarioDir
  });
  const expectedRouteCoverage = summarizeNightlyCoverage(nightlyScenarios);

  if (dryRun) {
    const scenarioBundle = await runScenarioBundle(service, {
      scenarios: nightlyScenarios,
      tags: NIGHTLY_TAGS,
      dryRun: true,
      includeCompare: true
    });
    const designReview = await runDesignReview(service, {
      dryRun: true
    });
    process.stdout.write(
      `${JSON.stringify(
        {
          dryRun: true,
          branch,
          allowNonMain,
          tags: NIGHTLY_TAGS,
          scenarioBundle,
          designReview
        },
        null,
        2
      )}\n`
    );
    return;
  }

  const summary = await runScenarioBundle(service, {
    scenarios: nightlyScenarios,
    tags: NIGHTLY_TAGS,
    runKind: "nightly-ui-qa",
    includeCompare: true,
    headless: !Boolean(args.headed),
    manageLocalApp: args["manage-local-app"] ? true : undefined,
    attachPort: args["attach-port"] || null,
    attachHost: args["attach-host"] || null,
    targetId: args["target-id"] || null,
    localUrl: args["local-url"] || null
  });
  const designReview = await runDesignReview(service, {
    headless: !Boolean(args.headed),
    manageLocalApp: args["manage-local-app"] ? true : undefined,
    attachPort: args["attach-port"] || null,
    attachHost: args["attach-host"] || null,
    targetId: args["target-id"] || null,
    localUrl: args["local-url"] || null
  });

  const previousRunDir = await findPreviousNightlyRun(service.config.artifactRoot, summary.runDir);
  const previousSummary = previousRunDir
    ? await readJson(path.join(previousRunDir, "summary.json"), null)
    : null;
  const { classification, overallState } = buildNightlyOutcome({
    summary,
    previousSummary,
    expectedRouteCoverage,
    designReviewState: designReview.state
  });
  const reportDate = reportDateString();
  const reportPath = path.resolve(repoRoot, `docs/qa/nightly-ui-report-${reportDate}.md`);

  const runMeta = {
    generatedAt: safeNowIso(),
    branch,
    allowNonMain,
    tags: NIGHTLY_TAGS,
    expectedSpectateAddresses: NIGHTLY_SPECTATE_ADDRESSES,
    expectedRouteCoverage,
    inspectedAddresses: classification.inspectedAddresses,
    previousRunDir,
    currentRunId: summary.runId,
    currentRunDir: summary.runDir,
    designReviewRunId: designReview.runId,
    designReviewRunDir: designReview.runDir,
    overallState
  };

  const resultRows = [
    ["scenarioId", "viewport", "route", "severity", "state", "message", "snapshotPath", "screenshotPath", "compareRunId"],
    ...(summary.results || []).map((result) => [
      result.scenarioId,
      result.viewport,
      result.route,
      result.severity,
      result.state,
      result.message || "",
      result.snapshotPath || "",
      result.screenshotPath || "",
      result.compareRunId || ""
    ])
  ];

  await writeJsonFile(path.join(summary.runDir, "run-meta.json"), runMeta);
  await writeJsonFile(path.join(summary.runDir, "failure-classification.json"), classification);
  await writeJsonFile(path.join(summary.runDir, "design-review-summary.json"), designReview);
  await fs.writeFile(path.join(summary.runDir, "attempt-summary.tsv"), `${toTsv(resultRows)}\n`);

  const filedIssues = await fileNightlyIssues({
    classification,
    repoRoot,
    runDir: summary.runDir,
    runId: summary.runId,
    reportPath,
    previousRunDir,
    dryRun: false
  });

  await fs.writeFile(
    reportPath,
    renderReport({
      overallState,
      summary,
      designReview,
      classification,
      branch,
      reportDate,
      reportPath,
      previousRunDir,
      filedIssues
    })
  );

  process.stdout.write(
    `${JSON.stringify(
      {
        state: overallState,
        runId: summary.runId,
        runDir: summary.runDir,
        scenarioState: summary.state,
        designReview,
        branch,
        previousRunDir,
        reportPath,
        classification,
        filedIssues
      },
      null,
      2
    )}\n`
  );

  if (overallState !== "pass") {
    process.exitCode =
      overallState === "manual-exception" || overallState === "design-review-blocked" ? 3 : 2;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`${error?.stack || error?.message || error}\n`);
    process.exitCode = 1;
  });
}
