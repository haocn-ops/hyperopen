# Reduce Open CRAP Hotspot Beads Under `hyperopen-ma0f`

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Linked work: `hyperopen-ma0f` ("Reduce top CRAP hotspots from coverage triage") plus the child beads `hyperopen-41zq`, `hyperopen-akuo`, `hyperopen-4ofd`, `hyperopen-hl3h`, `hyperopen-g2np`, `hyperopen-9ysa`, `hyperopen-25iz`, `hyperopen-ryvg`, `hyperopen-tlm1`, and `hyperopen-aaqv`. Excluded child bead: `hyperopen-4hzw`, already closed as a placeholder.

## Purpose / Big Picture

After this work, the current top CRAP hotspots in `src/` should either disappear, drop below the default CRAP threshold, or become thinner wrappers with direct tests proving behavior. The user-visible goal is no product regression: routes, tables, charts, modals, and trading effects should behave the same. The observable proof is a fresh CRAP report showing the targeted bead functions no longer above the threshold, plus the required repository gates and browser-QA accounting for the UI-facing slices.

The baseline re-run on 2026-03-19 confirms the original ten open child beads still match the current live report exactly:

- `hyperopen.vaults.adapters.webdata/balances` — CRAP `92.23`
- `hyperopen.views.api-wallets-view/modal-view` — CRAP `91.94`
- `hyperopen.views.trading-chart.core/chart-canvas` — CRAP `90.16`
- `hyperopen.api.trading/sign-and-post-agent-action!` — CRAP `90.00`
- `hyperopen.views.vaults.detail.chart-view/chart-series-area-layers` — CRAP `86.58`
- `hyperopen.runtime.action-adapters/navigate` — CRAP `72.00`
- `hyperopen.vaults.adapters.webdata/fill-row` — CRAP `61.82`
- `hyperopen.views.account-info.projections.orders/normalize-order-history-row` — CRAP `61.32`
- `hyperopen.portfolio.actions/returns-benchmark-candle-request` — CRAP `60.01`
- `hyperopen.views.l2-orderbook-view/normalize-trade` — CRAP `57.90`

## Progress

- [x] (2026-03-19 22:37Z) Claimed `hyperopen-ma0f`, verified the worktree was clean, and confirmed the repository lacked `node_modules`.
- [x] (2026-03-19 22:41Z) Restored the declared Node toolchain with `npm ci`.
- [x] (2026-03-19 22:42Z) Ran `npm run coverage` successfully to create `coverage/lcov.info`.
- [x] (2026-03-19 22:46Z) Ran `bb tools/crap_report.clj --scope src --top-functions 25 --top-modules 10 --format json` and confirmed the ten open child beads still match the live hotspot list.
- [x] (2026-03-19 22:47Z) Created this parent ExecPlan and recorded the live baseline CRAP scores.
- [x] (2026-03-19 23:18Z) Reduced `hyperopen.vaults.adapters.webdata` by extracting private alias readers for fills and balances, added direct tests for the refactor, and preserved the downstream transfer regression.
- [x] (2026-03-19 23:19Z) Re-ran `npm run coverage`, `npm run check`, `npm run test:websocket`, and the targeted webdata/transfer test invocations; all passed. The `npm test -- --namespace ...` harness ignored the namespace flag and ran the full suite, which also passed.
- [x] (2026-03-19 23:20Z) Verified the touched module with `bb tools/crap_report.clj --module src/hyperopen/vaults/adapters/webdata.cljs --format json`; the module now reports `crappy-functions: 0` and the hotspot functions `balances` and `fill-row` are below threshold.
- [x] (2026-03-19 23:29Z) Completed the remaining Wave 1 reductions in `portfolio/actions.cljs`, `views/api_wallets_view.cljs`, `views/vaults/detail/chart_view.cljs`, and `views/l2_orderbook_view.cljs`, including the new dedicated `api_wallets_view` render tests and direct coverage for the chart/orderbook helpers.
- [x] (2026-03-19 23:33Z) Completed the Wave 2 pure-logic decompositions in `views/account_info/projections/orders.cljs` and `runtime/action_adapters.cljs` while preserving output shapes and route-effect ordering.
- [x] (2026-03-19 23:36Z) Completed the Wave 3 orchestration refactors in `views/trading_chart/core.cljs` and `api/trading.cljs`, added lifecycle/session-focused regressions, and repaired the new `trading_chart.core` tests so the suite remained green.
- [x] (2026-03-19 23:37Z) Ran the required repository gates: `npm run check`, `npm test`, `npm run test:websocket`, and `npm run coverage`; all passed on the final tree.
- [x] (2026-03-19 23:39Z) Verified every targeted hotspot with module-scoped CRAP reports. All ten child-bead functions are now below the default threshold or no longer appear above threshold. Two touched modules still contain unrelated CRAP hotspots outside this epic: `normalize-portfolio-account-info-tab` (`35.63`) and `normalize-summary-time-range` (`31.00`) in `portfolio/actions.cljs`, plus `normalize-open-order` (`46.00`) in `views/account_info/projections/orders.cljs`.
- [x] (2026-03-19 23:40Z) Ran governed browser QA with artifacts under `/Users/barry/.codex/worktrees/56fd/hyperopen/tmp/browser-inspection/design-review-2026-03-19T23-02-10-345Z-75a70138/`. Required route accounting is complete across widths `375`, `768`, `1280`, and `1440`; the overall bundle state is `FAIL` only because of unrelated `/portfolio` jank. `/api-wallets` and `/vaults/detail` report `interaction: BLOCKED` because the reviewed scenes expose no focusable controls.
- [x] (2026-03-19 23:41Z) Added the final execution notes to `bd`, then closed the ten child beads and the parent epic `hyperopen-ma0f` from the measured post-change outcome.

