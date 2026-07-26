# Refactor Open Order Chart Overlays

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

Tracked work: `hyperopen-sces`.

## Purpose / Big Picture

The open order chart overlay renderer currently lives mostly in one namespace, `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs`. That file owns chart lifecycle, order text shaping, TP/SL intent detection, overlap layout, DOM row creation, DOM patching, repaint subscriptions, and sidecar state. The result is harder to review and harder for agents to change safely.

After this refactor, the user-visible chart behavior should be unchanged: open order price lines render at the same chart coordinates, TP/SL badges stack the same way, inline cancel dispatches the same order exactly once, retained rows patch in place, and sync/clear entrypoints keep their existing arities. The improvement is internal: the root namespace becomes a narrow lifecycle facade, while focused child namespaces own support helpers, presentation/layout policy, and row DOM mutation.

## Progress

- [x] (2026-04-19 16:59Z) Created and claimed `bd` task `hyperopen-sces`.
- [x] (2026-04-19 17:00Z) Audited the current open order overlay namespace and the already-split position overlay namespaces.
- [x] (2026-04-19 17:00Z) Recorded the planned split boundaries and validation gates in this active ExecPlan.
- [x] (2026-04-19 17:11Z) Established the structural RED check after the parent removed the namespace-size exception: `npm run lint:namespace-sizes` failed on the 803-line root namespace before extraction.
- [x] (2026-04-19 17:03Z) Added the focused fake-DOM regression test for retained row handler patching before production edits.
- [x] (2026-04-19 17:11Z) Extracted support, presentation/layout, and rows namespaces under `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays/`.
- [x] (2026-04-19 17:11Z) Narrowed `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` to lifecycle orchestration and public compatibility entrypoints.
- [x] (2026-04-19 17:11Z) Ran focused overlay tests and namespace-size lint after extraction. Focused command reported `Ran 3291 tests containing 17957 assertions. 0 failures, 0 errors.` Namespace-size lint passed.
- [x] (2026-04-19 17:14Z) Ran required repository gates: `npm run check`, `npm test`, and `npm run test:websocket` all passed.
- [x] (2026-04-19 17:14Z) Accounted for governed browser QA: `npm run qa:design-ui -- --targets trade-route --manage-local-app` completed with `reviewOutcome`/`state` `PASS` for `trade-route` at widths 375, 768, 1280, and 1440 across all six passes. `npm run browser:cleanup` completed with `"ok": true`.
- [x] (2026-04-19 17:14Z) Moved this plan to `docs/exec-plans/completed/` after validation and closed `hyperopen-sces`.

## Surprises & Discoveries

- Observation: `npm run lint:namespace-sizes` currently passes because `dev/namespace_size_exceptions.edn` has an exact exception for `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` with `:max-lines 803`.
  Evidence: `npm run lint:namespace-sizes` passed, and the exception entry names that file with `:max-lines 803`.
- Observation: An initial attempt to inspect test runner help via `npm test -- --help` compiled the test build but failed at runtime because Node could not resolve `lucide/dist/esm/icons/external-link.js`.
  Evidence: Node raised `Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'` from `out/test.js`.
- Observation: The retained-row cancel-handler characterization test passes against the current production code, so it did not provide a behavioral RED failure.
  Evidence: `npx shadow-cljs --force-spawn --config-merge '{:builds {:test {:ns-regexp "^hyperopen\\.views\\.trading-chart\\.utils\\.chart-interop\\.open-order-overlays-test$"}}}' compile test && node out/test.js` completed with `Ran 3291 tests containing 17957 assertions. 0 failures, 0 errors.` The generated test runner still executed the broader test suite despite the namespace regexp.
- Observation: The parent precondition was present in this worktree before extraction: with the exception already removed, `npm run lint:namespace-sizes` failed on `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` at 803 lines.
  Evidence: `npm run lint:namespace-sizes` reported `[missing-size-exception] ... namespace has 803 lines`.
