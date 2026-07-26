# Trading Chart Lazy Candle Backfill

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows the planning contract in `docs/PLANS.md` and `.agents/PLANS.md`.

## Purpose / Big Picture

The trading chart currently loads a fixed recent candle snapshot, which is efficient at startup but makes the daily chart look truncated when a user zooms out far enough to expect older candles. After this change, the initial chart load should remain small, but zooming or panning near the oldest loaded candle should request one older candle window and merge it into the existing chart data. A user should be able to start with the normal daily chart, zoom out toward the left edge, and see additional older daily candles load without switching assets or timeframes.

## Context References

Public refs:
- Direct user request on 2026-05-25: keep the upfront candle request small, but fetch and pipe in older data when the user zooms out.

Repo artifacts:
- `AGENTS.md` requires an ExecPlan for risky UI work and requires `npm run check`, `npm test`, and `npm run test:websocket` when code changes.
- `docs/BROWSER_TESTING.md` and `docs/FRONTEND.md` govern deterministic browser coverage and browser-QA accounting for UI-facing chart changes.

Local scratch refs:
- None.

## Progress

- [x] (2026-05-25 00:48Z) Confirmed root cause before this plan: the live daily chart had 331 BTC daily rows and the REST candle snapshot defaults to 330 bars.
- [x] (2026-05-25 00:49Z) Chose the lazy-backfill design: do not increase startup bars; request an older window only when the visible logical range approaches the loaded left edge.
- [x] (2026-05-25 01:16Z) Added RED coverage for bounded historical candle requests, merged candle projections, chart action dispatch, runtime backfill triggering, and effect argument propagation.
- [x] (2026-05-25 01:32Z) Implemented API, runtime, action, projection, registration, and effect-order-contract changes to satisfy the tests.
- [x] (2026-05-25 01:52Z) Ran focused tests, required gates, and chart-focused Playwright browser QA accounting.

## Surprises & Discoveries

- Observation: `apply-candle-snapshot-success` currently replaces `[:candles coin interval]` rather than merging rows.
  Evidence: `src/hyperopen/api/projections/market.cljs` stores `rows` directly with `assoc-in`. Lazy backfill would lose recent candles unless this projection becomes a dedupe-and-sort merge.
- Observation: the chart already subscribes to visible logical range changes for persistence.
  Evidence: `src/hyperopen/views/trading_chart/runtime.cljs` calls `subscribe-visible-range-persistence!`, which receives every visible range change and can safely host the backfill trigger without adding a second chart subscription.
- Observation: the local worktree did not have `node_modules`, so the first focused JS test execution could not resolve bundled UI dependencies.
  Evidence: `node out/test.js` failed on missing `lucide/dist/esm/icons/external-link.js`; `npm ci` installed the dependency tree before the verification run.
- Observation: adding a new chart action and effect policy pushed two central contract namespaces past their tracked size exceptions.
  Evidence: `npm run check` initially failed `lint:namespace-sizes` for `src/hyperopen/schema/contracts/action_args.cljs` and `src/hyperopen/runtime/effect_order_contract.cljs`; `dev/namespace_size_exceptions.edn` was updated with the new limits and rationale.
- Observation: the generated effect-order contract vectors only matched after the Lean model gained the corresponding heavy-only chart-backfill policy.
  Evidence: `bb tools/formal.clj sync --surface effect-order-contract` was rerun after updating `spec/lean/Hyperopen/Formal/EffectOrderContract.lean`.

## Decision Log

- Decision: Use the existing `:effects/fetch-candle-snapshot` effect with an optional `:end-time-ms` argument instead of adding a new endpoint or data store.
  Rationale: The Hyperliquid `candleSnapshot` request already supports `startTime` and `endTime`. Extending the existing effect keeps initial load, backfill, cache policy, error handling, and candle storage on one path.
  Date/Author: 2026-05-25 / Codex.
- Decision: Trigger backfill when the visible logical range starts within roughly the first 32 loaded bars.
  Rationale: This waits until the user has intentionally zoomed or panned near the historical edge, avoiding speculative startup fetches while loading data before the chart visibly feels exhausted.
  Date/Author: 2026-05-25 / Codex.
- Decision: Request each backfill window ending at one millisecond before the oldest loaded candle time.
  Rationale: It prevents intentional overlap at the API boundary while the projection merge still dedupes rows if the upstream response overlaps.
  Date/Author: 2026-05-25 / Codex.
