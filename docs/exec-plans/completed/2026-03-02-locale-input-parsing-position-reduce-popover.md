# Locale-Aware Input Parsing for Position Reduce Popover

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

Position reduce popover input handling still assumed dot-decimal parsing for user-entered percent and limit price values. After this change, users in locales like `fr-FR` can enter values like `"25,5"` and `"11,5"` and still get the expected close-size and limit-price submit behavior.

A user can verify this by setting `[:ui :locale]` to `"fr-FR"`, opening position reduce, entering `25,5%` and `11,5` limit price, and seeing a valid order submit payload.

## Progress

- [x] (2026-03-02 17:04Z) Audited reduce-popover parsing and identified remaining locale gap in `/hyperopen/src/hyperopen/account/history/position_reduce.cljs` and locale threading gap in `/hyperopen/src/hyperopen/account/history/actions.cljs`.
- [x] (2026-03-02 17:05Z) Implemented locale-aware parsing for reduce percent and limit-price paths and added `:locale` to reduce popover state.
- [x] (2026-03-02 17:06Z) Updated actions open/update/submit wrappers to preserve `[:ui :locale]` in reduce popover state.
- [x] (2026-03-02 17:06Z) Added/updated tests in `/hyperopen/test/hyperopen/account/history/actions_test.cljs` for locale propagation and localized submit behavior.
- [x] (2026-03-02 17:07Z) Ran required gates successfully (`npm test`, `npm run check`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: Reduce popover domain logic already centralizes percent and limit-price parsing, so adding locale-awareness at that boundary covered both validation and submit-form generation.
  Evidence: `configured-size-percent`, `validate-popover`, and `submit-price` in `/hyperopen/src/hyperopen/account/history/position_reduce.cljs` now share `parse-popover-num`.

## Decision Log

- Decision: Keep locale on popover state and thread it from action wrappers, matching TP/SL and margin modal strategy.
  Rationale: This keeps parse behavior deterministic and avoids coupling parsing helpers to global app state reads.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

This tranche closes the reduce-popover locale parsing gap for user-entered numeric inputs. Position reduce now accepts decimal comma input for both size percent and limit price in locales that use comma decimals. Existing behavior for default popover/reset flows was preserved.

## Context and Orientation

Position reduce popover data and submit policy are implemented in `/hyperopen/src/hyperopen/account/history/position_reduce.cljs`. Account history actions in `/hyperopen/src/hyperopen/account/history/actions.cljs` control popover open/update/submit effects and are the canonical place to carry locale into UI modal/popover state.

## Plan of Work

Add a locale-aware numeric parse helper in reduce-popover domain logic. Route percent and limit-price parse points through that helper. Thread locale from app state into reduce-popover state in all open/update/submit actions. Extend account-history actions tests to prove locale propagation and localized decimal parsing in submit payloads.

## Concrete Steps

From `/hyperopen`:

1. Update `/hyperopen/src/hyperopen/account/history/position_reduce.cljs` to use `hyperopen.utils.parse/parse-localized-decimal` with popover locale for percent/limit parse paths.
2. Update `/hyperopen/src/hyperopen/account/history/actions.cljs` reduce-popover actions to carry `:locale (get-in state [:ui :locale])`.
3. Add tests to `/hyperopen/test/hyperopen/account/history/actions_test.cljs` for locale propagation and `fr-FR` parsing behavior.
4. Run required validation gates:
   - `npm test`
   - `npm run check`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

1. Reduce popover stores locale when opened/updated under localized UI state.
2. `fr-FR` decimal-comma input for size percent and limit price produces valid parsed submit payloads.
3. Required repository validation gates pass.

## Idempotence and Recovery

The changes are additive and idempotent. If regressions appear, fallback is to keep locale threading while narrowing localized parse calls to only percent input first, then restore limit-price parsing once validated.

## Artifacts and Notes

Validation commands run:

    npm test
    npm run check
    npm run test:websocket

## Interfaces and Dependencies

No new dependencies were added. This work reuses `/hyperopen/src/hyperopen/utils/parse.cljs` for locale-aware decimal normalization/parsing and existing position reduce action contracts.

Plan revision note: 2026-03-02 17:07Z - Initial and final completed plan for locale-aware reduce-popover parsing tranche.
