# Nightly UI Spectate Matrix Coverage

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Nightly UI QA is supposed to prove that Hyperopen still renders the governed `/trade`, `/portfolio`, and `/vaults` routes while exercising a fixed spectate-address matrix for `/trade` and `/portfolio`. Right now the wrapper does not meet that contract: it only hits one hardcoded spectate address, it runs `/portfolio` without spectate, and the report gives no direct address coverage summary. After this change, `npm run qa:nightly-ui` should deterministically expand the required smoke scenarios across the full spectate matrix, preserve the existing richer trade scenarios, and record the addresses it actually inspected so future drift is obvious.

## Context References

Public refs:
- Direct user request in this thread to prioritize and fix the nightly spectate coverage gap.

Repo artifacts:
- `/Users/barry/projects/hyperopen/docs/qa/nightly-ui-report-2026-06-09.md`
- `/Users/barry/projects/hyperopen/tools/browser-inspection/src/nightly_ui_qa.mjs`
- `/Users/barry/projects/hyperopen/tools/browser-inspection/scenarios/trade-route-smoke.json`
- `/Users/barry/projects/hyperopen/tools/browser-inspection/scenarios/portfolio-route-smoke.json`
- `/Users/barry/projects/hyperopen/docs/exec-plans/completed/2026-03-09-nightly-ui-qa-tooling-hardening.md`

Local scratch refs (non-authoritative):
- `hyperopen-8e05` in `bd` tracks the coverage gap and is useful for local follow-up, but this plan is the authoritative implementation artifact.

## Progress

- [x] (2026-06-10 02:29Z) Reproduced the current contract drift from the June 9 nightly bundle and confirmed the root cause in source: only `mobile-position-margin-presentation.json` and `mobile-account-surface-positions.json` hardcode a spectate address, while the smoke scenarios for `/trade` and `/portfolio` remain unspectated.
- [x] (2026-06-10 02:29Z) Chose the implementation shape: keep scenario manifests stable, make the nightly wrapper own the required spectate matrix, and test the expansion in code so coverage cannot silently regress via tag or manifest drift.
- [x] (2026-06-10 02:31Z) Added RED tests in `tools/browser-inspection/test/scenario_runner.test.mjs` and the new `tools/browser-inspection/test/nightly_ui_coverage.test.mjs`, then verified they failed for the expected missing helper and missing `options.scenarios` seam.
- [x] (2026-06-10 02:34Z) Implemented `tools/browser-inspection/src/nightly_ui_coverage.mjs`, extended `runScenarioBundle` to accept in-memory manifests, and updated `nightly_ui_qa.mjs` to build the nightly matrix plus report inspected addresses.
- [x] (2026-06-10 02:50Z) Re-ran the focused validations: targeted Node tests, `npm run qa:nightly-ui -- --dry-run`, `npm run test:playwright:smoke`, `npm run test:browser-inspection`, `npm test`, `npm run test:websocket`, and `npm run check` all passed. A real `npm run qa:nightly-ui -- --manage-local-app` run confirmed the three-address matrix in live artifacts while the known `/trade` mobile regression and portfolio `jank-perf` failure remained.
- [x] (2026-06-10 02:51Z) Prepared this plan to move to `completed/` with the acceptance evidence recorded.

## Surprises & Discoveries

- Observation: The current nightly wrapper selects scenarios by tags only, so the coverage contract is implicit and scattered across manifests instead of being enforced in one place.
  Evidence: `/Users/barry/projects/hyperopen/tools/browser-inspection/src/nightly_ui_qa.mjs` passes only `tags: NIGHTLY_TAGS` into `runScenarioBundle`, and `/Users/barry/projects/hyperopen/tools/browser-inspection/src/scenario_runner.mjs` loads manifests purely from `scenarioDir`.

- Observation: The smoke scenarios that should carry most of the route-coverage contract are unspectated today, which means adding more hardcoded manifest copies would spread the contract even further.
  Evidence: `/Users/barry/projects/hyperopen/tools/browser-inspection/scenarios/trade-route-smoke.json` and `portfolio-route-smoke.json` both point at bare `http://localhost:8080/...` URLs with unspectated compare targets.

- Observation: A wrapper-level smoke-matrix expansion is sufficient; the richer trade scenarios can stay pinned to one data-rich fixture address without weakening route coverage, because the smoke scenarios now carry the contract for all three required addresses.
  Evidence: `npm run qa:nightly-ui -- --dry-run` selected twelve new smoke variants: six `/trade` and six `/portfolio`, while preserving `mobile-account-surface-positions` and `mobile-position-margin-presentation` on the original `0x162c...8185` fixture.

