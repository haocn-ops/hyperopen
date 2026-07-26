# Funding Modal Workflow Slices and Intent Actions

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

This plan executes `bd` epic `hyperopen-393` with active child tasks `hyperopen-thb` and `hyperopen-ii5`.

## Purpose / Big Picture

Today `/hyperopen/src/hyperopen/views/funding_modal.cljs` mixes anchored popover layout, DOM fallback lookup, view-model interpretation, provider-specific lifecycle rendering, and all deposit/transfer/withdraw/legacy branches inside one render function. That shape makes the UI harder to reason about, easier to regress, and harder for follow-on contributors to change safely.

After this change, the funding modal will render through explicit content kinds backed by a clearer read model, shared panels will replace duplicated branches, funding UI controls will emit intent-revealing actions instead of raw field mutations, and the known correctness bugs in the current implementation will be fixed. A contributor can verify the result by opening the modal from `/trade` and `/portfolio`, checking anchored placement still works, and seeing that deposit, transfer, and withdraw flows render through smaller workflow slices with the same visible behavior.

## Progress

- [x] (2026-03-07 13:30Z) Re-read `/hyperopen/docs/PLANS.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`.
- [x] (2026-03-07 13:30Z) Audited `/hyperopen/src/hyperopen/views/funding_modal.cljs`, `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`, `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs`, and the current funding tests to define scope.
- [x] (2026-03-07 13:30Z) Created `bd` epic `hyperopen-393` and claimed child tasks `hyperopen-thb` and `hyperopen-ii5`.
- [x] (2026-03-07 13:30Z) Authored this ExecPlan.
- [x] (2026-03-07 13:43Z) Refactored the funding modal read model, content-kind dispatch, and shared panels, and extracted shared anchored popover math into `/hyperopen/src/hyperopen/views/ui/anchored_popover.cljs`.
- [x] (2026-03-07 13:43Z) Replaced funding modal UI event wiring with intent-revealing actions while preserving `:actions/set-funding-modal-field` as a compatibility seam.
- [x] (2026-03-07 13:46Z) Added regression tests for the new command wrappers, direction-scoped lifecycle rendering, portfolio funding entry points, modal view branches, and anchored popover width clamping.
- [x] (2026-03-07 13:51Z) Ran `npm run check`, `npm test`, and `npm run test:websocket` successfully after installing missing npm dependencies with `npm ci`.
- [x] (2026-03-07 13:51Z) Completed browser QA via browser-inspection live session `sess-1772891307682-1ae2d7` and recorded evidence in `/hyperopen/docs/qa/funding-modal-workflow-slices-2026-03-07.md`.

## Surprises & Discoveries

- Observation: The current view already depends on a view-model layer in `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`, but the view still recomputes lifecycle applicability, labels, and several fallback defaults locally.
  Evidence: `/hyperopen/src/hyperopen/views/funding_modal.cljs` currently derives `show-hyperunit-lifecycle?`, `lifecycle-state-label`, `lifecycle-status-label`, and fallback selected assets in render.

- Observation: The deposit branch can render withdraw lifecycle data because the deposit path checks only the shared `show-hyperunit-lifecycle?` flag while the withdraw branch also guards on `:direction`.
  Evidence: Deposit renders `(when show-hyperunit-lifecycle? ...)`, while withdraw renders `(when (and show-hyperunit-lifecycle? (= :withdraw (:direction lifecycle*))) ...)`.

- Observation: Render-time DOM anchor fallback is only needed because `/hyperopen/src/hyperopen/views/portfolio_view.cljs` still opens funding flows through the compatibility action without event bounds.
  Evidence: `/hyperopen/src/hyperopen/views/account_equity_view.cljs` already passes `:event.currentTarget/bounds`; `/hyperopen/src/hyperopen/views/portfolio_view.cljs` still uses `[:actions/set-funding-modal ...]`.

- Observation: The worktree was missing installed npm packages, so `shadow-cljs` could not compile until `npm ci` restored `node_modules`.
  Evidence: `npm run check` initially failed with `The required JS dependency "@noble/secp256k1" is not available`.

- Observation: Browser-inspection unsafe DOM clicks open the correct funding flows but do not preserve the anchor-bound placement signal used by the funding open actions.
  Evidence: The live session opened the deposit, withdraw, and transfer modals correctly, but the resulting modal geometry stayed centered with empty inline `left`/`top`/`width` style values after scripted clicks.

## Decision Log

- Decision: Keep the stored funding modal state shape compatible for this pass, but add a clearer nested read model in `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` and migrate the view to that shape.
  Rationale: The view is the immediate maintainability hotspot, while storage-shape migration would widen the change to effects and runtime seams unnecessarily. A nested read model gives the render layer a closed set of content kinds now without destabilizing persistence or effect code.
  Date/Author: 2026-03-07 / Codex

- Decision: Replace deposit’s misleading `MAX` affordance with a minimum-prefill action and label.
  Rationale: The modal does not have a known deposit maximum, and the current behavior already writes the minimum amount. Renaming the control to match behavior fixes the bug without inventing unsupported semantics.
  Date/Author: 2026-03-07 / Codex

