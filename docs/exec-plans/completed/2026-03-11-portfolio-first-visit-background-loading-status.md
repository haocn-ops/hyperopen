# Add visible first-visit background loading status to `/portfolio`

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Linked `bd` issue: `hyperopen-b12`.

## Purpose / Big Picture

After this change, a user landing on `/portfolio` gets an immediate explanation that more analytics work is still running in the background. The page no longer relies on a below-the-fold spinner while benchmark history and performance metrics finish loading after the first chart paint.

The user-visible result is a compact route status banner that appears only while first-visit portfolio work is still pending, plus clearer copy inside the Performance Metrics surface itself. A user can open `/portfolio`, see what is still syncing, and watch the indicator disappear once the route is genuinely ready.

## Progress

- [x] (2026-03-11 20:00Z) Reviewed `/hyperopen/docs/PLANS.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-03-11 20:01Z) Created and claimed `bd` issue `hyperopen-b12`.
- [x] (2026-03-11 20:02Z) Diagnosed current gap in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` and `/hyperopen/src/hyperopen/views/portfolio/vm*.cljs`: only the lower metrics card consumed loading state, while benchmark and account-data progress were not surfaced above the fold.
- [x] (2026-03-11 20:06Z) Added a derived `:background-status` model in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` that summarizes initial portfolio data fetches, missing benchmark history, and worker-side performance metrics.
- [x] (2026-03-11 20:07Z) Rendered the new route-level banner and replaced the anonymous metrics spinner with explanatory loading copy in `/hyperopen/src/hyperopen/views/portfolio_view.cljs`.
- [x] (2026-03-11 20:08Z) Added regressions in `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` and `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`.
- [x] (2026-03-11 20:09Z) Installed missing workspace JS dependencies with `npm ci` so validation could run locally; the initial environment had no `node_modules`.
- [x] (2026-03-11 20:10Z) Ran required gates successfully: `npm run check`, `npm test`, and `npm run test:websocket`.

## Surprises & Discoveries

- Observation: the portfolio page already had a metrics loading flag, but it only affected the lower Performance Metrics card.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm/performance.cljs` exposes `:loading?`, and `/hyperopen/src/hyperopen/views/portfolio_view.cljs` previously used it only inside `performance-metrics-card`.

- Observation: benchmark history readiness is implicit today; there is no dedicated loading atom for selected benchmark candles.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm/chart.cljs` always includes selected benchmark series, but missing candles simply produce empty benchmark paths.

- Observation: local validation was initially blocked by missing JS dependencies rather than by code errors.
  Evidence: the first `npm test` run failed with `sh: shadow-cljs: command not found`, and the first `npm run check` compile failed because `@noble/secp256k1` was unavailable until `npm ci` populated `node_modules`.

- Observation: typography policy tests reject explicit sub-16px utility classes in view code.
  Evidence: `hyperopen.views.typography-scale-test` failed on `text-[11px]` in the first banner implementation, and passed after switching to `text-xs`.

## Decision Log

- Decision: add a route-level, above-the-fold status banner instead of relying only on the existing lower-card spinner.
  Rationale: the existing spinner could sit below the user’s first viewport, which failed the “timely status feedback” requirement even though the loading state technically existed.
  Date/Author: 2026-03-11 / Codex

- Decision: derive benchmark background status from the existing benchmark computation context rather than adding a new effect-side loading atom.
  Rationale: the benchmark fetch path is already deterministic and pure at the VM boundary; deriving “selected benchmark has no aligned rows yet” keeps side effects unchanged.
  Date/Author: 2026-03-11 / Codex

- Decision: limit the route banner’s account-data items to initial fetches by gating them on missing `loaded-at-ms` timestamps.
  Rationale: this keeps the banner focused on first-visit incompleteness instead of advertising every later refresh where prior data is already visible.
  Date/Author: 2026-03-11 / Codex

- Decision: keep the banner non-blocking and use the metrics card overlay only as a secondary local explanation.
  Rationale: users need feedback without losing chart interactivity or suffering a full-surface loading interruption.
  Date/Author: 2026-03-11 / Codex

## Outcomes & Retrospective

Implemented and validated.

What changed:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` now derives a `:background-status` model that names pending first-visit work.
- `/hyperopen/src/hyperopen/views/portfolio_view.cljs` now renders a top-of-route status banner when that model is active.
- `/hyperopen/src/hyperopen/views/portfolio_view.cljs` now gives the Performance Metrics overlay explicit copy: `Calculating performance metrics`.
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` and `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` now lock the new pending-state contract and rendering behavior.

Validation evidence:

- `npm test` passed: `Ran 2272 tests containing 11839 assertions. 0 failures, 0 errors.`
- `npm run check` passed, including docs/lint gates plus `app`, `portfolio`, `portfolio-worker`, and `test` compiles with `0 warnings`.
- `npm run test:websocket` passed: `Ran 373 tests containing 2131 assertions. 0 failures, 0 errors.`

Complexity assessment:

This change slightly increased local view-model complexity by adding a small derived status model, but it reduced overall user-facing complexity more than it added code complexity. The route now makes partial readiness explicit without introducing new effect flows or additional mutable loading state.

## Context and Orientation

The portfolio route is assembled in `/hyperopen/src/hyperopen/views/portfolio_view.cljs`. That file renders the top metric cards, the summary card, the chart card, and the lower account-info tabs that include Performance Metrics.

The view-model for this route lives in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` and delegates to helper modules:

