# Refactor duplicated chart hover tooltip projection into a shared pure seam

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Tracked issue: `hyperopen-42c` ("Extract shared chart tooltip projection seam").

## Purpose / Big Picture

After this change, agents and humans should be able to find chart hover tooltip rules in one deterministic place instead of reverse-engineering two branch-heavy view functions. The visible behavior should stay the same: portfolio and vault detail charts still show the same timestamp, metric value, and benchmark rows, but the assembly logic moves into pure tooltip projectors that can be tested directly without rendering the entire view tree.

## Progress

- [x] 2026-03-11 00:07Z Created and claimed `hyperopen-42c` for this refactor.
- [x] 2026-03-11 00:08Z Inspected the duplicated tooltip builders in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs`, plus the existing chart model seams in `/hyperopen/src/hyperopen/views/portfolio/vm/chart.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`.
- [x] 2026-03-11 00:12Z Added `/hyperopen/src/hyperopen/views/chart/tooltip_core.cljs` plus bounded-context adapters in `/hyperopen/src/hyperopen/views/portfolio/vm/chart_tooltip.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail/chart_tooltip.cljs`.
- [x] 2026-03-11 00:14Z Rewired `/hyperopen/src/hyperopen/views/portfolio/vm/chart.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs` to compute `:hover-tooltip`, then removed tooltip assembly from the two view namespaces.
- [x] 2026-03-11 00:16Z Added focused helper coverage in `/hyperopen/test/hyperopen/views/chart/tooltip_core_test.cljs`, `/hyperopen/test/hyperopen/views/portfolio/vm/chart_tooltip_test.cljs`, and `/hyperopen/test/hyperopen/views/vaults/detail/chart_tooltip_test.cljs`, while trimming private-var tooltip assertions from the vault chart view test.
- [x] 2026-03-11 00:20Z Restored missing local npm dependencies with `npm ci`, then passed `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] 2026-03-11 00:20Z Moved this ExecPlan to `completed` and prepared `hyperopen-42c` for closure.

## Surprises & Discoveries

- Observation: the duplicated hotspot is not only in the benchmark-row function. Both view namespaces also duplicate timestamp selection, metric labels, and tone-class rules.
  Evidence: `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs` defines `chart-tooltip-model`, `chart-tooltip-benchmark-values`, `tooltip-metric-label`, and `tooltip-value-classes`; `/hyperopen/src/hyperopen/views/portfolio_view.cljs` defines the same concepts with only formatter differences.

- Observation: the repository already uses a pure tooltip-model pattern inside a view namespace for another surface.
  Evidence: `/hyperopen/src/hyperopen/views/active_asset_view.cljs` contains `funding-tooltip-model` plus a memoized wrapper, showing that tooltip content can be modeled separately from DOM rendering.

