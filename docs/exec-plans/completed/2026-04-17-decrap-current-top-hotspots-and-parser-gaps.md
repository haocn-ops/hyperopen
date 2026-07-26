# DeCRAP Current Top Hotspots And Parser Gaps

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The live work-tracking item is `bd` issue `hyperopen-p3q4` (`DeCRAP current top hotspots and CRAP parser gaps`). `bd` was the local tracker reference while this file recorded the implementation plan, progress, decisions, and validation evidence.

## Purpose / Big Picture

After this work, a developer running Hyperopen's CRAP tooling will get a more trustworthy ranked hotspot list: schema contract files will no longer be skipped by parser alias failures, and the current top zero-coverage hotspots will either have real focused coverage or be explained and fixed as coverage-correlation false positives. The visible outcome is a fresh `bb tools/crap_report.clj --scope src` report with `parse_errors=0`, fewer zero-coverage top-ten entries, and no function above the CRAP threshold caused only by missing tests or analyzer blind spots.

The work is intentionally maintenance-focused. It must preserve public ClojureScript APIs, runtime action/effect IDs, DOM anchors, and route-visible behavior. If production UI code under `/hyperopen/src/hyperopen/views/**` changes, browser QA is required and must be accounted for before completion.

## Progress

- [x] (2026-04-17 00:50Z) Generated the baseline CRAP report from fresh coverage and identified the top ten hotspots, the five schema parse errors, and the likely coverage-correlation anomaly for several already-tested functions.
- [x] (2026-04-17 00:50Z) Created `bd` issue `hyperopen-p3q4` for the execution-plan scope.
- [x] (2026-04-17 00:50Z) Created this active ExecPlan.
- [x] (2026-04-17 01:02Z) Verified the approved `parse-int-value` contract was RED, fixed only the finite integer parsing boundary in `src/hyperopen/schema/contracts/common.cljs`, and updated the websocket schema smoke rejection sample from integer string `"12"` to fractional string `"12.5"` after `::intish` intentionally accepted integer-shaped strings.
- [x] (2026-04-17 01:02Z) Reran `npx shadow-cljs --force-spawn compile test && node out/test.js`, `npm test`, `npm run check`, and `npm run test:websocket` for the `parse-int-value` fix.
- [x] (2026-04-17 01:05Z) Follow-up coverage verification showed the integer-string regex needed explicit anchors in the websocket coverage path, so `parse-int-value` now uses `#"^[+-]?\d+$"` and `npm run coverage` passes.
- [x] (2026-04-17 02:18Z) Reproduced the baseline in this plan's Artifacts section with whole-project and module-scoped CRAP output.
- [x] (2026-04-17 02:18Z) Audited the top zero-coverage functions against existing tests and `coverage/lcov.info` line data. `offset-input-ready?`, `parse-int-value`, and `set-spectate-mode-search` were real focused-test gaps; `compute-connection-id`, `change-indicator`, `data-column`, and `set-connected!` were coverage-correlation/source-map artifacts with positive function-hit evidence; `open-spectate-mode-modal`, `handle-message`, and `schedule-asset-list-render-limit-sync!` needed small helper extraction or deterministic scheduler coverage.
- [x] (2026-04-17 02:22Z) Fixed CRAP parser auto-resolve handling for both aliased `::common/value` and bare current-namespace `::value`, and fixed nil-message parser exceptions so they remain visible in `parse-errors`.
- [x] (2026-04-17 02:24Z) Added focused tests/refactors for the current top hotspots according to the audit classification.
- [x] (2026-04-17 02:34Z) Reran coverage and CRAP reports, including module-scoped reports for the formerly hot modules.
- [x] (2026-04-17 02:38Z) Ran required gates plus the narrow asset-selector Playwright regression and browser cleanup for the touched UI runtime seam.
- [x] (2026-04-17 02:41Z) Recorded final results and updated `bd` for issue `hyperopen-p3q4`.

## Surprises & Discoveries

