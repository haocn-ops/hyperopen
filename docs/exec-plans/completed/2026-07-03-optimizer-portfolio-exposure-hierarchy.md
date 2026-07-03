# Optimizer New Page: Portfolio Exposure Hierarchy

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`. It is self-contained so another worker can continue from this file alone.

## Purpose / Big Picture

The optimizer setup page at `/portfolio/optimize/new` lets a trader choose the universe, objective, return/risk model, and portfolio policy before running a rebalance. The exposure policy is one of the highest-impact controls because it defines gross leverage, net long/short bias, concentration, turnover, and rebalance tolerance. Today those controls live inside a disclosure whose visible title is `Constraints`. That title describes the solver implementation, not the trader's decision, and the disclosure starts closed, so a user can reach `Ready to run` without seeing leverage and directional exposure.

After this change, the same underlying controls remain canonical and exact, but the page names the section `Portfolio exposure`, opens it by default, explains in one sentence that it sets leverage and net long/short exposure, and keeps a readable exact summary in the header. The 2D exposure map gains a plain caption and the current-portfolio preview becomes clearer when the current book is outside the chosen policy. A user should immediately understand that this is where they control how aggressive and directional the rebalance can be.

## Context References

Public refs:

- Direct maintainer request on 2026-07-03: consider an expert designer's feedback about the optimizer new page, create an execution plan, and implement it. The maintainer explicitly delegated final design judgment to the implementer.

Repo artifacts:

- `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` renders the current constraints disclosure, grouped controls, and advanced raw solver drawer.
- `src/hyperopen/views/portfolio/optimize/setup_exposure_map.cljs` renders the 2D exposure map and current exposure preview.
- `src/hyperopen/portfolio/optimizer/application/view_model/setup.cljs` formats the exact constraints summary line shown in the setup header and scenario contract.
- `test/hyperopen/views/portfolio/optimize/setup_layout_test.cljs` and `test/hyperopen/views/portfolio/optimize/setup_view_test.cljs` pin the setup route's Hiccup structure and user-facing copy.
- `tools/playwright/test/optimizer-exposure-map.spec.mjs` and `tools/playwright/test/portfolio-regressions.spec.mjs` pin stable browser behavior for the exposure controls.
- UI policy docs: `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/agent-guides/browser-qa.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.

Local scratch refs:

- None. Prior memory noted earlier exposure-map work, but this plan does not require that memory to execute.

## Progress

