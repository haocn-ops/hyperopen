# Eliminate multi-second optimizer setup render stalls from per-render history re-alignment

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. Maintain this document in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

On `/portfolio/optimize`, clicking the "Minimum risk" or "Maximum Sharpe" goal card takes multiple seconds to visually select. A DevTools performance trace (owner-captured 2026-07-08, `Trace-20260708T165504.json`) shows why: the setup page's render path re-runs the full instrument-history alignment — a ~3.3 second synchronous main-thread computation — over and over. The page's local web-vitals panel reports LCP 6.07 s and CLS 0.81, both rated "poor".

After this change, clicking a goal card highlights it within one frame-budget-scale delay (target: interaction-to-paint under 200 ms), streaming renders of the setup page stop freezing the page for seconds at a five-second cadence, and the initial route render is no longer blocked behind seconds of alignment work. No optimizer behavior changes: the same assets align, the same warnings appear, the same runs produce the same results.

## Context References

Public refs:
- Direct user/maintainer request (2026-07-08): "the responsiveness on the Portfolio Optimize page looks pretty bad … clicked minimum risk … takes a while … make an execution plan and get to the root cause".
- Owner-supplied DevTools trace `Trace-20260708T165504.json` (local Downloads; evidence excerpted below in `Artifacts and Notes` so the plan stays self-contained).

