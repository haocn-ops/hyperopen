import assert from "node:assert/strict";
import test from "node:test";
import zlib from "node:zlib";

import {
  checkBudget,
  measureGzipBytes,
  renderBudgetFile,
  resolveMainArtifact,
} from "./check_bundle_budget.mjs";

test("resolveMainArtifact returns the hashed release output name", () => {
  const manifest = [
    { "module-id": "main", "output-name": "main.97EDB0167F0DB3C4EB22CF3ED6C1C175.js" },
    { "module-id": "trade_chart", "output-name": "trade_chart.ABC123ABC123ABC1.js" },
  ];

  assert.equal(
    resolveMainArtifact(manifest),
    "main.97EDB0167F0DB3C4EB22CF3ED6C1C175.js"
  );
});

test("resolveMainArtifact rejects unhashed dev artifacts", () => {
  const manifest = [{ "module-id": "main", "output-name": "main.js" }];

  assert.throws(() => resolveMainArtifact(manifest), /not a hashed release artifact/);
});

test("resolveMainArtifact rejects manifests without a main module", () => {
  assert.throws(() => resolveMainArtifact([]), /no entry with module-id "main"/);
  assert.throws(() => resolveMainArtifact({}), /must be an array/);
});

test("measureGzipBytes matches level-9 gzip output", () => {
  const buffer = Buffer.from("hyperopen".repeat(500));

  assert.equal(
    measureGzipBytes(buffer),
    zlib.gzipSync(buffer, { level: 9 }).length
  );
});

test("checkBudget passes at or under budget and fails over budget", () => {
  const under = checkBudget({ moduleId: "main", actualGzipBytes: 90, budgetGzipBytes: 100 });
  assert.equal(under.ok, true);
  assert.match(under.message, /within budget/);

  const exact = checkBudget({ moduleId: "main", actualGzipBytes: 100, budgetGzipBytes: 100 });
  assert.equal(exact.ok, true);

  const over = checkBudget({ moduleId: "main", actualGzipBytes: 101, budgetGzipBytes: 100 });
  assert.equal(over.ok, false);
  assert.match(over.message, /exceeds budget/);
  assert.match(over.message, /--update/);
});

test("checkBudget fails on a missing or invalid budget", () => {
  const missing = checkBudget({ moduleId: "main", actualGzipBytes: 1, budgetGzipBytes: undefined });
  assert.equal(missing.ok, false);
  assert.match(missing.message, /missing or invalid/);
});

test("renderBudgetFile produces parseable ratchet metadata", () => {
  const rendered = renderBudgetFile({
    gzipBytes: 691020,
    recordedAt: "2026-06-11T13:00:00.000Z",
    outputName: "main.97EDB0167F0DB3C4EB22CF3ED6C1C175.js",
  });

  const parsed = JSON.parse(rendered);
  assert.equal(parsed.main.gzipBytes, 691020);
  assert.equal(parsed.main.recordedOutputName, "main.97EDB0167F0DB3C4EB22CF3ED6C1C175.js");
  assert.match(parsed.comment, /never raise it without review/);
});
