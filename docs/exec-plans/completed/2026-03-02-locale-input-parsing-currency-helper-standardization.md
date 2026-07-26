# Locale Input Parsing Currency Helper Standardization

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

Locale-aware parsing support existed, but several modules still owned duplicated currency-input parsing helpers. This change consolidates those callsites onto one canonical parse utility so currency-like decimal text is interpreted consistently and future feature work has a single extension point.

A user can verify this by entering localized currency-like decimal inputs in existing flows (funding hypothetical and TP/SL USD fields) and observing unchanged behavior, while code ownership now routes through a shared utility.

## Progress

- [x] (2026-03-02 21:18Z) Audited duplicated currency-input parsing helpers in `/hyperopen/src/hyperopen/asset_selector/actions.cljs`, `/hyperopen/src/hyperopen/views/active_asset_view.cljs`, and `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`.
- [x] (2026-03-02 21:20Z) Added canonical utility helpers in `/hyperopen/src/hyperopen/utils/parse.cljs`:
  - `sanitize-currency-decimal-input`
  - `parse-localized-currency-decimal`
- [x] (2026-03-02 21:22Z) Migrated duplicated parsing callsites to the shared helper in asset selector, active-asset view, and TP/SL modal.
- [x] (2026-03-02 21:23Z) Added parser utility tests in `/hyperopen/test/hyperopen/utils/parse_test.cljs` for localized currency-like decimal input and fallback compatibility.
- [x] (2026-03-02 21:25Z) Ran required validation gates successfully (`npm test`, `npm run check`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: Existing duplicated helpers already had equivalent fallback behavior (locale parse first, then permissive numeric parse), which made the consolidation low-risk and mostly mechanical.
  Evidence: Both funding hypothetical and active-asset tooltip parsing previously used nearly identical normalize + locale parse + finite number fallback logic.

## Decision Log

- Decision: Keep compatibility fallback in the canonical helper (`parse-localized-currency-decimal`) instead of strict locale-only parsing.
  Rationale: Current UX accepts certain non-locale numeric forms (for example scientific notation via `js/Number`), and this standardization should not narrow accepted input formats unexpectedly.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

Currency-like input parsing logic is now centralized in `hyperopen.utils.parse`, reducing duplication and making locale parsing policy easier to evolve safely. Existing behavior in migrated callsites remains stable while ownership is now explicit and canonical.

## Context and Orientation

Primary files:

- `/hyperopen/src/hyperopen/utils/parse.cljs`
- `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
- `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
- `/hyperopen/src/hyperopen/views/account_info/position_tpsl_modal.cljs`
- `/hyperopen/test/hyperopen/utils/parse_test.cljs`

## Plan of Work

Create shared parse helper for currency-like localized decimal input. Replace per-module duplicate implementations with shared utility calls. Add focused utility-level tests and run full required validation gates.

## Concrete Steps

From `/hyperopen`:

1. Add canonical parse helper APIs in `hyperopen.utils.parse`.
2. Replace duplicated parser logic in identified modules.
3. Add helper-level regression tests.
4. Run required gates:
   - `npm test`
   - `npm run check`
   - `npm run test:websocket`

## Validation and Acceptance

Acceptance criteria:

1. Duplicated currency-like parse helper logic is removed from migrated modules.
2. Shared helper correctly parses localized decimal strings and `$`-prefixed values.
3. Compatibility fallback behavior remains available for existing accepted numeric forms.
4. Required repository gates pass.

## Idempotence and Recovery

This refactor is additive and idempotent. If regressions appear, rollback can be limited to callsite-level helper substitution while retaining new parse helper tests.

## Artifacts and Notes

Validation commands run:

    npm test
    npm run check
    npm run test:websocket

## Interfaces and Dependencies

No external dependencies were added. This work extends existing parsing utilities and preserves existing module contracts.
