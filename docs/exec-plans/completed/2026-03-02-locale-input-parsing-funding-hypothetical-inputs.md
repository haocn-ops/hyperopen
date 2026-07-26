# Locale-Aware Parsing for Funding Hypothetical Inputs

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

Funding hypothetical position inputs in the active-asset funding tooltip previously normalized commas away and assumed dot-decimal input, which breaks decimal-comma locales. After this change, users with locales like `fr-FR` can enter values such as `0,02` and `-1250,0` and get correct derived size/value projections.

A user can verify this by setting `[:ui :locale]` to `"fr-FR"`, typing decimal-comma values into hypothetical size/value fields, and observing the same funding projection outputs expected from equivalent dot-decimal values.

## Progress

- [x] (2026-03-02 20:23Z) Audited remaining locale parsing boundaries and selected funding hypothetical input path in `/hyperopen/src/hyperopen/asset_selector/actions.cljs` and `/hyperopen/src/hyperopen/views/active_asset_view.cljs`.
- [x] (2026-03-02 20:24Z) Replaced comma-stripping parse logic with locale-aware decimal parsing using `/hyperopen/src/hyperopen/utils/parse.cljs` while preserving raw input display text.
- [x] (2026-03-02 20:25Z) Threaded locale from full app state into tooltip hypothetical modeling.
- [x] (2026-03-02 20:26Z) Added test coverage in `/hyperopen/test/hyperopen/asset_selector/actions_test.cljs` and `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` for fr-FR decimal-comma behavior.
- [x] (2026-03-02 20:28Z) Ran required gates successfully (`npm test`, `npm run check`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: Funding hypothetical actions and tooltip model each had their own decimal normalization/parsing helper, creating duplicate locale-risky behavior.
  Evidence: Both modules independently removed commas before parsing (`normalize-decimal-input`), so decimal-comma input would be interpreted as a larger integer.

## Decision Log

- Decision: Preserve user-entered display text (except stripping `$` in value field) and parse locale-aware for calculations only.
  Rationale: This keeps input UX stable while making computed size/value projections locale-correct.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

This tranche removed a user-facing locale parsing regression in funding hypothetical inputs and aligned both action-level state updates and tooltip-level model projection parsing with the shared locale parsing utility. Locale-aware decimal input now works in these flows without introducing new dependencies.

## Context and Orientation

Funding hypothetical state writes occur in `/hyperopen/src/hyperopen/asset_selector/actions.cljs` via `set-funding-hypothetical-size` and `set-funding-hypothetical-value`. Projection reads for the tooltip are modeled in `/hyperopen/src/hyperopen/views/active_asset_view.cljs` via `hypothetical-position-model` and `funding-tooltip-model`.

## Plan of Work

Use `hyperopen.utils.parse/parse-localized-decimal` in both write-time and read-time hypothetical parsing paths. Keep stored text user-facing, and carry locale into model derivation from app state.

## Concrete Steps

From `/hyperopen`:

1. Update `/hyperopen/src/hyperopen/asset_selector/actions.cljs` to parse hypothetical input with locale-aware decimal parser and locale from state.
2. Update `/hyperopen/src/hyperopen/views/active_asset_view.cljs` to parse hypothetical input model fields with locale-aware parser and locale from full state.
3. Add tests for fr-FR decimal-comma inputs in actions and view tooltip tests.
4. Run required validation gates:
   - `npm test`
   - `npm run check`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

1. `set-funding-hypothetical-size` and `set-funding-hypothetical-value` parse decimal-comma values correctly under locale `fr-FR`.
2. Funding tooltip hypothetical model treats stored decimal-comma values as numeric decimals (not comma-stripped integers).
3. Required repository validation gates pass.

## Idempotence and Recovery

The change is additive and can be rerun safely. If regressions appear, keep locale threading in place and narrow parser replacement to action handlers first, then re-enable view-model locale parsing once verified.

## Artifacts and Notes

Validation commands run:

    npm test
    npm run check
    npm run test:websocket

## Interfaces and Dependencies

No external dependencies were added. This work reuses `/hyperopen/src/hyperopen/utils/parse.cljs` as the canonical locale-aware decimal parser.

Plan revision note: 2026-03-02 20:28Z - Completed funding hypothetical locale parsing tranche with tests and full gate validation.
