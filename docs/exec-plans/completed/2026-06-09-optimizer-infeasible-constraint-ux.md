# Optimizer infeasible constraint UX

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan follows `/hyperopen/.agents/PLANS.md` and exists because of a direct user request on 2026-06-09 to make the optimizer setup page explain presolve constraint violations and highlight the right controls. The concrete user-visible bug is that `/portfolio/optimize/new` can show `Infeasible Optimization`, `Reason: constraint-presolve`, and a raw `sum-upper-below-net-min` chip without explaining that the requested minimum net exposure is higher than the total possible positive exposure.

## Purpose / Big Picture

After this change, a user who enters impossible optimizer constraints can understand what happened and which inputs to change without reading source code or knowing optimizer internals. For the observed case, the banner should say that the maximum possible net exposure is below the minimum net exposure, identify the two numeric values when the result payload includes them, and mark the `Net Exposure Min` and `Max Asset Weight` controls as affected. The optimizer math and solver behavior should not change; this is a presentation and regression-coverage change for existing infeasible result payloads.

## Context References

Public refs:
- Direct user/maintainer request in this Codex thread on 2026-06-09: "can you create a plan to address the UX issues, so the banner explains the violation and highlights the right controls?"

Repo artifacts:
- `/hyperopen/src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs` renders the infeasible banner and computes highlighted controls.
- `/hyperopen/src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` renders the constraint inputs and already explains that `0.5` means 50 percent.
- `/hyperopen/src/hyperopen/portfolio/optimizer/domain/constraints.cljs` emits presolve violation maps such as `{:code :sum-upper-below-net-min :sum-upper ... :net-min ...}`.
- `/hyperopen/src/hyperopen/portfolio/optimizer/domain/objectives.cljs` wraps encoded constraint failures as `{:status :infeasible :reason :constraint-presolve :details {:violations ...}}`.
- `/hyperopen/test/hyperopen/views/portfolio/optimize/workspace_view_test.cljs` has view-level coverage for the infeasible banner and highlighted controls.
- `/hyperopen/tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs` has a deterministic optimizer infeasible banner browser smoke.
- `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/agent-guides/browser-qa.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md` govern UI copy, control highlighting, Playwright coverage, and browser QA.

Local scratch refs (non-authoritative):
- None.

## Progress

- [x] (2026-06-09 14:29Z) Traced the screenshot failure to `:sum-upper-below-net-min` emitted by the constraint encoder and confirmed the banner currently exposes raw codes with incomplete control mapping.
- [x] (2026-06-09 14:29Z) Created this active ExecPlan.
- [x] (2026-06-09 15:05Z) Added RED view coverage for user-facing `:sum-upper-below-net-min` copy and `Net Exposure Min` plus `Max Asset Weight` highlighting.
- [x] (2026-06-09 15:05Z) Implemented the narrow banner presentation change in `src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs`.
- [x] (2026-06-09 15:05Z) Added browser smoke coverage for the same seeded infeasible presolve result on `/portfolio/optimize/new` across `375`, `768`, `1280`, and `1440` widths.
- [x] (2026-06-09 15:36Z) Ran focused ClojureScript and Playwright validation, then required repo gates for code changes.
- [x] (2026-06-09 15:36Z) Completed governed browser QA and recorded every required pass as `PASS`.

## Surprises & Discoveries

