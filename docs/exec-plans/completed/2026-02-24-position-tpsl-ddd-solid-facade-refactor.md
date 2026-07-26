# Position TP/SL DDD and SOLID Facade Refactor

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, users will see the same Position TP/SL modal behavior, but the implementation will be split into clear domain, application, and boundary modules that are deterministic and easier to extend safely. This removes the current "all-in-one" module risk while preserving existing public APIs used by actions, views, and effects.

A user can verify this by opening the Position TP/SL modal, editing TP/SL, configure amount, and submit flows exactly as before, while the repository verifies unchanged behavior through targeted regressions and required validation gates.

## Progress

- [x] (2026-02-24 23:37Z) Audited `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` and identified responsibility clusters (state defaults, parsing/normalization, policy validation, transitions, submit orchestration, market resolution).
- [x] (2026-02-24 23:37Z) Audited architecture and quality constraints in `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/QUALITY_SCORE.md`, `/hyperopen/docs/FRONTEND.md`, and `/hyperopen/AGENTS.md`.
- [x] (2026-02-24 23:37Z) Mapped external consumers of the current public surface from actions, views, and tests to preserve compatibility.
- [x] (2026-02-24 23:37Z) Authored this ExecPlan in `/hyperopen/docs/exec-plans/active/2026-02-24-position-tpsl-ddd-solid-facade-refactor.md`.
- [x] (2026-02-24 23:40Z) Added characterization tests for deterministic facade behavior and anchor normalization in `/hyperopen/test/hyperopen/account/history/position_tpsl_test.cljs`.
- [x] (2026-02-24 23:42Z) Extracted position identity and modal state primitives into `/hyperopen/src/hyperopen/account/history/position_identity.cljs` and `/hyperopen/src/hyperopen/account/history/position_tpsl_state.cljs`.
- [x] (2026-02-24 23:42Z) Extracted pure TP/SL policy and transition logic into `/hyperopen/src/hyperopen/account/history/position_tpsl_policy.cljs` and `/hyperopen/src/hyperopen/account/history/position_tpsl_transitions.cljs`.
- [x] (2026-02-24 23:42Z) Extracted submit preparation and row hydration orchestration into `/hyperopen/src/hyperopen/account/history/position_tpsl_application.cljs`.
- [x] (2026-02-24 23:42Z) Converted `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` into a compatibility facade that re-exports the existing API surface.
- [x] (2026-02-24 23:44Z) Ran full required validation and compile gates: `npx shadow-cljs compile app`, `npx shadow-cljs compile test`, `npm run check`, `npm test`, `npm run test:websocket`.
- [x] (2026-02-24 23:44Z) Scored completion confidence at `92.6%` (Testing `95`, Code review `90`, Logical inspection `92`) using the required 40/30/30 weighting.

## Surprises & Discoveries

- Observation: `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` is 760 LOC, exceeding the architecture guidance target for bounded namespace complexity.
  Evidence: `wc -l src/hyperopen/account/history/position_tpsl.cljs` reported `760`.

- Observation: The TP/SL module currently depends on a views projection namespace for position identity, which inverts expected dependency direction for domain behavior.
  Evidence: `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` requires `hyperopen.views.account-info.projections` only to call `position-unique-key`.

- Observation: `set-modal-field` is currently a large path-based conditional, making extension high-risk and violating single-responsibility boundaries.
  Evidence: `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` function `set-modal-field` spans multiple behavior families (size sync, PnL conversion, mode switching, generic field updates).

- Observation: Submit preparation repeats work by calling `submit-form` twice and combines validation, market lookup, ACL shaping, and request creation in one place.
  Evidence: `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` `prepare-submit` calls `(submit-form modal)` for both side and payload while also handling market/asset resolution and request formatting.

- Observation: Running multiple `npm test` commands in parallel against the shared shadow build output can create non-deterministic runtime parse failures unrelated to behavior changes.
  Evidence: parallel command execution produced `SyntaxError: Unexpected token '}'` from `.shadow-cljs/builds/test/dev/out/cljs-runtime/replicant.core.js`, while sequential reruns passed consistently.

## Decision Log

- Decision: Preserve the existing public function surface in `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` (`default-modal-state`, `open?`, `validate-modal`, `prepare-submit`, `from-position-row`, `set-modal-field`, estimate helpers).
  Rationale: Actions and views currently call this namespace directly; compatibility-first refactor reduces rollout risk and avoids broad runtime registry changes.
  Date/Author: 2026-02-24 / Codex

- Decision: Split responsibilities into `state`, `policy`, `transitions`, and `application` namespaces under `/hyperopen/src/hyperopen/account/history/`.
  Rationale: This mirrors existing successful decomposition patterns used by order-form modules and aligns with DDD layering and SRP.
  Date/Author: 2026-02-24 / Codex

- Decision: Move position identity ownership into account-history/domain-facing code and keep view projection helpers as adapters, not domain dependencies.
  Rationale: DDD boundary rule requires domain decisions to avoid depending on view-layer namespaces.
  Date/Author: 2026-02-24 / Codex

