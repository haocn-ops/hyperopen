# Close Security Review Gaps

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` are maintained under `.agents/PLANS.md`.

## Purpose / Big Picture

Close the two verified gaps from the security review. A release with no valid Hyperliquid network must reject before loading crypto, incrementing a nonce, or asking a wallet to sign. Tenants with an enabled affiliate integration must expose an explicit device-local consent control; granting sends future eligible events, while revoking removes pending events and prevents stale retries from sending later.

## Context References

Public refs:
- Direct user request to fix the findings in `tmp/multi-agent/2026-07-27-security-remediation/review-report.json`.

Repo artifacts:
- `docs/exec-plans/completed/2026-07-27-security-remediation.md`
- `tmp/multi-agent/2026-07-27-security-remediation/review-report.json`

## Progress

- [x] (2026-07-27) Add pre-signing trading guards and regression tests.
- [x] (2026-07-27) Add affiliate consent/revocation effects, queue invalidation, settings UI, and tests.
- [x] (2026-07-27) Run focused static, release, proxy, optimizer, and project pre-compile gates.
- [x] (2026-07-27) Install a user-local OpenJDK 21 runtime and run the ClojureScript compile and test suites.
- [x] (2026-07-27) Complete acceptance after all required gates passed.

## Surprises & Discoveries

- The existing `:trading-enabled?` check only protects the Enable Trading UI action; direct approval, agent-action, and user-action signing functions bypass it.
- Affiliate consent currently has a read-only localStorage predicate; no runtime action/effect or settings control writes it.

## Decision Log

- Decision: put the network guard in `hyperopen.api.trading.http` and call it from every signing family before crypto loading. Rationale: this keeps the error contract central and prevents future endpoints from relying on a late URL check. Date/Author: 2026-07-27 / Codex.
- Decision: implement consent as a wallet-settings action/effect using the existing runtime registry, with tenant-scoped pending queue removal on revoke. Rationale: settings already owns device-local preferences and the attribution adapter owns queue side effects. Date/Author: 2026-07-27 / Codex.

## Outcomes & Retrospective

The two reviewed gaps are implemented: signing APIs reject before crypto loading when the network contract is disabled, and affiliate consent now has a registered settings action/effect with tenant-scoped queue invalidation and stale-retry protection. Static checks, release tests (51/51), Cloudflare Worker tests (23/23), proxy tests (4/4), optimizer tests (7/7), the full ClojureScript suite (5,823 tests / 32,378 assertions), the WebSocket suite (561 tests / 3,184 assertions), and `npm run check` all pass with OpenJDK 21.

## Context and Orientation

Trading signatures flow through `src/hyperopen/api/trading/user_actions.cljs` for wallet typed-data actions and `src/hyperopen/api/trading/agent_actions.cljs` for private-key agent actions. `src/hyperopen/api/trading/http.cljs` owns the configured exchange URL and stable disabled-network error. Affiliate delivery and its local queue live in `src/hyperopen/runtime/effect_adapters/attribution.cljs`; runtime actions/effects are registered in `src/hyperopen/schema/runtime_registration/wallet.cljs`, `src/hyperopen/schema/contracts/*`, and wired by `src/hyperopen/app/actions.cljs` and `src/hyperopen/app/effects.cljs`. The header settings view model is `src/hyperopen/views/header/vm.cljs`, rendered by `src/hyperopen/views/header/settings.cljs`.

## Plan of Work

Add `trading-enabled?`, a shared disabled error, and a promise rejection helper to the trading HTTP module. Guard approval, user-action, and agent-action signing before crypto loading and nonce mutation. Add tests asserting zero signer/fetch calls for disabled contracts.

Expose consent state and a setter/revoker in the attribution adapter. Persist the tenant-scoped preference, remove pending records for that tenant on revoke, and require a record to remain queued before any retry callback may deliver. Register a boolean effect and action, expose them through header collaborators, and render a conditional Affiliate section only for a validated enabled tenant. Add unit and view tests for grant, revoke, stale retry, and conditional rendering.

## Concrete Steps

Run from `/Users/zh/Documents/Hyperopen`:

    npm run setup:worktree
    npm test -- --grep "trading|attribution|header"
    npm run check
    npm run test:websocket
    npm test

## Validation and Acceptance

Disabled network tests must show the signer, nonce persistence, crypto loader, and fetch are untouched and the stable disabled-network message is returned. Affiliate tests must show no delivery before consent, exact delivery after opt-in, pending records removed on revoke, and stale scheduled callbacks cannot send after re-enable. The settings view must render the Affiliate section only for a validated enabled tenant and dispatch the consent effect.

## Idempotence and Recovery

All changes are additive and safe to rerun. Do not reset or revert unrelated dirty-worktree changes. If a focused test exposes an existing fixture assumption, update only the relevant fixture or test seam.

## Artifacts and Notes

The review artifact remains at `tmp/multi-agent/2026-07-27-security-remediation/review-report.json` and is not modified by this implementation.

## Interfaces and Dependencies

The final runtime interfaces include `hyperopen.api.trading.http/trading-enabled?`, `trading-disabled-error`, and `hyperopen.runtime.effect-adapters.attribution/set-affiliate-consent!`. The consent action uses one boolean argument and is registered as `:actions/set-affiliate-consent`; the effect is `:effects/set-affiliate-consent`.
