import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import test from "node:test";

import { validateWorkflowSecurity } from "./ci_workflow_contract.mjs";

const PIN = "11d5960a326750d5838078e36cf38b85af677262";
const BABASHKA_SHA = "1fff1d97fa08b6b43cb9b4f8726a1c72c3115a15611ab1248d3d57c3c70ed908";

function safeWorkflow() {
  return `name: Safe
permissions:
  contents: read
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@${PIN} # v4
      - run: |
          npm ci --ignore-scripts
          npm run security:npm-contract
      - run: |
          curl -fsSL -o bb.tar.gz https://example.invalid/bb.tar.gz
          echo "${BABASHKA_SHA}  bb.tar.gz" | sha256sum -c -
          tar -xzf bb.tar.gz bb
`;
}

test("immutable read-only workflow with disabled lifecycle scripts passes", () => {
  const summary = validateWorkflowSecurity({ filePath: "safe.yml", source: safeWorkflow() });
  assert.deepEqual(summary.actionPins, [`actions/checkout@${PIN}`]);
  assert.equal(summary.npmInstallCount, 1);
});

test("workflow contract rejects mutable actions, write authority, lifecycle scripts, unchecked downloads, and pushes", () => {
  for (const [mutate, pattern] of [
    [(source) => source.replace(`@${PIN}`, "@v4"), /immutable 40-character commit/i],
    [(source) => source.replace("contents: read", "contents: write"), /write permission/i],
    [(source) => source.replace("npm ci --ignore-scripts", "npm ci"), /ignore-scripts/i],
    [(source) => source.replace("npm run security:npm-contract", "true"), /npm contract/i],
    [(source) => source.replace(/^\s*echo .*sha256sum -c -\n/m, ""), /checksum/i],
    [(source) => source.replace(/(\s+echo .*sha256sum -c -\n)(\s+tar -xzf.*\n)/, "$2$1"), /checksum/i],
    [(source) => `${source}\n      - run: git push origin main\n`, /git push/i],
  ]) {
    assert.throws(
      () => validateWorkflowSecurity({ filePath: "unsafe.yml", source: mutate(safeWorkflow()) }),
      pattern,
    );
  }
});

test("checked-in workflows satisfy the CI supply-chain contract", async () => {
  for (const fileName of ["tests.yml", "playwright.yml", "security.yml"]) {
    const filePath = path.join(".github", "workflows", fileName);
    const source = await fs.readFile(filePath, "utf8");
    validateWorkflowSecurity({ filePath, source });
  }
});
