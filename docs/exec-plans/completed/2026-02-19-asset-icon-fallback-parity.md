# Asset Icon Fallback Parity for Cross-DEX Markets

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, Hyperopen will load icon files for assets whose primary coin key does not have a dedicated `/coins/<key>.svg` on Hyperliquid, but where an equivalent icon does exist under another key. Today, examples like `cash:MSFT` and `xyz:COPPER` render broken icons because Hyperopen only tries one URL derived from the market coin. After this change, those rows will resolve to available alternatives (for example `xyz:MSFT.svg` and `flx:COPPER.svg`) and render deterministically in both active asset and selector rows.

## Progress

- [x] (2026-02-19 14:50Z) Read required planning and UI guardrail docs (`/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, `/hyperopen/docs/agent-guides/trading-ui-policy.md`).
- [x] (2026-02-19 14:50Z) Inspected current icon implementation and integration points in `/hyperopen/src/hyperopen/views/asset_icon.cljs`, `/hyperopen/src/hyperopen/views/active_asset_view.cljs`, and `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`.
- [x] (2026-02-19 14:50Z) Collected live Hyperliquid evidence from app bundle and API to identify failing icon keys and available equivalents.
- [x] (2026-02-19 14:55Z) Implemented icon fallback aliasing in `/hyperopen/src/hyperopen/views/asset_icon.cljs`.
- [x] (2026-02-19 14:56Z) Added fallback-focused tests in `/hyperopen/test/hyperopen/views/asset_icon_test.cljs` and integration assertions in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` and `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`.
- [x] (2026-02-19 15:00Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) with all green.
- [x] (2026-02-19 15:01Z) Updated outcomes and moved this plan to `/hyperopen/docs/exec-plans/completed/2026-02-19-asset-icon-fallback-parity.md`.

## Surprises & Discoveries

- Observation: Hyperliquid’s current trade bundle (`/static/js/main.ccb853ef.js`) builds icon URLs as `/coins/<key>.svg` and only normalizes keys by stripping leading `k` when not `km:`.
  Evidence: Extracted bundle snippet around icon component shows `u="/coins/"`, icon `src: "".concat(u).concat(h, ".svg")`, and key normalization `function Dr(e){return e.startsWith("k")&&!e.startsWith("km:")?e.slice(1):e}`.
- Observation: Hyperliquid’s coin CDN currently returns HTML fallback (no SVG) for many valid market coin keys (56 of 356 unique keys from the current market universe).
  Evidence: Live scan against `https://api.hyperliquid.xyz/info` market universe + `https://app.hyperliquid.xyz/coins/<key>.svg` detected missing keys including `cash:MSFT`, `cash:GOLD`, `km:NVDA`, `hyna:ADA`, and `xyz:COPPER`.
- Observation: Several missing keys have working equivalent icon keys (for example `cash:MSFT -> xyz:MSFT`, `xyz:COPPER -> flx:COPPER`, `hyna:ADA -> ADA`).
  Evidence: Direct live checks against alternate paths returned SVG payloads for those aliases.
- Observation: `AZTEC.svg` is live and returns SVG; `AZTEC` icon failure in user screenshots is not due to a missing CDN asset file.
  Evidence: Probe check `https://app.hyperliquid.xyz/coins/AZTEC.svg` returned SVG payload.

## Decision Log

- Decision: Implement deterministic alias fallback inside icon-key normalization (pure helper layer) instead of adding asynchronous runtime retry state.
  Rationale: This keeps UI action pipelines simple and deterministic, avoids widening action/state contracts, and addresses current user-visible failures with low surface-area risk.
  Date/Author: 2026-02-19 / Codex
- Decision: Build alias mappings from live-verified equivalents only and keep existing `k`/`km:` and spot behavior unchanged.
  Rationale: Preserves current behavior for assets that already work while fixing the known cross-DEX key mismatch class.
  Date/Author: 2026-02-19 / Codex

## Outcomes & Retrospective

Implemented deterministic alias mapping in `/hyperopen/src/hyperopen/views/asset_icon.cljs` and covered behavior with both helper-level and view-level tests. The fix resolves the user-reported class where a market key icon is missing but an equivalent icon exists under a different key.

Measured outcome from live icon scans:

- Before alias mapping: 56 unresolved icon keys.
- After alias mapping: 25 unresolved icon keys.

Resolved examples from this task:

- `cash:MSFT` now resolves to `xyz:MSFT.svg`.
- `xyz:COPPER` now resolves to `flx:COPPER.svg`.

Remaining unresolved keys appear to have no equivalent SVG file currently available on Hyperliquid’s icon CDN (for example `xyz:COST`, `km:TENCENT`, `vntl:GOLDJM`, `flx:USDE`).

## Context and Orientation

Icon URL derivation is centralized in `/hyperopen/src/hyperopen/views/asset_icon.cljs` and consumed by:

- `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
- `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`

Both views currently trust a single URL from `asset-icon/market-icon-url` and mark the market key as missing on image `error`.

The user-reported failures come from markets where Hyperopen’s derived key is valid market identity but the CDN lacks the exact SVG path. Example current misses observed live:

- `cash:MSFT` (icon exists at `xyz:MSFT`)
- `xyz:COPPER` (icon exists at `flx:COPPER`)

Acceptance is user-visible: icons in active asset and selector should render for these known cases without regressing existing spot/perp behavior.

## Plan of Work

Update `market-icon-key` logic in `/hyperopen/src/hyperopen/views/asset_icon.cljs` to apply a deterministic alias map after current normalization rules. Keep existing spot handling (`BASE_spot`) and `k`/`km:` handling untouched.

Add tests that assert the alias-backed URLs for representative failing keys (`cash:MSFT`, `xyz:COPPER`, `hyna:ADA`) and preserve existing expectations for namespaced keys that already resolve directly (`xyz:XYZ100`).

Run full repo-required validation gates.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/asset_icon.cljs` to add alias fallback map and apply it in `market-icon-key`.
2. Add or update tests in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs` and `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs` (or a dedicated asset icon helper test file).
3. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
4. Update this plan with final outcomes and move it to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

Acceptance criteria:

- `asset-icon/market-icon-url` returns URL ending in `/coins/xyz:MSFT.svg` for market coin `cash:MSFT`.
- `asset-icon/market-icon-url` returns URL ending in `/coins/flx:COPPER.svg` for market coin `xyz:COPPER`.
- Existing direct mappings still hold (for example `xyz:XYZ100 -> /coins/xyz:XYZ100.svg`, `PURR/USDC -> /coins/PURR_spot.svg`).
- Required gates pass:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`

## Idempotence and Recovery

Edits are additive and local to icon derivation/tests. Re-running steps is safe. If validation fails, revert only touched icon helper/test files and rerun gates to restore baseline.

## Artifacts and Notes

Live evidence sources used during planning:

- `https://app.hyperliquid.xyz/trade`
- `https://app.hyperliquid.xyz/static/js/main.ccb853ef.js`
- `https://api.hyperliquid.xyz/info` (`perpDexs`, `metaAndAssetCtxs`, `spotMeta`, `webData2`)
- `https://app.hyperliquid.xyz/coins/<iconKey>.svg` checks

Representative current missing icon keys from scan: `cash:MSFT`, `cash:GOLD`, `cash:INTC`, `km:NVDA`, `hyna:ADA`, `xyz:COPPER`, `xyz:NATGAS`, `xyz:SOFTBANK`.

Baseline unresolved groups (56 keys total, before aliasing):

- `abcd`: `abcd:USA500`
- `base`: `CANTO`, `MYRO`, `PEOPLE`
- `cash`: `cash:GOLD`, `cash:INTC`, `cash:MSFT`, `cash:SILVER`
- `flx`: `flx:BTC`, `flx:GAS`, `flx:PALLADIUM`, `flx:PLATINUM`, `flx:USDE`
- `hyna`: `hyna:1000PEPE`, `hyna:ADA`, `hyna:BCH`, `hyna:BNB`, `hyna:DOGE`, `hyna:ENA`, `hyna:FARTCOIN`, `hyna:IP`, `hyna:LIGHTER`, `hyna:LINK`, `hyna:LIT`, `hyna:LTC`, `hyna:PUMP`, `hyna:SUI`, `hyna:XMR`, `hyna:XPL`
- `km`: `km:AAPL`, `km:EUR`, `km:GLDMINE`, `km:GOLD`, `km:GOOGL`, `km:JPN225`, `km:MU`, `km:NVDA`, `km:SEMI`, `km:SILVER`, `km:TENCENT`, `km:USENERGY`
- `vntl`: `vntl:GOLDJM`, `vntl:SILVERJM`
- `xyz`: `xyz:COPPER`, `xyz:COST`, `xyz:DXY`, `xyz:GME`, `xyz:HYUNDAI`, `xyz:JP225`, `xyz:KR200`, `xyz:LLY`, `xyz:NATGAS`, `xyz:SKHX`, `xyz:SMSN`, `xyz:SOFTBANK`, `xyz:URNM`

Post-fix unresolved groups (25 keys total, after aliasing):

- `base`: `CANTO`, `MYRO`, `PEOPLE`
- `flx`: `flx:USDE`
- `hyna`: `hyna:1000PEPE`, `hyna:LIGHTER`
- `km`: `km:GLDMINE`, `km:JPN225`, `km:SEMI`, `km:TENCENT`, `km:USENERGY`
- `vntl`: `vntl:GOLDJM`, `vntl:SILVERJM`
- `xyz`: `xyz:COST`, `xyz:DXY`, `xyz:GME`, `xyz:HYUNDAI`, `xyz:JP225`, `xyz:KR200`, `xyz:LLY`, `xyz:NATGAS`, `xyz:SKHX`, `xyz:SMSN`, `xyz:SOFTBANK`, `xyz:URNM`

## Interfaces and Dependencies

No new dependencies will be added. The change stays inside existing view helper and tests:

- `/hyperopen/src/hyperopen/views/asset_icon.cljs`
- `/hyperopen/test/hyperopen/views/*.cljs`

Plan revision note: 2026-02-19 14:50Z - Initial plan created after live Hyperliquid bundle/API inspection and before code edits.
Plan revision note: 2026-02-19 15:00Z - Updated progress, outcomes, and artifact evidence after implementing alias fallback, adding tests, and passing required validation gates.
Plan revision note: 2026-02-19 15:01Z - Marked plan move completion after relocating the file from active to completed.