- Decision: Remove render-time DOM anchor fallback from the funding modal and rely on explicit button bounds where the UI opens anchored modals.
  Rationale: This removes brittle selector coupling from the render path and keeps anchoring local to the interaction that triggered the modal. Compatibility openings with no anchor can still fall back to centered modal presentation.
  Date/Author: 2026-03-07 / Codex

- Decision: Replace the stale countdown-style “next check” copy with stable lifecycle scheduling copy in the read model instead of adding a new timer subsystem in this pass.
  Rationale: The current bug is stale time-dependent render output. Stable scheduling copy fixes correctness immediately without widening scope into recurring UI timer infrastructure.
  Date/Author: 2026-03-07 / Codex

## Outcomes & Retrospective

The implementation completed the scoped refactor for `hyperopen-393`, `hyperopen-thb`, and `hyperopen-ii5`.

Delivered changes:

- `/hyperopen/src/hyperopen/views/funding_modal.cljs` now dispatches through explicit content kinds and smaller helper renderers instead of one monolithic branch-heavy function.
- `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` now builds a nested read model for modal shell data, workflow content, lifecycle presentation, and feedback while preserving the older flat fields for compatibility.
- `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs` and related runtime registration files now expose named funding actions for search, asset selection, amount entry, withdraw destination entry, and deposit minimum prefilling.
- `/hyperopen/src/hyperopen/views/portfolio_view.cljs` now opens funding flows through the explicit anchor-aware open actions instead of the compatibility action, which allowed the render-time DOM selector fallback to be removed.
- Known bugs from the audit were fixed: deposit no longer shows a misleading `MAX` button, deposit does not render withdraw lifecycle state, anchored panel width no longer exceeds available viewport width, `complete-anchor?` no longer requires unused `:bottom`, and the stale render-time countdown copy was replaced with stable scheduling text.

Validation results:

- `npm run check` passed.
- `npm test` passed.
- `npm run test:websocket` passed.
- Browser QA note: `/hyperopen/docs/qa/funding-modal-workflow-slices-2026-03-07.md`
- Browser-inspection artifact roots:
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-07T13-50-54-300Z-3267d94b/`
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-07T13-50-54-304Z-6be36191/`

Residual note:

- Live browser QA confirmed the modal flows and the new controls, but anchor geometry could not be conclusively verified through scripted browser-inspection clicks because those clicks do not appear to carry the same bounds payload as the real UI event path. The anchoring change is therefore covered by the new explicit action wiring and automated layout tests, with the limitation documented in the QA note.

## Context and Orientation

The funding modal host lives in `/hyperopen/src/hyperopen/views/funding_modal.cljs`. It currently performs four jobs at once:

1. It calculates anchored modal placement and fallback selector lookup.
2. It interprets a large flat view model with deposit, withdraw, transfer, lifecycle, queue, and status fields.
3. It renders all workflow branches inline.
4. It wires most controls through the generic action `:actions/set-funding-modal-field`.

Funding modal state and submit behavior already have better seams than the view:

- `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` builds the current read model.
- `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs` owns modal state transitions.
- `/hyperopen/src/hyperopen/funding/application/modal_actions.cljs` exports actions and command dependencies.
- `/hyperopen/src/hyperopen/funding/domain/modal_state.cljs` defines the persisted modal state shape.
- `/hyperopen/test/hyperopen/funding/actions_test.cljs` already covers most funding action and view-model behavior.

The key goal in this plan is not to redesign the entire funding subsystem. It is to make the render path explicit and safer: a small set of content kinds, shared subcomponents for repeated UI panels, intent-revealing action names from the view, and targeted fixes for the current bugs.

## Plan of Work

First, extract the anchored popover math from `/hyperopen/src/hyperopen/views/funding_modal.cljs` into a shared UI helper namespace. That helper should own only generic placement rules. The funding modal view should stop querying DOM selectors at render time. To preserve anchored behavior, `/hyperopen/src/hyperopen/views/portfolio_view.cljs` will open deposit, transfer, and withdraw modals through the explicit funding open actions with `:event.currentTarget/bounds`, matching the existing account-equity entry points.

Second, reshape `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs` so it returns a nested read model that includes a `:content` kind and per-workflow sections for deposit, transfer, withdraw, legacy, feedback, and lifecycle presentation. The current flat keys may remain temporarily for compatibility, but the view should switch to the nested structure. Lifecycle applicability, status labels, stable next-check copy, and provider-specific summary state should be computed here instead of inside render helpers.

Third, rewrite `/hyperopen/src/hyperopen/views/funding_modal.cljs` into smaller private rendering functions: modal shell, deposit asset selection, deposit amount workflow, transfer form, withdraw form, shared lifecycle panel, shared summary row, and shared action row helpers. The top-level view should dispatch by content kind instead of nesting most workflow logic in one `let` block. This step also fixes the current bugs: deposit/withdraw lifecycle bleed-through, overflow on narrow viewports, the `spectate-none` typo, and the misleading deposit `MAX` control.

