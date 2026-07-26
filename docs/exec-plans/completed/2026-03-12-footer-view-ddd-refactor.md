# Footer View Bounded-Context Refactor

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

Tracking `bd` issue: `hyperopen-y7gi` (`Refactor footer view into diagnostics VM, policy, and render seams`, completed).

## Purpose / Big Picture

After this refactor, the footer should still look and behave the same to users, but `/hyperopen/src/hyperopen/views/footer_view.cljs` should stop acting like a catch-all namespace for unrelated concerns. The footer shell, mobile navigation, websocket diagnostics drawer, market projection telemetry, and connection scoring logic will be separated into smaller modules with explicit contracts.

Users should still be able to open the trade page, use the mobile bottom nav, click the footer connection control to open diagnostics, inspect transport and stream health, and run reconnect or reset actions without regressions. Contributors should be able to verify the change by reading smaller namespaces, running targeted diagnostics tests plus the required repository gates, and confirming that the footer facade remains stable.

## Progress

- [x] (2026-03-12 20:43 EDT) Audited `/hyperopen/src/hyperopen/views/footer_view.cljs`, `/hyperopen/test/hyperopen/views/footer_view_test.cljs`, and the existing websocket diagnostics seams already present in `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`, `/hyperopen/src/hyperopen/websocket/diagnostics_payload.cljs`, and `/hyperopen/src/hyperopen/websocket/diagnostics_sanitize.cljs`.
- [x] (2026-03-12 20:58 EDT) Created and claimed `bd` issue `hyperopen-y7gi` for this refactor so the work matched the local tracker policy at the time.
- [x] (2026-03-12 21:00 EDT) Authored this active ExecPlan with concrete namespace targets, milestone ordering, correctness fixes, and validation gates.
- [x] (2026-03-12 21:12 EDT) Extracted canonical diagnostics taxonomy and normalization into `/hyperopen/src/hyperopen/websocket/diagnostics/catalog.cljs` and `/hyperopen/src/hyperopen/websocket/diagnostics/schema.cljs`, including explicit `:n-a` -> `:event-driven` normalization and `nil` -> `:unknown`.
- [x] (2026-03-12 21:14 EDT) Moved connection scoring, status dominance, cooldown labeling, and reset or reconnect availability rules into `/hyperopen/src/hyperopen/websocket/diagnostics/policy.cljs`, and updated `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`, `/hyperopen/src/hyperopen/websocket/diagnostics_runtime.cljs`, and `/hyperopen/src/hyperopen/websocket/health_runtime.cljs` to delegate to the shared policy seam.
- [x] (2026-03-12 21:17 EDT) Built `/hyperopen/src/hyperopen/websocket/diagnostics/view_model.cljs` so the footer and diagnostics drawer render from prepared, sanitized data instead of spelunking raw app state.
- [x] (2026-03-12 21:19 EDT) Split rendering into `/hyperopen/src/hyperopen/views/footer/connection_meter.cljs`, `/hyperopen/src/hyperopen/views/footer/mobile_nav.cljs`, `/hyperopen/src/hyperopen/views/footer/links.cljs`, `/hyperopen/src/hyperopen/views/footer/diagnostics_drawer.cljs`, and `/hyperopen/src/hyperopen/views/footer/market_projection_diagnostics.cljs`, while shrinking `/hyperopen/src/hyperopen/views/footer_view.cljs` to a 77-line facade.
- [x] (2026-03-12 21:24 EDT) Added pure diagnostics tests under `/hyperopen/test/hyperopen/websocket/diagnostics/`, extended footer integration coverage for visible browser-network penalties, and passed `npm test`.
- [x] (2026-03-12 21:25 EDT) Passed `npm run test:websocket` with the new diagnostics modules included in the websocket-focused suite.
- [x] (2026-03-12 21:27 EDT) Passed `npm run check`, updated this ExecPlan with final evidence, and prepared the file to move from `active/` to `completed/`.

## Surprises & Discoveries

- Observation: `/hyperopen/src/hyperopen/views/footer_view.cljs` is currently 1230 lines, and the paired view test file is 752 lines, which means even the regression surface is concentrated around one oversized namespace.
  Evidence: `wc -l src/hyperopen/views/footer_view.cljs test/hyperopen/views/footer_view_test.cljs` returned `1230` and `752`.

