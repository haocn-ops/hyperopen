# Reduce Residual Non-Tooltip Hover-Window Jank On Shared Charts

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

Related `bd` issue: `hyperopen-mceo` ("Investigate residual non-tooltip long tasks and layout shifts during shared chart hover"), ready to close after this plan archival.

## Purpose / Big Picture

After this change, hovering the shared performance charts on populated `/portfolio` and Vault detail should remain a local chart interaction even when the surrounding route is busy. The tooltip path is already fast, but the broader hover window still records long tasks and layout shifts from route-shell and value-update work. A contributor can verify the improvement by profiling both routes with benchmarks selected and observing that the hover window no longer records blocking tasks above `50ms`, while hover settle remains below `25ms` p95 and tooltip behavior stays unchanged.

## Progress

- [x] (2026-03-31 12:07 EDT) Claimed `hyperopen-mceo` in `bd`.
- [x] (2026-03-31 12:10 EDT) Committed the preceding documentation-only follow-up for `hyperopen-atw0` so this work starts from a clean worktree.
- [x] (2026-03-31 12:13 EDT) Authored this active ExecPlan.
- [x] (2026-03-31 12:44 EDT) Audited the current residual hover-window evidence and mapped the dominant remaining work to route-shell rerenders, account-table churn, and eager Vault detail activity projections.
- [x] (2026-03-31 13:04 EDT) Implemented route-local hover freezing for shared chart surfaces by tracking hovered surfaces in the D3 runtime and reusing cached Portfolio and Vault detail route sections while hover is active.
- [x] (2026-03-31 13:32 EDT) Added focused regression coverage for the new hover-freeze behavior plus hover-state cleanup fixtures in the existing route-view suites.
- [x] (2026-03-31 13:43 EDT) Ran the smallest relevant Playwright checks plus `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-03-31 13:25 EDT) Promoted the hover-profile harness into tracked repo tooling and added per-step logging, timeouts, and failure artifacts so the trace no longer hangs silently.
- [x] (2026-03-31 13:29 EDT) Identified the remaining hover failure as a benchmark-suggestion overlay bug, not residual route jank: selecting a returns benchmark incorrectly reopened the suggestion menu and covered the right side of the chart.
- [x] (2026-03-31 13:31 EDT) Closed the benchmark suggestion overlay on selection for both Portfolio and Vault detail and updated the existing action-level regression expectations.
- [x] (2026-03-31 13:39 EDT) Re-ran the deterministic hover-profile harness and recorded a clean post-fix artifact for both populated `/portfolio` and Vault detail.

## Surprises & Discoveries

- Observation: the tooltip-specific follow-up is closed, but its repro still recorded route-wide long tasks during the sampled hover window.
  Evidence: `/hyperopen/docs/qa/shared-chart-hover-reprofile-2026-03-30.md` records Portfolio `max long task` about `312ms` and Vault detail `max long task` about `439ms` while also stating the shared tooltip root no longer appeared as the dominant layout-shift source.

- Observation: the residual issue is already split from tooltip work and is tracked as separate debt.
  Evidence: `bd show hyperopen-mceo --json` describes remaining route-shell, account-table, value-span, and live-update work that overlaps hover after `hyperopen-atw0` completed.

- Observation: the repo already had the essential hover-freeze implementation staged locally rather than missing entirely.
  Evidence: `/hyperopen/src/hyperopen/views/chart/d3/runtime.cljs`, `/hyperopen/src/hyperopen/views/portfolio_view.cljs`, and `/hyperopen/src/hyperopen/views/vaults/detail_view.cljs` were all dirty when investigation began and already formed a coherent path: D3 runtime hover tracking plus cached route-shell reuse keyed by route and surface.

- Observation: the remaining static-code risk still points at route-wide churn more than tooltip DOM work.
  Evidence: a read-only audit found unconditional route rebuilds in `/hyperopen/src/hyperopen/runtime/bootstrap.cljs`, full Portfolio route recomputation in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`, and eager Vault activity projection in `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`.

- Observation: the local post-fix profiling harness did not complete cleanly in this session.
  Evidence: `HYPEROPEN_POST_IDLE_SETTLE_MS=5000 node tmp/browser-inspection/mceo-hover-profile.mjs` remained hung for over a minute without producing a new artifact directory and had to be terminated.

- Observation: the apparent residual settle failure was caused by the benchmark suggestion overlay stealing pointer hit-testing on the chart's right half.
  Evidence: an element hit-test probe found `portfolio-returns-benchmark-suggestion-SOL` under right-side chart coordinates after benchmark selection, while the hover line remained stuck at its initial x-position and the tooltip was hidden. The action layer in `/hyperopen/src/hyperopen/portfolio/actions.cljs` and `/hyperopen/src/hyperopen/vaults/application/detail_commands.cljs` reopened `*-returns-benchmark-suggestions-open?` on selection instead of closing it.