## Surprises & Discoveries

- Observation: the current worktree had no installed Node dependencies at the start of execution, so the baseline coverage run was blocked until `npm ci`.
  Evidence: `test -d node_modules && echo INSTALLED || echo MISSING` printed `MISSING` before installation.

- Observation: the live baseline exactly matches the original ten open child beads, so no child bead can be closed as stale before implementation.
  Evidence: `bb tools/crap_report.clj --scope src --top-functions 25 --top-modules 10 --format json` returned the same ten named hotspot functions at the top of the report.

- Observation: several target modules already have strong surrounding regression suites, but the hotspot functions still score highly because complexity, not just missing tests, is driving the metric.
  Evidence: `chart-canvas`, `normalize-order-history-row`, and `navigate` all appear in files with substantial direct test coverage, yet remain above threshold in the live baseline.

- Observation: the repository test harness ignores `npm test -- --namespace ...` filtering and executes the full suite instead.
  Evidence: each targeted namespace invocation reran the full project suite, so the final validation relied on explicit full-suite success rather than namespace-level isolation.

- Observation: governed browser QA expands the touched view set into its standard route targets, so the recorded bundle includes `/portfolio` and `/vaults` in addition to the requested `/api-wallets`, `/trade`, and `/vaults/detail` coverage.
  Evidence: `/Users/barry/.codex/worktrees/56fd/hyperopen/tmp/browser-inspection/design-review-2026-03-19T23-02-10-345Z-75a70138/summary.json` lists target ids `api-wallets-route`, `portfolio-route`, `trade-route`, `vault-detail-route`, and `vaults-route`.

- Observation: the governed review's only failures were unrelated `/portfolio` jank traces at widths `1280` and `1440`; none of the targeted route passes failed.
  Evidence: the bundle summary reports two `jank-perf` issues, both attached to `/portfolio`, while the targeted routes remain `PASS` or `BLOCKED` only.

## Decision Log

- Decision: keep the original epic scope intact and implement the ten open child beads instead of reticketing the work.
  Rationale: the live CRAP rebaseline still matches the original bead set exactly, so the prepared issue fan-out remains valid.
  Date/Author: 2026-03-19 / Codex

- Decision: execute the work in three waves, from low-risk lookup/normalizer cleanup through pure decomposition to the highest-risk chart/trading orchestration changes.
  Rationale: this sequencing reduces merge risk, produces earlier CRAP wins, and isolates the most behavior-sensitive refactors until the end.
  Date/Author: 2026-03-19 / Codex

- Decision: preserve all public function names, arities, and output shapes for the targeted hotspots.
  Rationale: the request is maintainability and testability improvement, not API redesign, and several hotspots sit on public or broadly consumed seams.
  Date/Author: 2026-03-19 / Codex

## Outcomes & Retrospective

This wave is complete. All ten targeted child-bead hotspots were reduced below the default CRAP threshold without changing the public interfaces called out in this plan, and the epic plus all open child beads were closed in `bd`.

