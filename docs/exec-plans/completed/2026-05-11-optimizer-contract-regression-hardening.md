---
owner: platform
status: completed
source_of_truth: true
tracked_issue: hyperopen-5783
---

# Optimizer Contract Regression Hardening

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. It is self-contained so an engineer can execute the work without relying on the conversation that produced it.

## Purpose / Big Picture

The optimizer contracts hardening commit made optimizer state paths explicit and tightened contract specs. That reduced ambiguity, but it also introduced regression risk: stricter specs may reject legitimate edge payloads, status allowlists may miss rare statuses, and wide path-constant refactors can hide a wrong constant behind passing happy-path tests. This plan adds targeted guardrails so the new contracts stay strict without becoming brittle.

After this work, a developer can run focused optimizer tests and see proof that real solved payloads, status-producing constructors, and path constants are covered by regression tests. The work is internal and should not change runtime behavior except to make validation failures more intentional.

## Progress

- [x] (2026-05-11T20:32:00Z) Captured a deferred hardening plan for the regression risks identified after `85f1799a refactor: harden optimizer contracts`.
- [x] (2026-05-11T23:02:16Z) Created and claimed `bd` issue `hyperopen-5783`, then moved this plan to `docs/exec-plans/active/`.
- [x] (2026-05-11T23:06:31Z) Added solved result payload fixture coverage and aligned the optimizer solved-result fixture with the engine payload shape.
- [x] (2026-05-11T23:07:20Z) Added status allowlist coverage for the current optimizer draft, scenario, tracking, and result status producers.
- [x] (2026-05-11T23:08:28Z) Added a production path-literal static guard and wired it into the focused optimizer contract scripts and `npm run check`.
- [x] (2026-05-11T23:09:53Z) Added v1 persisted contract fixtures and migration validation coverage for draft, scenario, and tracking records.
- [x] (2026-05-11T23:12:07Z) Ran required gates successfully and prepared the plan for completion.

## Surprises & Discoveries

- Observation: The immediate regression risks are not from changed business logic; they are from stricter validation and broad replacement of state-path literals.
  Evidence: The implementation commit changed contract specs and path references across optimizer actions/runtime adapters while preserving state layout and public function names.

- Observation: Current v2 migration behavior is intentionally a loud failure.
  Evidence: `test/hyperopen/portfolio/optimizer/contracts_test.cljs` asserts schema-version constants remain `1` and version `2` migration inputs throw until a real persisted format change exists.

- Observation: The shared optimizer solved-result fixture was missing `:expected-returns-by-instrument`, even though production engine payload assembly emits that field.
  Evidence: The new `::result-payload` fixture validation failed until `test/hyperopen/portfolio/optimizer/fixtures.cljs` added the aligned expected-return map.

- Observation: Current production optimizer source already respects the new path constants.
  Evidence: `npm run lint:optimizer-contract-paths` and `npm run test:optimizer-contract-paths` both passed after adding the scanner.

## Decision Log

- Decision: Treat this as a defensive test and guardrail pass, not a behavior change.
  Rationale: The prior commit already centralized ownership. The next safest improvement is to test real data examples and guard against drift, not to broaden contracts speculatively.
  Date/Author: 2026-05-11 / Codex

- Decision: Keep path literal assertions in tests, but disallow production optimizer root path literals outside `contracts.cljs`.
  Rationale: Test literals are useful for proving constant values. Production literals reintroduce the maintenance problem this feature is solving.
  Date/Author: 2026-05-11 / Codex

- Decision: Execute the plan on branch `codex/optimizer-contract-regression-hardening`.
  Rationale: The execution workflow says not to start new implementation on `main`. A feature branch isolates this follow-up from the already-committed optimizer contracts work.
  Date/Author: 2026-05-11 / Codex

## Outcomes & Retrospective

Implemented. The optimizer contracts layer now has targeted regression guards for solved result payload fixtures, lifecycle status allowlists, production optimizer path literals, and current v1 persisted migration fixtures. Runtime behavior remains unchanged; the only shape alignment was adding the engine-emitted `:expected-returns-by-instrument` field to the shared optimizer solved-result test fixture.

