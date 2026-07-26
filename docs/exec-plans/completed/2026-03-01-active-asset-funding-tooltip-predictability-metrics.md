# Active Asset Funding Tooltip 30-Day Predictability Metrics

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Today, the active-asset funding tooltip shows position and projection cashflow, but it does not quantify predictability. After this change, hovering `Funding / Countdown` will also show a new `Predictability (30d)` section with five statistics derived from market funding history: 30-day mean funding rate, 30-day rate volatility (standard deviation), and autocorrelation at lag 1 day, lag 5 days, and lag 15 days.

The user-visible gain is immediate ranking intuition for stability: users can see whether a funding stream is merely high, or actually persistent and smooth. The behavior is verifiable by selecting a perp market, hovering the funding value, and seeing the added `Predictability (30d)` rows populate without repeated full-history API pulls.

## Progress

- [x] (2026-03-01 23:23Z) Audited current tooltip rendering path in `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and tests in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`.
- [x] (2026-03-01 23:23Z) Confirmed existing market funding cache/delta infrastructure in `/hyperopen/src/hyperopen/funding/history_cache.cljs` is available for 30-day data retrieval.
- [x] (2026-03-01 23:23Z) Drafted implementation strategy for state, effect wiring, statistics computation, and tooltip design.
- [x] (2026-03-01 23:35Z) Implemented pure funding predictability statistics module and unit tests.
- [x] (2026-03-01 23:37Z) Implemented runtime effect to sync cache and project 30-day predictability summary into store.
- [x] (2026-03-01 23:38Z) Wired predictability sync trigger into active-asset selection/startup subscription flows.
- [x] (2026-03-01 23:39Z) Rendered `Predictability (30d)` section in the funding tooltip with loading/empty/error-safe fallbacks.
- [x] (2026-03-01 23:40Z) Updated effect/action contracts and validation coverage for the new effect ID.
- [x] (2026-03-01 23:42Z) Passed required gates: `npm run check`, `npm test`, `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Tooltip visibility is currently CSS hover (`group-hover`) and does not dispatch an action on mouse enter.
  Evidence: `/hyperopen/src/hyperopen/views/active_asset_view.cljs` `tooltip` helper uses static `group-hover:opacity-100` classes and no event handlers.

- Observation: A reusable 30-day rolling cache with delta fetch already exists and exposes deterministic row windows.
  Evidence: `/hyperopen/src/hyperopen/funding/history_cache.cljs` provides `sync-market-funding-history-cache!` and `rows-for-window`.

- Observation: Existing math utilities already provide mean, sample standard deviation, and Pearson correlation primitives.
  Evidence: `/hyperopen/src/hyperopen/portfolio/metrics/math.cljs` includes `mean`, `sample-stddev`, and `pearson-correlation`.

- Observation: `:actions/select-asset` is covered by effect-order policy and heavy-effect dedupe checks, so adding a new network-heavy effect requires contract updates.
  Evidence: `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs` includes explicit heavy-effect IDs for `:actions/select-asset`.

## Decision Log

- Decision: Compute lag autocorrelation from daily-aggregated funding means, not raw hourly rows.
  Rationale: Requested lags are in days (`1d`, `5d`, `15d`), and day-level aggregation avoids ambiguity and noise from variable intra-day sample density.
  Date/Author: 2026-03-01 / Codex

- Decision: Trigger data sync on active-asset selection/subscription instead of waiting for hover enter.
  Rationale: Hover currently has no action channel, and prefetch gives predictable UX with instant tooltip reads while still respecting cache cooldown and delta fetch semantics.
  Date/Author: 2026-03-01 / Codex

- Decision: Add a dedicated tooltip subsection `Predictability (30d)` with a compact metric/value grid, rather than merging into `Projections`.
  Rationale: `Projections` are forward cashflow estimates, while predictability metrics are historical diagnostics; separate sections preserve cognitive chunking.
  Date/Author: 2026-03-01 / Codex

- Decision: Keep statistical computation in a pure funding domain module and keep side effects in runtime effect adapters.
  Rationale: Preserves deterministic compute boundaries and follows repository architecture constraints on side-effect ownership.
  Date/Author: 2026-03-01 / Codex

## Outcomes & Retrospective

Implemented end to end.

Concrete outcomes:

- Added pure predictability computation module:
  - `/hyperopen/src/hyperopen/funding/predictability.cljs`
- Added runtime effect to sync cached funding history and project 30-day stats:
  - `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`
- Registered and contracted new effect:
  - `/hyperopen/src/hyperopen/registry/runtime.cljs`
  - `/hyperopen/src/hyperopen/runtime/registry_composition.cljs`
  - `/hyperopen/src/hyperopen/app/effects.cljs`
  - `/hyperopen/src/hyperopen/schema/contracts.cljs`
  - `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`
- Added default app state branch for predictability by coin:
  - `/hyperopen/src/hyperopen/state/app_defaults.cljs`
- Wired prefetch trigger on active-asset selection/subscription:
  - `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
  - `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`
