# Reduce `draw-spaghetti!` CRAP In The Monte Carlo Chart

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The repo-native CRAP report identifies `/hyperopen/src/hyperopen/views/portfolio/montecarlo/chart.cljs` as the largest current CRAP-load module. The worst function is `hyperopen.views.portfolio.montecarlo.chart/draw-spaghetti!` at line 60, with CRAP `676.97`, complexity `26`, and coverage `0.0125`.

After this change, `draw-spaghetti!` should remain a canvas rendering entrypoint with the same user-visible chart behavior, but its branch-heavy data preparation and drawing decisions should be split into small, testable helpers. A contributor should be able to validate chart scale geometry, reveal-window selection, axis ticks, threshold visibility, and sampled-path limits without exercising a browser canvas.

The requested scope is direct user request on 2026-06-06: "create a plan and address draw-spaghetti! (line 60)".

## Acceptance Criteria

- Add focused regression coverage for the extracted spaghetti-chart helper seams before changing production chart code.
- Preserve the public `draw-spaghetti!`, `spaghetti-on-render`, and Replicant lifecycle API.
- Keep the chart canvas behavior equivalent for confidence bands, sampled paths, median/p5/p95 paths, start/goal/bust markers, endpoint dots, and returned hover geometry.
- Reduce `draw-spaghetti!` below the default CRAP threshold of `30` in a module-scoped CRAP report.
- Run focused tests, fresh coverage, module CRAP, and the required repo gates:

      npm test
      npm run test:websocket
      npm run check

- Because this touches `/hyperopen/src/hyperopen/views/**`, account for browser QA passes from `/hyperopen/docs/FRONTEND.md` and `/hyperopen/docs/agent-guides/browser-qa.md`.

## Progress

