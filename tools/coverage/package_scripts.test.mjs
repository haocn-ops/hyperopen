import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const pkg = JSON.parse(fs.readFileSync(new URL("../../package.json", import.meta.url), "utf8"));

test("coverage script uses the coverage-safe optimizer worker solver", () => {
  const script = pkg.scripts.coverage ?? "";

  assert.match(script, /HYPEROPEN_OPTIMIZER_WORKER_SOLVER=quadprog/);
  assert.match(script, /NODE_V8_COVERAGE=\.coverage/);
  assert.match(script, /--merge-async/);
});