- Added tooltip section `Predictability (30d)` with mean, volatility, and ACF lag rows:
  - `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
- Added and updated tests:
  - `/hyperopen/test/hyperopen/funding/predictability_test.cljs`
  - `/hyperopen/test/hyperopen/runtime/effect_adapters_test.cljs`
  - `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`
  - `/hyperopen/test/hyperopen/asset_selector/actions_test.cljs`
  - `/hyperopen/test/hyperopen/core_bootstrap/asset_selector_actions_test.cljs`
  - `/hyperopen/test/hyperopen/runtime/action_adapters_test.cljs`
  - `/hyperopen/test/hyperopen/schema/contracts_test.cljs`
  - `/hyperopen/test/hyperopen/state/app_defaults_test.cljs`
  - `/hyperopen/test/test_runner_generated.cljs`

Validation summary:

- `npm run check` passed.
- `npm test` passed (`1682` tests, `8777` assertions, `0` failures).
- `npm run test:websocket` passed (`289` tests, `1645` assertions, `0` failures).

Retrospective:

- Existing funding cache and delta sync infrastructure made endpoint-safe integration straightforward.
- Prefetching on active-asset switch avoided hover-trigger complexity while preserving perceived tooltip responsiveness.

## Context and Orientation

The active-asset strip is rendered in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`. Funding tooltip model assembly currently happens in `funding-tooltip-model`, and rendering is in `funding-tooltip-panel`. The tooltip currently has `Position` and `Projections` sections only.

Market funding history transport and local cache are now available from prior work:

- API wrapper: `request-market-funding-history!` in `/hyperopen/src/hyperopen/api/endpoints/market.cljs` and wired through gateway/default/instance.
- Cache and delta sync: `/hyperopen/src/hyperopen/funding/history_cache.cljs`.

Runtime effect wiring follows this path:

- Effect handlers are declared in `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`.
- Effect IDs are registered in `/hyperopen/src/hyperopen/registry/runtime.cljs`.
- Contracts for action/effect args live in `/hyperopen/src/hyperopen/schema/contracts.cljs`.
- Coverage checks for contract/registry drift live in `/hyperopen/test/hyperopen/schema/contracts_coverage_test.cljs`.

`Effect-order contract` means a runtime rule enforcing that state projection effects happen before heavy I/O effects for specific actions. This matters for `:actions/select-asset` and is defined in `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`.

## Plan of Work

Milestone 1 introduces pure statistics functions over normalized funding rows. Add a new module at `/hyperopen/src/hyperopen/funding/predictability.cljs` that:

- Accepts normalized market funding rows (`:time-ms`, `:funding-rate-raw`).
- Builds a daily time series by grouping rows into UTC days and averaging each day.
- Computes 30-day summary metrics:
  - Mean: arithmetic average of funding-rate values over the 30-day window.
  - Volatility (standard deviation): sample standard deviation of funding-rate values over the same window.
  - Autocorrelation lag k days: Pearson correlation between aligned daily series pairs `(x[t], x[t-k])` for `k in {1,5,15}`.
- Returns a single summary map with explicit metadata (`:sample-count`, `:daily-count`, `:window-start-ms`, `:window-end-ms`, `:insufficient?` flags per lag).

