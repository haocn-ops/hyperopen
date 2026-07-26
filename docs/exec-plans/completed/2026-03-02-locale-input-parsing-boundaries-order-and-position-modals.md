# Locale-Aware Input Parsing for Order Form and Position Modals

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

Hyperopen now has locale-aware display formatting, but key user input boundaries still assume dot-decimal parsing. After this change, users on locales like `fr-FR` can enter decimal commas in the order form and position margin/TP-SL modals, and those inputs are parsed correctly for validation and submit-request construction.

A user can verify this by setting `[:ui :locale]` to `"fr-FR"`, entering values like `"2,5"` and `"101,5"` in those flows, and observing the same resulting order/margin behavior as equivalent dot-decimal inputs.

## Progress

- [x] (2026-03-02 16:56Z) Created active ExecPlan for follow-on locale-aware input parsing tranche.
- [x] (2026-03-02 16:56Z) Audited remaining input parsing boundaries and identified implementation targets: `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`, `/hyperopen/src/hyperopen/account/history/position_margin.cljs`, `/hyperopen/src/hyperopen/account/history/position_tpsl_policy.cljs`, `/hyperopen/src/hyperopen/account/history/position_tpsl_transitions.cljs`, and account-history action open/update wrappers.
- [x] (2026-03-02 16:58Z) Implemented locale-aware parsing for order-form numeric transitions while preserving raw `:size-display` text semantics.
- [x] (2026-03-02 16:59Z) Implemented locale-aware parsing for position margin and position TP/SL modal numeric inputs, including locale propagation on modal open/update actions.
- [x] (2026-03-02 17:00Z) Added focused fr-FR decimal-comma coverage in order-form transitions, position margin, TP/SL policy/transitions, and account-history actions tests.
- [x] (2026-03-02 17:02Z) Ran required gates (`npm test`, `npm run check`, `npm run test:websocket`) successfully and prepared plan for completion move.

## Surprises & Discoveries

- Observation: Locale-aware parsing utility already exists (`/hyperopen/src/hyperopen/utils/parse.cljs`) and can be reused directly rather than introducing a new parsing module.
  Evidence: Existing functions `normalize-localized-decimal-input` and `parse-localized-decimal` are already used in vault transfer parsing.
- Observation: Parsing helpers in position modal domain code did not have direct access to UI locale unless explicitly carried on modal state from action wrappers.
  Evidence: `from-position-row` and `open-position-tpsl-modal` required explicit `:locale (get-in state [:ui :locale])` threading to make downstream parse helpers deterministic.

## Decision Log

- Decision: Keep order-form raw typed text where UI semantics depend on preserving user display input (for example `:size-display`), while parsing locale-aware values for canonical calculations.
  Rationale: This preserves current UX expectations and existing tests while enabling international decimal input behavior.
  Date/Author: 2026-03-02 / Codex
- Decision: Thread locale through position modal state at open/update boundaries rather than reading directly from global state in deep policy/transitions functions.
  Rationale: Modal-local locale keeps parse behavior pure/deterministic and avoids widening deep function signatures to carry full app state.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

Completed locale-aware parsing migration for this tranche. Order-form numeric computation paths, position margin amount/percent parsing, and position TP/SL size/price/PNL parsing now accept localized decimal input. Focused fr-FR tests were added in all touched domains and all required validation gates passed (`npm test`, `npm run check`, `npm run test:websocket`).

Remaining broader migration work (if additional locale-sensitive parse boundaries are discovered) should continue through `/hyperopen/docs/exec-plans/tech-debt-tracker.md`.

## Context and Orientation

The canonical order-form transition logic is in `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`. This module receives user input strings from command handlers and decides how those strings update canonical order-form fields, display fields, and runtime flags.

Position margin modal logic is in `/hyperopen/src/hyperopen/account/history/position_margin.cljs`, and TP/SL modal parsing logic is split between `/hyperopen/src/hyperopen/account/history/position_tpsl_policy.cljs` and `/hyperopen/src/hyperopen/account/history/position_tpsl_transitions.cljs`. Account-history action wrappers in `/hyperopen/src/hyperopen/account/history/actions.cljs` own modal opening and field updates and are the cleanest place to preserve locale context on modal state.

## Plan of Work

The implementation will add locale-aware numeric parsing at input boundaries only. Order-form transitions will continue to own canonical form projection, but numeric string reads will pass through locale-aware parsing before calculations. Position modal parsing helpers will read a modal-local locale token and parse user-entered amount/percent/trigger values with locale awareness. Existing behavior for invalid input, blank input, and deterministic fallback messages will be preserved.

## Concrete Steps

From `/hyperopen`:

1. Update targeted input transition modules to call `hyperopen.utils.parse/parse-localized-decimal` or `normalize-localized-decimal-input` with locale from `state` or modal context.
2. Ensure modal open/update actions preserve locale in modal state.
3. Add focused tests in existing transition and account-history test namespaces for comma-decimal behavior.
4. Run required gates:
   - `npm test`
   - `npm run check`
   - `npm run test:websocket`
5. Update this plan sections with concrete evidence and move to completed.

## Validation and Acceptance

Acceptance criteria:

1. In order-form transitions, locale `fr-FR` accepts decimal comma for numeric inputs used in calculations.
2. Position margin and TP/SL modal calculations accept decimal comma for user-entered numeric fields under locale `fr-FR`.
3. Existing behavior for blank/invalid input remains fail-closed and deterministic.
4. Required repository validation gates pass.

## Idempotence and Recovery

All edits are additive and can be safely re-run. If locale parsing introduces regressions, fallback is to keep raw input storage unchanged and narrow locale-aware parsing to only canonical computation points while preserving previous validation paths.

## Artifacts and Notes

Initial discovery commands:

    rg -n "parseFloat|set-order-size-display|update-order-form|position-margin|position-tpsl" src/hyperopen test/hyperopen

## Interfaces and Dependencies

No external dependencies are added. This work reuses:

- `/hyperopen/src/hyperopen/utils/parse.cljs` for locale-aware normalization/parsing.
- Existing order-form transition contracts in `/hyperopen/src/hyperopen/trading/order_form_transitions.cljs`.
- Existing position modal contracts in `/hyperopen/src/hyperopen/account/history/position_margin.cljs` and `/hyperopen/src/hyperopen/account/history/position_tpsl_*.cljs`.

Plan revision note: 2026-03-02 16:56Z - Initial plan authored for post-migration locale-aware input boundary rollout.
Plan revision note: 2026-03-02 17:02Z - Marked implementation/testing complete, captured final decisions/discoveries, and prepared for move to completed plans.
