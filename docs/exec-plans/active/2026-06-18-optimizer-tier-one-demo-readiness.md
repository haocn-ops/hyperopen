# Optimizer Tier-One Demo Readiness

This ExecPlan is a living document. Keep `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` current while the work proceeds.

## Purpose / Big Picture

The portfolio optimizer has a strong engine (real OSQP/closed-form solves, IndexedDB scenario save/load, real agent-signed execution) but cannot be credibly shown to a user. A multi-agent readiness audit (2026-06-18) confirmed four small, high-value gaps that block a demo. This plan closes those four "Tier 1" gaps so a scripted run on a clean universe, and a small-portfolio rebalance, are trustworthy end to end.

The four gaps and the target after this change:

1. **No entry point.** The optimizer is reachable only by hand-typing `/portfolio/optimize`. After: a global `Optimize` nav item plus an `Optimize` action on the own-portfolio header navigate into it.
2. **Run button lies.** The Run button stays enabled and shows a green "Ready to run" pill even when readiness already knows the run cannot succeed (no common history, missing assumptions, missing BL views). After: the button is disabled and the status pill names the blocking reason whenever `readiness :runnable?` is false.
3. **Dead "Constrain Frontier" toggle.** The results-page checkbox dispatches a bare-keyword Nexus placeholder (`:event.target/checked`) that never resolves, so the control silently does nothing. After: it uses the vector placeholder form `[:event.target/checked]` and actually toggles the constrained frontier.
4. **No `$10` minimum-notional gate.** Sub-`$10` rebalance legs become green `:ready` rows that Hyperliquid rejects at submit. After: such legs are `:blocked` with a `:below-min-notional` reason and are never offered for execution.

## Context Reference

Direct user request on 2026-06-18: create an execution plan for the Tier 1 optimizer readiness items (using best UX judgment, no further sign-off) and implement them. Findings come from the in-repo readiness audit summarized in the same session (ranks 1-4). Related active plans: `docs/exec-plans/active/2026-05-15-optimizer-api-v2-return-first-readiness.md`, `docs/exec-plans/active/2026-05-30-optimizer-from-holdings-usable-universe.md`.

## Progress