- Observation: Some diagnostics behavior that looks like view logic is already partially owned elsewhere. Reconnect and reset gating exists in `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`, while diagnostics copy payload shaping exists in `/hyperopen/src/hyperopen/websocket/diagnostics_payload.cljs`.
  Evidence: `reconnect-blocked?`, `reset-blocked?`, and `diagnostics-copy-payload` are already defined outside the view namespace.

- Observation: The current footer view duplicates taxonomy in several parallel structures. Group ordering, ranking, labels, and weights are scattered across `group-priority`, `group-rank`, `meter-group-weight`, and `group-title`; status semantics are similarly split across label, tone, neutrality, and penalty helpers.
  Evidence: `/hyperopen/src/hyperopen/views/footer_view.cljs` defines separate vars and functions for the same concepts near lines 31-164 and 732-769.

- Observation: The current diagnostics drawer already contains at least one correctness hazard independent of architecture: `view-now-ms` can return `0` when `:generated-at-ms` is absent, which can make age rendering and cooldown math lie.
  Evidence: `/hyperopen/src/hyperopen/views/footer_view.cljs` `view-now-ms` returns `generated*` when the input is below `1000000000000`, and `generated*` is `(or generated-at-ms 0)`.

- Observation: Display ages and cooldown gating need different time semantics. Using wall-clock time for all numeric `generated-at-ms` values broke the repo’s synthetic low-number fixtures, while using snapshot time everywhere would preserve the original bug for missing timestamps.
  Evidence: The first implementation changed reconnect and reset cooldown tests in `/hyperopen/test/hyperopen/core_bootstrap/websocket_diagnostics_test.cljs` and `/hyperopen/test/hyperopen/views/footer_view_test.cljs` because test fixtures use `5000`, `10000`, and `12000` as synthetic snapshot times.

- Observation: The copy-fallback sanitization guarantee already exists upstream, so the view-model layer can safely treat `:fallback-json` as pre-redacted output instead of reparsing JSON strings in the UI.
  Evidence: `/hyperopen/src/hyperopen/websocket/diagnostics_copy.cljs` applies `diagnostics-sanitize/sanitize-value :redact` before serializing the payload, and `/hyperopen/test/hyperopen/core_bootstrap/websocket_diagnostics_test.cljs` already asserts that fallback JSON contains `<redacted>`.

## Decision Log

- Decision: Keep `hyperopen.views.footer-view/footer-view` as the stable public entrypoint and make it a composition-only facade instead of renaming call sites.
  Rationale: `/hyperopen/src/hyperopen/views/app_view.cljs` already depends on this namespace, and the refactor goal is boundary cleanup, not public API churn.
  Date/Author: 2026-03-12 / Codex

- Decision: Add new pure modules under `/hyperopen/src/hyperopen/websocket/diagnostics/` for catalog, schema, policy, and view-model work instead of extending the already overloaded footer namespace or inventing a second diagnostics ownership path.
  Rationale: The repo already has websocket diagnostics behavior gathered under `hyperopen.websocket.diagnostics_*`; creating a dedicated `diagnostics/` subtree makes the bounded context explicit while preserving local discoverability.
  Date/Author: 2026-03-12 / Codex

- Decision: Split render code under `/hyperopen/src/hyperopen/views/footer/` by presentation concern, not by arbitrary file size alone.
  Rationale: The mobile nav, links, diagnostics drawer, and market projection subsections have distinct reasons to change and distinct test surfaces; extracting them by responsibility reduces the chance of cross-cutting regressions.
  Date/Author: 2026-03-12 / Codex

- Decision: Treat status normalization as a boundary problem, not a rendering convenience.
  Rationale: The current `:n-a` token means different things in different helpers. Normalizing wire data once and deriving display labels from a single catalog is safer than continuing to scatter special cases through the view.
  Date/Author: 2026-03-12 / Codex

- Decision: Split timestamp handling into snapshot-oriented display time and wall-clock-aware effective time.
  Rationale: Display ages should follow the health snapshot so the UI remains deterministic under test fixtures and stale captures, while reconnect and reset cooldown logic must still fall back to wall time when the snapshot timestamp is missing.
  Date/Author: 2026-03-12 / Codex

