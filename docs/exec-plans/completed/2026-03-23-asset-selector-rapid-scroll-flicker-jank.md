# Eliminate Asset Selector Rapid-Scroll Flicker And Jank

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is governed by `/hyperopen/.agents/PLANS.md` and `/hyperopen/docs/PLANS.md`. The tracked work item for this plan is `hyperopen-2614`; the `bd` issue is historical local tracker context for this plan.

## Purpose / Big Picture

The desktop `/trade` asset selector still flashes black for a split second and can feel sticky when the user rapidly scrolls through a long list of markets. The earlier blank-window and nested-render problems are no longer the main issue; the current problem is scroll-time route churn, timer churn, and deferred selector updates that still let the visible list stutter under load.

After this work, a user should be able to open the asset selector on `/trade`, fling the scroll wheel or trackpad through the list several times, and keep seeing continuous rows with no visible black flashes and no obvious frozen frames. The goal is not to eliminate all work during scroll, but to confine that work so the selector stays visually stable and responsive.

## Progress

- [x] (2026-03-23 17:34Z) Created and claimed `hyperopen-2614` for the rapid-scroll flicker and jank regression.
- [x] (2026-03-23 17:36Z) Reviewed prior selector fixes and current browser traces; confirmed the remaining issue is still observable under rapid manual scroll.
- [x] (2026-03-23 18:18Z) Added a selector scroll-active runtime signal and froze the desktop orderbook and order-form panels while the selector is actively scrolling so route churn can reuse their last settled snapshots.
- [x] (2026-03-23 18:20Z) Added focused render-cache coverage proving orderbook and order-form stop rerendering during active selector scroll and resume live rerenders when scrolling settles.
- [x] (2026-03-23 18:42Z) Extended the same active-scroll freeze boundary to the desktop chart/account siblings and the desktop active-asset panel, with render-cache coverage proving those surfaces also stop rerendering during active selector scroll.
- [x] (2026-03-23 18:32Z) Froze the desktop active-asset selector wrapper props while the local selector list runtime is actively scrolling and memoized the wrapper so parent churn no longer rebuilds the selector dropdown during a fling.
- [x] (2026-03-23 18:40Z) Gated selector market-cache persistence to actual selector refreshes instead of every live selector market patch so websocket updates stop re-sorting and re-normalizing the full selector list during scroll.
- [x] (2026-03-23 18:49Z) Added a stronger blank-pixel coverage probe, confirmed the remaining one-frame flash was still present on deep jump-scrolls, and switched the desktop selector to full-row rendering for normal-sized market sets while keeping the large-list virtualization fallback.
- [x] (2026-03-23 18:50Z) Tightened the committed browser regression to assert blank viewport coverage stays at `<=1px`, not merely that some rows remain visible.
- [x] (2026-03-23 18:37Z) Re-ran the committed asset-selector Playwright regression plus the required repository gates after the panel-freeze experiment.
- [x] (2026-03-23 23:15Z) Reproduced the remaining bottom-edge upward-scroll jank with a dedicated headless wheel probe and isolated the reversal-specific path to eager settle/resume behavior plus selector active-context subscription churn.
- [x] (2026-03-23 23:19Z) Moved selector live-subscription resume behind a post-settle idle window, stopped `set-asset-selector-scroll-top` from implicitly resuming paused selector subscriptions, and removed eager `scrollend` finalization from the desktop selector runtime.
- [x] (2026-03-23 23:22Z) Fixed the remaining bottom-edge reversal case by treating `wheel` input on the selector container as active scroll activity even when `scrollTop` is pinned at a boundary, so settle cannot fire while the user is still interacting at the edge.
- [x] (2026-03-23 23:24Z) Changed paused selector active-context sync to freeze the current owner subscription set instead of diffing to `#{}`, removing the unsubscribe/resubscribe tax from scroll-start boundaries while keeping the selector’s visible-subscription refresh settled.
- [x] (2026-03-23 23:27Z) Re-ran the bottom-up wheel probe, committed browser regression, required repo gates, and governed browser QA after the reversal-specific fixes.
- [x] (2026-03-23 23:35Z) Split the desktop selector runtime signal into “actual scroll active” versus a slightly longer “heavy-panel freeze active” window so selector controls resume immediately after settle while sibling desktop panels stay frozen through the live-subscription resume cooldown.
- [x] (2026-03-23 23:37Z) Made the desktop trade view compute heavy panel state lazily behind the selector freeze boundary so scroll-time route churn no longer rebuilds chart, account, orderbook, order form, or active-asset view-state maps while the selector is frozen.
- [x] (2026-03-23 23:42Z) Re-ran the focused selector/trade-view tests, the committed asset-selector Playwright regression, a fresh bottom-up wheel probe, the full repo gates, and governed browser QA after the lazy-freeze follow-up.
- [x] Refresh browser evidence after each experiment with a short trace or QA artifact and record whether the change reduced black flashes, reduced frozen scroll frames, both, or neither.
- [x] Reduce or eliminate the remaining scroll-time work on the selector path without regressing reachability of the full market list.
- [x] (2026-03-25 15:59 EDT) Confirmed the latest desktop full-render and bottom-edge reversal behavior on real manual hardware scroll: the user rapidly scrolled all the way down and all the way back up in a live browser session and reported no visible jank, no flickering, and no follow-up chart/runtime issue needed before closing this ExecPlan.

