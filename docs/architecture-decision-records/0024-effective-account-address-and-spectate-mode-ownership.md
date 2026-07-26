# ADR 0024: Effective Account Address and Spectate Mode Ownership Split

- Status: Accepted
- Date: 2026-03-04

## Context

Hyperopen currently uses `[:wallet :address]` for two different concerns:

- signing ownership (who is allowed to sign `approveAgent`, order, cancel, funding, and transfer requests), and
- read-side account identity (which address websocket user streams, account history fetches, and account freshness cues follow).

Trade.xyz-style Spectate Mode requires a read-only spectating identity that can differ from the connected wallet owner. Reusing `[:wallet :address]` for both concerns makes that unsafe, because spectating could accidentally leak into signing or agent-session ownership paths.

## Decision

1. Introduce a canonical account-context seam in `/hyperopen/src/hyperopen/account/context.cljs`.
2. Keep wallet owner address (`[:wallet :address]`) as the only signing identity authority.
3. Introduce Spectate Mode read identity (`[:account-context :spectate-mode :address]`) with explicit active state.
4. Define `effective-account-address` as:
   - spectate address when Spectate Mode is active,
   - otherwise owner address.
5. Read-side subscription/fetch/projection ownership must use `effective-account-address`.
6. Mutation eligibility must be centralized as `mutations-allowed?` and default to false during active Spectate Mode.

## Consequences

- Spectate Mode can change account read context without changing signer ownership.
- Account-stream and account-history routing can migrate to one deterministic selector instead of ad hoc branching.
- Signing invariants in `/hyperopen/src/hyperopen/api/trading.cljs` and wallet agent-session paths remain consensus-safe and isolated.
- Runtime tests must explicitly cover owner-versus-effective identity separation to prevent regressions.

## Invariant Ownership

- Canonical account-context selectors and address normalization:
  `/hyperopen/src/hyperopen/account/context.cljs`
- Startup state restoration for Spectate Mode preference/watchlist:
  `/hyperopen/src/hyperopen/startup/restore.cljs`
- Default app-state shape for account context:
  `/hyperopen/src/hyperopen/state/app_defaults.cljs`
- Signing ownership and agent-session invariants (unchanged authority):
  `/hyperopen/src/hyperopen/api/trading.cljs`
  `/hyperopen/src/hyperopen/wallet/agent_runtime.cljs`
  `/hyperopen/src/hyperopen/wallet/agent_session.cljs`