- Decision: Keep the browser `navigator.connection` and build-id reads in the footer facade as environment assembly, and pass plain values into the pure diagnostics view-model builder.
  Rationale: This preserves dependency inversion for diagnostics policy and view-model code without forcing a new infrastructure namespace for a single refactor slice.
  Date/Author: 2026-03-12 / Codex

- Decision: Reuse the existing copy-redaction pipeline and document that contract instead of adding a second JSON-sanitization pass in the drawer.
  Rationale: `diagnostics_copy.cljs` already redacts the payload before serialization. Re-sanitizing raw JSON strings in the view would add complexity without improving safety.
  Date/Author: 2026-03-12 / Codex

## Outcomes & Retrospective

Implementation is complete.

The footer entrypoint is now a 77-line composition facade in `/hyperopen/src/hyperopen/views/footer_view.cljs`. Diagnostics semantics live under `/hyperopen/src/hyperopen/websocket/diagnostics/`, and rendering is split across five footer-focused namespaces under `/hyperopen/src/hyperopen/views/footer/`. The new pure diagnostics modules are all below the repository’s 500-line ceiling: `catalog.cljs` 164 lines, `schema.cljs` 152 lines, `policy.cljs` 437 lines, and `view_model.cljs` 439 lines.

Validation outcomes:

- `npm test`: pass (`Ran 2359 tests containing 12379 assertions. 0 failures, 0 errors.`)
- `npm run test:websocket`: pass (`Ran 385 tests containing 2187 assertions. 0 failures, 0 errors.`)
- `npm run check`: pass (docs, lint, and all required app/portfolio/test compiles succeeded).

Complexity outcome: overall complexity decreased. The original 1230-line footer namespace mixed shell rendering, browser environment reads, policy, formatting, and diagnostics control-state derivation. The replacement keeps the public `footer-view` seam stable while concentrating taxonomy, policy, view-model shaping, and rendering into explicit modules with narrower reasons to change.

## Context and Orientation

The current footer entrypoint is `/hyperopen/src/hyperopen/views/footer_view.cljs`. It is consumed from `/hyperopen/src/hyperopen/views/app_view.cljs` and currently owns all of the following:

The footer shell itself, including desktop links and the lower-left connection control.

The mobile bottom navigation, including route and trade-surface interpretation via `/hyperopen/src/hyperopen/trade/layout_actions.cljs`.

The websocket diagnostics drawer, including reconnect and reset button labels, copy feedback, timeline rendering, stream rendering, and market projection diagnostics.

The connection meter algorithm, including weighted penalties, group and status ordering, browser network hint reads, and transport or stream freshness heuristics.

Several display-formatting and masking helpers, including age formatting, sensitive-value masking, and market projection telemetry labels.

The view receives the full app state instead of a narrow contract. In this plan, “view model” means a pure data map prepared for rendering. A view model resolves labels, booleans, breakdown rows, and sanitized strings before any Hiccup is built. In this plan, “catalog” means a single data map that defines metadata for statuses or groups once, such as labels, ranks, tones, neutrality, and scoring weights.

The current websocket diagnostics support code already includes:

- `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs` for action-side reset and reconnect effect gating.
- `/hyperopen/src/hyperopen/websocket/diagnostics_payload.cljs` for copy payload shaping.
- `/hyperopen/src/hyperopen/websocket/diagnostics_sanitize.cljs` for masking sensitive values.

The current tests are concentrated in `/hyperopen/test/hyperopen/views/footer_view_test.cljs`. Those tests cover meter behavior, diagnostics drawer sections, masking, reconnect and reset button wiring, market projection rendering, and mobile nav actions. That coverage should be preserved, but pure policy assertions should move into smaller module-specific tests so the footer facade test stops carrying every diagnostic rule indirectly.

## Plan of Work

### Milestone 1: Establish explicit diagnostics taxonomy and correctness contracts

Create pure modules under `/hyperopen/src/hyperopen/websocket/diagnostics/` for the semantics that are currently scattered through `footer_view.cljs`. At minimum, add a catalog namespace and a schema namespace.

