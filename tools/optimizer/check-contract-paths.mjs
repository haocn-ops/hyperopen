import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

const roots = [
  "src/hyperopen/portfolio/optimizer",
  "src/hyperopen/runtime/effect_adapters",
];

const allowedFiles = new Set([
  "src/hyperopen/portfolio/optimizer/contracts.cljs",
  "src/hyperopen/portfolio/optimizer/contracts/paths.cljs",
]);

const patterns = [
  /\[:portfolio\s+:optimizer\b/,
  /\[:portfolio-ui\s+:optimizer\b/,
];

const migratedPlaywrightOptimizerSpecs = [
  "tools/playwright/test/optimizer-black-litterman-views.spec.mjs",
];

const playwrightOptimizerSeedPatterns = [
  {
    pattern: /PersistentArrayMap/,
    reason: "direct CLJS map construction",
  },
  {
    pattern: /PersistentVector/,
    reason: "direct CLJS vector construction",
  },
  {
    pattern: /\bassoc_in\b/,
    reason: "direct app store assoc_in mutation",
  },
  {
    pattern: /kw\("portfolio"\),\s*kw\("optimizer"\)/,
    reason: "hardcoded optimizer browser path",
  },
  {
    pattern: /path\("portfolio",\s*"optimizer"/,
    reason: "hardcoded optimizer browser path",
  },
];

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return walk(full);
    return full.endsWith(".cljs") ? [full] : [];
  });
}

export function findViolations() {
  const files = roots.flatMap((root) => walk(path.join(repoRoot, root)));

  return files.flatMap((file) => {
    const rel = path.relative(repoRoot, file);
    if (allowedFiles.has(rel)) return [];

    const text = fs.readFileSync(file, "utf8");
    return text.split("\n").flatMap((line, idx) => {
      if (patterns.some((pattern) => pattern.test(line))) {
        return [`${rel}:${idx + 1}: hardcoded optimizer state path`];
      }
      return [];
    });
  });
}

export function findPlaywrightOptimizerSeedViolations() {
  return migratedPlaywrightOptimizerSpecs.flatMap((rel) => {
    const text = fs.readFileSync(path.join(repoRoot, rel), "utf8");
    return text.split("\n").flatMap((line, idx) => {
      const violation = playwrightOptimizerSeedPatterns.find(({ pattern }) =>
        pattern.test(line)
      );
      if (!violation) {
        return [];
      }
      return [`${rel}:${idx + 1}: ${violation.reason}; use tools/playwright/support/optimizer_state.mjs`];
    });
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const violations = [
    ...findViolations(),
    ...findPlaywrightOptimizerSeedViolations(),
  ];
  if (violations.length) {
    console.error(violations.join("\n"));
    process.exit(1);
  }

  console.log("Optimizer contract path check passed.");
}
