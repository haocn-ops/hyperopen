# HIP3 Asset Icon Parity with Hyperliquid

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, Hyperopen will render HIP3 market icons the same way Hyperliquid does in its trade UI. Today, HIP3 entries such as `xyz:NVDA` and `xyz:SNDK` fail to show icons because Hyperopen requests `/coins/<base>.svg` (for example `NVDA.svg`) instead of Hyperliquid’s actual coin-key icon path (for example `xyz:NVDA.svg`). Users will be able to open the active asset bar and the asset selector and see HIP3 icons when Hyperliquid serves them.

## Progress

- [x] (2026-02-19 14:31Z) Read architecture/planning/frontend guardrail docs required for UI task execution.
- [x] (2026-02-19 14:31Z) Traced current icon flow in `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`.
- [x] (2026-02-19 14:31Z) Browser/API inspection completed against Hyperliquid app bundle and `/info` API.
- [x] (2026-02-19 14:32Z) Implemented shared icon-key normalization in `/hyperopen/src/hyperopen/views/asset_icon.cljs` with HIP3 and spot/perp handling.
- [x] (2026-02-19 14:32Z) Wired both active asset row and selector rows to use shared icon URL helper and removed HIP3 dex-based suppression.
- [x] (2026-02-19 14:32Z) Replaced view tests that enforced “component markets omit icons” with namespaced icon URL assertions.
- [x] (2026-02-19 14:33Z) Ran required validation gates (`npm run check`, `npm test`, `npm run test:websocket`) with all green.
- [x] (2026-02-19 14:33Z) Finalized plan and moved it to `/hyperopen/docs/exec-plans/completed/2026-02-19-hip3-asset-icon-parity.md`.

## Surprises & Discoveries

- Observation: Hyperliquid’s own icon component uses a `/coins/<normalizedCoin>.svg` path where HIP3 assets keep their namespace prefix (for example `xyz:NVDA`).
  Evidence: Prettified app bundle from `https://app.hyperliquid.xyz/static/js/main.ccb853ef.js` shows `u = "/coins/"` and icon `src: "".concat(u).concat(h, ".svg")`; `h` is derived from coin and preserves namespaced perp values.
- Observation: Hyperopen currently blocks icons whenever `:dex` exists, which excludes all component/HIP3 markets from rendering images.
  Evidence: `component-market?` branch in `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`.
- Observation: Hyperliquid does publish HIP3 icons at namespaced paths such as `/coins/xyz:NVDA.svg` and `/coins/xyz:SNDK.svg`.
  Evidence: Live `curl -I` checks return `Content-Type: image/svg+xml` for those paths.
- Observation: Hyperliquid spot icons are available as `BASE_spot.svg`, which allows parity-aligned spot icon URLs instead of base-only URLs.
  Evidence: Live `curl -I` checks returned `Content-Type: image/svg+xml` for `/coins/PURR_spot.svg`, `/coins/BTC_spot.svg`, and `/coins/HYPE_spot.svg`.

## Decision Log

- Decision: Reuse Hyperliquid’s icon-path behavior by deriving icon keys from `:coin` identity (not only `:base`) and allowing HIP3 icon fetches.
  Rationale: This is the minimal, deterministic parity fix and directly resolves HIP3 icon misses.
  Date/Author: 2026-02-19 / Codex
- Decision: Introduce one shared helper namespace for icon key/url normalization and reuse it in both views.
  Rationale: Prevents duplicated formatting logic and keeps selector/active-bar behavior consistent.
  Date/Author: 2026-02-19 / Codex
- Decision: Remove tests that assert HIP3/component markets must omit icons, replacing them with tests that assert namespaced icon URLs.
  Rationale: Existing tests encode now-incorrect behavior relative to Hyperliquid parity and user requirements.
  Date/Author: 2026-02-19 / Codex

## Outcomes & Retrospective

HIP3 icon parity is implemented for both active asset and selector rows. Hyperopen now derives icon URLs from canonical `:coin` identity (including namespaced HIP3 keys) and uses shared logic in `/hyperopen/src/hyperopen/views/asset_icon.cljs`. Existing behavior for non-HIP3 markets remains deterministic, and tests now assert namespaced icon rendering rather than suppressing component-market icons. Required repository validation gates passed with zero failures:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Context and Orientation