- Observation: Several functions that existing tests already call still appeared with `coverage=0.00` in the CRAP report.
  Evidence: `test/hyperopen/account/spectate_mode_actions_test.cljs` calls `open-spectate-mode-modal`, `test/hyperopen/utils/hl_signing_test.cljs` calls `compute-connection-id`, and `test/hyperopen/views/active_asset/row_test.cljs` renders paths that include `change-indicator` and `data-column`, yet `coverage/lcov.info` currently shows `DA` rows of `0` for those source ranges.

- Observation: The schema parse failures all involve auto-resolved namespaced keywords such as `::common/foo` or `::action-args/foo`.
  Evidence: the whole-project report printed parse errors for `src/hyperopen/schema/contracts.cljs`, `src/hyperopen/schema/contracts/action_args.cljs`, `src/hyperopen/schema/contracts/assertions.cljs`, `src/hyperopen/schema/contracts/effect_args.cljs`, and `src/hyperopen/schema/contracts/state.cljs` with messages like `Alias common not found in :auto-resolve`.

- Observation: `parse-int-value` rejected all string integers because its regex literal matched a backslash followed by `d`, and it accepted `js/Infinity` because the number branch rejected only `NaN`.
  Evidence: the approved contract in `test/hyperopen/schema/contracts/common_test.cljs` failed for `" -7 "`, `"+42"`, and `js/Infinity` before the production change.

- Observation: The websocket schema smoke test used `["12"]` as a rejection sample for `:actions/next-order-history-page`, but that action is backed by `::common/max-page-args` and `::common/intish`.
  Evidence: after the `parse-int-value` fix, `npm run test:websocket` failed only at `test/hyperopen/websocket/coverage_low_functions_test.cljs:145`; changing the rejection sample to `["12.5"]` preserves fractional rejection without contradicting the integer-string contract.

- Observation: The websocket coverage run still required explicit regex anchors for fractional strings.
  Evidence: after the first production fix, follow-up verification reported `npm run coverage` failed in the websocket contract path because `#"[+-]?\d+"` accepted the integer prefix of `"12.5"`; anchoring the regex as `#"^[+-]?\d+$"` made `npm run coverage` pass with both main and websocket test runners green.

- Observation: Edamame needed both alias auto-resolution and a current namespace fallback for bare current-namespace auto-resolved keywords.
  Evidence: `bb -e '(require (quote [tools.crap.complexity :as c])) (c/analyze-file "." "src/hyperopen/schema/contracts/common.cljs")'` threw a `NullPointerException` before adding `:auto-resolve {:current 'user}` alongside `:auto-resolve-ns true`; after the fix it returned `14` records for `common.cljs`.

- Observation: The CRAP analyzer could hide nil-message parser failures.
  Evidence: a reviewer reproduced `bb tools/crap_report.clj --module src/hyperopen/schema/contracts/common.cljs --format json` reporting `functions-scanned=0` and `parse-errors=[]` while direct parsing threw `NullPointerException`. `dev.crap-test/analyzer-reports-parser-errors-without-throwable-messages` now guards this.

- Observation: `offset-input-ready?` intentionally preserves `normalize-unit` fallback semantics for unknown units.
  Evidence: `normalize-unit` already maps `:unknown` to `:usd`; the focused test now asserts unknown units are ready when the USD-required baseline and size are present and not ready when size is missing.

## Decision Log

- Decision: Begin with a coverage-correlation audit before adding tests.
  Rationale: Adding duplicate tests to already-covered behavior would not improve the CRAP report if the real issue is LCOV/source-map correlation or analyzer logic. The first milestone must prove which case applies to each hotspot.
  Date/Author: 2026-04-17 / Codex

- Decision: Keep parser fixes in the same plan as hotspot remediation.
  Rationale: The user's follow-up list included both hotspot remediation and parser parse-error cleanup, and both affect whether the next top-ten report is trustworthy.
  Date/Author: 2026-04-17 / Codex

