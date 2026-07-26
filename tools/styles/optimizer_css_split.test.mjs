import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
  "..",
);

const optimizerEntryPath = path.join(
  repoRoot,
  "src",
  "styles",
  "surfaces",
  "optimizer.css",
);

const optimizerPartialDir = path.join(
  repoRoot,
  "src",
  "styles",
  "surfaces",
  "optimizer",
);

const expectedPartials = [
  "base.css",
  "setup.css",
  "results.css",
  "universe.css",
  "frontier.css",
  "execution.css",
];

const expectedEntryImports = expectedPartials.map(
  (partial) => `./optimizer/${partial}`,
);

function significantLines(text) {
  return text
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter(Boolean);
}

test("optimizer.css remains a focused import entrypoint", () => {
  const optimizerCss = fs.readFileSync(optimizerEntryPath, "utf8");
  const imports = significantLines(optimizerCss).map((line) => {
    const match = /^@import\s+"([^"]+)";$/u.exec(line);
    assert.ok(match, `expected optimizer.css to contain only import statements, received: ${line}`);
    return match[1];
  });

  assert.deepEqual(imports, expectedEntryImports);
});

test("optimizer CSS partials are split by surface responsibility", () => {
  const actualPartials = fs
    .readdirSync(optimizerPartialDir)
    .filter((fileName) => fileName.endsWith(".css"))
    .sort();

  assert.deepEqual(actualPartials, [...expectedPartials].sort());
});

test("optimizer CSS partials style semantic classes instead of data-role test hooks", () => {
  for (const partial of expectedPartials) {
    const partialPath = path.join(optimizerPartialDir, partial);
    const css = fs.readFileSync(partialPath, "utf8");

    assert.doesNotMatch(
      css,
      /\[data-role/u,
      `${partial} should not style through data-role selectors`,
    );

    assert.doesNotMatch(
      css,
      /\[class\*=/u,
      `${partial} should not use substring class selectors for optimizer chrome`,
    );
  }
});

test("optimizer target exposure change cells override table body text color", () => {
  const resultsCss = fs.readFileSync(
    path.join(optimizerPartialDir, "results.css"),
    "utf8",
  );

  assert.match(
    resultsCss,
    /\.optimizer-target-exposure-change-cell\.text-trading-green/u,
  );
  assert.match(
    resultsCss,
    /\.optimizer-target-exposure-change-cell\.text-trading-red/u,
  );
});

test("optimizer constraint toggles use the shared switch treatment", () => {
  const setupCss = fs.readFileSync(
    path.join(optimizerPartialDir, "setup.css"),
    "utf8",
  );
  const utilitiesCss = fs.readFileSync(
    path.join(repoRoot, "src/styles/surfaces/utilities.css"),
    "utf8",
  );

  for (const controlClass of [
    "optimizer-turnover-cap-control",
    "optimizer-long-only-control",
    "optimizer-include-spot-control",
  ]) {
    assert.match(
      setupCss,
      new RegExp(`\\.${controlClass}`, "u"),
      `${controlClass} should be included in the optimizer hx-toggle override`,
    );
    assert.match(
      utilitiesCss,
      new RegExp(`\\.${controlClass}`, "u"),
      `${controlClass} should be protected in the utilities-layer hx-toggle override`,
    );
  }

  assert.match(
    utilitiesCss,
    /\.portfolio-optimizer[\s\S]*\.hx-toggle:focus-visible[\s\S]*outline:\s*none/u,
  );
  assert.match(
    utilitiesCss,
    /\.portfolio-optimizer[\s\S]*\.hx-toggle:focus-visible[\s\S]*box-shadow:\s*0 0 0 1px/u,
  );
  assert.ok(
    utilitiesCss.indexOf(".portfolio-optimizer") > utilitiesCss.indexOf(".hx-toggle {"),
    "optimizer switch treatment should live after the global hx-toggle rule",
  );
  assert.doesNotMatch(
    setupCss,
    /\.hx-toggle/u,
    "setup partial should only place constraint controls; utilities layer owns switch chrome",
  );
});
