# Optimizer Frontier 1280 Layout Fix

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document follows the repository planning contract in `.agents/PLANS.md`.

## Purpose / Big Picture

The optimizer result page should keep the efficient frontier readable at common desktop widths. During exploratory QA, the frontier explanatory row collapsed into one-word-per-line text at 1280px because the result grid kept allocation, chart, and diagnostics columns side by side while the frontier toolbar also reserved a fixed-width control group. After this change, a user reviewing optimizer output at 1280px can read the frontier chart and helper copy without the chart being squeezed by the right diagnostics rail.

The working behavior is visible by running the portfolio optimizer Playwright regression at 1280px and observing that the frontier panel gets a real center column, the `Reading this` label remains one line, and the helper sentence has enough width to scan normally.

## Context References

Public refs:
- Direct user request in this Codex thread on 2026-06-01: create an execution plan and implement the proposed cramped frontier layout fix.

Repo artifacts:
- `docs/PLANS.md` and `.agents/PLANS.md` define active ExecPlan requirements.
- `docs/BROWSER_TESTING.md` defines Playwright versus Browser MCP routing.
- `docs/agent-guides/browser-qa.md` defines UI QA reporting expectations.

Local scratch refs (non-authoritative):
- `tmp/browser-inspection/optimizer-explore-2026-06-01T15-34-08-397Z/manual-four-assets-after-run.png` shows the cramped 1280px frontier result.
- `tmp/browser-inspection/optimizer-explore-2026-06-01T15-34-08-397Z/use-my-views-after-run-click.png` shows the same cramped helper text in a Use my views result.

## Progress

- [x] (2026-06-01 15:45Z) Confirmed relevant files: `src/hyperopen/views/portfolio/optimize/results_panel.cljs`, `src/hyperopen/views/portfolio/optimize/frontier_chart.cljs`, `src/hyperopen/views/portfolio/optimize/frontier_chart_toolbar.cljs`, and `tools/playwright/test/portfolio-regressions.spec.mjs`.
- [x] (2026-06-01 15:45Z) Decided to write a focused Playwright regression first, before production code, that exercises the existing optimizer recommendation chart at 1280px.
- [x] (2026-06-01 15:49Z) Added the failing Playwright assertion for 1280px frontier layout.
- [x] (2026-06-01 15:50Z) Verified the new assertion fails before implementation: center panel width was 0.359375 of the result grid, below the 0.44 floor.
- [x] (2026-06-01 15:52Z) Implemented the smallest layout change to keep the frontier readable at 1280px.
- [x] (2026-06-01 15:57Z) Re-ran the focused Playwright command after implementation; the optimizer frontier regression passed at 1280px.
- [x] (2026-06-01 16:58Z) Ran governed design review for `portfolio-optimizer-results-route`; Browser QA failed before route capture at all four required widths with `Timed out waiting for Network.enable`.
- [x] (2026-06-01 17:00Z) Extended the focused Playwright regression to also assert that the right diagnostics rail returns to the right column at 1536px.
- [x] (2026-06-01 17:01Z) Re-ran the focused Playwright command with the 1280px and 1536px assertions; it passed.
- [x] (2026-06-01 17:04Z) Ran `npm test`; it passed with 4109 tests and 22667 assertions.
- [x] (2026-06-01 17:05Z) Ran `npm run test:websocket`; it passed with 527 tests and 3067 assertions.
- [x] (2026-06-01 17:06Z) Ran `npm run check`; it failed at `lint:docs` on an unrelated active ExecPlan with no unchecked progress items.
- [x] (2026-06-01 17:07Z) Ran the Shadow compile portion directly for `app`, `portfolio`, `portfolio-worker`, `portfolio-optimizer-worker`, `vault-detail-worker`, and `test`; all builds completed with 0 warnings.
- [x] (2026-06-01 17:08Z) Moved this plan to `/hyperopen/docs/exec-plans/completed/`.
- [x] (2026-06-01 17:15Z) Ran focused manual visual QA through a real Playwright browser against the solved optimizer flow at 375, 768, 1280, 1440, and 1536px.

## Surprises & Discoveries