- Decision: Preserve `::intish` as the shared integer-shaped input contract after fixing `parse-int-value`, and update stale tests that asserted bare integer strings were invalid.
  Rationale: The approved `parse-int-value` contract explicitly accepts signed and trimmed integer strings, so shared specs using `parseable-int?` should accept `"12"` while still rejecting fractional strings such as `"12.5"`.
  Date/Author: 2026-04-17 / Codex

- Decision: Trust positive LCOV `FNDA` function hits when every source-mapped `DA` row inside the analyzed function range is zero, but keep line-ratio coverage when any relevant `DA` row is positive.
  Rationale: This fixes source-map false negatives for already-executed functions without inflating partially covered functions.
  Date/Author: 2026-04-17 / Codex

- Decision: Preserve the existing unknown-unit TPSL fallback to USD instead of changing runtime semantics to reject unknown units.
  Rationale: The existing `normalize-unit` contract explicitly maps unknown units to `:usd`; this plan is a CRAP/tooling/test remediation, not a behavior change to order-entry policy.
  Date/Author: 2026-04-17 / Codex

## Outcomes & Retrospective

Final outcome (2026-04-17 02:41Z): The final whole-project CRAP report from fresh LCOV scanned `579` files, `547` modules, and `7789` functions with `parse_errors=0`, `crappy_functions=0`, and `project_crapload=0.00`. The original top-ten zero-coverage entries no longer appear in the whole-project top functions. The improvement came from a mix of focused tests, a CRAP coverage-correlation fix for positive `FNDA`/zero-`DA` source-map artifacts, parser auto-resolve hardening, and small helper extraction around side-effect boundaries.

Final top function is now `hyperopen.vaults.adapters.webdata/normalize-twap-row` at CRAP `28.09`, coverage `0.77`, complexity `22`. The highest formerly targeted score is `parse-int-value` at CRAP `8.00`, coverage `1.00`, complexity `8`; the remaining targeted functions are all at CRAP `6.00` or below except `schedule-asset-list-render-limit-sync!` at `5.01`.

Partial outcome (2026-04-17 01:02Z): The `parse-int-value` RED contract is green. The production change is limited to finite integer parsing in `src/hyperopen/schema/contracts/common.cljs`; the only test edit updates a downstream schema smoke rejection sample to remain consistent with the approved integer-string contract. Browser QA is not applicable for this slice because no UI production files changed.

Partial outcome (2026-04-17 01:05Z): The follow-up anchor fix preserves signed whole-string parsing while rejecting fractional strings in the websocket coverage path. `npm test`, `npm run test:websocket`, `npm run check`, and `npm run coverage` all exit 0 for this slice.

## Context and Orientation

Hyperopen is a ClojureScript application compiled by Shadow CLJS. The relevant commands are run from `/hyperopen`, which in this worktree is `/Users/barry/.codex/worktrees/88d3/hyperopen`. The command `npm run coverage` compiles the main test build and websocket test build, runs both Node test runners with V8 coverage, and writes `coverage/lcov.info`. The command `bb tools/crap_report.clj --scope src` reads that LCOV file, parses ClojureScript source under `src/`, computes source-level cyclomatic complexity, calculates per-function coverage, and ranks functions by the CRAP formula.

In this plan, CRAP means `complexity^2 * (1 - coverage)^3 + complexity`, where coverage is a ratio from `0.0` to `1.0`. A function at complexity `5` and coverage `0.0` scores `30.0`, which is exactly the repo's current default threshold. "Parse error" means the CRAP source parser skipped a file before scoring its functions. "Coverage-correlation anomaly" means tests may execute behavior but the source-mapped LCOV rows used by the CRAP reporter still show no covered lines for that function range.

The baseline whole-project CRAP report from fresh coverage scanned `579` files, `543` modules, and `7763` functions. It reported `crappy_functions=0`, `threshold=30.00`, `project_crapload=0.00`, and `parse_errors=5`. The current top ten functions are:

