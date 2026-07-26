import test from "node:test";
import assert from "node:assert/strict";
import { classifyErrorMessage, classifyNightlyFailure } from "../src/failure_classification.mjs";

test("classifyErrorMessage detects EPERM loopback bind failures", () => {
  const classification = classifyErrorMessage("Error: listen EPERM: operation not permitted 127.0.0.1");
  assert.ok(classification);
  assert.equal(classification.classification, "automation-gap");
  assert.equal(classification.reason, "loopback_bind_blocked");
});

test("classifyNightlyFailure marks unknown capture failures as product-regression", () => {
  const result = classifyNightlyFailure({
    attempts: [
      {
        key: "trade-large-active",
        status: "failed",
        errorMessage: "Unexpected null pointer in UI verification"
      }
    ]
  });

  assert.equal(result.classification, "product-regression");
  assert.equal(result.reason, "capture_failures_without_infra_signature");
});

test("classifyNightlyFailure returns pass when no failures are present", () => {
  const result = classifyNightlyFailure({
    attempts: [
      {
        key: "trade-large-active",
        status: "ok"
      }
    ]
  });

  assert.equal(result.classification, "pass");
});
