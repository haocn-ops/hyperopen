# Fix Active Asset Icon Default Render Regression

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the active asset bar on `/trade` will keep showing normal asset icons immediately for common markets such as `BTC-USDC`, `ETH-USDC`, and `USA500-USDT0`, while still falling back to the black monogram circle when an upstream icon URL is broken. A user should be able to open the trade page, see the normal icon for working markets without waiting on any special state transition, and still see a monogram instead of a browser broken-image placeholder for the edge case of a missing icon.

## Progress

- [x] (2026-03-06 19:00Z) Created and claimed `bd` issue `hyperopen-ekr` for the follow-up regression.
- [x] (2026-03-06 19:02Z) Confirmed from Replicant source that element attributes are applied before `:on` listeners, which makes a `src`-before-`:load` gate unsafe for cached images.
- [x] (2026-03-06 19:03Z) Captured a local browser inspection snapshot at `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-01-32-687Z-99d95f57/` to validate the active asset header structure while debugging.
- [x] (2026-03-06 19:06Z) Restored an immediate-success render path that keeps the monogram mounted, adds a visible `background-image` layer for unresolved icon URLs, and limits visible `<img>` rendering to the already-loaded state.
- [x] (2026-03-06 19:08Z) Updated `active_asset_view_test.cljs` so the unloaded state requires the monogram plus hidden probe and background layer, while the loaded state still requires the transparent visible icon path.
- [x] (2026-03-06 19:19Z) Re-ran `npm run check`, `npm test`, and `npm run test:websocket` after the renderer and test updates.
- [x] (2026-03-06 19:23Z) Performed manual browser QA for `BTC-USDC`, `ETH-USDC`, `USA500-USDT0`, and the broken `BRENTOIL-USDC` icon case using the local browser inspection workflow.
- [x] (2026-03-06 19:24Z) Closed `bd` issue `hyperopen-ekr`.

## Surprises & Discoveries

- Observation: The previous fix depended on `:actions/mark-loaded-asset-icon`, but Replicant does not guarantee event listeners are attached before `src` is set on newly created image nodes.
  Evidence: In Replicant `create-node`, `set-attributes` runs before append, and `set-attr` applies `:src` before `:on` for the map shape used by `asset-icon`.

- Observation: A visible browser screenshot alone is not enough to prove the race, because some sessions will still receive the `load` event depending on caching and timing.
  Evidence: The snapshot at `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-01-32-687Z-99d95f57/local-active-asset/desktop/screenshot.png` still showed `BTC` correctly even though the current implementation remains timing-sensitive.

- Observation: The same event-order hazard applies to `error`, not only `load`; in the `BRENTOIL` browser case the hidden probe stayed mounted with `naturalWidth` `0`, but the UI remained correct because the monogram base never left the DOM.
  Evidence: Manual browser QA for `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-22-44-755Z-807f8043/qa-brentoil/desktop/screenshot.png` showed the monogram fallback, and a live eval still reported the hidden probe at `https://app.hyperliquid.xyz/coins/xyz:BRENTOIL.svg` with `naturalWidth` `0`.

## Decision Log

- Decision: Reintroduce the immediate background-image render layer for the unloaded state instead of relying on the hidden probe image to promote working icons into view.
  Rationale: CSS `background-image` renders working icons immediately, renders nothing for broken URLs, and does not produce the browser broken-image placeholder.
  Date/Author: 2026-03-06 / Codex

- Decision: Keep the hidden probe image in the unloaded state.
  Rationale: The probe still lets the app record `loaded-icons` and `missing-icons`, which prevents repeated error work and preserves the existing icon-status state model.
  Date/Author: 2026-03-06 / Codex

- Decision: Keep the monogram fallback permanently mounted underneath any unresolved icon layer instead of letting probe state control whether fallback is visible.
  Rationale: This makes both the default success path and the broken-link fallback visually correct even when Replicant misses the initial `load` or `error` event because listeners attach after `src`.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

The second regression came from treating `loaded-icons` as the source of truth for whether a working icon could be shown. Because Replicant applies `src` before `:on`, cached images can load before the `:load` handler exists, so working assets such as `BTC`, `ETH`, and `cash:USA500` remained on the monogram fallback. The same ordering hazard can also suppress the initial `:error` event, which means the UI cannot depend on probe state to reveal the fallback either.

