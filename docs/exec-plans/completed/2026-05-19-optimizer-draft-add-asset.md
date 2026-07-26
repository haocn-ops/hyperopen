# Optimizer Draft Add Asset

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `.agents/PLANS.md` and `docs/PLANS.md`. It captures a direct user request from May 19, 2026: add an Add asset affordance to the optimizer draft allocation view, surface the same tradable asset search used by the optimizer setup flow, and automatically recompute after the asset is added.

## Purpose / Big Picture

After this change, a user reviewing an optimizer draft result can add another tradable asset directly from the Allocation / Current vs Target panel. Clicking Add asset opens a compact searchable selector using the optimizer universe candidate model. Choosing a candidate updates the draft universe, closes the selector, and starts a new optimizer pipeline run so the recommendation recomputes with the added asset.

The observable result is that the recommendation tab for a current draft run shows Add asset in the allocation panel, the selector can search and add unused assets such as ETH, and the add click emits a single ordered action that saves the new draft universe before running the optimizer pipeline.

## Context References

Public refs:
- Direct user/maintainer request in this Codex session on 2026-05-19.

Repo artifacts:
- `AGENTS.md` requires an ExecPlan for UI work and required gates after code changes.
- `docs/FRONTEND.md` governs UI runtime interaction ordering and browser QA.
- `docs/BROWSER_TESTING.md` and `docs/agent-guides/browser-qa.md` govern Playwright and browser-QA routing.
- `.agents/skills/ui-flow/SKILL.md` requires UI evidence, deterministic coverage when possible, implementation review, and browser-QA accounting.

Local scratch refs:
- Read-only explorer agents in this session identified optimizer view and test surfaces. They are non-authoritative; this plan is the source for implementation.

## Progress

- [x] (2026-05-19 15:37Z) Reviewed the root operating contract, UI/testing guidance, ExecPlan template, existing optimizer universe selector, result allocation table, universe add action, optimizer run action, and relevant CLJS/Playwright tests.
- [x] (2026-05-19 15:37Z) Created this active ExecPlan before production edits.
- [x] (2026-05-19 15:38Z) Added failing CLJS tests for the draft allocation add-asset UI and the combined add-and-run action. The RED run showed missing action vars before implementation.
- [x] (2026-05-19 15:45Z) Implemented UI state paths, actions, runtime registration, effect-order policy, and allocation selector rendering. Focused tests passed after implementation.
- [x] (2026-05-19 15:56Z) Added focused Playwright coverage for the stable draft allocation add-asset interaction using the retained draft route and optimizer state seed helpers.
- [x] (2026-05-19 16:15Z) Addressed static review: the combined action now composes with `run-portfolio-optimizer-from-draft` on the projected post-add state, preserving Black-Litterman validation/materialization gates.
- [x] (2026-05-19 16:24Z) Addressed visual review: the selector now autofocuses, uses a text input with custom clear affordance, shares the setup search shell styling, and stays contained across `375`, `768`, `1280`, and `1440` Playwright viewports.
- [x] (2026-05-19 16:39Z) Regenerated and verified the effect-order formal surface after adding the new action to the Lean policy corpus.
- [x] (2026-05-19 16:54Z) Ran required gates and browser-QA accounting. `npm run check`, `npm test`, `npm run test:websocket`, focused Playwright, and `npm run browser:cleanup` all passed.
- [x] (2026-05-19 16:55Z) Moved this plan to `docs/exec-plans/completed/` with final validation evidence.

## Surprises & Discoveries

- Observation: The optimizer setup screen already owns the preferred tradable asset search model.
  Evidence: `src/hyperopen/views/portfolio/optimize/setup_universe.cljs` renders `portfolio-optimizer-universe-search-input`, candidate rows, and `:actions/add-portfolio-optimizer-universe-instrument`; `src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs` exposes `universe-section-model` and `universe-panel-model`.

- Observation: The existing add action updates the draft universe and starts history prefetch, but it does not recompute.
  Evidence: `src/hyperopen/portfolio/optimizer/actions/universe.cljs` `add-portfolio-optimizer-universe-instrument` returns `:effects/save-many` and optionally `:effects/load-portfolio-optimizer-history`; `src/hyperopen/portfolio/optimizer/actions/run.cljs` `run-portfolio-optimizer-from-draft` separately emits `:effects/run-portfolio-optimizer-pipeline`.