- Observation: The 1280px cramped helper is not caused by SVG rendering. It comes from the HTML layout around the chart.
  Evidence: `src/hyperopen/views/portfolio/optimize/results_panel.cljs` uses `xl:grid-cols-[500px_minmax(0,1fr)_320px]`, so 1280px viewports still force three result columns.

- Observation: The frontier toolbar has a fixed minimum control group that competes with the chart title and helper text.
  Evidence: `src/hyperopen/views/portfolio/optimize/frontier_chart_toolbar.cljs` uses `lg:grid-cols-[minmax(0,1fr)_auto]` and the overlay mode group uses `min-w-[19.25rem]`.

- Observation: The new Playwright assertion fails on the current layout for the intended reason.
  Evidence: `npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart shows minimum variance frontier overlays and honest target weights" --workers=1` failed with `Expected: >= 0.44` and `Received: 0.359375` for `centerPanelBox.width / resultsGridBox.width`.

- Observation: The governed Browser QA runner is not currently usable for this route in this session.
  Evidence: `npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app` first timed out waiting for the default Chrome debug endpoint. Retrying with Playwright's bundled Chrome for Testing reached the runner, but failed capture at `review-375`, `review-768`, `review-1280`, and `review-1440` with `Timed out waiting for Network.enable`.

- Observation: The repository-wide `npm run check` gate is currently blocked before the compile phase by an unrelated active ExecPlan hygiene issue.
  Evidence: `npm run check` failed at `lint:docs` with `[active-exec-plan-no-unchecked-progress] docs/exec-plans/active/2026-06-01-optimizer-history-window-limiter-diagnostics.md - active ExecPlan has no remaining unchecked progress items; move it out of active`.

## Decision Log

- Decision: Use a responsive two-column result layout at `xl` and reserve the three-column allocation/frontier/diagnostics layout for `2xl`.
  Rationale: At 1280px, the frontier is the primary result and should not be starved by the right diagnostics rail. Keeping diagnostics below the first row until `2xl` preserves content without hiding it.
  Date/Author: 2026-06-01 / Codex

- Decision: Keep the `Reading this` label on one line and give the explanatory sentence its own flexible text region.
  Rationale: The label is short metadata and should not wrap; only the descriptive copy should wrap.
  Date/Author: 2026-06-01 / Codex

- Decision: Add coverage to the existing optimizer recommendation chart regression rather than creating a separate full optimizer scenario.
  Rationale: The existing test already builds a deterministic solved optimizer result with frontier overlays. Adding 1280px layout assertions there avoids duplicating a long setup.
  Date/Author: 2026-06-01 / Codex

## Outcomes & Retrospective

The cramped 1280px optimizer frontier layout is fixed by keeping the results grid to two columns at `xl` and reserving the three-column allocation/frontier/diagnostics layout for `2xl`. The frontier reading row now keeps the `Reading this` label on one line while letting only the explanatory sentence wrap.

The deterministic browser regression now covers both the original failure width and the desktop re-expansion breakpoint: at 1280px the center panel must be at least 44% of the results grid and the `Reading this` label must remain one line; at 1536px the diagnostics rail must sit to the right of the frontier panel. Unit tests and websocket tests pass. The full `npm run check` gate is blocked by a pre-existing unrelated active ExecPlan docs-lint issue, so the Shadow compile portion was run directly and passed with 0 warnings.

The governed Browser QA six-pass review could not produce route artifacts in this session. The default Chrome run failed waiting for a debug endpoint, and the Chrome-for-Testing retry failed before route capture at every required review width with `Timed out waiting for Network.enable`. Browser-inspection sessions were cleaned up with `npm run browser:cleanup`; `pgrep` confirmed no design-review, Shadow watch, or Tailwind watch processes remained.

A focused manual visual QA pass was added after the completed plan was first written. It drove the solved optimizer flow in a real Playwright browser, captured the results surface at 375, 768, 1280, 1440, and 1536px, and inspected the affected chart/helper/diagnostics layout. The changed surface checked out: no horizontal overflow at any inspected width, the `Reading this` label remained one line, 1280/1440 used the wider two-column layout with diagnostics below, and 1536 restored the diagnostics rail to the right column. This focused pass does not replace the blocked governed six-pass design review.