- Observation: once the benchmark overlay bug was fixed, the fresh deterministic profile met the bead's thresholds on both routes.
  Evidence: `/hyperopen/tmp/browser-inspection/mceo-hover-profile-1774963460815/profile.json` reports Portfolio settle `p95 ≈ 0.10ms`, Vault settle `p95 ≈ 0.10ms`, zero hover-window long tasks on both routes, and zero settle timeouts.

## Decision Log

- Decision: treat `hyperopen-mceo` as a new residual-perf task, not as a reopening of the tooltip implementation issue.
  Rationale: the March 30 repro shows the tooltip path is no longer the dominant shift source, so the remaining work needs a narrower investigation and should preserve the transform-only tooltip behavior.
  Date/Author: 2026-03-31 / Codex

- Decision: land the already-staged hover-freeze route isolation plus deterministic regression tests before attempting deeper VM refactors.
  Rationale: the strongest current hypothesis is route-shell churn overlapping hover, and the staged changes directly isolate Portfolio and Vault detail from unrelated updates while preserving the shared tooltip path.
  Date/Author: 2026-03-31 / Codex

- Decision: keep `hyperopen-mceo` open until a fresh post-fix hover profile is captured.
  Rationale: the code path and deterministic browser regressions now pass, but this bead's acceptance criteria are performance thresholds, so it should not be closed on static reasoning alone after the local profiling harness hung.
  Date/Author: 2026-03-31 / Codex

- Decision: fix the benchmark-selection overlay before attempting deeper hover-runtime or VM changes.
  Rationale: the fresh hit-test probe showed the chart was being occluded by UI state reopened on selection, so the remaining failure was a routing-of-input bug rather than another expensive render path.
  Date/Author: 2026-03-31 / Codex

- Decision: close `hyperopen-mceo` after archiving this plan.
  Rationale: the new tracked profiler completed successfully, both routes met the hover settle and long-task thresholds, and the relevant browser and repo validation gates passed after the overlay fix.
  Date/Author: 2026-03-31 / Codex

## Outcomes & Retrospective

The route-level mitigation is now in place: shared chart surfaces mark hover-active state in the D3 runtime, and both `/portfolio` and Vault detail reuse their last non-hover route shell while hover remains active on the same route. Deterministic coverage now asserts that Portfolio chart-tab state and Vault detail hero content remain frozen until hover clears.

The final user-facing fix was smaller than expected: benchmark selection was leaving the suggestions overlay open above the chart surface. Closing that menu on selection restored the full hoverable plot area, after which the tracked profiler reported clean settle timings and zero hover-window long tasks.

Browser verification and repo validation are green:

- `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio route exposes deterministic interaction states"`
- `npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "vault position coin jumps to the trade route market"`
- `HYPEROPEN_POST_IDLE_SETTLE_MS=1000 HYPEROPEN_STEP_TIMEOUT_MS=8000 HYPEROPEN_TRACE_TIMEOUT_MS=30000 npm run browser:profile:mceo`
- `npm run check`
- `npm test`
- `npm run test:websocket`

Fresh performance evidence is now recorded at:

- `/hyperopen/tmp/browser-inspection/mceo-hover-profile-1774963460815/profile.json`

Key measurements from that artifact:

- populated Portfolio hover settle `p95 ≈ 0.10ms`, `max ≈ 0.60ms`, `timeoutCount = 0`, `maxLongTaskMs = 0`
- Vault detail hover settle `p95 ≈ 0.10ms`, `max ≈ 0.10ms`, `timeoutCount = 0`, `maxLongTaskMs = 0`
- remaining layout shifts are tiny numeric-span updates (`CLS`-style totals `≈ 0.000028` on Portfolio and `≈ 0.000015` on Vault detail), not blocking hover-window work

## Context and Orientation