Repo artifacts:
- `/docs/exec-plans/completed/2026-07-08-optimizer-proxy-loading-rail-and-normalize-perf.md` — the prior pass that memoized api-v2 codec key normalization; this plan removes the next (much larger) render-path hotspot.
- `/docs/exec-plans/tech-debt-tracker.md` — earlier render-performance leftovers.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-07-08) Root cause established from the owner trace + source reading (see `Context and Orientation`).
- [x] (2026-07-08) M1: legacy-fallback series is now computed lazily in `align-api-v2-history-inputs` (`fallback-wanted?` short-circuit); covered by two invocation-counting tests in `history_loader_api_v2_legacy_fallback_test.cljs` that fail on pre-change code.
- [x] (2026-07-08) M2: `align-history` memo keys on data inputs only; `:freshness` re-stamped per call; covered by `request_builder_align_memo_test.cljs` (fails pre-change: 2 alignment runs vs 1). Also raised `align-history-memo-capacity` 16 → 64 — with 30+ proxy assumptions the wholesale reset fired mid-build and nothing ever survived to the next render (this was the missing amplifier behind the owner's 3.3 s tasks; see Surprises).
- [x] (2026-07-08) M3: re-profiled live (fresh-origin heavy 74-asset spectate state, worktree dev build on :8093): goal-card click renders now worst-case 151 ms; no long task >723 ms anywhere (that one is the history-bundle settle render); zero long tasks across minutes of idle. Contingencies (per-coin normalization memo, readiness decoration memo) NOT applied — not hot.
- [x] (2026-07-08) M4 (partial): CLS/LCP could not be re-measured live because the browser window was hidden (Chrome suppresses LCP/layout-shift reporting for hidden documents). The driving cause (multi-second tasks delaying paints) is gone; remaining action: owner re-checks the local web-vitals panel in a foreground session and reports the numbers back here.
- [x] (2026-07-08) M5: `npm run gates` 34/34 PASS (6005 tests, 31928 assertions); Playwright `optimizer-proxy-loading-ux.spec.mjs` passes against the worktree build (`PLAYWRIGHT_BASE_URL=http://127.0.0.1:8093`, reuse-existing-server).
- [x] (2026-07-08 19:00) Owner re-test surfaced the NEXT layer: with alignment fixed, min-risk→max-sharpe still took seconds (owner trace `Trace-20260708T185946.json`: three 1.2–1.9 s tasks). Cause: the engine-context return-input memos keyed on the request's full `:history` INCLUDING the `:freshness` stamp M2 now re-stamps per rebuild — so every 5 s bucket roll re-ran the Ledoit-Wolf estimate while a Max-Sharpe panel was visible, and the two UI helpers each estimated independently (double cost per switch). Fixed: memo keys drop `:freshness` (`history-sans-freshness`), and one shared `ui-risk-result` memo feeds both helpers. Playwright regression (`switching Minimum risk to Maximum Sharpe estimates the risk model at most once`, incl. a 5.5 s bucket-roll re-render check) fails pre-change (2 estimates) and passes after. Gates 34/34.
- [x] (2026-07-08) Eliminated the remaining one-time first-switch cost by making the estimate itself fast instead of hiding it (option (c), chosen over prewarm/two-phase-paint): rewrote `risk-ledoit-wolf/estimate` in flat JS loops that reproduce the original persistent-vector arithmetic ORDER exactly (bit-identical results — new `risk_ledoit_wolf_test.cljs` asserts exact `=` against the original implementation inlined as a reference), and deferred the unconditionally-computed pairwise `sample` covariance in `risk.cljs/estimate-risk-model` behind a `delay` (the `:ledoit-wolf-dense` branch never read it). Measured in the dev build at the owner-trace scale (45 instruments × 700 observations): estimate now runs in ~13 ms, down from ~1,200–1,900 ms. Playwright exposure-map spec 9/9; gates 34/34 (6,009 tests).
- [ ] Owner confirmation on the real wallet/profile: min-risk↔max-sharpe goal switches should now highlight effectively instantly (covariance ~13 ms + render), with no recurring stalls; re-check the local web-vitals panel (LCP/CLS baseline 6.07 s / 0.81).

## Surprises & Discoveries

- Observation: both memoization layers that were *supposed* to prevent this exist already (`build-request-memo` in `setup_readiness.cljs`, `align-history-memo` in `request_builder.cljs`) — they are defeated by their own key contents, not missing.
  Evidence: `setup_readiness.cljs:62-93` and `request_builder.cljs:499-533` (both memoize on maps that include `:as-of-ms`).
- Observation: the single largest cost inside alignment is work whose result is usually thrown away — the legacy-fallback series is built for every instrument, then used only when the api-v2 series is unusable.
  Evidence: trace bottom-up, `legacy-fallback/series` on-stack for 56–65% of each long task; `alignment.cljs:258-270` computes it unconditionally.
- Observation: a fresh browser origin does NOT reproduce the owner's multi-second stalls, on pre-fix OR post-fix code. The amplifier is the owner's per-wallet stored state: dozens of engine-backed proxy assumptions each add a regression sub-alignment per build (30+ `align-history` calls per `build-engine-request`), which overflowed the capacity-16 memo's wholesale reset MID-BUILD — so under that state, every render realigned everything from scratch. This is why the capacity bump to 64 is part of the fix, not an optional tune.
  Evidence: A/B on 2026-07-08 — pre-fix main-checkout build served on :8094 with a fresh 74-asset spectate state showed no long task >680 ms, while the owner's trace (same code, their profile) showed 3.2–3.8 s tasks; `request_builder.cljs` sub-alignment call sites at lines 124 and 625; owner's right rail lists per-asset "HISTORY ASSUMPTIONS" entries.
- Observation: LCP/layout-shift PerformanceObserver entries are never delivered for a hidden document, so web-vitals cannot be re-measured from a backgrounded automation window.
  Evidence: `document.visibilityState === "hidden"` with `buffered: true` observers returning zero entries on :8093.

## Decision Log

- Decision: fix render-path cost by (a) making the alignment itself lazy about fallback work and (b) removing wall-clock from the alignment memo key — rather than moving alignment off the render path (e.g., into an action/effect that stores the aligned result in state).
  Rationale: the read-time `build-readiness` seam is deliberate (draft enrichment doc, `draft_enrichment.cljs` header) and many consumers assume readiness can be derived from state at any time. Restructuring to write-time alignment is a far bigger architectural change with real staleness risk; the two targeted fixes remove ~all of the measured cost with provably identical outputs.
  Date/Author: 2026-07-08 / Claude + owner request.
- Decision: keep the request-level `:as-of-ms` at its 5-second quantization (do not widen to 30–60 s).
  Rationale: orderbook cost-context staleness classification consumes the request `:as-of-ms` as `:now-ms`; widening the bucket risks misclassifying orderbook staleness. Once alignment no longer re-runs on bucket roll, the residual 5-second rebuild is only the cheap tail of `build-engine-request` (measured ~1-2% of the old task) plus per-render decoration that already runs outside the memo.
  Date/Author: 2026-07-08 / Claude.

## Decision Log (continued)

- Decision: raise `align-history-memo-capacity` from 16 to 64 (wholesale-reset eviction kept).
  Rationale: a build with dozens of proxy assumptions performs 30+ alignments; at capacity 16 the reset fired mid-build so the memo NEVER carried an alignment across renders for exactly the users with the heaviest states. 64 exceeds any realistic build; a smarter eviction policy was not worth the complexity.
  Date/Author: 2026-07-08 / Claude (discovered during live A/B, see Surprises).
- Decision: trim comment prose in `request_builder.cljs` to stay within its 670-line namespace-size exception rather than raising the exception or splitting the namespace.
  Rationale: the change adds no new functional surface; the split is tracked repo-wide by the namespace-size playbook and should not be smuggled into a perf fix.
  Date/Author: 2026-07-08 / Claude.

## Outcomes & Retrospective

(2026-07-08, implementation complete pending owner confirmation.) All three defects fixed: lazy legacy-fallback, data-keyed alignment memo with per-call freshness, and memo capacity 64. Evidence: three new tests fail on pre-change code and pass after (laziness ×2, memo-survival ×1) with outputs asserted equal; `npm run gates` 34/34 PASS; Playwright loading-UX spec passes against the worktree build; live heavy-state profiling shows worst render task 723 ms (history settle, down from 3,766 ms) and click renders ≤151 ms (down from 3.3 s). Complexity: net reduced — the same memo/laziness machinery that existed before now actually works; the only added surface is one short-circuit boolean and a freshness re-stamp, minus a misleading comment. Remaining: owner re-measures LCP/CLS on their profile; if CLS stays poor, open a follow-up scoped to the shifting elements the vitals panel names.

## Context and Orientation

The optimizer setup page is rendered by `src/hyperopen/views/portfolio/optimize/setup_view.cljs`, which builds its data via `workspace-model` (`src/hyperopen/portfolio/optimizer/application/view_model/workspace.cljs`). `workspace-model` calls `setup-readiness/build-readiness` (`src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs`) on every render. Renders happen constantly on this page because live websocket data (marks, account state) streams into the app state.

"Alignment" means: taking every selected instrument's price/funding history and intersecting the observation calendars so all return series share the same dates — the input the optimization engine needs. It is implemented by `align-api-v2-history-inputs` in `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs` and reached through `align-history` in `src/hyperopen/portfolio/optimizer/application/request_builder.cljs` (called from `build-engine-request` for the main universe, again for the currently-held universe, and once per engine-backed proxy assumption).

Three findings from the owner trace (numbers in `Artifacts and Notes`):

1. Every long task is the same stack. ~85–90% of each 3.2–3.8 s main-thread task is `workspace-model → build-readiness → build-request → build-engine-request → align-history → align-api-v2-history-inputs`. Clicking a goal card merely dispatches a small draft change; the multi-second delay before the highlight is the next render paying for a full re-alignment.

2. Most of the alignment is wasted fallback work. `alignment.cljs:258` computes `legacy-fallback/series` — full candle normalization plus funding-history normalization (per-row parse, dedupe, sort of hourly funding rows) — for **every instrument on every alignment**, even though the result is only consulted when the instrument's api-v2 series is missing/unusable (`legacy-fallback?`, line 264). `legacy-fallback/series` is on-stack for 56–65% of each long task; its `legacy-funding-summary` alone is 50–58%.

3. The memos exist but the wall clock evicts them. `align-history` (request_builder.cljs:509) memoizes on an inputs map that includes `:as-of-ms` and `:stale-after-ms`. `build-request` (setup_readiness.cljs:65) memoizes on an inputs map that includes `:as-of-ms` from `current-as-of-ms`, which — because nothing in production writes `contracts/runtime-as-of-ms-path` — falls back to wall-clock quantized to 5-second buckets (setup_readiness.cljs:27-37). Every 5 seconds the bucket increments, both memo keys change, and the next streaming render re-runs the entire alignment. The trace's long tasks start at t=29.1 s, 33.2 s, and 38.5 s — the ~5 s cadence — and only one of the three was click-triggered. Inside alignment, `as-of-ms`/`stale-after-ms` feed exactly one output: the `:freshness` map (`alignment.cljs:507`), computed by `calendar/freshness`, an O(1) function over the already-computed calendar.

Also relevant: `build-request`'s memo legitimately busts when the draft changes (a goal-card click changes `:objective`/`:return-model`), which is fine — but today that rebuild cascades into full re-alignment because the goal click often lands in a fresh 5 s bucket, and because `build-engine-request` may pass a changed `alignment-universe` only when history assumptions exist (objective changes flip `return-required-for-objective?`, which can change which assumption entries count as "structurally complete" and are excluded from alignment — `request_builder.cljs:572-588`). With M2 in place, an unchanged alignment universe hits the data-keyed memo regardless of bucket.

The LCP element is a text div on the setup page; LCP 6.07 s is a downstream symptom of the same main-thread saturation during load (the first full alignment ran as a 3.77 s task right as history arrived). CLS 0.81 accumulated during load as panels settled; it is re-measured in M4 after the main-thread fixes land, because long tasks delay paints that would otherwise land before content-shifting updates batch up.

## Plan of Work

Milestone 1 — lazy legacy fallback (alignment.cljs). In `align-api-v2-history-inputs`, reorder the per-row `let` so the legacy series is computed only when it can possibly be used:

    fallback-wanted? = (and (not hard-api-warning?)
                            (legacy-fallback/series-fallback-needed? api-series))
    legacy-series*   = (when fallback-wanted? (legacy-fallback/series ...))
    legacy-fallback? = (and fallback-wanted? (usable-series? legacy-series*))

This is a pure short-circuit: `series-fallback-needed?` depends only on the api series, so the chosen series, warnings, funding summaries, and every downstream output are bit-identical. Instruments served by api-v2 (the common case after the history bundle loads) stop paying candle+funding normalization entirely.

Milestone 2 — data-keyed alignment memo (request_builder.cljs). Change the private `align-history` wrapper so the memo key/inputs map excludes `:as-of-ms` and `:stale-after-ms`, the memoized value is the alignment result *without* `:freshness`, and each call returns `(assoc cached :freshness (calendar/freshness (:calendar cached) as-of-ms stale-after-ms))`. Implementation detail: pass the data-only inputs to `history-loader/align-history-inputs` (alignment already tolerates nil as-of — `freshness` guards with `number?` — but to keep one honest seam, compute freshness in the wrapper and `assoc` it over whatever alignment stamped). Every call site (main universe, current-portfolio history, proxy regression sub-alignments) flows through this wrapper, so all results keep a correct, current `:freshness`. Add a code comment stating the invariant: nothing inside `align-api-v2-history-inputs` may consume `as-of-ms` except the freshness stamp, or the memo becomes dishonest.

Milestone 3 — re-profile and decide on contingencies. With M1+M2 in a dev build (`npm run dev`, worktree recipe below), record a fresh DevTools trace over: history-load settle, two goal-card clicks, and ~30 s of idle streaming. Expected: no render task over ~150 ms attributable to `align-api-v2-history-inputs`; goal-card click-to-highlight under ~200 ms. The trace also showed a floor of ~90 ms streaming renders whose profile is generic view/seq work, not alignment; if after M1+M2 streaming renders still exceed ~50 ms, apply in order, re-measuring after each: (a) memoize `normalize-funding-history`/`normalize-candle-history` per raw-rows identity (small volatile cache keyed by the raw vector, mirroring `enrich-draft-instruments`) so fallback-heavy universes stay cheap; (b) memoize `orderbook-cost-contexts`+`native-marks-by-id` decoration in `build-readiness` on `[universe-identity market-by-key-identity orderbook-state-identity bucketed-now]`. Do not implement either preemptively — they add cache-invalidation surface for a cost that M1/M2 likely removes.

Milestone 4 — LCP/CLS re-measurement. Reload the route cold with the Performance panel's web-vitals lane. Expect LCP to fall far below 6 s once the first paint is no longer queued behind multi-second tasks. For CLS, inspect the "worst cluster" attribution: if optimizer cards/right rail still shift after content settles, reserve space (min-height skeletons on the loading cards/rail rows that already exist from the proxy-loading-UX work) — scoped to the shifting elements the panel names, nothing speculative.

Milestone 5 — validation. Unit tests: extend `test/hyperopen/portfolio/optimizer/application/history_loader_api_v2_test.cljs` (or the `_split_test` sibling if size-capped; check `namespace-size` lint) with (a) a `with-redefs` invocation-counting test proving `legacy-fallback/series` is NOT called for an instrument whose api-v2 series is usable and IS called when it is missing, with outputs equal to the pre-change expectation fixtures; (b) a request-builder test proving two `align-history` calls that differ only in `as-of-ms` return identical results apart from `:freshness` and hit the memo (count `align-history-inputs` invocations via `with-redefs`). Then `npm run gates` and the existing Playwright optimizer loading-UX spec.

## Concrete Steps

Work from the repo root (this worktree). A fresh worktree must be bootstrapped first:

    npm run setup:worktree

Implement M1 in `src/hyperopen/portfolio/optimizer/application/history_loader/api_v2/alignment.cljs` (the `rows` mapv around line 248). Implement M2 in `src/hyperopen/portfolio/optimizer/application/request_builder.cljs` (`align-history`, around line 509).

Run the focused tests first, then the full gates:

    npm test            # shadow-cljs node test build; expect 0 failures
    npm run gates       # check + test + test:websocket; expect PASS matrix

For the live measurement (M3/M4), run the dev server on a free port (worktree recipe: :8080 is often held by the main checkout; use :8090):

    npm run dev -- --config-merge '{:dev-http {8090 "public"}}'

then open `http://localhost:8090/portfolio/optimize`, use spectate mode for a real wallet with a large universe, record a Performance trace covering load + two goal-card clicks + 30 s idle, and compare against the baseline numbers in `Artifacts and Notes`.

## Validation and Acceptance

Acceptance is behavioral:

1. On a loaded setup page with a large universe, clicking "Minimum risk" then "Maximum Sharpe" highlights the clicked card in under ~200 ms (baseline: 2.8–4.4 s measured click-to-feedback in the owner trace).
2. A 30-second idle Performance trace of the setup page contains no main-thread task over 200 ms attributable to `align-api-v2-history-inputs` (baseline: 3.2–3.8 s tasks recurring at ~5 s cadence).
3. Local web-vitals LCP for the route drops materially from 6.07 s (record the new number here); CLS improves or the worst cluster is fixed under M4 (record).
4. `npm run gates` passes; the new unit tests fail before M1/M2 and pass after; the Playwright optimizer loading-UX spec passes.
5. No output change: readiness/warnings/run results for a fixture universe are equal before/after (the invocation-counting tests assert equal outputs, not just fewer calls).

## Idempotence and Recovery

Both code changes are small, additive-in-spirit edits to pure functions and can be re-applied or reverted independently; the memo shape change in M2 is process-local (a `volatile!`) with no persisted format. If M2 causes any freshness-related regression (a consumer found to read `as-of-ms` deep inside alignment), revert M2 alone — M1 stands on its own and removes the majority of the cost. Contingencies in M3 are explicitly optional and gated on fresh measurements.

## Artifacts and Notes

Baseline evidence from `Trace-20260708T165504.json` (135,286 events; DevTools trace with CPU samples, captured 2026-07-08):

    Long main-thread tasks (>50 ms), renderer pid 39643:
      t=29.132s  3766 ms   (history-load settle render)
      t=33.197s  3325 ms   (render after goal-card click 1 at t=32.08s)
      t=38.481s  3244 ms   (streaming render; 5s as-of bucket roll — no click)
      t=41.731s   637 ms   (render after click 2 at t=39.65s, landed mid-task C)
      + a dozen ~90-95 ms streaming renders between them

    Click events: pointerdown/click at t=32.03/32.08s and t=39.56/39.65s —
    each click's visual feedback waited for the next multi-second render
    (~4.4 s and ~2.8 s click-to-paint).

    Sampled-stack attribution of task B (25,881 samples, on-stack %):
      89.5%  setup_view / optimizer_view render
      87.3%  view_model.workspace/workspace-model
      86.1%  setup_readiness/build-readiness → build-request
      86.1%  request_builder/build-engine-request → align-history
      86.0%  history_loader.api_v2.alignment/align-api-v2-history-inputs
      64.9%  …legacy_fallback/series          ← computed per instrument, per render
      58.6%  …legacy_fallback/legacy-funding-summary
    Self-time is dominated by cljs.core compare/sort/array-map ops and GC
    (14,344 scavenger events in the trace) — allocation churn from
    re-normalizing and re-sorting history rows.

    Web-vitals panel (owner screenshot): LCP 6.07 s (poor), CLS 0.81 (poor,
    "worst cluster 2 shifts"); LCP element is a text div on the setup page.

## Interfaces and Dependencies

No public API changes. `align-api-v2-history-inputs` keeps its exact input/output contract (M1 is call-order-internal). The private `request-builder/align-history` keeps its signature; only its memo key and the point where `:freshness` is attached change. New/changed tests live under `test/hyperopen/portfolio/optimizer/application/`. Respect the repo memories: history-bundle `instrument_id` stays the canonical backend id (no proxy-id swaps), and calendar-safe merge semantics in `history-merge` are untouched by this plan.
