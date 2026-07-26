# Rebalance Slippage Snapshot Estimates

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The Rebalance Preview currently estimates slippage from cost contexts that are often missing live orderbook data, so users can see a dollar slippage number sourced from `fallback-bps` without enough explanation of how fresh or market-backed that estimate is. After this change, the rebalance page should improve slippage estimates by fetching bounded one-time `l2Book` snapshots for the actual executable rows and clearly display whether each estimate came from a fresh snapshot, a stale snapshot, or a fallback assumption.

The user-visible result is that a user can open the Rebalance Preview, see slippage in dollars and basis points, and understand the estimate source and freshness. The implementation must avoid maintaining many live websocket orderbook subscriptions.

## Context References

Public refs:
- Direct user/maintainer request on 2026-05-24: create an execution plan to update rebalance slippage estimates and how they are displayed on the rebalance page, using one-time orderbook snapshots rather than many live orderbook subscriptions.
- Official Hyperliquid API docs state that `l2Book` info requests return a snapshot with at most 20 price levels per side and that `l2Book` info requests have REST weight 2. Hyperliquid REST requests share an aggregate per-IP weight limit, so this plan must keep snapshot fetches bounded, deduped, and cached.
- Official Hyperliquid websocket docs describe `l2Book` subscriptions, but this plan intentionally does not use a subscription fanout for optimizer rebalance estimates.

Repo artifacts:
- `/hyperopen/AGENTS.md` requires ExecPlans for complex work and requires UI-specific docs and browser QA for UI-facing changes.
- `/hyperopen/docs/FRONTEND.md` requires deterministic Playwright coverage for committed UI flows and governed browser-QA accounting for UI-facing work.
- `/hyperopen/docs/BROWSER_TESTING.md` owns browser-tool routing.
- `/hyperopen/src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` currently owns rebalance row classification, gross trade totals, fee estimates, slippage estimates, blocked reasons, and margin summary.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/rebalance_preview.cljs` derives a rebalance preview from a solved result and request context when a solved result lacks a preview.
- `/hyperopen/src/hyperopen/portfolio/optimizer/application/orderbook_loader.cljs` currently maps in-memory orderbook state to cost contexts and can label contexts as live, stale, or fallback.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/rebalance_tab.cljs` renders the Rebalance Preview KPI cards and row table.
- `/hyperopen/src/hyperopen/api/endpoints/market.cljs`, `/hyperopen/src/hyperopen/api/gateway/market.cljs`, and `/hyperopen/src/hyperopen/api/default.cljs` are the existing market `/info` API boundary.
- `/hyperopen/src/hyperopen/api/info_client/flow.cljs` already provides request cache keys, TTLs, single-flight dedupe, retries, and rate-limit cooldown handling for `/info` requests.
- `/hyperopen/src/hyperopen/portfolio/optimizer/actions/run.cljs` owns results tab selection and currently saves the selected tab before replacing the shareable query string.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-05-24 00:00Z) Captured the product direction: use bounded one-time orderbook snapshots and surface source/freshness instead of maintaining many live orderbook subscriptions.
- [x] (2026-05-24 00:00Z) Traced the current rebalance preview, orderbook cost-context, market API, and optimizer tab selection paths.
- [x] (2026-05-24 00:00Z) Authored this active ExecPlan with scope, file paths, milestones, acceptance criteria, rate-limit guardrails, and UI validation requirements.
- [x] (2026-05-24 17:10Z) Implemented tests for snapshot request construction, snapshot refresh planning, weighted-depth slippage math, preview refresh behavior, runtime effect dispatch, and rebalance tab display.
- [x] (2026-05-24 17:10Z) Implemented bounded `l2Book` snapshot requests with low-priority request policy, single-flight/cache keys, and a 30s snapshot TTL.
- [x] (2026-05-24 17:10Z) Replaced best-bid/best-ask-only slippage with snapshot weighted-depth slippage when visible depth covers the trade, with fallback retained for depth-limited rows.
- [x] (2026-05-24 17:10Z) Updated Rebalance Preview rows to show slippage amount plus bps and cost source plus age/depth labels.
- [x] (2026-05-24 17:30Z) Ran focused tests, required repository gates where possible, focused Playwright, and governed browser QA. `npm run check` remains blocked by pre-existing docs/namespace-size policy failures recorded below.

