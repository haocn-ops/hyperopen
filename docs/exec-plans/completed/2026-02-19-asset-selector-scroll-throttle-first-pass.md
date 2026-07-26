# Asset Selector First-Pass Scroll Throttle and Chunk Reduction

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, scrolling the asset selector list should feel less janky because the app will request render-limit growth less aggressively and in smaller batches. Users should still reach the full list by scrolling to the bottom, but the work on each growth step should be reduced, lowering long-task spikes measured in browser profiling.

## Progress

- [x] (2026-02-19 15:31Z) Created ExecPlan and scoped first-pass implementation to throttled scroll prefetch plus smaller chunk growth.
- [x] (2026-02-19 15:33Z) Implemented action-layer throttle guard for `:actions/maybe-increase-asset-selector-render-limit` with optional timestamp input.
- [x] (2026-02-19 15:33Z) Reduced incremental render-limit step size from 80 to 40 while preserving manual controls.
- [x] (2026-02-19 15:34Z) Wired scroll event timestamp placeholder and updated asset selector scroll dispatch payload.
- [x] (2026-02-19 15:35Z) Updated tests covering action behavior, view wiring, core action wrappers, and action contracts.
- [x] (2026-02-19 15:37Z) Ran required validation gates: `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-19 15:39Z) Updated plan outcomes/retrospective and moved plan to completed.

## Surprises & Discoveries

- Observation: Existing profiling shows render-limit growth happens in seven rapid jumps (120 to 629), which aligns with current step size and bottom-prefetch logic.
  Evidence: `/hyperopen/tmp/asset-selector-perf-2026-02-19T15-22-33-236Z/summary.json`.
- Observation: Runtime action validation enforces payload arity contracts, so adding a second scroll argument required schema updates to avoid rejecting UI-dispatched actions.
  Evidence: Existing `:actions/maybe-increase-asset-selector-render-limit` contract in `/hyperopen/src/hyperopen/schema/contracts.cljs` was single-argument only before this change.

## Decision Log

- Decision: Keep this as a first-pass optimization (throttle + smaller chunk) rather than introducing full virtualization in the same change.
  Rationale: It is lower-risk and preserves current structure while directly targeting the measured long-task pattern.
  Date/Author: 2026-02-19 / Codex
- Decision: Use event-provided `timeStamp` as the throttle clock and store `:last-render-limit-increase-ms` in selector state.
  Rationale: This keeps action logic deterministic from explicit inputs and avoids introducing `Date.now` into action functions.
  Date/Author: 2026-02-19 / Codex
- Decision: Keep backward compatibility by allowing both one-arg and two-arg forms of `maybe-increase-asset-selector-render-limit`.
  Rationale: Existing direct function calls in tests and potential callers continue to work while the UI path upgrades to timestamp-aware throttling.
  Date/Author: 2026-02-19 / Codex

## Outcomes & Retrospective

The first-pass optimization shipped as planned. Automatic render-limit growth now increments by 40 rows instead of 80, and near-bottom scroll growth is throttled with a 90ms cooldown when event timestamps are available. The scroll handler now dispatches both `scrollTop` and `timeStamp`, runtime placeholders expose `:event/timeStamp`, and action schema contracts accept either one or two payload args for this action.

The selector reset paths now clear `:last-render-limit-increase-ms` so stale cooldown state does not carry across dropdown reopen/filter reset flows. Regression coverage was updated in selector action tests, selector view tests, core bootstrap action tests, and schema contract tests. Required validation gates all passed.

## Context and Orientation

The asset selector list rendering lives in `/hyperopen/src/hyperopen/views/asset_selector_view.cljs` and dispatches a scroll action whenever the list viewport scrolls near bottom. The state transition logic for render-limit growth lives in `/hyperopen/src/hyperopen/asset_selector/actions.cljs`, which now grows in 40-row steps and applies timestamp-aware throttling for high-frequency scroll events. Action argument contracts are validated in `/hyperopen/src/hyperopen/schema/contracts.cljs`, and event placeholders are registered in `/hyperopen/src/hyperopen/registry/runtime.cljs`.

In this repository, an "action" is a pure function that takes app state plus payload args and returns declarative effects; keeping action decisions deterministic from inputs is expected. For that reason, throttling should use the DOM event timestamp passed as an argument (via placeholder) rather than reading `Date.now` directly inside the action.

## Plan of Work

Update the asset selector scroll pipeline in small, additive steps. First, lower the render-limit increment constant in `/hyperopen/src/hyperopen/asset_selector/actions.cljs` from 80 to a smaller value (40) to reduce per-growth render cost. Next, extend `maybe-increase-asset-selector-render-limit` to support an optional event timestamp argument and gate growth with a short cooldown window, while preserving existing behavior when no timestamp is supplied. Then add a new asset-selector state field to track the last growth timestamp and reset that field whenever the dropdown or filter controls reset render-limit, so stale timestamps do not block new sessions.

After action logic is updated, wire the view scroll handler in `/hyperopen/src/hyperopen/views/asset_selector_view.cljs` to pass both `scrollTop` and `timeStamp`, and register a new `:event/timeStamp` placeholder in `/hyperopen/src/hyperopen/registry/runtime.cljs`. Update action argument contracts in `/hyperopen/src/hyperopen/schema/contracts.cljs` so this action accepts one or two payload arguments.

Finally, update and run tests in `/hyperopen/test/hyperopen/asset_selector/actions_test.cljs`, `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`, and `/hyperopen/test/hyperopen/core_bootstrap_test.cljs` (plus contract tests if needed), then run required repository gates.

## Concrete Steps

From `/hyperopen`:

1. Edit action/view/runtime/contracts files to introduce throttled scroll growth and smaller chunk size.
2. Update affected tests for new expected render-limit increments and scroll action payload shape.
3. Run required gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

- Scroll handler in asset selector dispatches `:actions/maybe-increase-asset-selector-render-limit` with both `scrollTop` and event timestamp placeholders.
- Render-limit grows by smaller increments than before (40 rows each automatic step) and still reaches full list by repeated near-bottom scrolling.
- Throttle state prevents repeated immediate growth calls from consecutive high-frequency scroll events within cooldown.
- Existing manual controls (`Load more`, `Show all`) continue to work.
- Required validation gates complete successfully.

## Idempotence and Recovery

All changes are source-level and additive. Re-running commands is safe. If a test fails mid-change, restore consistency by fixing expectations rather than reverting unrelated files. No destructive migration or data rewrite is required.

## Artifacts and Notes

Performance reference artifacts used to scope this pass:

- `/hyperopen/tmp/asset-selector-perf-2026-02-19T15-22-33-236Z/summary.json`
- `/hyperopen/tmp/asset-selector-perf-2026-02-19T15-22-33-236Z/run-01-analysis.json`

## Interfaces and Dependencies

No new external dependencies are needed.

Interfaces updated:

- `hyperopen.asset-selector.actions/maybe-increase-asset-selector-render-limit` accepts `(state scroll-top)` and `(state scroll-top event-time-ms)`.
- Runtime placeholder registration adds `:event/timeStamp` in `/hyperopen/src/hyperopen/registry/runtime.cljs`.
- Action contract for `:actions/maybe-increase-asset-selector-render-limit` accepts one or two payload args.

Plan revision note: 2026-02-19 15:31Z - Initial plan created for first-pass throttled scroll and chunk-size reduction work.
Plan revision note: 2026-02-19 15:38Z - Updated progress, discoveries, decisions, and outcomes after implementation and full validation gates passed.
