# Active Asset Funding Tooltip Lazy Derivation and Memoization

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file. It builds on the already-shipped tooltip behavior from `/hyperopen/docs/exec-plans/active/2026-03-01-active-asset-funding-tooltip-position-projections.md`, but repeats all context needed for this performance fix.

## Purpose / Big Picture

Today, the active asset strip eagerly derives the full funding tooltip model during every row render, even when the tooltip is closed. That work includes building predictability rows, lag copy, and materializing `:daily-funding-series` and `:autocorrelation-series` vectors. After this change, the active row only derives the funding tooltip while the tooltip is actually visible or pinned open, and repeated renders with the same predictability summary reuse a cached tooltip model instead of rebuilding it.

You can verify the result by rendering the active asset row with the tooltip closed and confirming no tooltip model derivation occurs, then opening the tooltip and confirming the expected funding content still renders. Re-rendering with an equivalent predictability summary should hit the cache and avoid a second model rebuild.

## Progress

- [x] (2026-03-06 02:58Z) Audited `/hyperopen/src/hyperopen/views/active_asset_view.cljs`, tooltip rendering behavior, existing active asset tooltip tests, and the planning/frontend/runtime constraints for this UI change.
- [x] (2026-03-06 02:58Z) Created and claimed `bd` task `hyperopen-n7l` for this scoped implementation.
- [x] (2026-03-06 03:05Z) Added funding tooltip hover/pin UI state wiring in `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and `/hyperopen/src/hyperopen/asset_selector/actions.cljs`, with action registration in the runtime/catalog wiring files.
- [x] (2026-03-06 03:06Z) Gated active asset funding tooltip derivation on tooltip visibility or pinned state, and wrapped the tooltip model in a local memo cache keyed by a stable summary signature plus the other rendered inputs.
- [x] (2026-03-06 03:07Z) Updated `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` so tooltip content assertions open the tooltip explicitly and added regressions for closed-state gating plus summary-signature memoization.
- [x] (2026-03-06 03:08Z) Installed npm dependencies in this worktree so the required validation commands could run.
- [x] (2026-03-06 03:10Z) Ran `npm test`, `npm run check`, and `npm run test:websocket` successfully.
- [x] (2026-03-06 03:10Z) Updated this plan with final outcomes and validation evidence.

## Surprises & Discoveries

- Observation: The current tooltip helper is CSS-driven (`group-hover` plus a hidden checkbox) and therefore provides no render-time signal about whether the tooltip body is open.
  Evidence: `/hyperopen/src/hyperopen/views/active_asset_view.cljs` computes `funding-tooltip` before calling `tooltip`, while `tooltip` itself only toggles visibility with CSS classes and a checkbox input.

- Observation: Existing active asset tooltip tests assume tooltip content is always present in the Hiccup tree, because the full tooltip model is currently built even while closed.
  Evidence: `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` asserts tooltip strings directly from `active-asset-row` without setting any tooltip-open state first.

- Observation: This repo already uses small atom-backed memo caches keyed by stable signatures for hot render paths, so a local cache in the active asset view is consistent with existing practice.
  Evidence: `/hyperopen/src/hyperopen/views/account_info/cache_keys.cljs` and consumers in `/hyperopen/src/hyperopen/views/account_info/tabs/**` use signature comparisons to decide cache hits.

- Observation: This worktree did not have `node_modules` installed, so the first `npm test` attempt failed before reaching our code.
  Evidence: Initial `npm test` output ended with `sh: shadow-cljs: command not found`; after `npm install`, the same test command passed.

## Decision Log

- Decision: Track funding tooltip open state in existing UI app state instead of inferring it from DOM state only.
  Rationale: The render path needs a deterministic boolean before building the tooltip model. Adding lightweight UI state keeps the model derivation pure and avoids reading DOM state from render code.
  Date/Author: 2026-03-06 / Codex

- Decision: Keep the cache local to `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and key it with a predictability summary signature plus the other user-visible tooltip inputs.
  Rationale: The expensive part of the model is driven by predictability summary content, but tooltip text also depends on position, market symbol, mark, funding rate, hypothetical input, and locale. The cache must stay correct when any of those values change.
  Date/Author: 2026-03-06 / Codex

- Decision: Preserve the existing tooltip UI output when open, and change only the derivation timing and caching behavior.
  Rationale: The user requested a performance fix, not a behavior or visual redesign. Scope stays tight if the only user-visible change is that the tooltip content is no longer derived while hidden.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

Implemented behavior:

- The active asset funding tooltip now tracks hover visibility and pinned-open state under `:funding-ui :tooltip`.
- `active-asset-row` only derives the funding tooltip model when that tooltip is visible or pinned.
- The heavy tooltip model path now uses a single-entry memo cache keyed by predictability summary signature plus the remaining rendered inputs, so equivalent summaries reuse the cached result across renders.
- Existing tooltip rendering tests now open the tooltip explicitly, and new regressions prove both closed-state gating and summary-signature memo reuse.

Validation outcomes:

- `npm test`: pass (`1947` tests, `9977` assertions)
- `npm run check`: pass
- `npm run test:websocket`: pass (`333` tests, `1840` assertions)

## Context and Orientation

The active asset bar lives in `/hyperopen/src/hyperopen/views/active_asset_view.cljs`. The function `active-asset-row` gathers the live asset context, active position, funding predictability summary, hypothetical input, and locale. It currently calls `funding-tooltip-model` unconditionally during row render, then passes the finished model into `funding-tooltip-panel`.

The expensive part of that model is the predictability section. If a predictability summary exists, `funding-tooltip-model` currently builds a vector of predictability rows and eagerly materializes two series vectors:

- `:predictability-daily-rate-series`
- `:predictability-autocorrelation-series`

The tooltip helper in the same file implements hover visibility with CSS classes and pinned-open behavior with a hidden checkbox. Because that helper is purely CSS-driven today, `active-asset-row` has no state path telling it whether the tooltip is actually visible.

Action handlers for this area already live in `/hyperopen/src/hyperopen/asset_selector/actions.cljs`, even for funding-tooltip hypothetical inputs. Public action exports live in `/hyperopen/src/hyperopen/core/public_actions.cljs`, dependency wiring lives in `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, and action registration/argument contracts live in `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs` and `/hyperopen/src/hyperopen/schema/contracts.cljs`.

Regression tests for the view live in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`.

## Plan of Work

First, extend the funding tooltip helper and active asset row so the funding tooltip open state is represented in app state. The row already derives a stable `funding-tooltip-pin-id` from the coin. Use that same identifier for both hover visibility and pinned-open tracking. Add action handlers that save the currently visible funding tooltip id and pinned funding tooltip id under `:funding-ui`, with no network or websocket effects.

Second, change `active-asset-row` so it computes the funding tooltip model only when the tooltip is open. “Open” means either hovered/focused or pinned. When closed, the row should pass no derived tooltip panel content.

Third, introduce a small memoized wrapper around `funding-tooltip-model`. That wrapper should compare a stable signature of `(:summary predictability-state)` and the other scalar or small-map inputs that affect rendered tooltip output. If the signature matches the previous cached entry, reuse the cached model. If it changes, rebuild the model and replace the cache entry. This wrapper must preserve correctness even when the predictability summary map is rebuilt with equal content on subsequent renders.

Fourth, update tests. Existing tooltip rendering tests must explicitly mark the tooltip as visible or pinned in `full-state`, because hidden tooltips should no longer eagerly contribute strings to the render tree. Add focused regression coverage proving that closed-state renders do not call the memoized tooltip model, and that equivalent summary content reuses the cache across repeated renders.

## Concrete Steps

From repository root `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/active_asset_view.cljs` to:
   - add local cache helpers and signature helpers,
   - wire tooltip hover/pin events,
   - read tooltip-open state from `full-state`,
   - gate tooltip model derivation on that open state.
2. Edit `/hyperopen/src/hyperopen/asset_selector/actions.cljs` to add funding tooltip visibility/pin action handlers.
3. Edit `/hyperopen/src/hyperopen/core/public_actions.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`, and `/hyperopen/src/hyperopen/schema/contracts.cljs` to register the new actions.
4. Edit `/hyperopen/src/hyperopen/state/app_defaults.cljs` if default tooltip UI state needs an explicit initial shape.
5. Edit `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` to update existing tooltip expectations and add the new regressions.
6. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
7. Update this ExecPlan with final progress, discoveries, decisions, and validation output.

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

1. Rendering `active-asset-row` with the funding tooltip closed does not derive the funding tooltip model.
2. Rendering `active-asset-row` with the funding tooltip visible or pinned still shows the same funding projection and predictability content as before.
3. Re-rendering with an equivalent predictability summary reuses the cached tooltip model instead of rebuilding it.
4. Re-rendering after a materially changed summary or position/funding input invalidates the cache and rebuilds the tooltip model.
5. `npm run check`, `npm test`, and `npm run test:websocket` all pass.

## Idempotence and Recovery

The change is localized to one view module, one action namespace, registration files, app defaults, and one test file. Re-running the test and lint commands is safe. If the new tooltip-open state causes UI regressions, revert only the new funding-tooltip state wiring while keeping the memoized model helper and tests so the performance path can be re-landed in smaller pieces.

## Artifacts and Notes

Tracked local issue: `hyperopen-n7l`

Primary files expected to change:

- `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
- `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
- `/hyperopen/src/hyperopen/core/public_actions.cljs`
- `/hyperopen/src/hyperopen/runtime/collaborators.cljs`
- `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`
- `/hyperopen/src/hyperopen/schema/contracts.cljs`
- `/hyperopen/src/hyperopen/state/app_defaults.cljs`
- `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`

## Interfaces and Dependencies

No new external dependencies are needed.

Expected internal interfaces after implementation:

- `/hyperopen/src/hyperopen/asset_selector/actions.cljs` will expose two new actions for funding tooltip UI state transitions: one for visibility and one for pinned state.
- `/hyperopen/src/hyperopen/views/active_asset_view.cljs` will expose a new private memoized wrapper around `funding-tooltip-model`.
- `/hyperopen/src/hyperopen/views/active_asset_view.cljs` will keep `funding-tooltip-model` pure and deterministic; the gating decision happens in `active-asset-row`.

Plan revision note: 2026-03-06 02:58Z - Created initial ExecPlan for lazy funding tooltip derivation, UI-state wiring, and summary-signature memoization.
Plan revision note: 2026-03-06 03:10Z - Marked implementation complete, recorded the missing-`node_modules` discovery, and added final validation results.
