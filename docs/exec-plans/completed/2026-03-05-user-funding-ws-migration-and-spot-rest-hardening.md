# User Funding WS Migration and Spot Clearinghouse REST Hardening

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

Reduce `/info` request pressure from Ghost Mode and active account sessions by moving `userFunding` refresh behavior to websocket-first (`userFundings`) and hardening remaining non-subscribable spot account refreshes (`spotClearinghouseState`) with strict TTL/dedupe/gating.

Expected user-visible outcome: lower `/info` request volume and fewer 429 rate-limit events while preserving Funding History correctness and spot balance freshness.

## Progress

- [x] (2026-03-05 16:40Z) Confirmed via docs + manual sampling that `userFundings` websocket subscription exists and `userFunding` REST still dominates request mix on funding-history workflow.
- [x] (2026-03-05 16:41Z) Confirmed `spotClearinghouseState` has no documented direct websocket parity endpoint.
- [x] (2026-03-05 17:05Z) Added websocket-first funding-history hydration and delayed fallback logic to prefer live `userFundings` stream data before REST.
- [x] (2026-03-05 17:05Z) Suppressed repeated `userFunding` REST fanout in steady-state websocket operation with stream-health + hydration gating.
- [x] (2026-03-05 17:05Z) Kept bounded REST backfill for cold start/recovery with delayed fallback and existing request-policy/dedupe controls.
- [x] (2026-03-05 18:04Z) Hardened `spotClearinghouseState` fallback cadence with stricter TTL (`15000ms`) plus route/surface gating in post-fill refresh path.
- [x] (2026-03-05 18:04Z) Added deterministic regression coverage for spot-surface gating and policy behavior; required gates passed.
- [x] (2026-03-05 18:11Z) Executed manual Ghost Mode 120s sampling windows and published QA report.

## Surprises & Discoveries

- Manual sampling on 2026-03-05 with Funding History tab active showed `userFunding` as dominant `/info` type.
- Control sample without funding tab trigger showed `userFunding` absent, indicating tab/workflow-triggered burst behavior.
- Post-fix cold-start sampling still showed one-time `userFunding` pagination fanout when funding workflow is forced immediately after reload; steady-state windows converged to zero `/info` churn in sampled scenario.

## Decision Log

- Decision: Prioritize `userFunding` WS-first migration before deeper spot changes.
  Rationale: It is the highest observed request contributor in the measured workflow and has direct websocket parity (`userFundings`).
  Date/Author: 2026-03-05 / Codex

- Decision: Treat `spotClearinghouseState` as non-subscribable until direct WS parity is documented; optimize REST behavior instead.
  Rationale: Current websocket docs list no direct `spotClearinghouseState` subscription type.
  Date/Author: 2026-03-05 / Codex

## Outcomes & Retrospective

Completed with bounded fallback behavior and manual QA evidence.

- `spotClearinghouseState` cadence reduced from `18` requests/`120s` pre-fix to `0` requests/`120s` in steady-state post-fix Ghost Mode sampling.
- No sampled rate-limit events were observed.
- Evidence report: `/hyperopen/docs/qa/user-funding-spot-clearinghouse-ghost-mode-sampling-2026-03-05.md`.

## Plan of Work

1. Funding history WS-first seam
   - Route Funding History hydration through `userFundings` stream state first.
   - Accept initial snapshot (`isSnapshot: true`) as canonical baseline.
   - Merge incremental updates (`isSnapshot: false`) without duplicate fanout.

2. Funding REST fallback control
   - Only issue `userFunding` POST when funding stream is unavailable/degraded or cold-start cache is missing.
   - Add explicit staleness TTL and single-flight dedupe for funding-history requests.

3. Spot clearinghouse non-WS hardening
   - Keep existing REST path but enforce central request-policy TTL + dedupe.
   - Ensure route/tab/activity gates prevent background churn when inactive.

4. Observability and safety
   - Add type+source request telemetry assertions for `userFunding` and `spotClearinghouseState`.
   - Add rollout flag hooks if behavior shift is material (default on in dev, guarded in prod).

5. Validation
   - Required gates: `npm run test:websocket`, `npm test`, `npm run check`.
   - Manual QA in Ghost Mode using active address over 120-second windows.
   - Publish before/after counts by type and rate-limit events.

## Acceptance Criteria

1. Funding History workflow no longer relies on repeated `userFunding` POST fanout when `userFundings` stream is live and cache is warm.
2. REST fallback remains correct and bounded for reconnect/cold-start/recovery scenarios.
3. `spotClearinghouseState` request cadence is bounded via TTL/dedupe/gating under active account churn.
4. Regression and websocket tests cover WS-first, fallback, and health-degrade behavior.
5. Manual QA report shows reduced `userFunding` POST volume and improved rate-limit profile versus baseline.

## Artifacts and Notes

- Baseline manual sampling session (2026-03-05): `sess-1772728064625-628417`
- Baseline observed dominant types during funding workflow: `userFunding`, `spotClearinghouseState`