- `src/hyperopen/account/spectate_mode_actions.cljs:98` `hyperopen.account.spectate-mode-actions/open-spectate-mode-modal`, CRAP `30.00`, coverage `0.00`, complexity `5`.
- `src/hyperopen/portfolio/worker.cljs:18` `hyperopen.portfolio.worker/handle-message`, CRAP `30.00`, coverage `0.00`, complexity `5`.
- `src/hyperopen/trading/order_form_tpsl_policy.cljs:61` `hyperopen.trading.order-form-tpsl-policy/offset-input-ready?`, CRAP `30.00`, coverage `0.00`, complexity `5`.
- `src/hyperopen/utils/hl_signing.cljs:154` `hyperopen.utils.hl-signing/compute-connection-id`, CRAP `30.00`, coverage `0.00`, complexity `5`.
- `src/hyperopen/views/active_asset/row.cljs:27` `hyperopen.views.active-asset.row/change-indicator`, CRAP `30.00`, coverage `0.00`, complexity `5`.
- `src/hyperopen/views/active_asset/row.cljs:35` `hyperopen.views.active-asset.row/data-column`, CRAP `30.00`, coverage `0.00`, complexity `5`.
- `src/hyperopen/views/asset_selector/runtime.cljs:188` `hyperopen.views.asset-selector.runtime/schedule-asset-list-render-limit-sync!`, CRAP `30.00`, coverage `0.00`, complexity `5`.
- `src/hyperopen/wallet/core.cljs:161` `hyperopen.wallet.core/set-connected!`, CRAP `30.00`, coverage `0.00`, complexity `5`.
- `src/hyperopen/schema/contracts/common.cljs:24` `hyperopen.schema.contracts.common/parse-int-value`, CRAP `29.30`, coverage `0.23`, complexity `7`.
- `src/hyperopen/account/spectate_mode_actions.cljs:125` `hyperopen.account.spectate-mode-actions/set-spectate-mode-search`, CRAP `28.67`, coverage `0.14`, complexity `6`.

The existing tests and production files most relevant to this plan are:

- `test/hyperopen/account/spectate_mode_actions_test.cljs` already covers opening the spectate modal, starting/stopping spectate mode, watchlist edits, copying links, and route query preservation. It does not yet have a focused assertion for `set-spectate-mode-search` preserving an editing label only when the normalized search still matches the edited watchlist address.
- `src/hyperopen/portfolio/worker.cljs` keeps `handle-message` private and mixes message decoding, metrics computation, benchmark mapping, and `postMessage` in one function. The likely low-risk shape is to extract a pure helper that returns the `"metrics-result"` payload, then leave `handle-message` as a side-effect wrapper.
- `test/hyperopen/trading/order_form_tpsl_policy_test.cljs` covers conversions through `offset-display-from-trigger`, `trigger-from-offset-input`, and `offset-display`, but does not directly exercise `offset-input-ready?` for the three units and missing-baseline/size/leverage cases.
- `test/hyperopen/utils/hl_signing_test.cljs` already has parity vectors that call `compute-connection-id`, including no-vault and vault-plus-expires cases. If fresh focused coverage still reports zero for this function, treat it as an analyzer/source-map attribution problem before adding more parity tests.
- `test/hyperopen/views/active_asset/row_test.cljs` renders active-asset rows through public functions and already reaches UI paths that conceptually use `change-indicator` and `data-column`. Direct private-var helper tests may still be useful because they make the color and numeric Hiccup contract explicit.
- `test/hyperopen/views/asset_selector/runtime_test.cljs` references `schedule-asset-list-render-limit-sync!` often, but several tests redefine it as a stub while exercising surrounding lifecycle code. Add a dedicated deterministic test for the real scheduler if the audit confirms it is still uncovered.
- `test/hyperopen/wallet/core_test.cljs` already covers `set-connected!` paths around previous-address clearing, notification, persisted sessions, and wallet state changes. If LCOV keeps reporting zero for the function body, prefer extracting a pure state-transition helper rather than duplicating similar tests.
- There is currently no obvious focused test namespace for `hyperopen.schema.contracts.common/parse-int-value`; add one under `test/hyperopen/schema/contracts/common_test.cljs` unless a better local owner appears during implementation.