## Surprises & Discoveries

- Observation: the first blank-window bug was not the whole problem. Once the visual window stayed populated, the remaining failure shifted to main-thread starvation during scroll.
  Evidence: the 2026-03-23 traces showed `on_scroll` improving into the mid-20ms range while `runtime.bootstrap` flushes and `goog.async.nexttick` batches still produced long tasks that lined up with the visible flicker.

- Observation: scroll-time store writes are still too expensive for this surface when they happen on every tick.
  Evidence: the selector path still wrote `:scroll-top` into global app state, and the traces showed route-wide bootstrap work after scroll events instead of a selector-local update only.

- Observation: clearing and re-installing settle timers on every scroll event created avoidable churn.
  Evidence: the latest trace segment showed repeated timer activity alongside scroll events, which is consistent with avoidable scheduling overhead rather than a single expensive render.

- Observation: prior fixes did improve some symptoms but not the user-visible outcome.
  Evidence: windowed virtualization and larger overscan eliminated the obvious blank viewport, and the nested render warnings disappeared, but the user can still reproduce brief black flashes and scroll stutter in a live browser session.

- Observation: the earlier browser regression was too weak to catch the remaining flash because it only asserted that some rows stayed visible, not that the viewport stayed fully covered by rows.
  Evidence: the stronger live coverage probe from the parent thread showed `blank=256px` immediately after deep `scrollTop` jumps even while later frames recovered, and the tightened Playwright regression now checks blank coverage directly instead of `visibleRows > 0`.

- Observation: the first automated post-fix headless probe was invalid because it started before the selector finished materializing its rows.
  Evidence: the probe artifact at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-scroll-probe-2026-03-23T18-02-14.985Z/probe.json` showed `scrollHeight=64`, `clientHeight=64`, and `0` rendered rows before the list populated. Tightening the probe to wait for visible selector rows restored comparable measurements.

- Observation: freezing only the orderbook and order-form reduced worst-case selector-scroll latency, but the largest remaining improvement came from also freezing the desktop chart/account siblings and the desktop active-asset panel.
  Evidence: the baseline probe at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-scroll-probe-2026-03-23T18-02-33.424Z/probe.json` measured mean `239.9ms`, median `264.2ms`, `10` samples above `200ms`, and `27` long tasks. After sibling freezes, `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-scroll-probe-2026-03-23T18-08-06.304Z/probe.json` improved to mean `230.8ms`, median `236.2ms`, and `8` samples above `200ms`. After also freezing the desktop active-asset panel, `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-scroll-probe-2026-03-23T18-10-54.962Z/probe.json` improved further to mean `198.9ms`, median `204.6ms`, `8` samples above `200ms`, max long task `273ms`, and `17` long tasks while keeping visible rows stable at `11-12`.