- Observation: Result panels currently receive `draft` but not the full `state`, so the allocation panel cannot compute searchable candidates without a signature extension.
  Evidence: `src/hyperopen/views/portfolio/optimize/results_panel.cljs` calls `target-exposure-table/target-exposure-table` with only the enriched result.

- Observation: This worktree did not have `node_modules`, which blocked the CLJS test runner after compilation.
  Evidence: `npm ls lucide --depth=0` reported an empty dependency tree and `node out/test.js` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`. Running `npm ci` installed the lockfile dependencies and the focused tests then executed.

- Observation: The optimizer history discovery path can enrich the added ETH instrument ID to `hl:perp:ETH` in browser tests.
  Evidence: The focused Playwright regression first saw the draft universe as `["perp:BTC", "hl:perp:ETH"]` after clicking the ETH candidate. The assertion now accepts either the local or backend-enriched ETH ID while still proving the ETH asset was added.

- Observation: Appending `:effects/run-portfolio-optimizer-pipeline` directly bypasses the existing Black-Litterman draft run gate.
  Evidence: Static review pointed to `run-portfolio-optimizer-from-draft` in `src/hyperopen/portfolio/optimizer/actions/run.cljs`, which blocks empty BL views and materializes valid pending editor views before emitting the pipeline effect. A new regression in `test/hyperopen/portfolio/optimizer/universe_actions_test.cljs` covers the blocked empty-view case.

- Observation: The first popover pass failed at `375px` and had a tablet-height edge at `768px`.
  Evidence: Visual review reported the original `w-[360px]` absolute popover at `left=-14`, `bottom=1051.59` for `375x812`; the broadened Playwright geometry test then caught a `768x900` bottom of `912px`. The final selector is fixed/inset on small screens and capped to `md:max-h-[360px]` on larger screens.

## Decision Log

- Decision: Reuse the optimizer universe candidate view-model instead of introducing a new asset search model.
  Rationale: The setup screen and draft result screen should filter, rank, and label tradable assets identically. Reuse also preserves vault, spot, perp, HIP-3, and namespaced instrument display behavior already covered by tests.
  Date/Author: 2026-05-19 / Codex

- Decision: Add a new combined action named `add-portfolio-optimizer-universe-instrument-and-run` for the draft result selector.
  Rationale: A single action can return projection effects first, then the optimizer pipeline effect. This satisfies `docs/FRONTEND.md` interaction ordering and avoids relying on multi-action event sequencing to update state before recompute.
  Date/Author: 2026-05-19 / Codex

- Decision: The combined action must delegate recompute decision-making to `run-portfolio-optimizer-from-draft` using a projected state after the add/save effects.
  Rationale: This preserves existing Black-Litterman validation and editor materialization behavior while still letting an empty-to-nonempty draft run after the new instrument save applies.
  Date/Author: 2026-05-19 / Codex

- Decision: Store selector open state at `[:portfolio-ui :optimizer :draft-add-asset-open?]`.
  Rationale: The open/closed state is UI-only and belongs next to existing optimizer UI state such as `:universe-search-query`, `:universe-search-active-index`, and result tab state.
  Date/Author: 2026-05-19 / Codex

## Outcomes & Retrospective

Implemented the draft allocation Add asset flow end to end. The recommendation allocation panel now opens a compact tradable-asset selector, focuses the search input on mount, filters via the existing universe candidate view-model, adds the chosen asset to the draft, closes the selector, and recomputes through the canonical draft run action.

Static review found a real BL correctness issue in the first combined action implementation. The final implementation projects the add/save effects into a transient state and then calls `run-portfolio-optimizer-from-draft`, so invalid BL drafts remain blocked and valid pending BL edits are materialized exactly like the existing rerun path.

Browser review found real mobile/focus/styling issues in the first popover. The final selector uses the setup search shell pattern, a text input with custom clear affordance, autofocus, mobile fixed positioning, and multi-viewport geometry coverage.

Final validation:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.universe-actions-test --test=hyperopen.views.portfolio.optimize.results-panel-test --test=hyperopen.portfolio.optimizer.actions-test
    bb tools/formal.clj sync --surface effect-order-contract
    bb tools/formal.clj verify --surface effect-order-contract
    npm run css:build && npx shadow-cljs --force-spawn compile app portfolio portfolio-worker portfolio-optimizer-worker vault-detail-worker && PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_WEB_SERVER_COMMAND='PLAYWRIGHT_WEB_PORT=18080 node tools/playwright/static_server.mjs' npx playwright test tools/playwright/test/optimizer-view-model-routes.smoke.spec.mjs --grep "draft allocation add asset|draft add asset selector" --workers=1
    npm run check
    npm test
    npm run test:websocket
    npm run browser:cleanup

All commands exited 0. `npm test` ran 3969 tests / 21851 assertions; `npm run test:websocket` ran 524 tests / 3043 assertions; focused Playwright ran two tests and covered the add-and-recompute flow plus selector containment/focus at `375`, `768`, `1280`, and `1440`.

## Context and Orientation

The optimizer setup/new route lets users build a draft universe with a searchable list of tradable assets. In this repository, “universe” means the vector of optimizer instruments stored at `[:portfolio :optimizer :draft :universe]`. Each instrument has an `:instrument-id` such as `perp:BTC`, `spot:PURR/USDC`, or `vault:0x...`.

The setup search UI lives in `src/hyperopen/views/portfolio/optimize/setup_universe.cljs`. It delegates filtering and row labels to `src/hyperopen/portfolio/optimizer/application/view_model/universe.cljs`. The add action lives in `src/hyperopen/portfolio/optimizer/actions/universe.cljs`. It converts an asset-selector market into an optimizer instrument, appends it to the draft universe, clears the shared universe search query, and enqueues history prefetch if needed.

The allocation table shown on the optimizer draft recommendation view lives in `src/hyperopen/views/portfolio/optimize/target_exposure_table.cljs`, and it is mounted by `src/hyperopen/views/portfolio/optimize/results_panel.cljs`. The scenario recommendation tab in `src/hyperopen/views/portfolio/optimize/scenario_detail_view.cljs` passes the solved draft result into `results-panel/results-panel`.

The optimizer recompute path is `:actions/run-portfolio-optimizer-from-draft`, implemented in `src/hyperopen/portfolio/optimizer/actions/run.cljs`. That action emits `:effects/run-portfolio-optimizer-pipeline`, whose runtime effect reads the current store after earlier projection effects have applied, loads history when needed, and runs the worker when the draft is ready.

## Plan of Work

First, add failing CLJS tests. Extend `test/hyperopen/portfolio/optimizer/universe_actions_test.cljs` to require a new combined add-and-run action that appends a market, closes the draft add selector, clears search state, enqueues history prefetch when needed, and emits `:effects/run-portfolio-optimizer-pipeline` after projection effects. Extend `test/hyperopen/views/portfolio/optimize/results_panel_test.cljs` to require an Add asset button, no selector when closed, a compact selector when open, and candidate click actions that use the combined add-and-run action.

Second, add UI state paths and actions. Add `ui-draft-add-asset-open-path` to `src/hyperopen/portfolio/optimizer/contracts/paths.cljs` and re-export it from `src/hyperopen/portfolio/optimizer/contracts.cljs`. Add `set-portfolio-optimizer-draft-add-asset-open` and `add-portfolio-optimizer-universe-instrument-and-run` in `src/hyperopen/portfolio/optimizer/actions/universe.cljs`, then expose them through `src/hyperopen/portfolio/optimizer/actions.cljs`, `src/hyperopen/runtime/action_adapters.cljs`, `src/hyperopen/portfolio/optimizer/runtime_catalog.cljs`, `src/hyperopen/schema/runtime_registration/portfolio.cljs`, and `src/hyperopen/schema/contracts/action_args.cljs`.

Third, render the allocation selector. Extend `results-panel/results-panel` so options may include `:state`. Pass `state` from `scenario_detail_view.cljs` into the recommendation tab result panel. Update `target_exposure_table.cljs` to accept an options map with `:state` and `:draft`, render Add asset in the allocation header, and render a compact absolute-positioned selector when `ui-draft-add-asset-open-path` is true. The selector should use `optimizer-view-model/universe-panel-model` with the current state and draft, display the same search input and candidate rows pattern, and wire add clicks to `:actions/add-portfolio-optimizer-universe-instrument-and-run`.

Fourth, validate with focused and broad tests. Run the focused CLJS tests first, then the relevant Playwright interaction if practical, then the required gates from `AGENTS.md`: `npm run check`, `npm test`, and `npm run test:websocket`. For UI QA, account for the six browser-QA passes and the widths `375`, `768`, `1280`, and `1440`. If browser-inspection tooling is unavailable or too slow in this environment, mark those passes `BLOCKED` with the reason and still run deterministic Playwright where possible.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/dc6f/hyperopen`.

