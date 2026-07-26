---
owner: platform
status: canonical
last_reviewed: 2026-05-12
review_cycle_days: 90
source_of_truth: true
---

# Browser Storage Policy

## Purpose
This document defines when Hyperopen browser persistence should use IndexedDB, `localStorage`, or `sessionStorage`. The goal is consistent storage choices across future agent and human changes so high-churn caches do not land in synchronous browser storage and tiny startup preferences do not get over-engineered.

## Scope and Precedence
- This policy applies to browser-side persistence added or changed under `/hyperopen/src/**`.
- Use this document with `/hyperopen/ARCHITECTURE.md`, `/hyperopen/docs/RELIABILITY.md`, and `/hyperopen/docs/SECURITY.md`.
- Task-specific user or developer instructions override this policy for the current task.

## Boundary Rules (MUST)
- Keep browser storage input and output out of pure domain and application logic. Storage reads and writes belong in platform helpers, runtime effect adapters, startup restore paths, or other infrastructure boundaries.
- Use `/hyperopen/src/hyperopen/platform/indexed_db.cljs` as the shared IndexedDB boundary. Do not add ad hoc `js/indexedDB` calls throughout feature code.
- Keep storage records deterministic. IndexedDB records should use a stable key and include `:saved-at-ms`. Include `:version` when schema evolution or migration ordering matters.
- Treat migration fallback as temporary rollout behavior, not as a permanent dual-write requirement. If a new backend replaces an old one, prefer new-backend read first and keep old-backend fallback only long enough to de-risk rollout.
- Record any planned fallback removal or storage cleanup follow-up in a contributor-visible artifact such as a GitHub Issue/PR, active ExecPlan, Improvement Plane artifact, or canonical doc.

## Storage Selection Rules

### Use `localStorage` when all of these are true
- The value is small and bounded. In this repository that means a preference or tiny fixed-size state, not a cache that grows with markets, coins, rows, or history depth.
- The value is useful to read synchronously during startup before later async work finishes.
- Writes are low-frequency. Typical examples are toggles, selected tabs, page sizes, chart mode preferences, locale, and similar user-interface preferences.
- A single key or small fixed set of keys is enough; no record indexing, pruning, or per-entity fanout is needed.

Canonical examples in the current codebase:
- Chart type and timeframe preferences.
- Orderbook view settings.
- Portfolio or account table page-size preferences.
- Locale, font, and similar small user-interface preferences.
- Active-market display or other tiny identity/display hints used at startup.

### Use IndexedDB when any of these are true
- The payload can grow with the number of assets, markets, rows, or time buckets.
- Writes can happen on hot paths such as websocket patch streams, drag or zoom interactions, scroll-driven updates, or repeated interaction bursts.
- Records are naturally keyed by entity, such as asset, coin, timeframe, or user-scoped cache identity.
- Future pruning, partial replacement, or schema migration is likely.
- Async restore is acceptable, or the feature already has a deterministic fallback/default while restore completes.

Canonical examples in the current codebase:
- Asset-selector markets cache.
- Market funding history cache keyed per coin.
- Chart visible-range persistence keyed per asset and timeframe.

### Use `sessionStorage` only when session scope is an explicit product rule
- The data should disappear when the browser tab or session ends.
- The user experience intentionally differs between session-only and device-persistent modes.
- Cross-tab or cross-session reuse is not required.

Canonical example in the current codebase:
- Agent session persistence when the user explicitly chooses session-only mode instead of device-persistent mode.

## Anti-Patterns (DO NOT)
- Do not put large or high-churn caches in `localStorage`.
- Do not use `localStorage` for write-heavy flows just because it is easier to read synchronously.
- Do not move tiny startup preferences to IndexedDB unless async restore is already acceptable for that surface.
- Do not assume IndexedDB, `localStorage`, or `sessionStorage` provides meaningful protection against Cross-Site Scripting. Browser-readable storage is still browser-readable storage.
- Do not add direct browser storage calls inside reducers, domain policy, or other pure decision code.

## Agent Decision Checklist
When adding or changing browser persistence, answer these questions in order:
1. Is this a small, fixed-size preference that materially benefits from synchronous startup restore? If yes, use `localStorage`.
2. Can this value grow per asset, timeframe, coin, or history row, or can it update repeatedly on a hot path? If yes, use IndexedDB.
3. Must the data disappear with the current browser session because product behavior says it is session-only? If yes, use `sessionStorage`.
4. Does the storage choice involve secret or signing material? If yes, follow `/hyperopen/docs/SECURITY.md` and remember that choosing IndexedDB versus `localStorage` does not solve Cross-Site Scripting risk.
5. Are you replacing an older storage backend? If yes, ship a staged migration with new-backend-first reads, temporary fallback, and contributor-visible follow-up tracking if cleanup is not part of the current task.

Default rule for future agents: if the value is not obviously a tiny synchronous preference, prefer IndexedDB over `localStorage` for browser durability.
