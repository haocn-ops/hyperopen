import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

// Run every validation gate WITHOUT short-circuiting, then print a full
// PASS/FAIL matrix with a copy-pasteable rerun command for each failure.
//
// `npm run check` is a single ~29-link `&&` chain that stops at the first
// failing gate, so an agent fixes one gate, re-pays every earlier gate, and
// only then discovers the next failure -- serial discovery that wastes turns
// and loses the thread of which gate to iterate on. This aggregator derives the
// gate list FROM the `check` script (so it stays in sync automatically), runs
// each gate to completion regardless of earlier failures, and surfaces all
// failures at once. Gates must run serially (later segments depend on artifacts
// from earlier ones, e.g. `compile test` needs `test:runner:generate`).

const __dirname = dirname(fileURLToPath(import.meta.url));
const packageJsonPath = join(__dirname, "..", "package.json");

function checkSegments() {
  try {
    const pkg = JSON.parse(readFileSync(packageJsonPath, "utf8"));
    const checkScript = pkg.scripts && pkg.scripts.check;
    if (typeof checkScript === "string") {
      return checkScript
        .split("&&")
        .map((segment) => segment.trim())
        .filter((segment) => segment.length > 0);
    }
  } catch {
    // fall through to a safe default
  }
  return ["npm run check"];
}

// Worktree bootstrap runs first so a missing node_modules surfaces at the top
// (and is auto-linked) instead of failing every compile gate with opaque errors.
const gates = [
  "npm run setup:worktree",
  ...checkSegments(),
  "npm test",
  "npm run test:websocket"
].map((command) => ({ label: command, command }));

function makeMetrics() {
  return {
    tests: 0,
    assertions: 0,
    nodeTests: 0
  };
}

function formatDuration(milliseconds) {
  const totalSeconds = Math.round(milliseconds / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  if (minutes === 0) {
    return `${seconds}s`;
  }

  return `${minutes}m ${seconds}s`;
}

function collectMetrics(metrics, text) {
  const cljsMatches = text.matchAll(/Ran\s+(\d+)\s+tests\s+containing\s+(\d+)\s+assertions\./g);
  for (const match of cljsMatches) {
    metrics.tests += Number(match[1]);
    metrics.assertions += Number(match[2]);
  }

  const nodeMatches = text.matchAll(/ℹ tests\s+(\d+)/g);
  for (const match of nodeMatches) {
    const count = Number(match[1]);
    metrics.tests += count;
    metrics.nodeTests += count;
  }
}

function runGate({ label, command }) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    process.stdout.write(`\n──── ${label} ────\n`);
    const child = spawn(command, {
      stdio: ["inherit", "pipe", "pipe"],
      shell: true
    });
    let output = "";

    const handleChunk = (chunk, writer) => {
      const text = chunk.toString();
      writer.write(text);
      output += text;
    };

    child.stdout.on("data", (chunk) => {
      handleChunk(chunk, process.stdout);
    });

    child.stderr.on("data", (chunk) => {
      handleChunk(chunk, process.stderr);
    });

    child.on("error", (error) => {
      const metrics = makeMetrics();
      collectMetrics(metrics, output);
      resolve({
        label,
        command,
        ok: false,
        code: null,
        signal: null,
        error,
        metrics,
        durationMs: Date.now() - startedAt
      });
    });

    child.on("exit", (code, signal) => {
      const metrics = makeMetrics();
      collectMetrics(metrics, output);
      resolve({
        label,
        command,
        ok: code === 0,
        code,
        signal,
        error: null,
        metrics,
        durationMs: Date.now() - startedAt
      });
    });
  });
}

function renderStatus(result) {
  if (result.ok) return "PASS";
  if (result.signal) return `FAIL (signal ${result.signal})`;
  if (result.error) return `FAIL (${result.error.message})`;
  return `FAIL (exit ${result.code})`;
}

const results = [];

// Serial, no short-circuit: every gate runs even after an earlier one fails.
for (const gate of gates) {
  results.push(await runGate(gate));
}

const labelWidth = Math.min(
  48,
  gates.reduce((width, gate) => Math.max(width, gate.label.length), 0)
);

console.log("");
console.log(`Gate matrix (${results.length} gates):`);

results.forEach((result, index) => {
  const number = String(index + 1).padStart(2, "0");
  const status = renderStatus(result);
  console.log(
    `  [${number}] ${status.padEnd(20)} ${result.label.padEnd(labelWidth)}  ${formatDuration(result.durationMs)}`
  );
});

const failed = results.filter((result) => !result.ok);

if (failed.length > 0) {
  console.log("");
  console.log(`Failed gates (${failed.length}) — rerun individually:`);
  for (const result of failed) {
    console.log(`  ✗ ${result.label}`);
    console.log(`      ↳ ${result.command}`);
  }
}

const totals = results.reduce((acc, result) => {
  acc.tests += result.metrics.tests;
  acc.assertions += result.metrics.assertions;
  acc.nodeTests += result.metrics.nodeTests;
  return acc;
}, makeMetrics());

console.log("");
console.log("Totals:");
console.log(`  gates passed:            ${results.length - failed.length}/${results.length}`);
console.log(`  tests run:               ${totals.tests}`);
console.log(`  assertions run:          ${totals.assertions}`);
console.log(`  total suite time:        ${formatDuration(results.reduce((sum, result) => sum + result.durationMs, 0))}`);
if (totals.nodeTests > 0) {
  console.log(`  node tests included:     ${totals.nodeTests}`);
}

const allPassed = failed.length === 0;
console.log(`Overall: ${allPassed ? "PASS" : "FAIL"}`);

process.exit(allPassed ? 0 : 1);
