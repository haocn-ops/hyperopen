# Migrate Chart Visible-Range Persistence from localStorage to IndexedDB

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Chart visible-range persistence is currently stored in `localStorage` with keys like `chart-visible-time-range:v2:<timeframe>:<asset>`. Writes are triggered from chart time-scale range subscriptions, which can fire frequently during pan and zoom interactions. `localStorage` write calls are synchronous and can add interaction jank.

After this migration, visible-range persistence uses IndexedDB with debounced write-behind so frequent range changes do not cause one synchronous write per event. Chart reloads still restore sensible ranges, and default fallback behavior remains deterministic when no persisted range exists.

## Progress

- [x] (2026-03-05 15:15Z) Audited current visible-range persistence implementation in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs`.
- [x] (2026-03-05 15:15Z) Audited chart mount/update lifecycle integration in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`.
- [x] (2026-03-05 15:15Z) Reviewed existing tests in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence_test.cljs`.
- [x] (2026-03-05 15:15Z) Authored initial migration plan with async restore guardrails.
- [x] (2026-03-05 15:18Z) Filed tracking issue in `bd`: `hyperopen-eux.3` under epic `hyperopen-eux`.
- [x] (2026-03-05 19:05Z) Added IndexedDB-backed visible-range load/persist adapters with localStorage fallback parsing.
- [x] (2026-03-05 19:05Z) Added debounced write-behind for chart visible-range persistence and explicit flush on cleanup.
- [x] (2026-03-05 19:05Z) Added async restore guards in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` so late loads do not override post-mount interaction.
- [x] (2026-03-05 19:05Z) Updated visible-range persistence tests and ran `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: Existing API is synchronous for loading persisted range (`apply-persisted-visible-range!` returns boolean immediately).
  Evidence: `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs` reads localStorage directly and returns applied/not applied.

- Observation: Persistence subscriptions can emit many candidate ranges while the user drags or scrolls.
  Evidence: `subscribeVisibleLogicalRangeChange` and `subscribeVisibleTimeRangeChange` handlers write on each event path.

- Observation: Current tests already cover range validation and fallback defaults, giving a strong baseline for parity checks.
  Evidence: `visible_range_persistence_test.cljs` includes invalid-range fallback and recent-window default assertions.

## Decision Log

- Decision: Introduce debounced write-behind for visible-range persistence.
  Rationale: The main value of this migration is reducing high-frequency persistence overhead during interactive chart movement.
  Date/Author: 2026-03-05 / Codex

- Decision: Keep fallback range behavior unchanged when no persisted range is available.
  Rationale: Users rely on recent-window defaults and right-anchor behavior; migration should only alter storage transport.
  Date/Author: 2026-03-05 / Codex

- Decision: Add stale-load guard so asynchronous restore never clobbers a range chosen by the user after mount.
  Rationale: IndexedDB reads resolve asynchronously and can arrive after user interaction.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

Implemented as planned. Visible-range persistence now stores normalized records in IndexedDB-first storage, uses debounced write-behind for high-frequency time-scale updates, and restores asynchronously with runtime-state guards so user interaction wins over late loads. Validation passed through `npm run check`, `npm test`, and `npm run test:websocket`.

## Context and Orientation

Visible-range persistence lives in:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` (facade exports)
- `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` (chart mount/update lifecycle)

Current key behavior:

- Normalize range payload (`:kind`, `:from`, `:to`).
- Attempt to apply persisted range if valid for current candles.
- Fall back to default recent logical range when persisted range is missing or invalid.

Migration must preserve these semantics while replacing localStorage with IndexedDB and adding write coalescing.

Issue tracking for this plan is in `bd`:

- Epic: `hyperopen-eux`
- Task: `hyperopen-eux.3`

## Plan of Work

### Milestone 1: Add Visible-Range IndexedDB Store

