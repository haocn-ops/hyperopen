# Reduce Shared Chart Hover Jank Across Portfolio and Vault Detail

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Related `bd` issue: `hyperopen-kvtj` ("Implement shared chart hover jank reduction"), completed.
Follow-up `bd` issue: `hyperopen-atw0` ("Reprofile shared chart hover interactions after jank reduction").

## Purpose / Big Picture

After this change, hovering the shared performance charts on `/portfolio` and Vault detail should feel like a local chart interaction instead of a route-wide rerender. The tooltip overlay should no longer create layout-shift records while it follows the pointer, and heavy benchmark or performance-metric recomputation should stop piggybacking on unrelated store writes during hover. A contributor can verify this by profiling hover on both routes with benchmarks selected and observing that the tooltip moves without `LayoutShift` events and that the remaining main-thread work stays below the prior multi-hundred-millisecond frame spikes.

## Progress

- [x] (2026-03-16 01:07Z) Re-audited the uploaded trace, confirmed that the shift cluster is the shared D3 tooltip node, and mapped the long-task samples to Vault detail benchmark and metrics derivations plus route-level rerenders.
- [x] (2026-03-16 01:07Z) Created `bd` issue `hyperopen-kvtj` for the implementation work.
- [x] (2026-03-16 01:08Z) Authored this active ExecPlan.
- [x] (2026-03-16 01:16Z) Patched the shared D3 runtime tooltip to use transform-only positioning and a stable tooltip box layout.
- [x] (2026-03-16 01:19Z) Added memoized vault benchmark selector option and candidate caches for both Portfolio and Vault detail.
- [x] (2026-03-16 01:22Z) Added source-versioned cache boundaries for Portfolio and Vault detail chart and performance derivations.
- [x] (2026-03-16 01:24Z) Moved Vault detail performance-metric computation onto a worker-backed async seam with signature dedupe.
- [x] (2026-03-16 01:25Z) Added lazy extra-tab rendering support to account-info and switched Portfolio extra tabs to lazy renderers.
- [x] (2026-03-16 01:37Z) Added regression coverage and ran `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-03-16 01:38Z) Closed `hyperopen-kvtj` and prepared this plan for `/hyperopen/docs/exec-plans/completed/`.
- [x] (2026-03-30 22:18 EDT) Re-profiled populated `/portfolio` and Vault detail hover interactions, recorded the measured outcome, and split the remaining non-tooltip perf debt into follow-up issue `hyperopen-mceo`.

## Surprises & Discoveries

- Observation: the uploaded trace is Vault-detail-led even though the user described Portfolio.
  Evidence: sampling frames in the trace include `hyperopen.views.vaults.detail_vm`, `hyperopen.vaults.detail.benchmarks`, and `hyperopen.views.vaults.detail.chart_view`, while the `LayoutShift` node matches the shared D3 tooltip shell used by both surfaces.

- Observation: the hover loop itself is not spending most of the captured time in browser layout or paint.
  Evidence: the traced cluster contains only a few milliseconds of `Layout`, `UpdateLayoutTree`, `PrePaint`, and `Paint`, while the dominant cost is repeated `FunctionCall` blocks spanning 250ms+ and embedded samples inside benchmark sorting, chart/metric projection, and route rendering work.

- Observation: Portfolio already caches market benchmark options, but it still recomputes vault benchmark options and selector candidates on every view-model build.
  Evidence: `/hyperopen/src/hyperopen/views/portfolio/vm/benchmarks.cljs` memoizes market options only, then rebuilds vault options, selected options, and `:candidates` inside `returns-benchmark-selector-model`.

- Observation: Vault detail has no equivalent selector cache boundary today.
  Evidence: `/hyperopen/src/hyperopen/vaults/detail/benchmarks.cljs` sorts market and vault options directly every time `returns-benchmark-selector-model` runs.

## Decision Log

- Decision: treat the uploaded trace as evidence for the shared chart stack instead of insisting on a fresh Portfolio-only trace before acting.
  Rationale: the layout-shifted node is the shared D3 tooltip implementation, and the same selector and benchmark derivation patterns exist on both routes.
  Date/Author: 2026-03-16 / Codex

- Decision: keep the D3 chart spec contract stable and confine tooltip changes to internal DOM/style behavior.
  Rationale: the shared runtime already has coverage around spec updates; changing the public chart spec would widen the blast radius without helping the performance goal.
  Date/Author: 2026-03-16 / Codex

- Decision: mirror the existing Portfolio worker bridge for Vault detail metrics instead of inventing a second async pattern.
  Rationale: the repository already has a tested request-signature and stale-result-preservation pattern for Portfolio metrics, so the safest path is to reuse that shape.
  Date/Author: 2026-03-16 / Codex

## Outcomes & Retrospective

Implementation is complete for the code path changes: the shared tooltip now moves with transform-only updates, both benchmark selector surfaces cache vault and candidate models, Portfolio and Vault detail route models cache their expensive derivations, Vault detail performance metrics now reuse a worker-backed async seam, and Portfolio extra tabs render lazily. Validation gates passed via `npm test`, `npm run test:websocket`, and `npm run check`.

Fresh browser reprofiling is now complete. On 2026-03-30 EDT, a Playwright-driven hover trace against the local app on `http://localhost:8081` exercised the populated Portfolio route in spectate mode (`/portfolio?spectate=0x162cc7c861ebd0c06b3d72319201150482518185`) and a live Vault detail route (`/vaults/0xdfc24b077bc1425ad1dea75bcb6f8158e10df303`) with the returns chart selected.

