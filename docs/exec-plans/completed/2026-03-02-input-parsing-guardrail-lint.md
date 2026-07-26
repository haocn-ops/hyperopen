# Input Parsing Guardrail Lint for Locale Safety

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md` and must be maintained in accordance with that file.

## Purpose / Big Picture

Locale-aware parsing migration reduced functional gaps, but regressions could still be reintroduced by future direct `js/parseFloat` usage in input-boundary code. This tranche adds an automated lint guardrail so CI/check runs fail if forbidden parsing patterns reappear in critical user-input boundary namespaces.

A user can verify this by running `npm run lint:input-parsing` (or `npm run check`) and observing pass/fail output tied to concrete file/line violations.

## Progress

- [x] (2026-03-02 21:52Z) Audited current input-boundary namespaces and selected guarded files for lint enforcement.
- [x] (2026-03-02 21:54Z) Removed residual direct `js/parseFloat` usage from guarded boundary files:
  - `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
  - `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
- [x] (2026-03-02 21:55Z) Implemented new lint tool `/hyperopen/dev/check_input_numeric_parsing.clj`.
- [x] (2026-03-02 21:56Z) Added npm script `lint:input-parsing` and integrated it into `npm run check` in `/hyperopen/package.json`.
- [x] (2026-03-02 21:58Z) Ran required gates successfully (`npm test`, `npm run check`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: A small number of boundary files still had non-critical direct parse calls, and replacing them with shared parser utilities was straightforward without broader behavior drift.
  Evidence: Two targeted callsites switched to `hyperopen.utils.parse` helpers before enabling lint enforcement.

## Decision Log

- Decision: Scope guardrail lint to curated boundary files rather than globally banning `js/parseFloat` in the repository.
  Rationale: Many non-input data normalization paths legitimately parse protocol/feed payloads; global bans would create noise and reduce lint signal quality.
  Date/Author: 2026-03-02 / Codex

## Outcomes & Retrospective

Input parsing safety now has executable enforcement in CI/check workflows. This reduces the chance that future UI input features bypass locale-aware parsing utilities and regress international behavior.

## Context and Orientation

Primary files:

- `/hyperopen/dev/check_input_numeric_parsing.clj`
- `/hyperopen/package.json`
- `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
- `/hyperopen/src/hyperopen/views/active_asset_view.cljs`

## Plan of Work

Create a focused lint command that scans guarded user-input boundary files for forbidden direct parsing patterns and fails with file/line diagnostics. Integrate the command into `npm run check` so enforcement is automatic.

## Concrete Steps

From `/hyperopen`:

1. Add lint script implementation in `dev/`.
2. Clean existing guardrail violations in selected files.
3. Wire script into npm check pipeline.
4. Run required gates.

## Validation and Acceptance

Acceptance criteria:

1. `npm run lint:input-parsing` passes on current branch.
2. `npm run check` includes and executes the new lint.
3. Guarded boundary files are free from direct forbidden parse patterns.
4. Required repository validation gates pass.

## Idempotence and Recovery

The lint is deterministic and additive. If future false positives appear, adjust guarded file list/rules in one place while preserving parsing utility policy intent.

## Artifacts and Notes

Validation commands run:

    npm test
    npm run check
    npm run test:websocket

## Interfaces and Dependencies

No external dependencies were added. The lint is a babashka script integrated into existing npm check flow.