## Surprises & Discoveries

- Observation: The current rebalance preview can only use orderbook data already present in `state[:orderbooks]`; otherwise it falls back to the configured basis-point assumption.
  Evidence: `/hyperopen/src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` calls `orderbook-cost-context` while building readiness, and `orderbook-cost-context` reads `state[:orderbooks coin]`.

- Observation: The existing `/info` client already has cache TTL, cache keys, single-flight dedupe, retry, and rate-limit cooldown support.
  Evidence: `/hyperopen/src/hyperopen/api/info_client/flow.cljs` computes `cache-key`, `dedupe-key`, `cache-ttl-ms`, and `flight-key`; it also retries retryable responses and tracks status 429 cooldowns.

- Observation: Fetching snapshots before the optimizer solves would require guessing which assets will actually trade. Fetching after a solved result exists lets the implementation request snapshots only for ready executable rows that contribute to displayed fees and slippage totals.
  Evidence: `:ready` row status is only known after `/hyperopen/src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` compares current and target weights and applies row blockers such as spot unsupported, missing price, or quantity below lot.

- Observation: The existing Playwright route helper does not preserve `?otab=rebalance` when dispatching non-`/trade` routes, so two older execution-modal regression greps land on the Recommendation tab and cannot find the execution button.
  Evidence: `tools/playwright/support/hyperopen.mjs` splits route search but dispatches only `path`; the failed screenshot from `tools/playwright/test/portfolio-regressions.spec.mjs` shows `/portfolio/optimize/scn_playwright_tracking_reload` on the Recommendation tab.

## Decision Log

- Decision: Do not use a fanout of websocket `l2Book` subscriptions for rebalance slippage estimation.
  Rationale: The user explicitly prefers one-time snapshots so the app does not consume unnecessary streaming data or maintain many subscriptions. One-time snapshots also fit the preview use case because rebalance estimates are review context, not a live trading book panel.
  Date/Author: 2026-05-24 / Codex

- Decision: Fetch snapshots after a solved result exists, not during initial setup readiness.
  Rationale: Ready executable rows are known only after target weights are available. Post-solve snapshot refresh allows the app to request data only for rows that can affect gross trade, fees, and slippage totals.
  Date/Author: 2026-05-24 / Codex

- Decision: Keep fallback slippage as a first-class estimate source.
  Rationale: Some rows will lack a coin, be rate-limited, exceed the per-preview cap, have missing snapshot depth, or fail snapshot fetch. The page should stay useful and honest by showing fallback assumptions instead of blocking preview rendering.
  Date/Author: 2026-05-24 / Codex

- Decision: Use weighted visible-depth slippage from snapshot levels when the snapshot contains enough quantity to cover the ready row.
  Rationale: The current best-bid/best-ask comparison is a spread estimate, not a trade-size estimate. Walking visible levels gives a better estimate for larger trades while still remaining deterministic and testable.
  Date/Author: 2026-05-24 / Codex

- Decision: If the visible snapshot does not cover the full quantity, mark the estimate as depth-limited and keep using fallback bps for that row unless a later implementation proves a more conservative hybrid formula.
  Rationale: A partial visible-depth calculation can understate cost for the unfilled remainder. Falling back while surfacing `depth-limited` is more honest than presenting a precise-looking number from incomplete depth.
  Date/Author: 2026-05-24 / Codex

## Outcomes & Retrospective

Implemented. Snapshot-based slippage now improves the Rebalance Preview without adding websocket orderbook subscriptions. The refresh effect runs after a solved optimizer result, requests at most a bounded number of distinct ready-row coins, relies on the `/info` client's cache/dedupe behavior, and leaves fallback estimates intact when snapshots fail, are malformed, or do not cover visible depth.

The UI now distinguishes `snapshot`, `stale snapshot`, `fallback-bps`, and depth-limited rows in the cost source cell, and shows row slippage in both dollars and bps. Group rows and non-applicable blocked rows still render `N/A`.