Fourth, add intent-revealing funding actions in `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs`, `/hyperopen/src/hyperopen/funding/application/modal_actions.cljs`, `/hyperopen/src/hyperopen/funding/actions.cljs`, `/hyperopen/src/hyperopen/runtime/collaborators.cljs`, `/hyperopen/src/hyperopen/schema/contracts.cljs`, and `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`. The funding modal view should use named actions such as selecting a deposit asset, searching deposit assets, returning to deposit asset selection, entering deposit/transfer/withdraw amounts, setting the deposit amount to minimum, selecting a withdraw asset, and entering a withdraw destination. The legacy generic setter stays as a compatibility seam for callers outside this view.

Finally, add tests. Extend `/hyperopen/test/hyperopen/funding/actions_test.cljs` for the new commands and updated read model. Add a new view test namespace for `/hyperopen/src/hyperopen/views/funding_modal.cljs` to lock in the rendering fixes. Add a focused test for the shared popover layout helper. Then run the required gates and a browser-inspection QA pass against local `/trade` and `/portfolio`, capturing the artifact path and observed behavior in this plan and in a QA note under `/hyperopen/docs/qa/`.

## Concrete Steps

Run from repository root `/hyperopen`.

1. Create a shared anchored popover helper and update funding entry points:
   - Add `/hyperopen/src/hyperopen/views/ui/anchored_popover.cljs`.
   - Edit `/hyperopen/src/hyperopen/views/funding_modal.cljs`.
   - Edit `/hyperopen/src/hyperopen/views/portfolio_view.cljs`.

2. Build the nested funding modal read model:
   - Edit `/hyperopen/src/hyperopen/funding/application/modal_vm.cljs`.

3. Introduce named funding modal commands and wire them into runtime registration:
   - Edit `/hyperopen/src/hyperopen/funding/application/modal_commands.cljs`.
   - Edit `/hyperopen/src/hyperopen/funding/application/modal_actions.cljs`.
   - Edit `/hyperopen/src/hyperopen/funding/actions.cljs`.
   - Edit `/hyperopen/src/hyperopen/runtime/collaborators.cljs`.
   - Edit `/hyperopen/src/hyperopen/schema/contracts.cljs`.
   - Edit `/hyperopen/src/hyperopen/schema/runtime_registration_catalog.cljs`.

4. Rewrite the funding modal render path around content-kind dispatch and shared panels:
   - Edit `/hyperopen/src/hyperopen/views/funding_modal.cljs`.

5. Add regression coverage:
   - Edit `/hyperopen/test/hyperopen/funding/actions_test.cljs`.
   - Add `/hyperopen/test/hyperopen/views/funding_modal_test.cljs`.
   - Add `/hyperopen/test/hyperopen/views/ui/anchored_popover_test.cljs`.

6. Run validation:

    npm run check
    npm test
    npm run test:websocket

7. Run manual browser QA and capture artifacts:

    npm run browser:inspect -- --url http://localhost:8080/trade --target local-trade
    npm run browser:inspect -- --url http://localhost:8080/portfolio --target local-portfolio

Expected result: the trade page funding actions open anchored modals with the refactored workflow slices, portfolio buttons still open the same flows, and both browser-inspection runs emit artifact directories under `/hyperopen/tmp/browser-inspection/`.

## Validation and Acceptance

Acceptance is satisfied when all of the following are true:

1. `funding-modal-view` dispatches through explicit content kinds and smaller render helpers rather than one monolithic render block.
2. The funding modal no longer performs render-time DOM selector fallback to find an anchor.
3. Deposit workflow renders only deposit lifecycle state, withdraw workflow renders only withdraw lifecycle state, and duplicated lifecycle panel code is replaced by one shared renderer.
4. Narrow viewport anchoring no longer overflows because panel width never exceeds available viewport width minus margins.
5. The deposit amount helper no longer exposes a misleading `MAX` control that writes the minimum amount.
6. Funding modal UI controls emit named intent actions instead of `:actions/set-funding-modal-field`.
7. Required validation gates pass.
8. Browser QA on local `/trade` and `/portfolio` confirms anchored open behavior and no visible regression in deposit, transfer, or withdraw modal flows.

## Idempotence and Recovery

These edits are additive and safe to repeat. If a step fails:

- keep `:actions/set-funding-modal-field` intact until the new named commands are fully wired,
- keep centered modal rendering as the fallback whenever no anchor bounds are available,
- rerun the targeted funding tests before rerunning the full gates.

No migration or destructive state change is required.

## Artifacts and Notes

Implementation evidence to capture during execution:

- `bd` epic/task ids: `hyperopen-393`, `hyperopen-thb`, `hyperopen-ii5`.
- Test namespaces covering the refactor.
- Browser-inspection artifact directories under `/hyperopen/tmp/browser-inspection/`.
- QA note path under `/hyperopen/docs/qa/`.

Revision note (2026-03-07): Initial ExecPlan created after auditing the current funding modal monolith, creating epic `hyperopen-393`, and claiming implementation tasks `hyperopen-thb` and `hyperopen-ii5`.

Revision note (2026-03-07): Updated after implementation to record the landed refactor, validation results, and the browser-inspection anchoring caveat observed during live QA.
