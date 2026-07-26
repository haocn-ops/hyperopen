# Eliminate Tooltip Hover Lag From Account Summary Render Work

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`. The live issue tracked by `bd` for this work is `hyperopen-bztp`, which is ready to close because the implementation and required validation gates passed.

## Purpose / Big Picture

After this change, hovering the account summary labels on the trade screen should feel immediate instead of waiting behind a long main-thread stall. The tooltip component in `/hyperopen/src/hyperopen/views/account_equity_view.cljs` already uses CSS-only hover behavior, so the user-visible goal is to remove the expensive render work that delays paint. A person validating this change should be able to open the trade screen in unified account mode, move the pointer over the “Unified Account Ratio” or “Unified Account Leverage” labels, and see the tooltip appear without the 800 ms to 1200 ms interaction delays captured in `/Users/barry/Downloads/Trace-20260313T201748.json`.

## Progress

- [x] (2026-03-13 20:37Z) Claimed `bd` issue `hyperopen-bztp` and confirmed the trace points to main-thread render saturation rather than tooltip event-handler time.
- [x] (2026-03-13 20:41Z) Authored this active ExecPlan in `/hyperopen/docs/exec-plans/active/2026-03-13-tooltip-hover-render-lag.md`.
- [x] (2026-03-13 21:02Z) Updated `/hyperopen/src/hyperopen/views/account_equity_view.cljs` to reuse `/hyperopen/src/hyperopen/views/account_info/derived_cache.cljs` and to memoize account-equity metrics by the relevant state slices instead of by the full app state.
- [x] (2026-03-13 21:05Z) Updated `/hyperopen/src/hyperopen/views/trade_view.cljs` to compute account-equity metrics once per render and only build the mobile account-summary overlay when the account surface is actually selected.
- [x] (2026-03-13 21:09Z) Added regression tests in `/hyperopen/test/hyperopen/views/account_equity_view_test.cljs` and `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` for memoization and trade-view account-summary composition.
- [x] (2026-03-13 21:22Z) Installed lockfile dependencies with `npm ci` so the standard npm validation scripts could run in this worktree.
- [x] (2026-03-13 21:29Z) Ran `npm test` successfully: 2374 tests, 12447 assertions, 0 failures, 0 errors.
- [x] (2026-03-13 21:31Z) Ran `npm run test:websocket` successfully: 385 tests, 2187 assertions, 0 failures, 0 errors.
- [x] (2026-03-13 21:32Z) Ran `npm run check` successfully, including docs guardrails, mutation/crap checks, lint checks, and app/portfolio/test compiles.

## Surprises & Discoveries

- Observation: The tooltip itself is not delayed by JavaScript. The trace showed `pointerover` interactions with roughly 956 ms queue time and roughly 0.012 ms handler time, which means the browser was waiting for unrelated main-thread work to finish.
  Evidence: `EventTiming` entries in `/Users/barry/Downloads/Trace-20260313T201748.json` reported `queue_ms` near `956.052` and `handler_ms` near `0.012`.

- Observation: The dominant app hotspot is unified account metric derivation in `/hyperopen/src/hyperopen/views/account_equity_view.cljs`, not websocket projection flush logic.
  Evidence: CPU sample aggregation from the trace concentrated most inclusive samples under `derive-account-equity-metrics`, `unified-clearinghouse-state-records`, `unified-account-ratio*`, `isolated-margin-by-token`, and `position-quote-token`.

- Observation: `/hyperopen/src/hyperopen/views/trade_view.cljs` builds both the desktop account summary and the hidden mobile account summary during one render pass.
  Evidence: The desktop path calls `account-equity-view/account-equity-view` inside the order-entry column, and the hidden mobile overlay also calls `mobile-account-surface`, which itself calls `account-equity-view/account-equity-view`.

- Observation: This worktree did not have local npm dependencies installed, so the first attempt to run `npm test` failed before validation started.
  Evidence: The initial script run reported `sh: shadow-cljs: command not found`, and `npm ci` fixed the environment so the standard scripts could run.

## Decision Log

- Decision: Treat this as a render-path optimization rather than a tooltip implementation change.
  Rationale: The tooltip code is CSS-only and the trace shows interaction delay before paint, not inside hover handlers.
  Date/Author: 2026-03-13 / Codex

- Decision: Reuse the existing derived-data cache in `/hyperopen/src/hyperopen/views/account_info/derived_cache.cljs` instead of introducing a new cache system first.
  Rationale: The repository already memoizes balance rows and positions for the account-info view, and reusing that seam reduces duplication while keeping the change scoped.
  Date/Author: 2026-03-13 / Codex

- Decision: Remove hidden duplicate account-surface construction in the trade view instead of trying to make duplicated rendering cheap enough.
  Rationale: The trace indicates the account summary is expensive enough that paying for two copies per frame is avoidable waste; conditional rendering is simpler than optimizing two identical paths.
  Date/Author: 2026-03-13 / Codex

- Decision: Add a dedicated account-equity metrics cache keyed by `:webdata2`, `:spot`, `:account`, `:perp-dex-clearinghouse`, and `:asset-selector :market-by-key`.
  Rationale: `trade_view` creates a derived `state*` map every render, so caching by the whole root state would miss constantly. Caching by the relevant stable subtrees allows unrelated orderbook or UI updates to reuse the previous account-equity result.
  Date/Author: 2026-03-13 / Codex

- Decision: Share one precomputed `account-equity-metrics` value across the desktop and mobile trade-view account-summary render paths.
  Rationale: This keeps the rendering contract stable while ensuring that the expensive metric derivation runs once per render even when two wrappers still need to render account-summary markup.
  Date/Author: 2026-03-13 / Codex

## Outcomes & Retrospective

The implementation met the original goal. `/hyperopen/src/hyperopen/views/account_equity_view.cljs` now reuses the existing balance-row and position memoization seam, and it adds a narrow memoization layer for the final account-equity metrics so unrelated store updates do not re-run the unified-account hot path. `/hyperopen/src/hyperopen/views/trade_view.cljs` now computes those metrics once per render and only builds the mobile account-summary overlay when the account surface is selected.

All required validation gates passed after installing the lockfile dependencies with `npm ci`. `npm test`, `npm run test:websocket`, and `npm run check` all completed successfully.

Overall complexity went down. The account-equity derivation path now follows the existing repository pattern for cached derived view data instead of reimplementing raw projections inside the view on every render, and the trade view now has a single explicit handoff point for shared account-equity metrics.

## Context and Orientation

The trade screen is composed by `/hyperopen/src/hyperopen/views/trade_view.cljs`. That file renders the chart, order book, order form, account info tables, and account equity summary. The account equity summary is built by `/hyperopen/src/hyperopen/views/account_equity_view.cljs`. In unified account mode, that file derives display metrics by walking clearinghouse state, balance rows, positions, and market metadata inside `derive-account-equity-metrics`.

In this repository, a “render loop” means the `requestAnimationFrame` callback installed in `/hyperopen/src/hyperopen/runtime/bootstrap.cljs`. Every store change schedules a frame, and the frame calls `render-app!` from `/hyperopen/src/hyperopen/app/bootstrap.cljs`, which rebuilds the top-level application view. If any view does too much synchronous work during render, pointer hover feedback is delayed until the frame completes.

The account-info area already has a memoization seam in `/hyperopen/src/hyperopen/views/account_info/derived_cache.cljs`. That file caches the results of `build-balance-rows` and `collect-positions` using identity checks on the source inputs. `/hyperopen/src/hyperopen/views/account_equity_view.cljs` currently bypasses that seam and calls `/hyperopen/src/hyperopen/views/account_info/projections.cljs` directly, so it recomputes heavy derived data during every app render.

The trace file `/Users/barry/Downloads/Trace-20260313T201748.json` captured the failing behavior. Its long `EventTiming` records match repeated long `requestAnimationFrame` tasks. CPU samples point most strongly at the account-equity render path and secondarily at trading-chart position-overlay work. This plan focuses first on the primary hotspot because it aligns directly with the hovered UI and dominates the samples.

## Plan of Work

First, `/hyperopen/src/hyperopen/views/account_equity_view.cljs` was changed so `derive-account-equity-metrics` now consumes `derived-cache/memoized-balance-rows` and `derived-cache/memoized-positions` instead of rebuilding those structures directly. A new `account-equity-metrics-cache` keyed by the relevant account-summary state slices now sits above that derivation and is resettable for tests.

Second, `/hyperopen/src/hyperopen/views/trade_view.cljs` was changed so `trade-view` computes `account-equity-view/account-equity-metrics` once and passes the resulting metrics into both the desktop and mobile account-summary render paths. The mobile overlay subtree is now wrapped in `when mobile-account-surface?`, so it is no longer built when the account surface is not selected.

Third, the regression coverage was updated. `/hyperopen/test/hyperopen/views/account_equity_view_test.cljs` now proves that the new memoization ignores unrelated root-state churn. `/hyperopen/test/hyperopen/views/app_shell_spacing_test.cljs` now proves that `trade-view` reuses one metrics snapshot for the desktop and mobile wrappers and no longer renders the mobile summary panel when that surface is not selected.

Finally, the required repo gates were run and passed.

## Concrete Steps

All commands in this section run from `/Users/barry/.codex/worktrees/c4c1/hyperopen`.

Inspect the current hotspot and affected files:

    rg -n "account-equity-view|mobile-account-surface|memoized-balance-rows|memoized-positions" src/hyperopen -S
    sed -n '1,260p' src/hyperopen/views/account_equity_view.cljs
    sed -n '1,260p' src/hyperopen/views/trade_view.cljs
    sed -n '1,120p' src/hyperopen/views/account_info/derived_cache.cljs

Run the full required gates:

    npm ci
    npm run check
    npm test
    npm run test:websocket

Observed success shape:

    npm test
    ...
    Ran 2374 tests containing 12447 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    ...
    Ran 385 tests containing 2187 assertions.
    0 failures, 0 errors.

    npm run check
    ...
    Docs check passed.
    ...
    [:app] Build completed.
    [:portfolio] Build completed.
    [:portfolio-worker] Build completed.
    [:test] Build completed.

If a gate fails, update the `Progress`, `Surprises & Discoveries`, and `Decision Log` sections before stopping.

## Validation and Acceptance

Acceptance is primarily behavioral. On a local dev build, open the trade screen with unified account data loaded and hover the account-summary labels that show tooltips. The tooltip should appear without the prior perceptible lag. The technical proof is now in place: account-summary derived data is memoized by the relevant state slices, the trade view reuses one metrics snapshot per render, and the mobile overlay is not built unless the account surface is selected.

`npm test`, `npm run test:websocket`, and `npm run check` all passed. The final implementation preserves the existing account-summary values and tooltip text while reducing unnecessary synchronous render work.

## Idempotence and Recovery

The code edits in this plan are safe to apply incrementally and rerun. Re-running the validation commands is safe. If a future refactor causes rendering regressions, the safe rollback path is to revert the memoization layer in `/hyperopen/src/hyperopen/views/account_equity_view.cljs` and the shared-metrics wiring in `/hyperopen/src/hyperopen/views/trade_view.cljs` together so the view does not mix old and new calling conventions.

## Artifacts and Notes

Important baseline findings from `/Users/barry/Downloads/Trace-20260313T201748.json`:

    pointerover duration_ms=981.254 queue_ms=956.052 handler_ms=0.012
    repeated requestAnimationFrame callbacks in hyperopen.runtime.bootstrap.js ~= 820 ms to 905 ms
    top inclusive app samples: hyperopen.views.account_equity_view.js, hyperopen.views.trade_view.js, hyperopen.views.app_view.js

Important source locations before the fix:

    /hyperopen/src/hyperopen/runtime/bootstrap.cljs
      install-render-loop! schedules one render per animation frame.

    /hyperopen/src/hyperopen/views/account_equity_view.cljs
      derive-account-equity-metrics performs balance-row and position rebuilding during render.

    /hyperopen/src/hyperopen/views/trade_view.cljs
      trade-view constructs both desktop and hidden mobile account surfaces.

Important validation evidence after the fix:

    npm test
      Ran 2374 tests containing 12447 assertions.
      0 failures, 0 errors.

    npm run test:websocket
      Ran 385 tests containing 2187 assertions.
      0 failures, 0 errors.

    npm run check
      Docs check passed.
      [:app] Build completed.
      [:portfolio] Build completed.
      [:portfolio-worker] Build completed.
      [:test] Build completed.

## Interfaces and Dependencies

Use the existing modules already in the repository:

- `/hyperopen/src/hyperopen/views/account_info/derived_cache.cljs` for memoized balance rows and memoized positions.
- `/hyperopen/src/hyperopen/views/account_equity_view.cljs` as the single owner of account-summary metric derivation.
- `/hyperopen/src/hyperopen/views/trade_view.cljs` as the composition point that decides whether desktop or mobile account surfaces are built.

No new external library is needed. The final memoized account-equity layer stays in `/hyperopen/src/hyperopen/views/account_equity_view.cljs`, while lower-level balance-row and position caching still belongs to the shared account-info derived cache.

Revision note: Updated the plan after implementation to record the final caching design, the trade-view shared-metrics wiring, the dependency-install prerequisite, and the passing validation results before moving this ExecPlan to `completed`.