- Observation: `sum-upper-below-net-min` is a presolve failure, not a numerical solver failure.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/constraints.cljs` computes `sum-upper` from upper bounds and emits `:sum-upper-below-net-min` when `sum-upper < net-min`; `src/hyperopen/portfolio/optimizer/domain/objectives.cljs` maps encoded constraint infeasibility to `:reason :constraint-presolve`.
- Observation: The UI already has a generic mapping for solver-result `:constraint-code :net-exposure`, but presolve violations without a `:constraint-code` fall back to code-specific mappings and the observed code is missing from that map.
  Evidence: `src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs` maps `:sum-upper-below-target` and solver result violations, but not `:sum-upper-below-net-min`.
- Observation: Constraint inputs are raw decimal exposure multiples, not percentage text fields.
  Evidence: `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs` tooltip copy says `0.5` means 50 percent and `1` means 100 percent of capital.
- Observation: The new view test failed for the intended RED reason before implementation.
  Evidence: `node out/test.js --test=hyperopen.views.portfolio.optimize.workspace-view-test` failed on missing explanatory copy and missing `data-infeasible` / `aria-invalid` on `Max Asset Weight` and `Net Exposure Min`.
- Observation: The focused view and browser regressions pass after implementation.
  Evidence: `node out/test.js --test=hyperopen.views.portfolio.optimize.workspace-view-test` passed 13 tests / 77 assertions, and `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=4173 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs -g "infeasible"` passed 4 tests.
- Observation: The in-app Browser surface could inspect the route but could not seed this app-state fixture through the same debug globals used by Playwright.
  Evidence: browser-side evaluation reported `window` and `document` but no `globalThis`, `cljs`, or `hyperopen` globals in that sandbox. The committed Playwright and one-off Playwright QA probe were used for the seeded state.
- Observation: A static-server browser QA probe must build CSS first in a fresh worktree.
  Evidence: the first screenshot was unstyled because `resources/public/css/main.css` was missing. After `npm run css:build`, the rerun produced styled screenshots and a passing `browser-report.json`.

## Decision Log

- Decision: Keep this change in the view layer and do not modify optimizer constraint encoding, request building, solver planning, or numeric semantics.
  Rationale: The engine already returns enough structured data for this case. The bug is that the banner does not translate that data into useful copy or control highlights.
  Date/Author: 2026-06-09 / Codex
- Decision: Add a small, explicit presolve-violation presentation map rather than deriving copy from raw keyword names.
  Rationale: User-facing error text should explain what happened, why it matters, and what to change. Keyword labels like `sum-upper-below-net-min` are useful for debugging but not for trading UX.
  Date/Author: 2026-06-09 / Codex
- Decision: Highlight both `Net Exposure Min` and `Max Asset Weight` for `:sum-upper-below-net-min`.
  Rationale: The impossible inequality is caused by a required minimum net exposure being greater than the available positive capacity. The user can usually fix it by lowering net minimum, adding eligible long assets, or raising caps; the visible controls that correspond to those actions in the current setup section are `Net Exposure Min` and `Max Asset Weight`.
  Date/Author: 2026-06-09 / Codex

## Outcomes & Retrospective

Complete. The final change is view-layer only: it adds one explicit presolve violation mapping and a small structured-message helper. Complexity increased only by the narrow translation needed to turn the existing optimizer payload into user-facing copy.

Behavior changed:
- `:sum-upper-below-net-min` now renders `Maximum possible net exposure is 2, below the minimum of 5.` when the payload includes `:sum-upper 2` and `:net-min 5`.
- The banner also renders `Lower Net Exposure Min, add eligible long assets, or raise Max Asset Weight.`
- `Net Exposure Min` and `Max Asset Weight` are marked with existing `data-infeasible` and `aria-invalid` behavior.
- `Net Exposure Max` is not marked for this violation alone.
- Existing explicit solver-result messages still render and dedupe.

Validation passed:
- `npm run test:runner:generate`
- `npx shadow-cljs --force-spawn compile test`
- RED before implementation: `node out/test.js --test=hyperopen.views.portfolio.optimize.workspace-view-test` failed on missing explanatory copy and missing `data-infeasible` / `aria-invalid` for the affected controls.
- GREEN after implementation: `node out/test.js --test=hyperopen.views.portfolio.optimize.workspace-view-test` passed 13 tests / 77 assertions.
- `npx shadow-cljs --force-spawn compile app`
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=4173 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs -g "infeasible"` passed 4 tests across `375`, `768`, `1280`, and `1440` widths.
- `npm run check`
- `npm test`
- `npm run test:websocket` passed 534 tests / 3090 assertions.
- `npm run css:build` for styled static-server QA screenshots. This emitted the existing Browserslist/caniuse-lite age warning.
- `npm run browser:cleanup` returned `{"ok": true, "stopped": [], "results": []}`.