- Decision: Introduce an explicit internal command map for modal field transitions while keeping external `path` action contract stable.
  Rationale: This enables open-closed extension without changing `:actions/set-position-tpsl-modal-field` schema.
  Date/Author: 2026-02-24 / Codex

- Decision: Keep an adapter function in `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs` that delegates `position-unique-key` to `/hyperopen/src/hyperopen/account/history/position_identity.cljs`.
  Rationale: This removes domain-to-view dependency while preserving existing projection exports and call sites.
  Date/Author: 2026-02-24 / Codex

- Decision: Do not add a new ADR for this change.
  Rationale: The refactor is internal extraction behind a stable facade with no public runtime seam or contract change; existing architecture docs and this ExecPlan capture the boundary ownership shift sufficiently.
  Date/Author: 2026-02-24 / Codex

## Outcomes & Retrospective

Implemented as planned. TP/SL behavior remains unchanged for users, while code ownership is now split across dedicated `state`, `policy`, `transitions`, and `application` namespaces with the original facade preserved for compatibility.

Validation evidence is complete: compile gates and required gates all passed (`npx shadow-cljs compile app`, `npx shadow-cljs compile test`, `npm run check`, `npm test`, `npm run test:websocket`).

Refactor completion confidence exceeded the required threshold:

- Testing score: 95/100 (40% weight -> 38.0)
- Code review score: 90/100 (30% weight -> 27.0)
- Logical inspection score: 92/100 (30% weight -> 27.6)
- Weighted confidence: 92.6% (required >= 84.7%)

Residual risk is low and concentrated in future divergence between facade exports and internal module contracts; current characterization tests reduce this risk.

## Context and Orientation

The current TP/SL implementation lives in `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` and currently combines six concerns in one file: modal defaults/state normalization, position-row hydration, validation rules, PnL/size calculations, input transition handling, and submit request building.

Callers rely on this namespace as a stable facade. State transitions are invoked by `/hyperopen/src/hyperopen/account/history/actions.cljs`, rendering reads happen in `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`, and request lifecycle effects resolve in `/hyperopen/src/hyperopen/order/effects.cljs`.

In this repository, "domain policy" means pure business rules that are deterministic and side-effect free. "Application orchestration" means logic that composes pure policy with injected collaborators at boundaries (for example market lookup and order command builders). "Facade" means a thin compatibility namespace that preserves existing public APIs and delegates internally.

This refactor must keep UI responsiveness and effect ordering unchanged. The existing action flow already writes modal state through `:effects/save` or `:effects/save-many`; this flow must remain deterministic and avoid duplicate submit effects.

## Plan of Work

Milestone 1 establishes a safety net first. Add characterization tests in existing TP/SL tests that lock the current facade outputs for key flows: modal hydration from a row, validation labels and failures, command path updates, estimate outputs, and submit preparation request shape. Add deterministic checks where the same input modal/state yields the same output map. This milestone follows Red -> Green -> Refactor by introducing at least one failing regression test before behavior-preserving extraction.

Milestone 2 extracts identity and state ownership boundaries. Create `/hyperopen/src/hyperopen/account/history/position_identity.cljs` with `position-unique-key`. Update `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs` to delegate to that function so existing projection API remains stable. Create `/hyperopen/src/hyperopen/account/history/position_tpsl_state.cljs` for default modal shape and normalization helpers (`open?`, anchor normalization, mode normalization). Keep this module pure and side-effect free.

Milestone 3 extracts domain policy and transitions. Create `/hyperopen/src/hyperopen/account/history/position_tpsl_policy.cljs` for parsed inputs, validation rules, size/percent conversions, pnl/price conversions, and estimate functions. Create `/hyperopen/src/hyperopen/account/history/position_tpsl_transitions.cljs` for modal update commands. Convert path-driven branching into explicit internal command maps while preserving external path contract accepted by `set-modal-field`. Ensure all transition functions return updated modal maps only.

Milestone 4 extracts application orchestration. Create `/hyperopen/src/hyperopen/account/history/position_tpsl_application.cljs` to implement `prepare-submit` with injected collaborators (`resolve-market`, `resolve-asset-id`, `build-tpsl-orders`). Keep default collaborator wiring in the facade to current modules (`hyperopen.asset-selector.markets` and `hyperopen.api.gateway.orders.commands`). Maintain exact response shapes (`{:ok? ... :display-message ...}` and request payload structure) to protect runtime/action contracts.

Milestone 5 converts `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` into a compatibility facade. It should require the new modules, expose the same public vars, and keep behavior identical. This file should become small enough to be navigable and limited to boundary delegation plus minimal adapters.

Milestone 6 completes verification and governance. Run targeted tests, compile gates, and required repository gates. Evaluate whether an ADR entry is required; if the team treats this extraction as architecture-affecting, add a new ADR under `/hyperopen/docs/architecture-decision-records/` summarizing new TP/SL module boundaries and invariant ownership.

## Concrete Steps

1. Add characterization tests before extraction.

   cd /hyperopen
   npm test -- hyperopen.account.history.position-tpsl-test
   npm test -- hyperopen.account.history.actions-test
   npm test -- hyperopen.views.account-info.position-tpsl-modal-test

   Expected result: new regression cases fail first when introduced, then pass once baseline behavior is captured without changing semantics.