Milestone 2 adds runtime data orchestration for this summary. Add a new effect function in `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs` (or a new funding effect module called from it) that:

- Takes `coin`.
- Calls `funding-cache/sync-market-funding-history-cache!`.
- Slices rows to 30 days with `funding-cache/rows-for-window`.
- Computes summary via `funding-predictability/compute-30d-summary`.
- Saves loading/success/error state into store under a new stable path, recommended:
  - `[:active-assets :funding-predictability :loading-by-coin coin]`
  - `[:active-assets :funding-predictability :by-coin coin]`
  - `[:active-assets :funding-predictability :error-by-coin coin]`
  - `[:active-assets :funding-predictability :loaded-at-ms-by-coin coin]`

Milestone 3 wires the trigger points so data is ready by the time tooltip appears:

- In `/hyperopen/src/hyperopen/asset_selector/actions.cljs`, append a new heavy effect in `select-asset` after subscribe effects: `[:effects/sync-active-asset-funding-predictability canonical-coin]`.
- In `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`, append the same effect to `subscribe-to-asset` so startup restore path also hydrates stats.
- Register the new effect ID in runtime registry and contracts.
- Update effect-order policy for `:actions/select-asset` to include the new effect in its heavy set.

Milestone 4 updates tooltip view model and layout in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`:

- Extend `funding-tooltip-model` input to accept predictability summary state for the active coin.
- Keep existing `Position` and `Projections` sections unchanged.
- Add `Predictability (30d)` section below projections with a two-column grid (`Metric`, `Value`) and rows:
  - `Mean`
  - `Volatility (σ)`
  - `ACF Lag 1d`
  - `ACF Lag 5d`
  - `ACF Lag 15d`
- Formatting guidance:
  - Mean: signed percentage text (same style family as projections).
  - Volatility: unsigned percentage text.
  - ACF values: signed decimal with three fractional digits.
  - Preserve plus/minus signs so meaning is not color-only.
- Loading and sparse-data behavior:
  - Loading row: `Loading 30d stats…`.
  - Sparse lag row: display `—` and attach explanatory microcopy in tooltip footer only when needed (for example `Lag 15d needs at least 16 daily points`).

Milestone 5 adds tests and regression coverage:

- New domain tests in `/hyperopen/test/hyperopen/funding/predictability_test.cljs` for:
  - Mean/stddev correctness.
  - Daily aggregation correctness.
  - Autocorrelation lag math and insufficient-sample handling.
- Update `/hyperopen/test/hyperopen/asset_selector/actions_test.cljs` expected effect vectors for `select-asset`.
- Add or update runtime effect tests in `/hyperopen/test/hyperopen/runtime/effect_adapters_test.cljs` for loading/success/error projection flow.
- Update `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` to assert new tooltip labels and formatted values.
- Update contract/registry tests as needed (`contracts_test`, `contracts_coverage_test`, runtime registry composition tests).

## Concrete Steps

From repository root `/hyperopen`:

1. Create pure statistics module and tests.

   - Edit/create:
     - `/hyperopen/src/hyperopen/funding/predictability.cljs`
     - `/hyperopen/test/hyperopen/funding/predictability_test.cljs`

2. Add runtime effect orchestration and state defaults.

   - Edit:
     - `/hyperopen/src/hyperopen/state/app_defaults.cljs`
     - `/hyperopen/src/hyperopen/runtime/effect_adapters.cljs`
     - `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
     - `/hyperopen/src/hyperopen/app/effects.cljs`
     - `/hyperopen/src/hyperopen/registry/runtime.cljs`
     - `/hyperopen/src/hyperopen/schema/contracts.cljs`
     - `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`