The shared D3 hover runtime lives in `/hyperopen/src/hyperopen/views/chart/d3/runtime.cljs`. Portfolio mounts it through `/hyperopen/src/hyperopen/views/portfolio/chart_view.cljs` and the top-level Portfolio route model in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`. Vault detail mounts the same runtime through `/hyperopen/src/hyperopen/views/vaults/detail/chart_view.cljs` and builds its route model in `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`.

A “hover window” in this plan means the short period while repeated pointer movement sweeps across the chart host and the browser is sampled for layout shifts and long tasks. The tooltip path is the code that positions and updates the shared tooltip itself. The residual problem is everything else on the route that still changes or recomputes while hover is active: route shell containers, account or summary panels, numeric value spans, benchmark derivations, or any store-driven updates that overlap the hover sample.

The immediate historical context is recorded in `/hyperopen/docs/exec-plans/completed/2026-03-15-shared-chart-hover-jank-reduction.md` and `/hyperopen/docs/qa/shared-chart-hover-reprofile-2026-03-30.md`. Those documents establish that tooltip positioning now uses transforms, hover settle is already fast, and the remaining debt is broader route-level work rather than the tooltip shell.

## Plan of Work

First, inspect the current populated Portfolio and Vault detail hover flows and map the residual profiling evidence back to the code paths that can still run during hover. This includes route-level view models, numeric summary cards or shells near the chart, and any benchmark-selection or live-update path that can write into the DOM while the pointer moves.

Second, reproduce the issue locally with deterministic browser evidence. The repro should use a populated `/portfolio` route, likely via spectate mode if that remains the most stable local fixture, and a concrete Vault detail route. The trace should distinguish hover settle timing from broader hover-window long tasks and layout shifts so the implementation changes can target the real remaining source.

Third, implement the smallest fix set that removes or defers that residual route work. Likely candidates are stricter cache boundaries, guarding route-shell value churn during hover, isolating DOM writes to stable containers, or moving more derived computation off the synchronous hover-adjacent path. Public APIs should remain stable, and the transform-only tooltip path should not be reworked unless the new evidence proves it regressed.

Finally, rerun the required repository gates and the smallest relevant browser verification command, then repeat the local hover profiling. The plan is complete only when the updated evidence shows no hover-window blocking task above `50ms`, hover settle remains at or below `25ms` p95, and any residual layout shifts are either eliminated or clearly proven unrelated to the shared chart interaction path.

## Concrete Steps

From `/hyperopen`:

1. Inspect the existing repro and code surfaces:

   `sed -n '1,260p' docs/qa/shared-chart-hover-reprofile-2026-03-30.md`
   `sed -n '1,220p' docs/exec-plans/completed/2026-03-15-shared-chart-hover-jank-reduction.md`
   `rg -n "hover|benchmark|metrics|summary|value span|translate3d" src/hyperopen/views src/hyperopen/vaults test`

2. Reproduce the residual hover-window profile locally with the app running:

   `npm run dev`
   `npm run browser:preflight`
   Then run a deterministic Playwright or browser-inspection harness against populated `/portfolio` and Vault detail routes, saving artifacts under `/hyperopen/tmp/browser-inspection/`.

3. Edit the smallest set of source files needed to isolate or remove remaining hover-window work.

4. Run the required validation gates:

   `npm run check`
   `npm test`
   `npm run test:websocket`

5. Run the smallest relevant browser regression command before broader QA:

   `npm run test:playwright:smoke`

6. Repeat the hover profiling and record measured results in this plan and, if useful as durable evidence, in `/hyperopen/docs/qa/`.

## Validation and Acceptance

This work is accepted when all of the following are true:

1. A populated `/portfolio` hover repro and a Vault detail hover repro both show hover settle at or below `25ms` p95.
2. Neither sampled hover window records a blocking long task above `50ms`.
3. The shared tooltip path remains transform-positioned and does not reappear as a sampled layout-shift source.
4. `npm run check`, `npm test`, and `npm run test:websocket` pass after the code changes.
5. The smallest relevant Playwright browser command passes after the code changes.
6. The final evidence is recorded in tracked documentation or artifact paths so a future contributor can verify what changed.

## Idempotence and Recovery

The investigation steps are safe to repeat. Browser profiling artifacts should be written to new timestamped directories under `/hyperopen/tmp/browser-inspection/` so reruns do not overwrite earlier evidence. If a candidate fix regresses chart behavior, revert only that change, keep the new evidence, and continue from the last known good tooltip implementation rather than reworking the tooltip path blindly.

## Artifacts and Notes

Pre-fix residual evidence currently lives at:

- `/hyperopen/docs/qa/shared-chart-hover-reprofile-2026-03-30.md`

That note reports:

  - populated Portfolio hover settle p95 about `7.4ms`, but long tasks up to about `312ms`
  - Vault detail hover settle p95 about `0.5ms`, but long tasks up to about `439ms`
  - sampled layout-shift sources attributed to route-shell containers and small value spans instead of the tooltip root

## Interfaces and Dependencies

This work should continue to use the existing shared chart runtime in `/hyperopen/src/hyperopen/views/chart/d3/runtime.cljs`, the existing route models in `/hyperopen/src/hyperopen/views/portfolio/vm.cljs` and `/hyperopen/src/hyperopen/views/vaults/detail_vm.cljs`, and the existing browser verification stack documented in `/hyperopen/docs/BROWSER_TESTING.md`. New code should prefer tightening existing cache or update boundaries over inventing parallel UI state systems. Any new browser evidence should use the existing debug bridge and profiling harness patterns rather than ad hoc route mutations.

Plan revision note: 2026-03-31 12:13 EDT - Initial active ExecPlan created after claiming `hyperopen-mceo`, confirming the residual non-tooltip hover-window perf debt from the March 30 repro, and separating this work from the already-completed tooltip follow-up.
Plan revision note: 2026-03-31 13:48 EDT - Recorded the landed hover-freeze route-isolation path, deterministic regression coverage, green validation gates, and the fact that the local post-fix profiling harness hung before producing a fresh artifact, so `hyperopen-mceo` remains open pending reprofiling.
Plan revision note: 2026-03-31 13:42 EDT - Promoted the profiler into tracked tooling, found the benchmark selector overlay occluding the chart, closed that overlay on selection, and recorded a clean post-fix artifact that satisfies the bead thresholds.