## Context and Orientation

The optimizer result page is rendered by `src/hyperopen/views/portfolio/optimize/results_panel.cljs`. The main result area is a CSS grid with a left allocation table, center frontier chart, and right diagnostics rail. The efficient frontier component lives in `src/hyperopen/views/portfolio/optimize/frontier_chart.cljs`; it renders a toolbar, SVG chart, and explanatory `Reading this` row. The toolbar itself lives in `src/hyperopen/views/portfolio/optimize/frontier_chart_toolbar.cljs`.

The existing deterministic browser coverage is in `tools/playwright/test/portfolio-regressions.spec.mjs`. The test named `portfolio optimizer recommendation chart shows minimum variance frontier overlays and honest target weights @regression` seeds mocked BTC/ETH/SOL/HYPE history, runs the optimizer, and asserts frontier controls, axes, target marker, and overlays. It is the right place to add a 1280px layout regression because it already reaches the affected result state without live backend dependencies.

In this plan, `xl` and `2xl` refer to Tailwind breakpoint prefixes already used in the codebase. `xl` starts at 1280px, which is exactly the failing width. `2xl` starts at 1536px, where three result columns have enough space.

## Plan of Work

First, update the existing Playwright test so it explicitly uses a 1280 by 900 viewport and asserts that the frontier helper label stays one line and the center panel is wide enough relative to the result grid. Run that focused test and confirm it fails on the current layout.

Second, update `src/hyperopen/views/portfolio/optimize/results_panel.cljs` so the result grid is one column by default, two columns at `xl`, and three columns at `2xl`. The right panel should span both `xl` columns and return to a single right column at `2xl`.

Third, update `src/hyperopen/views/portfolio/optimize/frontier_chart.cljs` so the `Reading this` row is a small grid or flex row where the label has `whitespace-nowrap` and the copy has `min-w-0`. This keeps utility copy readable even when the chart panel is moderately narrow.

Fourth, update `src/hyperopen/views/portfolio/optimize/frontier_chart_toolbar.cljs` only if the test still shows the toolbar crowding the chart. The likely minimal fallback is to stack toolbar title and controls until `2xl`, but the primary fix should be the result grid.

Finally, run the focused Playwright test. If it passes, run the smallest relevant lint/test checks and then the repository-required validation gates if time allows: `npm run check`, `npm test`, and `npm run test:websocket`.

## Concrete Steps

From `/Users/barry/projects/hyperopen`, run the focused Playwright regression after adding the failing assertion:

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:8080 npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart shows minimum variance frontier overlays and honest target weights" --workers=1

Expected before implementation: the test fails because the frontier center panel is too narrow or the `Reading this` label wraps at 1280px.

After implementation, run the same command again. Expected after implementation: the test passes and the frontier helper label height is one line.

If the dev server binds to a different port, let Playwright manage the app through `playwright.config.mjs` by omitting `PLAYWRIGHT_BASE_URL`, or set `PLAYWRIGHT_REUSE_EXISTING_SERVER=true` only when a compatible `npm run dev:browser-inspection` server is already running.

## Validation and Acceptance

Acceptance criteria:

1. At 1280px wide, the optimizer result page no longer renders the frontier helper label as multiple stacked words.
2. At 1280px wide, the frontier center panel has enough width for the chart and toolbar controls to remain readable.
3. At wider desktop widths, the diagnostics rail remains visible as the right column.
4. The existing optimizer frontier regression still passes, including target marker, axis labels, overlay mode buttons, and frontier path assertions.
5. The plan records the exact commands run and their result.

Required repository gates for code changes are `npm run check`, `npm test`, and `npm run test:websocket`. If they cannot be run in this session, record the blocker and run the focused Playwright proof.

## Idempotence and Recovery

The changes are limited to component class strings and a Playwright assertion. Re-running the Playwright command is safe. If a Playwright web server remains running after a failed run, stop it with `npm run browser:cleanup` for browser-inspection sessions and interrupt any local dev server process started by this plan. If the layout change breaks wider desktop rendering, revert only the latest edits in `results_panel.cljs`, `frontier_chart.cljs`, and the added test assertion, then rerun the focused test to confirm the previous behavior is restored.