Use shared IndexedDB platform boundary and add a dedicated object store for chart visible ranges keyed by `(timeframe, asset)`. Keep existing key token normalization so persistence identity stays stable across reloads.

This milestone is complete when put/get of a normalized range payload works asynchronously in tests.

### Milestone 2: Introduce Debounced Persistence Handlers

Update `subscribe-visible-range-persistence!` so rapid consecutive range events are coalesced into one persistence write per debounce window (for example 200-300ms). Maintain final-value correctness: the latest range in the burst is what gets persisted.

This milestone is complete when tests show multiple events produce one persisted write in a window.

### Milestone 3: Add Async Restore with User-Interaction Guard

Because IndexedDB load is async, add a restore sequence that:

1. Applies default behavior immediately if needed.
2. Loads persisted range asynchronously.
3. Applies loaded range only if chart context (asset/timeframe) is unchanged and no user-driven range change occurred after restore started.

Track a restore token or interaction epoch in chart runtime state to guard against stale apply.

This milestone is complete when tests prove late async restore does not override post-mount user movement.

### Milestone 4: Preserve Fallback and Compatibility During Rollout

Keep localStorage read fallback during migration and keep validation guards unchanged. Add follow-up cleanup issue to remove fallback once rollout confidence is established.

## Concrete Steps

All commands run from `/hyperopen`.

1. Locate range persistence seams.

    rg -n "apply-persisted-visible-range|subscribe-visible-range-persistence|chart-visible-time-range" src/hyperopen/views/trading_chart

2. Add IndexedDB adapter and debounced write logic.

    rg -n "storage-get|storage-set|local-storage" src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs

3. Add async restore and stale-load guard wiring in chart core lifecycle.

    rg -n "visible-range-restore-tried|visible-range-persistence-subscribed|chart-runtime" src/hyperopen/views/trading_chart/core.cljs

4. Update tests and run gates.

    npm test -- --focus=visible_range_persistence
    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance is behavior-based:

1. Reloading the chart with the same asset/timeframe restores a valid persisted range from IndexedDB.
2. Rapid pan/zoom events do not trigger one persistence write per event; writes are debounced/coalesced.
3. If user changes range after mount but before async restore resolves, stale restore does not override that interaction.
4. Invalid persisted range still falls back to existing default-recent behavior.
5. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

Manual verification example:

- Open trade chart, pan several times quickly, then stop.
- Confirm one recent visible-range record update in IndexedDB rather than high-frequency synchronous localStorage writes.
- Reload and verify range restore.

## Idempotence and Recovery

The migration is safe to repeat because persisted payload normalization and range validation are deterministic. If IndexedDB fails, fallback path should preserve behavior.

Rollback strategy:

- Keep a storage backend toggle for visible-range persistence during rollout.
- If interaction regressions appear, route persistence back to localStorage while retaining debounced handler and guard logic where safe.

## Artifacts and Notes

Suggested visible-range store row shape:

    {:id "v2:<timeframe>:<encoded-asset>"
     :timeframe "1h"
     :asset "BTC"
     :kind "logical"
     :from 211
     :to 338
     :saved-at-ms <epoch-ms>}

Persist only validated normalized ranges. Do not persist invalid or inverted ranges.

## Interfaces and Dependencies

Define (or equivalent) functions by completion:

- In `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/visible_range_persistence.cljs`:

    load-persisted-visible-range! :: asset timeframe [opts] -> js/Promise
    subscribe-visible-range-persistence! :: chart timeframe [opts] -> cleanup-fn
    apply-persisted-visible-range! :: chart timeframe [opts] -> boolean|js/Promise

- In `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` runtime state:

    :visible-range-restore-token
    :visible-range-interaction-epoch

These fields (or equivalent) must guarantee stale async restore does not clobber user-driven range updates.

Revision note (2026-03-05 / Codex): Initial plan drafted from visible-range persistence audit to migrate high-frequency chart persistence from localStorage to IndexedDB with interaction-safe async restore.