The catalog namespace should define one canonical group catalog and one canonical status catalog. Group metadata must include the current ordering, display title, source label, and scoring weight in one place. Status metadata must include the normalized status key, human label, meter label, tone, neutrality, and any display fallback behavior in one place. If raw runtime payloads still use `:n-a`, normalize that token at the boundary into a clearer internal meaning such as `:event-driven` or another explicit status name chosen during implementation. Unknown values must remain unknown instead of silently falling back to `"transport"`.

The schema namespace should use the repository’s existing `cljs.spec.alpha` style, or a similarly lightweight contract mechanism already in-tree, to define the expected shapes for diagnostics health input and the prepared footer or diagnostics view model. The point is not runtime ceremony. The point is to stop forcing future contributors to reverse-engineer nested `get-in` shapes from the render code.

This milestone should also fix the correctness issues that are safe to settle at the boundary: `view-now-ms` must fall back to wall-clock time instead of `0`; missing store ids must render `"n/a"` instead of `""`; and missing or unknown statuses must be modeled consistently rather than treated as neutral in one helper and penalized elsewhere.

Acceptance for this milestone is a new pure test surface that proves normalization and correctness behavior directly, without needing to render the whole footer. Examples include tests that unknown sources stay unknown, missing timestamps do not produce fresh-looking ages, and `nil` or `:n-a` inputs normalize deterministically.

### Milestone 2: Extract pure diagnostics policy and scoring

Add a pure policy namespace, preferably `/hyperopen/src/hyperopen/websocket/diagnostics/policy.cljs` or `/hyperopen/src/hyperopen/websocket/diagnostics/scoring.cljs`, to own the rules that currently live inside the footer render file.

This namespace should own connection scoring, dominant status selection, transport and stream penalty breakdowns, browser network hint contribution interpretation, banner selection, and reconnect or reset availability modeling. It should return explicit data instead of only a final number. For example, if browser network hints affect the score, the returned model must expose that contribution so the diagnostics drawer can explain it to users instead of hiding it.

Do not move effect emission here. The existing action namespace should remain the owner of emitted effects and action ordering. The new policy layer should provide pure helpers that both the view-model builder and action layer can use for shared availability semantics. If action-side gating remains in `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`, update that namespace to delegate to the new pure policy functions instead of recomputing the rules independently.

This milestone should remove duplicate heuristics so the connection label and bar count derive from the same normalized status model or from one explicit scoring model. It should also eliminate duplicate group-order definitions and derive `meter-bars-total` from the actual rendered bar config instead of storing mismatched constants.

Acceptance for this milestone is a dedicated diagnostics policy test namespace proving intent with direct assertions. At minimum, prove that healthy transport scores better than delayed transport, delayed streams never improve the score, missing timestamps do not accidentally reduce age to zero, and reconnect or reset availability labels behave deterministically around cooldown windows.

### Milestone 3: Build a prepared footer and diagnostics view model

Add `/hyperopen/src/hyperopen/websocket/diagnostics/view_model.cljs` to translate raw app state plus a small set of injected collaborators into render-ready data. The injected collaborators should include the current time, build id, and browser network hint snapshot so the view model stays pure and the leaf renderers stop reading browser globals directly.

The view model should prepare at least these top-level keys:

- `:connection-meter` for the footer control, including label, tone, active bars, tooltip text, and breakdown data.
- `:banner` for the persistent orders or market offline banner.
- `:diagnostics` for the drawer, including transport rows, timeline rows, stream groups, reconnect and reset controls, copy feedback, and sensitivity state.
- `:market-projection` for telemetry cards and recent flush rows.
- `:mobile-nav` and `:footer-links` for the shell-specific render data.

All sensitive values shown in the drawer or market projection sections should be sanitized in the view-model layer unless the reveal flag is explicitly enabled. If `fallback-json` from copy status is intended to be pre-sanitized upstream, verify and document that guarantee. If the guarantee does not exist, sanitize it in the view-model path before rendering.

Acceptance for this milestone is that the render code can stop reaching into raw `state` and nested `health` maps for most decisions. The new view model should be testable as data, including masking behavior, group ordering, market projection empty states, and the new visible score breakdown rows.

### Milestone 4: Split the rendering surface and shrink the footer facade