The post-change hotspot results are:

- `hyperopen.vaults.adapters.webdata/balances` -> `5.00`
- `hyperopen.vaults.adapters.webdata/fill-row` -> `4.00`
- `hyperopen.views.api-wallets-view/modal-view` -> `2.00`
- `hyperopen.views.vaults.detail.chart-view/chart-series-area-layers` -> `6.00`
- `hyperopen.views.l2-orderbook-view/normalize-trade` -> `3.00`
- `hyperopen.views.account-info.projections.orders/normalize-order-history-row` -> `5.00`
- `hyperopen.runtime.action-adapters/navigate` -> `2.00`
- `hyperopen.views.trading-chart.core/chart-canvas` -> `6.00`
- `hyperopen.api.trading/sign-and-post-agent-action!` -> `12.00`
- `hyperopen.portfolio.actions/returns-benchmark-candle-request` -> no longer appears above threshold in the module report

Two touched modules still contain unrelated CRAP hotspots that were outside the scoped bead list:

- `src/hyperopen/portfolio/actions.cljs`: `normalize-portfolio-account-info-tab` (`35.63`) and `normalize-summary-time-range` (`31.00`)
- `src/hyperopen/views/account_info/projections/orders.cljs`: `normalize-open-order` (`46.00`)

Validation passed end to end: `npm run check`, `npm test`, `npm run test:websocket`, and `npm run coverage` all succeeded on the final tree. Governed browser QA also completed with full accounting across the required widths. The only recorded browser failures were unrelated `/portfolio` jank traces; for the targeted routes, `/trade` passed all six governed checks at every width, while `/api-wallets` and `/vaults/detail` were `BLOCKED` only on the interaction pass because the reviewed scenes had no focusable controls to traverse.

## Context and Orientation

This wave spans nine source modules and one new test namespace. The targeted functions fall into three groups.

The first group is low-risk alias normalization or small lookup logic: `/hyperopen/src/hyperopen/portfolio/actions.cljs`, `/hyperopen/src/hyperopen/vaults/adapters/webdata.cljs`, `/hyperopen/src/hyperopen/views/api_wallets_view.cljs`, `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs`, and `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`. These hotspots are mostly driven by repeated fallback branches and low direct coverage. The safest tactic is to extract tiny private helpers, add direct tests, and keep the public surface unchanged.

The second group is pure behavior-rich projection or action assembly: `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs` and `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`. These functions already have meaningful tests, so the main lever is to split one large pure function into smaller helpers without changing the returned data or effect ordering.

The final group is high-risk orchestration: `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` and `/hyperopen/src/hyperopen/api/trading.cljs`. `chart-canvas` owns chart lifecycle and chart-runtime side effects; `sign-and-post-agent-action!` owns async retry, signing, posting, and agent-session invalidation. These need decomposition-first treatment and targeted regression reinforcement before any browser or CRAP verification.

The main direct test surfaces are:

- `/hyperopen/test/hyperopen/portfolio/actions_test.cljs`
- `/hyperopen/test/hyperopen/vaults/adapters/webdata_test.cljs`
- `/hyperopen/test/hyperopen/vaults/detail/transfer_test.cljs`
- `/hyperopen/test/hyperopen/views/api_wallets_view_test.cljs` (new)
- `/hyperopen/test/hyperopen/views/vaults/detail/chart_view_test.cljs`
- `/hyperopen/test/hyperopen/views/l2_orderbook_view_test.cljs`
- `/hyperopen/test/hyperopen/views/account_info/projections_test.cljs`
- `/hyperopen/test/hyperopen/views/account_info/tabs/order_history_test.cljs`
- `/hyperopen/test/hyperopen/runtime/action_adapters_test.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/core_test.cljs`
- `/hyperopen/test/hyperopen/api/trading/sign_and_submit_test.cljs`
- `/hyperopen/test/hyperopen/api/trading/session_invalidation_test.cljs`

Because this wave touches UI code under `/hyperopen/src/hyperopen/views/**`, the frontend runtime and browser-QA policy applies. Final signoff must explicitly account for all six browser-QA passes across widths `375`, `768`, `1280`, and `1440` for `/API`, `/trade`, and `/vaults/<address>`.

## Plan of Work

