# Build `/staking` With Hyperliquid-Parity Data and Core Workflows

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. The active tracked issue for this work is `hyperopen-r5n0` in `bd`.

## Purpose / Big Picture

After this change, a user can open `/staking` in Hyperopen and use a real staking surface instead of a dead link. They can see validator performance, see staking totals, inspect staking rewards/action history for the connected account, and perform core staking workflows: transfer HYPE into staking balance, transfer out of staking balance, delegate, and undelegate.

This is visible by running the app, navigating to `/staking`, and observing live data loaded from Hyperliquid `/info` plus mutation actions routed through `/exchange`.

## Progress

- [x] (2026-03-12 12:46Z) Created and claimed `bd` issue `hyperopen-r5n0` for this implementation.
- [x] (2026-03-12 12:45Z) Captured Hyperliquid staking desktop/mobile snapshots and extracted visible sections/tables.
- [x] (2026-03-12 12:46Z) Verified live `/info` staking response schemas for `validatorSummaries`, `delegatorSummary`, `delegations`, `delegatorRewards`, and `delegatorHistory`.
- [x] (2026-03-12 14:33Z) Implemented staking API/action/effect/runtime plumbing and route loading.
- [x] (2026-03-12 14:33Z) Implemented staking mutations (`cDeposit`, `cWithdraw`, `tokenDelegate`) via signed exchange actions.
- [x] (2026-03-12 14:33Z) Built `/staking` view + VM and wired app route rendering.
- [x] (2026-03-12 14:43Z) Added staking tests and passed required validation gates (`npm run check`, `npm test`, `npm run test:websocket`).

## Surprises & Discoveries

- Observation: `app.hyperliquid.xyz/staking` is fully client-rendered; plain crawler fetches returned only shell HTML.
  Evidence: Initial web fetch returned no useful route content; browser-inspection snapshot was required.

- Observation: Hyperliquid staking validator rows use `stake` values that appear to be fixed-point integers (display scale differs from integer payload).
  Evidence: Live `validatorSummaries` response returns large integers such as `556530876114619`, while UI row displays values on the order of millions.

- Observation: CLJS regex literals can be over-escaped for JS output when using doubled backslashes for literal decimal dots.
  Evidence: `parse-hype-input->wei` failed on `"1.25"` until the regex changed from `\\.` to `\.`.

## Decision Log

- Decision: Implement staking as a first-class feature module (`actions`, `effects`, `view`, `vm`) with dedicated runtime/effect registration.
  Rationale: Existing route features (`vaults`, `funding-comparison`, `api-wallets`) use module-level ownership and route-gated effects; this keeps architecture consistent and deterministic.
  Date/Author: 2026-03-12 / Codex

- Decision: Use direct Hyperliquid staking `/info` request types and exchange action types surfaced by official docs/live endpoints (`validatorSummaries`, `delegatorSummary`, `delegations`, `delegatorRewards`, `delegatorHistory`, `cDeposit`, `cWithdraw`, `tokenDelegate`).
  Rationale: Prevents speculative schema design and aligns behavior with current protocol payloads.
  Date/Author: 2026-03-12 / Codex

## Outcomes & Retrospective

Completed. `/staking` is now a fully wired feature module with:

- Route rendering and startup/action-adapter route loading hooks.
- Read-side `/info` staking data fetch + projection state.
- Signed staking mutations for deposit/withdraw/delegate/undelegate.
- Staking VM + view with validator table, balances, action cards, and reward/history tabs.
- Runtime/catalog/schema/effect-order registrations and tests.

Validation outcomes:

- `npm run check`: pass
- `npm test`: pass
- `npm run test:websocket`: pass

## Context and Orientation

Current repo state has a nav link to `/staking` but no route rendering in `/hyperopen/src/hyperopen/views/app_view.cljs`. The header includes a desktop Staking link that is hardcoded inactive. Portfolio already links to `/staking`.