Run the focused tests before implementation to see the new assertions fail:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.portfolio.optimizer.universe-actions-test --test=hyperopen.views.portfolio.optimize.results-panel-test

Expected before implementation: failures naming missing action vars or missing Add asset UI nodes. Expected after implementation: both focused test namespaces pass.

If a Playwright test is added or changed, run it with a focused grep:

    PLAYWRIGHT_REUSE_EXISTING_SERVER=true npx playwright test tools/playwright/test/portfolio-regressions.spec.mjs --grep "portfolio optimizer draft allocation add asset"

Expected after implementation: the test opens the draft recommendation allocation selector, adds an unused asset, and observes optimizer progress or a rerun signal.

Run required gates after focused tests pass:

    npm run check
    npm test
    npm run test:websocket

Expected: each command exits 0. Record actual output summaries in this plan before moving it to completed.

## Validation and Acceptance

Acceptance criteria:

1. A current optimizer draft recommendation shows Add asset in the allocation table header.
2. Clicking Add asset opens a compact searchable tradable asset selector anchored to the allocation panel.
3. The selector uses the same candidate filtering and labels as the optimizer setup/new universe search.
4. Clicking a candidate appends that asset to the draft universe, clears/closes the selector, and emits `:effects/run-portfolio-optimizer-pipeline` after the draft save effect so the optimizer recomputes from the updated universe.
5. Existing setup universe search behavior remains unchanged.
6. Focused CLJS tests and required repository gates pass, or any blocked browser-QA item is explicitly reported with evidence.