- Observation: After extraction, no new or modified open order overlay namespace exceeds the 500-line guardrail.
  Evidence: `wc -l` reported root 208 lines, support 95 lines, presentation 287 lines, rows 263 lines; `npm run lint:namespace-sizes` passed.

## Decision Log

- Decision: Keep `open_order_overlays.cljs` as the public facade and lifecycle owner instead of replacing the root namespace.
  Rationale: Production callers go through `chart_interop.cljs`, and tests directly redefine root vars `render-overlays!`, `*schedule-overlay-repaint-frame!*`, and `*cancel-overlay-repaint-frame!*`. Keeping those root seams avoids public API and test seam churn.
  Date/Author: 2026-04-19 / Codex
- Decision: Mirror the position overlay split with `support.cljs`, `presentation.cljs`, and `rows.cljs`; no `liquidation_drag.cljs` equivalent is needed.
  Rationale: Open order overlays have no drag interaction. Their separable responsibilities are generic support helpers, pure presentation/layout decisions, and DOM row ownership.
  Date/Author: 2026-04-19 / Codex
- Decision: Use the namespace-size exception removal as the structural RED signal.
  Rationale: The current behavior tests already cover the user-visible overlay contract. Removing the temporary size exception should fail before extraction and pass after the root facade is reduced below the guardrail.
  Date/Author: 2026-04-19 / Codex
- Decision: Add one focused retained-row cancel-handler regression test.
  Rationale: Existing tests prove retained DOM rows and patched text, but they do not prove that a retained cancel button uses the latest `on-cancel-order` callback and latest order after a same-key sync.
  Date/Author: 2026-04-19 / Codex
- Decision: When `sync-open-order-overlays!` cancels a pending repaint before writing refreshed overlay state, carry forward the sidecar state after cancellation instead of the pre-cancel snapshot.
  Rationale: This mirrors the existing position overlay lifecycle and avoids restoring a stale `:repaint-frame-id` immediately after cancellation while keeping repaint decisions in the lifecycle facade.
  Date/Author: 2026-04-19 / Codex

## Outcomes & Retrospective

Implementation split is complete. The root namespace is now a lifecycle facade while `support`, `presentation`, and `rows` own sidecar/DOM helpers, pure presentation/layout, and row DOM mutation respectively. Root line count is 208; child namespace line counts are support 95, presentation 287, and rows 263.

Validation passed:

- Focused open order overlay test command: `Ran 3291 tests containing 17957 assertions. 0 failures, 0 errors.`
- `npm run lint:namespace-sizes`: passed.
- `npm run lint:namespace-boundaries`: passed.
- `npm run check`: passed.
- `npm test`: `Ran 3291 tests containing 17957 assertions. 0 failures, 0 errors.`
- `npm run test:websocket`: `Ran 449 tests containing 2701 assertions. 0 failures, 0 errors.`
- `npm run qa:design-ui -- --targets trade-route --manage-local-app`: PASS for all six governed passes at 375, 768, 1280, and 1440. Residual blind spot is limited to the design-review tool's standard note that hover, active, disabled, and loading states require targeted route actions when they are not present by default.

No blockers remain. No remote sync, commit, rebase, or push was performed.

## Context and Orientation

The public open order overlay entrypoints are `sync-open-order-overlays!` and `clear-open-order-overlays!`, exposed through `src/hyperopen/views/trading_chart/utils/chart_interop.cljs`. The root implementation namespace is `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs`.

An "open order overlay" means a chart-local HTML layer that draws a dashed horizontal line at an order price and places a badge near the middle of the chart. A badge contains an intent chip such as `ORD`, `TP`, or `SL`, a label with order size and price, and an inline cancel button. The overlay is not rendered by Replicant; it is imperative DOM owned by the chart interop layer.

A "sidecar" means a `js/WeakMap` keyed by the chart object. The sidecar stores the overlay root DOM node, chart and series handles, row DOM cache, current inputs, and repaint subscription data without mutating the chart object.

The analogous position overlay implementation is already split into:

- `src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` for lifecycle orchestration.
- `src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays/support.cljs` for sidecar state, numeric helpers, and defensive DOM helpers.
- `src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays/presentation.cljs` for pure text, color, and geometry policy.
- `src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays/rows.cljs` for imperative DOM row creation and patching.

This refactor should follow that shape for open order overlays.

## Plan of Work

First, establish the safety net. Remove the `open_order_overlays.cljs` entry from `dev/namespace_size_exceptions.edn` and run `npm run lint:namespace-sizes`. Before extraction, the command should fail because the root file has 803 lines. Add a fake-DOM test to `test/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays_test.cljs` named `open-order-overlays-retained-row-updates-cancel-handler-and-order-test`. The test should render an order, capture the row and cancel button, sync again with the same `coin::oid` but a changed size, changed price, and a different cancel callback, assert the row and button are retained, assert the text is patched, and assert clicking dispatches the updated order through the updated callback.

Second, create `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays/support.cljs`. Move the WeakMap sidecar and low-level helpers there: `overlay-state`, `set-overlay-state!`, a new `delete-overlay-state!`, `finite-number?`, `non-negative-number`, `parse-order-number`, `clamp`, `apply-inline-style!`, `invoke-method`, `clear-children!`, `create-text-node!`, `set-text-node-value!`, and `resolve-document`. The namespace may require `hyperopen.views.account-info.shared` only for numeric parsing. The root facade and child namespaces should call these helpers through the `support` alias.

Third, create `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays/presentation.cljs`. Move color constants, order intent detection, order labels, formatting, row presentation maps, and overlap layout there. The root renderer should call `presentation/order-intent`, `presentation/layout-overlapping-badges`, and other public presentation helpers. Keep purely local helpers private inside `presentation.cljs`. This namespace should require `clojure.string`, `hyperopen.views.account-info.shared`, and `support`.

Fourth, create `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays/rows.cljs`. Move overlay root creation, overlay root mounting, row key generation, row DOM creation, row DOM patching, inline cancel event wiring, and stale row removal there. Expose `ensure-overlay-root!`, `order-row-key`, `build-overlay-row!` if needed, `patch-overlay-row!` if needed, and preferably a single `patch-visible-order-rows!` helper that takes the root, document, current cache, laid-out rows, callback, and formatter options, then returns the next row cache. The inline cancel button must keep the pointer-first dispatch behavior and must not reset its once-only dispatch atom on every patch.

Fifth, narrow `open_order_overlays.cljs`. It should require `support`, `presentation`, and `rows`; retain the public dynamic repaint vars; retain `render-overlays!`, `clear-open-order-overlays!`, and `sync-open-order-overlays!`; retain subscription scheduling; and keep the identity short-circuit in `sync-open-order-overlays!`. `render-overlays!` should project visible orders to coordinates, call `presentation/layout-overlapping-badges`, and delegate row reconciliation to `rows/patch-visible-order-rows!`.