Results were mixed but useful:

- The shared tooltip itself no longer appeared as the dominant layout-shift source during hover. In the populated Portfolio trace, sampled `LayoutShift` sources pointed at route-shell containers and small value spans instead of the shared tooltip root, which is consistent with the transform-only tooltip change landing correctly.
- Hover settle remained fast once the chart responded. The sampled hover-line / tooltip mutation p95 was about `7.4ms` on populated Portfolio and `0.5ms` on Vault detail, with max sampled settle times of `33ms` and `52.4ms` respectively.
- Residual live-route work still exists during the hover window. The same trace window recorded non-zero layout-shift totals and long tasks (`maxLongTaskMs` about `312ms` on populated Portfolio and `439ms` on Vault detail), so the broader acceptance target for “no post-load hover-window FunctionCall over 50ms” is not satisfied yet.

Follow-up issue `hyperopen-mceo` now tracks that remaining non-tooltip hover-window perf debt. The original tooltip-jank follow-up `hyperopen-atw0` is satisfied because the requested reprofiling was completed and the result is now recorded here.

## Context and Orientation

The shared D3 performance-chart runtime lives in `/hyperopen/src/hyperopen/views/chart/d3/runtime.cljs`. Portfolio uses it from `/hyperopen/src/hyperopen/views/portfolio_view.cljs`, and Vault detail uses it from `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs`. In this repository, a “chart spec” is the data map passed into `chart-d3-runtime/on-render`; it contains the normalized points, series, theme values, and a tooltip builder.

Benchmark selector state is derived separately on each surface. Portfolio uses `/hyperopen/src/hyperopen/views/portfolio/vm/benchmarks.cljs`, while Vault detail uses `/hyperopen/src/hyperopen/vaults/detail/benchmarks.cljs`. Both modules build a combined option list from selector markets and vault index rows, then filter that list into selected rows and search candidates. Only the market-option half is currently memoized in Portfolio; Vault detail has no selector-level cache.