- `/hyperopen/src/hyperopen/views/portfolio/vm/benchmarks.cljs` computes selected benchmark series inputs.
- `/hyperopen/src/hyperopen/views/portfolio/vm/chart.cljs` builds chart lines and treats missing benchmark candles as empty series.
- `/hyperopen/src/hyperopen/views/portfolio/vm/performance.cljs` computes the performance-metrics surface and exposes `:loading?`.
- `/hyperopen/src/hyperopen/views/portfolio/vm/metrics_bridge.cljs` toggles `[:portfolio-ui :metrics-loading?]` while the worker computes metrics.

Relevant existing state used by this implementation:

- `[:portfolio :loading?]` for the portfolio summary fetch.
- `[:portfolio :user-fees-loading?]` for the fees/volume fetch.
- `[:portfolio-ui :metrics-loading?]` for worker-side metric computation.
- `[:candles coin interval]` for selected benchmark history snapshots.

The missing behavior was not data loading itself; it was the lack of a visible summary of those states in the route’s top section.

## Plan of Work

### Milestone 1: Build a background-loading summary in the portfolio VM

Done. `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` now exposes a small `:background-status` model that answers whether first-visit work is still pending, which tasks are pending, and what copy the UI should show.

### Milestone 2: Render a visible but non-blocking status treatment

Done. `/hyperopen/src/hyperopen/views/portfolio_view.cljs` now renders a compact banner directly under the page header while `:background-status` is active, and the Performance Metrics overlay now tells the user what is being computed.

### Milestone 3: Lock behavior with tests

Done. New tests prove the VM surfaces the correct pending-task labels and the view renders both the banner and the explicit metrics-loading copy.

### Milestone 4: Validate and close

Done. The required gates passed after installing missing local dependencies with `npm ci`.

## Concrete Steps

1. Edited `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` to add the derived background-status model.
2. Edited `/hyperopen/src/hyperopen/views/portfolio_view.cljs` to render the banner and improve metrics-loading copy.
3. Edited `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs` and `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs` for regressions.
4. Installed workspace dependencies from `/hyperopen`:

       npm ci

5. Ran validation from `/hyperopen`:

       npm test
       npm run check
       npm run test:websocket

## Validation and Acceptance

Acceptance criteria met:

1. On a first-visit loading state, `/portfolio` now shows a clear, above-the-fold status treatment that names pending background work.
2. The new status treatment disappears automatically when pending work is absent because it is derived from existing route state.
3. The Performance Metrics card now explains what is loading instead of showing a bare spinner.
4. `npm run check`, `npm test`, and `npm run test:websocket` all passed.

## Idempotence and Recovery

The change is localized to the portfolio VM/view/tests. Re-running `npm ci`, `npm test`, `npm run check`, and `npm run test:websocket` is safe.

If the banner later proves too noisy, recovery is straightforward: remove the call site in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` while keeping the VM derivation and tests as the baseline for a revised treatment.

## Artifacts and Notes

Primary code targets:

- `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
- `/hyperopen/src/hyperopen/views/portfolio_view.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/vm_test.cljs`
- `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`

Issue tracking target:

- `hyperopen-b12`

## Interfaces and Dependencies

No public API changed. The new behavior stays inside the portfolio route view-model and render layer.

Additive VM surface delivered:

- `[:background-status :visible?]`
- `[:background-status :title]`
- `[:background-status :detail]`
- `[:background-status :items]`

Each background-status item exposes a stable `:id` and user-facing `:label` so the view and tests can assert behavior without coupling to styling details.

Plan revision note: 2026-03-11 20:02Z - Initial active ExecPlan created after diagnosis and linked to `hyperopen-b12`.
Plan revision note: 2026-03-11 20:10Z - Marked implementation complete, recorded dependency-install surprise, captured validation results, and moved the plan to completed.