## Plan of Work

Milestone 1 proves the baseline and classifies each hotspot. Re-run `npm run coverage` if `coverage/lcov.info` is missing or stale, then run the whole-project JSON report and module reports for each file in the top ten. For each function, inspect both the test namespace that should exercise it and the LCOV `DA` rows inside the function's source range. Record a short classification in `Surprises & Discoveries`: real coverage gap, source-map/LCOV attribution gap, or mixed side-effect wrapper that should be split. This milestone does not change production behavior.

Milestone 2 fixes schema parser alias handling. In `tools/crap/complexity.clj`, change the edamame parse options so auto-resolved namespaced keywords are resolved from each file's `ns` form instead of requiring every alias to be predeclared as `user`. Edamame supports `:auto-resolve-ns true`, which is the direct fit for source files that contain an `ns` form with `:require` aliases. Add a regression fixture to `dev/crap_test.clj` with a sample namespace requiring `[sample.common :as common]` and using `::common/value` inside a top-level function or def map. The test must fail before the parser fix with an alias error and pass after the fix. Acceptance for this milestone is `npm run test:crap` passing and `bb tools/crap_report.clj --scope src --format json` reporting `parse-errors` as an empty vector.

Milestone 3 remediates true test gaps in the top ten. Add narrow tests before changing source code. For `src/hyperopen/trading/order_form_tpsl_policy.cljs`, extend `test/hyperopen/trading/order_form_tpsl_policy_test.cljs` with direct assertions that `offset-input-ready?` returns true for `:usd` only when baseline and size are positive, true for `:roe-percent` only when baseline and leverage are positive, true for `:position-percent` when baseline is positive even without size/leverage, false when the unit-specific required inputs are missing, and that unknown units preserve the existing `normalize-unit` fallback to `:usd`. For `src/hyperopen/schema/contracts/common.cljs`, create `test/hyperopen/schema/contracts/common_test.cljs` with direct assertions for integers, whole finite numbers, fractional rejection, signed and whitespace-padded string integers, blank strings, non-numeric strings, `nil`, and non-string collections. For `src/hyperopen/account/spectate_mode_actions.cljs`, add focused assertions to `test/hyperopen/account/spectate_mode_actions_test.cljs` for `set-spectate-mode-search`: a matching normalized editing address preserves the existing label and editing address, while a different search clears both and clears the search error. Run `npm test` after each focused set and update `Progress`.

Milestone 4 handles hotspots that are side-effect wrappers or likely analyzer false positives. For `src/hyperopen/portfolio/worker.cljs`, extract a pure helper such as `metrics-result-payload` or `compute-metrics-message-payload` that accepts the decoded payload map and returns `{:portfolio-values ... :benchmark-values-by-coin ...}`. Then leave `handle-message` responsible only for decoding the event, dispatching on type, posting the result, and warning on unknown types. Add `test/hyperopen/portfolio/worker_test.cljs` to call the pure helper with `:strategy-daily-rows` and fallback `:strategy-cumulative-rows` inputs so benchmark and portfolio request branches are both covered. If a direct test of private `handle-message` is feasible without brittle global worker mutation, add it; otherwise accept the thinner wrapper as a small side-effect boundary and verify its CRAP score after coverage.

For `src/hyperopen/views/asset_selector/runtime.cljs`, first check whether `schedule-asset-list-render-limit-sync!` can be tested deterministically through existing wrappers. The source already defines `asset-list-set-timeout!` and `asset-list-clear-timeout!`, but the scheduler currently calls `js/setTimeout` directly. If confirmed, change the scheduler to call `asset-list-set-timeout!`, then add a focused test in `test/hyperopen/views/asset_selector/runtime_test.cljs` that redefines `asset-list-set-timeout!` to capture and invoke the callback, supplies a fake store, verifies only one timeout is scheduled while the atom is non-nil, and verifies the callback dispatches `[[:actions/show-all-asset-selector-markets]]` only when `asset-list-render-limit-sync-required?` remains true. This is a production UI runtime file, so browser QA must be accounted for if this source edit lands.