- Decision: Track requested backfill end times in the chart runtime state.
  Rationale: The range-change callback can fire repeatedly during a zoom gesture. Local runtime dedupe prevents duplicate network effects for the same left edge without adding global loading state.
  Date/Author: 2026-05-25 / Codex.

## Outcomes & Retrospective

Delivered lazy candle history backfill without increasing the startup candle request size. The default chart candle request still asks for the recent bounded window, while zooming or panning near the loaded left edge dispatches a one-window historical candle request ending just before the oldest loaded candle. Incoming older rows now merge into the existing candle vector with timestamp dedupe and ascending ordering.

Validation evidence:
- Focused chart/action/API/projection/runtime tests: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.websocket.gateway.market-test --test=hyperopen.runtime.app-effects-test --test=hyperopen.runtime.effect-adapters.websocket-test --test=hyperopen.api.projections.market-test --test=hyperopen.core-bootstrap.chart-menu-and-storage-test --test=hyperopen.views.trading-chart.actions-test --test=hyperopen.views.trading-chart.vm-test --test=hyperopen.views.trading-chart.runtime-test` completed with `42 tests, 251 assertions, 0 failures, 0 errors`.
- Formal contract sync: `bb tools/formal.clj sync --surface effect-order-contract` completed after the Lean model update.
- Required gate: `npm run check` completed successfully.
- Required gate: `npm test` completed with `4036 tests, 22225 assertions, 0 failures, 0 errors`.
- Required gate: `npm run test:websocket` completed with `526 tests, 3046 assertions, 0 failures, 0 errors`.
- Browser QA: `PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "trade chart context menu supports pointer and keyboard flows"` completed with `1 passed`.

No Browser MCP or browser-inspection sessions were created, so there was no browser session cleanup to run. The existing process on `127.0.0.1:8080` blocked Playwright's default webServer startup path, so the supported `PLAYWRIGHT_REUSE_EXISTING_SERVER=true` mode was used for the deterministic browser check.

Follow-up from manual QA, 2026-05-25: a deeply zoomed-out daily chart still showed empty left-side space after the first backfill. Root cause: the runtime requested a fixed 330-bar history window even when the current visible logical range extended more than 330 bars before the loaded first candle. The runtime now sizes the backfill request from the visible left-side deficit plus the 32-bar threshold buffer, while preserving the 330-bar minimum for normal near-edge zooms. Regression coverage was added for a visible logical range with `from = -520`, which now requests 552 bars instead of 330.

## Context and Orientation

The trading chart lives under `src/hyperopen/views/trading_chart`. `vm.cljs` turns app state into chart render options, `core.cljs` creates the Replicant node for the chart, and `runtime.cljs` owns the Lightweight Charts lifecycle after the DOM node mounts. `utils/chart_interop/visible_range_persistence.cljs` subscribes to Lightweight Charts visible range changes so the user’s zoomed or panned view can be persisted.

The app’s candle request path starts from chart actions in `src/hyperopen/chart/actions.cljs`, flows through runtime effect adapters in `src/hyperopen/runtime/effect_adapters/websocket.cljs` and `src/hyperopen/runtime/app_effects.cljs`, then reaches `src/hyperopen/api/endpoints/market.cljs`. The endpoint calculates `startTime` from `bars * interval` and sends a `candleSnapshot` request. Today, all normal trading-chart requests use `endTime = now`, which means daily data is capped to the latest 330 bars.

The phrase “logical range” means Lightweight Charts’ bar-index viewport, not wall-clock time. A visible logical range with `from` near zero means the user can see, or is close to seeing, the oldest loaded candle. That is the right signal for lazy backfill because it is driven by the actual chart view, not by startup state.

## Plan of Work

First, add tests that prove `candleSnapshot` accepts an explicit historical `endTime`, effect adapters preserve that option, and candle success projection merges overlapping row windows. These tests define the data contract needed before touching chart runtime behavior.

Second, add a chart action named `request-chart-candle-backfill`. It will validate that the request still targets the current active asset and emit exactly one `:effects/fetch-candle-snapshot` effect with `:coin`, `:interval`, `:bars`, and `:end-time-ms`. Register the action in runtime collaborators, action argument schemas, runtime registration, and the effect-order contract.

Third, add a chart-runtime backfill trigger. When visible range persistence reports a range change, the runtime should inspect the latest chart context from `chart-runtime` state. If candles exist, an `on-history-backfill-request` callback exists, the visible range is near the left edge, and this oldest candle end time has not already been requested, it should dispatch a backfill request ending one millisecond before the oldest candle.

Fourth, wire the view model to produce the callback. The callback should dispatch `[:actions/request-chart-candle-backfill {...}]` through the same Replicant dispatch function already used by chart overlays, with the active asset and selected timeframe captured from the current model.

Fifth, run focused tests and required gates. Because this touches chart interaction behavior, account for browser QA. If a deterministic browser assertion is practical in the existing Playwright harness, add or run the smallest relevant Playwright check. If not practical in this turn, record the residual browser-QA gap explicitly.

## Concrete Steps

Run focused RED tests after adding test cases:

    cd /Users/barry/.codex/worktrees/16da/hyperopen
    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.websocket.gateway.market-test --test=hyperopen.runtime.app-effects-test --test=hyperopen.runtime.effect-adapters.websocket-test --test=hyperopen.api.projections.market-test --test=hyperopen.core-bootstrap.chart-menu-and-storage-test --test=hyperopen.views.trading-chart.actions-test --test=hyperopen.views.trading-chart.vm-test --test=hyperopen.views.trading-chart.runtime-test

Expected before implementation: the new tests fail because `:end-time-ms` is not accepted or propagated, candle snapshots replace rows instead of merging, and no runtime backfill callback exists.

After implementation, rerun the same focused command and expect zero failures. Then run:

    npm run check
    npm test
    npm run test:websocket

If browser verification is possible, run the smallest relevant Playwright command. If Browser MCP or browser-inspection sessions are created, run:

    npm run browser:cleanup

## Validation and Acceptance

Acceptance requires all of these observable behaviors:

The initial daily chart request still defaults to 330 bars when no explicit `:bars` or `:end-time-ms` is supplied.

A historical backfill request can be expressed as `:effects/fetch-candle-snapshot :coin "BTC" :interval :1d :bars 330 :end-time-ms <oldest-minus-one-ms>`, and the REST body uses that value as `endTime` while computing `startTime` from the same interval and bar count.

When older rows arrive for the same coin and timeframe, they are merged with existing rows, deduped by candle timestamp, sorted ascending, and available to `chart-view-model` as one continuous candle vector.

When the mounted chart’s visible logical range moves near the loaded left edge, the runtime dispatches one backfill action for the current asset and timeframe. Repeated range-change events at the same loaded edge do not dispatch duplicate backfill requests.

The final validation result must include focused tests, `npm run check`, `npm test`, `npm run test:websocket`, and browser-QA accounting.

## Idempotence and Recovery

The implementation is additive and safe to retry. Re-running the same tests and gates should not modify source files except generated test runner output when `npm run test:runner:generate` refreshes it. If a test command fails during implementation, keep the ExecPlan `Progress` section current with the failure and continue from the failing test rather than reverting unrelated work.

Runtime backfill dedupe is local to the mounted chart node. If a request fails, the user can still trigger a new attempt by changing asset, timeframe, or remounting the chart. This avoids a global stuck loading flag while preventing rapid duplicate requests during one zoom gesture.

## Artifacts and Notes

Pre-plan runtime evidence from nREPL:

    {:active-asset "BTC",
     :selected-timeframe :1d,
     :selected-count 331,
     :selected-first {:t 1751068800000, :T 1751155199999},
     :selected-last {:t 1779580800000, :T 1779667199999}}

This showed the live daily chart held only the recent 330-bar request plus the latest live candle.

## Interfaces and Dependencies

Extend the existing candle effect argument interface to allow `:end-time-ms`. The effect vector shape at the end of this work is:

    [:effects/fetch-candle-snapshot
     :coin "BTC"
     :interval :1d
     :bars 330
     :end-time-ms 1751068799999]

Add the action interface:

    (request-chart-candle-backfill state {:coin "BTC"
                                          :interval :1d
                                          :bars 330
                                          :end-time-ms 1751068799999})

It returns either an empty vector for stale or malformed requests, or one fetch effect for the current active asset.

Add the chart runtime callback interface inside `chart-runtime-options`:

    {:on-history-backfill-request (fn [{:keys [interval bars end-time-ms]}] ...)}

The runtime calls this callback only after the user’s visible logical range reaches the left-edge threshold.

Revision note, 2026-05-25 00:49Z: Initial ExecPlan created from the direct user request to implement lazy historical candle loading on chart zoom-out.