Validation evidence from 2026-05-24:
- Focused ClojureScript suite: 73 tests / 491 assertions / 0 failures.
- Additional workflow/effect focused suite: 32 tests / 108 assertions / 0 failures.
- Post-async-guard focused suite: 15 tests / 58 assertions / 0 failures.
- `npm test`: 4026 tests / 22178 assertions / 0 failures.
- `npm run test:websocket`: 524 tests / 3048 assertions / 0 failures.
- `npx shadow-cljs --force-spawn compile app`: 0 warnings.
- `npx shadow-cljs --force-spawn compile portfolio`: 0 warnings.
- `npx shadow-cljs --force-spawn compile portfolio-optimizer-worker`: 0 warnings.
- `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app`: PASS for visual evidence, native controls, styling consistency, interaction, layout regression, and jank/perf at 375, 768, 1280, and 1440 widths. Artifact run: `/Users/barry/.codex/worktrees/3160/hyperopen/tmp/browser-inspection/design-review-2026-05-24T17-38-16-884Z-bfb69314`.
- `npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "portfolio optimizer setup and retained draft detail routes render through view models"`: 1 passed.
- `npm run browser:cleanup`: completed with no active sessions left.

Blocked or non-passing validation:
- `npm run check` fails before compile steps on `[stale-doc] docs/product-specs/portfolio-page-parity-prd.md - document is stale: 94 days old, max allowed 90`.
- Running `npm run lint:namespace-sizes` directly also reports existing oversized namespaces and exception drift outside this change, including `test/hyperopen/views/portfolio/optimize/scenario_detail_view_test.cljs`, `src/hyperopen/views/portfolio/optimize/scenario_objective_menu.cljs`, and several wallet/header test files.
- Full `npm run test:playwright:smoke` had 32 passing tests and one first-run failure in `portfolio optimizer draft allocation add asset selector updates draft and starts recompute`; the isolated rerun of that test passed. This looks like a route-load flake around `[data-parity-id='app-route-module-shell']`, not a rebalance slippage failure.
- Targeted greps for two older rebalance execution-modal regression tests are blocked by the Playwright helper query-string issue noted in Surprises & Discoveries.

## Context and Orientation

The optimizer route solves for target portfolio weights. A rebalance preview compares current weights to target weights and turns the difference into per-instrument rows. A row is `ready` when the app can stage a supported perp order with a positive quantity; it is `blocked` when it cannot be staged, such as when the row is spot-only or missing required market data. Summary KPIs such as gross trade, fees, and slippage currently include only ready rows.

Slippage is the expected cost from crossing the market or consuming visible orderbook depth. A basis point is one one-hundredth of one percent; 25 bps means 0.25%. The current preview estimates slippage as `abs(delta-notional-usd) * slippage-bps / 10000`. The existing `slippage-bps` source is either provided in a cost context, derived from available in-memory best bid/ask, or defaulted to the fallback assumption. In the screenshot that prompted this plan, the ready row used `fallback-bps`, which means the app did not have a usable market-backed estimate for that row at preview build time.

Hyperliquid exposes a one-time `l2Book` snapshot through the REST `/info` endpoint. The snapshot returns up to 20 price levels on each side. A buy should consume ask levels from best ask upward. A sell should consume bid levels from best bid downward. For each row with enough visible depth, compute the weighted average fill price from the snapshot and compare it to the row's reference price to derive slippage bps. If the row quantity cannot be fully covered by visible levels, keep the fallback estimate and mark the row as depth-limited.

This change touches both application behavior and UI display. The application behavior is the bounded snapshot fetch and preview refresh. The UI display is the Rebalance Preview table and summary cards that should explain estimate source and freshness.

## Plan of Work

Milestone 1 establishes the pure domain behavior. Add tests in `/hyperopen/test/hyperopen/portfolio/optimizer/domain/rebalance_test.cljs` for weighted visible-depth slippage. A buy row with a reference price of 100, quantity 3, and asks at 101 for size 1 and 102 for size 2 should produce an estimated fill price of 101.666..., slippage bps of 166.666..., and slippage USD equal to `notional * bps / 10000`. A sell row should mirror that behavior using bids. Add a depth-limited case where visible levels do not cover the quantity and assert that the row uses fallback bps while recording a depth-limited marker. Then update `/hyperopen/src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` so `cost-estimate` receives quantity, derives weighted fill prices from `:bids` and `:asks` when present, and includes fields such as `:slippage-bps`, `:estimated-fill-price`, `:estimated-slippage-usd`, `:source`, `:stale?`, `:age-ms`, and `:depth-status`.