- [x] (2026-06-18) Audited current code for all four items and pinned exact edit sites and reuse points (readiness `:runnable?`/`:reason`, the correct `[:event.target/checked]` form, the `$10` floor precedent in `domain/trading/core.cljs`).
- [x] (2026-06-18) Item 1 - Entry points: added an `Optimize` nav item in the global header (`views/header/nav.cljs`, voice catalog `ui/voice.cljs`) and an `Optimize` action on the own-portfolio header (`views/portfolio/header.cljs`); `Portfolio` no longer co-highlights on optimize routes.
- [x] (2026-06-18) Item 2 - Readiness gate: threaded `readiness` into `setup-bottom-actions`; the status pill now names the blocking reason instead of a false green "Ready to run" whenever `:runnable?` is false. The Run button stays enabled (see Decision Log: the click is the intentional, tested "run retries anything still missing" affordance). Applies to both the standard and Use-my-views run buttons.
- [x] (2026-06-18) Item 3 - Constrain Frontier toggle: wrapped the placeholder in the vector form so the checkbox dispatches a real boolean.
- [x] (2026-06-18) Item 4 - Minimum-notional gate: sub-`$10` rebalance legs are now `:blocked` with a `:below-min-notional` reason and a "Below $10 minimum" human label; they are excluded from execution-ready rows.
- [x] (2026-06-18) Added focused tests: `setup_actions_test.cljs` (run-status + pill/button), `frontier_chart_toolbar_test.cljs` (vector placeholder), and a rebalance domain test for the `$10` floor; updated the tests that pinned prior behavior (nav lists, voice, action-button count, the constrain-frontier masking test, the below-lot fixture).
- [x] (2026-06-18) `npm test` green (4771 tests, 0 failures); `app` + `portfolio` production builds compile with 0 warnings; affected lint gates pass (`lint:docs` structure, `lint:hiccup`, `lint:theme-colors`, `lint:namespace-sizes`, `lint:namespace-boundaries`, `lint:input-parsing`, `lint:optimizer-contract-paths`).
- [ ] Resolve the unrelated pre-existing `lint:docs` stale-doc failure (`docs/design-docs/core-beliefs.md`, 92 days old) so the full `npm run check` chain is green, then move this ExecPlan to `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The readiness MODEL is already correct - `build-readiness` returns `:runnable?` and a precise `:reason` (`:no-eligible-history`/`:incomplete-history`/`:missing-history-assumptions`/`:history-loading`/`:missing-black-litterman-views`). Only the Run button and status pill ignore it.
  Evidence: `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` `build-readiness` (≈461-515); `src/hyperopen/views/portfolio/optimize/setup_actions.cljs` gates only on `run-triggerable?`.
- Observation: The correct Nexus placeholder form is already used a few lines away in the same feature, so item 3 is a one-token fix with a known-good reference.
  Evidence: `src/hyperopen/views/portfolio/optimize/instrument_overrides_panel.cljs` uses `[:event.target/checked]`.
- Observation: A `$10` order-notional floor already exists for scale/TWAP, confirming Hyperliquid enforces it; the optimizer rebalance path simply lacks the check.
  Evidence: `src/hyperopen/domain/trading/core.cljs` `scale-min-endpoint-notional`/`twap-min-suborder-notional` = 10.
- Surprise (mid-implementation): Disabling the Run button on a non-runnable readiness contradicts an INTENTIONAL, tested affordance - clicking Run with missing/incomplete history is a deliberate "run retries anything still missing" history-load trigger.
  Evidence: `workspace_view_test.cljs` `...allows-one-click-run-when-history-is-missing-test` and `universe_panel_test.cljs` `...blocks-run-when-retained-history-misses-assets-test` both assert the Run button stays enabled (`:disabled false`) with missing history; readiness copy literally reads "Run Optimization retries anything still missing." This reframed Item 2 from "disable the button" to "make the status pill honest while keeping the button clickable".
- Surprise: A pre-existing test (`rebalance_test.cljs` below-lot case) used a sub-`$10` notional, so the new floor fired before the lot gate. Fixed by giving that fixture a `$12` notional that still rounds below the lot, keeping each gate independently covered.
- Surprise: `results_panel_test.cljs` pinned the BUGGY bare-keyword dispatch form for the Constrain Frontier checkbox - exactly the masking test the audit predicted; updated it to the vector form.

## Decision Log

- Decision: Derive the run state in the VIEW (`setup-actions/run-status`, a pure tested fn) rather than folding readiness into the view-model's `run-triggerable?`.
  Rationale: Keeps `run-triggerable?` meaning "has assets and not running" so the pill can still distinguish "no assets" from "assets but blocked"; avoids changing `run-identity`/stale-run semantics and the existing view-model test.
  Date/Author: 2026-06-18 / Claude
- Decision (REVISED mid-implementation): Fix Item 2 by making the STATUS PILL honest while keeping the Run button clickable - NOT by disabling the button.
  Rationale: The QA P1 defect is the lying green "Ready to run" pill, not the clickable button. Clicking Run on missing history is an intentional, tested retry/load affordance (see Surprises). So the pill now never shows green "Ready to run" while `:runnable?` is false - it shows a short reason (e.g. "History incomplete", "Loading history", "Needs assumptions", "Add a view") with an amber dot - while the button stays enabled to preserve the retry. The right-rail Readiness panel keeps the full explanation. This removes the demo-fatal contradiction without regressing the deliberate run-as-retry design.
  Date/Author: 2026-06-18 / Claude
- Decision: Define a local `min-order-notional-usd` (= 10) constant in `domain/rebalance.cljs` and cross-reference the trading-core precedent, instead of importing the scale/TWAP constants.
  Rationale: Keeps the optimizer domain decoupled from the scale/TWAP trading domain while documenting that both encode the same Hyperliquid `$10` floor.
  Date/Author: 2026-06-18 / Claude
- Decision: Entry-point nav item routes to the optimizer index (`/portfolio/optimize`, the feature home); the own-portfolio header action routes to setup (`/portfolio/optimize/new`) so "optimize this portfolio" lands where "From holdings" is available.
  Rationale: Matches user intent per surface; carrying foreign trader-inspection context is explicitly out of scope (Tier 2).
  Date/Author: 2026-06-18 / Claude

## Validation and Acceptance

- Item 1: The global header shows an `Optimize` item that navigates to `/portfolio/optimize`; the own-portfolio header shows an `Optimize` action that navigates to `/portfolio/optimize/new`; `Portfolio` is not highlighted while on an optimize route.
- Item 2: With a non-runnable readiness (e.g. `:no-eligible-history`/`:incomplete-history`/`:missing-history-assumptions`/`:missing-black-litterman-views`), the Run button is `:disabled` and the status pill shows the blocking reason, not green "Ready to run". With a runnable readiness and assets present, the button is enabled and the pill is green. Both the standard and Use-my-views run buttons honor this.
- Item 3: The Constrain Frontier checkbox dispatches `[:actions/set-portfolio-optimizer-constrain-frontier [:event.target/checked]]`; toggling it persists the real boolean and selects the constrained frontier.
- Item 4: A rebalance leg whose absolute delta notional is below `$10` resolves to `{:status :blocked :reason :below-min-notional}`, is excluded from execution-ready rows, and renders a human label rather than a raw token.
- Tests: focused optimizer tests pass; required gates pass.

Required final commands:

- `npm run check`
- `npm test`

## Outcomes & Retrospective

All four Tier-1 items are implemented and validated: `npm test` is green (4771 tests, 0 failures, +8 new assertions), the `app` and `portfolio` production builds compile with 0 warnings, and every affected lint gate passes. The optimizer now has discoverable entry points, an honest run-status pill (no more green "Ready to run" over a blocked setup), a working Constrain Frontier toggle, and a `$10` minimum-notional gate that keeps doomed sub-`$10` legs out of execution.

The one open item is unrelated to this work: the full `npm run check` chain is blocked by a pre-existing stale governed doc (`docs/design-docs/core-beliefs.md`, last reviewed > 90 days ago). This plan stays in `active/` until that is resolved (a `last_reviewed` refresh by a doc owner) and then moves to `completed/`.

Retro note: the most valuable mid-flight correction was reframing Item 2 away from "disable the Run button" to "make the pill honest" once the tests surfaced the deliberate run-as-retry affordance - a reminder that an audit finding's recommended mechanism should be checked against the existing design's intent before implementing it literally.
