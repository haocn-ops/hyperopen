import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

const scannedTargets = [
  "src/hyperopen/views/portfolio/optimize",
  "src/hyperopen/views/portfolio_view.cljs",
  "test/hyperopen/views/portfolio/optimize",
  "src/styles/surfaces/optimizer",
  "src/hyperopen/portfolio/optimizer/BOUNDARY.md",
  "test/test_runner_generated.cljs",
];

const pathPatterns = [
  { pattern: /setup_v4/, label: "setup_v4 path" },
];

const contentPatterns = [
  { pattern: /setup-v4/, label: "setup-v4 namespace or prose" },
  { pattern: /portfolio-optimizer-v4/, label: "portfolio-optimizer-v4 class" },
  { pattern: /v1[- ]results/i, label: "v1 results prose or test name" },
  { pattern: /\bv4\b/i, label: "v4 generation prose or test name" },
  { pattern: /v4 route surfaces/i, label: "v4 route-surface prose" },
];

function filesUnder(target) {
  const full = path.join(repoRoot, target);
  const stat = fs.statSync(full);

  if (stat.isFile()) {
    return [full];
  }

  return fs.readdirSync(full, { withFileTypes: true }).flatMap((entry) => {
    const child = path.join(full, entry.name);
    if (entry.isDirectory()) {
      return filesUnder(path.relative(repoRoot, child));
    }
    return [child];
  });
}

function findLegacyOptimizerGenerationLabels() {
  const files = scannedTargets.flatMap(filesUnder);

  return files.flatMap((file) => {
    const rel = path.relative(repoRoot, file);
    const pathViolations = pathPatterns
      .filter(({ pattern }) => pattern.test(rel))
      .map(({ label }) => `${rel}: path uses ${label}`);

    const text = fs.readFileSync(file, "utf8");
    const contentViolations = text.split("\n").flatMap((line, idx) => {
      return contentPatterns
        .filter(({ pattern }) => pattern.test(line))
        .map(({ label }) => `${rel}:${idx + 1}: ${label}`);
    });

    return [...pathViolations, ...contentViolations];
  }).sort();
}

test("active optimizer surfaces use canonical names instead of v1/v4 generation labels", () => {
  assert.deepEqual(findLegacyOptimizerGenerationLabels(), []);
});