- Observation: The live nightly run proves the automation gap is closed even though the pre-existing product regression remains.
  Evidence: `/Users/barry/projects/hyperopen/tmp/browser-inspection/nightly-ui-qa-2026-06-10T02-38-50-734Z-afa3d996/failure-classification.json` recorded `inspectedAddresses` as all three required addresses and route coverage of three desktop plus three mobile `/portfolio` attempts, with zero automation-gap results.

## Decision Log

- Decision: Enforce the required address matrix in the nightly wrapper instead of cloning more checked-in scenario manifests.
  Rationale: The gap is specific to nightly orchestration. Centralizing the matrix in wrapper code makes the contract explicit, keeps the generic scenario runner mostly unchanged, and gives one place to test and report inspected addresses.
  Date/Author: 2026-06-10 / Codex

- Decision: Extend the scenario runner to accept prebuilt in-memory scenario manifests.
  Rationale: This is a smaller and cleaner seam than generating temporary JSON files on disk. It keeps the nightly wrapper additive while preserving the existing scenario manifest format and dry-run behavior.
  Date/Author: 2026-06-10 / Codex

- Decision: Keep the inspected-address summary as ordered contract metadata rather than deriving a free-form address list from whatever URLs happened to appear first.
  Rationale: The nightly report is meant to prove the required three-address contract. Returning the addresses in the governed order makes drift easy to spot and keeps the human-facing report stable across runs.
  Date/Author: 2026-06-10 / Codex

## Outcomes & Retrospective

The nightly wrapper now owns the spectate-address matrix instead of relying on scattered scenario URLs. The new helper module expands `/trade` and `/portfolio` route-smoke scenarios into per-address variants for all three required spectate addresses, the generic scenario runner can consume in-memory manifests, and the nightly report/classification now records the exact inspected addresses. A real nightly run proved the contract with `22` passing scenarios, `1` existing product regression, and `0` automation gaps.

Overall complexity increased slightly in the browser-inspection tooling because there is now a dedicated nightly coverage helper, but the change reduced system complexity at the workflow level. The contract now has one owner, one test surface, and one report summary instead of being implicit across several JSON manifests and a manual inspection step. The remaining failures after the live run are the same known product/regression signals from before this work: `mobile-position-margin-presentation` on `/trade` mobile and the medium `/portfolio` `jank-perf` design-review finding.

## Context and Orientation

The browser-inspection toolchain lives under `/Users/barry/projects/hyperopen/tools/browser-inspection/`. The file `src/scenario_runner.mjs` is the generic executor that takes scenario manifests, starts a browser session, runs each step, and writes per-scenario results plus `summary.json`. A scenario manifest is a JSON file in `tools/browser-inspection/scenarios/` with an `id`, a `url`, one or more `viewports`, and ordered `steps` like `navigate`, `oracle`, `capture`, and `compare`.

The file `src/nightly_ui_qa.mjs` is the nightly wrapper that sits above the scenario runner. It currently selects scenarios by `NIGHTLY_TAGS`, runs the scenario bundle, runs the design review, classifies failures, writes `attempt-summary.tsv`, `failure-classification.json`, and the dated markdown report under `/Users/barry/projects/hyperopen/docs/qa/`.

The contract drift is in two places. First, the required spectate-address matrix is not represented in the wrapper at all. Second, the smoke scenarios for `/trade` and `/portfolio` use unspectated URLs, so the nightly route-coverage rows do not prove that read-only spectate rendering still works for the required addresses. `/vaults` is different: it is allowed to stay baseline and unspectated.

The browser-inspection unit tests live in `/Users/barry/projects/hyperopen/tools/browser-inspection/test/`. `scenario_runner.test.mjs` is the right place to cover new runner inputs. A new nightly-wrapper-focused test file can exercise the pure helper that expands the route smoke scenarios into the required address matrix.

## Plan of Work

Start by writing failing tests around the exact drift we want to prevent. Add a new nightly-wrapper helper module, likely alongside `src/nightly_ui_qa.mjs`, that loads the tagged nightly scenarios and rewrites only the governed smoke scenarios for `/trade` and `/portfolio` into per-address variants. The helper must leave `/vaults` unchanged and preserve the existing richer scenarios like `mobile-position-margin-presentation`, which still rely on a single data-rich address for functional assertions.

To make that helper testable without writing temporary manifests, extend `src/scenario_runner.mjs` so `runScenarioBundle` can accept an explicit `scenarios` array in addition to `scenarioDir`. Keep the current manifest loading path intact when no array is provided. Then use the helper from `src/nightly_ui_qa.mjs` for both dry-run and real execution paths.

After the nightly wrapper has the full matrix, update its classification/report metadata so it records the unique inspected spectate addresses. The markdown report should list those addresses directly. This does not replace route coverage; it complements it so future operators can spot drift immediately from the report itself.

Finish by validating at three levels: the targeted Node unit tests for the helper and runner, the smallest relevant Playwright smoke command required by repo policy for browser-tooling changes, and the repo gates required by `AGENTS.md`. Then run a wrapper-level dry-run or focused verification that proves the selected nightly scenarios now include the three `/trade` and three `/portfolio` spectate variants.