First, reduce the lowest-risk hotspots that are mostly lookup or alias-normalizer logic. In `/hyperopen/src/hyperopen/portfolio/actions.cljs`, replace the `returns-benchmark-candle-request` `case` tree with a private lookup map keyed by normalized summary range. In `/hyperopen/src/hyperopen/vaults/adapters/webdata.cljs`, extract small private readers for spot-balance aliases and fill aliases so `balances` and `fill-row` become thin composition layers. In `/hyperopen/src/hyperopen/views/api_wallets_view.cljs`, add direct render coverage for `modal-view` and only extract tiny body/label helpers if that meaningfully reduces branching. In `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs`, add direct coverage for `chart-series-area-layers`. In `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`, keep `normalize-trade` local and reduce repeated alias fallbacks with at most a tiny private helper.

Second, decompose the two pure but branch-heavy functions. In `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs`, split `normalize-order-history-row` into pure helpers for source-map selection, status/time extraction, trigger extraction, numeric parsing, and derived fields such as `filled-size` and `order-value`. In `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`, extract route-effect assembly helpers while keeping `navigate` as the single ordered concatenation point so effect ordering remains explicit and testable.

Third, decompose the two highest-risk orchestration seams. In `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, move `chart-canvas` mount, update, unmount, and visible-range coordination into top-level private helpers while preserving both public arities. In `/hyperopen/src/hyperopen/api/trading.cljs`, split `sign-and-post-agent-action!` into option normalization, session-availability checks, response classification, retry sequencing, and invalidation/persistence helpers without changing the public trading seams or agent-session semantics.

Finally, rerun targeted tests, required gates, fresh coverage, module-scoped CRAP reports, and governed browser QA accounting. Update this plan with measured outcomes and close or update the child beads based on the post-change report rather than assumption.

## Concrete Steps

Run these commands from `/hyperopen` while iterating:

1. Rebaseline coverage and hotspot list:

       npm run coverage
       bb tools/crap_report.clj --scope src --top-functions 25 --top-modules 10 --format json

2. Run targeted tests during implementation:

       npm test -- --namespace hyperopen.portfolio.actions-test
       npm test -- --namespace hyperopen.vaults.adapters.webdata-test
       npm test -- --namespace hyperopen.vaults.detail.transfer-test
       npm test -- --namespace hyperopen.views.api-wallets-view-test
       npm test -- --namespace hyperopen.views.vaults.detail.chart-view-test
       npm test -- --namespace hyperopen.views.l2-orderbook-view-test
       npm test -- --namespace hyperopen.views.account-info.projections-test
       npm test -- --namespace hyperopen.views.account-info.tabs.order-history-test
       npm test -- --namespace hyperopen.runtime.action-adapters-test
       npm test -- --namespace hyperopen.views.trading-chart.core-test
       npm test -- --namespace hyperopen.api.trading.sign-and-submit-test
       npm test -- --namespace hyperopen.api.trading.session-invalidation-test

3. Run required repository gates after all code changes:

       npm run check
       npm test
       npm run test:websocket
       npm run coverage

4. Verify the touched hotspot modules:

       bb tools/crap_report.clj --module src/hyperopen/portfolio/actions.cljs
       bb tools/crap_report.clj --module src/hyperopen/vaults/adapters/webdata.cljs
       bb tools/crap_report.clj --module src/hyperopen/views/api_wallets_view.cljs
       bb tools/crap_report.clj --module src/hyperopen/views/vaults/detail/chart_view.cljs
       bb tools/crap_report.clj --module src/hyperopen/views/l2_orderbook_view.cljs
       bb tools/crap_report.clj --module src/hyperopen/views/account_info/projections/orders.cljs
       bb tools/crap_report.clj --module src/hyperopen/runtime/action_adapters.cljs
       bb tools/crap_report.clj --module src/hyperopen/views/trading_chart/core.cljs
       bb tools/crap_report.clj --module src/hyperopen/api/trading.cljs

5. Record browser-QA accounting for `/API`, `/trade`, and `/vaults/<address>` with all six passes and four widths explicitly marked `PASS`, `FAIL`, or `BLOCKED`.

## Validation and Acceptance

Acceptance for this wave is complete only when all of the following are true:

- each of the ten child-bead hotspot functions either no longer appears above the default CRAP threshold or has been removed in favor of a narrower seam;
- `npm run check`, `npm test`, and `npm run test:websocket` pass;
- fresh `npm run coverage` completes successfully;
- the module-scoped CRAP reports for the touched files show the expected reduction;
- UI-facing changes have explicit browser-QA accounting for `/API`, `/trade`, and `/vaults/<address>` across visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at widths `375`, `768`, `1280`, and `1440`;
- `bd` status is updated for the epic and child beads based on the measured post-change outcome.

## Idempotence and Recovery

The changes in this wave are source-local and safe to rerun. The coverage and CRAP-report commands are read-only with respect to tracked source and can be repeated at any point. If a decomposition breaks behavior, rerun the nearest targeted namespace first, restore only the last touched helper structure in that file, and retry in smaller steps. Do not widen public surfaces merely to make the refactor easier; prefer extracting private helpers inside the same namespace.

## Artifacts and Notes

Baseline artifacts captured before source edits:

    npm run coverage
      Ran 2531 tests containing 13393 assertions. 0 failures, 0 errors.
      Ran 398 tests containing 2271 assertions. 0 failures, 0 errors.
      Statements 90.7%, Branches 68.57%, Functions 85.39%, Lines 90.7%.

    bb tools/crap_report.clj --scope src --top-functions 25 --top-modules 10 --format json
      project-crapload: 818.4438165881919
      crappy-functions: 42
      top targeted hotspots:
        hyperopen.vaults.adapters.webdata/balances -> 92.22815813265393
        hyperopen.views.api-wallets-view/modal-view -> 91.94472865086418
        hyperopen.views.trading-chart.core/chart-canvas -> 90.16059933342314
        hyperopen.api.trading/sign-and-post-agent-action! -> 90.0
        hyperopen.views.vaults.detail.chart-view/chart-series-area-layers -> 86.57937065968041
        hyperopen.runtime.action-adapters/navigate -> 72.0
        hyperopen.vaults.adapters.webdata/fill-row -> 61.82213077274805
        hyperopen.views.account-info.projections.orders/normalize-order-history-row -> 61.324419
        hyperopen.portfolio.actions/returns-benchmark-candle-request -> 60.00874635568515
        hyperopen.views.l2-orderbook-view/normalize-trade -> 57.898323615160336

Final validation artifacts:

    npm run check
      PASS

    npm test
      Ran 2552 tests containing 13512 assertions. 0 failures, 0 errors.

    npm run test:websocket
      Ran 398 tests containing 2271 assertions. 0 failures, 0 errors.

    npm run coverage
      Ran 2552 tests containing 13512 assertions. 0 failures, 0 errors.
      Ran 398 tests containing 2271 assertions. 0 failures, 0 errors.
      Statements 91.1%, Branches 68.68%, Functions 85.61%, Lines 91.1%.

    Module CRAP verification
      src/hyperopen/portfolio/actions.cljs
        hyperopen.portfolio.actions/returns-benchmark-candle-request -> no longer above threshold
        unrelated remaining hotspots:
          hyperopen.portfolio.actions/normalize-portfolio-account-info-tab -> 35.63
          hyperopen.portfolio.actions/normalize-summary-time-range -> 31.00
      src/hyperopen/vaults/adapters/webdata.cljs
        hyperopen.vaults.adapters.webdata/balances -> 5.00
        hyperopen.vaults.adapters.webdata/fill-row -> 4.00
      src/hyperopen/views/api_wallets_view.cljs
        hyperopen.views.api-wallets-view/modal-view -> 2.00
      src/hyperopen/views/vaults/detail/chart_view.cljs
        hyperopen.views.vaults.detail.chart-view/chart-series-area-layers -> 6.00
      src/hyperopen/views/l2_orderbook_view.cljs
        hyperopen.views.l2-orderbook-view/normalize-trade -> 3.00
      src/hyperopen/views/account_info/projections/orders.cljs
        hyperopen.views.account-info.projections.orders/normalize-order-history-row -> 5.00
        unrelated remaining hotspot:
          hyperopen.views.account-info.projections.orders/normalize-open-order -> 46.00
      src/hyperopen/runtime/action_adapters.cljs
        hyperopen.runtime.action-adapters/navigate -> 2.00
      src/hyperopen/views/trading_chart/core.cljs
        hyperopen.views.trading-chart.core/chart-canvas -> 6.00
      src/hyperopen/api/trading.cljs
        hyperopen.api.trading/sign-and-post-agent-action! -> 12.00
        note:
          hyperopen.api.trading/post-signed-action! now reports 30.00 and is not marked crappy because the threshold is strictly greater than 30.00

    Governed browser QA
      command:
        npm run qa:design-ui -- --changed-files src/hyperopen/views/api_wallets_view.cljs,src/hyperopen/views/l2_orderbook_view.cljs,src/hyperopen/views/vaults/detail/chart_view.cljs,src/hyperopen/views/trading_chart/core.cljs --manage-local-app
      summary:
        /Users/barry/.codex/worktrees/56fd/hyperopen/tmp/browser-inspection/design-review-2026-03-19T23-02-10-345Z-75a70138/summary.json
      overall state:
        FAIL
      targeted route accounting:
        governed route `/api-wallets` (requested `/API`):
          375 -> visual PASS, native-control PASS, styling-consistency PASS, interaction BLOCKED, layout-regression PASS, jank-perf PASS
          768 -> visual PASS, native-control PASS, styling-consistency PASS, interaction BLOCKED, layout-regression PASS, jank-perf PASS
          1280 -> visual PASS, native-control PASS, styling-consistency PASS, interaction BLOCKED, layout-regression PASS, jank-perf PASS
          1440 -> visual PASS, native-control PASS, styling-consistency PASS, interaction BLOCKED, layout-regression PASS, jank-perf PASS
        `/trade`:
          375 -> visual PASS, native-control PASS, styling-consistency PASS, interaction PASS, layout-regression PASS, jank-perf PASS
          768 -> visual PASS, native-control PASS, styling-consistency PASS, interaction PASS, layout-regression PASS, jank-perf PASS
          1280 -> visual PASS, native-control PASS, styling-consistency PASS, interaction PASS, layout-regression PASS, jank-perf PASS
          1440 -> visual PASS, native-control PASS, styling-consistency PASS, interaction PASS, layout-regression PASS, jank-perf PASS
        governed route `/vaults/detail` (requested `/vaults/<address>`):
          375 -> visual PASS, native-control PASS, styling-consistency PASS, interaction BLOCKED, layout-regression PASS, jank-perf PASS
          768 -> visual PASS, native-control PASS, styling-consistency PASS, interaction BLOCKED, layout-regression PASS, jank-perf PASS
          1280 -> visual PASS, native-control PASS, styling-consistency PASS, interaction BLOCKED, layout-regression PASS, jank-perf PASS
          1440 -> visual PASS, native-control PASS, styling-consistency PASS, interaction BLOCKED, layout-regression PASS, jank-perf PASS
      unrelated recorded issues:
        /portfolio review-1280 -> jank-perf FAIL, long task 194.0ms
        /portfolio review-1440 -> jank-perf FAIL, long task 182.0ms

## Interfaces and Dependencies

The following public interfaces must remain stable at the end of this wave:

- `/hyperopen/src/hyperopen/portfolio/actions.cljs`
  `returns-benchmark-candle-request`
- `/hyperopen/src/hyperopen/vaults/adapters/webdata.cljs`
  `fills`
  `balances`
- `/hyperopen/src/hyperopen/views/api_wallets_view.cljs`
  `api-wallets-view`
  `route-view`
- `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs`
  existing chart-section and fallback SVG behavior
- `/hyperopen/src/hyperopen/views/l2_orderbook_view.cljs`
  `normalize-trade`
- `/hyperopen/src/hyperopen/views/account_info/projections/orders.cljs`
  `normalize-order-history-row`
- `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`
  `navigate`
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`
  `chart-canvas`
- `/hyperopen/src/hyperopen/api/trading.cljs`
  `submit-order!`
  `cancel-order!`
  `submit-vault-transfer!`

Dependencies and seams to preserve:

- route/effect ordering continues to be validated by `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`
- chart lifecycle still flows through `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/runtime_state.cljs`
- agent-session behavior continues to flow through `/hyperopen/src/hyperopen/wallet/agent_session.cljs` and the existing trading API tests
- API-wallet view state continues to be driven by `/hyperopen/src/hyperopen/views/api_wallets/vm.cljs` and `/hyperopen/src/hyperopen/api_wallets/domain/policy.cljs`

Revision note: created on 2026-03-19 after a fresh `npm run coverage` and live CRAP rebaseline confirmed that the ten open child beads under `hyperopen-ma0f` still match the current top hotspot set exactly.
