# Refactor Top Seven CRAP Hotspots

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-dnf9`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

The current top CRAP hotspots span funding balance normalization, Hyperunit lifecycle polling, leaderboard and vault endpoint normalization, portfolio tab parsing, and the trade route UI shell. After this change, users should see the same funding, leaderboard, vault, portfolio, chart-legend, and trade-route behavior they see today, but the implementation should stop concentrating branch-heavy logic inside seven oversized functions. A contributor should be able to run the unit and browser checks, refresh coverage, run the CRAP tool, and confirm that these hotspots were reduced by extracting smaller helpers and adding direct regression coverage instead of changing public behavior.

The visible proof is behavior-preserving. Withdrawable amounts and lifecycle polling still update correctly, leaderboard rows and vault snapshot ranges still normalize to the same keywords and metric defaults, portfolio account-info tabs still select the same stored values, the chart legend still renders the same DOM and updates on crosshair moves, and the trade route still renders the same panel layout, parity anchors, and mobile-surface behavior. The implementation proof is the refreshed CRAP output for the touched modules after `npm run coverage`.

## Progress

- [x] (2026-03-30 15:02Z) Audited `/hyperopen/AGENTS.md`, `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/BROWSER_TESTING.md`, and prior completed deCRAP ExecPlans.
- [x] (2026-03-30 15:07Z) Inspected the seven reported hotspot functions, their surrounding call sites, and the current direct or indirect tests in funding, portfolio, vault, leaderboard, legend, and trade-view suites.
- [x] (2026-03-30 15:11Z) Created and claimed `hyperopen-dnf9` for this hotspot wave.
- [x] (2026-03-30 15:15Z) Verified the current validation surface: `npm test` does not expose a supported namespace-filter flag, `npx shadow-cljs compile test` is the smallest fast compile gate, and UI work under `/hyperopen/src/hyperopen/views/**` still requires explicit Playwright plus governed browser-QA accounting.
- [x] (2026-03-30 15:20Z) Incorporated targeted subagent recommendations for acceptance coverage, edge-case coverage, refactor ordering, and the smallest trade-route browser gate.
- [x] (2026-03-30 15:24Z) Ran `npm run lint:docs` and `npm run lint:docs:test`; both passed.
- [x] (2026-03-30 15:43Z) Restored the missing JS toolchain with `npm ci`, which installed `node_modules` including the previously missing `zod` and `smol-toml` packages needed by `npm run check`.
- [x] (2026-03-30 15:58Z) Implemented the funding bucket in `/hyperopen/src/hyperopen/funding/domain/policy.cljs` and `/hyperopen/src/hyperopen/funding/application/lifecycle_polling.cljs`, added direct helper and polling-path tests, and reduced the original hotspot scores materially.
- [x] (2026-03-30 16:02Z) Implemented the API normalization bucket by introducing `/hyperopen/src/hyperopen/leaderboard/normalization.cljs`, delegating the leaderboard endpoint and cache to the shared normalizer, converting vault snapshot-key normalization to alias tables, and adding direct endpoint and normalizer tests.
- [x] (2026-03-30 16:07Z) Implemented the trade UI bucket by extracting legend orchestration helpers and a new pure trade layout seam in `/hyperopen/src/hyperopen/views/trade_view/layout_state.cljs`, while preserving existing DOM anchors, memoization boundaries, and browser-visible behavior.
- [x] (2026-03-30 16:09Z) Implemented the portfolio action bucket by replacing tab and summary-time-range branch chains with alias maps and widening alias coverage in `/hyperopen/test/hyperopen/portfolio/actions_test.cljs`.
- [x] (2026-03-30 16:11Z) Rebuilt the CLJS test artifacts and ran the focused impacted suites successfully: `91` tests, `487` assertions, `0` failures, `0` errors.
- [x] (2026-03-30 16:13Z) Ran `npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "trade"` successfully for the smallest committed browser regression on the trade route.
- [x] (2026-03-30 16:14Z) Completed governed browser QA with `npm run qa:design-ui -- --targets trade-route --manage-local-app`; the trade-route design review passed at `375`, `768`, `1280`, and `1440` viewports.
- [x] (2026-03-30 16:15Z) Ran `npm run check`; it passed after resolving namespace-size overages introduced during helper extraction.
- [x] (2026-03-30 16:16Z) Ran `npm run test:websocket`; it passed with `429` tests, `2434` assertions, `0` failures, `0` errors.
- [x] (2026-03-30 16:18Z) Ran `npm test`; the suite remains blocked by an unrelated error in `hyperopen.views.trading-chart.utils.chart-interop.series-test` (`ReferenceError: values__9980__auto___86434 is not defined`). This blocker prevents a clean full-suite `npm run coverage`, so focused coverage plus module-scoped CRAP reports were used for hotspot verification instead.

## Surprises & Discoveries

- Observation: `/hyperopen/test/hyperopen/funding/application/lifecycle_polling_test.cljs` is named after the lifecycle-polling namespace, but its current assertions mostly exercise `hyperopen.funding.effects/api-submit-funding-deposit!` and only cover the polling function indirectly.
  Evidence: the existing tests require `hyperopen.funding.effects` rather than `hyperopen.funding.application.lifecycle-polling`, and they assert submit-flow side effects instead of directly driving `start-hyperunit-lifecycle-polling!`.

- Observation: leaderboard normalization logic is duplicated almost line-for-line between `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs` and `/hyperopen/src/hyperopen/leaderboard/cache.cljs`, but there is no direct endpoint test namespace under `/hyperopen/test/hyperopen/api/endpoints/`.
  Evidence: the repo has `/hyperopen/test/hyperopen/leaderboard/cache_test.cljs` but no `/hyperopen/test/hyperopen/api/endpoints/leaderboard_test.cljs`, while both source files define `known-window-keys`, `normalize-window-key`, and `normalize-window-performance`.

- Observation: the funding hotspot `balance-row-available` already has a clearer two-stage equivalent in `/hyperopen/src/hyperopen/vaults/detail/transfer.cljs`.
  Evidence: `vaults/detail/transfer.cljs` splits the same concern into `direct-balance-row-available`, `derived-balance-row-available`, and `balance-row-available`, and `/hyperopen/test/hyperopen/vaults/detail/transfer_test.cljs` already covers the intended precedence and clamp behavior.

- Observation: `trade-view` already has broad composed-view coverage, so the highest UI refactor risk is not missing assertions but breaking memoization boundaries, parity anchors, or route QA by changing structure unnecessarily.
  Evidence: `/hyperopen/test/hyperopen/views/trade_view/layout_test.cljs`, `/hyperopen/test/hyperopen/views/trade_view/mobile_surface_test.cljs`, `/hyperopen/test/hyperopen/views/trade_view/render_cache_test.cljs`, and `/hyperopen/test/hyperopen/views/trade_view/loading_shell_test.cljs` together cover layout classes, mobile-surface routing, render caching, and loading-shell geometry.

- Observation: three touched namespaces already sit under explicit namespace-size exceptions, so this work should prefer extraction that reduces concentration without silently growing those files further.
  Evidence: `/hyperopen/dev/namespace_size_exceptions.edn` already lists `src/hyperopen/api/endpoints/vaults.cljs`, `src/hyperopen/funding/domain/policy.cljs`, and `src/hyperopen/views/trade_view.cljs`.

- Observation: this worktree initially had no `node_modules`, which made the first `npm run check` attempt fail before it could validate the actual hotspot work.
  Evidence: the 2026-03-30 local run reached `npm run test:multi-agent` and failed with `ERR_MODULE_NOT_FOUND` for packages `zod` and `smol-toml` until `npm ci` restored the missing JavaScript dependencies.

- Observation: the first refactor pass reduced the named hotspots, but two touched modules still surfaced new sibling hotspots and needed a second intra-module cleanup before the pass met the CRAP acceptance bar.
  Evidence: `resolve-poll-runtime` in `/hyperopen/src/hyperopen/funding/application/lifecycle_polling.cljs` and `normalize-summary-time-range` in `/hyperopen/src/hyperopen/portfolio/actions.cljs` became the next highest-scoring functions until they were converted to smaller helper seams and alias maps.

- Observation: repeated targeted CLJS runs temporarily left the generated test artifacts in a bad state, and the focused suite would not stabilize until those artifacts were rebuilt cleanly.
  Evidence: removing `.shadow-cljs/builds/test/dev` and `out/test.js*` and then rerunning `npx shadow-cljs compile test` restored a healthy `out/test.js` for the impacted-suite command.

- Observation: the required full `npm test` gate is still red for a reason unrelated to this refactor, which also makes a full `npm run coverage` refresh unreliable for CRAP verification.
  Evidence: the current full-suite error is in `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/series_test.cljs`, test `series-module-resolve-transform-and-extract-cover-registry-branches-test`, with `ReferenceError: values__9980__auto___86434 is not defined`.

- Observation: the three pre-existing namespace-size exceptions on policy, vaults, and trade-view were tight enough that helper extraction alone was not sufficient; the final pass also needed formatting and small structure compaction to satisfy lint without changing behavior.
  Evidence: `npm run lint:namespace-sizes` only passed after compacting the alias tables and helper layout in `/hyperopen/src/hyperopen/funding/domain/policy.cljs` and `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs`.

- Observation: the targeted hotspot in funding policy is fixed, but that module still has unrelated low-coverage hotspots that this plan did not widen into a broader policy rewrite.
  Evidence: the refreshed module CRAP report no longer flags `balance-row-available`, but it still reports `deposit-preview` and `send-preview` as separate low-coverage follow-up candidates.

## Decision Log

- Decision: keep this pass behavior-preserving and preserve all existing public entrypoints, return shapes, and DOM anchors.
  Rationale: the user asked for an execution plan to refactor CRAP hotspots, not to migrate contracts. The measurable finish line is lower CRAP and broader direct coverage without changing what callers or users observe.
  Date/Author: 2026-03-30 / Codex

- Decision: prefer local helper extraction for six of the seven hotspots, but treat leaderboard normalization as shared utility extraction because the endpoint and cache layers currently duplicate the same logic.
  Rationale: local extraction keeps risk low when the hotspot is private to one file, while the leaderboard duplication is already causing drift risk and has an obvious shared seam.
  Date/Author: 2026-03-30 / Codex

- Decision: do not widen this pass into a global time-range normalization abstraction across portfolio and vaults unless the exact accepted alias set proves identical during implementation.
  Rationale: `normalize-snapshot-key` and `normalize-summary-time-range` look similar, but the vault preview layer only accepts a subset for preview rendering and uses different downstream semantics. Forcing one global parser could turn a deCRAP pass into a behavior migration.
  Date/Author: 2026-03-30 / Codex

- Decision: for `trade-view`, reduce complexity by moving pure visibility and snapshot-derivation decisions out of the main `trade-view` body first, not by rewriting the hiccup tree or changing parity ids.
  Rationale: the existing tests and browser flows assert the rendered structure heavily. The safe seam is pure layout-state extraction plus local render helpers that keep the DOM contract intact.
  Date/Author: 2026-03-30 / Codex

- Decision: explicitly account for trade-route browser validation if either `trade-view` or `create-legend!` changes land.
  Rationale: both files live under the governed UI tree and affect the trade route. The repo contract requires the smallest relevant Playwright pass first and an explicit browser-QA accounting for UI work under `/hyperopen/src/hyperopen/views/**`.
  Date/Author: 2026-03-30 / Codex

## Outcomes & Retrospective

Implementation landed across all seven requested hotspots. The funding pass split `balance-row-available` into direct and derived availability helpers and decomposed `start-hyperunit-lifecycle-polling!` into smaller scheduling, lifecycle-merge, and callback helpers with direct polling tests. The API pass introduced `/hyperopen/src/hyperopen/leaderboard/normalization.cljs` to remove endpoint-cache duplication and replaced vault snapshot-key branching with data-driven alias tables. The UI pass extracted legend DOM/update helpers and introduced `/hyperopen/src/hyperopen/views/trade_view/layout_state.cljs` so `trade-view` now delegates pure layout decisions instead of holding them inline. The portfolio pass converted account-info-tab and summary-time-range normalization to alias maps and widened direct alias coverage.

The new direct tests cover the previously thin seams: `/hyperopen/test/hyperopen/api/endpoints/leaderboard_test.cljs`, `/hyperopen/test/hyperopen/leaderboard/normalization_test.cljs`, and `/hyperopen/test/hyperopen/views/trade_view/layout_state_test.cljs` were added, while the existing funding, vaults, portfolio, trade-view, and legend suites gained targeted regression cases. The focused impacted test run passed with `91` tests and `487` assertions. Browser validation for the governed trade route also passed: the committed smoke suite succeeded, and the governed `trade-route` design review passed at `375`, `768`, `1280`, and `1440` viewports.

The required repo gates are mostly green on the current tree. `npm run check` passed. `npm run test:websocket` passed. `npm test` is still blocked by an unrelated `series-test` `ReferenceError`, so the full coverage command was not reliable. To keep CRAP verification rigorous anyway, the pass used focused coverage for the touched namespaces plus `bb tools/crap_report.clj --module ... --format json` on each touched module. The resulting hotspot outcomes are:

- `/hyperopen/src/hyperopen/funding/domain/policy.cljs` `balance-row-available`: CRAP `41.40` -> `4.0`
- `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs` `normalize-window-performance`: CRAP `40.66` -> `1.0`
- `/hyperopen/src/hyperopen/funding/application/lifecycle_polling.cljs` `start-hyperunit-lifecycle-polling!`: CRAP `40.25` -> `2.0`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/legend.cljs` `create-legend!`: CRAP `39.01` -> `5.0`
- `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs` `normalize-snapshot-key`: removed from the module hotspot list; no crappy functions remain in the module
- `/hyperopen/src/hyperopen/views/trade_view.cljs` `trade-view`: removed from the module hotspot list; no crappy functions remain in the module
- `/hyperopen/src/hyperopen/portfolio/actions.cljs` `normalize-portfolio-account-info-tab`: CRAP `35.63` -> `2.0`

The remaining follow-up is outside the requested hotspot set: fix the unrelated `series-test` failure so the repo can rerun the full `npm test` and `npm run coverage` stack, and separately decide whether the remaining funding-policy hotspots (`deposit-preview`, `send-preview`) warrant their own follow-up ticket.

## Context and Orientation

This plan covers the following reported hotspot baselines from the user-supplied CRAP list:

- `/hyperopen/src/hyperopen/funding/domain/policy.cljs` `balance-row-available` — CRAP `41.40`, coverage `0.26`, complexity `9`
- `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs` `normalize-window-performance` — CRAP `40.66`, coverage `0.12`, complexity `7`
- `/hyperopen/src/hyperopen/funding/application/lifecycle_polling.cljs` `start-hyperunit-lifecycle-polling!` — CRAP `40.25`, coverage `0.75`, complexity `28`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/legend.cljs` `create-legend!` — CRAP `39.01`, coverage `0.98`, complexity `39`
- `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs` `normalize-snapshot-key` — CRAP `38.00`, coverage `1.00`, complexity `38`
- `/hyperopen/src/hyperopen/views/trade_view.cljs` `trade-view` — CRAP `36.00`, coverage `1.00`, complexity `34`
- `/hyperopen/src/hyperopen/portfolio/actions.cljs` `normalize-portfolio-account-info-tab` — CRAP `35.63`, coverage `0.41`, complexity `11`

The current tests around these hotspots are uneven. `create-legend!` and `trade-view` already have substantial direct tests, `normalize-snapshot-key` and `normalize-window-performance` are only covered indirectly through larger endpoint or cache behavior, `normalize-portfolio-account-info-tab` has thin alias coverage, `balance-row-available` is only exercised indirectly through higher-level withdrawal previews, and `start-hyperunit-lifecycle-polling!` is mostly covered through effect-level submission flows rather than direct polling control tests.

The recommended execution order is low-risk pure seams first, then stateful or UI-heavy seams: `balance-row-available`, `normalize-window-performance`, `normalize-snapshot-key`, `normalize-portfolio-account-info-tab`, `start-hyperunit-lifecycle-polling!`, `create-legend!`, and finally `trade-view`.

The key source and test files are:

- `/hyperopen/src/hyperopen/funding/domain/policy.cljs`
- `/hyperopen/test/hyperopen/funding/domain/policy_test.cljs`
- `/hyperopen/src/hyperopen/funding/application/lifecycle_polling.cljs`
- `/hyperopen/test/hyperopen/funding/application/lifecycle_polling_test.cljs`
- `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs`
- `/hyperopen/src/hyperopen/leaderboard/cache.cljs`
- `/hyperopen/test/hyperopen/leaderboard/cache_test.cljs`
- `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs`
- `/hyperopen/test/hyperopen/api/endpoints/vaults_test.cljs`
- `/hyperopen/src/hyperopen/portfolio/actions.cljs`
- `/hyperopen/test/hyperopen/portfolio/actions_test.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/legend.cljs`
- `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/legend_test.cljs`
- `/hyperopen/src/hyperopen/views/trade_view.cljs`
- `/hyperopen/test/hyperopen/views/trade_view/layout_test.cljs`
- `/hyperopen/test/hyperopen/views/trade_view/mobile_surface_test.cljs`
- `/hyperopen/test/hyperopen/views/trade_view/render_cache_test.cljs`
- `/hyperopen/test/hyperopen/views/trade_view/loading_shell_test.cljs`
- `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`

## Plan of Work

First, implement the funding bucket. In `/hyperopen/src/hyperopen/funding/domain/policy.cljs`, split `balance-row-available` into the same conceptual stages already proven in `/hyperopen/src/hyperopen/vaults/detail/transfer.cljs`: one helper for direct available fields, one helper for derived `total - hold`, and one thin top-level combiner that clamps finite values at zero. Preserve the current precedence of `:available`, `:availableBalance`, and `:free`, preserve the `:total` or `:totalBalance` fallback, and keep `nil` for non-map or non-numeric inputs. Extend `/hyperopen/test/hyperopen/funding/domain/policy_test.cljs` with direct assertions for those private helper seams or for the composed helper through var deref.

In `/hyperopen/src/hyperopen/funding/application/lifecycle_polling.cljs`, break `start-hyperunit-lifecycle-polling!` into a small coordinator plus helper functions for input eligibility, lifecycle-store updates, withdraw-queue refresh eligibility, success-path operation selection, error-path lifecycle merge, and next-poll scheduling. Keep the public options map unchanged and keep the deposit and withdraw wrapper fns as thin `assoc` shims. The direct tests should move into `/hyperopen/test/hyperopen/funding/application/lifecycle_polling_test.cljs` and cover no-op preconditions, stale-token cancellation, terminal lifecycle completion, withdraw-only queue refreshes, delay computation, and error retries that preserve prior lifecycle details.

Second, implement the API normalization bucket. Create a shared leaderboard normalization helper namespace, for example `/hyperopen/src/hyperopen/leaderboard/normalization.cljs`, that owns `known-window-keys`, window-key normalization, metric normalization, and default window performance maps. Delegate both `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs` and `/hyperopen/src/hyperopen/leaderboard/cache.cljs` to that shared logic so the endpoint and cache cannot drift. Add a new direct endpoint suite at `/hyperopen/test/hyperopen/api/endpoints/leaderboard_test.cljs` and, if needed, a shared normalizer test under `/hyperopen/test/hyperopen/leaderboard/`. The tests must prove that unknown metrics are ignored, `:vlm` still maps to `:volume`, invalid numbers fall back to zeroed metrics, negative values are preserved, and day/week/month/all-time defaults remain intact. The endpoint happy path should also prove that `request-leaderboard!` turns numeric strings into canonical `:pnl`, `:roi`, and `:volume` maps instead of relying only on cache-layer coverage.

Still in the API bucket, replace the branch-heavy `normalize-snapshot-key` implementation in `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs` with data-driven alias tables grouped by canonical range. Keep the accepted canonical outputs exactly as they are today: `:day`, `:week`, `:month`, `:three-month`, `:six-month`, `:one-year`, `:two-year`, and `:all-time`. Then widen `/hyperopen/test/hyperopen/api/endpoints/vaults_test.cljs` with direct alias coverage for representative variants such as `"3M"`, `"quarter"`, `"half-year"`, `"1Y"`, `"2year"`, and `"allTime"`, and prove that preview rendering still includes only the preview-safe keys while `normalize-vault-pnls` keeps the full accepted range set.

Third, implement the trade UI bucket. In `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/legend.cljs`, keep the `create-legend!` public API intact but extract DOM setup, session-indicator rendering, candle-value rendering, empty-state rendering, update handling, and destroy cleanup into smaller private helpers. The goal is for `create-legend!` to own orchestration only: choose the document, ensure the container is relatively positioned, build the DOM once, subscribe the crosshair handler, and return the `#js {:update ... :destroy ...}` control. Keep the `chart-market-status` node, default formatters, crosshair subscription behavior, and fallback `"-- (--)”` text unchanged. Extend the existing legend tests with one more update/destroy ordering case so header or session updates cannot leak stale candle state across `.update` calls.

Also in the trade UI bucket, refactor `/hyperopen/src/hyperopen/views/trade_view.cljs` by extracting pure layout-state and visibility decisions out of the main `trade-view` body. The safest form is a new private helper section or a new internal namespace such as `/hyperopen/src/hyperopen/views/trade_view/layout_state.cljs` that computes mobile-surface flags, freeze behavior, selector snapshots, and pre-rendered panel states. The main `trade-view` function should then assemble the same hiccup tree from those prepared values. Preserve `data-parity-id` and `data-role` anchors, preserve memoized render-count behavior, keep the current CSS class contracts, and do not change the freeze or visibility semantics already asserted by the existing composed-view tests.

Fourth, implement the portfolio bucket. Replace the long `case` inside `/hyperopen/src/hyperopen/portfolio/actions.cljs` `normalize-portfolio-account-info-tab` with a small alias map keyed by normalized tokens. Preserve the accepted canonical values and keep `:performance-metrics` as the default. Extend `/hyperopen/test/hyperopen/portfolio/actions_test.cljs` so it covers all meaningful aliases, not just the three currently asserted paths.

## Concrete Steps

From the repository root (`/hyperopen` in repo docs; this worktree is `/Users/barry/.codex/worktrees/382d/hyperopen`):

1. Edit the funding source and test files:

   - `/hyperopen/src/hyperopen/funding/domain/policy.cljs`
   - `/hyperopen/test/hyperopen/funding/domain/policy_test.cljs`
   - `/hyperopen/src/hyperopen/funding/application/lifecycle_polling.cljs`
   - `/hyperopen/test/hyperopen/funding/application/lifecycle_polling_test.cljs`

2. Edit or add the API normalization source and test files:

   - `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs`
   - `/hyperopen/src/hyperopen/leaderboard/cache.cljs`
   - `/hyperopen/src/hyperopen/leaderboard/normalization.cljs` if the shared extraction is introduced
   - `/hyperopen/test/hyperopen/api/endpoints/leaderboard_test.cljs`
   - `/hyperopen/test/hyperopen/leaderboard/cache_test.cljs`
   - `/hyperopen/test/hyperopen/leaderboard/normalization_test.cljs` if the shared extraction is introduced
   - `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs`
   - `/hyperopen/test/hyperopen/api/endpoints/vaults_test.cljs`

3. Edit the trade UI source and test files:

   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/legend.cljs`
   - `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/legend_test.cljs`
   - `/hyperopen/src/hyperopen/views/trade_view.cljs`
   - `/hyperopen/src/hyperopen/views/trade_view/layout_state.cljs` if a dedicated pure-layout namespace is introduced
   - `/hyperopen/test/hyperopen/views/trade_view/layout_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade_view/mobile_surface_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade_view/render_cache_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade_view/loading_shell_test.cljs`
   - `/hyperopen/test/hyperopen/views/trade_view/layout_state_test.cljs` if a dedicated pure-layout namespace is introduced

4. Edit the portfolio source and test files:

   - `/hyperopen/src/hyperopen/portfolio/actions.cljs`
   - `/hyperopen/test/hyperopen/portfolio/actions_test.cljs`

5. Use the smallest fast feedback commands first:

   npx shadow-cljs compile test

   This repo’s generated CLJS test runner currently does not expose a supported namespace-filter flag, so use the compile pass for the first local feedback loop before the full suite.

6. If the trade UI bucket lands, run the smallest relevant committed browser regression first:

   npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "trade"

7. After implementation is stable, run the required repository gates:

   npm run check
   npm test
   npm run test:websocket

8. Refresh coverage and CRAP evidence:

   npm run coverage
   bb tools/crap_report.clj --scope src
   bb tools/crap_report.clj --module src/hyperopen/funding/domain/policy.cljs --format json
   bb tools/crap_report.clj --module src/hyperopen/funding/application/lifecycle_polling.cljs --format json
   bb tools/crap_report.clj --module src/hyperopen/api/endpoints/leaderboard.cljs --format json
   bb tools/crap_report.clj --module src/hyperopen/api/endpoints/vaults.cljs --format json
   bb tools/crap_report.clj --module src/hyperopen/views/trading_chart/utils/chart_interop/legend.cljs --format json
   bb tools/crap_report.clj --module src/hyperopen/views/trade_view.cljs --format json
   bb tools/crap_report.clj --module src/hyperopen/portfolio/actions.cljs --format json

9. Account for governed browser QA on the trade route explicitly:

   npm run qa:design-ui -- --targets trade-route --manage-local-app

## Validation and Acceptance

Acceptance is behavior-based and hotspot-specific:

- `balance-row-available` still prefers direct available fields, still derives from `total - hold` when direct values are absent, still clamps negative finite results to zero, and now has direct tests for those branches.
- `start-hyperunit-lifecycle-polling!` still installs and clears poll tokens correctly, still updates lifecycle state only while the modal is active, still refreshes the withdraw queue only for withdraw flows, and now has direct tests for terminal, non-terminal, stale-token, and error-retry paths.
- leaderboard endpoint normalization still emits the same window keys and zero-default metric maps as the cache layer, and direct endpoint tests now prove that behavior without relying only on cache tests.
- vault snapshot-key normalization still accepts the same range aliases and still feeds preview and pnl normalization correctly, with direct alias assertions added to `/hyperopen/test/hyperopen/api/endpoints/vaults_test.cljs`.
- `create-legend!` still builds the same DOM contract, uses the same market-status semantics, uses the latest candle as the fallback when crosshair time does not match, unsubscribes correctly, and keeps the same update and destroy API.
- `trade-view` still renders the same layout, panel visibility, memoized render counts, parity anchors, and loading shell behavior across desktop and mobile states.
- `normalize-portfolio-account-info-tab` still maps the same aliases to the same stored tabs and still defaults to `:performance-metrics`.
- `npm run check`, `npm test`, `npm run test:websocket`, the best-available coverage refresh, the module-scoped CRAP reports above, the committed Playwright trade regression suite, and the governed `trade-route` design review all pass or produce an explicitly documented blocker.

The CRAP-specific acceptance rule is stricter than “the score moved a bit.” After the refreshed module reports are available, the listed hotspot in each touched module should be materially below its starting score, and implementation should not stop if the first rerun only moves the hotspot to a sibling function inside the same touched module.

## Idempotence and Recovery

This work is source-and-test refactoring only. Re-running the edits is safe because there is no persistence or migration step. If a refactor breaks behavior, restore the affected helper extraction in that bucket, rerun `npx shadow-cljs compile test`, and then rerun the smallest applicable higher-level check before returning to the full gate stack. If the CRAP tool reports missing or stale coverage, rerun `npm run coverage` before any `bb tools/crap_report.clj ...` command because the report reads `coverage/lcov.info`.

If `npx playwright test tools/playwright/test/trade-regressions.spec.mjs` or `npm run qa:design-ui -- --targets trade-route --manage-local-app` fails for a route-lab reason unrelated to the touched behavior, record that blocker in this plan and in `bd` instead of silently skipping browser accounting.

## Artifacts and Notes

The tracked issue is `hyperopen-dnf9`. This plan is the active execution artifact for the work.

Direct test gaps closed by this implementation:

- `/hyperopen/test/hyperopen/api/endpoints/leaderboard_test.cljs` now provides direct endpoint coverage for leaderboard normalization and request handling
- `/hyperopen/test/hyperopen/leaderboard/normalization_test.cljs` now locks the shared normalizer behavior used by both the endpoint and cache layers
- `/hyperopen/test/hyperopen/funding/application/lifecycle_polling_test.cljs` now directly targets the lifecycle-polling namespace instead of only effect-level submission flows
- `/hyperopen/test/hyperopen/portfolio/actions_test.cljs` now covers the expanded alias surface for account-info tabs and summary time ranges
- `/hyperopen/test/hyperopen/funding/domain/policy_test.cljs` now directly asserts balance-row precedence and derived-availability behavior
- `/hyperopen/test/hyperopen/views/trade_view/layout_state_test.cljs` now locks the pure trade-view layout seam introduced by this refactor

Relevant UI browser surface:

- `/hyperopen/src/hyperopen/views/trade_view.cljs`
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/legend.cljs`
- `/hyperopen/tools/playwright/test/routes.smoke.spec.mjs`
- `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`

## Interfaces and Dependencies

The following public or externally consumed interfaces must remain stable:

- `/hyperopen/src/hyperopen/funding/application/lifecycle_polling.cljs`

      (start-hyperunit-lifecycle-polling! opts)
      (start-hyperunit-deposit-lifecycle-polling! opts)
      (start-hyperunit-withdraw-lifecycle-polling! opts)

  The `opts` keys used by `/hyperopen/src/hyperopen/funding/effects.cljs` and `/hyperopen/test/hyperopen/funding/test_support/effects.cljs` must remain valid.

- `/hyperopen/src/hyperopen/api/endpoints/leaderboard.cljs`

      (normalize-window-performance payload)
      (normalize-window-performances payload)
      (normalize-leaderboard-row row)
      (normalize-leaderboard-rows payload)
      (request-leaderboard! fetch-fn url opts)

  These must keep the current row shape: `:eth-address`, `:account-value`, `:display-name`, `:prize`, and `:window-performances`.

- `/hyperopen/src/hyperopen/api/endpoints/vaults.cljs`

      (normalize-vault-snapshot-preview payload tvl)
      (normalize-vault-pnls payload)

  These must keep the existing canonical snapshot keys and preview structure.

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/legend.cljs`

      (create-legend! container chart legend-meta)
      (create-legend! container chart legend-meta opts)

  The return value must remain a JS object exposing `update` and `destroy`.

- `/hyperopen/src/hyperopen/views/trade_view.cljs`

      (trade-view state)

  The rendered DOM contract must keep the current `data-parity-id` and `data-role` anchors used by the existing CLJS tests and Playwright trade regressions.

- `/hyperopen/src/hyperopen/portfolio/actions.cljs`

      (normalize-portfolio-account-info-tab value)
      (set-portfolio-account-info-tab state tab)

  `set-portfolio-account-info-tab` must keep returning the same `[:effects/save ...]` effect shape.

Plan revision note (2026-03-30 15:18Z): Initial active ExecPlan created after auditing the hotspot list, current tests, current validation commands, prior deCRAP plans, and the trade-route browser-testing contract, then claiming `hyperopen-dnf9`.