Browser QA:
- Visual pass: PASS. Evidence: `tmp/browser-inspection/optimizer-infeasible-constraint-ux/browser-report.json` includes the explanatory banner copy at `375`, `768`, `1280`, and `1440`; screenshots were written as `review-375.png`, `review-768.png`, `review-1280.png`, and `review-1440.png`.
- Native-control pass: PASS. Evidence: `specialNativeControls` is empty at every required width.
- Styling-consistency pass: PASS. Evidence: the report captures computed banner and highlighted-input styles at every required width; no new one-off styling was introduced.
- Interaction pass: PASS. Evidence: the report confirms highlighted controls have `data-infeasible` and `aria-invalid`, `Net Exposure Max` remains clean, and hover/focus/keyboard checks completed at every width.
- Layout-regression pass: PASS. Evidence: `horizontalOverflow` is false and changed surfaces are not horizontally clipped at every required width.
- Jank/perf pass: PASS. Evidence: the QA probe completed repeated route open, panel open, hover, focus, tab, scroll, and resize work with zero console/page errors.

Residual blind spots:
- The in-app Browser sandbox could not seed this precise optimizer state through app globals, so the seeded-state QA evidence comes from Playwright rather than live Browser mutation.

## Context and Orientation

The optimizer setup route is `/portfolio/optimize/new`. Users choose a universe of instruments and numeric constraints such as maximum asset weight, gross exposure, and net exposure range. In this codebase, "net exposure" means the signed sum of target weights. A value of `1` means 100 percent net long, `0.5` means 50 percent net long, and `5` means 500 percent net long. "sum upper" means the total of the highest allowed positive target weight for each selected instrument after long-only settings, side selection, per-asset caps, held locks, and runtime sparse-history caps are applied.

The pure optimizer domain code is in `src/hyperopen/portfolio/optimizer/domain/constraints.cljs`. It calculates lower and upper bounds for each instrument in `bounds-for`, adds upper bounds in `violations`, and emits a violation map when the maximum possible net exposure is below the requested minimum net exposure. The view receives that violation through the run-state result.

The UI banner is in `src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs`. The current implementation gathers `:code` values, renders existing `:message` strings when present, renders raw keyword chips, and computes highlighted controls from `violation-control-keys` and `violation-constraint-control-keys`. This plan changes only that view presentation logic. It should remain pure Hiccup construction, using existing formatting helpers and existing `data-role` anchors.

The constraint inputs are rendered by `src/hyperopen/views/portfolio/optimize/setup_constraint_controls.cljs`. They already set `data-infeasible` and `aria-invalid` when `highlighted-control-keys` includes a corresponding control key. No new input component is required for this task unless implementation discovers that the existing highlighted state is insufficient.

## Plan of Work

First, add failing view-level coverage in `test/hyperopen/views/portfolio/optimize/workspace_view_test.cljs`. The existing `portfolio-optimizer-workspace-renders-infeasible-result-and-highlights-controls-test` can be extended or a new test can be added next to it. Prefer a new focused test named `portfolio-optimizer-workspace-explains-net-min-capacity-presolve-test` so the old target-net case stays readable.

The test should build a `/portfolio/optimize/new` view state with a draft containing two perp instruments and constraints `{:long-only? false :max-asset-weight 1.0 :gross-max 100 :net-min 5 :net-max 50}`. The run-state result should be:

    {:status :infeasible
     :reason :constraint-presolve
     :details {:violations [{:code :sum-upper-below-net-min
                             :sum-upper 2
                             :net-min 5}]}}

Before implementation, this test should fail because the banner does not contain explanatory copy and the `Net Exposure Min` input is not highlighted for this presolve code. After implementation, assert that the banner contains `Maximum possible net exposure is 2.00, below the minimum of 5.00.` or equivalently precise user-facing wording chosen during implementation. Also assert that the banner contains a remediation sentence such as `Lower Net Exposure Min, add eligible long assets, or raise Max Asset Weight.` Assert that both `portfolio-optimizer-constraint-net-min-input` and `portfolio-optimizer-constraint-max-asset-weight-input` have `data-infeasible` and `aria-invalid` set to `"true"`. Assert that `portfolio-optimizer-constraint-net-max-input` does not become highlighted from this violation alone.