Milestone 2 adds a market API snapshot boundary. Add tests around `/hyperopen/src/hyperopen/api/endpoints/market.cljs` and gateway/default facade tests proving `request-l2-book-snapshot!` posts `{"type" "l2Book" "coin" coin}` with `:priority :low`, `:dedupe-key [:l2-book-snapshot coin]`, `:cache-key [:l2-book-snapshot coin]`, and a default `:cache-ttl-ms` of 30000. Extend `/hyperopen/src/hyperopen/api/request_policy.cljs` with `:l2-book-snapshot 30000`. Add public facade wrappers in `/hyperopen/src/hyperopen/api/gateway/market.cljs` and `/hyperopen/src/hyperopen/api/default.cljs`. The endpoint should resolve to `nil` without network work when coin is missing.

Milestone 3 introduces snapshot refresh planning. Create or extend an application namespace under `/hyperopen/src/hyperopen/portfolio/optimizer/application/` to derive snapshot candidates from a solved last-successful run. Candidate rows must be ready perps with a nonblank coin, positive quantity, nonzero delta notional, and either fallback or stale cost source. Deduplicate candidates by coin, sort by absolute delta notional descending, and cap requests with a default such as 10 distinct coins per refresh. The planner should return skipped reasons for over-cap, missing coin, blocked row, and already-fresh estimate so the UI can explain why a row remains fallback if needed.

Milestone 4 wires the bounded fetch effect. Add an effect adapter in `/hyperopen/src/hyperopen/runtime/effect_adapters/portfolio_optimizer.cljs` or a focused child namespace that reads the current store, asks the planner for snapshot candidates, calls `api/request-l2-book-snapshot!` sequentially or with very low concurrency, and then updates the last successful run with refreshed cost contexts. The implementation must not write snapshots into the global `:orderbooks` websocket projection; store refresh status and snapshot cost contexts under optimizer-owned paths, for example `[:portfolio :optimizer :rebalance-slippage-refresh]` and `[:portfolio :optimizer :rebalance-slippage-snapshots]`, with named path constants in `/hyperopen/src/hyperopen/portfolio/optimizer/contracts/paths.cljs`.

Milestone 5 refreshes the preview using solved-result context. Extend `/hyperopen/src/hyperopen/portfolio/optimizer/application/rebalance_preview.cljs` with a function that can rebuild an existing solved result's `:rebalance-preview` using the saved request plus snapshot cost contexts. The current `result-with-rebalance-preview` preserves an existing preview; keep that behavior for normal calls, and add an explicit refresh path that replaces the preview only when a snapshot refresh effect has new cost contexts for the same run signature. This prevents unrelated stale snapshots from mutating a newer run.

Milestone 6 triggers refresh from user-visible flows without delaying the page. Update `/hyperopen/src/hyperopen/portfolio/optimizer/actions/run.cljs` so `set-portfolio-optimizer-results-tab` still saves the selected tab and replaces the shareable query first, then emits a refresh effect only when the selected tab normalizes to `:rebalance`. Also trigger refresh after a new optimizer run succeeds if that path can be made run-signature-safe without adding worker-side HTTP; otherwise record the reason in this ExecPlan and rely on tab selection and execution-review entry points. Update `/hyperopen/src/hyperopen/portfolio/optimizer/actions/execution.cljs` so opening the execution review uses the latest refreshed preview and can trigger a refresh before review when every ready row is still fallback. Do not block execution modal opening on snapshot fetch completion.

