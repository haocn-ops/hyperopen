# Refactor Trading Chart Candle Sync Policy Into a Pure Decision Seam

bd_issue: hyperopen-h0i

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

This plan executes `bd` issue `hyperopen-h0i`.

## Purpose / Big Picture

The trading chart used to decide whether candle updates should no-op, full-reset, update the tail candle, or append one new candle inside `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`. That put pure chart-sync policy inside the JavaScript interop facade and made a high-risk function (`infer-candle-sync-mode`) hard to test directly.

After this change, the candle comparison logic lives in a dedicated pure policy namespace, the runtime side-effect code lives in a dedicated executor namespace, and the public interop facade stays stable. A contributor can now verify the behavior by reading one small pure policy file, one small executor file, and focused tests that name each sync rule directly.

## Progress

- [x] (2026-03-11 01:30Z) Reviewed `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/QUALITY_SCORE.md`, and `/hyperopen/docs/RELIABILITY.md` for boundary, testing, and planning rules.
- [x] (2026-03-11 01:30Z) Audited `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and traced `infer-candle-sync-mode` through both `set-series-data!` and `set-volume-data!`.
- [x] (2026-03-11 01:30Z) Audited existing regression coverage in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/series_test.cljs` and confirmed the sync behavior was mostly tested indirectly rather than as a standalone policy.
- [x] (2026-03-11 01:29Z) Created and claimed `bd` task `hyperopen-h0i`.
- [x] (2026-03-11 01:38Z) Moved this plan into `/hyperopen/docs/exec-plans/active/` when implementation started.
- [x] (2026-03-11 01:41Z) Added `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/series_sync.cljs`, then rewired `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` into a thinner facade.
- [x] (2026-03-11 01:42Z) Added direct policy coverage in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy_test.cljs` and extended `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/series_test.cljs` with config-change and volume-reset regressions.
- [x] (2026-03-11 01:44Z) Installed missing npm dependencies with `npm install` after the first `npm test` run failed because `shadow-cljs` was unavailable on the local script path.
- [x] (2026-03-11 01:45Z) Ran `npm test` successfully (`2250` tests, `11749` assertions, `0` failures, `0` errors).
- [x] (2026-03-11 01:47Z) Ran `npm run check` successfully.
- [x] (2026-03-11 01:47Z) Ran `npm run test:websocket` successfully (`372` tests, `2128` assertions, `0` failures, `0` errors).
- [x] (2026-03-11 01:50Z) Ran `npm run coverage` successfully and regenerated `coverage/lcov.info` (`91.05%` statements, `85.97%` functions).
- [x] (2026-03-11 01:51Z) Ran `npm run crap:report` plus module-scoped CRAP reports and confirmed the old facade hotspot is gone; `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` now has `0` crappy functions and max CRAP `8.729`.
- [x] (2026-03-11 01:50Z) Performed manual browser QA on the live `/trade` route through browser-inspection, including timeframe change and chart-type round-trip checks, and captured artifacts under `/hyperopen/tmp/browser-inspection/inspect-2026-03-11T01-50-31-518Z-7a704f04/`.

## Surprises & Discoveries

- Observation: The same private decision function governed both the main price series path and the volume series path, so a misclassification would affect two visible chart surfaces at once.
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` called `infer-candle-sync-mode` from both `set-series-data!` and `set-volume-data!`.

- Observation: Existing tests already covered several important outcomes, but they asserted them indirectly through fake series method calls instead of directly naming the decision rules.
  Evidence: `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/series_test.cljs` covered repeated identity no-op, tail update, append, and non-tail mutation reset, but there was no dedicated `candle_sync_policy_test.cljs`.

- Observation: Incremental sync still recomputes transformed data and option inference from the full candle vector even when the final write becomes `.update`.
  Evidence: the refactored executor still computes `transformed-data`, `base-value`, and `price-format*` before applying the decision.

- Observation: This worktree did not have npm dependencies installed, so the first `npm test` run failed before compile actually started.
  Evidence: initial `npm test` output ended with `sh: shadow-cljs: command not found`; `npm install` resolved it immediately.

- Observation: After extraction, the facade module fell below the prior size and hotspot concerns without introducing a new crappy module.
  Evidence: line counts are now `356` for `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, `82` for `candle_sync_policy.cljs`, and `107` for `series_sync.cljs`; module-scoped CRAP reports show `0` crappy functions for both the facade and the new policy module.

## Decision Log

- Decision: Treat candle sync inference as a local domain-policy concern for the trading-chart bounded context, not as a JavaScript interop concern.
  Rationale: The logic is pure and deterministic: it compares two ordered candle snapshots and classifies a state transition. That aligns with the architecture rule that domain decisions stay pure and side effects stay in infrastructure/interpreter boundaries.
  Date/Author: 2026-03-11 / Codex

- Decision: Preserve the public facade in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, including `set-series-data!` and `set-volume-data!`.
  Rationale: The repository already uses the chart interop namespace as a stable seam. The refactor should reduce internal complexity without creating call-site churn in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` or tests that exercise the facade.
  Date/Author: 2026-03-11 / Codex

