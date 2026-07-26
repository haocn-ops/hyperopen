import test from "node:test";
import assert from "node:assert/strict";

import {
  findPlaywrightOptimizerSeedViolations,
  findViolations
} from "./check-contract-paths.mjs";

test("optimizer production code keeps root state paths in contracts namespace", () => {
  assert.deepEqual(findViolations(), []);
});

test("migrated Black-Litterman Playwright spec uses shared optimizer seed helper", () => {
  assert.deepEqual(findPlaywrightOptimizerSeedViolations(), []);
});