Milestone 7 updates the Rebalance Preview display. In `/hyperopen/src/hyperopen/views/portfolio/optimize/rebalance_tab.cljs`, change the Slippage KPI to show the total dollar estimate and a small source summary such as `2 snapshot · 1 fallback · max age 18s` when available. In row cells, display slippage as amount plus bps, for example `$197.94 · 25 bps`. Display cost source as a compact label with freshness, for example `snapshot · 7s`, `stale snapshot · 54s`, `fallback-bps`, or `fallback-bps · depth-limited`. Preserve `N/A` for group rows, blocked rows without costs, and fields that genuinely do not apply. Keep the existing dense trading UI style and avoid adding a modal or explanatory text block.

Milestone 8 adds deterministic UI and interaction coverage. Add or extend Hiccup tests under `/hyperopen/test/hyperopen/views/portfolio/optimize/` to assert the new source/age/bps display. Add action/effect tests proving tab selection emits the visible state update before the heavy snapshot refresh effect. Add Playwright coverage for a route fixture where a rebalance row first renders fallback and then displays snapshot-backed slippage after the mocked `l2Book` response. Because this is UI-facing, run governed browser QA and account for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes across 375, 768, 1280, and 1440 widths.

## Concrete Steps

Work from repository root:

    cd /hyperopen

Start with focused RED tests for the domain and API boundary:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.domain.rebalance-test --test=hyperopen.api.endpoints.market-test --test=hyperopen.api.default-test

Expected RED result before implementation: the new weighted-depth slippage tests fail because `domain.rebalance` does not consume snapshot levels by quantity, and the new API facade tests fail because `request-l2-book-snapshot!` does not exist.

After implementing the domain and API boundary, run:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.portfolio.optimizer.domain.rebalance-test --test=hyperopen.api.endpoints.market-test --test=hyperopen.api.gateway.market-test --test=hyperopen.api.default-test

Expected GREEN result: all selected tests pass, including explicit request body, cache key, TTL, and weighted-depth slippage assertions.

Then implement refresh planning and effect wiring with focused tests:

    node out/test.js --test=hyperopen.portfolio.optimizer.application.rebalance-preview-test --test=hyperopen.portfolio.optimizer.actions-test --test=hyperopen.runtime.effect-adapters.portfolio-optimizer-test

Expected RED result before implementation: tests fail because no snapshot candidate planner, refresh effect, or preview refresh path exists. Expected GREEN result after implementation: tests pass and prove capped, deduped, run-signature-safe refresh.

Add UI tests and run:

    node out/test.js --test=hyperopen.views.portfolio.optimize.scenario-detail-view-test --test=hyperopen.views.portfolio.optimize.execution-modal-test

Expected result: the Rebalance Preview renders slippage amount plus bps and source/freshness labels without regressing execution modal rows.

Run the smallest relevant Playwright regression. If no existing test cleanly covers the Rebalance Preview with mocked optimizer data, create one under `/hyperopen/tools/playwright/test/` and run only that file first:

    npx playwright test -c playwright.config.mjs tools/playwright/test/<new-or-existing-rebalance-slippage-test>.spec.mjs

Expected result: the test opens an optimizer result route, asserts fallback slippage display, fulfills mocked `l2Book` snapshot responses, and then asserts snapshot source/age/bps display.

Before signoff, run required gates:

    npm run check
    npm test
    npm run test:websocket

Run governed browser QA for the optimizer results route:

    npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app
    npm run browser:cleanup

If `npm run check` is blocked by an unrelated stale canonical document, record the exact blocker in this ExecPlan and in the final handoff rather than claiming the gate passed.

## Validation and Acceptance

Acceptance is met when the Rebalance Preview can show slippage estimates from one-time snapshots without requiring live websocket orderbook subscriptions. A ready perp row with enough visible snapshot depth should show a snapshot-backed source label, snapshot age, slippage bps, and slippage dollars. A ready row without a snapshot, with a failed/rate-limited snapshot request, or with depth-limited visible levels should remain usable and show fallback source information. Blocked rows and group rows should continue to show `N/A` where cost data does not apply.

The snapshot refresh must be bounded. A single refresh should request at most the configured cap of distinct coins, defaulting to 8 in the implementation. Requests must dedupe by coin and use the `/info` client's single-flight and short TTL cache so repeated rerenders do not repeatedly call `l2Book` for the same coin. The implementation must not create persistent websocket orderbook subscriptions for optimizer slippage estimates.