3. Wire trigger emission.

   - Edit:
     - `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
     - `/hyperopen/src/hyperopen/runtime/action_adapters.cljs`

4. Render tooltip stats.

   - Edit:
     - `/hyperopen/src/hyperopen/views/active_asset_view.cljs`

5. Update tests and generated test runner.

   - Edit:
     - `/hyperopen/test/hyperopen/asset_selector/actions_test.cljs`
     - `/hyperopen/test/hyperopen/runtime/effect_adapters_test.cljs`
     - `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`
     - `/hyperopen/test/hyperopen/schema/contracts_test.cljs`
     - `/hyperopen/test/hyperopen/schema/contracts_coverage_test.cljs`
   - Regenerate runner:

         npm run test:runner:generate

6. Run required validation commands:

       npm run check
       npm test
       npm run test:websocket

Expected validation outcome: all three commands exit successfully; existing unrelated warnings may persist, but no new failures or errors are introduced.

## Validation and Acceptance

Acceptance is behavior-first and complete when all items below are true:

1. Hovering `Funding / Countdown` on a perp asset displays a `Predictability (30d)` section with `Mean`, `Volatility (σ)`, `ACF Lag 1d`, `ACF Lag 5d`, and `ACF Lag 15d`.
2. Tooltip still displays existing `Position` and `Projections` rows exactly as before.
3. Stats render from locally cached 30-day rows when available and only fetch missing deltas when needed.
4. Selecting a new active asset eventually hydrates predictability stats without user reload.
5. When insufficient daily history exists for a lag, the row displays a safe placeholder (`—`) and does not show misleading numeric output.
6. All required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

Manual verification scenario:

- Select `BTC` perp.
- Hover funding value and observe `Predictability (30d)` rows.
- Switch to another perp, then back to `BTC`.
- Confirm second hover is immediate and does not rely on full-history refetch.

## Idempotence and Recovery

This implementation is additive and repeatable. Re-running sync effects is safe due cache dedupe and cooldown behavior already implemented in funding history cache logic. If the new statistics effect fails, tooltip should fall back to placeholders while preserving existing `Position` and `Projections` sections.

Recovery approach if integration regresses UI:

- Temporarily disable only the new predictability rows in tooltip model/render while leaving the effect and domain computation intact.
- Keep state writes isolated under `:active-assets :funding-predictability` so rollback does not affect unrelated account/funding views.

## Artifacts and Notes

Recommended tooltip micro-layout (content example):

    Position
    Size      Long 0.0185 BTC
    Value     $99.39

    Projections      Rate       Payment
    Next 24h         -0.0568%   —
    APY              -20.7298%  —

    Predictability (30d)
    Mean             +0.0042%
    Volatility (σ)   0.0187%
    ACF Lag 1d       +0.714
    ACF Lag 5d       +0.482
    ACF Lag 15d      +0.210

Design preference notes:

- Keep the existing card visual language and type ramp from current tooltip polish work.
- Use clear section chunking and lightweight separators to avoid crowding.
- Keep numeric columns left-aligned under consistent labels for scan speed.
- Preserve non-color semantics with explicit signs (`+`/`-`) and placeholders (`—`).

## Interfaces and Dependencies

New pure module interface (proposed):

- In `/hyperopen/src/hyperopen/funding/predictability.cljs` define:

      (defn compute-30d-summary
        [rows now-ms]
        ;; rows = normalized funding rows with :time-ms and :funding-rate-raw
        ;; returns summary map with mean/stddev/autocorr and metadata
        ...)

Runtime effect interface (proposed):

- New effect ID: `:effects/sync-active-asset-funding-predictability`.
- Effect args: one required `coin` string.
- Registered and contracted across:
  - `/hyperopen/src/hyperopen/registry/runtime.cljs`
  - `/hyperopen/src/hyperopen/schema/contracts.cljs`

No new external dependencies are required. Use existing utilities:

- Funding cache: `/hyperopen/src/hyperopen/funding/history_cache.cljs`
- Math primitives: `/hyperopen/src/hyperopen/portfolio/metrics/math.cljs`

Plan revision note: 2026-03-01 23:23Z - Created initial implementation-ready ExecPlan for adding 30-day predictability metrics (mean, volatility, autocorrelation lags 1/5/15 days) to the active-asset funding tooltip, including UI design choices and endpoint-protection strategy via existing cache/delta infrastructure.
Plan revision note: 2026-03-01 23:42Z - Updated plan to completed status with shipped implementation details, final validation outcomes, and retrospective notes.