- Observation: current tooltip behavior is mostly protected by whole-view rendering tests, which makes the hotspot hard to change safely.
  Evidence: portfolio tooltip assertions live mainly in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`, while direct pure coverage exists only for vault detail private vars in `/hyperopen/test/hyperopen/views/vaults/detail/chart_view_test.cljs`.

- Observation: the required validation gates depended on a missing local npm install rather than a source-level build failure.
  Evidence: the first `npm run check` failed during `shadow-cljs compile app` with `The required JS dependency "@noble/secp256k1" is not available`; running `npm ci` restored `node_modules` and the same gate then passed cleanly.

- Observation: the generated test runner inventory must be regenerated when new test namespaces are added.
  Evidence: `test/test_runner_generated.cljs` changed after `npm run test:runner:generate` because the new tooltip test namespaces had to be included in the compiled test bundle.

## Decision Log

- Decision: introduce a shared pure tooltip core under `/hyperopen/src/hyperopen/views/chart/` instead of copying one tooltip builder into the other bounded context.
  Rationale: the overlap is presentation-policy logic, not portfolio or vault business logic. A shared core reduces duplicate branching while still allowing each bounded context to inject its own formatting rules.
  Date/Author: 2026-03-11 / Codex

- Decision: keep Hiccup tooltip markup in the existing view namespaces for this refactor.
  Rationale: the problem is duplicated projection logic, not duplicated markup structure. Moving DOM rendering at the same time would widen the blast radius without improving the architectural seam enough to justify it.
  Date/Author: 2026-03-11 / Codex

- Decision: prefer chart-model enrichment over view-local recomputation.
  Rationale: `/hyperopen/ARCHITECTURE.md` requires pure decision logic and focused responsibilities. Chart models already own normalized series and hover state, so tooltip projection belongs there or in helpers they call, not in the final rendering function.
  Date/Author: 2026-03-11 / Codex

- Decision: keep metric label and tone-class defaults in the shared core, while leaving number/date formatting inside the bounded-context adapters.
  Rationale: the label and tone rules were identical across portfolio and vault detail, so centralizing them removes duplicate branching. Currency/date formatting still differs slightly by bounded context and remains local to avoid leaking context-specific formatting rules across modules.
  Date/Author: 2026-03-11 / Codex

## Outcomes & Retrospective

The refactor achieved the planned seam. The duplicated hover tooltip assembly now lives behind a shared pure core in `/hyperopen/src/hyperopen/views/chart/tooltip_core.cljs`, with context-specific formatters in `/hyperopen/src/hyperopen/views/portfolio/vm/chart_tooltip.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail/chart_tooltip.cljs`. Portfolio and vault detail chart models now expose `:hover-tooltip`, and the two view namespaces only handle tooltip positioning and DOM structure.

Overall complexity decreased. The view entry points lost the highest-branching tooltip helpers, and the important control flow now lives in pure projector functions with direct tests. The remaining complexity is easier to navigate because shared presentation policy is centralized while bounded-context formatting stays local.

Validation passed after restoring missing local npm dependencies: `npm run check`, `npm test`, and `npm run test:websocket` all exited successfully.

## Context and Orientation

The duplicated logic lives in two UI entry-point namespaces:

- `/hyperopen/src/hyperopen/views/portfolio_view.cljs` renders the portfolio page and currently computes chart hover tooltip content inline in private helpers near `chart-card`.
- `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs` renders the vault detail chart panel and currently computes the same kind of content inline in private helpers near `chart-section`.

Both screens consume chart models that are already built elsewhere:

- `/hyperopen/src/hyperopen/views/portfolio/vm/chart.cljs` constructs the portfolio chart series, hover point, and benchmark series metadata.
- `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs` builds the vault detail view model, including the chart map that is later rendered by the view.
- `/hyperopen/src/hyperopen/views/vaults/detail/chart.cljs` is a pure chart-math helper namespace for the vault detail bounded context.

In this repository, a "view model" means a pure data projection prepared for rendering. A "tooltip projector" in this plan means a pure function that accepts normalized chart data and returns a render-ready map such as `{:timestamp ... :metric-label ... :metric-value ... :benchmark-values [...]}`.

The key constraint is to keep business-context formatting local. Portfolio returns percentages and currency values using its own formatting helpers; vault detail does the same with a slightly different currency formatter. The shared seam should handle control flow, filtering, and record shape, while each bounded context supplies its metric-specific formatting and label policy.

## Plan of Work

First, add a new shared namespace at `/hyperopen/src/hyperopen/views/chart/tooltip_core.cljs`. This namespace should contain only pure projection logic. It should not know about portfolio-specific labels such as `SPY (SPOT)` or vault-specific currency helpers. Instead, it should accept an options map with small injected functions or descriptor maps for timestamp formatting, metric formatting, metric label lookup, value-tone class lookup, and benchmark-series eligibility.

Second, add small bounded-context adapters. The portfolio side should live close to the existing chart view-model modules, likely in `/hyperopen/src/hyperopen/views/portfolio/vm/chart_tooltip.cljs`. The vault side should live near the vault detail chart helpers, likely in `/hyperopen/src/hyperopen/views/vaults/detail/chart_tooltip.cljs`. Each adapter should translate `selected-tab` or `axis-kind` into the shared tooltip-core contract and return a ready-to-render tooltip map.

Third, enrich the chart data earlier. Portfolio chart view code should stop calling private tooltip helpers and instead receive `:hover-tooltip` from the chart map built for the view. Vault detail should do the same by adding tooltip data to the `:chart` map assembled in `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs` or by calling the vault adapter before rendering. The exact ownership should stay pure and deterministic.

Fourth, delete the duplicated private tooltip helpers from the two view namespaces once callers are switched over. Keep any purely visual layout helpers, such as tooltip positioning and chart gutter width, in the view namespaces.

Fifth, rebalance tests. Add direct tests for the shared tooltip core and the two adapters. Keep one or two existing rendering tests to prove the tooltip still appears in the DOM with the expected strings and benchmark color, but remove reliance on private-var testing where the public helper seam gives clearer intent.

## Concrete Steps

From `/hyperopen`:

1. Create the new tooltip-core namespace and the two bounded-context adapter namespaces.
2. Update `/hyperopen/src/hyperopen/views/portfolio/vm/chart.cljs` to compute a `:hover-tooltip` field for the chart map, or another clearly named tooltip field if a more precise shape emerges during implementation.
3. Update `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs` to compute the vault chart tooltip field in the chart map.
4. Remove tooltip assembly helpers from the two view namespaces and make the Hiccup rendering read from the prebuilt tooltip map.
5. Add or update tests in:
   - `/hyperopen/test/hyperopen/views/chart/tooltip_core_test.cljs`
   - `/hyperopen/test/hyperopen/views/portfolio/vm/chart_tooltip_test.cljs`
   - `/hyperopen/test/hyperopen/views/vaults/detail/chart_tooltip_test.cljs`
   - existing rendering tests in `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`
   - existing rendering tests in `/hyperopen/test/hyperopen/views/vaults/detail/chart_view_test.cljs`

The commands to run after edits are:

    cd /Users/barry/.codex/worktrees/763b/hyperopen
    npm ci
    npm run check
    npm test
    npm run test:websocket

Expected outcome: all commands exit with status `0`, and the focused tooltip tests exercise timestamp selection, returns-only benchmark rows, fallback labels/colors, and positive/negative tone classes without requiring full view rendering.

## Validation and Acceptance

Acceptance is behavioral and structural.

Behavioral acceptance means the portfolio and vault detail chart hover tooltips still render the same visible strings for day and non-day ranges, still color benchmark rows with the matching series stroke, and still omit benchmark rows on non-returns charts.

Structural acceptance means there is one shared pure tooltip-core seam, the two view namespaces no longer assemble tooltip data directly, and new direct tests cover the tooltip projection rules. The visible rendering tests should prove the wiring, while the helper tests should prove the decision logic.

This acceptance bar was met by the final code. The new pure tests cover the core tooltip seam directly, and the existing portfolio/vault rendering tests still verify visible tooltip behavior.

## Idempotence and Recovery

This refactor was safe to repeat because it was additive first: create the new helper namespaces, switch callers, then delete the old duplicated helpers. The only non-source recovery step in this run was `npm ci` to restore missing local dependencies before compile gates. No storage, schema, or application runtime side effects were introduced by the source changes.

## Artifacts and Notes

Key hotspot locations at plan creation time:

- `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs:81`
- `/hyperopen/src/hyperopen/views/portfolio_view.cljs:175`

Key current tests at plan creation time:

- `/hyperopen/test/hyperopen/views/vaults/detail/chart_view_test.cljs`
- `/hyperopen/test/hyperopen/views/portfolio_view_test.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/vm/chart_helpers_test.cljs`

Key new artifacts after implementation:

- `/hyperopen/src/hyperopen/views/chart/tooltip_core.cljs`
- `/hyperopen/src/hyperopen/views/portfolio/vm/chart_tooltip.cljs`
- `/hyperopen/src/hyperopen/views/vaults/detail/chart_tooltip.cljs`
- `/hyperopen/test/hyperopen/views/chart/tooltip_core_test.cljs`
- `/hyperopen/test/hyperopen/views/portfolio/vm/chart_tooltip_test.cljs`
- `/hyperopen/test/hyperopen/views/vaults/detail/chart_tooltip_test.cljs`

## Interfaces and Dependencies

The new shared tooltip core should expose stable pure functions only. A suitable final surface is:

`hyperopen.views.chart.tooltip-core/build-hover-tooltip`
  Accepts a map with normalized chart hover data and a policy map of formatting functions, then returns either `nil` or a render-ready tooltip map.

`hyperopen.views.chart.tooltip-core/benchmark-rows`
  Accepts a hovered index, series collection, and a benchmark-row policy map, then returns a vector of render-ready benchmark row maps.

The portfolio adapter should expose a function shaped like:

`hyperopen.views.portfolio.vm.chart-tooltip/build-chart-hover-tooltip`

The vault detail adapter should expose a function shaped like:

`hyperopen.views.vaults.detail.chart-tooltip/build-chart-hover-tooltip`

Those adapter functions should remain pure and should hide context-specific label/value formatting from the shared tooltip core.

Revision note: created this plan on 2026-03-11 to execute `hyperopen-42c` after confirming the tooltip hotspot exists in both portfolio and vault detail chart views with only thin direct coverage.
Revision note: updated and moved this plan to `completed` on 2026-03-11 after landing the shared tooltip seam, adding focused tests, restoring missing local npm dependencies with `npm ci`, and passing all required validation gates.