The final renderer keeps the monogram in place at all times, adds an immediate CSS `background-image` success layer while an icon URL is unresolved, and preserves the explicit visible `<img>` only for keys already marked loaded. That restores the common-case icon rendering without reintroducing the browser broken-image glyph for missing upstream assets.

Manual browser QA on 2026-03-06 confirmed:

- `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-20-55-937Z-1375ac59/qa-btc/desktop/screenshot.png` showed the normal `BTC-USDC` icon.
- `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-21-30-032Z-6ba228c8/qa-eth/desktop/screenshot.png` showed the normal `ETH-USDC` icon.
- `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-22-08-832Z-0f924cc1/qa-usa500/desktop/screenshot.png` showed the normal `USA500-USDT0` icon.
- `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-22-44-755Z-807f8043/qa-brentoil/desktop/screenshot.png` showed the monogram fallback for the broken `BRENTOIL-USDC` icon instead of a broken-image placeholder.

All required validation gates passed: `npm run check`, `npm test`, and `npm run test:websocket`.

## Context and Orientation

The active asset icon renderer lives in `/hyperopen/src/hyperopen/views/active_asset_view.cljs` as `asset-icon`. It receives `missing-icons` and `loaded-icons` from the asset selector state. The regression came from making the visible icon depend on `loaded-icons`; that turned a best-effort bookkeeping signal into the sole authority for whether a working icon could be shown.

The render engine is Replicant. In its node creation path, it sets element attributes before it attaches event listeners, so an image can begin loading before the `load` handler exists. That is acceptable for telemetry or caching, but it is not acceptable when the UI waits for that event before showing a normal icon.

The relevant tests are in `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`. The important cases are:

- unloaded icon URL present
- loaded icon URL present
- icon key known missing
- spot icon parity for `HYPE_spot.svg`

## Plan of Work

Edit `asset-icon` so the monogram base remains in the DOM, an immediate `background-image` layer is shown whenever an icon URL exists but the icon is not yet marked loaded, and the hidden probe image is used only to emit `:load` and `:error`. Keep the current direct visible `<img>` for the loaded state so transparent SVGs remain rendered without a forced white backing.

Update the tests to assert that the unloaded state includes a `:background-image` layer and an `opacity-0` probe image, while the loaded state includes the visible image and no `:background-image` layer. Keep the missing-icon fallback assertion and HYPE transparent parity assertion.

After the code and tests pass locally, perform manual browser QA. At minimum, verify normal icons for `BTC-USDC`, `ETH-USDC`, and `USA500-USDT0` in the active asset bar, then verify the broken-icon edge case still resolves to the monogram fallback instead of the browser broken-image glyph.

## Concrete Steps

From `/hyperopen`:

1. Edit `/hyperopen/src/hyperopen/views/active_asset_view.cljs`.
2. Edit `/hyperopen/test/hyperopen/views/active_asset_view_test.cljs`.
3. Run:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
4. Perform browser QA and capture screenshot evidence under `/hyperopen/tmp/browser-inspection/`.
5. Close the issue:
   - `bd close hyperopen-ekr --reason "Completed" --json`

## Validation and Acceptance

Acceptance is satisfied when:

1. Working icons render immediately in the active asset bar without depending on the `load` event.
2. Broken icon URLs still resolve to the monogram fallback instead of a browser broken-image placeholder.
3. Transparent SVG parity remains intact.
4. `npm run check`, `npm test`, and `npm run test:websocket` all pass.
5. Manual browser QA confirms no regression for the common working assets named above.

## Idempotence and Recovery

This change is safe to reapply because it only adjusts one view renderer and its tests. If it regresses, restore the previous renderer and revert the paired test expectations together.

## Artifacts and Notes

- Tracking issue: `hyperopen-ekr`
- Browser inspection artifact: `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-01-32-687Z-99d95f57/`
- Browser QA artifacts:
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-20-55-937Z-1375ac59/`
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-21-30-032Z-6ba228c8/`
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-22-08-832Z-0f924cc1/`
  - `/hyperopen/tmp/browser-inspection/inspect-2026-03-06T19-22-44-755Z-807f8043/`

## Interfaces and Dependencies

No new dependencies are required.

The existing action contract remains:

- `[:actions/mark-loaded-asset-icon market-key]`
- `[:actions/mark-missing-asset-icon market-key]`

Plan revision note: 2026-03-06 19:24Z - Completed after restoring the immediate icon render path, validating the broken-link fallback with the monogram underlay, rerunning required gates, and capturing browser QA artifacts for BTC, ETH, USA500, and BRENTOIL.
