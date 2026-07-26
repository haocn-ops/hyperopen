# ADR 0018: Effect-Order Authority Contract

- Status: Accepted
- Date: 2026-02-25

## Context

Interaction responsiveness policy already required projection-first action effects, but enforcement lived in scattered action-specific tests and conventions.

That created drift risk:

- action modules could regress effect order without a centralized guard
- duplicate heavy effects could be introduced silently during refactors
- runtime validation only checked payload and effect argument shape, not interaction ordering policy

## Decision

1. Effect-order authority for covered interaction actions is centralized in runtime validation.
2. Runtime validation enforces a contract module at `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`.
3. The contract uses ordered effect phases:
   - projection: immediate UI state (`:effects/save`, `:effects/save-many`)
   - persistence: local durability (`:effects/local-storage-set`, `:effects/local-storage-set-json`)
   - heavy I/O: subscriptions, fetches, reconnects, and API effects configured per covered action
4. Covered actions must satisfy projection-before-heavy ordering and nondecreasing phase order for tracked phases.
5. Covered actions reject duplicate heavy effects unless a policy entry explicitly allows duplicates.
6. Adding a new interaction-critical action with heavy I/O requires adding an explicit policy entry plus regression tests.

## Consequences

- Interaction ordering regressions fail fast in validation-enabled runs with concrete error context (`action-id`, `effect-index`, violated rule).
- Action-specific tests remain valuable for domain behavior, but centralized policy no longer depends on action-by-action convention.
- Covered actions can evolve internal effect lists while preserving responsiveness guarantees through phase categories.
- Uncovered actions remain unaffected until explicitly opted in.

## Invariant Ownership

- Central effect-order contract authority:
  - `/hyperopen/src/hyperopen/runtime/effect_order_contract.cljs`
- Runtime enforcement boundary:
  - `/hyperopen/src/hyperopen/runtime/validation.cljs`
- Covered interaction producers:
  - `/hyperopen/src/hyperopen/asset_selector/actions.cljs`
  - `/hyperopen/src/hyperopen/chart/actions.cljs`
  - `/hyperopen/src/hyperopen/account/history/actions.cljs`
  - `/hyperopen/src/hyperopen/orderbook/settings.cljs`
  - `/hyperopen/src/hyperopen/wallet/actions.cljs`
  - `/hyperopen/src/hyperopen/websocket/diagnostics_actions.cljs`