The UI must remain dense and review-oriented. The summary KPI should make the total slippage estimate understandable without long explanatory text. Row-level display must answer: amount, bps, source, and freshness. The implementation must preserve the current execution behavior: ready rows can still open the execution review, blocked rows remain visible, and fallback estimates do not block review.

Required validation:
- Focused ClojureScript tests for domain, API, refresh planner/effect, actions, and views.
- Focused Playwright regression for the Rebalance Preview source/freshness display.
- `npm run check`.
- `npm test`.
- `npm run test:websocket`.
- Governed browser QA for the optimizer results route with all required passes and widths explicitly accounted for.

## Idempotence and Recovery

Snapshot fetching must be safe to run repeatedly. The request layer should dedupe in-flight calls and reuse cached responses for the TTL window. The refresh effect must check the current run signature before applying returned snapshots so late responses from an older run do not mutate a newer result. If a snapshot request fails, the effect should record row/source status and leave the fallback preview intact.

If a row's snapshot is missing or malformed, do not throw from rendering. Keep the row visible, use fallback cost fields, and display the source or depth reason. If an implementation stores optimizer-owned snapshot data under a new path, clearing that path must not affect websocket orderbook state or the trading route.

If browser QA leaves sessions open, run:

    npm run browser:cleanup

## Artifacts and Notes

The snapshot request body must be:

    {"type" "l2Book"
     "coin" "<coin>"}

Optional aggregation fields such as `nSigFigs` and `mantissa` are out of scope for the first implementation. The API returns at most 20 levels per side, so the preview must explicitly handle insufficient visible quantity.

For a buy of size 3 at reference price 100 with asks of 1 at 101 and 2 at 102:

    weighted-fill-price = ((1 * 101) + (2 * 102)) / 3
    weighted-fill-price = 101.6666667
    slippage-bps = ((101.6666667 - 100) / 100) * 10000
    slippage-bps = 166.666667

For a sell, use bids and compare reference price to weighted fill:

    slippage-bps = max(0, (reference-price - weighted-fill-price) / reference-price) * 10000

For the row dollar estimate:

    estimated-slippage-usd = abs(delta-notional-usd) * slippage-bps / 10000

## Interfaces and Dependencies

Add a market API function in `/hyperopen/src/hyperopen/api/endpoints/market.cljs`:

    request-l2-book-snapshot!

It should accept `post-info!`, a `coin`, and an `opts` map, and should return a promise resolving to the raw parsed book snapshot or `nil` for blank coin. It must use `request-policy/apply-info-request-policy` with request kind `:l2-book-snapshot`.

Expose the endpoint through:

    /hyperopen/src/hyperopen/api/gateway/market.cljs
    /hyperopen/src/hyperopen/api/default.cljs

Implementation note: the first implementation does not persist a separate optimizer snapshot cache path. Instead, it fetches bounded snapshots after a solved run, merges the normalized snapshot contexts into the saved run request's execution assumptions, and rebuilds the rebalance preview only for the current last-successful run.

Extend `/hyperopen/src/hyperopen/portfolio/optimizer/domain/rebalance.cljs` so ready-row cost maps can include:

    :source
    :estimated-fill-price
    :notional-usd
    :slippage-bps
    :estimated-slippage-usd
    :fee-bps
    :estimated-fee-usd
    :stale?
    :age-ms
    :depth-status

Add a snapshot candidate planner under `/hyperopen/src/hyperopen/portfolio/optimizer/application/`. It must be pure and testable. It should take state or a map containing the last successful run, current refresh state, now-ms, and configuration, and return candidates plus skipped reasons. The effect adapter can then perform the actual API calls.

Add a refresh effect that is emitted as:

    [:effects/refresh-portfolio-optimizer-rebalance-slippage-snapshots]

The effect is registered in the centralized runtime registration and effect-args contracts. It performs network I/O after the solved run is visible, so it does not block the primary result rendering path.

Revision note 2026-05-24 / Codex: Initial ExecPlan created from the user request to replace subscription-based rebalance slippage improvement with bounded orderbook snapshots and clearer source/freshness display.