- [x] (2026-06-06) Reviewed `/hyperopen/AGENTS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/browser-qa.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-06-06) Confirmed the current CRAP baseline from the prior fresh run: `draw-spaghetti!` CRAP `676.97`, coverage `0.0125`, complexity `26`; module `chart.cljs` crapload `1148.99`.
- [x] (2026-06-06) Created this active ExecPlan.
- [x] (2026-06-06) Added RED tests for extracted spaghetti chart helpers. The first compile failed because `hyperopen.views.portfolio.montecarlo.chart.model/spaghetti-model` did not exist yet.
- [x] (2026-06-06) Refactored `draw-spaghetti!` into covered helper seams while preserving API and visuals.
- [x] (2026-06-06) Split pure spaghetti chart derivation into `hyperopen.views.portfolio.montecarlo.chart.model` after the namespace-size lint caught the initial single-file refactor.
- [x] (2026-06-06) Ran focused tests, fresh coverage, and module CRAP reports against `chart.cljs` and `chart/model.cljs`.
- [x] (2026-06-06) Ran required repo gates and browser-QA/design-review accounting.
- [x] (2026-06-06) Moved this ExecPlan to completed with final evidence.

## Implementation Plan

First, add `test/hyperopen/views/portfolio/montecarlo/chart_test.cljs` with focused tests that target intended helper APIs. The tests should cover the derived chart model rather than canvas pixels: scale domain includes start, bust, goal, p5, and p95 with padding; reveal progress clamps to a valid grid window; threshold visibility distinguishes in-domain and out-of-domain goal/bust lines; sampled-path count honors `path-count` and available paths; returned hover geometry preserves the fields the tooltip needs.

Second, update `src/hyperopen/views/portfolio/montecarlo/chart.cljs` by extracting pure helpers from `draw-spaghetti!`. Expected helpers include chart palette/spec normalization, geometry/domain calculation, reveal-window calculation, axis-grid row derivation, confidence-band point derivation, sampled-path point derivation, and threshold/endpoint derivation. Keep canvas mutation in small imperative drawing helpers so the public renderer is mostly orchestration.

Third, run the focused test namespace and update this plan with the RED and GREEN evidence. Then run `npm run coverage` and the module-scoped CRAP report:

    npm run crap:report -- --module src/hyperopen/views/portfolio/montecarlo/chart.cljs --format json

Finally, run the required gates. Since this is a behavior-preserving canvas refactor, the browser-QA expectation is to run the smallest deterministic browser route check that renders portfolio Monte Carlo if available; if the existing design-review tooling cannot target this route/state deterministically, mark the browser-QA passes `BLOCKED` with the exact missing route/state support and rely on focused chart tests plus compile/CRAP evidence for this slice.

## Surprises & Discoveries

- `npm run check` includes namespace-size linting. The first green functional refactor still left `chart.cljs` at 581 lines, which failed the lint threshold. Extracting the pure model namespace reduced `chart.cljs` to 408 lines and kept the model at 179 lines.
- The focused model test materially covers the extracted pure namespace: post-coverage `chart/model.cljs` reports zero crappy functions and `spaghetti-model` reports CRAP `2.25`, complexity `2`, coverage `0.6053`.
- The endpoint-dot extraction initially changed a subtle animation edge: at reveal progress `0.999`, the original renderer draws endpoint dots at the final frame, not the current reveal index. A RED regression caught x `379` instead of the final x `484`, and the model now preserves the final-frame endpoint behavior.

## Decision Log

- Decision: Keep this pass focused on `draw-spaghetti!`; do not bundle `render-hook`, `draw-histogram!`, or tooltip remediation even though they are nearby hotspots.
  Rationale: The user named line 60 specifically, and the top function alone is large enough for one verifiable slice.
  Date/Author: 2026-06-06 / Codex

- Decision: Test the derived chart model and drawing inputs rather than exact canvas pixels.
  Rationale: The CRAP score is dominated by branch-heavy geometry and draw-decision logic. Pure model tests are deterministic, fast, and reduce risk without tying tests to antialiasing or browser raster output.
  Date/Author: 2026-06-06 / Codex

- Decision: Move reusable palette, axis labels, geometry, domain, reveal-window, path, band, threshold, endpoint, and hover-geometry derivation into `hyperopen.views.portfolio.montecarlo.chart.model`.
  Rationale: This preserves the public canvas renderer API while making the CRAP-heavy decisions testable without browser canvas pixels.
  Date/Author: 2026-06-06 / Codex

## Outcomes & Retrospective

`draw-spaghetti!` is no longer a CRAP hotspot. The function now orchestrates setup, pure model derivation, drawing helpers, and hover-geometry return. The branch-heavy chart derivation lives in `chart/model.cljs` and is covered by deterministic CLJS tests.

Post-change module CRAP evidence:

- `src/hyperopen/views/portfolio/montecarlo/chart.cljs`: `draw-spaghetti!` CRAP `5.08`, complexity `2`, coverage `0.0833`, not crappy. Remaining out-of-scope hotspots in this module are `render-hook`, `draw-histogram!`, and `show-spaghetti-tip!`.
- `src/hyperopen/views/portfolio/montecarlo/chart/model.cljs`: zero crappy functions; module crapload `0.0`.

## Validation Evidence

- RED: `npx shadow-cljs --force-spawn compile test` initially failed because `spaghetti-model` was missing from the new model namespace.
- GREEN focused compile: `npx shadow-cljs --force-spawn compile test` passed after implementation.
- RED: the new endpoint-dot regression failed before the fix with expected x positions `[484 484 484]` and actual `[379 379 379]`.
- `npm test`: passed, `4224` tests, `23442` assertions, `0` failures, `0` errors.
- `npm run test:websocket`: passed, `531` tests, `3080` assertions, `0` failures, `0` errors.
- `npm run check`: passed after the model namespace split.
- Final `npm run check`: passed again after moving this ExecPlan to `completed/`; app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test Shadow builds all completed with `0` warnings.
- `npm run coverage`: passed, `4224` main tests and `531` websocket tests, all `0` failures/errors; merged coverage `90.38%` statements/lines, `70.15%` branches, `83.01%` functions.
- `npm run crap:report -- --module src/hyperopen/views/portfolio/montecarlo/chart.cljs --top-functions 25 --top-modules 5 --format json`: passed; `draw-spaghetti!` CRAP `5.08`.
- `npm run crap:report -- --module src/hyperopen/views/portfolio/montecarlo/chart/model.cljs --top-functions 25 --top-modules 5 --format json`: passed; zero crappy functions.
- `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio Monte Carlo forecast horizon can return to one year after six months"`: passed, `1` test.
- `npm run qa:design-ui -- --targets portfolio-route --manage-local-app`: passed for `portfolio-route` across `review-375`, `review-768`, `review-1280`, and `review-1440`; all governed passes reported `PASS` with `0` issues. The final run id was `design-review-2026-06-06T12-39-06-327Z-a72b8051`. The tool reported residual sampled-state blind spots for hover/active/disabled/loading states at each viewport.
- `npm run browser:cleanup`: passed, stopped no lingering tracked sessions.