- Observation: once the route-wide desktop siblings were frozen, the next meaningful savings came from stopping parent selector rerenders and cache-persistence churn rather than changing the list window again.
  Evidence: the post-freeze wheel probe in the parent thread showed worst wheel-to-scroll delay improving from roughly `117ms` to roughly `92ms` after freezing selector props and memoizing the wrapper, and then the startup watcher fix cut the same probe’s long-task total from roughly `173ms` to roughly `53ms` while keeping visible rows stable at `11`. The follow-up CPU profile at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-cpuprofile-2026-03-23T18-09-55-020Z/profile.cpuprofile` also showed the remaining selector-specific hot samples dominated by `hyperopen.asset_selector.markets_cache/*` and `hyperopen.asset_selector.query/*`, which traced back to cache-persistence work rather than row-window blanking.

- Observation: the remaining bottom-edge reversal jank was not just “fast upward scroll from the bottom.” The idle timer was only refreshed by `scroll` events, so continued wheel input at the bottom edge could let the selector finalize and resume while the user was still interacting, because wheel gestures at a pinned boundary do not produce new scroll events.
  Evidence: the new bottom-up probe at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-bottom-up-probe-2026-03-23T19-11-38-605Z/probe-summary.json` still showed fast first upward movement but noisy bad-tail stalls, and the telemetry run showed a full selector active-context transition even during a down-to-bottom-then-up sequence until wheel input itself started extending the active-scroll window.

- Observation: “pause selector live subscriptions” was still implemented as “diff desired selector coins to `#{}`,” which meant every scroll session paid an owner-scoped unsubscribe burst on entry and a resubscribe burst on resume even after the view stopped rerendering mid-scroll.
  Evidence: the reversal telemetry probe consistently emitted a full set of selector `Unsubscribed` / `Subscribed` active-asset-context logs during the bad path. Freezing the current owner subscription set while paused removed that forced zero-state diff while preserving the settled visible-set refresh.

- Observation: freezing the heavy desktop trade surfaces at render time was not enough by itself because `trade-view` still eagerly built the “frozen” state maps before deciding to reuse them.
  Evidence: the uncommitted `trade_view.cljs` follow-up showed `active-asset-view-state`, `trade-chart-view-state`, `account-info-view-state`, `account-equity-view-state`, `orderbook-view-state`, and `order-form-view-state` were all evaluated before `selector-scroll-snapshot` returned the cached snapshot. Making the snapshot helper thunk-based removed that remaining eager work, and the new render-cache test now proves those state builders stop executing while selector freeze is active.

- Observation: using the same selector runtime signal for both “user is actively scrolling” and “keep heavy siblings frozen through the cooldown” risked leaving selector-adjacent UI stale after scrolling settled.
  Evidence: once the freeze window extended beyond immediate scroll activity, the wrapper-freeze logic and desktop sibling-freeze logic were both keying off the same global flag. Splitting that state into `asset-list-scroll-active?` and `asset-list-freeze-active?` kept selector controls live again while preserving the longer sibling freeze window.

## Decision Log

- Decision: treat this as an evidence-driven bug investigation rather than a one-shot refactor.
  Rationale: the failure mode has shifted at least twice, so the plan needs a running log of what each experiment changed and what it failed to change.
  Date/Author: 2026-03-23 / Codex

- Decision: keep the active selector fix scoped to `/hyperopen/src/hyperopen/views/asset_selector_view.cljs` and the smallest supporting tests unless browser evidence proves a deeper architectural change is required.
  Rationale: the traces point at selector-local scroll handling and state churn, so the next useful step is to narrow the hot path before broadening the change surface.
  Date/Author: 2026-03-23 / Codex

- Decision: track every experiment in the research log before moving to the next one.
  Rationale: the user explicitly wants the work to avoid circular retries. The log is the mechanism that prevents re-trying the same idea without new evidence.
  Date/Author: 2026-03-23 / Codex

- Decision: for this pass, freeze only the desktop orderbook and order-form panels while selector scrolling is active, and only when the asset selector dropdown is actually open.
  Rationale: the traces implicated `active-assets`, `orderbooks`, and route-wide render churn, but the selector itself must stay live. Gating the freeze to the open desktop selector keeps the behavior narrow and avoids leaking stale snapshots into unrelated trade renders.
  Date/Author: 2026-03-23 / Codex

- Decision: broaden the active-scroll freeze boundary to the desktop chart/account siblings and the desktop active-asset panel after the orderbook/order-form-only experiment.
  Rationale: the first panel-freeze pass improved the probe but left too much selector-scroll latency on the table, which meant the remaining main-thread pressure was still coming from desktop sibling renders beyond the orderbook and order form.
  Date/Author: 2026-03-23 / Codex

- Decision: once the desktop siblings were frozen, hold the selector wrapper props stable during active scroll and memoize the wrapper instead of changing the list virtualization rules again.
  Rationale: the CPU profile still sampled `hyperopen.views.asset_selector_view` itself after the sibling-freeze pass, which meant parent churn was still rebuilding the dropdown shell even though the row host had already moved to a local runtime.
  Date/Author: 2026-03-23 / Codex

- Decision: do not persist the selector market cache off live websocket-driven selector market patches.
  Rationale: the cache exists for startup symbol metadata, not live marks, and the startup watcher was re-running `hyperopen.asset_selector.markets_cache` sorting/normalization work during scroll for no user-visible benefit.
  Date/Author: 2026-03-23 / Codex

- Decision: for normal-sized desktop market sets, render the full selector row list instead of a windowed slice; keep the existing virtualization path only as a fallback for much larger lists.
  Rationale: the latest probe showed the remaining black flash was the viewport outrunning the mounted slice for a frame, which cannot be prevented reliably by more throttling once the scroll delta exceeds the retained buffer. Rendering the full current desktop list removes that one-frame failure mode while the earlier route-freeze work keeps the surrounding churn low enough to absorb the extra rows.
  Date/Author: 2026-03-23 / Codex

- Decision: do not let `scrollend` or a `scrollTop`-only idle clock decide that selector scrolling has settled.
  Rationale: the remaining bottom-edge case proved that boundary wheel input can continue after `scrollTop` stops changing, so settle must follow user interaction at the container boundary, not just scroll deltas.
  Date/Author: 2026-03-23 / Codex

- Decision: while selector live updates are “paused,” freeze the selector owner’s existing active-asset-context subscription set instead of syncing desired coins to `#{}`.
  Rationale: by this point the selector UI itself and the heavy desktop siblings were already held stable during scroll, so forced unsubscribe/resubscribe churn was more expensive than the residual cost of letting the pre-scroll visible coin set stay subscribed until the settled refresh.
  Date/Author: 2026-03-23 / Codex

- Decision: separate the selector runtime’s “actively scrolling” signal from the slightly longer “freeze heavy siblings” signal.
  Rationale: the selector wrapper and container interactions should resume as soon as scrolling settles, but the heavy desktop trade surfaces still benefit from staying frozen through the short live-subscription resume delay. One flag cannot serve both timing requirements without either reintroducing sibling churn or holding selector UI stale too long.
  Date/Author: 2026-03-23 / Codex

- Decision: make `trade-view` freeze snapshots lazy by passing a thunk into `selector-scroll-snapshot`.
  Rationale: a cached snapshot is only useful if the expensive next state is not computed first. The remaining route-churn hole was eager view-state derivation, not repainting, so the freeze boundary had to cover state-building work as well as child view invocation.
  Date/Author: 2026-03-23 / Codex

## Outcomes & Retrospective

The asset selector no longer exhibits the two user-visible failures that kept this plan open: blank or black flashes during deep desktop scroll, and sticky late reversal behavior when scrolling back up from the bottom edge. The final selector/runtime path now renders full rows for normal-sized desktop lists, keeps heavy desktop siblings frozen only through the short resume cooldown, avoids eager `scrollend` finalization, and freezes the selector owner’s active-context subscriptions instead of thrashing them to `#{}` at scroll boundaries. Deterministic browser and repo gates remained green through the final iterations, and the closeout manual desktop verification on 2026-03-25 confirmed that rapid hardware scrolling all the way down and back up now shows no visible jank and no flickering.

No narrower follow-up issue is needed from this ticket. The remaining long-tail risk described in earlier probe notes did not reproduce in the final live manual pass, so the plan is complete for scope and can move out of `/active/`.

## Context and Orientation

The affected UI lives in `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`. The selector still uses the shared geometry helpers in `/hyperopen/src/hyperopen/asset_selector/list_metrics.cljs` and the selector growth logic in `/hyperopen/src/hyperopen/asset_selector/actions.cljs`, but the remaining flicker appears in the view/runtime path that handles scrolling and body rerendering.

The committed regression coverage for this area lives in `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`, and the browser regression for `/trade` lives in `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`.

In this repository, “scroll-time churn” means any work that runs synchronously or repeatedly while the user is actively scrolling and that causes the browser to miss frames or repaint visibly stale content. In this bug, that churn can come from global store writes, selector rerenders, deferred props, or timer loops.

## Plan of Work

First, keep a running research log of the asset selector experiments already attempted, what browser evidence each one addressed, and what symptom still remained afterward. This log belongs in this ExecPlan and must be updated after every experiment so the next pass starts from measured impact rather than memory.

Next, use browser evidence to separate selector-local rendering work from app-wide state churn. If the current trace still shows store writes or bootstrap flushes during scroll, the likely fix is to move more of the scroll handling into the local selector runtime and make any global persistence coarse or settled.

Then, implement the smallest behavior change that removes the remaining visible flash and scroll freeze. The preferred direction is to keep the visible window locally driven by the scroll container, avoid rerendering the body when the current window already covers the viewport, and defer non-essential state sync until scrolling settles.

Finally, tighten the deterministic tests and browser regression so the same failure mode cannot quietly return. The tests should prove both of these user-visible claims: rapid scroll never drops the selector into a blank or black frame, and the selector remains responsive enough that the scroll direction continues to track the user’s input.

## Concrete Steps

From `/Users/barry/.codex/worktrees/2790/hyperopen`:

1. Inspect the latest browser trace or governed browser artifact after each code change and record the result in the research log.
2. Edit the selector runtime and the smallest supporting tests only after the trace points to a specific hot path.
3. Run the smallest relevant Playwright regression first, then the required repo gates.
4. Update this ExecPlan with the exact artifact paths, changed files, and the observed effect of the latest experiment.

Expected command sequence during implementation:

    npx playwright test tools/playwright/test/trade-regressions.spec.mjs --grep "asset selector"
    npm run check
    npm test
    npm run test:websocket

## Validation and Acceptance

Acceptance means the following can be observed in a live browser on `/trade`:

- Rapid scrolling through the asset selector does not show a black or blank flash between visible rows.
- Rapid scrolling does not freeze the component long enough to feel like it has stopped moving in the requested direction.
- The selector still reaches the full filtered market set.
- The committed regression and repo gates pass.

The browser evidence should be treated as part of the acceptance bar, not as a nice-to-have. If a change improves the trace but the user can still see a flicker, the issue remains open.

## Idempotence and Recovery

This work should be safe to repeat because it is source-only and measurement-driven. If an experiment fails to help, keep the code that produced the measurement, add the result to the research log, and pivot to a different hypothesis instead of re-running the same idea.

If a proposed change regresses selector reachability or introduces new warnings, back it out in the smallest possible patch and keep the trace notes so the next attempt has a clean baseline.

## Artifacts and Notes

Current evidence and supporting artifacts:

- `/Users/barry/Downloads/Trace-20260323T125824.json`
- `/Users/barry/Downloads/Trace-20260323T123451.json`
- `/Users/barry/Downloads/Trace-20260323T122522.json`
- `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/design-review-2026-03-23T17-12-21-175Z-3d4b59ce`

Research log format to keep inside this plan:

- Experiment: what was changed.
  Evidence addressed: which trace, browser run, or console warning it was supposed to fix.
  Result: improved, unchanged, or regressed.
  Next hypothesis: what to try next if the symptom remains.

Research log:

- Experiment: keep a minimal `asset-list-scroll-active?` signal in `/hyperopen/src/hyperopen/views/asset_selector_view.cljs` and use it in `/hyperopen/src/hyperopen/views/trade_view.cljs` to hold the last settled desktop orderbook and order-form snapshots while the selector is actively scrolling.
  Evidence addressed: the latest local traces and `HYPEROPEN_DEBUG.events()` output showed repeated `app-render-flush` work with changed root keys including `active-assets`, `asset-selector`, and `orderbooks` during the bad scroll bursts, even after the selector body itself stopped blanking.
  Result: improved. The probe at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-scroll-probe-2026-03-23T18-08-06.304Z/probe.json` reduced max latency from `435.2ms` to `362.8ms`, improved median from `264.2ms` to `236.2ms`, and reduced `>200ms` samples from `10` to `8`, while render-cache coverage proved those panels stop rerendering during active selector scroll.
  Next hypothesis: the desktop chart/account siblings and/or desktop active-asset panel are still participating in the remaining long tasks and need the same temporary freeze boundary.

- Experiment: freeze the desktop chart/account siblings and the desktop active-asset panel while the selector is actively scrolling.
  Evidence addressed: the orderbook/order-form-only follow-up still showed a selector-scroll mean above `230ms`, a median above `236ms`, and too many `>200ms` samples in the headless rapid-scroll probe.
  Result: improved. The follow-up probe at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-scroll-probe-2026-03-23T18-10-54.962Z/probe.json` brought the mean down to `198.9ms`, the median down to `204.6ms`, the max long task down to `273ms`, and long-task count down to `17`, with no blanking and stable visible rows.
  Next hypothesis: if a live browser still shows noticeable lag, the next unexplored path is no longer list virtualization or panel rerenders. It is temporarily quiescing chart runtime work itself or any other non-render main-thread work that continues even when the desktop panel rerenders are frozen.

- Experiment: freeze the selector wrapper props while the local selector runtime is actively scrolling and memoize the wrapper output for equal props.
  Evidence addressed: the first post-freeze CPU profile still sampled `hyperopen.views.asset_selector_view` in the parent thread, and the wheel-input probe still showed worst-case wheel-to-scroll delay around `117ms`, stall windows around `168ms`, and long-task total around `377ms`.
  Result: improved. The next wheel-input probe in the parent thread reduced worst-case wheel-to-scroll delay to roughly `92ms`, reduced max stall windows to roughly `142ms`, and cut long-task total to roughly `173ms` while keeping visible rows stable at `11`.
  Next hypothesis: the remaining selector-specific hot samples are not row-window blanking or parent rerender fanout anymore. They come from background selector bookkeeping, especially cache-persistence work triggered by live selector market patches.

- Experiment: gate selector market-cache persistence to actual selector refreshes, not live websocket patches.
  Evidence addressed: the post-freeze CPU profile at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-cpuprofile-2026-03-23T18-09-55-020Z/profile.cpuprofile` still showed `hyperopen.asset_selector.markets_cache/sort_token`, `compare_selector_markets`, and `normalize_asset_selector_market_cache_entry` samples during the scroll burst.
  Result: improved, but not yet fully decisive. The immediate single wheel probe after the watcher fix reduced long-task total to roughly `53ms`, max long task to `53ms`, and max stall window to roughly `125ms`, but repeated wheel trials still showed noisy long-tail outliers. That keeps the issue open even though the clear wasted cache-persistence work is now gone.
  Next hypothesis: if the remaining tail is still visible in manual browser use, the next experiment should target chart-runtime or websocket-health work that still runs on the main thread even after selector view churn and cache persistence have been reduced.

- Experiment: replace the desktop selector’s normal-sized virtual row window with a fully rendered row list, while keeping the local scroll runtime and a large-list virtualization fallback.
  Evidence addressed: the new stronger coverage probe from the parent thread showed the remaining failure mode directly: after deep jump-scrolls to `900px`, `1800px`, `3600px`, and beyond, the current implementation exposed `256px` of blank viewport immediately and only recovered on the next animation frame, which matches the user-visible black flash.
  Result: improved. The post-fix live browser inspection summary at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-scroll-coverage-2026-03-23T18-49-09Z/probe-summary.json` recorded `658` rendered rows and `0px` blank coverage across all sampled deep scroll targets, and the tightened Playwright regression now passes while asserting blank coverage directly.
  Next hypothesis: if manual hardware scroll still finds noticeable lag after this pass, the next remaining path is likely long-tail non-selector main-thread work rather than the selector viewport outrunning its own row window.

- Experiment: remove eager `scrollend` finalization, keep paused selector subscriptions paused across scroll-top persistence, and resume them only after a second post-settle idle timer.
  Evidence addressed: the local reversal probe and the first dedicated bottom-up probe at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-bottom-up-probe-2026-03-23T19-11-38-605Z/probe-summary.json` showed the worst remaining symptom at the bottom edge when the user quickly reversed upward and the selector appeared to “unfreeze” late.
  Result: partially improved. The first upward response tightened materially, but the probe still showed noisy long-tail stalls because the idle window still only followed `scroll` events, not boundary wheel input.
  Next hypothesis: the settle window must treat wheel input at the pinned bottom edge as scroll activity too, otherwise resume can still fire while the user is actively trying to reverse direction.

- Experiment: treat selector `wheel` input as active scroll activity even when `scrollTop` is pinned, and freeze the current selector active-context owner set while paused instead of diffing to `#{}`.
  Evidence addressed: the local down-to-bottom-then-up telemetry run still showed selector active-context churn in the reversal path, and the first post-delay probe still showed bad-tail frame gaps inconsistent with the old blank-window bug.
  Result: improved. The follow-up bottom-up probe at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-bottom-up-probe-2026-03-23T19-19-31-801Z/probe-summary.json` reduced worst first upward movement to `21.4-33.9ms`, reduced max frame gaps to `75-110.3ms`, and cut long-task totals to `890-1112ms`, down from the previous `1820-8051ms` run at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-bottom-up-probe-2026-03-23T19-11-38-605Z/probe-summary.json`. The committed browser regression still passed, and governed browser QA at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/design-review-2026-03-23T19-21-14-480Z-8830ed25` passed visual, native-control, interaction, layout-regression, and jank-perf with only the known styling-consistency tooling gap remaining blocked.
  Next hypothesis: if the user can still feel long-tail stalls on real hardware after this pass, the remaining work is likely outside the selector boundary logic and would justify a narrower follow-up into chart/runtime or websocket main-thread work.

- Experiment: split selector “scroll active” from selector “freeze heavy siblings” and make the desktop trade-view freeze snapshots lazy.
  Evidence addressed: the remaining bottom-edge path still had one route-churn hole because heavy desktop panel state builders were being recomputed even while the freeze boundary reused their last rendered snapshot, and extending a single global signal through the cooldown risked regressing selector responsiveness.
  Result: improved. The focused render-cache regression now proves the heavy state builders do not run while selector freeze is active, the selector runtime tests prove scroll-active drops immediately while freeze-active remains high through the resume cooldown, the fresh bottom-up probe at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/asset-selector-bottom-up-probe-2026-03-23T19-33-12-356Z/probe-summary.json` showed `0ms` reversal-window long-task time with working trials moving upward in roughly `55.6-56.7ms`, and governed browser QA at `/Users/barry/.codex/worktrees/2790/hyperopen/tmp/browser-inspection/design-review-2026-03-23T19-33-43-404Z-66eb2fc3` again passed visual, native-control, interaction, layout-regression, and jank-perf with only the known styling-consistency tooling gap blocked.
  Next hypothesis: if the user can still feel bottom-edge jank on real hardware after this pass, the remaining tail is likely outside the selector route-freeze boundary and should be investigated as browser or chart/runtime work rather than more selector list changes.

## Interfaces and Dependencies

No new external dependency is expected. The relevant internal seams are:

- `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`
- `/hyperopen/src/hyperopen/asset_selector/list_metrics.cljs`
- `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
- `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`
- `/hyperopen/tools/playwright/test/trade-regressions.spec.mjs`

The experiment loop should preserve public APIs unless browser evidence proves a contract change is required. Any follow-up that broadens scope should be called out in this plan before implementation.

Plan revision note: 2026-03-23 17:45Z - Initial active ExecPlan created for `hyperopen-2614` after confirming the remaining asset-selector issue is a scroll-time flicker/jank regression and not the earlier blank-window bug.

Plan revision note: 2026-03-25 15:59 EDT - Closeout manual desktop verification passed: the user rapidly scrolled the `/trade` asset selector all the way down and all the way back up in a live browser session, observed no jank and no flickering, and confirmed no further chart/runtime follow-up was needed. This satisfies the last unchecked acceptance item and closes the plan for scope.