## Idempotence and Recovery

The edits are additive and can be retried safely. If tests fail after a partial edit, re-run the focused CLJS command and inspect only the named failing namespace. If a new action is added in one runtime registry but not another, runtime or schema tests should fail; add the missing mapping rather than changing the test. If browser inspection sessions are started, run `npm run browser:cleanup` before finishing.

## Artifacts and Notes

Initial focused search evidence:

    src/hyperopen/views/portfolio/optimize/setup_universe.cljs renders portfolio-optimizer-universe-search-input and candidate rows.
    src/hyperopen/portfolio/optimizer/actions/universe.cljs add-portfolio-optimizer-universe-instrument saves draft universe and prefetch state.
    src/hyperopen/views/portfolio/optimize/target_exposure_table.cljs renders the Allocation table.

## Interfaces and Dependencies

New UI state path:

    contracts/ui-draft-add-asset-open-path
    [:portfolio-ui :optimizer :draft-add-asset-open?]

New actions:

    set-portfolio-optimizer-draft-add-asset-open
    Arguments: [state open?]
    Effects: save boolean open state, and clear search query/index when closing.

    add-portfolio-optimizer-universe-instrument-and-run
    Arguments: [state market-key]
    Effects: same projection as add-portfolio-optimizer-universe-instrument, plus close draft selector, plus :effects/run-portfolio-optimizer-pipeline after projection and optional history prefetch effect.

New or updated data roles:

    portfolio-optimizer-draft-add-asset
    portfolio-optimizer-draft-add-asset-popover
    portfolio-optimizer-draft-add-asset-search-input
    portfolio-optimizer-draft-add-asset-search-results
    portfolio-optimizer-draft-add-asset-candidate-row-<market-key>
    portfolio-optimizer-draft-add-asset-add-<market-key>

Revision note, 2026-05-19: Initial plan created from the direct user request before production edits so implementation can proceed with clear acceptance criteria and validation evidence.

Revision note, 2026-05-19: Updated progress and discoveries after the focused RED/GREEN loop and dependency installation needed to run the CLJS test runner.

Revision note, 2026-05-19: Added focused Playwright coverage and recorded the backend-enriched ETH identity observed in browser state.