Icon rendering for selected market and selector rows currently lives in:

- `/hyperopen/src/hyperopen/views/active_asset_view.cljs`
- `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`

Both files currently build image URLs from `(or :base :coin)` and suppress rendering for `:dex` markets. HIP3 markets in Hyperopen are represented by namespaced `:coin` values (for example `xyz:NVDA`) with `:base` stripped to `NVDA`. That means Hyperopen requests `https://app.hyperliquid.xyz/coins/NVDA.svg`, which does not exist for HIP3 entries that require namespace-prefixed keys.

Tests that enforce current behavior live in:

- `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`
- `/hyperopen/test/hyperopen/views/asset_selector_view_test.cljs`

The repository mandates required validation gates after implementation:

- `npm run check`
- `npm test`
- `npm run test:websocket`

## Plan of Work

Add a pure helper namespace for icon key derivation, then switch both view call sites to use it.

In `/hyperopen/src/hyperopen/views/asset_icon.cljs` (new file), define pure functions:

- A function to normalize non-empty text.
- A function to derive icon key from market data and follow Hyperliquid parity rules:
  - Start from `:coin` identity when available.
  - For spot identifiers (`BASE/QUOTE`), use `BASE_spot`.
  - Preserve namespaced HIP3 coin keys (for example `xyz:NVDA`).
  - Apply the Hyperliquid compatibility rule where keys beginning with `k` drop the leading `k` except `km:` prefixed keys.
  - Skip keys starting with `@` (provider synthetic ids).
- A function that returns full icon URL string for valid keys.

In `/hyperopen/src/hyperopen/views/active_asset_view.cljs` and `/hyperopen/src/hyperopen/views/asset_selector_view.cljs`, replace local icon string construction with the shared helper and remove the `component-market?` suppression so HIP3 images can load.

Update tests to assert URLs built for namespaced coins and to remove assertions that component markets cannot render icons.

## Concrete Steps

From `/hyperopen`:

1. Add icon helper namespace and switch active/selector views to use it.
2. Update existing view tests for HIP3 icon behavior.
3. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
4. If all acceptance checks pass, move this file to `/hyperopen/docs/exec-plans/completed/` and add final retrospective notes.

## Validation and Acceptance

Acceptance is satisfied when all conditions below hold:

- In the active asset bar, a market like `{:coin "xyz:NVDA" ...}` produces `img :src` ending in `/coins/xyz:NVDA.svg`.
- In the asset selector row, HIP3/component markets no longer skip image rendering solely because `:dex` is set.
- Existing behavior for standard perps/spot remains valid (icons still render with deterministic URL format).
- Required validation gates pass:
  - `npm run check`
  - `npm test`
  - `npm run test:websocket`

## Idempotence and Recovery

Changes are additive and safe to reapply. If an edit fails midway, rerun the same file modifications and tests. If icon behavior regresses, revert only the touched icon helper/view/test files and re-run required gates to confirm recovery.

## Artifacts and Notes

Browser/API inspection artifacts (transient local evidence):

- Hyperliquid app HTML: `https://app.hyperliquid.xyz/trade`
- Hyperliquid main bundle: `https://app.hyperliquid.xyz/static/js/main.ccb853ef.js`
- Live icon checks: `https://app.hyperliquid.xyz/coins/xyz:NVDA.svg`, `https://app.hyperliquid.xyz/coins/xyz:SNDK.svg`
- API endpoint used for market metadata confirmation: `https://api.hyperliquid.xyz/info` (`{"type":"perpDexs"}`)

## Interfaces and Dependencies

No new third-party dependencies are required. The implementation uses existing ClojureScript view modules and action wiring (`:actions/mark-loaded-asset-icon`, `:actions/mark-missing-asset-icon`) unchanged.

Plan revision note: 2026-02-19 14:31Z - Initial plan created after Hyperliquid browser/API inspection and before code implementation.
Plan revision note: 2026-02-19 14:33Z - Updated progress/outcomes/discoveries after implementation and required validation gates passed.
Plan revision note: 2026-02-19 14:33Z - Marked plan move completion in `Progress` after relocating this file into `/hyperopen/docs/exec-plans/completed/`.