- Decision: Make the pure policy return an explicit decision map with both `:mode` and `:reason`, not only a bare keyword.
  Rationale: A single keyword is efficient but opaque. A small decision object gives humans and AI agents a traceable explanation for why a branch was chosen, and it lets tests assert both outcome and rationale.
  Date/Author: 2026-03-11 / Codex

- Decision: Move sync sidecar ownership into `series_sync.cljs` instead of leaving it in the facade.
  Rationale: Sidecar state is part of the executor/runtime concern, not part of the public interop API. Co-locating state with the executor makes the facade smaller and reduces the number of files needed to understand a sync write.
  Date/Author: 2026-03-11 / Codex

- Decision: Keep config-driven full resets as an executor-local decision (`:config-changed`) rather than pushing them into the raw candle comparison policy.
  Rationale: Candle comparison and chart configuration changes are separate reasons to rewrite the dataset. Mixing them would blur the pure policy boundary and make the policy less reusable and harder to test.
  Date/Author: 2026-03-11 / Codex

- Decision: Use browser-inspection against the real `/trade` route for manual QA instead of inventing a workbench-only path.
  Rationale: The workbench intentionally does not yet host the lifecycle-heavy trading chart, so live route QA is the only honest rendered verification path for this change.
  Date/Author: 2026-03-11 / Codex

## Outcomes & Retrospective

Implementation and validation are complete.

What changed:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` is now a thinner facade that delegates sync behavior instead of owning the candle comparison branch tree.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy.cljs` now owns the pure candle comparison policy and returns explicit `{:mode ... :reason ...}` decisions.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/series_sync.cljs` now owns sidecar state, config-driven reset decisions, and `.setData` versus `.update` execution.
- Direct tests now cover the policy branches explicitly, while existing facade tests still verify runtime behavior.

Complexity decreased overall. The previous 487-line facade dropped to 356 lines, the high-risk branch tree moved into an 82-line pure policy file, and the executor concern moved into a 107-line runtime file. The former hotspot no longer appears in the project top-functions report, and module-scoped CRAP reports show no crappy functions in either the facade or the extracted policy module.

## Context and Orientation

The chart update seam remains `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`. The high-risk area before this change was the private function `infer-candle-sync-mode`, which compared previous and next raw candles and returned one of four modes:

- `:noop` means the chart should not write any data.
- `:full-reset` means the chart should call `.setData` with the whole transformed dataset.
- `:update-last` means the chart should call `.update` with one transformed point for the existing last candle time bucket.
- `:append-last` means the chart should call `.update` with one transformed point for one new trailing candle.

After this refactor, the responsibilities are split this way:

- policy: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy.cljs`
- executor/runtime: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/series_sync.cljs`
- public facade: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`

In this plan:

- “policy” means pure comparison logic that accepts previous and next raw candles and returns a decision.
- “executor” means code that interprets a policy decision into concrete chart API calls.
- “facade” means the existing public namespace `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`.
- “sidecar state” means per-series metadata stored in `WeakMap` rather than on the chart object itself.

Relevant files:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/series_sync.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy_test.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/series_test.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`

## Plan of Work

1. Extract raw-candle comparison into a pure policy namespace with explicit decision data.
2. Move `.setData` versus `.update` execution plus sync sidecar state into an executor namespace.
3. Thin the public facade so it keeps existing signatures but delegates to the new seams.
4. Add direct policy tests and keep facade-level regression tests for the runtime behavior.
5. Run required validation gates, regenerate coverage, verify the new CRAP profile, and perform live `/trade` QA.

## Concrete Steps

From `/hyperopen`:

1. Added the pure policy namespace:
   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy.cljs`

2. Added the executor namespace:
   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/series_sync.cljs`

3. Updated the public facade:
   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`

4. Added and updated tests:
   - `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy_test.cljs`
   - `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/series_test.cljs`

5. Ran validation and evidence commands:
   - `npm install`
   - `npm test`
   - `npm run check`
   - `npm run test:websocket`
   - `npm run coverage`
   - `npm run crap:report`
   - `bb tools/crap_report.clj --module src/hyperopen/views/trading_chart/utils/chart_interop.cljs --format json`
   - `bb tools/crap_report.clj --module src/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy.cljs --format json`

6. Ran live QA commands:
   - `node tools/browser-inspection/src/cli.mjs session start --manage-local-app`
   - `node tools/browser-inspection/src/cli.mjs navigate --session-id <id> --url http://localhost:8080/trade --viewport desktop`
   - `node tools/browser-inspection/src/cli.mjs eval --session-id <id> ...`
   - `node tools/browser-inspection/src/cli.mjs inspect --session-id <id> --url http://localhost:8080/trade --target local-chart-sync-qa --viewports desktop`
   - `node tools/browser-inspection/src/cli.mjs session stop --session-id <id>`

## Validation and Acceptance

Acceptance is complete when all conditions below are true:

1. `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` no longer owns the raw candle comparison branch tree.
2. One pure namespace owns candle-sync inference and can be tested with ordinary data structures only.
3. The pure policy returns an explicit decision object that includes both `:mode` and `:reason`.
4. Main-series and volume-series updates still preserve current external behavior: no-op, full reset, tail update, and single append all behave exactly as before.
5. New tests directly cover every decision reason and keep behavior-oriented names.
6. Existing public interop functions keep their signatures.
7. Required repository gates pass:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
8. CRAP evidence shows the former hotspot is structurally reduced and no longer lives as a branch-heavy private function in the facade.

Validation evidence:

- `npm test`: pass (`2250` tests, `11749` assertions, `0` failures, `0` errors)
- `npm run check`: pass
- `npm run test:websocket`: pass (`372` tests, `2128` assertions, `0` failures, `0` errors)
- `npm run coverage`: pass with `91.05%` statements and `85.97%` functions
- `npm run crap:report`: pass; project top functions no longer include the old chart interop sync function
- `bb tools/crap_report.clj --module src/hyperopen/views/trading_chart/utils/chart_interop.cljs --format json`: facade module has `0` crappy functions, max CRAP `8.729`, average coverage `0.932`
- `bb tools/crap_report.clj --module src/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy.cljs --format json`: policy module has `0` crappy functions; `infer-decision` has CRAP `10.0`, complexity `10`, coverage `1.0`

## Idempotence and Recovery

This refactor was performed additively:

- first add the pure policy file and its tests;
- then add the executor seam;
- only then remove the old private helpers from the facade.

That sequence makes retry safe. If the executor refactor had introduced regressions, the safe fallback would have been to point the facade back to a simpler full-reset path temporarily while keeping the pure decision tests. Correctness is a higher priority than preserving the incremental `.update` optimization.

No persisted schema, browser storage layout, or external API contract changes were involved.

## Artifacts and Notes

Decision examples preserved by the final implementation:

- previous `[]`, next `[]` -> `{:mode :noop :reason :both-empty}`
- previous `candles`, next same reference `candles` -> `{:mode :noop :reason :identical-reference}`
- previous `[t1 t2]`, next `[t1 t2']` with same last `:time` and changed last values -> `{:mode :update-last :reason :tail-rewrite}`
- previous `[t1 t2]`, next `[t1 t2 t3]` with unchanged prefix and new trailing `:time` -> `{:mode :append-last :reason :single-append}`
- previous `[t1 t2]`, next `[t1' t2]` with changed prefix -> `{:mode :full-reset :reason :prefix-mutation}`
- previous `[t1 t2]`, next `[t1]` or `[t1 t2 t3 t4]` -> `{:mode :full-reset :reason :count-mismatch}` unless a narrower reason is intentionally introduced and tested

Manual QA evidence:

- Browser-inspection session: `sess-1773193716686-285710`
- Live route: `http://localhost:8080/trade`
- Verified initial chart render with `11` chart canvases and no `Error fetching chart data.` text
- Clicked timeframe `1h` and confirmed it became the active green timeframe while the chart stayed mounted with `11` canvases
- Switched chart type to `Line`, then back to `Candles`, and confirmed the selected chart type toggled correctly while the chart stayed mounted with `11` canvases and no visible error state
- Screenshot artifact: `/hyperopen/tmp/browser-inspection/inspect-2026-03-11T01-50-31-518Z-7a704f04/local-chart-sync-qa/desktop/screenshot.png`
- Snapshot artifact: `/hyperopen/tmp/browser-inspection/inspect-2026-03-11T01-50-31-518Z-7a704f04/local-chart-sync-qa/desktop/snapshot.json`

Related historical context:

- `/hyperopen/docs/exec-plans/completed/2026-02-18-chart-update-incremental-sync-and-identity-gates.md` introduced the current behavior.
- `/hyperopen/docs/exec-plans/completed/2026-02-20-chart-interop-test-domain-split.md` already created module-aligned chart interop test files, so this refactor extended that structure instead of creating a new mixed test monolith.

## Interfaces and Dependencies

Interfaces after implementation:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/candle_sync_policy.cljs`
  - `infer-decision [previous-candles next-candles]`
  - return shape:

    `{:mode :noop|:full-reset|:update-last|:append-last
      :reason keyword
      :previous-count int
      :next-count int}`

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/series_sync.cljs`
  - `sync-main-series! [series* candles chart-type {:keys [price-decimals]}]`
  - `sync-volume-series! [volume-series candles]`

Dependencies preserved:

- `lightweight-charts` series methods `.setData`, `.update`, and `.applyOptions`
- existing chart-type transforms in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/series.cljs`
- existing price-format inference in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/price_format.cljs`
- existing candle input contract checks in `/hyperopen/src/hyperopen/schema/chart_interop_contracts.cljs`

Plan revision note: 2026-03-11 01:30Z - Initial ExecPlan created after architecture/test audit of the `infer-candle-sync-mode` hotspot, creation of `bd` issue `hyperopen-h0i`, and decision to record this as deferred planning work rather than active execution.
Plan revision note: 2026-03-11 01:51Z - Updated for completed implementation: moved plan to active, extracted policy and executor seams, added direct policy tests, passed required gates, captured CRAP evidence, and recorded live browser QA on `/trade`.