## Artifacts and Notes

Important pre-change artifact:

    tmp/browser-inspection/optimizer-explore-2026-06-01T15-34-08-397Z/manual-four-assets-after-run.png

Focused pre-change Playwright proof:

    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart shows minimum variance frontier overlays and honest target weights" --workers=1

Result before implementation: failed as expected with `Expected: >= 0.44` and `Received: 0.359375` for `centerPanelBox.width / resultsGridBox.width`.

Focused post-change Playwright proof:

    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart shows minimum variance frontier overlays and honest target weights" --workers=1

Result after initial implementation: passed, `1 passed (52.8s)`.

Governed Browser QA attempt:

    npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app

Result: failed before route capture. Artifact: `tmp/browser-inspection/design-review-2026-06-01T16-52-17-795Z-da32f47a/manifest.json`.

Governed Browser QA retry with Playwright's bundled Chrome:

    BROWSER_INSPECTION_CHROME_PATH="/Users/barry/Library/Caches/ms-playwright/chromium-1208/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing" npm run qa:design-ui -- --targets portfolio-optimizer-results-route --manage-local-app

Result: failed before route capture at all required widths with `Timed out waiting for Network.enable`. Artifact: `tmp/browser-inspection/design-review-2026-06-01T16-56-04-151Z-82d26939/summary.json`.

Focused post-change Playwright proof after adding the 1536px right-rail assertion:

    npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g "portfolio optimizer recommendation chart shows minimum variance frontier overlays and honest target weights" --workers=1

Result: passed, `1 passed (28.6s)`.

Repository test gate:

    npm test

Result: passed, `4109 tests`, `22667 assertions`, `0 failures`, `0 errors`.

Repository websocket gate:

    npm run test:websocket

Result: passed, `527 tests`, `3067 assertions`, `0 failures`, `0 errors`.

Repository check gate:

    npm run check

Result: failed at `lint:docs` on unrelated active plan `docs/exec-plans/active/2026-06-01-optimizer-history-window-limiter-diagnostics.md` with `active-exec-plan-no-unchecked-progress`.

Direct compile fallback for the part of `npm run check` not reached after `lint:docs`:

    npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker vault-detail-worker test

Result: passed. `app`, `portfolio`, `portfolio-worker`, `portfolio-optimizer-worker`, `vault-detail-worker`, and `test` builds all completed with `0 warnings`.

Browser-inspection cleanup:

    npm run browser:cleanup

Result after Browser QA attempts: passed, `stopped: []`. A subsequent process scan found no remaining design-review, Shadow watch, or Tailwind watch processes.

Focused manual visual QA:

    npm run dev:browser-inspection
    node --input-type=module - <<'NODE'
      ...
    NODE

Result: captured and inspected a solved optimizer result at `375`, `768`, `1280`, `1440`, and `1536` widths. Evidence bundle: `tmp/browser-inspection/manual-frontier-layout-qa-2026-06-01-scrolltop/`. `manual-qa-results.json` records no horizontal overflow at all inspected widths; the `Reading this` label measured 16px high; 1280px center ratio was `0.671875`; 1440px center ratio was `0.7083333333333334`; and 1536px placed the right diagnostics rail to the right of the frontier panel.

Plan revision note: 2026-06-01 17:08Z - Implementation and focused deterministic validation are complete; this plan moved from active to completed. The only unfinished repository gate is blocked by an unrelated active ExecPlan hygiene issue outside this plan's scope.

Plan revision note: 2026-06-01 17:15Z - Added focused manual visual QA evidence after the user asked whether manual QA had been performed.

## Interfaces and Dependencies

No new runtime dependencies are required. The implementation uses existing Tailwind utility classes embedded in Replicant/Hiccup vectors. The Playwright test uses existing helpers from `tools/playwright/support/hyperopen.mjs` and the existing deterministic optimizer test fixture in `tools/playwright/test/portfolio-regressions.spec.mjs`.