Validation completed:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Context and Orientation

The contract owner is `src/hyperopen/portfolio/optimizer/contracts.cljs`. It defines optimizer schema versions, state path constants, migration functions, canonical request signatures, wire codecs, and `clojure.spec.alpha` specs such as `::draft`, `::scenario-record`, `::tracking-record`, and `::result-payload`.

The main regression risks are:

1. `::result-payload` may be too strict for valid solved payloads.
2. Status allowlists may miss legitimate statuses produced by existing constructors.
3. Path constants may point to a wrong vector or future code may reintroduce production hardcoded paths.
4. Persisted migration tests are intentionally v1-only until a real v2 format exists.

Use TDD. Each task below starts with a failing test or static guard, then implements the smallest change that makes it pass.

## Plan of Work

First, add result-payload coverage that uses a real optimizer engine result rather than only a hand-written sample map. The test should call existing engine or fixture helpers that produce the same solved payload shape downstream views consume. If a legitimate solved payload lacks an optional field, update `contracts/solved-result-payload?` to permit that field to be absent only when downstream code already handles it.

Second, add status coverage from the constructors that create optimizer states. The test should enumerate statuses produced by defaults, scenario records, tracking snapshots, progress states, history prefetch states, and execution ledger states, then assert the relevant status sets in `contracts.cljs` include them. This catches missing status additions close to the source.

Third, add a repo-local static guard for production optimizer path literals. The guard should allow literals inside `contracts.cljs` and test files, but fail if production optimizer or optimizer runtime adapter code introduces `[:portfolio :optimizer ...]` or `[:portfolio-ui :optimizer ...]` directly. Wire it into `npm run check` only if the command is fast and stable; otherwise add it as a focused npm script and document it in the ExecPlan.

Fourth, add a migration fixture harness for current v1 persisted formats. Keep schema-version constants at `1`. Store representative v1 draft, scenario record, and tracking record fixtures in a test helper namespace, run them through migration, and assert they validate. When a real v2 persisted format change happens later, this harness becomes the place to add v2 input and expected migrated output.

## Concrete Steps

Work from `/Users/barry/projects/hyperopen`.

### Task 1: Result Payload Fixture Coverage

Files:

- Modify: `test/hyperopen/portfolio/optimizer/contracts_test.cljs`
- Possibly modify: `src/hyperopen/portfolio/optimizer/contracts.cljs`

Step 1: Add a failing test that validates a real solved payload.

In `test/hyperopen/portfolio/optimizer/contracts_test.cljs`, add a require for an existing fixture namespace if not already present:

    [hyperopen.portfolio.optimizer.fixtures :as optimizer-fixtures]

Add this test near `result-payload-contract-validates-solved-payloads-test`:

    (deftest result-payload-contract-accepts-real-solved-fixture-test
      (let [payload (:result (optimizer-fixtures/solved-scenario-state))]
        (is (= :solved (:status payload)))
        (is (s/valid? ::contracts/result-payload payload)
            (s/explain-str ::contracts/result-payload payload))))

If `optimizer-fixtures/solved-scenario-state` is not the exact helper name, inspect `test/hyperopen/portfolio/optimizer/fixtures_test.cljs` and `src/hyperopen/portfolio/optimizer/fixtures.cljs` and use the existing helper that returns a state containing `[:portfolio :optimizer :last-successful-run :result]`.

Step 2: Run the focused test command:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected before implementation: if the current spec is too strict, the new test fails with `::contracts/result-payload` explain output. If it passes, no production change is needed for this task.

Step 3: If the test fails because a field is legitimately optional, update only `solved-result-payload?` in `src/hyperopen/portfolio/optimizer/contracts.cljs`. The change should follow this pattern:

    (defn- optional-instrument-map?
      [instrument-ids value]
      (or (nil? value)
          (valid-instrument-map? instrument-ids value)))

Then use `optional-instrument-map?` only for fields proven optional by the real fixture and downstream code. Keep `:instrument-ids`, `:target-weights`, `:current-weights`, `:target-weights-by-instrument`, and `:current-weights-by-instrument` required for `:solved`.