Sixth, validate. Run the focused open-order overlay test command if dependency state allows it, then `npm run lint:namespace-sizes`. After focused checks pass, run `npm run check`, `npm test`, and `npm run test:websocket`. Because the code lives under `src/hyperopen/views/**`, browser QA must be accounted for. Since the intended change is behavior-preserving and the fake-DOM tests cover the overlay contract, a full design review can be skipped only if recorded as `BLOCKED` with the concrete local dependency/tooling reason; otherwise run `npm run qa:design-ui -- --targets trade-route --manage-local-app` and record the PASS/FAIL/BLOCKED outcome.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/9a13/hyperopen`.

1. Remove the `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` entry from `dev/namespace_size_exceptions.edn`.

2. Run:

       npm run lint:namespace-sizes

   Expected before extraction: failure naming `src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs` because the namespace exceeds the default size guardrail.

3. Add the retained-row cancel-handler regression test to `test/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays_test.cljs`.

4. Run the focused overlay test build:

       npx shadow-cljs --force-spawn --config-merge '{:builds {:test {:ns-regexp "^hyperopen\\.views\\.trading-chart\\.utils\\.chart-interop\\.open-order-overlays-test$"}}}' compile test && node out/test.js

   Expected before implementation: if the test is written against the existing bug gap, it should fail because it expects a handler/order patch behavior not yet explicitly guaranteed. If the existing code already passes the new behavior, record that in `Surprises & Discoveries` and proceed using the namespace-size RED signal.

5. Extract `support.cljs`, update root calls to use `support/*`, and rerun the focused test command.

6. Extract `presentation.cljs`, update root calls to use `presentation/*`, and rerun the focused test command.

7. Extract `rows.cljs`, delegate row cache reconciliation from root, and rerun the focused test command.

8. Run:

       npm run lint:namespace-sizes

   Expected after extraction: pass without the open order overlay exception.

9. Run required gates:

       npm run check
       npm test
       npm run test:websocket

10. Account for browser QA:

       npm run qa:design-ui -- --targets trade-route --manage-local-app

    If local dependency/tooling state blocks this command, record the concrete blocker and classify browser QA as `BLOCKED`, not silently skipped.

## Validation and Acceptance

Acceptance criteria:

- `chart_interop/sync-open-order-overlays!` and `chart_interop/clear-open-order-overlays!` keep their existing arities and option keys.
- Root `open_order_overlays.cljs` still exposes `render-overlays!`, `*schedule-overlay-repaint-frame!*`, and `*cancel-overlay-repaint-frame!*` so existing tests can redefine the repaint seams.
- Open order rows render the same labels and TP/SL chips as before.
- Inline cancel dispatches exactly once for pointerdown plus click on the same retained button.
- A retained row with the same `coin::oid` patches text, coordinates, callback, and order payload without replacing the row DOM.
- Subscription repaint callbacks coalesce into one frame.
- `dev/namespace_size_exceptions.edn` no longer needs an exception for `open_order_overlays.cljs`.
- No new source namespace introduced by this refactor exceeds the namespace-size guardrail.
- `npm run check`, `npm test`, and `npm run test:websocket` pass, or any failure is recorded with a concrete external blocker.
- Browser QA for `trade-route` is recorded as PASS/FAIL/BLOCKED with all six governed passes and widths accounted for, or the plan records why the local run could not proceed.

## Idempotence and Recovery

The refactor is additive until the root namespace is narrowed. If an extraction step fails, keep the new child namespace but restore root requires/calls from git diff hunks rather than deleting unrelated work. `clear-open-order-overlays!` should remain safe to call repeatedly; it must cancel pending repaint frames, unsubscribe chart listeners, remove the overlay root from its parent, clear children, and delete the sidecar state.

No remote synchronization is part of this plan. Do not run `git pull --rebase` or `git push` unless explicitly requested in a later user message.

## Artifacts and Notes

Initial evidence:

       npm run lint:namespace-sizes
       Namespace size check passed.

       dev/namespace_size_exceptions.edn includes:
       {:path "src/hyperopen/views/trading_chart/utils/chart_interop/open_order_overlays.cljs"
        :owner "platform"
        :reason "Architecture remediation wave baseline oversized namespace pending split or tightened facade."
        :max-lines 803
        :retire-by "2026-06-30"}

       npm test -- --help
       Error: Cannot find module 'lucide/dist/esm/icons/external-link.js'

## Interfaces and Dependencies

The new namespaces must use these names:

- `hyperopen.views.trading-chart.utils.chart-interop.open-order-overlays.support`
- `hyperopen.views.trading-chart.utils.chart-interop.open-order-overlays.presentation`
- `hyperopen.views.trading-chart.utils.chart-interop.open-order-overlays.rows`

The root namespace must require them as `support`, `presentation`, and `rows`. Child namespaces must not require the root namespace. Dependency direction should be root to children, rows to support and presentation, presentation to support and shared formatting, and support to shared numeric parsing.

Plan revision note, 2026-04-19: Initial plan created because this is active multi-agent refactor work and the repository requires active ExecPlans for significant refactors.