Second, update `src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs`. Add `:sum-upper-below-net-min #{:net-min :max-asset-weight}` to `violation-control-keys`. Add a small helper that turns structured presolve violations into messages. The helper should only generate messages when enough numeric fields are present, and it must preserve existing explicit violation messages for solver diagnostics. One acceptable shape is:

    (defn- format-number [value]
      (opt-format/format-decimal value))

    (defn- structured-violation-message [violation]
      (case (:code violation)
        :sum-upper-below-net-min
        (when (and (number? (:sum-upper violation))
                   (number? (:net-min violation)))
          (str "Maximum possible net exposure is "
               (format-number (:sum-upper violation))
               ", below the minimum of "
               (format-number (:net-min violation))
               ". Lower Net Exposure Min, add eligible long assets, or raise Max Asset Weight."))
        nil))

If `opt-format/format-decimal` produces too many or too few digits for this context, add a private formatting helper in the same file using the existing format namespace behavior as a guide. Do not introduce a new dependency for formatting. Preserve de-duplication by feeding both explicit `:message` values and generated structured messages through `distinct`.

Third, extend the Playwright smoke in `tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs`. There is already a test that seeds an infeasible solver-result run state and checks banner messages plus net/turnover highlights. Add a second test near it for the presolve capacity case, or broaden the existing test only if it remains easy to read. Seed the same draft and run-state values from the view test. Assert the banner is visible, contains the explanatory sentence, contains the remediation sentence, and that the max-asset and net-min inputs have `data-infeasible="true"` while net-max does not.

Fourth, run validation in increasing scope. Start with generated test compilation and the focused ClojureScript test:

    cd /Users/barry/.codex/worktrees/f79a/hyperopen
    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.views.portfolio.optimize.workspace-view-test

Expected before implementation for the new RED test: the focused test fails because the new explanatory copy and net-min highlighting are missing. Expected after implementation: the focused namespace passes.

Then run the focused Playwright smoke path:

    cd /Users/barry/.codex/worktrees/f79a/hyperopen
    npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs -g "infeasible"

Expected after implementation: the optimizer infeasible tests pass and Playwright exits cleanly. If local browsers are missing, run `npm run test:playwright:install` once and retry.

Finally run the required code-change gates from `AGENTS.md`:

    cd /Users/barry/.codex/worktrees/f79a/hyperopen
    npm run check
    npm test
    npm run test:websocket

Because this is UI-facing work, complete browser QA according to `docs/FRONTEND.md` and `docs/agent-guides/browser-qa.md`. At minimum, account for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes at widths `375`, `768`, `1280`, and `1440`. Use Playwright for committed deterministic assertions. Use Browser MCP or browser-inspection for exploratory design-system QA if needed, then run `npm run browser:cleanup` before signoff.

## Concrete Steps

1. Edit `test/hyperopen/views/portfolio/optimize/workspace_view_test.cljs` and add the focused RED test described above after `portfolio-optimizer-workspace-renders-infeasible-result-and-highlights-controls-test`.
2. Run `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.portfolio.optimize.workspace-view-test` and record the failing assertion in this plan's `Surprises & Discoveries` or `Artifacts and Notes`.
3. Edit `src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs` to map `:sum-upper-below-net-min` to the affected controls and to generate the human-readable message from `:sum-upper` and `:net-min`.
4. Re-run the focused ClojureScript command and update `Progress` with the result.
5. Edit `tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs` and add the deterministic browser assertion for the seeded presolve result.
6. Run `npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs -g "infeasible"` and update `Progress`.
7. Run `npm run check`, `npm test`, and `npm run test:websocket`. If `npm run check` fails because of unrelated stale active ExecPlans or existing repo state, record the exact failure and do not hide it.
8. Perform governed browser QA for `/portfolio/optimize/new` in the seeded infeasible state. Record PASS, FAIL, or BLOCKED for each required pass and viewport before moving this plan to completed.