Create smaller render namespaces under `/hyperopen/src/hyperopen/views/footer/`. The exact filenames may change slightly during implementation, but the intended ownership is:

- `/hyperopen/src/hyperopen/views/footer/mobile_nav.cljs` for the mobile bottom nav only.
- `/hyperopen/src/hyperopen/views/footer/links.cljs` for footer links only.
- `/hyperopen/src/hyperopen/views/footer/diagnostics_drawer.cljs` for the side-panel shell and diagnostics sections.
- `/hyperopen/src/hyperopen/views/footer/market_projection_diagnostics.cljs` for the market projection cards and recent flush table.

Reduce `/hyperopen/src/hyperopen/views/footer_view.cljs` to a small facade that builds the footer view model, chooses the correct z-layering, and composes the extracted render helpers. It should preserve the existing public var `footer-view` and the existing user action wiring.

This milestone should also remove the forward declaration and topologically order remaining helpers so the file is readable without scanning for deferred definitions. The final footer facade should not know the internal shape of websocket health beyond the view-model boundary.

Acceptance for this milestone is that `/hyperopen/src/hyperopen/views/footer_view.cljs` becomes an orchestration entrypoint instead of a god module, while the existing UI behavior and action dispatches still pass regression tests.

### Milestone 5: Rebalance tests and validate end-to-end

Split the current monolithic footer test surface so each layer has the right level of responsibility:

- Pure diagnostics catalog, schema, policy, and view-model tests under `/hyperopen/test/hyperopen/websocket/diagnostics/`.
- Focused render tests under `/hyperopen/test/hyperopen/views/footer/` for mobile nav, diagnostics drawer, and market projection presentation.
- A slimmer `/hyperopen/test/hyperopen/views/footer_view_test.cljs` that proves the public footer facade composes the expected sections and preserves key action wiring.

Validation must include the repository-required commands from `/hyperopen/AGENTS.md`:

`npm run check`

`npm test`

`npm run test:websocket`