Runtime action routing currently supports route-triggered loaders for vaults, funding comparison, and API wallets through `/hyperopen/src/hyperopen/runtime/action_adapters.cljs` and startup dispatches in `/hyperopen/src/hyperopen/startup/runtime.cljs`.

API stack already has the shared Hyperliquid `/info` client and gateway/endpoints pattern in `/hyperopen/src/hyperopen/api/default.cljs`, `/hyperopen/src/hyperopen/api/gateway/**`, and `/hyperopen/src/hyperopen/api/endpoints/**`. Exchange mutations are centralized in `/hyperopen/src/hyperopen/api/trading.cljs` and typed-data signing helpers in `/hyperopen/src/hyperopen/utils/hl_signing.cljs`.

## Plan of Work

Add a new staking module that mirrors the established route feature structure:

1. Create staking API endpoint and gateway helpers for read operations from `/info`.
2. Add default API facade methods that expose those gateway calls.
3. Add staking projections in `/hyperopen/src/hyperopen/api/projections.cljs` for begin/success/error states.
4. Create staking actions for route detection/loading, UI controls (tab/timeframe/selection), and mutation triggers.
5. Create staking effects for async loading/mutations with route gating and connected-address context.
6. Add runtime registrations:
   - action dependencies in collaborators + app overrides.
   - effect dependencies in collaborators + app effects/effect adapters.
   - schema runtime catalog and contracts.
   - effect-order policy entries for new heavy effects.
7. Add signing + trading support for user-signed staking actions (`cDeposit`, `cWithdraw`, `tokenDelegate`) and expose helpers consumed by staking effects.
8. Build staking VM and view for desktop/mobile parity-inspired layout.
9. Wire `/staking` route rendering in app view and active-link behavior in header.
10. Add tests for staking normalization/actions/effects/trading request builders.

## Concrete Steps

From `/Users/barry/.codex/worktrees/2387/hyperopen`:

1. Implement staking API + route/runtime wiring.
2. Implement signing/exchange support for staking mutations.
3. Implement staking view + VM and route rendering.
4. Run:

    npm run check
    npm test
    npm run test:websocket

5. Update this ExecPlan with final progress/discoveries/outcomes, then move to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

Acceptance criteria:

- `/staking` renders in-app and is reachable from header and portfolio link.
- Unconnected state shows connect gating and still renders validator performance data.
- Connected state loads delegator summary, delegations, rewards history, and action history.
- Staking mutation actions dispatch and return success/error feedback (including route-gated no-op when not on staking page).
- Runtime contract/schema checks pass with new action/effect IDs.
- Required gates pass: `npm run check`, `npm test`, `npm run test:websocket`.

## Idempotence and Recovery

All read-side requests are idempotent (`/info` queries). Mutation requests require signatures and are only sent from explicit user actions. If an API mutation fails, keep local projection stable and surface error text; no destructive local rollback is needed beyond clearing pending states.

## Artifacts and Notes

External parity and docs evidence used for this plan:

- Live app screenshot capture: `tmp/browser-inspection/inspect-2026-03-12T12-40-04-947Z-49fe5ae9/hyperliquid-staking/**`
- Hyperliquid docs pages:
  - Staking concepts and validator process.
  - How-to-stake page.
  - `/info` endpoint staking query types.
  - `/exchange` endpoint staking mutation action types.

## Interfaces and Dependencies

At completion, these interfaces must exist:

- Staking read API functions under `hyperopen.api.default` for validator/delegator queries.
- Staking action/effect handlers registered through runtime catalog and contracts.
- Trading/signing helpers for `cDeposit`, `cWithdraw`, `tokenDelegate` mutation payloads.
- Staking route view model and view namespace consumed by `app-view` route switch.

Update note (2026-03-12): Initial plan created after live app/docs/API research so implementation can proceed with concrete payload contracts instead of inferred schemas.