- [x] (2026-07-03T17:44:54Z) Read the attached expert feedback, current UI docs, browser-QA contract, memory notes, and current setup/exposure implementation.
- [x] (2026-07-03T17:44:54Z) Decided the revamp should adapt the expert feedback by changing hierarchy and language while preserving the existing 2D policy surface and exact advanced solver drawer.
- [x] (2026-07-03T17:44:54Z) Authored this active ExecPlan.
- [x] (2026-07-03T17:55:00Z) Added failing unit and browser-facing tests that expected `Portfolio exposure`, default-open behavior, readable summary copy, and clearer exposure-map explanatory copy.
- [x] (2026-07-03T18:03:00Z) Implemented the focused UI, formatter, and setup-route CSS changes.
- [x] (2026-07-03T18:17:53Z) Ran focused tests, required gates, browser regression coverage, and governed design review.
- [x] (2026-07-03T18:28:00Z) Recorded validation evidence and prepared the plan for `docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: The page already has the right interaction model: a 2D exposure map, grouped risk/rebalance controls, persistent exact solver summary, and an advanced raw min/max drawer. The current issue is not missing functionality; it is hierarchy and terminology.
  Evidence: `setup_constraint_controls.cljs` already renders `Positioning`, `Risk guards`, `Rebalance behavior`, `Advanced solver constraints`, and `setup_exposure_map.cljs` renders the 2D pad plus `Sent to solver`.

- Observation: Existing tests still assert that the constraints panel is closed by default and that its title is `Constraints`.
  Evidence: `setup_layout_test.cljs` has `setup-return-risk-and-constraints-panels-are-collapsed-disclosures-test`, and Playwright specs manually open the panel before checking the exposure map.

- Observation: Opening the exposure panel by default surfaced two existing browser-QA issues: the exposure band range inputs had no visible keyboard focus indicator, and the 375px reviewed viewport had route-surface internal overflow.
  Evidence: `npm run qa:design-ui -- --targets portfolio-optimizer-route --viewports review-375,review-768,review-1280,review-1440 --manage-local-app` produced failing run `design-review-2026-07-03T18-06-56-776Z-26384d85` with interaction focus failures for `portfolio-optimizer-exposure-gross-band` / `portfolio-optimizer-exposure-net-band` and a 375px `horizontal-overflow` finding.

- Observation: The route root carries both `portfolio-optimizer` and `optimizer-setup-route-surface` on the same element, so descendant-only CSS selectors did not apply to the root route surface.
  Evidence: the passing CSS fix added `.portfolio-optimizer.optimizer-setup-route-surface` beside the existing descendant selector; the final design review `design-review-2026-07-03T18-17-21-567Z-82e82793` passed layout-regression at 375px.

- Observation: One broad optimizer Playwright run hit a transient persisted-tracking hydration miss unrelated to the setup-page change, but it did not reproduce.
  Evidence: the first broad run reported 21/22 with `portfolio optimizer persisted scenario hydrates results and tracking after reload` missing `Weight Drift RMS`; an isolated rerun of that case passed, and the final full optimizer grep later passed 22/22.

## Decision Log

- Decision: Rename only the setup policy section to `Portfolio exposure`; keep the internal data role `portfolio-optimizer-constraints-panel` and rename the raw drawer to `Advanced solver limits`.
  Rationale: The trader-facing section should use the user's mental model. The data role is a stable test and tooling anchor, and the raw drawer is correctly solver-facing because it is explicitly advanced.
  Date/Author: 2026-07-03 / Codex.

- Decision: Open `Portfolio exposure` by default on the new optimizer page while leaving Return / Risk Model and Advanced Overrides collapsed.
  Rationale: Exposure policy is a first-order rebalance input. Opening this one section improves discovery without expanding every secondary configuration surface.
  Date/Author: 2026-07-03 / Codex.

- Decision: Improve the summary line in the header from abbreviated solver copy such as `gross`, `net`, `cap`, and `band` to readable policy copy such as `Gross`, `Net`, `Max asset`, and `Rebalance`, with explicit `long`, `short`, or `neutral` on net exposure when possible.
  Rationale: The summary should be exact enough for expert review and readable enough to scan without decoding abbreviations.
  Date/Author: 2026-07-03 / Codex.

- Decision: Add explanatory copy around the exposure map and current portfolio preview, not a tutorial overlay or marketing copy.
  Rationale: The page is a dense trading tool. Short product copy clarifies mapping and status while respecting the existing compact workbench design.
  Date/Author: 2026-07-03 / Codex.

## Outcomes & Retrospective

Completed. The optimizer setup page now exposes the leverage/net-exposure policy as `Portfolio exposure`, opens that section by default, adds a concise explanation, keeps exact gross/net/cap/rebalance values in the header, and keeps raw solver fields available under `Advanced solver limits`.

The expert feedback was directionally right about discovery and naming. The implementation kept the existing 2D map because it already solved the higher-value interaction problem: users can set gross leverage and net long/short bias together, while exact solver-facing values remain visible. The extra work was in making that surface accessible and robust once it became default-open: range inputs now show keyboard focus, and the setup route clamps narrow desktop scroll-gutter overflow.

## Context and Orientation

Hyperopen is a ClojureScript application using Replicant Hiccup views. Hiccup is Clojure data that describes DOM nodes, for example `[:div {:class ["x"]} "copy"]`. The optimizer setup route is composed under `src/hyperopen/views/portfolio/optimize/`. The center policy pane is assembled by `setup_sections.cljs`, with objective controls, return/risk model controls, the exposure policy section, advanced overrides, notes, assumptions, and a sticky Run bar.

The current exposure section is named `constraints-section` because the canonical draft stores solver constraints under `[:portfolio :optimizer :draft :constraints]`. That implementation name should remain internal. The user-facing title should describe the decision: portfolio exposure. The section's raw control data roles should stay stable because Playwright and unit tests use them to assert that the same canonical controls are still present exactly once.

The 2D exposure map converts gross/net target plus bands into the canonical min/max draft constraints through pure domain/action code. This plan does not change solver semantics, persistence, route loading, or request building. It changes presentation, default disclosure state, summary formatting, and explanatory copy.

## Plan of Work

First, add tests before production code. Update the Hiccup-level setup tests to expect the `portfolio-optimizer-constraints-panel` details node to carry `:open true`, the visible title `Portfolio exposure`, and the one-sentence description. Add pure formatter assertions for `constraints-summary-line` so the exact summary uses readable labels and net direction text. Update or add Playwright assertions so the exposure panel is visible without a manual open step and the 2D map's caption is visible.

Second, update `setup_constraint_controls.cljs`. Use `controls/disclosure-panel-open` for the top-level constraints panel. Change the summary heading title to `Portfolio exposure`, keep the exact summary on the right, and add a short description immediately inside the open panel: `Set how levered and net long/short the target portfolio can be.` Keep the grouped controls and advanced drawer order intact.

Third, update `setup_exposure_map.cljs`. Add a brief caption under or near the map that says the dot shows the target gross leverage and net long/short bias, and that dragging it changes how aggressive and directional the rebalance can be. Reword the preview from terse `Now` / `off policy` copy to `Current portfolio is outside this exposure policy` when out of range, while preserving exact current gross/net values and the data role used by tests.

Fourth, update `view_model/setup.cljs` summary formatting. Keep numeric values exact to two decimals for leverage-like multiples and one decimal for rebalance tolerance. Use `Gross`, `Net`, `Max asset`, and `Rebalance`; make net ranges explicit with signed values and a direction suffix when both endpoints point the same way. If net crosses zero, label it `neutral range` rather than implying long or short.

Fifth, update CSS only if the new caption/status copy needs spacing or contrast adjustment. Prefer existing setup and exposure-map classes; add small scoped classes in `src/styles/surfaces/optimizer/setup.css` rather than one-off inline class strings when the styling is reusable.

## Concrete Steps

Run commands from `/Users/barry/.codex/worktrees/3667/hyperopen`.

1. Add failing tests:

   `apply_patch` updates `test/hyperopen/views/portfolio/optimize/setup_layout_test.cljs`, `test/hyperopen/portfolio/optimizer/application/view_model_setup_boundary_test.cljs`, and `tools/playwright/test/optimizer-exposure-map.spec.mjs`.

2. Verify the red state:

   `npm test`

   Expected before implementation: failure mentioning the old closed `details` panel or old summary string.

3. Implement the UI and summary formatter changes:

   `apply_patch` updates `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs`, `src/hyperopen/views/portfolio/optimize/setup_exposure_map.cljs`, `src/hyperopen/portfolio/optimizer/application/view_model/setup.cljs`, and optionally `src/styles/surfaces/optimizer/setup.css`.

4. Run focused and broad validation:

   `npm test`

   `npx playwright test tools/playwright/test/optimizer-exposure-map.spec.mjs --workers=1`

   `npm run gates`

   `npm run qa:design-ui -- --targets portfolio-optimizer-route --viewports review-375,review-768,review-1280,review-1440 --manage-local-app`

   `npm run browser:cleanup`

5. Update this ExecPlan with actual validation output, then move it to `docs/exec-plans/completed/2026-07-03-optimizer-portfolio-exposure-hierarchy.md`.

## Validation and Acceptance

Acceptance is behavioral and user-visible:

- On `/portfolio/optimize/new`, the section formerly titled `Constraints` is titled `Portfolio exposure`.
- The section is open by default, while `Return / Risk Model` and `Advanced Overrides` remain collapsed.
- The header includes an exact readable summary such as `Gross <= 2.00x · Net +1.00x long · Max asset 50% · Rebalance 3.0 pp` with the app's multiplication glyph in real UI.
- The section body explains that it sets leverage and net long/short exposure.
- The 2D map remains visible and interactive, and its caption makes clear that the dot represents target gross leverage and net bias.
- The raw gross/net min/max inputs remain available behind `Advanced solver limits` and retain their existing data roles.
- `npm run gates` passes.
- The focused Playwright exposure-map spec passes.
- Governed browser QA accounts for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at 375, 768, 1280, and 1440 widths.

## Idempotence and Recovery

The changes are additive presentation changes around existing controls. Re-running tests and browser QA is safe. If the governed design-review run fails because a managed Shadow CLJS server is already running, run `npm run browser:cleanup` and rerun the design review. If tests reveal hidden assumptions around the `portfolio-optimizer-constraints-panel` role, keep the role stable and update only user-facing copy assertions.

## Artifacts and Notes

Validation evidence from 2026-07-03:

- `npm test` first failed before implementation, as expected, on the old closed `Constraints` behavior and old summary/caption assumptions.
- `npm test` after implementation passed: `Ran 5001 tests containing 27418 assertions. 0 failures, 0 errors.`
- `npm run css:build` passed after CSS changes; it printed the existing outdated Browserslist data warning.
- `PLAYWRIGHT_REUSE_EXISTING_SERVER=false npx playwright test tools/playwright/test/optimizer-exposure-map.spec.mjs --workers=1` passed on the final rerun: `6 passed (37.9s)`.
- `PLAYWRIGHT_REUSE_EXISTING_SERVER=false npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs -g optimizer --workers=1` passed on the final rerun: `22 passed (3.4m)`.
- `npm run qa:design-ui -- --targets portfolio-optimizer-route --viewports review-375,review-768,review-1280,review-1440 --manage-local-app` passed in run `design-review-2026-07-03T18-17-21-567Z-82e82793`, run dir `tmp/browser-inspection/design-review-2026-07-03T18-17-21-567Z-82e82793`, with visual-evidence-captured, native-control, styling-consistency, interaction, layout-regression, and jank-perf all PASS at 375, 768, 1280, and 1440.
- `npm run gates` passed: 34/34 gates, 5706 tests, 30763 assertions, overall PASS.
- `npm run browser:cleanup` after browser work returned `ok: true` with no sessions left to stop.

## Interfaces and Dependencies

No new runtime interfaces, browser-storage keys, solver contracts, or action contracts are introduced. The implementation depends only on existing view helpers:

- `controls/disclosure-panel-open` for the default-open native details panel.
- `controls/disclosure-heading` for the summary/header structure.
- `optimizer-view-model/constraints-summary-line` for exact solver-facing summary copy.
- Existing `data-role` anchors under `portfolio-optimizer-constraints-panel` and `portfolio-optimizer-exposure-map`.

Revision note 2026-07-03: Created the active plan from the direct maintainer request and current source inspection.