For `src/hyperopen/wallet/core.cljs`, inspect why existing `wallet/core_test.cljs` coverage does not mark `set-connected!` as covered. If the issue is that the branchy work lives inside a `swap!` callback whose source-mapped rows are not attributed to the outer function, extract a pure helper such as `connected-wallet-state` that accepts `wallet-state`, `addr`, and the loaded persisted session, and returns the merged wallet state. Test the helper directly for same-address and changed-address behavior, preserving passkey support and storage/local-protection modes. Keep `set-connected!` as the public side-effect function and preserve its `notify-connected?` API.

For `src/hyperopen/utils/hl_signing.cljs`, do not add duplicate parity vectors until the audit proves a real behavioral gap. If `compute-connection-id` remains zero-covered despite the existing parity tests, add a `dev/crap_test.clj` regression around coverage correlation only if the CRAP analyzer is demonstrably wrong. If the issue is source-map attribution outside the analyzer's control, record the limitation in `Artifacts and Notes` and keep the existing parity tests as the behavioral evidence.

For `src/hyperopen/views/active_asset/row.cljs`, add direct private-var tests in `test/hyperopen/views/active_asset/row_test.cljs` only if the audit shows current public render tests do not move the source rows. Assert that `change-indicator` emits `text-success` for non-negative deltas, `text-error` for negative or nil deltas, and falls back to `"-- / --"` for missing formatted values. Assert that `data-column` applies numeric and underline classes, and delegates change rendering when `:change?` is true. This is a UI source module, but helper-test-only edits do not require browser QA unless the production Hiccup changes.

Milestone 5 reruns the reports and decides whether further refactor is justified. After tests and parser fixes pass, run `npm run coverage`, then run the whole-project report and module-scoped reports for every touched source file. The must-run module reports are:

    bb tools/crap_report.clj --module src/hyperopen/account/spectate_mode_actions.cljs --format json
    bb tools/crap_report.clj --module src/hyperopen/views/active_asset/row.cljs --format json

Also run module reports for `src/hyperopen/portfolio/worker.cljs`, `src/hyperopen/trading/order_form_tpsl_policy.cljs`, `src/hyperopen/utils/hl_signing.cljs`, `src/hyperopen/views/asset_selector/runtime.cljs`, `src/hyperopen/wallet/core.cljs`, and `src/hyperopen/schema/contracts/common.cljs` if those files changed or remained in the top ten. Record before/after scores for the named functions in `Artifacts and Notes`.

Milestone 6 runs final validation and cleanup. Required gates for code changes are `npm run check`, `npm test`, and `npm run test:websocket`. If production UI files under `src/hyperopen/views/**` changed, also run the smallest relevant committed browser check first, then governed design review for the touched route. For active asset row or asset selector runtime changes, the expected narrow browser command is:

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "asset selector opens and selects ETH"

If that passes and production UI behavior changed, run:

    npm run qa:design-ui -- --targets trade-route --manage-local-app
    npm run browser:cleanup

If only tests or non-UI tooling changed, explicitly record browser QA as not applicable and explain why.

## Concrete Steps

From `/Users/barry/.codex/worktrees/88d3/hyperopen`, first ensure dependencies and coverage are present:

    npm ci
    npm run coverage

Run the baseline reports and save concise output in this plan:

    bb tools/crap_report.clj --scope src
    bb tools/crap_report.clj --scope src --format json
    bb tools/crap_report.clj --module src/hyperopen/account/spectate_mode_actions.cljs --format json
    bb tools/crap_report.clj --module src/hyperopen/views/active_asset/row.cljs --format json

Inspect LCOV line attribution for any surprising zero-coverage function. A useful pattern is:

    awk 'BEGIN{show=0} /^SF:.*hyperopen\/account\/spectate_mode_actions.cljs$/{show=1; print; next} show && /^end_of_record/{print; show=0} show && /^(FN|FNDA|DA):(9[8-9]|1[0-3][0-9]),/{print}' coverage/lcov.info