If practical during implementation, add one manual smoke pass by opening the trade page and confirming that the desktop footer control opens diagnostics, the mobile nav actions still route correctly, and the drawer now explains every score contribution that affects the visible meter.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/1d98/hyperopen`.

1. Create and keep this plan linked to live work:

   `bd update hyperopen-y7gi --claim --json`

2. Create the new diagnostics pure modules and view render folders:

   `mkdir -p src/hyperopen/websocket/diagnostics src/hyperopen/views/footer test/hyperopen/websocket/diagnostics test/hyperopen/views/footer`

3. Extract and test the diagnostics catalog and contracts first, before moving large render sections.

4. Extract diagnostics policy and scoring, then update any shared action gating in `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs` to delegate to pure helpers instead of duplicating rules.

5. Add `/hyperopen/src/hyperopen/websocket/diagnostics/view_model.cljs`, convert render code to consume the prepared model, and verify that sensitive-value masking happens before rendering.

6. Split render code under `/hyperopen/src/hyperopen/views/footer/`, reduce `/hyperopen/src/hyperopen/views/footer_view.cljs` to a facade, and keep `hyperopen.views.footer-view/footer-view` stable for callers.

7. Rebalance tests and run the full validation suite:

   `npm run check`

   `npm test`

   `npm run test:websocket`

8. Update this ExecPlan as milestones land, then move it from `active` to `completed` only after validation passes and `hyperopen-y7gi` is closed.

## Validation and Acceptance

This refactor is complete only when all of the following are true:

1. `/hyperopen/src/hyperopen/views/footer_view.cljs` no longer owns diagnostics taxonomy, scoring, market projection rendering, mobile nav rendering, and footer shell rendering in one namespace.
2. The footer connection meter label, tone, active bars, and tooltip all derive from one consistent diagnostics model rather than parallel heuristics that can disagree.
3. Raw runtime status tokens such as `:n-a`, `nil`, and unknown values normalize consistently through a canonical catalog, and unknown source values do not silently render as `"transport"`.
4. `view-now-ms` or its replacement never returns `0` just because the snapshot timestamp is missing, and missing store ids render `"n/a"` instead of an empty string.
5. Browser network penalties, if still part of scoring, are visible in the diagnostics explanation instead of being a hidden contribution.
6. Sensitive values remain masked by default throughout the drawer and copy-fallback path unless the user explicitly enables reveal-sensitive mode.
7. The mobile bottom nav, banner rules, diagnostics drawer actions, market projection cards, recent flush table, and footer links preserve their current user-visible behavior and action dispatches.
8. `npm run check`, `npm test`, and `npm run test:websocket` all pass.

## Idempotence and Recovery

This migration should be performed incrementally and is safe to repeat because the new modules are additive until old code is deleted. If a split causes regressions, keep the new pure module in place and temporarily route the old view through it rather than moving logic back into the god module.

If a milestone stalls halfway, the safe stopping point is any state where the public `footer-view` entrypoint still renders correctly and all tests pass. Do not leave duplicated status or group catalogs in production code after a milestone is declared complete. Consolidation is part of the acceptance criteria, not optional cleanup.

Because the refactor changes module boundaries rather than remote state or persisted user data, recovery is primarily source-level: restore the previous facade call path, keep the new tests, and continue the extraction behind the stable entrypoint.

## Artifacts and Notes

Primary files expected to change:

- `/hyperopen/src/hyperopen/views/footer_view.cljs`
- `/hyperopen/src/hyperopen/views/footer/mobile_nav.cljs`
- `/hyperopen/src/hyperopen/views/footer/links.cljs`
- `/hyperopen/src/hyperopen/views/footer/diagnostics_drawer.cljs`
- `/hyperopen/src/hyperopen/views/footer/market_projection_diagnostics.cljs`
- `/hyperopen/src/hyperopen/websocket/diagnostics/catalog.cljs`
- `/hyperopen/src/hyperopen/websocket/diagnostics/schema.cljs`
- `/hyperopen/src/hyperopen/websocket/diagnostics/policy.cljs` or `/hyperopen/src/hyperopen/websocket/diagnostics/scoring.cljs`
- `/hyperopen/src/hyperopen/websocket/diagnostics/view_model.cljs`
- `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`
- `/hyperopen/test/hyperopen/views/footer_view_test.cljs`
- `/hyperopen/test/hyperopen/views/footer/*.cljs`
- `/hyperopen/test/hyperopen/websocket/diagnostics/*.cljs`

The footer facade must continue to satisfy the app shell integration in `/hyperopen/src/hyperopen/views/app_view.cljs`.

## Interfaces and Dependencies

No new external library is required for this refactor.

Expected internal interfaces after implementation:

- `/hyperopen/src/hyperopen/websocket/diagnostics/catalog.cljs` exposes canonical group and status metadata plus normalization helpers.
- `/hyperopen/src/hyperopen/websocket/diagnostics/schema.cljs` exposes specs for raw diagnostics input and prepared footer or diagnostics view-model output.
- `/hyperopen/src/hyperopen/websocket/diagnostics/policy.cljs` or `/hyperopen/src/hyperopen/websocket/diagnostics/scoring.cljs` exposes pure helpers for dominant status, score breakdowns, banner selection, and reconnect or reset availability modeling.
- `/hyperopen/src/hyperopen/websocket/diagnostics/view_model.cljs` exposes a pure builder that accepts raw app state and injected environment values and returns the prepared footer model.
- `/hyperopen/src/hyperopen/views/footer/mobile_nav.cljs`, `/hyperopen/src/hyperopen/views/footer/links.cljs`, `/hyperopen/src/hyperopen/views/footer/diagnostics_drawer.cljs`, and `/hyperopen/src/hyperopen/views/footer/market_projection_diagnostics.cljs` render Hiccup from already-prepared data.
- `/hyperopen/src/hyperopen/views/footer_view.cljs` remains the stable public facade and composition entrypoint.

Plan revision note: 2026-03-12 21:00 EDT - Created the initial active ExecPlan after auditing the footer namespace, existing websocket diagnostics seams, and repository planning requirements, and linked it to `hyperopen-y7gi`.
Plan revision note: 2026-03-12 21:27 EDT - Marked implementation complete after extracting diagnostics catalog, policy, view-model, and footer render seams; recorded the snapshot-time vs effective-time split; and added final validation evidence before moving the plan to `completed/`.