Step 4: Rerun the focused test command. Expected: 0 failures, 0 errors.

Step 5: Commit this task:

    git add src/hyperopen/portfolio/optimizer/contracts.cljs test/hyperopen/portfolio/optimizer/contracts_test.cljs
    git commit -m "test: cover real optimizer result payload contract"

### Task 2: Status Allowlist Coverage

Files:

- Modify: `test/hyperopen/portfolio/optimizer/contracts_test.cljs`
- Possibly modify: `src/hyperopen/portfolio/optimizer/contracts.cljs`

Step 1: Add tests for every current status producer.

Add this helper in `contracts_test.cljs`:

    (defn- allowed?
      [allowed status]
      (contains? allowed status))

Add this test:

    (deftest optimizer-contract-status-sets-cover-current-producers-test
      (testing "draft statuses"
        (doseq [status [:draft :saved :archived :tracking]]
          (is (allowed? contracts/draft-statuses status))))
      (testing "scenario record statuses"
        (doseq [status [:saved :archived :executed :partially-executed :tracking :failed]]
          (is (allowed? contracts/scenario-record-statuses status))))
      (testing "tracking snapshot statuses"
        (doseq [status [:tracked :not-trackable]]
          (is (allowed? contracts/tracking-snapshot-statuses status))))
      (testing "result payload statuses"
        (doseq [status [:solved :infeasible :error :failed]]
          (is (allowed? contracts/result-payload-statuses status)))))

Step 2: Run:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected: pass. If it fails, add only statuses that are produced by current constructors or fixtures. Do not add speculative statuses.

Step 3: Commit:

    git add src/hyperopen/portfolio/optimizer/contracts.cljs test/hyperopen/portfolio/optimizer/contracts_test.cljs
    git commit -m "test: cover optimizer contract status sets"

### Task 3: Production Path Literal Static Guard

Files:

- Create: `tools/optimizer/check-contract-paths.mjs`
- Modify: `package.json`
- Create: `tools/optimizer/check-contract-paths.test.mjs`

Step 1: Create a Node script that scans production optimizer files.