Repeat the same shape for each source file under investigation. Use the output to decide whether to add tests, split a side-effect wrapper, or fix the analyzer.

After each focused test or parser change, run the smallest relevant command:

    npm run test:crap
    npm test

After all changes, run the full required validation set:

    npm run check
    npm test
    npm run test:websocket
    npm run coverage
    bb tools/crap_report.clj --scope src

If browser QA is required by UI production changes, run the Playwright/design-review commands from Milestone 6 and then clean up browser sessions:

    npm run browser:cleanup

## Validation and Acceptance

Acceptance requires all of the following:

- `bb tools/crap_report.clj --scope src` completes with `parse_errors=0`, and the JSON report has an empty `parse-errors` vector.
- `npm run test:crap` passes and includes a regression proving auto-resolved namespace aliases such as `::common/value` parse correctly.
- The top-ten hotspot list no longer contains avoidable zero-coverage entries for functions where focused tests or small helper extraction can provide accurate coverage.
- `offset-input-ready?`, `parse-int-value`, and `set-spectate-mode-search` have direct behavioral tests for their branch cases.
- Any production refactor preserves public APIs and side-effect boundaries. In particular, `set-connected!`, `compute-connection-id`, and the runtime action/effect IDs keep their existing call shapes.
- `npm run check`, `npm test`, and `npm run test:websocket` pass after code changes.
- `npm run coverage` passes after code changes and produces the final report artifacts used by this plan.
- If UI production files changed, the relevant Playwright/browser QA commands are run and `npm run browser:cleanup` is executed before completion. If no UI production files changed, this plan records that browser QA was not applicable.

## Idempotence and Recovery

The commands in this plan are safe to rerun. `npm run coverage` deletes and recreates `.coverage` and `coverage`, so do not rely on those directories for persistent notes. Store important transcripts in this ExecPlan or in a deliberate artifact file under `docs/exec-plans/active/artifacts/` if the output is too large for the plan.

If `npm run coverage` fails because dependencies are missing, run `npm ci` and retry. If it fails due a ClojureScript compile or test error introduced by this work, stop and fix the failing focused test before broadening validation. If browser inspection leaves a server/session running, run `npm run browser:cleanup` and retry the browser command.

Do not run `git pull --rebase` or `git push` during this work unless the user explicitly requests remote sync in the current session.

## Artifacts and Notes

Baseline command evidence from the CRAP analysis that created this plan:

    npm run coverage
    Ran 3207 tests containing 17082 assertions.
    0 failures, 0 errors.
    Ran 432 tests containing 2479 assertions.
    0 failures, 0 errors.
    Statements: 90.67%
    Branches: 69.32%
    Functions: 83.62%
    Lines: 90.67%

    bb tools/crap_report.clj --scope src
    scanned_files=579
    modules_scanned=543
    functions_scanned=7763
    crappy_functions=0
    parse_errors=5
    threshold=30.00
    project_crapload=0.00

The five parser failures were:

    src/hyperopen/schema/contracts.cljs
    src/hyperopen/schema/contracts/action_args.cljs
    src/hyperopen/schema/contracts/assertions.cljs
    src/hyperopen/schema/contracts/effect_args.cljs
    src/hyperopen/schema/contracts/state.cljs

Observed LCOV anomaly examples:

    src/hyperopen/account/spectate_mode_actions.cljs line 98 open-spectate-mode-modal has DA rows 98-115 all at 0 despite an existing direct test.
    src/hyperopen/utils/hl_signing.cljs line 154 compute-connection-id has DA rows 154-169 all at 0 despite existing parity vector tests that call the function.
    src/hyperopen/views/active_asset/row.cljs lines 27 and 35 have DA rows at 0 despite public row-render tests.