Route-level chart and performance models are built in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`. Vault detail still computes performance metrics synchronously on the main thread via `/hyperopen/src/hyperopen/vaults/detail/performance.cljs`. Portfolio already uses a worker-backed bridge in `/hyperopen/src/hyperopen/views/portfolio/vm/metrics_bridge.cljs` and `/hyperopen/src/hyperopen/portfolio/worker.cljs`.

The account-info shell in `/hyperopen/src/hyperopen/views/account_info_view.cljs` currently accepts extra tabs with eager `:content`. Portfolio passes fully realized Hiccup for its extra tabs from `/hyperopen/src/hyperopen/views/portfolio_view.cljs`, which means those hidden tabs are still built during every route render.

## Plan of Work

First, update the shared D3 tooltip DOM behavior in `/hyperopen/src/hyperopen/views/chart/d3/runtime.cljs`. Keep the existing tooltip content and side-switch semantics, but stop driving the overlay with `left` and `top`. The runtime should position the tooltip with `translate3d(...)`, keep the root anchored to a fixed local origin, and apply layout-containment hints and fixed benchmark-row columns so the tooltip’s own content updates do not register as layout shifts while hovering.

Second, add explicit selector caches. Portfolio’s benchmark module will gain memoized vault-option and selector-model caches keyed by the same identity-first plus signature-fallback policy already used for market options. Vault detail’s benchmark module will gain equivalent caches for market options, vault options, and the final selector model so search candidate rebuilding only happens when inputs that matter have actually changed.

Third, add source-versioned route-model caches. Portfolio’s top-level view model should stop rebuilding benchmark computation context, chart normalization, and performance-metric grouping when the selected tab, summary range, selected benchmarks, and sampled source versions have not changed. Vault detail should gain the same style of cache for summary projection, `chart-series-data`, aligned benchmark rows, and performance-metric projection in `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`.

Fourth, move Vault detail performance metrics to an async worker-backed bridge. The new bridge should mirror the existing Portfolio contract: compute a deterministic request signature, keep prior metrics visible while recomputes are in flight, and only store new metrics when the worker returns a fresh result. The worker itself should live alongside the Portfolio worker under `/hyperopen/src/hyperopen/vaults/` or another route-local namespace that compiles cleanly into a dedicated worker bundle.

Fifth, make account-info extra tabs lazy. Update `/hyperopen/src/hyperopen/views/account_info_view.cljs` so extra tabs can provide either eager `:content` or lazy `:render`. Then switch Portfolio’s extra tabs in `/hyperopen/src/hyperopen/views/portfolio_view.cljs` to `:render` functions so hidden tab markup is not built unless selected.

Finally, add regression coverage for the runtime, selector caches, route-model invalidation boundaries, and new Vault async metrics flow. After that, run the required repository gates and use Chromium profiling on both surfaces to confirm the tooltip no longer emits layout-shift records during steady hover and that post-load hover frames are materially lighter than the uploaded trace.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/chart/d3/runtime.cljs` and related tests so tooltip positioning becomes transform-only and benchmark rows use stable layout columns.
2. Edit `/hyperopen/src/hyperopen/views/portfolio/vm/benchmarks.cljs` and `/hyperopen/src/hyperopen/vaults/detail/benchmarks.cljs` to add memoized selector-option and selector-model caches, plus reset seams for tests.
3. Edit `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`, `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`, and supporting metric modules to add source-versioned cache boundaries and Vault async metric requests.
4. Edit `/hyperopen/src/hyperopen/views/account_info_view.cljs` and `/hyperopen/src/hyperopen/views/portfolio_view.cljs` to add lazy extra-tab rendering.
5. Run:

   `npm run check`
   `npm test`
   `npm run test:websocket`

6. Re-profile `/portfolio` and Vault detail with selected benchmarks and record the measured outcome.

## Validation and Acceptance

The work is accepted when all of the following are true:

1. Hovering either shared chart does not create `LayoutShift` events for the tooltip node during steady-state pointer movement.
2. Selector and benchmark-option recomputation does not occur on unrelated store writes when their signatures are unchanged.
3. Vault detail performance metrics no longer compute synchronously on the main thread during ordinary route rerenders.
4. Hidden Portfolio account-info extra tabs do not build their full content until selected.
5. `npm run check`, `npm test`, and `npm run test:websocket` pass.
6. Chromium re-profiling on `/portfolio` and Vault detail shows no post-load hover-window `FunctionCall` over 50ms and a p95 hover settle at or below 25ms.

## Idempotence and Recovery

These changes are source-only and safe to repeat. Cache additions must be recoverable by clearing the local cache atoms through the provided reset helpers. If the new Vault async metrics bridge regresses behavior, the safe fallback is to keep the worker module and tests in place but route `detail_vm` back to the existing synchronous `performance-model/performance-metrics-model` until the invalidation logic is corrected.

## Artifacts and Notes

Observed trace evidence before implementation:

  - The layout-shifted node is `DIV class='absolute pointer-events-none min-w-[188px] rounded-xl border px-3 py-2 spectate-lg z-20'`, which matches the shared tooltip root created in `/hyperopen/src/hyperopen/views/chart/d3/runtime.cljs`.
  - The sampled hot frames include `hyperopen$vaults$detail$benchmarks$benchmark_vault_option_rank`, `hyperopen$vaults$detail$benchmarks$benchmark_market_selector_options`, `hyperopen$portfolio$metrics$history$normalize_daily_rows`, and other route-level derivations during the same hover window.

## Interfaces and Dependencies

`account-info-view` extra tabs will support:

- `{:id <keyword> :label <string> :content <hiccup>}`
- `{:id <keyword> :label <string> :render (fn [view-model] <hiccup>)}`

The D3 runtime public spec stays unchanged from the caller’s perspective. Internally, the tooltip root will keep the same `data-role` and content fields while changing only its style application strategy.

Vault async metrics will introduce a deterministic request-signature seam parallel to the existing Portfolio bridge. The route must end with a stable internal contract shaped like:

- request signature derived from snapshot range, selected benchmark coins, strategy source version, and benchmark source versions;
- store fields for the latest metrics result and loading flag;
- worker responses normalized before writing into the store.

Plan revision note: 2026-03-16 01:08Z - Initial active ExecPlan created after mapping the uploaded trace to the shared tooltip node plus Vault-detail-led selector and metric recomputation hot paths.