## Validation and Acceptance

Acceptance requires observable behavior, not just passing tests. A seeded infeasible optimizer result with `:code :sum-upper-below-net-min`, `:sum-upper 2`, and `:net-min 5` must render an infeasible banner that a user can understand without knowing the raw code. The banner must state that maximum possible net exposure is below minimum required net exposure and give concrete remediation. The raw keyword chip may remain as secondary technical detail, but it must not be the only explanation.

The `Net Exposure Min` and `Max Asset Weight` controls must be visually and semantically marked as affected using the existing `data-infeasible` and `aria-invalid` behavior. `Net Exposure Max` must not be marked for this exact violation unless another violation in the same payload points to it.

The existing solver-result diagnostics behavior must continue working. Explicit `:message` values in solver-result violations must still render once each, duplicate messages must stay deduped, and `:constraint-code :net-exposure` solver-result violations must still highlight both net exposure controls.

The required validation commands are:

    npm run test:runner:generate
    npx shadow-cljs --force-spawn compile test
    node out/test.js --test=hyperopen.views.portfolio.optimize.workspace-view-test
    npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs -g "infeasible"
    npm run check
    npm test
    npm run test:websocket

Browser QA is complete only when every pass from `docs/agent-guides/browser-qa.md` is explicitly accounted for at widths `375`, `768`, `1280`, and `1440`.

## Idempotence and Recovery

The implementation is additive and safe to retry. Re-running the ClojureScript and Playwright commands should not mutate persistent application data. If a browser-inspection or Browser MCP session is created during exploratory QA, run `npm run browser:cleanup` before signoff. If a test command fails before implementation for the intended RED reason, keep the failing test and proceed. If it fails for an unrelated compile or environment issue, record the exact failure and resolve that blocker before changing production code.

If the generated message copy changes during implementation, update all tests and this plan together so the plan remains self-contained. Do not weaken assertions to raw keyword text; the point of this task is human-readable remediation.

## Artifacts and Notes

Initial source evidence:

    src/hyperopen/portfolio/optimizer/domain/constraints.cljs
      (when (and (not (number? target-net*))
                 (finite-number? net-min)
                 (< sum-upper net-min))
        [{:code :sum-upper-below-net-min
          :sum-upper sum-upper
          :net-min net-min}])

    src/hyperopen/views/portfolio/optimize/infeasible_panel.cljs
      violation-control-keys currently includes :sum-upper-below-target
      but does not include :sum-upper-below-net-min.

    User screenshot values:
      Per-asset cap = 1
      Gross exposure = 100
      Net exposure min = 5
      Net exposure max = 50
      Observed banner chip = sum-upper-below-net-min

## Interfaces and Dependencies

No new external dependencies are allowed or needed. Use the existing ClojureScript view namespace `hyperopen.views.portfolio.optimize.infeasible-panel` and existing formatting helpers from `hyperopen.views.portfolio.optimize.format`. The public functions that must remain available are:

- `hyperopen.views.portfolio.optimize.infeasible-panel/infeasible-result`
- `hyperopen.views.portfolio.optimize.infeasible-panel/highlighted-control-keys`
- `hyperopen.views.portfolio.optimize.infeasible-panel/infeasible-banner`

The result payload contract consumed by this view is:

    {:status :infeasible
     :reason :constraint-presolve
     :message optional-human-readable-string
     :details {:violations [{:code keyword
                             :message optional-human-readable-string
                             :constraint-code optional-keyword
                             ...structured-fields}]}}

For this task, the structured fields for `:sum-upper-below-net-min` are `:sum-upper` and `:net-min`. The implementation should tolerate either field being absent by omitting the generated sentence and falling back to existing code-chip behavior.

## Revision Notes

- 2026-06-09 / Codex: Initial plan created from direct user request after tracing the optimizer infeasible banner behavior.
