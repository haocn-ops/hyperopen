# Unify Black-Litterman Editor and View Model

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and the public planning contract in `/hyperopen/docs/PLANS.md`.

## Purpose / Big Picture

Black-Litterman views let a portfolio optimizer user express beliefs such as “BTC expected return is 20%” or “ETH will outperform SOL by 5%.” Before this change, the action path and the UI view path both modeled the same editor concepts: confidence levels, horizons, selected view kind, draft defaults, validation, auto-prefill, pending state, and preview text. This duplication increases the chance that the save action accepts one draft while the UI says another draft is valid, or that preview copy diverges from the saved view.

After this change, a single application-owned model under `src/hyperopen/portfolio/optimizer/application/` owns those concepts. The optimizer actions and `src/hyperopen/views/portfolio/optimize/**` UI code both consume that model. The observable behavior remains the same: existing Black-Litterman editor tests and panel tests pass, and a new application-model test proves the shared model produces the same draft, validation, and preview result used by both consumers.

## Context References

Public refs:

- Direct maintainer request on 2026-05-13: “Unify Black-Litterman editor/view modeling. The same concepts are modeled in optimizer action/application code and UI view code: confidence levels, horizons, selected kind, draft defaults, validation, preview text.”

Repo artifacts:

- `/hyperopen/AGENTS.md`
- `/hyperopen/docs/PLANS.md`
- `/hyperopen/.agents/PLANS.md`
- Existing related completed plans: `/hyperopen/docs/exec-plans/completed/2026-05-02-black-litterman-view-edit-crap-reduction.md`, `/hyperopen/docs/exec-plans/completed/2026-05-05-black-litterman-posterior-return-calibration.md`

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-13T20:27Z) Read the workflow, planning, and relevant Black-Litterman source files.
- [x] (2026-05-13T20:27Z) Created this active ExecPlan with acceptance criteria and implementation route.
- [x] (2026-05-13T20:29Z) Added RED-phase application-model tests and confirmed the focused compile fails because the shared namespace does not exist yet.
- [x] (2026-05-13T20:40Z) Implemented the shared application-owned Black-Litterman editor/view model facade with internal `rules` and `views` namespaces to satisfy namespace-size guardrails.
- [x] (2026-05-13T20:40Z) Migrated optimizer actions, defaults, and UI views/controls to consume the shared model facade.
- [x] (2026-05-13T20:49Z) Ran focused tests, committed Playwright Black-Litterman views coverage, `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-05-13T20:49Z) Confirmed no current-worktree static server, Browser MCP, or browser-inspection session remained running.
- [x] (2026-05-13T20:49Z) Moved this plan to `docs/exec-plans/completed/` after validation evidence was recorded.

## Surprises & Discoveries

- Observation: `src/hyperopen/views/portfolio/optimize/black_litterman_views_model.cljs` duplicates most of the editor draft and validation logic already present in `src/hyperopen/portfolio/optimizer/black_litterman_actions/common.cljs` and `src/hyperopen/portfolio/optimizer/black_litterman_actions/editor_model.cljs`.
  Evidence: both paths define selected kind fallback, absolute and relative draft defaults, max active views, pending draft detection, percentage parsing, valid relative comparator rules, and absolute-return auto-prefill.
- Observation: UI-specific instrument display should not be pulled into the application layer.
  Evidence: `black_litterman_views_model.cljs` requires `hyperopen.views.portfolio.optimize.instrument-display` for vault-aware labels. The new application model should accept a label function for preview text, while the view namespace keeps the UI label implementation.
- Observation: A single physical namespace for the full model exceeded the repository's default 500-line namespace-size guardrail.
  Evidence: the initial `src/hyperopen/portfolio/optimizer/application/black_litterman_editor_model.cljs` implementation was 588 lines. Splitting implementation details into `black_litterman_editor_model/rules.cljs` and `black_litterman_editor_model/views.cljs` reduced the facade to 94 lines, and `bb -m dev.check-namespace-sizes` passed.

## Decision Log

- Decision: Create `src/hyperopen/portfolio/optimizer/application/black_litterman_editor_model.cljs` as the shared pure model.
  Rationale: The requested ownership is application-level, and the model needs to be consumed by both action namespaces and view namespaces without making application code depend on `src/hyperopen/views/**`.
  Date/Author: 2026-05-13 / Codex
- Decision: Keep effect construction and UI-path persistence in action namespaces, but delegate editor concepts and draft-to-view materialization to the application model.
  Rationale: Actions are the side-effect boundary; the shared model should stay deterministic and easy to test.
  Date/Author: 2026-05-13 / Codex
- Decision: Keep UI label lookup in `black_litterman_views_model.cljs` and pass it into the application model for preview text.
  Rationale: This prevents a dependency from application code back into view code while still making preview sentence construction shared.
  Date/Author: 2026-05-13 / Codex
- Decision: Keep `hyperopen.portfolio.optimizer.application.black-litterman-editor-model` as the public consumer namespace, but split implementation into sibling `rules` and `views` namespaces.
  Rationale: Consumers still use one application-owned model, while each source file stays within the repository namespace-size policy.
  Date/Author: 2026-05-13 / Codex

## Outcomes & Retrospective

Implementation completed. The Black-Litterman editor semantics now live behind the application-owned facade `src/hyperopen/portfolio/optimizer/application/black_litterman_editor_model.cljs`, with internal `rules` and `views` namespaces keeping individual files below the repository namespace-size threshold. Optimizer actions, default UI state, and portfolio optimizer views/controls now consume that model instead of carrying separate confidence, horizon, draft, validation, and preview-text definitions.

Complexity decreased overall: action and UI namespaces now delegate to one shared model, and the duplicated view/action implementations were reduced by roughly 500 changed-line deletions in existing namespaces. The small cost is an extra internal namespace split, chosen to satisfy the repository size guardrail without weakening the public model boundary.

No Browser MCP or browser-inspection session was used for final evidence. Browser QA is accounted for by the committed Playwright Black-Litterman views spec: visual/layout coverage included the `review-375`, `review-768`, `review-1280`, and `review-1440` preview-width scenario; native-control coverage asserted no `select` elements in the editor panel; interaction coverage clicked the editor kind toggle, remove, clear, cancel, and run paths; styling and jank/perf risk is low because no CSS classes or DOM structure were intentionally changed, and `npm run check` passed the Hiccup, style, namespace, and build gates. The remaining blind spot is that no separate Browser MCP design-review artifact was produced for this behavior-preserving model refactor.

## Context and Orientation

The Black-Litterman editor state is stored under `[:portfolio-ui :optimizer :black-litterman-editor]`. Its drafts are keyed by view kind: `:absolute` for a single-asset expected return view, and `:relative` for an outperformance or underperformance spread between two assets. Saved views live under `[:portfolio :optimizer :draft :return-model :views]`.

The action path currently starts in `src/hyperopen/portfolio/optimizer/black_litterman_actions.cljs`, then delegates editor actions to `src/hyperopen/portfolio/optimizer/black_litterman_actions/editor.cljs`. That editor namespace uses `src/hyperopen/portfolio/optimizer/black_litterman_actions/common.cljs` for constants and draft helpers, and `src/hyperopen/portfolio/optimizer/black_litterman_actions/editor_model.cljs` to validate and materialize drafts.

The UI path currently uses `src/hyperopen/views/portfolio/optimize/black_litterman_views_panel.cljs`, `src/hyperopen/views/portfolio/optimize/black_litterman_views_controls.cljs`, and `src/hyperopen/views/portfolio/optimize/black_litterman_views_model.cljs`. The view model independently reconstructs selected kind, defaults, validation, pending status, auto-prefill, confidence display, horizon display, and preview text.

The shared application model will define the canonical editor concepts. UI code will still own Hiccup rendering, control CSS, and instrument labels. Action code will still own returning `:effects/save-many` data and dirty-draft flags.

## Plan of Work

First, add a focused application test namespace at `test/hyperopen/portfolio/optimizer/application/black_litterman_editor_model_test.cljs`. The RED test should require `hyperopen.portfolio.optimizer.application.black-litterman-editor-model` and assert that the shared model normalizes selected kind, builds absolute and relative selected drafts, auto-prefills an untouched absolute return from readiness inputs, validates duplicate relative comparator drafts, and produces preview text through an injected label function. Running the focused test before implementation should fail because the namespace does not exist.

Second, implement `src/hyperopen/portfolio/optimizer/application/black_litterman_editor_model.cljs`. It exposes canonical constants and functions: `view-kinds`, `max-active-views`, `confidence-options`, `horizon-options`, `direction-options`, `normalize-view-kind`, `selected-kind`, `draft-defaults`, `selected-draft`, `pending-draft?`, `validate-draft`, `draft-valid?`, `preview-text`, `display-confidence`, `display-horizon`, `confidence-weight`, `confidence-variance`, `draft->view`, `view->draft`, `reset-draft-after-save`, `editor-view-result`, `pending-editor-view-result`, and helpers for primary and comparator ids. All functions are pure, except helpers that deliberately accept already-built readiness data; this model does not emit effects and does not require view namespaces.

Third, change `src/hyperopen/portfolio/optimizer/black_litterman_actions/common.cljs` and `src/hyperopen/portfolio/optimizer/black_litterman_actions/editor_model.cljs` so they delegate the shared concepts to the application model. Preserve the public action function names and effect shapes. Keep state path helpers and `save-draft-path-values` in action code because those are action-side infrastructure.

Fourth, change `src/hyperopen/views/portfolio/optimize/black_litterman_views_model.cljs` and `src/hyperopen/views/portfolio/optimize/black_litterman_views_controls.cljs` to consume the shared application model. The view model should keep only display label lookup, formatting aliases needed by view components, and thin delegation. The controls namespace should source confidence and horizon options from the shared model so button sets cannot drift from action normalization.

Fifth, run the narrow ClojureScript tests first, then the required repository gates. Because this work touches `src/hyperopen/views/portfolio/optimize/**`, account for browser QA. If no browser-inspection session is created during implementation and the deterministic panel tests cover the UI behavior, record that browser MCP cleanup is not applicable; otherwise run `npm run browser:cleanup` before handoff.

## Concrete Steps

From `/Users/barry/.codex/worktrees/2cde/hyperopen`, run the RED command after adding the test:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.black-litterman-editor-model-test

Expected before implementation: compilation or namespace resolution fails because `hyperopen.portfolio.optimizer.application.black-litterman-editor-model` does not exist.

After implementing the shared model and migration, run:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.black-litterman-editor-model-test --test=hyperopen.portfolio.optimizer.black-litterman-actions-test --test=hyperopen.portfolio.optimizer.black-litterman-view-edits-test --test=hyperopen.views.portfolio.optimize.black-litterman-views-panel-test

Expected after implementation: all listed tests pass with zero failures and zero errors.

Then run the required gates:

    npm run check
    npm test
    npm run test:websocket

Expected after implementation: all commands exit 0. If browser sessions were created, also run:

    npm run browser:cleanup

## Validation and Acceptance

Acceptance is met when the shared application model test proves the canonical selected draft, validation, preview text, confidence/horizon options, and draft materialization behavior, and when existing action and panel tests still pass unchanged or with only import-level updates. The saved action shape must remain compatible: saving a valid absolute draft still writes a view with `:kind :absolute`, parsed decimal `:return`, `:confidence-level`, numeric `:confidence`, `:confidence-variance`, `:horizon`, `:weights`, and the dirty flag. Rendering the panel must still show the same preview strings and enable or disable the save button based on the shared validation result.

The required repository gates are `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The changes are source-level refactors and tests. They can be rerun safely. If the RED test fails for a typo instead of the missing namespace, fix the test before implementing. If migration breaks action behavior, compare the effect maps returned by `test/hyperopen/portfolio/optimizer/black_litterman_actions_test.cljs` before changing public action names or state paths. If UI preview copy changes unexpectedly, keep the old copy unless the shared model test explicitly documents the changed behavior.

## Artifacts and Notes

RED evidence:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.black-litterman-editor-model-test
    Generated test/test_runner_generated.cljs with 635 namespaces.
    [:test] Compiling ...
    The required namespace "hyperopen.portfolio.optimizer.application.black-litterman-editor-model" is not available, it was required by "hyperopen/portfolio/optimizer/application/black_litterman_editor_model_test.cljs".

Focused GREEN evidence:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.black-litterman-editor-model-test --test=hyperopen.portfolio.optimizer.black-litterman-actions-test --test=hyperopen.portfolio.optimizer.black-litterman-view-edits-test --test=hyperopen.views.portfolio.optimize.black-litterman-views-panel-test
    [:test] Build completed. (1659 files, 236 compiled, 0 warnings, 11.25s)
    Ran 20 tests containing 135 assertions.
    0 failures, 0 errors.

Playwright browser regression evidence:

    PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 PLAYWRIGHT_WEB_PORT=4174 PLAYWRIGHT_WEB_SERVER_COMMAND='node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs
    5 passed (50.2s)

Required gate evidence:

    npm run check
    Exit 0. Shadow builds completed for app, portfolio, portfolio-worker, portfolio-optimizer-worker, vault-detail-worker, and test with 0 warnings in the final build steps.

    npm test
    Ran 3878 tests containing 21391 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 524 tests containing 3043 assertions.
    0 failures, 0 errors.

Browser-session cleanup evidence:

    lsof -nP -iTCP:4174 -sTCP:LISTEN || true
    no output

    ps -ef | rg '/Users/barry/.codex/worktrees/2cde/hyperopen/.+(dev:browser-inspection|shadow-cljs --force-spawn watch|tailwindcss -i|static_server)'
    no matching current-worktree browser/dev process beyond the rg command itself

## Interfaces and Dependencies

The new namespace must be:

    hyperopen.portfolio.optimizer.application.black-litterman-editor-model

It may depend on:

    clojure.string
    hyperopen.portfolio.optimizer.application.return-inputs
    hyperopen.portfolio.optimizer.coercion

It must not depend on:

    hyperopen.views.*
    hyperopen.portfolio.optimizer.black-litterman-actions.*

The view namespace may pass this label function into preview helpers:

    (fn [instrument-id]
      (instrument-label universe instrument-id))

Revision note, 2026-05-13T20:27Z: Initial active ExecPlan created from direct maintainer request. The plan chooses an application-owned pure model with thin action and view adapters to remove duplicated editor semantics without changing public action APIs.

Revision note, 2026-05-13T20:29Z: Added RED evidence after the focused test failed at compile time for the intended missing shared namespace.

Revision note, 2026-05-13T20:40Z: Recorded implementation progress, the namespace-size split decision, and focused GREEN evidence.

Revision note, 2026-05-13T20:49Z: Recorded final Playwright and repository gate evidence, browser cleanup disposition, outcome summary, and moved the plan from active to completed.
