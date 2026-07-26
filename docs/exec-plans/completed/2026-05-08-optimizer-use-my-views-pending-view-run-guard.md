---
owner: portfolio
status: completed
created: 2026-05-08
source_of_truth: false
tracked_issue: hyperopen-fexd
---

# Portfolio Optimizer Use My Views Pending View Run Guard

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while work proceeds.

This document is maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`. The live `bd` issue for this work is `hyperopen-fexd`; the `bd` issue is historical local tracker context from the active phase.

## Purpose / Big Picture

A user who selects `Use my views`, enters a BTC expected return of `20%`, and runs the optimizer should not see the BTC standalone frontier marker still reporting the old baseline expected return, such as `-21.33%`. The optimizer currently has two separate places that look like "the view": an editor draft under UI state and saved active views under the optimizer draft. Only saved active views reach the engine. If the user types `20%` but runs before pressing `+ Add view`, the engine receives a Black-Litterman return model with zero views, so BTC keeps its baseline return and the result looks like the user's view was inverted or ignored.

After this change, the run path must make that state boundary explicit. A valid pending editor view is materialized into the optimizer draft before the run starts, invalid pending editor content blocks the run with field-level errors, and Black-Litterman mode cannot run with zero active or pending views. The repair also guards against stale persisted views, such as a raw `BTC` view ID when the eligible universe contains `perp:BTC`, so a view cannot silently become a zero-weight row in the posterior math.

Follow-up screenshots on 2026-05-08 showed a second failure mode: the setup page's combined output correctly showed BTC moving from about `-21.4%` to `9.7%`, while the weights/frontier page still showed the old `-21.38%` BTC standalone marker. That means the posterior math and preview can be correct while the user is navigated to a retained stale result. The setup page exposed `View weights` and the right-rail results link whenever any old successful result existed, even when the current draft was dirty or a replacement run was in flight. That stale navigation path must be closed so only a clean, completed current result is presented as the weights page.

## Progress

- [x] (2026-05-08T18:36Z) Investigated the screenshot path from frontier marker display back through engine payload, Black-Litterman posterior, request building, readiness, and the editor actions.
- [x] (2026-05-08T18:36Z) Created `bd` bug `hyperopen-fexd`.
- [x] (2026-05-08T18:36Z) Documented the root cause and repair sequence in this active ExecPlan.
- [x] (2026-05-08T19:15Z) Add RED tests for pending editor views, zero-view Black-Litterman runs, stale view IDs, and the BTC `20%` view path.
- [x] (2026-05-08T19:15Z) Implement the shared pending-view materialization and run guard.
- [x] (2026-05-08T19:15Z) Implement request-builder validation for non-overlapping Black-Litterman view rows.
- [x] (2026-05-08T19:15Z) Update UI affordances and browser coverage.
- [x] (2026-05-08T19:15Z) Run focused tests, Playwright coverage, and required repo gates.
- [x] (2026-05-08T20:55Z) Reproduced the follow-up stale weights navigation bug with a RED setup-view test.
- [x] (2026-05-08T21:00Z) Hid stale setup result navigation until the draft is clean and no run is active.
- [x] (2026-05-08T21:00Z) Tightened the Black-Litterman calibration regression so BTC standalone overlays must use a positive posterior after a valid BTC view.

## Surprises & Discoveries

- Observation: The frontier tooltip is not changing return signs. It renders the `:frontier-overlays` data from engine payload.
  Evidence: `src/hyperopen/portfolio/optimizer/application/engine/payload.cljs` passes `expected-returns` and `covariance` to `frontier-overlays/overlay-series`; `src/hyperopen/portfolio/optimizer/domain/frontier_overlays.cljs` copies each return into `:standalone :expected-return`, and `src/hyperopen/views/portfolio/optimize/frontier_callout.cljs` formats that value as `Expected Return`.

- Observation: A valid active Black-Litterman BTC absolute view is already expected to move BTC away from a negative baseline.
  Evidence: `test/hyperopen/views/portfolio/optimize/setup_v4_use_my_views_cards_test.cljs` has a fixture where BTC has `:prior-return -0.216`, a `+20%` high-confidence view, and a `:posterior-return 0.096`. A correctly saved active view should not leave the final standalone marker at the prior.

- Observation: The typed editor value is not the same as an active view. The editor preview can display `BTC expected return +20% annualized` from `portfolio-ui` draft state even when `[:portfolio :optimizer :draft :return-model :views]` is still empty.
  Evidence: `src/hyperopen/views/portfolio/optimize/black_litterman_views_panel.cljs` builds `draft*` from `editor-state` and shows `preview-text`; `src/hyperopen/portfolio/optimizer/actions/run.cljs` starts the run from the optimizer draft; `src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs` builds the request from that draft only.

- Observation: The run button can be enabled even when readiness would not prove that the Black-Litterman views are meaningful.
  Evidence: `src/hyperopen/views/portfolio/optimize/workspace_view.cljs` computes `run-triggerable?` from `(seq (:universe draft))` and `running?`; it does not consider active view count or pending editor state.

- Observation: The displayed `136.22%` BTC volatility is not affected by a return view. It is the annualized standalone volatility from the covariance diagonal.
  Evidence: `src/hyperopen/portfolio/optimizer/domain/frontier_overlays.cljs` computes standalone volatility as `sqrt(covariance[idx][idx])`, and the Use My Views helper text correctly says that views adjust expected returns only and risk covariance is unchanged.

- Observation: Local focused tests were initially blocked by missing Node dependencies, then passed after a dependency refresh.
  Evidence: `npx shadow-cljs --force-spawn compile test` succeeded, but `node out/test.js` first failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. After `npm install`, the focused command ran `24 tests`, `131 assertions`, `0 failures`, `0 errors`.

- Observation: The Black-Litterman editor panel was already close to the production namespace-size threshold.
  Evidence: Adding the pending-view status pushed `src/hyperopen/views/portfolio/optimize/black_litterman_views_panel.cljs` to `512` lines and `npm run check` failed at `lint:namespace-sizes`. Splitting pure display helpers into `src/hyperopen/views/portfolio/optimize/black_litterman_views_model.cljs` reduced the panel to `417` lines and kept the new helper at `99` lines without adding a size exception.

- Observation: The follow-up screenshots prove this was not a formatter or chart sign flip. The setup preview had already computed the posterior BTC row, but the weights page could still show a retained prior result.
  Evidence: `setup_v4_use_my_views_cards.cljs` derives the setup cards from the current readiness request, while `results_panel.cljs` renders `[:portfolio :optimizer :last-successful-run :result]`. `workspace_view.cljs` previously considered `solved-run?` true for any old solved run and passed that through to `setup-bottom-actions` and `context-rail`, even when `[:portfolio :optimizer :draft :metadata :dirty?]` was true or `run-state` was `:running`.

## Decision Log

- Decision: Treat this as a run-boundary and validation bug, not a chart-rendering bug.
  Rationale: The chart displays whatever the engine emits. The observed `-21.33%` is consistent with a baseline expected return reaching the overlay, not a formatter flipping a saved `+20%` view.
  Date/Author: 2026-05-08 / Codex

- Decision: Materialize a valid pending editor view before running instead of silently requiring users to know that `+ Add view` is mandatory.
  Rationale: Clicking `Run optimization` after typing a valid view is a clear intent to use that view. The current split between editor draft and active views is too easy to miss, and the resulting wrong-looking financial output is worse than applying the valid pending input.
  Date/Author: 2026-05-08 / Codex

- Decision: Also block Black-Litterman runs with no active or pending views.
  Rationale: `Use my views` with zero views degenerates to the baseline return model while still labeling the run as Black-Litterman/posterior views. Blocking creates an explicit correction point instead of producing misleading output.
  Date/Author: 2026-05-08 / Codex

- Decision: Add a request-level guard for views whose weight rows do not overlap the eligible engine universe.
  Rationale: Persisted or legacy views can use display-level IDs such as `BTC` while the engine uses instrument IDs such as `perp:BTC`. Those views currently normalize as structurally valid but become all-zero view rows and do not affect the posterior.
  Date/Author: 2026-05-08 / Codex

- Decision: Treat the follow-up as stale result navigation, not a second posterior computation bug.
  Rationale: Engine payload construction already uses the posterior `expected-returns` vector for `:frontier-overlays`; the failing UI path was that setup still linked to an old `last-successful-run` while the current draft had unsaved Black-Litterman changes or a rerun was in flight.
  Date/Author: 2026-05-08 / Codex

- Decision: Hide setup weights/result navigation unless the last successful run is current.
  Rationale: A retained result is useful status context during edits or reruns, but presenting it as "View weights" makes stale weights look like the output of the current views. A result is current only when the last run is solved, no run is active, and the draft dirty flag is false.
  Date/Author: 2026-05-08 / Codex

## Outcomes & Retrospective

This plan records the diagnosis, implementation path, and validation evidence. The complexity increase stayed small by extracting editor parsing and materialization into one shared pure namespace reused by the editor, run action, and tests. The complexity decrease is larger because the optimizer now has one explicit path from typed view to active engine view, with no silent no-op state.

Implementation completed on 2026-05-08. The run action now materializes a valid pending Black-Litterman editor view into the optimizer draft before starting the pipeline, blocks invalid pending content with editor field errors, and blocks `Use my views` runs with no active or pending view. Request building drops Black-Litterman views that do not overlap the eligible engine universe and emits `:black-litterman-view-outside-universe` warnings instead of allowing all-zero posterior rows. The editor save path and run path share `black_litterman_actions/editor_model.cljs`, and the UI now shows a concise pending-view status before run.

Validation completed:

- Focused RED/GREEN CLJS target: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.actions-test --test=hyperopen.portfolio.optimizer.application.setup-readiness-test --test=hyperopen.portfolio.optimizer.application.request-builder-test` reached `30 tests`, `119 assertions`, `0 failures`, `0 errors` after implementation.
- Editor/panel regression target: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.black-litterman-actions-test --test=hyperopen.portfolio.optimizer.black-litterman-view-edits-test --test=hyperopen.views.portfolio.optimize.black-litterman-views-panel-test --test=hyperopen.portfolio.optimizer.actions-test --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.application.setup-readiness-test` reached `47 tests`, `233 assertions`, `0 failures`, `0 errors`.
- Engine/domain regression target: `npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.black-litterman-calibration-test --test=hyperopen.portfolio.optimizer.application.black-litterman-preview-test --test=hyperopen.portfolio.optimizer.domain.black-litterman-test --test=hyperopen.portfolio.optimizer.domain.frontier-overlays-test --test=hyperopen.portfolio.optimizer.domain.risk-test` reached `15 tests`, `59 assertions`, `0 failures`, `0 errors`.
- Browser regression: `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8082 PLAYWRIGHT_WEB_SERVER_COMMAND='npx shadow-cljs --force-spawn --config-merge ... watch app portfolio portfolio-worker portfolio-optimizer-worker vault-detail-worker' npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs` reached `4 passed`.
- Browser cleanup: `npm run browser:cleanup` returned `{"ok": true, "stopped": [], "results": []}`.
- Required gates: `npm run check`, `npm test` (`3793 tests`, `20909 assertions`, `0 failures`, `0 errors`), and `npm run test:websocket` (`524 tests`, `3043 assertions`, `0 failures`, `0 errors`) all exited `0`.

Follow-up stale-result repair completed on 2026-05-08. The setup workspace now treats a successful run as current only when the last run is solved, no optimizer run is active, and the draft dirty flag is false. Dirty or in-flight drafts can still show the retained last successful run as status context, but they no longer expose `View weights`, the right-rail results link, or scenario saving as if the retained result belongs to the current Black-Litterman view. The Black-Litterman calibration regression also now asserts that a valid BTC posterior cannot leave the standalone BTC overlay negative.

Follow-up validation completed:

- RED setup regression first failed with `portfolio-optimizer-setup-route-shows-run-state-without-retained-result-surface-test`: dirty/in-flight drafts still exposed stale weights navigation.
- Focused GREEN regression: `npx shadow-cljs --force-spawn compile test && node out/test.js` reached `3794 tests`, `20912 assertions`, `0 failures`, `0 errors`.
- Browser regression: `PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs` reached `5 passed`.
- Browser cleanup: `npm run browser:cleanup` returned `{"ok": true, "stopped": [], "results": []}`.
- Required gates: `npm run check`, `npm test` (`3794 tests`, `20912 assertions`, `0 failures`, `0 errors`), and `npm run test:websocket` (`524 tests`, `3043 assertions`, `0 failures`, `0 errors`) all exited `0`.

## Context and Orientation

The Portfolio Optimizer setup UI is under `src/hyperopen/views/portfolio/optimize/**`. The pure optimizer application and domain code is under `src/hyperopen/portfolio/optimizer/**`. Black-Litterman is exposed to users as `Use my views`. A Black-Litterman absolute view is a belief that one instrument has a specific annualized expected return. A BTC absolute view of `20%` is stored as decimal `0.2`, with weights `{"perp:BTC" 1}`.

The editor has its own UI state under `[:portfolio-ui :optimizer :black-litterman-editor]`. Saved active views live under `[:portfolio :optimizer :draft :return-model :views]`. The current editor save action converts `return-text` such as `"20"` or `"20%"` to decimal `0.2` and saves an active view. The run action does not call that save path; it starts `:effects/run-portfolio-optimizer-pipeline`, and the pipeline builds a request from the optimizer draft.

The engine path is deterministic once a request is built. `src/hyperopen/portfolio/optimizer/application/engine/context.cljs` estimates risk, estimates baseline expected returns, computes the Black-Litterman posterior when `:return-model :kind` is `:black-litterman`, and passes the resulting expected-return vector to the solver and display-frontier overlay builder. If there are no views, `black-litterman/posterior-returns` returns the baseline prior returns unchanged.

## Plan of Work

Milestone 1 is to lock the bug with tests before implementation. Add action-level coverage that starts from a state with `Use my views`, BTC in the universe, no active views, and a pending editor draft with `:instrument-id "perp:BTC"`, `:return-text "20"`, and `:return-text-touched? true`. The test should assert that the run action does not emit a pipeline effect alone; it must first materialize the pending view or block with editor errors. Add a second test for `Use my views` with no active views and no pending view, expecting no run effect and a visible error path. Add request-builder coverage for a stale view with `:instrument-id "BTC"` while the eligible universe contains `perp:BTC`; that view should be dropped with a warning instead of reaching posterior math as a zero row. Extend the Black-Litterman calibration test so a BTC baseline around `-21.33%` plus an active `20%` view cannot produce a standalone overlay at `-21.33%`.

Milestone 2 is to extract reusable editor materialization logic. Create `src/hyperopen/portfolio/optimizer/black_litterman_actions/editor_model.cljs` or an equivalent small namespace. Move the pure pieces currently private in `src/hyperopen/portfolio/optimizer/black_litterman_actions/editor.cljs` into it: parsing percent text, validation, confidence/horizon normalization, `draft->view`, and reset behavior. Preserve the existing public action API. The extracted helper should expose a function like `pending-editor-view-result` that returns one of three outcomes: `{:status :none}`, `{:status :invalid :errors ...}`, or `{:status :valid :view ... :kind ... :editing-view-id ...}`.

Milestone 3 is to repair the run action. Update `src/hyperopen/portfolio/optimizer/actions/run.cljs` so `run-portfolio-optimizer-from-draft` checks for a Black-Litterman pending editor view before emitting the pipeline effect. If the pending view is valid, emit `:effects/save-many` that inserts or replaces the view in `[:portfolio :optimizer :draft :return-model :views]`, clears editor errors, clears `:editing-view-id`, resets the relevant editor draft, marks the optimizer draft dirty, and then emits `:effects/run-portfolio-optimizer-pipeline`. If the pending view is invalid, save the field errors and do not run. If Black-Litterman is selected with no active views and no valid pending view, save an error such as `{:return-text "Add a view before running Use my views."}` and do not run.

Milestone 4 is to add request-level view validity. Update `src/hyperopen/portfolio/optimizer/application/request_builder.cljs` so normalized Black-Litterman views are checked against the engine-eligible universe after history alignment. A view is eligible only when at least one non-zero key in its `:weights` map is in the eligible instrument ID set, and relative views should require both legs when both are expected. Invalid or stale views should be removed from `[:return-model :views]` and represented by warnings such as `{:code :black-litterman-view-outside-universe :view-id ... :instrument-ids [...]}`. Add a low-level guard in `src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs` only if request-builder coverage proves a malformed row can still reach the domain; the domain should report ignored view count rather than silently applying an all-zero row.

Milestone 5 is to make the UI state visible. Update `src/hyperopen/views/portfolio/optimize/black_litterman_views_panel.cljs`, `src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs`, and related tests so a pending valid editor view is not visually confused with an active view. The run area should either say that the pending view will be applied on run or block with concise copy when Use My Views has no active view. Keep the copy operational and short; do not add explanatory feature text beyond what is needed to prevent a mistaken run.

Milestone 6 is browser verification. Because this touches optimizer UI and run behavior, add or update the smallest deterministic Playwright coverage in `tools/playwright/test/optimizer-black-litterman-views.spec.mjs`. The browser path should select Use My Views, select BTC, type `20`, run without separately clicking `+ Add view`, and verify that the active request/result no longer leaves BTC at the negative baseline. If the browser test cannot reach a deterministic solved result without excessive fixture work, assert the deterministic DOM/request state and keep the engine-level BTC overlay assertion in CLJS tests.

## Concrete Steps

Run all commands from `/Users/barry/.codex/worktrees/eb1c/hyperopen`.

1. Refresh the generated CLJS test runner after adding or renaming any test namespace:

       npm run test:runner:generate

   Expected result: the command exits `0` and updates `test/test_runner_generated.cljs` only if namespace membership changed.

2. Add RED tests for pending editor materialization and zero-view blocking:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.black-litterman-actions-test --test=hyperopen.portfolio.optimizer.application.setup-readiness-test

   Expected before implementation: at least one new assertion fails because the run action currently emits only `:effects/run-portfolio-optimizer-pipeline` and readiness currently permits zero-view Black-Litterman requests.

3. Add RED tests for stale view IDs and the BTC `20%` result path:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.application.black-litterman-calibration-test

   Expected before implementation: stale IDs either survive without warning or the BTC result can remain at the baseline when the view does not overlap the eligible engine IDs.

4. Implement the shared editor model and update editor/run actions. Rerun:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.black-litterman-actions-test --test=hyperopen.portfolio.optimizer.black-litterman-view-edits-test --test=hyperopen.views.portfolio.optimize.black-litterman-views-panel-test

   Expected result: the editor tests pass, active view saving remains unchanged, and run action tests prove a valid pending BTC `20%` view is materialized before the pipeline starts.

5. Implement request-builder stale-view validation and any necessary domain diagnostic guard. Rerun:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.portfolio.optimizer.application.request-builder-test --test=hyperopen.portfolio.optimizer.domain.black-litterman-test --test=hyperopen.portfolio.optimizer.application.black-litterman-calibration-test --test=hyperopen.portfolio.optimizer.application.black-litterman-preview-test

   Expected result: stale view IDs are warned and dropped, a valid BTC active view changes the posterior, and preview behavior remains consistent.

6. Update setup UI tests and Playwright coverage:

       npx shadow-cljs --force-spawn compile test
       node out/test.js --test=hyperopen.views.portfolio.optimize.setup-v4-layout-test --test=hyperopen.views.portfolio.optimize.setup-view-test --test=hyperopen.views.portfolio.optimize.setup-v4-use-my-views-cards-test
       npx playwright test tools/playwright/test/optimizer-black-litterman-views.spec.mjs
       npm run browser:cleanup

   Expected result: setup UI tests pass, the Playwright path proves the user workflow, and browser cleanup exits `0`.

7. Run the required gates for code changes:

       npm run check
       npm test
       npm run test:websocket

   Expected result: all three commands exit `0`. If a gate fails for an unrelated environment issue, record the exact command and error in this plan before stopping.

## Validation and Acceptance

Acceptance requires a failing-before, passing-after test that reproduces the user workflow: `Use my views`, BTC selected, `20` or `20%` typed in the editor, and run clicked without a separate `+ Add view` click. The resulting engine request must contain a BTC absolute view with decimal return `0.2`, and the result's `[:frontier-overlays :standalone]` BTC row must not remain at the negative baseline.

Acceptance also requires a zero-view guard. If Black-Litterman is selected and there are no active views and no valid pending editor view, the run must not start and the UI must tell the user to add a view. This prevents a run labeled as `Use my views` from behaving like historical expected returns.

Acceptance also requires stale view validation. A view with `:instrument-id "BTC"` must not silently no-op when the eligible universe uses `perp:BTC`. The request builder must either map it through a deliberate compatibility rule or drop it with a warning. Silent all-zero Black-Litterman rows are not acceptable.

Volatility acceptance is separate: a BTC expected-return view must not change BTC's standalone volatility because risk covariance is intentionally unchanged by Use My Views. If BTC volatility remains around `136%`, that can be valid when supported by the annualized covariance diagonal. The UI should avoid implying that expected-return views target volatility.

## Idempotence and Recovery

The implementation is safe to develop incrementally. Start with tests, keep the editor model extraction small, and avoid changing solver math unless a test proves malformed view rows can still reach the domain. Do not run `git pull --rebase` or `git push` for this work unless the user explicitly requests remote sync in the current session.

If dependency state blocks `node out/test.js` with a missing Lucide module, run `npm install` once and retry the exact command. This was observed during investigation and did not require package metadata changes.

If browser verification opens local sessions, stop them with `npm run browser:cleanup` before concluding. Do not leave Browser MCP or browser-inspection sessions running.

## Artifacts and Notes

Focused investigation command that passed after dependency refresh:

    npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.application.black-litterman-calibration-test --test=hyperopen.portfolio.optimizer.application.black-litterman-preview-test --test=hyperopen.portfolio.optimizer.black-litterman-actions-test --test=hyperopen.views.portfolio.optimize.black-litterman-views-panel-test --test=hyperopen.portfolio.optimizer.domain.frontier-overlays-test --test=hyperopen.portfolio.optimizer.domain.risk-test

    Ran 24 tests containing 131 assertions.
    0 failures, 0 errors.

Key source paths:

    src/hyperopen/views/portfolio/optimize/black_litterman_views_panel.cljs
    src/hyperopen/views/portfolio/optimize/workspace_view.cljs
    src/hyperopen/views/portfolio/optimize/setup_v4_sections.cljs
    src/hyperopen/portfolio/optimizer/actions/run.cljs
    src/hyperopen/portfolio/optimizer/black_litterman_actions/editor.cljs
    src/hyperopen/portfolio/optimizer/black_litterman_actions/common.cljs
    src/hyperopen/portfolio/optimizer/application/setup_readiness.cljs
    src/hyperopen/portfolio/optimizer/application/request_builder.cljs
    src/hyperopen/portfolio/optimizer/domain/black_litterman.cljs
    src/hyperopen/portfolio/optimizer/domain/frontier_overlays.cljs
    src/hyperopen/portfolio/optimizer/application/engine/payload.cljs

## Interfaces and Dependencies

The new shared editor model namespace should expose pure functions with stable map inputs and outputs so action tests can exercise it without DOM state. Suggested public functions are `pending-editor-view-result`, `materialize-view`, and `empty-black-litterman-views?`, but exact names may follow local style if they remain clear and covered.

`run-portfolio-optimizer-from-draft` must continue to return effect vectors only. It should not call effects directly. If it needs to save a pending view before running, the returned vector must place the `:effects/save-many` before `:effects/run-portfolio-optimizer-pipeline`.

`request-builder/build-engine-request` remains the application boundary that converts draft state and loaded history into an engine request. It should preserve valid normalized views and append warnings for dropped invalid views. The engine domain should remain pure and deterministic.