Create `tools/optimizer/check-contract-paths.mjs`:

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
    ]);
    const patterns = [
      /\[:portfolio\s+:optimizer\b/,
      /\[:portfolio-ui\s+:optimizer\b/,
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

    if (process.argv[1] === fileURLToPath(import.meta.url)) {
      const violations = findViolations();
      if (violations.length) {
        console.error(violations.join("\n"));
        process.exit(1);
      }
      console.log("Optimizer contract path check passed.");
    }

Step 2: Add tests for the script.

Create `tools/optimizer/check-contract-paths.test.mjs`:

    import test from "node:test";
    import assert from "node:assert/strict";
    import { findViolations } from "./check-contract-paths.mjs";

    test("optimizer production code keeps root state paths in contracts namespace", () => {
      assert.deepEqual(findViolations(), []);
    });

Step 3: Add npm scripts in `package.json`:

    "lint:optimizer-contract-paths": "node tools/optimizer/check-contract-paths.mjs",
    "test:optimizer-contract-paths": "node --test tools/optimizer/check-contract-paths.test.mjs",

Add `npm run lint:optimizer-contract-paths` and `npm run test:optimizer-contract-paths` to the `check` script after `npm run test:optimizer-spike`.

Step 4: Run:

    npm run lint:optimizer-contract-paths
    npm run test:optimizer-contract-paths
    npm run check

Expected: all commands exit 0. The lint command prints `Optimizer contract path check passed.`

Step 5: Commit:

    git add package.json tools/optimizer/check-contract-paths.mjs tools/optimizer/check-contract-paths.test.mjs
    git commit -m "test: guard optimizer contract path ownership"

### Task 4: V1 Migration Fixture Harness

Files:

- Create: `test/hyperopen/portfolio/optimizer/contract_fixtures.cljs`
- Modify: `test/hyperopen/portfolio/optimizer/contracts_test.cljs`

Step 1: Create current v1 fixture namespace.

Create `test/hyperopen/portfolio/optimizer/contract_fixtures.cljs`:

    (ns hyperopen.portfolio.optimizer.contract-fixtures)

    (def v1-draft
      {:schema-version 1
       :status :draft
       :name "Fixture Draft"
       :universe []
       :objective {:kind :minimum-variance}
       :return-model {:kind :historical-mean}
       :risk-model {:kind :diagonal-shrink}
       :constraints {:long-only? false}
       :execution-assumptions {:default-order-type :market}
       :metadata {:dirty? false}})

    (def v1-scenario-record
      {:schema-version 1
       :id "scn_fixture"
       :name "Fixture Scenario"
       :status :saved
       :config v1-draft
       :saved-run nil
       :updated-at-ms 1000})

    (def v1-tracking-record
      {:schema-version 1
       :scenario-id "scn_fixture"
       :updated-at-ms 2000
       :snapshots [{:scenario-id "scn_fixture"
                    :as-of-ms 2000
                    :status :not-trackable
                    :warnings [{:code :missing-solved-run}]}]})

Step 2: Add require in `contracts_test.cljs`:

    [hyperopen.portfolio.optimizer.contract-fixtures :as contract-fixtures]

Step 3: Add fixture migration test:

    (deftest v1-persisted-contract-fixtures-migrate-and-validate-test
      (let [draft (contracts/migrate-draft contract-fixtures/v1-draft)
            scenario (contracts/migrate-scenario-record contract-fixtures/v1-scenario-record)
            tracking (contracts/migrate-tracking-record contract-fixtures/v1-tracking-record)]
        (is (s/valid? ::contracts/draft draft)
            (s/explain-str ::contracts/draft draft))
        (is (s/valid? ::contracts/scenario-record scenario)
            (s/explain-str ::contracts/scenario-record scenario))
        (is (s/valid? ::contracts/tracking-record tracking)
            (s/explain-str ::contracts/tracking-record tracking))))

Step 4: Run:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected: 0 failures, 0 errors.

Step 5: Commit:

    git add test/hyperopen/portfolio/optimizer/contract_fixtures.cljs test/hyperopen/portfolio/optimizer/contracts_test.cljs
    git commit -m "test: add optimizer contract migration fixtures"

## Validation and Acceptance

Acceptance requires:

1. Real solved optimizer fixture payloads validate against `::contracts/result-payload`.
2. Status allowlists are covered by tests that name the current statuses from known producers.
3. Production optimizer source and runtime effect adapters fail a static guard if they reintroduce optimizer root state path literals outside `contracts.cljs`.
4. Current v1 persisted draft, scenario, and tracking fixtures migrate and validate.
5. Unsupported v2 inputs still fail until a real v2 persisted format exists.
6. Required gates pass:

       npm run check
       npm test
       npm run test:websocket

No browser QA is required because this hardening plan changes contract tests, static linting, and validation helpers only.

## Idempotence and Recovery

Each task is test-first and can be retried safely. If the static path guard flags existing production code, replace the hardcoded path with a constant from `contracts.cljs`; if no suitable constant exists, add one and test its literal value in `contracts_test.cljs`. If a real solved fixture fails result validation, inspect downstream consumers before relaxing the spec. Do not change schema version constants to `2` as part of this plan.

## Artifacts and Notes

This plan follows from commit `85f1799a refactor: harden optimizer contracts`, which passed:

    npm run check
    npm test
    npm run test:websocket

The highest-priority task is Task 1 because an over-narrow `::result-payload` spec is the most likely functional regression.

## Interfaces and Dependencies

Use existing tooling only:

- ClojureScript tests through `npm test`.
- Node test runner for the optional static path guard.
- `clojure.spec.alpha` for contract validation.
- Existing optimizer fixtures and constructors where available.

Do not add a new schema library. Do not broaden result or status contracts without a concrete fixture or constructor proving the value is valid.

## Plan Revision Notes

- 2026-05-11 / Codex: Initial deferred plan created to harden against the regression risks identified after optimizer contracts hardening.
- 2026-05-11 / Codex: Activated plan under `hyperopen-5783` on branch `codex/optimizer-contract-regression-hardening`.