## Concrete Steps

Work from `/Users/barry/projects/hyperopen`.

1. Add failing tests:
   - `node --test tools/browser-inspection/test/scenario_runner.test.mjs`
   - `node --test tools/browser-inspection/test/nightly_ui_coverage.test.mjs`
   The new test file should fail first because the helper and runner support do not exist yet.

2. Implement the runner/helper/wrapper changes:
   - Modify `/Users/barry/projects/hyperopen/tools/browser-inspection/src/scenario_runner.mjs`
   - Create `/Users/barry/projects/hyperopen/tools/browser-inspection/src/nightly_ui_coverage.mjs`
   - Modify `/Users/barry/projects/hyperopen/tools/browser-inspection/src/nightly_ui_qa.mjs`

3. Re-run the targeted tests and inspect the selected scenario metadata:
   - `node --test tools/browser-inspection/test/scenario_runner.test.mjs tools/browser-inspection/test/nightly_ui_coverage.test.mjs`
   - `npm run qa:nightly-ui -- --dry-run`

4. Run the smallest relevant Playwright coverage before broader gates:
   - `npm run test:playwright:smoke`

5. Run the required repo gates from `AGENTS.md`:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

6. Run the browser-inspection unit suite and a final wrapper verification:
   - `npm run test:browser-inspection`
   - `npm run qa:nightly-ui -- --manage-local-app` if time permits and the earlier product regression does not block the coverage verification; otherwise rely on dry-run selection plus unit tests and record the blocker.

## Validation and Acceptance

Acceptance is behavioral:

1. `npm run qa:nightly-ui -- --dry-run` must show that the nightly wrapper selects spectated `/trade` and `/portfolio` smoke scenarios for all three required addresses, while still retaining `/vaults` smoke coverage.
2. The new helper test must prove the selected scenario URLs include:
   - `/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185`
   - `/trade?spectate=0x2ba553d9f990a3b66b03b2dc0d030dfc1c061036`
   - `/trade?spectate=0x4096d3377ae5ade578daae8188804740c8b1da3e`
   - the same three addresses under `/portfolio?spectate=...`
3. The scenario runner test must prove `runScenarioBundle` can execute an in-memory scenario list, because the nightly wrapper depends on that seam.
4. The dated nightly report format must include the unique inspected addresses so the operator can verify the contract from one file.
5. `npm run check`, `npm test`, and `npm run test:websocket` must pass after the change.

## Idempotence and Recovery

The code changes are additive and safe to re-run. The nightly dry-run does not create browser sessions or mutate application state; it only prints the selected scenario set. If a real nightly run is blocked by the known product regression in `mobile-position-margin-presentation`, keep the coverage work and record that the product bug is an external acceptance blocker for the full live bundle rather than rolling back the contract fix.

## Artifacts and Notes

The June 9 report is currently untracked in this worktree and was produced by the automation run before this implementation turn. Do not delete or rewrite it beyond accuracy fixes that directly support this work.

Expected dry-run evidence after the fix should include selected scenario IDs or URLs that make the matrix obvious, for example:

    trade-route-smoke-spectate-162c / http://localhost:8080/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185
    trade-route-smoke-spectate-2ba5 / http://localhost:8080/trade?spectate=0x2ba553d9f990a3b66b03b2dc0d030dfc1c061036
    portfolio-route-smoke-spectate-4096 / http://localhost:8080/portfolio?spectate=0x4096d3377ae5ade578daae8188804740c8b1da3e

## Interfaces and Dependencies

The new helper module should export stable pure functions that the wrapper and tests can both use. At minimum:

    buildNightlyScenarios({ scenarioDir, tags }) -> Promise<Array<ScenarioManifest>>
    extractInspectedAddresses(results) -> Array<string>

`ScenarioManifest` here means the same plain object shape already stored in the checked-in JSON manifests: `id`, `title`, `route`, `severity`, `tags`, `viewports`, `url`, and `steps`.

`runScenarioBundle(service, options)` in `/Users/barry/projects/hyperopen/tools/browser-inspection/src/scenario_runner.mjs` must continue to support `scenarioDir` and also accept:

    options.scenarios?: Array<ScenarioManifest>

When `options.scenarios` is present, it becomes the selected manifest source and still honors optional `scenarioIds`, `tags`, and `viewports` filtering semantics.

Revision note, 2026-06-10 / Codex: Created this active ExecPlan after reproducing the nightly spectate-matrix drift from the June 9 automation report and tracing the gap to unspectated smoke manifests plus tag-only nightly selection.
Revision note, 2026-06-10 / Codex: Updated the plan after implementation with the actual validation commands, the successful live nightly matrix evidence, and the remaining known product/design-review failures.