Final CRAP report evidence (2026-04-17 02:41Z):

    npm run coverage
    Ran 3217 tests containing 17122 assertions.
    0 failures, 0 errors.
    Ran 432 tests containing 2479 assertions.
    0 failures, 0 errors.
    Statements: 90.71%
    Branches: 69.37%
    Functions: 83.67%
    Lines: 90.71%

    bb tools/crap_report.clj --scope src
    scanned_files=579
    modules_scanned=547
    functions_scanned=7789
    crappy_functions=0
    parse_errors=0
    threshold=30.00
    project_crapload=0.00

Final before/after scores for the original top-ten targets:

    open-spectate-mode-modal: 30.00 -> 2.00 (coverage 0.00, complexity 5 -> 1)
    handle-message: 30.00 -> 3.94 (coverage 0.21, complexity 5 -> 2)
    offset-input-ready?: 30.00 -> 5.00 (coverage 1.00, complexity 5)
    compute-connection-id: 30.00 -> 5.00 (coverage 1.00, complexity 5)
    change-indicator: 30.00 -> 5.00 (coverage 1.00, complexity 5)
    data-column: 30.00 -> 5.00 (coverage 1.00, complexity 5)
    schedule-asset-list-render-limit-sync!: 30.00 -> 5.01 (coverage 0.93, complexity 5)
    set-connected!: 30.00 -> 5.00 (coverage 1.00, complexity 5)
    parse-int-value: 29.30 -> 8.00 (coverage 1.00, complexity 8)
    set-spectate-mode-search: 28.67 -> 6.00 (coverage 1.00, complexity 6)

Final validation evidence:

    npm run test:crap
    Ran 8 tests containing 30 assertions.
    0 failures, 0 errors.

    npm test
    Ran 3217 tests containing 17122 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 432 tests containing 2479 assertions.
    0 failures, 0 errors.

    npm run check
    Completed all configured static/tooling/build checks with exit status 0.

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "asset selector opens and selects ETH"
    1 passed.

    npm run browser:cleanup
    {"ok": true, "stopped": [], "results": []}

Review follow-up evidence:

    A read-only reviewer found that bare current-namespace auto-resolved keywords still threw `NullPointerException` and that nil-message parser exceptions were hidden. Both are now covered in `dev/crap_test.clj`, and `bb tools/crap_report.clj --module src/hyperopen/schema/contracts/common.cljs --format json` now reports `functions-scanned=14`, `parse-errors=[]`.

Plan creation note (2026-04-17 00:50Z): Created after the user requested an execution plan based on the CRAP analysis follow-ups. The plan intentionally starts with evidence gathering because the baseline report showed zero coverage for functions with existing tests, which makes blind test addition risky.

Parse integer contract evidence (2026-04-17 01:02Z):

    npx shadow-cljs --force-spawn compile test && node out/test.js
    RED before fix: 3214 tests, 17110 assertions, 3 failures, all in `hyperopen.schema.contracts.common-test` for `" -7 "`, `"+42"`, and `js/Infinity`.
    GREEN after fix: 3214 tests, 17110 assertions, 0 failures, 0 errors.

    npm test
    Generated test/test_runner_generated.cljs with 482 namespaces.
    Ran 3214 tests containing 17110 assertions.
    0 failures, 0 errors.

    npm run check
    Completed all configured static/tooling/build checks with exit status 0.

    npm run test:websocket
    Initial post-fix run failed only because `test/hyperopen/websocket/coverage_low_functions_test.cljs` still expected `["12"]` to be rejected by `::intish`.
    After updating that rejection sample to `["12.5"]`: Ran 432 tests containing 2479 assertions; 0 failures, 0 errors.

Parse integer anchor follow-up evidence (2026-04-17 01:05Z):

    npm test
    Ran 3214 tests containing 17110 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 432 tests containing 2479 assertions.
    0 failures, 0 errors.

    npm run check
    Completed all configured static/tooling/build checks with exit status 0.

    npm run coverage
    Ran 3214 tests containing 17110 assertions.
    0 failures, 0 errors.
    Ran 432 tests containing 2479 assertions.
    0 failures, 0 errors.
    Statements: 90.69%
    Branches: 69.35%
    Functions: 83.64%
    Lines: 90.69%