2. Extract identity and state namespaces, then run focused tests.

   cd /hyperopen
   npm test -- hyperopen.account.history.position-tpsl-test
   npm test -- hyperopen.views.account-info.position-tpsl-modal-test

   Expected result: no output shape changes in facade-level tests; view tests remain green.

3. Extract policy/transitions/application and wire facade delegation.

   cd /hyperopen
   npm test -- hyperopen.account.history.position-tpsl-test
   npm test -- hyperopen.account.history.actions-test
   npm test -- hyperopen.order.effects-test

   Expected result: submit and state transition behavior remains unchanged.

4. Run compile and full validation gates.

   cd /hyperopen
   npx shadow-cljs compile app
   npx shadow-cljs compile test
   npm run check
   npm test
   npm run test:websocket

   Expected result: all commands exit with status 0.

5. Record completion confidence score with evidence.

   Score formula:

   confidence = (testing_score * 0.40) + (code_review_score * 0.30) + (logical_inspection_score * 0.30)

   Expected result: confidence >= 84.7%; otherwise record gaps and required next checks.

## Validation and Acceptance

Acceptance requires all of the following observable outcomes:

1. Public TP/SL facade API in `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs` remains callable by existing actions/views/tests with no call-site changes.
2. `set-modal-field`, `set-configure-amount`, and `set-limit-price` produce the same modal transition outcomes for existing covered scenarios.
3. `validate-modal` and `prepare-submit` preserve current message texts and request shapes for valid and invalid flows.
4. TP/SL estimate helpers (`estimated-gain-usd`, `estimated-loss-usd`, `estimated-gain-percent`, `estimated-loss-percent`) remain behaviorally identical for existing fixtures.
5. Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.
6. Compile gates pass: `npx shadow-cljs compile app`, `npx shadow-cljs compile test`.
7. Refactor completion confidence is documented and >= 84.7% using required weighting.

## Idempotence and Recovery

The refactor is designed as additive extraction behind a stable facade. Each milestone can be rerun safely because exported APIs stay stable and tests remain the checkpoint after each extraction.

If a milestone causes regressions, recovery is to keep the facade in place and route affected function(s) back to legacy implementations temporarily while preserving new modules and tests. This allows incremental rollback at function granularity instead of reverting the full refactor.

No destructive data migrations are involved. The state shape under `:positions-ui :tpsl-modal` remains compatible.

## Artifacts and Notes

Primary files to create:

- `/hyperopen/src/hyperopen/account/history/position_identity.cljs`
- `/hyperopen/src/hyperopen/account/history/position_tpsl_state.cljs`
- `/hyperopen/src/hyperopen/account/history/position_tpsl_policy.cljs`
- `/hyperopen/src/hyperopen/account/history/position_tpsl_transitions.cljs`
- `/hyperopen/src/hyperopen/account/history/position_tpsl_application.cljs`

Primary files to modify:

- `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs`
- `/hyperopen/src/hyperopen/account/history/actions.cljs` (only if adapter wiring needs minor updates)
- `/hyperopen/src/hyperopen/views/account_info/projections/balances.cljs`
- `/hyperopen/test/hyperopen/account/history/position_tpsl_test.cljs`
- `/hyperopen/test/hyperopen/account/history/actions_test.cljs`
- `/hyperopen/test/hyperopen/views/account_info/position_tpsl_modal_test.cljs`

Optional governance artifact (if required by review outcome):

- `/hyperopen/docs/architecture-decision-records/00xx-position-tpsl-module-boundary-extraction.md`

Plan revision note: 2026-02-24 23:37Z - Initial plan authored from DDD/SOLID refactor recommendations.
Plan revision note: 2026-02-24 23:44Z - Updated all living sections with implementation progress, validation evidence, confidence scoring, and rationale for no ADR.

## Interfaces and Dependencies

Stable facade interfaces that must remain available from `/hyperopen/src/hyperopen/account/history/position_tpsl.cljs`:

- `(default-modal-state)`
- `(open? modal)`
- `(tp-gain-mode modal)`
- `(sl-loss-mode modal)`
- `(preview-submit-label modal)`
- `(validate-modal modal)`
- `(prepare-submit state modal)`
- `(from-position-row position-data)` and `(from-position-row position-data anchor)`
- `(set-modal-field modal path value)`
- `(set-configure-amount modal checked)`
- `(set-limit-price modal checked)`
- `(active-size modal)`
- `(configured-size-percent modal)`
- `(estimated-gain-usd modal)`
- `(estimated-loss-usd modal)`
- `(estimated-gain-percent modal)`
- `(estimated-loss-percent modal)`
- `(valid-size? modal)`

Expected module responsibilities after extraction:

- `position_tpsl_state`: modal defaults and normalization helpers.
- `position_tpsl_policy`: pure validation and quantitative policy.
- `position_tpsl_transitions`: pure modal transition functions from normalized commands.
- `position_tpsl_application`: submit orchestration with injected collaborators.
- `position_tpsl` facade: compatibility exports and boundary wiring.

No new external dependencies are added.
